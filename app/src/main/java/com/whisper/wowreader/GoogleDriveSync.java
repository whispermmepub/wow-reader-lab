package com.whisper.wowreader;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;

import com.google.android.gms.auth.api.identity.AuthorizationClient;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.RevokeAccessRequest;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.Scope;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class GoogleDriveSync {
    static final int REQUEST_AUTHORIZE = 4104;
    private static final String BACKUP_NAME = "wow_reader_backup_v1.zip";
    private static final String STATE_BACKUP_NAME = "wow_reader_state_v2.zip";
    private static final List<Scope> SCOPES = Arrays.asList(
            new Scope(Scopes.DRIVE_APPFOLDER)
    );

    static final class Profile {
        String uid = "";
        String name = "Google account";
        String email = "";
        String picture = "";
        String accessToken = "";
    }

    interface AuthCallback {
        void onReady(Profile profile);
        void onError(String message);
    }

    interface SyncCallback {
        void onSuccess(String message);
        void onError(String message);
    }

    interface BackupCheckCallback {
        void onResult(boolean found);
    }

    private final Activity activity;
    private final AuthorizationClient authorizationClient;
    private AuthCallback pendingAuthCallback;

    GoogleDriveSync(Activity activity) {
        this.activity = activity;
        this.authorizationClient = Identity.getAuthorizationClient(activity);
    }

    void authorize(boolean chooseAccount, AuthCallback callback) {
        AuthorizationRequest.Builder builder = AuthorizationRequest.builder()
                .setRequestedScopes(SCOPES);
        AuthorizationRequest request = builder.build();
        authorizationClient.authorize(request)
                .addOnSuccessListener(result -> handleAuthorizationResult(result, callback))
                .addOnFailureListener(e -> callback.onError(friendly(e)));
    }

    void authorizeSilently(AuthCallback callback) {
        AuthorizationRequest request = AuthorizationRequest.builder()
                .setRequestedScopes(SCOPES)
                .build();
        authorizationClient.authorize(request)
                .addOnSuccessListener(result -> {
                    if (result != null && result.hasResolution()) {
                        callback.onError("Google account needs reconnect");
                        return;
                    }
                    handleAuthorizationResult(result, callback);
                })
                .addOnFailureListener(e -> callback.onError(friendly(e)));
    }

    boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_AUTHORIZE) return false;
        AuthCallback callback = pendingAuthCallback;
        pendingAuthCallback = null;
        if (callback == null) return true;
        if (resultCode != Activity.RESULT_OK || data == null) {
            callback.onError("Google account connection was cancelled");
            return true;
        }
        try {
            AuthorizationResult result = authorizationClient.getAuthorizationResultFromIntent(data);
            handleAuthorizationResult(result, callback);
        } catch (Exception e) {
            callback.onError(friendly(e));
        }
        return true;
    }

    private void handleAuthorizationResult(AuthorizationResult result, AuthCallback callback) {
        if (result == null) {
            callback.onError("Google authorization did not return a result");
            return;
        }
        if (result.hasResolution()) {
            PendingIntent pending = result.getPendingIntent();
            if (pending == null) {
                callback.onError("Google account chooser is unavailable");
                return;
            }
            pendingAuthCallback = callback;
            try {
                activity.startIntentSenderForResult(pending.getIntentSender(), REQUEST_AUTHORIZE,
                        null, 0, 0, 0, null);
            } catch (IntentSender.SendIntentException e) {
                pendingAuthCallback = null;
                callback.onError(friendly(e));
            }
            return;
        }
        String token = result.getAccessToken();
        if (token == null || token.trim().isEmpty()) {
            callback.onError("Google Drive access token is unavailable");
            return;
        }
        Profile profile = new Profile();
        profile.accessToken = token;
        callback.onReady(profile);
    }

    void revoke(Profile profile, Runnable onDone) {
        RevokeAccessRequest.Builder builder = RevokeAccessRequest.builder().setScopes(SCOPES);
        authorizationClient.revokeAccess(builder.build())
                .addOnCompleteListener(task -> {
                    if (onDone != null) onDone.run();
                });
    }

    static void backup(Activity activity, String token, File libraryDir, File fontsDir,
                       SharedPreferences prefs, SyncCallback callback) {
        new Thread(() -> {
            File archive = null;
            try {
                archive = buildBackup(activity, libraryDir, fontsDir, prefs);
                String fileId = findBackupId(token);
                if (fileId == null) createBackup(token, archive);
                else updateBackup(token, fileId, archive);
                prefs.edit().putLong("google_last_backup_ms", System.currentTimeMillis()).apply();
                activity.runOnUiThread(() -> callback.onSuccess("Google Drive backup is up to date"));
            } catch (Exception e) {
                String message = friendly(e);
                activity.runOnUiThread(() -> callback.onError(message));
            } finally {
                if (archive != null) archive.delete();
            }
        }, "wow-google-backup").start();
    }

    static void smartBackup(Activity activity, String token, File libraryDir, File fontsDir,
                           SharedPreferences prefs, SyncCallback callback) {
        if (prefs.getBoolean("google_use_legacy_zip_sync", false)) {
            legacySmartBackup(activity, token, libraryDir, fontsDir, prefs, callback); return;
        }
        new Thread(() -> {
            File stateArchive = null;
            try {
                ReaderStateDb db = ReaderStateDb.initialize(activity, prefs, libraryDir);
                if (!db.isLibraryIndexReady()) {
                    prefs.edit().putLong("sync_updated_ms", System.currentTimeMillis()).apply();
                    activity.runOnUiThread(() -> callback.onSuccess("Preparing library index for sync"));
                    return;
                }
                int uploaded=0, deleted=0;
                for (ReaderStateDb.PendingSyncRow row : db.pendingBookUploads(SyncBatchPolicy.BOOK_BATCH)) {
                    File file=row.file();
                    if(!file.isFile()) continue;
                    syncOneBook(token, db, prefs, row, file);
                    uploaded++;
                }
                for (ReaderStateDb.TombstoneRow row : db.pendingTombstones(SyncBatchPolicy.DELETE_BATCH)) {
                    deleteOneBook(token, db, row); deleted++;
                }
                int coverSynced=0;
                for (ReaderStateDb.PendingCoverRow row : db.pendingCoverUploads(SyncBatchPolicy.COVER_BATCH)) { syncOneCover(token,db,prefs,row); coverSynced++; }
                int remainingBooks=db.pendingBookUploadCount();
                int remainingDeletes=db.pendingTombstoneCount();
                int remainingCovers=db.pendingCoverUploadCount();
                boolean more=SyncBatchPolicy.hasMore(remainingBooks,remainingDeletes,remainingCovers);
                if (!more) {
                    stateArchive=buildStateBackup(activity,prefs,fontsDir);
                    BackupInfo state=findFileInfo(token,STATE_BACKUP_NAME);
                    if(state==null) createNamedZip(token,STATE_BACKUP_NAME,stateArchive);
                    else updateNamedFile(token,state.id,"application/zip",stateArchive);
                } else {
                    // Force GoogleAutoSync to schedule the next small batch instead of considering the giant bootstrap finished.
                    prefs.edit().putLong("sync_updated_ms",System.currentTimeMillis()).apply();
                }
                prefs.edit().putLong("google_last_backup_ms",System.currentTimeMillis()).apply();
                final int u=uploaded,d=deleted,c=coverSynced,r=remainingBooks+remainingDeletes+remainingCovers;
                activity.runOnUiThread(() -> callback.onSuccess(r>0 ?
                        "Synced "+u+" books · "+c+" covers · "+r+" queued" : "Google Drive incremental sync is up to date"));
            } catch(Exception e) {
                String message=friendly(e); activity.runOnUiThread(() -> callback.onError(message));
            } finally { if(stateArchive!=null)stateArchive.delete(); }
        },"wow-google-incremental-sync").start();
    }

    private static void syncOneBook(String token, ReaderStateDb db, SharedPreferences prefs,
                                    ReaderStateDb.PendingSyncRow row, File file) throws Exception {
        String hash=row.hash;
        if(hash==null||hash.isEmpty()) hash=db.ensureHash(file,prefs);
        if(hash==null||hash.isEmpty()) throw new Exception("Unable to identify "+file.getName());
        String objectName=DriveObjectNamer.bookName(hash,row.format);
        BackupInfo remote=null;
        if(row.remoteId!=null&&!row.remoteId.isEmpty()) { remote=new BackupInfo(); remote.id=row.remoteId; }
        else remote=findFileInfo(token,objectName);
        String session=row.sessionUrl;
        long offset=Math.max(0L,row.offset);
        if(session==null||session.isEmpty()) {
            session=startResumable(token,objectName,file,hash,remote==null?"":remote.id);
            offset=0L; db.saveUploadCheckpoint(row.fileName,session,0L);
        }
        BackupInfo done;
        try { done=uploadResumable(token,file,session,offset,db,row.fileName); }
        catch(ResumableExpiredException expired) {
            session=startResumable(token,objectName,file,hash,remote==null?"":remote.id);
            db.saveUploadCheckpoint(row.fileName,session,0L);
            done=uploadResumable(token,file,session,0L,db,row.fileName);
        }
        String remoteId=done.id;
        if((remoteId==null||remoteId.isEmpty())&&remote!=null)remoteId=remote.id;
        db.markBookSynced(row.fileName,remoteId,done.modifiedTime);
    }

    private static void syncOneCover(String token,ReaderStateDb db,SharedPreferences prefs,ReaderStateDb.PendingCoverRow row)throws Exception{
        String hash=row.hash;File book=new File(row.filePath);if((hash==null||hash.isEmpty())&&book.isFile())hash=db.ensureHash(book,prefs);if(hash==null||hash.isEmpty())throw new Exception("Unable to identify cover book");
        if(row.coverPath==null||row.coverPath.isEmpty()){String id=row.remoteId;if((id==null||id.isEmpty())){BackupInfo i=findFileInfo(token,DriveObjectNamer.coverName(hash));if(i!=null)id=i.id;}if(id!=null&&!id.isEmpty())deleteRemoteFile(token,id);db.markCoverSynced(row.fileName,"");return;}
        File cover=new File(row.coverPath);if(!cover.isFile())throw new Exception("Custom cover file is unavailable");String name=DriveObjectNamer.coverName(hash);BackupInfo remote=null;if(row.remoteId!=null&&!row.remoteId.isEmpty()){remote=new BackupInfo();remote.id=row.remoteId;}else remote=findFileInfo(token,name);
        String session=row.session;long offset=Math.max(0,row.offset);if(session==null||session.isEmpty()){session=startResumable(token,name,cover,hash,remote==null?"":remote.id);offset=0;db.saveCoverUploadCheckpoint(row.fileName,session,0);}
        BackupInfo done;try{done=uploadResumableCover(token,cover,session,offset,db,row.fileName);}catch(ResumableExpiredException x){session=startResumable(token,name,cover,hash,remote==null?"":remote.id);db.saveCoverUploadCheckpoint(row.fileName,session,0);done=uploadResumableCover(token,cover,session,0,db,row.fileName);}String id=done.id;if((id==null||id.isEmpty())&&remote!=null)id=remote.id;db.markCoverSynced(row.fileName,id);
    }
    private static BackupInfo uploadResumableCover(String token,File file,String session,long start,ReaderStateDb db,String fileName)throws Exception{
        long total=file.length(),offset=Math.max(0,Math.min(start,total));try(RandomAccessFile raf=new RandomAccessFile(file,"r")){while(offset<total){int len=(int)Math.min((long)SyncBatchPolicy.CHUNK_BYTES,total-offset);long end=offset+len-1;HttpURLConnection c=open(session,"PUT",token);c.setDoOutput(true);c.setRequestProperty("Content-Type","image/jpeg");c.setRequestProperty("Content-Range","bytes "+offset+"-"+end+"/"+total);c.setFixedLengthStreamingMode(len);raf.seek(offset);byte[] b=new byte[64*1024];int remain=len;try(OutputStream out=new BufferedOutputStream(c.getOutputStream())){while(remain>0){int n=raf.read(b,0,Math.min(b.length,remain));if(n<0)throw new Exception("Unexpected end of cover file");out.write(b,0,n);remain-=n;}}int code=c.getResponseCode();if(code==404||code==410){c.disconnect();throw new ResumableExpiredException();}if(code==308){String range=c.getHeaderField("Range");c.disconnect();offset=parseNextOffset(range,end+1);db.saveCoverUploadCheckpoint(fileName,session,offset);continue;}if(code>=200&&code<300){byte[] data;try(InputStream in=c.getInputStream()){data=readAll(in);}finally{c.disconnect();}BackupInfo info=new BackupInfo();if(data.length>0){JSONObject o=new JSONObject(new String(data,StandardCharsets.UTF_8));info.id=o.optString("id","");info.modifiedTime=o.optString("modifiedTime","");}return info;}c.disconnect();throw new Exception("Google Drive cover upload error "+code);}}return new BackupInfo();
    }
    private static void deleteRemoteFile(String token,String id)throws Exception{HttpURLConnection c=open("https://www.googleapis.com/drive/v3/files/"+id,"DELETE",token);int code=c.getResponseCode();c.disconnect();if(code!=404&&code!=204&&!(code>=200&&code<300))throw new Exception("Google Drive delete error "+code);}
    private static String mimeFor(File file){String n=file==null?"":file.getName().toLowerCase(java.util.Locale.ROOT);if(n.endsWith(".pdf"))return "application/pdf";if(n.endsWith(".jpg")||n.endsWith(".jpeg"))return "image/jpeg";if(n.endsWith(".png"))return "image/png";return "application/epub+zip";}

    private static void deleteOneBook(String token, ReaderStateDb db, ReaderStateDb.TombstoneRow row) throws Exception {
        String id=row.remoteId;
        if((id==null||id.isEmpty())&&row.hash!=null&&!row.hash.isEmpty()) {
            String format=row.fileName!=null&&row.fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")?"pdf":"epub";
            BackupInfo info=findFileInfo(token,DriveObjectNamer.bookName(row.hash,format)); if(info!=null)id=info.id;
        }
        if(id!=null&&!id.isEmpty()) {
            HttpURLConnection c=open("https://www.googleapis.com/drive/v3/files/"+id,"DELETE",token);
            int code=c.getResponseCode(); c.disconnect();
            if(code!=404&&code!=204&&!(code>=200&&code<300)) throw new Exception("Google Drive delete error "+code);
        }
        db.markTombstoneSynced(row.hash);
    }

    private static String startResumable(String token,String name,File file,String hash,String remoteId) throws Exception {
        boolean updating=remoteId!=null&&!remoteId.isEmpty();
        String url=updating ? "https://www.googleapis.com/upload/drive/v3/files/"+remoteId+"?uploadType=resumable&fields=id,modifiedTime" :
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,modifiedTime";
        HttpURLConnection c=open(url,"POST",token);
        if(updating)c.setRequestProperty("X-HTTP-Method-Override","PATCH");
        c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
        c.setRequestProperty("X-Upload-Content-Type", mimeFor(file));
        c.setRequestProperty("X-Upload-Content-Length",Long.toString(file.length())); c.setDoOutput(true);
        JSONObject meta=new JSONObject(); meta.put("name",name);
        if(!updating){JSONArray parents=new JSONArray();parents.put("appDataFolder");meta.put("parents",parents);}
        // No Drive properties/appProperties here. The SHA-256 identity is encoded in the object name.
        byte[] body=meta.toString().getBytes(StandardCharsets.UTF_8); c.setFixedLengthStreamingMode(body.length);
        try(OutputStream out=c.getOutputStream()){out.write(body);}
        try { ensureSuccess(c); }
        catch (Exception e) { c.disconnect(); throw new Exception("Google Drive resumable-session error: " + e.getMessage()); }
        String location=c.getHeaderField("Location"); c.disconnect();
        if(location==null||location.trim().isEmpty())throw new Exception("Google Drive resumable session unavailable");
        return location;
    }

    private static BackupInfo uploadResumable(String token,File file,String session,long start,
                                               ReaderStateDb db,String fileName) throws Exception {
        long total=file.length(); long offset=Math.max(0L,Math.min(start,total));
        try(RandomAccessFile raf=new RandomAccessFile(file,"r")) {
            while(offset<total) {
                int len=(int)Math.min((long)SyncBatchPolicy.CHUNK_BYTES,total-offset); long end=offset+len-1;
                HttpURLConnection c=open(session,"PUT",token); c.setDoOutput(true);
                c.setRequestProperty("Content-Type",mimeFor(file));
                c.setRequestProperty("Content-Range","bytes "+offset+"-"+end+"/"+total); c.setFixedLengthStreamingMode(len);
                raf.seek(offset); byte[] buffer=new byte[64*1024]; int remain=len;
                try(OutputStream out=new BufferedOutputStream(c.getOutputStream())) {
                    while(remain>0){int n=raf.read(buffer,0,Math.min(buffer.length,remain));if(n<0)throw new Exception("Unexpected end of book file");out.write(buffer,0,n);remain-=n;}
                }
                int code=c.getResponseCode();
                if(code==404||code==410){c.disconnect();throw new ResumableExpiredException();}
                if(code==308){String range=c.getHeaderField("Range");c.disconnect();offset=parseNextOffset(range,end+1);db.saveUploadCheckpoint(fileName,session,offset);continue;}
                if(code>=200&&code<300){byte[] data;try(InputStream in=c.getInputStream()){data=readAll(in);}finally{c.disconnect();}BackupInfo info=new BackupInfo();if(data.length>0){JSONObject o=new JSONObject(new String(data,StandardCharsets.UTF_8));info.id=o.optString("id","");info.modifiedTime=o.optString("modifiedTime","");}return info;}
                InputStream err=c.getErrorStream();String detail=err==null?"":new String(readAll(err),StandardCharsets.UTF_8);c.disconnect();throw new Exception("Google Drive upload error "+code+(detail.isEmpty()?"":": "+detail));
            }
        }
        BackupInfo info=new BackupInfo();return info;
    }
    private static long parseNextOffset(String range,long fallback){
        if(range!=null){int dash=range.lastIndexOf('-');if(dash>=0)try{return Long.parseLong(range.substring(dash+1).trim())+1L;}catch(Exception ignored){}}
        return fallback;
    }
    private static final class ResumableExpiredException extends Exception {}

    private static void legacySmartBackup(Activity activity, String token, File libraryDir, File fontsDir,
                           SharedPreferences prefs, SyncCallback callback) {
        new Thread(() -> {
            File remoteArchive = null, remoteStateArchive = null, temp = null, stateTemp = null;
            File mergedArchive = null, stateArchive = null;
            try {
                BackupInfo remote = findFileInfo(token, BACKUP_NAME);
                BackupInfo remoteState = findFileInfo(token, STATE_BACKUP_NAME);
                String seenRemote = prefs.getString("google_last_seen_remote_modified", "");
                String seenState = prefs.getString("google_last_seen_state_modified", "");
                boolean remoteFilesChanged = remote != null && (seenRemote.isEmpty() || !seenRemote.equals(remote.modifiedTime));
                boolean remoteStateChanged = remoteState != null && (seenState.isEmpty() || !seenState.equals(remoteState.modifiedTime));

                if (remoteFilesChanged) {
                    remoteArchive = File.createTempFile("wow-smart-sync-", ".zip", activity.getCacheDir());
                    downloadFile(token, remote.id, remoteArchive);
                    temp = new File(activity.getCacheDir(), "wow_smart_merge_" + System.currentTimeMillis());
                    if (!temp.mkdirs()) throw new Exception("Unable to prepare cloud merge folder");
                    unzipSafely(remoteArchive, temp);
                    mergeMissingFiles(new File(temp, "books"), libraryDir);
                    mergeMissingFiles(new File(temp, "fonts"), fontsDir);
                    CloudMergePolicy.mergePreferences(readPreferenceValues(new File(temp, "state.json")), prefs);
                    File remoteDb = new File(temp, "state/reader.db");
                    if (remoteDb.isFile()) ReaderStateDb.initialize(activity, prefs, libraryDir).mergeSnapshot(remoteDb, prefs);
                    prefs.edit().putLong("library_files_updated_ms", System.currentTimeMillis()).apply();
                }

                if (remoteStateChanged) {
                    remoteStateArchive = File.createTempFile("wow-state-sync-", ".zip", activity.getCacheDir());
                    downloadFile(token, remoteState.id, remoteStateArchive);
                    stateTemp = new File(activity.getCacheDir(), "wow_state_merge_" + System.currentTimeMillis());
                    if (!stateTemp.mkdirs()) throw new Exception("Unable to prepare state merge folder");
                    unzipSafely(remoteStateArchive, stateTemp);
                    CloudMergePolicy.mergePreferences(readPreferenceValues(new File(stateTemp, "state.json")), prefs);
                    File remoteDb = new File(stateTemp, "state/reader.db");
                    if (remoteDb.isFile()) ReaderStateDb.initialize(activity, prefs, libraryDir).mergeSnapshot(remoteDb, prefs);
                }

                long filesRevision = prefs.getLong("library_files_updated_ms", 0L);
                long lastFilesBackupRevision = prefs.getLong("google_last_files_backup_revision_ms", 0L);
                boolean needFull = remote == null || remoteFilesChanged || filesRevision > lastFilesBackupRevision;
                if (needFull) {
                    mergedArchive = buildBackup(activity, libraryDir, fontsDir, prefs);
                    BackupInfo latest = findFileInfo(token, BACKUP_NAME);
                    if (remote == null) {
                        if (latest == null) createNamedZip(token, BACKUP_NAME, mergedArchive);
                        else updateNamedFile(token, latest.id, "application/zip", mergedArchive);
                    } else {
                        if (latest == null || !remote.id.equals(latest.id) || !remote.modifiedTime.equals(latest.modifiedTime))
                            throw new Exception("Cloud library changed during sync; retrying safely");
                        updateNamedFile(token, remote.id, "application/zip", mergedArchive);
                    }
                    prefs.edit().putLong("google_last_files_backup_revision_ms", Math.max(filesRevision, System.currentTimeMillis())).apply();
                }

                // Reading progress/settings/state are small and sync separately, so page progress never rebuilds every EPUB/PDF.
                stateArchive = buildStateBackup(activity, prefs, fontsDir);
                BackupInfo latestState = findFileInfo(token, STATE_BACKUP_NAME);
                if (latestState == null) createNamedZip(token, STATE_BACKUP_NAME, stateArchive);
                else updateNamedFile(token, latestState.id, "application/zip", stateArchive);

                BackupInfo updated = findFileInfo(token, BACKUP_NAME);
                BackupInfo updatedState = findFileInfo(token, STATE_BACKUP_NAME);
                SharedPreferences.Editor done = prefs.edit().putLong("google_last_backup_ms", System.currentTimeMillis());
                if (updated != null) done.putString("google_last_seen_remote_modified", updated.modifiedTime);
                if (updatedState != null) done.putString("google_last_seen_state_modified", updatedState.modifiedTime);
                done.apply();
                activity.runOnUiThread(() -> callback.onSuccess("Google Drive smart sync is up to date"));
            } catch (Exception e) {
                String message = friendly(e);
                activity.runOnUiThread(() -> callback.onError(message));
            } finally {
                if (remoteArchive != null) remoteArchive.delete();
                if (remoteStateArchive != null) remoteStateArchive.delete();
                if (mergedArchive != null) mergedArchive.delete();
                if (stateArchive != null) stateArchive.delete();
                deleteRecursively(temp); deleteRecursively(stateTemp);
            }
        }, "wow-google-smart-sync").start();
    }

    static void restore(Activity activity, String token, File libraryDir, File fontsDir,
                        SharedPreferences prefs, SyncCallback callback) {
        new Thread(() -> {
            File archive = null;
            File temp = null;
            boolean foundAny = false;
            int restoredBooks = 0, restoredCovers = 0;
            try {
                if (!libraryDir.exists() && !libraryDir.mkdirs()) throw new Exception("Unable to prepare library folder");
                if (!fontsDir.exists() && !fontsDir.mkdirs()) throw new Exception("Unable to prepare fonts folder");

                // Legacy ZIP remains a migration/fallback source, but is no longer required.
                BackupInfo legacy = findBackupInfo(token);
                if (legacy != null) {
                    foundAny = true;
                    archive = File.createTempFile("wow-drive-restore-", ".zip", activity.getCacheDir());
                    downloadBackup(token, legacy.id, archive);
                    temp = new File(activity.getCacheDir(), "wow_restore_" + System.currentTimeMillis());
                    if (!temp.mkdirs()) throw new Exception("Unable to prepare restore folder");
                    unzipSafely(archive, temp);
                    restoreFiles(new File(temp, "books"), libraryDir);
                    restoreFiles(new File(temp, "fonts"), fontsDir);
                    restorePreferences(new File(temp, "state.json"), prefs);
                    File fullDb = new File(temp, "state/reader.db");
                    if (fullDb.isFile()) ReaderStateDb.initialize(activity, prefs, libraryDir).mergeSnapshot(fullDb, prefs);
                }

                // The state ZIP is authoritative for the current incremental format.
                BackupInfo stateInfo = findFileInfo(token, STATE_BACKUP_NAME);
                if (stateInfo != null) {
                    foundAny = true;
                    restoreStateArchive(activity, token, stateInfo, libraryDir, fontsDir, prefs);
                }

                ReaderStateDb db = ReaderStateDb.initialize(activity, prefs, libraryDir);
                // One-time restore may be O(N); normal startup/sync remains paged and incremental.
                restoredBooks = restoreIncrementalBooks(token, activity, db, prefs, libraryDir);
                restoredCovers = restoreIncrementalCovers(token, activity, db);
                if (restoredBooks > 0 || restoredCovers > 0) foundAny = true;

                // Rebind legacy-restored files to this device's local path after remote DB merge.
                File[] local = libraryDir.listFiles();
                if (local != null) for (File book : local) {
                    if (book == null || !book.isFile()) continue;
                    String lower = book.getName().toLowerCase(java.util.Locale.ROOT);
                    if (!lower.endsWith(".epub") && !lower.endsWith(".pdf")) continue;
                    String hash = db.contentHash(book.getName());
                    if (hash != null && !hash.isEmpty()) db.recordRestoredBook(book, hash, "", prefs);
                }

                if (!foundAny) throw new Exception("No WoW Reader backup was found in this Google Drive");
                prefs.edit()
                        .putLong("library_files_updated_ms", System.currentTimeMillis())
                        .putLong("google_last_backup_ms", System.currentTimeMillis())
                        .putLong("sync_updated_ms", System.currentTimeMillis())
                        .apply();
                final int books = restoredBooks, covers = restoredCovers;
                activity.runOnUiThread(() -> callback.onSuccess(
                        books > 0 || covers > 0
                                ? "Restored " + books + " cloud books · " + covers + " custom covers · reading data"
                                : "Books, notes and reading data restored"));
            } catch (Exception e) {
                String message = friendly(e);
                activity.runOnUiThread(() -> callback.onError(message));
            } finally {
                if (archive != null) archive.delete();
                deleteRecursively(temp);
            }
        }, "wow-google-restore").start();
    }

    private static void restoreStateArchive(Activity activity, String token, BackupInfo stateInfo,
                                            File libraryDir, File fontsDir, SharedPreferences prefs) throws Exception {
        File stateZip = File.createTempFile("wow-state-restore-", ".zip", activity.getCacheDir());
        File stateDir = new File(activity.getCacheDir(), "wow_state_restore_" + System.currentTimeMillis());
        try {
            downloadFile(token, stateInfo.id, stateZip);
            if (!stateDir.mkdirs()) throw new Exception("Unable to prepare state restore folder");
            unzipSafely(stateZip, stateDir);
            restorePreferences(new File(stateDir, "state.json"), prefs);
            restoreFiles(new File(stateDir, "fonts"), fontsDir);
            File stateDb = new File(stateDir, "state/reader.db");
            if (stateDb.isFile()) ReaderStateDb.initialize(activity, prefs, libraryDir).mergeSnapshot(stateDb, prefs);
        } finally {
            stateZip.delete();
            deleteRecursively(stateDir);
        }
    }

    private static int restoreIncrementalBooks(String token, Activity activity, ReaderStateDb db,
                                               SharedPreferences prefs, File libraryDir) throws Exception {
        int restored = 0;
        String pageToken = "";
        do {
            JSONObject page = listIncrementalObjects(token, "wow_book_", pageToken);
            JSONArray files = page.optJSONArray("files");
            if (files != null) for (int i = 0; i < files.length(); i++) {
                JSONObject remote = files.optJSONObject(i);
                if (remote == null) continue;
                String id = remote.optString("id", "");
                String remoteName = remote.optString("name", "");
                DriveRestorePlanner.BookObject object = DriveRestorePlanner.parseBookObject(remoteName);
                if (id.isEmpty() || object == null) continue;

                String preferred = db.fileNameForHash(object.hash);
                String localName = DriveRestorePlanner.safeLocalName(preferred, object);
                File destination = chooseRestoreDestination(libraryDir, localName, object);
                if (destination.isFile()) {
                    try {
                        String existingHash = FileIdentityUtil.sha256(destination);
                        if (object.hash.equalsIgnoreCase(existingHash)) {
                            db.recordRestoredBook(destination, object.hash, id, prefs);
                            continue;
                        }
                    } catch (Exception ignored) {}
                    destination = new File(libraryDir, object.hash + "-cloud." + object.extension);
                }

                File scratch = File.createTempFile("wow-book-restore-", "." + object.extension, activity.getCacheDir());
                try {
                    downloadFile(token, id, scratch);
                    String actual = FileIdentityUtil.sha256(scratch);
                    if (!object.hash.equalsIgnoreCase(actual))
                        throw new Exception("Cloud book integrity check failed: " + remoteName);
                    installRestoredFile(scratch, destination);
                    db.recordRestoredBook(destination, object.hash, id, prefs);
                    restored++;
                } finally {
                    scratch.delete();
                }
            }
            pageToken = page.optString("nextPageToken", "");
        } while (!pageToken.isEmpty());
        return restored;
    }

    private static int restoreIncrementalCovers(String token, Activity activity, ReaderStateDb db) throws Exception {
        int restored = 0;
        File coverDir = new File(activity.getFilesDir(), "custom_covers");
        if (!coverDir.exists() && !coverDir.mkdirs()) throw new Exception("Unable to prepare custom cover folder");
        String pageToken = "";
        do {
            JSONObject page = listIncrementalObjects(token, "wow_cover_", pageToken);
            JSONArray files = page.optJSONArray("files");
            if (files != null) for (int i = 0; i < files.length(); i++) {
                JSONObject remote = files.optJSONObject(i);
                if (remote == null) continue;
                String id = remote.optString("id", "");
                String hash = DriveRestorePlanner.parseCoverHash(remote.optString("name", ""));
                if (id.isEmpty() || hash.isEmpty() || db.fileNameForHash(hash).isEmpty()) continue;
                File destination = new File(coverDir, "cloud_" + hash.substring(0, 24) + ".jpg");
                File scratch = File.createTempFile("wow-cover-restore-", ".jpg", activity.getCacheDir());
                try {
                    downloadFile(token, id, scratch);
                    android.graphics.Bitmap preview = CustomCoverStore.decodeSampled(scratch, 120, 180);
                    if (preview == null) continue;
                    preview.recycle();
                    installRestoredFile(scratch, destination);
                    db.recordRestoredCover(hash, destination.getAbsolutePath(), id);
                    restored++;
                } finally {
                    scratch.delete();
                }
            }
            pageToken = page.optString("nextPageToken", "");
        } while (!pageToken.isEmpty());
        return restored;
    }

    private static JSONObject listIncrementalObjects(String token, String prefix, String pageToken) throws Exception {
        String q = "trashed=false and name contains '" + prefix.replace("'", "\\'") + "'";
        String url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&pageSize=1000" +
                "&orderBy=name&fields=nextPageToken,files(id,name,size,modifiedTime)&q=" + URLEncoder.encode(q, "UTF-8");
        if (pageToken != null && !pageToken.isEmpty()) url += "&pageToken=" + URLEncoder.encode(pageToken, "UTF-8");
        return authorizedJson(url, token);
    }

    private static File chooseRestoreDestination(File libraryDir, String localName, DriveRestorePlanner.BookObject object) {
        File candidate = new File(libraryDir, localName);
        try {
            String root = libraryDir.getCanonicalPath() + File.separator;
            if (!candidate.getCanonicalPath().startsWith(root))
                return new File(libraryDir, object.hash + "." + object.extension);
        } catch (Exception ignored) {
            return new File(libraryDir, object.hash + "." + object.extension);
        }
        return candidate;
    }

    private static void installRestoredFile(File source, File destination) throws Exception {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new Exception("Unable to prepare restore destination");
        File part = new File(destination.getAbsolutePath() + ".restore");
        if (part.exists() && !part.delete()) throw new Exception("Unable to replace restore temp file");
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             FileOutputStream fos = new FileOutputStream(part);
             OutputStream out = new BufferedOutputStream(fos)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            out.flush();
            fos.getFD().sync();
        }
        if (destination.exists() && !destination.delete()) {
            part.delete();
            throw new Exception("Unable to replace restored file");
        }
        if (!part.renameTo(destination)) {
            part.delete();
            throw new Exception("Unable to finalize restored file");
        }
    }

    static void hasBackup(Activity activity, String token, BackupCheckCallback callback) {
        new Thread(() -> {
            boolean found = false;
            try {
                found = findBackupId(token) != null || findFileInfo(token, STATE_BACKUP_NAME) != null || hasIncrementalBook(token);
            } catch (Exception ignored) {}
            final boolean value = found;
            activity.runOnUiThread(() -> callback.onResult(value));
        }, "wow-google-backup-check").start();
    }

    private static boolean hasIncrementalBook(String token) throws Exception {
        String q = "trashed=false and name contains 'wow_book_'";
        String url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&pageSize=10&fields=files(name)&q=" +
                URLEncoder.encode(q, "UTF-8");
        JSONArray files = authorizedJson(url, token).optJSONArray("files");
        if (files == null) return false;
        for (int i=0;i<files.length();i++) {
            JSONObject item=files.optJSONObject(i);
            if(item!=null && DriveRestorePlanner.parseBookObject(item.optString("name",""))!=null) return true;
        }
        return false;
    }

    private static File buildBackup(Activity activity, File libraryDir, File fontsDir,
                                    SharedPreferences prefs) throws Exception {
        File out = File.createTempFile("wow-reader-backup-", ".zip", activity.getCacheDir());
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            addDirectory(zip, libraryDir, "books/");
            addDirectory(zip, fontsDir, "fonts/");
            byte[] state = exportPreferences(prefs).toString().getBytes(StandardCharsets.UTF_8);
            ZipEntry entry = new ZipEntry("state.json");
            zip.putNextEntry(entry);
            zip.write(state);
            zip.closeEntry();
            ReaderStateDb db = ReaderStateDb.initialize(activity, prefs, libraryDir);
            File dbFile = db.databaseFile(activity);
            if (dbFile != null && dbFile.isFile()) addFile(zip, dbFile, "state/reader.db");
        }
        return out;
    }

    private static File buildStateBackup(Activity activity, SharedPreferences prefs, File fontsDir) throws Exception {
        File out = File.createTempFile("wow-reader-state-", ".zip", activity.getCacheDir());
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            byte[] state = exportPreferences(prefs).toString().getBytes(StandardCharsets.UTF_8);
            ZipEntry entry = new ZipEntry("state.json");
            zip.putNextEntry(entry); zip.write(state); zip.closeEntry();
            ReaderStateDb db = ReaderStateDb.initialize(activity, prefs, new File(activity.getFilesDir(), "library"));
            File dbFile = db.databaseFile(activity);
            if (dbFile != null && dbFile.isFile()) addFile(zip, dbFile, "state/reader.db");
            addDirectory(zip, fontsDir, "fonts/");
        }
        return out;
    }

    private static JSONObject exportPreferences(SharedPreferences prefs) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", 1);
        root.put("created_ms", System.currentTimeMillis());
        JSONObject values = new JSONObject();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (key == null || key.startsWith("google_account_") || key.startsWith("google_sync_")) continue;
            Object value = entry.getValue();
            JSONObject item = new JSONObject();
            if (value instanceof String) { item.put("t", "s"); item.put("v", value); }
            else if (value instanceof Integer) { item.put("t", "i"); item.put("v", value); }
            else if (value instanceof Long) { item.put("t", "l"); item.put("v", value); }
            else if (value instanceof Float) { item.put("t", "f"); item.put("v", value); }
            else if (value instanceof Boolean) { item.put("t", "b"); item.put("v", value); }
            else if (value instanceof Set) {
                item.put("t", "ss");
                JSONArray arr = new JSONArray();
                for (Object s : (Set<?>) value) if (s != null) arr.put(String.valueOf(s));
                item.put("v", arr);
            } else continue;
            values.put(key, item);
        }
        root.put("prefs", values);
        return root;
    }

    private static void restorePreferences(File stateFile, SharedPreferences prefs) throws Exception {
        if (stateFile == null || !stateFile.isFile()) return;
        String json = new String(readAll(new FileInputStream(stateFile)), StandardCharsets.UTF_8);
        JSONObject values = new JSONObject(json).optJSONObject("prefs");
        if (values == null) return;
        SharedPreferences.Editor edit = prefs.edit();
        java.util.Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.startsWith("google_account_") || key.startsWith("google_sync_")) continue;
            JSONObject item = values.optJSONObject(key);
            if (item == null) continue;
            String t = item.optString("t", "");
            if ("s".equals(t)) edit.putString(key, item.optString("v", ""));
            else if ("i".equals(t)) edit.putInt(key, item.optInt("v", 0));
            else if ("l".equals(t)) edit.putLong(key, item.optLong("v", 0L));
            else if ("f".equals(t)) edit.putFloat(key, (float) item.optDouble("v", 0));
            else if ("b".equals(t)) edit.putBoolean(key, item.optBoolean("v", false));
            else if ("ss".equals(t)) {
                JSONArray arr = item.optJSONArray("v");
                java.util.HashSet<String> set = new java.util.HashSet<>();
                if (arr != null) for (int i = 0; i < arr.length(); i++) set.add(arr.optString(i, ""));
                edit.putStringSet(key, set);
            }
        }
        edit.apply();
    }

    private static void addFile(ZipOutputStream zip, File file, String entryName) throws Exception {
        if (file == null || !file.isFile()) return;
        ZipEntry entry = new ZipEntry(entryName); zip.putNextEntry(entry);
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024]; int n; while ((n = in.read(buffer)) > 0) zip.write(buffer, 0, n);
        }
        zip.closeEntry();
    }

    private static void addDirectory(ZipOutputStream zip, File dir, String prefix) throws Exception {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        byte[] buffer = new byte[64 * 1024];
        for (File file : files) {
            if (!file.isFile()) continue;
            ZipEntry entry = new ZipEntry(prefix + file.getName());
            zip.putNextEntry(entry);
            try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                int n;
                while ((n = in.read(buffer)) > 0) zip.write(buffer, 0, n);
            }
            zip.closeEntry();
        }
    }

    private static JSONObject readPreferenceValues(File stateFile) throws Exception {
        if (stateFile == null || !stateFile.isFile()) return new JSONObject();
        String json = new String(readAll(new FileInputStream(stateFile)), StandardCharsets.UTF_8);
        JSONObject values = new JSONObject(json).optJSONObject("prefs");
        return values == null ? new JSONObject() : values;
    }

    private static void mergeMissingFiles(File source, File destination) throws Exception {
        if (source == null || !source.isDirectory()) return;
        if (!destination.exists() && !destination.mkdirs())
            throw new Exception("Unable to create cloud merge destination");
        File[] files = source.listFiles();
        if (files == null) return;
        byte[] buffer = new byte[64 * 1024];
        for (File file : files) {
            if (!file.isFile()) continue;
            File out = new File(destination, file.getName());
            if (out.exists()) continue;
            try (InputStream in = new BufferedInputStream(new FileInputStream(file));
                 OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                int n;
                while ((n = in.read(buffer)) > 0) os.write(buffer, 0, n);
            }
        }
    }

    private static void restoreFiles(File source, File destination) throws Exception {
        if (source == null || !source.isDirectory()) return;
        if (!destination.exists() && !destination.mkdirs()) throw new Exception("Unable to create restore destination");
        File[] files = source.listFiles();
        if (files == null) return;
        byte[] buffer = new byte[64 * 1024];
        for (File file : files) {
            if (!file.isFile()) continue;
            File out = new File(destination, file.getName());
            try (InputStream in = new BufferedInputStream(new FileInputStream(file));
                 OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                int n;
                while ((n = in.read(buffer)) > 0) os.write(buffer, 0, n);
            }
        }
    }

    private static void unzipSafely(File zipFile, File destination) throws Exception {
        String root = destination.getCanonicalPath() + File.separator;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                File out = new File(destination, entry.getName());
                if (!out.getCanonicalPath().startsWith(root)) throw new Exception("Unsafe backup archive");
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        int n;
                        while ((n = zip.read(buffer)) > 0) os.write(buffer, 0, n);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static final class BackupInfo {
        String id = "";
        String modifiedTime = "";
    }

    private static BackupInfo findBackupInfo(String token) throws Exception { return findFileInfo(token, BACKUP_NAME); }

    private static BackupInfo findFileInfo(String token, String name) throws Exception {
        String q = "name='" + name.replace("'", "\'") + "' and trashed=false";
        String url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&pageSize=10&orderBy=modifiedTime%20desc&fields=files(id,name,modifiedTime)&q=" +
                URLEncoder.encode(q, "UTF-8");
        JSONObject result = authorizedJson(url, token);
        JSONArray files = result.optJSONArray("files");
        if (files == null || files.length() == 0) return null;
        JSONObject first = files.optJSONObject(0);
        if (first == null) return null;
        BackupInfo info = new BackupInfo();
        info.id = first.optString("id", "");
        info.modifiedTime = first.optString("modifiedTime", "");
        return info.id.isEmpty() ? null : info;
    }

    private static String findBackupId(String token) throws Exception {
        BackupInfo info = findBackupInfo(token);
        return info == null ? null : info.id;
    }

    private static void createBackup(String token, File archive) throws Exception { createNamedZip(token, BACKUP_NAME, archive); }

    private static void updateBackup(String token, String id, File archive) throws Exception { updateNamedFile(token, id, "application/zip", archive); }

    private static void downloadBackup(String token, String id, File destination) throws Exception { downloadFile(token, id, destination); }

    private static void createNamedZip(String token, String name, File archive) throws Exception {
        String boundary = "wowreader_" + System.currentTimeMillis();
        HttpURLConnection c = open("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", "POST", token);
        c.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary); c.setDoOutput(true);
        try (OutputStream out = new BufferedOutputStream(c.getOutputStream())) {
            String metadata = "{\"name\":" + JSONObject.quote(name) + ",\"parents\":[\"appDataFolder\"],\"mimeType\":\"application/zip\"}";
            writeUtf8(out, "--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + metadata + "\r\n");
            writeUtf8(out, "--" + boundary + "\r\nContent-Type: application/zip\r\n\r\n"); copy(new FileInputStream(archive), out);
            writeUtf8(out, "\r\n--" + boundary + "--\r\n");
        }
        ensureSuccess(c); c.disconnect();
    }

    private static void updateNamedFile(String token, String id, String mime, File file) throws Exception {
        HttpURLConnection c = open("https://www.googleapis.com/upload/drive/v3/files/" + id + "?uploadType=media&fields=id", "POST", token);
        c.setRequestProperty("X-HTTP-Method-Override", "PATCH"); c.setRequestProperty("Content-Type", mime); c.setDoOutput(true);
        try (OutputStream out = new BufferedOutputStream(c.getOutputStream())) { copy(new FileInputStream(file), out); }
        ensureSuccess(c); c.disconnect();
    }

    private static void downloadFile(String token, String id, File destination) throws Exception {
        HttpURLConnection c = open("https://www.googleapis.com/drive/v3/files/" + id + "?alt=media", "GET", token); ensureSuccess(c);
        try (InputStream in = new BufferedInputStream(c.getInputStream()); OutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) { copy(in, out); }
        c.disconnect();
    }

    private static JSONObject authorizedJson(String url, String token) throws Exception {
        HttpURLConnection c = open(url, "GET", token);
        ensureSuccess(c);
        byte[] data;
        try (InputStream in = new BufferedInputStream(c.getInputStream())) { data = readAll(in); }
        c.disconnect();
        return new JSONObject(new String(data, StandardCharsets.UTF_8));
    }

    private static HttpURLConnection open(String url, String method, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20_000);
        c.setReadTimeout(120_000);
        c.setUseCaches(false);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/json");
        return c;
    }

    private static void ensureSuccess(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        if (code >= 200 && code < 300) return;
        InputStream err = c.getErrorStream();
        String detail = err == null ? "" : new String(readAll(err), StandardCharsets.UTF_8);
        if (detail.length() > 500) detail = detail.substring(0, 500);
        throw new Exception("Google Drive error " + code + (detail.isEmpty() ? "" : ": " + detail));
    }

    private static byte[] readAll(InputStream in) throws Exception {
        try (InputStream source = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[32 * 1024];
            int n;
            while ((n = source.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        try (InputStream source = in) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = source.read(buffer)) > 0) out.write(buffer, 0, n);
        }
    }

    private static void writeUtf8(OutputStream out, String value) throws Exception {
        out.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    private static String friendly(Throwable e) {
        if (e == null) return "Google Drive sync failed";
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? "Google Drive sync failed" : m.trim();
    }
}
