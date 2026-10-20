package com.whisper.wowreader;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.accounts.Account;

import com.google.android.gms.auth.api.identity.AuthorizationClient;
import com.google.android.gms.auth.api.identity.ClearTokenRequest;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.TimeUnit;
import java.util.LinkedHashMap;
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
import java.util.concurrent.atomic.AtomicBoolean;

final class GoogleDriveSync {
    static final int REQUEST_AUTHORIZE = 4104;
    private static final String BACKUP_NAME = "wow_reader_backup_v1.zip";
    private static final String STATE_BACKUP_NAME = "wow_reader_state_v2.zip";
    // State is metadata, but may legitimately contain a very large library. Bound both
    // compressed and expanded input so a corrupt Drive object cannot exhaust the process.
    private static final long MAX_STATE_ZIP_BYTES = 128L * 1024L * 1024L;
    private static final long MAX_STATE_UNCOMPRESSED_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_STATE_JSON_BYTES = 96L * 1024L * 1024L;
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
        default void onCancelled() { onError("Google Drive operation cancelled"); }
        default void onProgress(String stage, String item, int completed, int total, long bytes, long totalBytes) { }
    }
    static final class SyncCancelledException extends Exception {
        SyncCancelledException() { super("Google Drive operation cancelled"); }
    }
    private static final class Operation {
        final SyncCallback callback; volatile boolean cancelled; Thread thread;
        final AtomicBoolean completed = new AtomicBoolean(false);
        Operation(SyncCallback callback) { this.callback = callback; }
        void cancel() { cancelled = true; if (thread != null) thread.interrupt(); }
    }
    private static volatile Operation activeOperation;
    private static volatile GoogleDriveSync pendingAuthOwner;
    private static final ThreadLocal<Operation> currentOperation = new ThreadLocal<>();
    private static synchronized Operation beginOperation(SyncCallback callback) {
        Operation old = activeOperation;
        if (old != null && !old.completed.get()) return null;
        Operation op = new Operation(callback); activeOperation = op; return op;
    }
    private static synchronized void finishOperation(Operation op) {
        if (op != null) op.completed.set(true);
        if (activeOperation == op) activeOperation = null;
        currentOperation.remove();
    }
    static boolean cancelActive() {
        Operation op=activeOperation;
        if (op != null && !op.completed.get()) { op.cancel(); return true; }
        GoogleDriveSync auth = pendingAuthOwner;
        if (auth != null) { auth.cancelAuth(); return true; }
        return false;
    }
    private static void checkCancelled() throws SyncCancelledException {
        Operation op=activeOperation;
        if ((op!=null && op.cancelled) || Thread.currentThread().isInterrupted()) throw new SyncCancelledException();
    }
    static void checkCancelledForTransfer() throws Exception {
        Operation op = activeOperation;
        if (op != null && op.cancelled) throw new SyncCancelledException();
    }
    private static void progress(String stage,String item,int completed,int total,long bytes,long totalBytes) {
        Operation op=currentOperation.get(); if (op == null) op=activeOperation;
        if(op!=null && !op.completed.get() && op.callback!=null) op.callback.onProgress(stage,item,completed,total,bytes,totalBytes);
    }
    private static void deliverCancelled(Activity activity, Operation op) {
        if (op.completed.compareAndSet(false, true) && op.callback != null)
            activity.runOnUiThread(op.callback::onCancelled);
    }
    private static void deliverError(Activity activity, Operation op, String message) {
        if (op.completed.compareAndSet(false, true) && op.callback != null)
            activity.runOnUiThread(() -> op.callback.onError(message));
    }
    private static void deliverSuccess(Activity activity, Operation op, String message) {
        if (op.cancelled) { deliverCancelled(activity, op); return; }
        if (op.completed.compareAndSet(false, true) && op.callback != null)
            activity.runOnUiThread(() -> op.callback.onSuccess(message));
    }

    interface BackupCheckCallback {
        void onResult(boolean found);
    }

    // Only a small in-memory credential registry; no tokens in preferences, logs or backups.
    private static final Map<String, DriveAuthRetry> driveCredentials = new LinkedHashMap<>();
    private static synchronized DriveAuthRetry credentials(String originalToken) {
        return driveCredentials.get(originalToken);
    }
    private void rememberCredentials(String token, String email) {
        final android.content.Context context = activity.getApplicationContext();
        final String accountEmail = email == null ? "" : email.trim();
        DriveAuthRetry session = new DriveAuthRetry(token, rejected -> {
            checkCancelled();
            String connected = context.getSharedPreferences("wow_reader", Activity.MODE_PRIVATE)
                    .getString("google_account_email", "").trim();
            if (accountEmail.isEmpty() || !accountEmail.equals(connected))
                throw new IllegalStateException("Google Drive account changed; reconnect and retry. Local data is preserved.");
            AuthorizationClient client = Identity.getAuthorizationClient(context);
            try {
                Tasks.await(client.clearToken(ClearTokenRequest.builder().setToken(rejected).build()), 60, TimeUnit.SECONDS);
                checkCancelled();
                AuthorizationResult fresh = Tasks.await(client.authorize(AuthorizationRequest.builder()
                        .setAccount(new Account(accountEmail, "com.google")).setRequestedScopes(SCOPES).build()), 60, TimeUnit.SECONDS);
                checkCancelled();
                if (fresh == null || fresh.hasResolution())
                    throw new IllegalStateException("Google Drive needs reconnect; reconnect Google Drive and retry. Local data is preserved.");
                return fresh.getAccessToken();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw e;
            } catch (SyncCancelledException e) { throw e;
            } catch (Exception e) {
                // SDK errors can contain account/token details. Keep this recovery message safe.
                throw new IllegalStateException("Google Drive authorization could not refresh; reconnect Google Drive and retry. Local data is preserved.");
            }
        });
        synchronized (GoogleDriveSync.class) {
            driveCredentials.put(token, session);
            while (driveCredentials.size() > 4) driveCredentials.remove(driveCredentials.keySet().iterator().next());
        }
    }
    private static <T> T withDriveAuth(String token, DriveAuthRetry.Request<T> request) throws Exception {
        DriveAuthRetry session = credentials(token);
        return session == null ? request.run() : session.execute(() -> { checkCancelled(); return request.run(); });
    }
    private static final Map<HttpURLConnection, String> requestTokens = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static int responseCode(HttpURLConnection connection) throws Exception {
        int code = connection.getResponseCode();
        if (code == 401) {
            String rejected = requestTokens.get(connection);
            connection.disconnect();
            throw new DriveAuthRetry.Unauthorized(rejected == null ? "" : rejected);
        }
        return code;
    }

    private final Activity activity;
    private final AuthorizationClient authorizationClient;
    private AuthCallback pendingAuthCallback;
    private volatile boolean authCancelled;
    private final AtomicBoolean authCallbackCompleted = new AtomicBoolean(false);

    private void beginAuth(AuthCallback callback) {
        synchronized (this) {
            authCancelled = false;
            authCallbackCompleted.set(false);
            pendingAuthCallback = callback;
            pendingAuthOwner = this;
        }
    }
    private void completeAuthError(String message) {
        AuthCallback callback;
        synchronized (this) {
            if (!authCallbackCompleted.compareAndSet(false, true)) return;
            callback = pendingAuthCallback;
            pendingAuthCallback = null;
            if (pendingAuthOwner == this) pendingAuthOwner = null;
        }
        if (callback != null) callback.onError(message);
    }
    private void completeAuthReady(Profile profile, AuthCallback callback) {
        synchronized (this) {
            if (!authCallbackCompleted.compareAndSet(false, true)) return;
            pendingAuthCallback = null;
            if (pendingAuthOwner == this) pendingAuthOwner = null;
        }
        callback.onReady(profile);
    }
    private void cancelAuth() {
        authCancelled = true;
        completeAuthError("Google account connection was cancelled");
    }

    GoogleDriveSync(Activity activity) {
        this.activity = activity;
        this.authorizationClient = Identity.getAuthorizationClient(activity);
    }

    void authorize(boolean chooseAccount, AuthCallback callback) {
        beginAuth(callback);
        AuthorizationRequest.Builder builder = AuthorizationRequest.builder()
                .setRequestedScopes(SCOPES);
        // A normal reconnect must stay on the account the user connected. The explicit
        // chooser path intentionally omits setAccount so Google can present account choice.
        if (!chooseAccount) {
            String email = activity.getSharedPreferences("wow_reader", Activity.MODE_PRIVATE)
                    .getString("google_account_email", "").trim();
            if (!email.isEmpty()) builder.setAccount(new Account(email, "com.google"));
        }
        AuthorizationRequest request = builder.build();
        authorizationClient.authorize(request)
                .addOnSuccessListener(result -> { if (authCancelled) { completeAuthError("Google account connection was cancelled"); return; } handleAuthorizationResult(result, callback); })
                .addOnFailureListener(e -> { if (!authCancelled) completeAuthError(friendly(e)); });
    }

    void authorizeSilently(AuthCallback callback) {
        beginAuth(callback);
        AuthorizationRequest.Builder builder = AuthorizationRequest.builder()
                .setRequestedScopes(SCOPES);
        String email = activity.getSharedPreferences("wow_reader", Activity.MODE_PRIVATE)
                .getString("google_account_email", "").trim();
        if (!email.isEmpty()) builder.setAccount(new Account(email, "com.google"));
        AuthorizationRequest request = builder.build();
        authorizationClient.authorize(request)
                .addOnSuccessListener(result -> {
                    if (authCancelled) { completeAuthError("Google account connection was cancelled"); return; }
                    if (result != null && result.hasResolution()) {
                        completeAuthError("Google account needs reconnect");
                        return;
                    }
                    handleAuthorizationResult(result, callback);
                })
                .addOnFailureListener(e -> { if (!authCancelled) completeAuthError(friendly(e)); });
    }

    boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_AUTHORIZE) return false;
        AuthCallback callback = pendingAuthCallback;
        if (callback == null || authCallbackCompleted.get()) return true;
        if (authCancelled) { completeAuthError("Google account connection was cancelled"); return true; }
        if (resultCode != Activity.RESULT_OK || data == null) {
            completeAuthError("Google account connection was cancelled");
            return true;
        }
        try {
            AuthorizationResult result = authorizationClient.getAuthorizationResultFromIntent(data);
            handleAuthorizationResult(result, callback);
        } catch (Exception e) {
            completeAuthError(friendly(e));
        }
        return true;
    }
    private void handleAuthorizationResult(AuthorizationResult result, AuthCallback callback) {
        if (authCancelled || authCallbackCompleted.get()) {
            if (authCancelled) completeAuthError("Google account connection was cancelled");
            return;
        }
        if (result == null) { completeAuthError("Google authorization did not return a result"); return; }
        if (result.hasResolution()) {
            PendingIntent pending = result.getPendingIntent();
            if (pending == null) { completeAuthError("Google account chooser is unavailable"); return; }
            pendingAuthCallback = callback;
            try {
                activity.startIntentSenderForResult(pending.getIntentSender(), REQUEST_AUTHORIZE,
                        null, 0, 0, 0, null);
            } catch (IntentSender.SendIntentException e) {
                completeAuthError(friendly(e));
            }
            return;
        }
        String token = result.getAccessToken();
        if (token == null || token.trim().isEmpty()) { completeAuthError("Google Drive access token is unavailable"); return; }
        Profile profile = new Profile();
        profile.accessToken = token;
        SharedPreferences p = activity.getSharedPreferences("wow_reader", Activity.MODE_PRIVATE);
        profile.email = p.getString("google_account_email", "");
        profile.name = p.getString("google_account_name", "Google account");
        profile.uid = p.getString("google_account_uid", "");
        profile.picture = p.getString("google_account_picture", "");
        rememberCredentials(token, profile.email);
        completeAuthReady(profile, callback);
    }

    void revoke(Profile profile, Runnable onDone) {
        RevokeAccessRequest.Builder builder = RevokeAccessRequest.builder().setScopes(SCOPES);
        authorizationClient.revokeAccess(builder.build())
                .addOnCompleteListener(task -> {
                    if (onDone != null) onDone.run();
                });
    }

    private static ReaderStateDb readyDatabase(Activity activity, SharedPreferences prefs, File libraryDir) {
        ReaderStateDb db = ReaderStateDb.initialize(activity, prefs, libraryDir);
        db.awaitReady();
        return db;
    }

    static void backup(Activity activity, String token, File libraryDir, File fontsDir,
                       SharedPreferences prefs, SyncCallback callback) {
        final Operation operation = beginOperation(callback);
        if (operation == null) { activity.runOnUiThread(() -> callback.onError("Google Drive operation already running")); return; }
        Thread worker = new Thread(() -> {
            currentOperation.set(operation); File archive = null;
            try {
                checkCancelled(); requireCompleteRestore(prefs); progress("preparing", "Preparing backup", 0, 0, 0, 0);
                archive = buildBackup(activity, libraryDir, fontsDir, prefs); checkCancelled();
                progress("upload", "Uploading backup", 0, 1, 0, archive.length());
                String fileId = findBackupId(token); if (fileId == null) createBackup(token, archive); else updateBackup(token, fileId, archive);
                prefs.edit().putLong("google_last_backup_ms", System.currentTimeMillis()).apply();
                deliverSuccess(activity, operation, "Google Drive backup is up to date");
            } catch (SyncCancelledException e) { deliverCancelled(activity, operation);
            } catch (Exception e) { if (operation.cancelled || Thread.currentThread().isInterrupted()) deliverCancelled(activity, operation); else deliverError(activity, operation, friendly(e));
            } finally { if (archive != null) archive.delete(); finishOperation(operation); }
        }, "wow-google-backup"); operation.thread = worker; worker.start();
    }

    static void smartBackup(Activity activity, String token, File libraryDir, File fontsDir,
                           SharedPreferences prefs, SyncCallback callback) {
        final Operation operation = beginOperation(callback);
        if (operation == null) { activity.runOnUiThread(() -> callback.onError("Google Drive operation already running")); return; }
        if (prefs.getBoolean("google_use_legacy_zip_sync", false)) {
            legacySmartBackup(activity, token, libraryDir, fontsDir, prefs, callback, operation); return;
        }
        Thread worker = new Thread(() -> { currentOperation.set(operation);
            File stateArchive = null;
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                requireCompleteRestore(prefs);
                checkCancelled();
                progress("metadata", "Checking cloud changes", 0, 0, 0, 0);
                GoogleAutoSync.requireHomeVisible();
                ReaderStateDb db = readyDatabase(activity, prefs, libraryDir);
                if (!db.isLibraryIndexReady()) {
                    prefs.edit().putLong("sync_updated_ms", System.currentTimeMillis()).apply();
                    deliverSuccess(activity, operation, "Preparing library index for sync");
                    return;
                }
                // Always reconcile the bounded state archive before publishing local state. This is
                // deliberately not a whole-library listing/download: book bytes remain incremental.
                BackupInfo remoteState = findFileInfo(token, STATE_BACKUP_NAME);
                String seenState = prefs.getString("google_last_seen_state_modified", "");
                if (remoteState != null && !remoteState.modifiedTime.equals(seenState)) {
                    File pulled = File.createTempFile("wow-state-pull-", ".zip", activity.getCacheDir());
                    File unpacked = new File(activity.getCacheDir(), "wow_state_pull_" + System.currentTimeMillis());
                    try {
                        downloadFileBounded(token, remoteState.id, pulled, MAX_STATE_ZIP_BYTES);
                        if (!unpacked.mkdirs()) throw new Exception("Unable to prepare state merge folder");
                        unzipSafelyBounded(pulled, unpacked, MAX_STATE_UNCOMPRESSED_BYTES);
                        File remoteDb = new File(unpacked, "state/reader.db");
                        mergeCloudMetadata(db, remoteDb, new File(unpacked, "state.json"), prefs, true);
                        prefs.edit().putString("google_last_seen_state_modified", remoteState.modifiedTime).apply();
                    } finally { pulled.delete(); deleteRecursively(unpacked); }
                }
                db.checkpoint();
                boolean publishState = remoteState == null
                        || prefs.getLong("sync_updated_ms", 0L) > prefs.getLong("google_last_synced_change_ms",
                                prefs.getLong("google_last_backup_ms", 0L))
                        || SyncBatchPolicy.hasMore(db.pendingBookUploadCount(), db.pendingTombstoneCount(), db.pendingCoverUploadCount());
                int uploaded=0, deleted=0;
                // Publish durable delete intent before processing any stale upload in this batch.
                for (ReaderStateDb.TombstoneRow row : db.pendingTombstones(SyncBatchPolicy.DELETE_BATCH)) {
                    GoogleAutoSync.requireHomeVisible();
                    deleteOneBook(token, db, row); deleted++;
                }
                for (ReaderStateDb.PendingSyncRow row : db.pendingBookUploads(SyncBatchPolicy.BOOK_BATCH)) {
                    GoogleAutoSync.requireHomeVisible();
                    File file=row.file();
                    if(!file.isFile()) continue;
                    if(syncOneBook(token, db, prefs, row, file)) uploaded++;
                }
                int coverSynced=0;
                for (ReaderStateDb.PendingCoverRow row : db.pendingCoverUploads(SyncBatchPolicy.COVER_BATCH)) { if(syncOneCover(token,db,prefs,row)) coverSynced++; }
                int remainingBooks=db.pendingBookUploadCount();
                int remainingDeletes=db.pendingTombstoneCount();
                int remainingCovers=db.pendingCoverUploadCount();
                boolean more=SyncBatchPolicy.hasMore(remainingBooks,remainingDeletes,remainingCovers);
                if (!more && publishState) {
                    // Do not publish a snapshot based on a stale read: another device may
                    // have changed the state archive while book batches were uploading.
                    BackupInfo beforePublish = findFileInfo(token, STATE_BACKUP_NAME);
                    if (remoteState == null ? beforePublish != null
                            : beforePublish == null || !remoteState.id.equals(beforePublish.id)
                            || !remoteState.modifiedTime.equals(beforePublish.modifiedTime)) {
                        throw new Exception("Google state changed during sync; retrying safely");
                    }
                    stateArchive=buildStateBackup(activity,prefs,fontsDir);
                    BackupInfo state=beforePublish;
                    if(state==null) createNamedZip(token,STATE_BACKUP_NAME,stateArchive);
                    else updateNamedFile(token,state.id,"application/zip",stateArchive);
                    BackupInfo published = findFileInfo(token, STATE_BACKUP_NAME);
                    if (published != null) prefs.edit().putString("google_last_seen_state_modified", published.modifiedTime).apply();
                } else if (more) {
                    // Force GoogleAutoSync to schedule the next small batch instead of considering the giant bootstrap finished.
                    prefs.edit().putLong("sync_updated_ms",System.currentTimeMillis()).apply();
                }
                prefs.edit().putLong("google_last_backup_ms",System.currentTimeMillis()).apply();
                final int u=uploaded,d=deleted,c=coverSynced,r=remainingBooks+remainingDeletes+remainingCovers;
                deliverSuccess(activity, operation, r>0 ? "Synced "+u+" books · "+c+" covers · "+r+" queued" : "Google Drive incremental sync is up to date");
            } catch(SyncCancelledException e) { deliverCancelled(activity, operation);
            } catch(Exception e) { if (operation.cancelled || Thread.currentThread().isInterrupted()) deliverCancelled(activity, operation); else deliverError(activity, operation, friendly(e));
            } finally { if(stateArchive!=null)stateArchive.delete(); finishOperation(operation); }
        },"wow-google-incremental-sync"); operation.thread = worker; worker.start();
    }

    private static boolean syncOneBook(String token, ReaderStateDb db, SharedPreferences prefs,
                                    ReaderStateDb.PendingSyncRow row, File file) throws Exception {
        String hash=row.hash;
        if(hash==null||hash.isEmpty()) hash=db.ensureHash(file,prefs);
        if(hash==null||hash.isEmpty()) throw new Exception("Unable to identify "+file.getName());
        final String identity=hash;
        final BackupInfo[] result=new BackupInfo[1];
        boolean uploaded=DriveDeletionGuard.uploadIfLive(identity,deletionRemote(token),deletionLocal(db),() ->
                result[0]=uploadOneBook(token,db,row,file,identity));
        if(uploaded)db.markBookSynced(row.fileName,result[0].id,result[0].modifiedTime);
        return uploaded;
    }

    private static BackupInfo uploadOneBook(String token,ReaderStateDb db,ReaderStateDb.PendingSyncRow row,
                                      File file,String hash) throws Exception {
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
        if (remoteId == null || remoteId.isEmpty()) {
            BackupInfo confirmed = findFileInfo(token, objectName);
            if (confirmed == null) throw new Exception("Google Drive upload confirmation is pending");
            remoteId = confirmed.id;
        }
        done.id=remoteId;
        return done;
    }

    private static boolean syncOneCover(String token,ReaderStateDb db,SharedPreferences prefs,ReaderStateDb.PendingCoverRow row)throws Exception{
        String hash=row.hash;File book=new File(row.filePath);if((hash==null||hash.isEmpty())&&book.isFile())hash=db.ensureHash(book,prefs);if(hash==null||hash.isEmpty())throw new Exception("Unable to identify cover book");
        final String identity=hash;
        final BackupInfo[] result=new BackupInfo[1];
        boolean uploaded=DriveDeletionGuard.uploadIfLive(identity,deletionRemote(token),deletionLocal(db),() ->
                result[0]=uploadOneCover(token,db,row,identity));
        if(uploaded)db.markCoverSynced(row.fileName,result[0].id);
        return uploaded;
    }

    private static BackupInfo uploadOneCover(String token,ReaderStateDb db,ReaderStateDb.PendingCoverRow row,String hash)throws Exception{
        if(row.coverPath==null||row.coverPath.isEmpty()){String id=row.remoteId;if((id==null||id.isEmpty())){BackupInfo i=findFileInfo(token,DriveObjectNamer.coverName(hash));if(i!=null)id=i.id;}if(id!=null&&!id.isEmpty())deleteRemoteFile(token,id);return new BackupInfo();}
        File cover=new File(row.coverPath);if(!cover.isFile())throw new Exception("Custom cover file is unavailable");String name=DriveObjectNamer.coverName(hash);BackupInfo remote=null;if(row.remoteId!=null&&!row.remoteId.isEmpty()){remote=new BackupInfo();remote.id=row.remoteId;}else remote=findFileInfo(token,name);
        String session=row.session;long offset=Math.max(0,row.offset);if(session==null||session.isEmpty()){session=startResumable(token,name,cover,hash,remote==null?"":remote.id);offset=0;db.saveCoverUploadCheckpoint(row.fileName,session,0);}
        BackupInfo done;try{done=uploadResumableCover(token,cover,session,offset,db,row.fileName);}catch(ResumableExpiredException x){session=startResumable(token,name,cover,hash,remote==null?"":remote.id);db.saveCoverUploadCheckpoint(row.fileName,session,0);done=uploadResumableCover(token,cover,session,0,db,row.fileName);}String id=done.id;if((id==null||id.isEmpty())&&remote!=null)id=remote.id;if(id==null||id.isEmpty()){BackupInfo confirmed=findFileInfo(token,name);if(confirmed==null)throw new Exception("Google Drive cover confirmation is pending");id=confirmed.id;}done.id=id;return done;
    }
    private static BackupInfo uploadResumableCover(String token, File file, String session, long start,
                                                  ReaderStateDb db, String fileName) throws Exception {
        return uploadResumable(token, file, session, start, db, fileName, true);
    }
    private static void deleteRemoteFile(String token,String id) throws Exception { withDriveAuth(token, () -> { deleteRemoteFileOnce(token,id); return null; }); }

    private static void deleteRemoteFileOnce(String token,String id)throws Exception{HttpURLConnection c=open("https://www.googleapis.com/drive/v3/files/"+id,"DELETE",token);int code=responseCode(c);c.disconnect();if(code!=404&&code!=204&&!(code>=200&&code<300))throw new Exception("Google Drive delete error "+code);}
    private static String mimeFor(File file){String n=file==null?"":file.getName().toLowerCase(java.util.Locale.ROOT);if(n.endsWith(".pdf"))return "application/pdf";if(n.endsWith(".jpg")||n.endsWith(".jpeg"))return "image/jpeg";if(n.endsWith(".png"))return "image/png";return "application/epub+zip";}

    private static void deleteOneBook(String token, ReaderStateDb db, ReaderStateDb.TombstoneRow row) throws Exception {
        DriveDeletionGuard.publishDeletion(row.hash,deletionRemote(token),() -> {
            // Also clean up a known older object ID, but never discard the tombstone itself.
            if(row.remoteId!=null&&!row.remoteId.isEmpty())deleteRemoteFile(token,row.remoteId);
            db.markTombstoneSynced(row.hash);
        });
    }

    private static DriveDeletionGuard.Local deletionLocal(ReaderStateDb db) {
        return new DriveDeletionGuard.Local() {
            public boolean hasTombstone(String hash) {return db.hasBookTombstone(hash);}
            public void rememberDeletion(String hash) {db.rememberRemoteDeletion(hash);}
        };
    }

    private static DriveDeletionGuard.Remote deletionRemote(String token) {
        return new DriveDeletionGuard.Remote() {
            public boolean hasMarker(String hash)throws Exception {
                return findFileInfo(token,DriveDeletionGuard.markerName(hash))!=null;
            }
            public void publishMarker(String hash)throws Exception {
                String name=DriveDeletionGuard.markerName(hash);
                if(findFileInfo(token,name)!=null)return;
                // Metadata-only immutable marker; duplicate creates are harmless (existence is authority).
                withDriveAuth(token, () -> {
                HttpURLConnection c=open("https://www.googleapis.com/drive/v3/files?fields=id","POST",token);
                c.setRequestProperty("Content-Type","application/json; charset=UTF-8");c.setDoOutput(true);
                JSONObject meta=new JSONObject();meta.put("name",name);meta.put("mimeType","application/octet-stream");
                meta.put("parents",new JSONArray().put("appDataFolder"));
                byte[] body=meta.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(body.length);
                try {try(OutputStream out=c.getOutputStream()){out.write(body);}ensureSuccess(c);}
                finally {c.disconnect();}
                    return null;
                });
            }
            public void deleteAssets(String hash)throws Exception {deleteBookAssets(token,hash);}
        };
    }

    private static void deleteBookAssets(String token,String hash)throws Exception {
        DriveDeletionGuard.markerName(hash);
        String q="trashed=false and (name='"+DriveObjectNamer.bookName(hash,"epub")+"' or name='"+
                DriveObjectNamer.bookName(hash,"pdf")+"' or name='"+DriveObjectNamer.coverName(hash)+"')";
        String url="https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&pageSize=100&fields=files(id)&q="+URLEncoder.encode(q,"UTF-8");
        // Re-read page one after deleting it: page tokens are unstable when objects are removed.
        for(int page=0;page<100;page++) { checkCancelled();
            JSONArray files=authorizedJson(url,token).optJSONArray("files");
            if(files==null||files.length()==0)return;
            for(int i=0;i<files.length();i++)deleteRemoteFile(token,files.getJSONObject(i).getString("id"));
        }
        throw new Exception("Cloud deletion cleanup is still pending; retry sync");
    }

    private static String startResumable(String token,String name,File file,String hash,String remoteId) throws Exception { return withDriveAuth(token, () -> startResumableOnce(token,name,file,hash,remoteId)); }

    private static String startResumableOnce(String token,String name,File file,String hash,String remoteId) throws Exception {
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
        catch (Exception e) { c.disconnect(); throw e; }
        String location=c.getHeaderField("Location"); c.disconnect();
        if(location==null||location.trim().isEmpty())throw new Exception("Google Drive resumable session unavailable");
        return location;
    }

    private static final class UploadProbe {
        long offset;
        BackupInfo completed;
    }

    private static BackupInfo readUploadInfo(HttpURLConnection connection) throws Exception {
        byte[] data = readAllLimited(connection.getInputStream(), 512L * 1024L);
        BackupInfo info = new BackupInfo();
        if (data.length > 0) {
            JSONObject value = new JSONObject(new String(data, StandardCharsets.UTF_8));
            info.id = value.optString("id", "");
            info.modifiedTime = value.optString("modifiedTime", "");
        }
        return info;
    }

    private static UploadProbe inspectUpload(String token, String session, long total) throws Exception { return withDriveAuth(token, () -> inspectUploadOnce(token,session,total)); }

    private static UploadProbe inspectUploadOnce(String token, String session, long total) throws Exception {
        GoogleAutoSync.requireHomeVisible();
        HttpURLConnection connection = open(session, "PUT", token);
        try {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Range", "bytes */" + total);
            connection.setFixedLengthStreamingMode(0);
            try (OutputStream ignored = connection.getOutputStream()) { }
            int code = responseCode(connection);
            if (code == 400 || code == 404 || code == 410) throw new ResumableExpiredException();
            UploadProbe probe = new UploadProbe();
            if (code == 308) {
                probe.offset = ResumableUploadPolicy.acknowledgedOffset(connection.getHeaderField("Range"), total);
                return probe;
            }
            if (code >= 200 && code < 300) {
                probe.offset = total;
                probe.completed = readUploadInfo(connection);
                return probe;
            }
            throw new Exception("Google Drive upload status error " + code);
        } finally { connection.disconnect(); }
    }

    private static void saveUploadOffset(ReaderStateDb db, String name, String session, long offset, boolean cover) {
        if (cover) db.saveCoverUploadCheckpoint(name, session, offset);
        else db.saveUploadCheckpoint(name, session, offset);
    }

    private static BackupInfo uploadResumable(String token, File file, String session, long start,
                                               ReaderStateDb db, String fileName) throws Exception {
        return uploadResumable(token, file, session, start, db, fileName, false);
    }

    private static BackupInfo uploadResumable(String token, File file, String session, long start,
                                               ReaderStateDb db, String fileName, boolean cover) throws Exception {
        long total = file.length();
        // The saved local offset is only a hint. The server may have accepted a chunk
        // whose response was lost, or accepted fewer bytes than the client sent.
        UploadProbe probe = inspectUpload(token, session, total);
        if (probe.completed != null) return probe.completed;
        long offset = probe.offset;
        saveUploadOffset(db, fileName, session, offset, cover);
        int stalled = 0;
        try (RandomAccessFile source = new RandomAccessFile(file, "r")) {
            while (offset < total) {
                checkCancelled(); GoogleAutoSync.requireHomeVisible();
                progress("upload", "Uploading " + fileName, 0, 0, offset, total);
                int length = (int) Math.min((long) SyncBatchPolicy.CHUNK_BYTES, total - offset);
                long end = offset + length - 1L;
                UploadProbe result = withDriveAuth(token, () -> sendUploadChunk(token, file, session, source, total, end, length));
                if (result.completed != null) return result.completed;
                long acknowledged = result.offset;
                stalled = ResumableUploadPolicy.stalledAcknowledgements(offset, acknowledged, stalled);
                offset = acknowledged;
                saveUploadOffset(db, fileName, session, offset, cover);
                if (stalled >= ResumableUploadPolicy.MAX_STALLED_ACKS)
                    throw new Exception("Google Drive upload made no progress; queued for retry");
            }
        }
        UploadProbe completed = inspectUpload(token, session, total);
        if (completed.completed != null) return completed.completed;
        throw new Exception("Google Drive upload confirmation is pending; queued for retry");
    }
    private static UploadProbe sendUploadChunk(String token, File file, String session,
                                                RandomAccessFile source, long total, long end, int length) throws Exception {
        HttpURLConnection connection = open(session, "PUT", token);
                try {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", mimeFor(file));
                    connection.setRequestProperty("Content-Range", "bytes " + (end - length + 1) + "-" + end + "/" + total);
                    connection.setFixedLengthStreamingMode(length);
                    source.seek(end - length + 1);
                    byte[] buffer = new byte[64 * 1024];
                    int remaining = length;
                    try (OutputStream out = new BufferedOutputStream(connection.getOutputStream())) {
                        while (remaining > 0) {
                            int count = source.read(buffer, 0, Math.min(buffer.length, remaining));
                            if (count < 0) throw new Exception("Unexpected end of upload file");
                            out.write(buffer, 0, count);
                            remaining -= count;
                        }
                    }
                    int code = responseCode(connection);
                    if (code == 400 || code == 404 || code == 410) throw new ResumableExpiredException();
                    if (code == 308) {
                        UploadProbe result = new UploadProbe();
                        result.offset = ResumableUploadPolicy.acknowledgedOffset(connection.getHeaderField("Range"), total);
                        return result;
                    }
                    if (code >= 200 && code < 300) { UploadProbe result = new UploadProbe(); result.completed = readUploadInfo(connection); return result; }
                    throw new Exception("Google Drive upload error " + code);
                } finally { connection.disconnect(); }
    }
    private static final class ResumableExpiredException extends Exception {}

    private static void legacySmartBackup(Activity activity, String token, File libraryDir, File fontsDir,
                           SharedPreferences prefs, SyncCallback callback, Operation operation) {
        Thread worker = new Thread(() -> {
            currentOperation.set(operation);
            File remoteArchive = null, remoteStateArchive = null, temp = null, stateTemp = null;
            File mergedArchive = null, stateArchive = null;
            try {
                requireCompleteRestore(prefs);
                checkCancelled();
                progress("preparing", "Preparing legacy backup", 0, 0, 0, 0);
                ReaderStateDb db=readyDatabase(activity,prefs,libraryDir);
                for(ReaderStateDb.TombstoneRow row:db.pendingTombstones(SyncBatchPolicy.DELETE_BATCH))deleteOneBook(token,db,row);
                if(db.pendingTombstoneCount()>0)throw new Exception("Cloud deletions are queued; sync again before legacy backup");
                // Legacy mode is already O(N); apply markers before merging old ZIP content.
                restoreDeletionMarkers(token,db);
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
                    File remoteDb = new File(temp, "state/reader.db");
                    if (remoteDb.isFile()) mergeCloudMetadata(db, remoteDb, new File(temp, "state.json"), prefs, false);
                    else CloudMergePolicy.mergePreferences(readPreferenceValues(new File(temp, "state.json")), prefs);
                    mergeMissingFiles(new File(temp, "fonts"), fontsDir);
                    prefs.edit().putLong("library_files_updated_ms", System.currentTimeMillis()).apply();
                }

                if (remoteStateChanged) {
                    remoteStateArchive = File.createTempFile("wow-state-sync-", ".zip", activity.getCacheDir());
                    downloadFileBounded(token, remoteState.id, remoteStateArchive, MAX_STATE_ZIP_BYTES);
                    stateTemp = new File(activity.getCacheDir(), "wow_state_merge_" + System.currentTimeMillis());
                    if (!stateTemp.mkdirs()) throw new Exception("Unable to prepare state merge folder");
                    unzipSafelyBounded(remoteStateArchive, stateTemp, MAX_STATE_UNCOMPRESSED_BYTES);
                    File remoteDb = new File(stateTemp, "state/reader.db");
                    mergeCloudMetadata(db, remoteDb, new File(stateTemp, "state.json"), prefs, false);
                }

                if(temp!=null)restoreLegacyBooks(token,db,prefs,new File(temp,"books"),libraryDir,true);
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
                deliverSuccess(activity, operation, "Google Drive smart sync is up to date");
            } catch (SyncCancelledException e) { deliverCancelled(activity, operation);
            } catch (Exception e) { if (operation.cancelled || Thread.currentThread().isInterrupted()) deliverCancelled(activity, operation); else deliverError(activity, operation, friendly(e));
            } finally {
                if (remoteArchive != null) remoteArchive.delete();
                if (remoteStateArchive != null) remoteStateArchive.delete();
                if (mergedArchive != null) mergedArchive.delete();
                if (stateArchive != null) stateArchive.delete();
                deleteRecursively(temp); deleteRecursively(stateTemp);
                finishOperation(operation);
            }
        }, "wow-google-smart-sync"); operation.thread = worker; worker.start();
    }

    static void restore(Activity activity, String token, File libraryDir, File fontsDir,
                        SharedPreferences prefs, SyncCallback callback) {
        final Operation operation = beginOperation(callback);
        if (operation == null) { activity.runOnUiThread(() -> callback.onError("Google Drive operation already running")); return; }
        Thread worker = new Thread(() -> {
            currentOperation.set(operation);
            File archive = null;
            File temp = null;
            boolean foundAny = false;
            boolean metadataIncomplete = false;
            int restoredBooks = 0, restoredCovers = 0;
            try {
                if (!libraryDir.exists() && !libraryDir.mkdirs()) throw new Exception("Unable to prepare library folder");
                if (!fontsDir.exists() && !fontsDir.mkdirs()) throw new Exception("Unable to prepare fonts folder");

                ReaderStateDb db = readyDatabase(activity, prefs, libraryDir);
                progress("checking", "Checking cloud backup", 0, 0, 0, 0);
                checkCancelled();
                restoreDeletionMarkers(token,db);
                progress("metadata", "Reading backup metadata", 0, 0, 0, 0);
                // Legacy ZIP remains a migration/fallback source, but is no longer required.
                BackupInfo legacy = findBackupInfo(token);
                if (legacy != null) {
                    foundAny = true;
                    archive = File.createTempFile("wow-drive-restore-", ".zip", activity.getCacheDir());
                    downloadBackup(token, legacy.id, archive);
                    temp = new File(activity.getCacheDir(), "wow_restore_" + System.currentTimeMillis());
                    if (!temp.mkdirs()) throw new Exception("Unable to prepare restore folder");
                    unzipSafely(archive, temp);
                    restoreFiles(new File(temp, "fonts"), fontsDir);
                    File fullDb = new File(temp, "state/reader.db");
                    // Pre-database legacy ZIPs legitimately contain only preferences and books.
                    if (fullDb.isFile()) metadataIncomplete = !restoreMetadata(db, fullDb, new File(temp, "state.json"), prefs);
                    else restorePreferences(new File(temp, "state.json"), prefs);
                }

                // The state ZIP is authoritative for the current incremental format.
                BackupInfo stateInfo = findFileInfo(token, STATE_BACKUP_NAME);
                if (stateInfo != null) {
                    foundAny = true;
                    // A healthy current archive supersedes a broken old full-ZIP database.
                    metadataIncomplete = !restoreStateArchive(activity, token, stateInfo, libraryDir, fontsDir, prefs);
                }

                // Merge both snapshots' delete intent before installing any legacy book bytes.
                Set<String> boundBooks = new java.util.HashSet<>();
                if(temp!=null) restoredBooks = restoreLegacyBooks(token,db,prefs,new File(temp,"books"),libraryDir,false,boundBooks);
                // One-time restore may be O(N); normal startup/sync remains paged and incremental.
                checkCancelled();
                restoredBooks += restoreIncrementalBooks(token, activity, db, prefs, libraryDir, boundBooks);
                checkCancelled();
                restoredCovers = restoreIncrementalCovers(token, activity, db);
                if (restoredBooks > 0 || restoredCovers > 0) foundAny = true;

                // Only pre-existing files still need rebinding after the remote DB merge.
                // Transfers already bound/repaired their files; do not repeat N write transactions.
                checkCancelled();
                rebindUnprocessedBooks(db, libraryDir, prefs, boundBooks);

                checkCancelled();
                db.reconcileLocalLibrary(libraryDir, prefs);
                if (!foundAny) throw new Exception("No WoW Reader backup was found in this Google Drive");
                checkCancelled();
                prefs.edit()
                        .putLong("library_files_updated_ms", System.currentTimeMillis())
                        .putLong("google_last_backup_ms", System.currentTimeMillis())
                        .putLong("sync_updated_ms", System.currentTimeMillis())
                        .apply();
                checkCancelled();
                rememberIncompleteRestore(prefs, metadataIncomplete);
                checkCancelled();
                final int books = restoredBooks, covers = restoredCovers;
                final boolean incomplete = metadataIncomplete;
                progress("ready", incomplete ? "Files restored; some reading metadata needs recovery" : "Ready for offline reading", books, books, 0, 0);
                deliverSuccess(activity, operation, restoreMessage(books, covers, incomplete));
            } catch (SyncCancelledException e) {
                deliverCancelled(activity, operation);
            } catch (Exception e) {
                if (operation.cancelled || Thread.currentThread().isInterrupted()) deliverCancelled(activity, operation); else deliverError(activity, operation, friendly(e));
            } finally {
                if (archive != null) archive.delete();
                deleteRecursively(temp);
                finishOperation(operation);
            }
        }, "wow-google-restore");
        operation.thread = worker;
        worker.start();
    }

    static void rebindUnprocessedBooks(ReaderStateDb db, File libraryDir, SharedPreferences prefs,
                                      Set<String> boundBooks) {
        File[] local = libraryDir.listFiles();
        if (local != null) for (File book : local) {
            if (book == null || !book.isFile() || boundBooks.contains(book.getName())) continue;
            String lower = book.getName().toLowerCase(java.util.Locale.ROOT);
            if (!lower.endsWith(".epub") && !lower.endsWith(".pdf")) continue;
            String hash = db.contentHash(book.getName());
            if (hash != null && !hash.isEmpty()) bindRestoredBook(db,book, hash, "", prefs);
        }
    }

    private static boolean restoreStateArchive(Activity activity, String token, BackupInfo stateInfo,
                                            File libraryDir, File fontsDir, SharedPreferences prefs) throws Exception {
        File stateZip = File.createTempFile("wow-state-restore-", ".zip", activity.getCacheDir());
        File stateDir = new File(activity.getCacheDir(), "wow_state_restore_" + System.currentTimeMillis());
        try {
            downloadFileBounded(token, stateInfo.id, stateZip, MAX_STATE_ZIP_BYTES);
            if (!stateDir.mkdirs()) throw new Exception("Unable to prepare state restore folder");
            unzipSafelyBounded(stateZip, stateDir, MAX_STATE_UNCOMPRESSED_BYTES);
            restoreFiles(new File(stateDir, "fonts"), fontsDir);
            File stateDb = new File(stateDir, "state/reader.db");
            return restoreMetadata(readyDatabase(activity, prefs, libraryDir),
                    stateDb, new File(stateDir, "state.json"), prefs);
        } finally {
            stateZip.delete();
            deleteRecursively(stateDir);
        }
    }

    // Only explicit Restore may recover verified book bytes without usable SQLite metadata.
    // Automatic sync never catches this exception and must not overwrite the recovery source.
    static void mergeCloudMetadata(ReaderStateDb db, File snapshot, File stateFile,
                                   SharedPreferences prefs, boolean existingBooksOnly) throws Exception {
        ReaderStateDb.validateSnapshot(snapshot);
        db.mergeSnapshot(snapshot, prefs, existingBooksOnly);
        CloudMergePolicy.mergePreferences(readPreferenceValues(stateFile), prefs);
    }

    static boolean restoreMetadata(ReaderStateDb db, File snapshot, File stateFile,
                                   SharedPreferences prefs) throws Exception {
        boolean usable = true;
        try { ReaderStateDb.validateSnapshot(snapshot); }
        catch (ReaderStateDb.InvalidSnapshotException invalid) {
            usable = false;
            rememberIncompleteRestore(prefs, true);
        }
        // Legacy preference data (including annotations) can still be recovered independently.
        restorePreferences(stateFile, prefs);
        if (usable) db.mergeSnapshot(snapshot, prefs);
        return usable;
    }

    static String restoreMessage(int books, int covers, boolean incomplete) {
        if (incomplete) return "Recovered " + books + " cloud books · " + covers
                + " custom covers. Backup reading database is incomplete; some progress, shelves or history may be missing. Cloud backup is paused to protect the original.";
        return books > 0 || covers > 0
                ? "Restored " + books + " cloud books · " + covers + " custom covers · reading data"
                : "Books, notes and reading data restored";
    }

    private static String restoreAccount(SharedPreferences prefs) {
        String uid = prefs.getString("google_account_uid", "");
        return uid.isEmpty() ? prefs.getString("google_account_email", "") : uid;
    }

    static void rememberIncompleteRestore(SharedPreferences prefs, boolean incomplete) {
        Set<String> accounts = new java.util.HashSet<>(prefs.getStringSet("google_restore_incomplete_accounts", java.util.Collections.emptySet()));
        if (incomplete) accounts.add(restoreAccount(prefs));
        else accounts.remove(restoreAccount(prefs));
        prefs.edit().putStringSet("google_restore_incomplete_accounts", accounts).apply();
    }

    static void requireCompleteRestore(SharedPreferences prefs) {
        if (prefs.getStringSet("google_restore_incomplete_accounts", java.util.Collections.emptySet()).contains(restoreAccount(prefs)))
            throw new IllegalStateException("Cloud backup paused: this account's restored reading database is incomplete. Local books are safe; retry Restore from a healthy backup before syncing.");
    }

    private static void restoreDeletionMarkers(String token,ReaderStateDb db)throws Exception {
        String pageToken="";
        do {
            JSONObject page=listIncrementalObjects(token,DriveDeletionGuard.PREFIX,pageToken);
            JSONArray files=page.optJSONArray("files");
            if(files!=null)for(int i=0;i<files.length();i++) {
                JSONObject file=files.optJSONObject(i);if(file==null)continue;
                String hash=DriveDeletionGuard.parseMarker(file.optString("name",""));
                if(!hash.isEmpty())db.rememberRemoteDeletion(hash);
            }
            pageToken=page.optString("nextPageToken","");
        } while(!pageToken.isEmpty());
    }

    private static int restoreLegacyBooks(String token,ReaderStateDb db,SharedPreferences prefs,
                                           File source,File destination,boolean missingOnly)throws Exception {
        return restoreLegacyBooks(token, db, prefs, source, destination, missingOnly, new java.util.HashSet<>());
    }

    private static int restoreLegacyBooks(String token, ReaderStateDb db, SharedPreferences prefs,
                                          File source, File destination, boolean missingOnly, Set<String> boundBooks) throws Exception {
        int restored = 0;
        File[] files=source.listFiles();if(files==null)return 0;
        int totalBooks = 0;
        for (File book : files) { if (book.isFile() && (book.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".epub") || book.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"))) totalBooks++; }
        int completedBooks = 0;
        for(File book:files) {
            checkCancelled();
            if(!book.isFile())continue;
            String name=book.getName().toLowerCase(java.util.Locale.ROOT);
            if(!name.endsWith(".epub")&&!name.endsWith(".pdf"))continue;
            File named=new File(destination,book.getName());
            if(missingOnly&&named.isFile())continue;
            String hash=FileIdentityUtil.sha256(book);
            String extension=name.endsWith(".pdf")?"pdf":"epub";
            File target=BookRestoreFiles.destination(destination,book.getName(),hash,extension);
            if (DriveDeletionGuard.restoreIfLive(hash,deletionRemote(token),deletionLocal(db),() -> {
                BookRestoreFiles.install(book,target,hash);
                bindRestoredBook(db,target,hash,"",prefs);
                boundBooks.add(target.getName());
            })) restored++;
            completedBooks++;
            progress("books", book.getName(), completedBooks, totalBooks, 0, 0);
        }
        return restored;
    }

    private static int restoreIncrementalBooks(String token, Activity activity, ReaderStateDb db,
                                               SharedPreferences prefs, File libraryDir, Set<String> boundBooks) throws Exception {
        int restored = 0;
        String pageToken = "";
        do {
            JSONObject page = listIncrementalObjects(token, "wow_book_", pageToken);
            JSONArray files = page.optJSONArray("files");
            List<RestoreTransfers.Task> transfers = new ArrayList<>();
            if (files != null) for (int i = 0; i < files.length(); i++) {
                JSONObject remote = files.optJSONObject(i);
                if (remote == null) continue;
                String id = remote.optString("id", "");
                DriveRestorePlanner.BookObject object = DriveRestorePlanner.parseBookObject(remote.optString("name", ""));
                if (id.isEmpty() || object == null || db.hasBookTombstone(object.hash)) continue;
                transfers.add(new RestoreBookTransfer(object.hash, deletionRemote(token), deletionLocal(db),
                        () -> BookRestoreFiles.destination(libraryDir,
                                DriveRestorePlanner.safeLocalName(db.fileNameForHash(object.hash), object),
                                object.hash, object.extension),
                        scratch -> downloadFile(token, id, scratch),
                        target -> {
                            bindRestoredBook(db, target, object.hash, id, prefs);
                            boundBooks.add(target.getName());
                        }));
            }
            restored += RestoreTransfers.run(activity.getCacheDir(), transfers,
                    (done, total) -> progress("books", "Book files (this batch)", done, total, 0, 0),
                    GoogleDriveSync::checkCancelledForTransfer);
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
            List<RestoreTransfers.Task> transfers = new ArrayList<>();
            if (files != null) for (int i = 0; i < files.length(); i++) {
                JSONObject remote = files.optJSONObject(i);
                if (remote == null) continue;
                String id = remote.optString("id", "");
                String hash = DriveRestorePlanner.parseCoverHash(remote.optString("name", ""));
                if (id.isEmpty() || hash.isEmpty() || db.fileNameForHash(hash).isEmpty()) continue;
                transfers.add(new RestoreTransfers.Task() {
                    public boolean download(File scratch) throws Exception {
                        if (DriveDeletionGuard.isDeleted(hash, deletionRemote(token), deletionLocal(db))) {
                            db.rememberRemoteDeletion(hash);
                            return false;
                        }
                        downloadFile(token, id, scratch);
                        android.graphics.Bitmap preview = CustomCoverStore.decodeSampled(scratch, 120, 180);
                        if (preview == null) return false;
                        preview.recycle();
                        return true;
                    }
                    public boolean install(File scratch) throws Exception {
                        File destination = new File(coverDir, "cloud_" + hash.substring(0, 24) + ".jpg");
                        return DriveDeletionGuard.restoreIfLive(hash, deletionRemote(token), deletionLocal(db), () -> {
                            installRestoredFile(scratch, destination);
                            db.recordRestoredCover(hash, destination.getAbsolutePath(), id);
                        });
                    }
                });
            }
            restored += RestoreTransfers.run(activity.getCacheDir(), transfers,
                    (done, total) -> progress("covers", "Custom covers (this batch)", done, total, 0, 0),
                    GoogleDriveSync::checkCancelledForTransfer);
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

    private static void bindRestoredBook(ReaderStateDb db, File book, String hash, String remoteId, SharedPreferences prefs) {
        db.recordRestoredBook(book, hash, remoteId, prefs);
        File library = book.getParentFile();
        File files = library == null ? null : library.getParentFile();
        if (files != null) RestoredBookMetadata.repairStored(db, book, new File(files, "cover_cache"), prefs);
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
            ReaderStateDb db = readyDatabase(activity, prefs, libraryDir);
            // Full ZIP is a compatibility path only. Do not put locally known deleted content back in it.
            File[] books=libraryDir.listFiles();
            if(books!=null)for(File book:books) {
                if(!book.isFile())continue;
                String name=book.getName().toLowerCase(java.util.Locale.ROOT);
                if(!name.endsWith(".epub")&&!name.endsWith(".pdf"))continue;
                String hash=db.ensureHash(book,prefs);
                if(!db.hasBookTombstone(hash))addFile(zip,book,"books/"+book.getName());
            }
            addDirectory(zip, fontsDir, "fonts/");
            byte[] state = exportPreferences(prefs).toString().getBytes(StandardCharsets.UTF_8);
            ZipEntry entry = new ZipEntry("state.json");
            zip.putNextEntry(entry);
            zip.write(state);
            zip.closeEntry();
            File dbFile = db.createSnapshot(activity);
            try { addFile(zip, dbFile, "state/reader.db"); }
            finally { dbFile.delete(); }
        }
        return out;
    }

    private static File buildStateBackup(Activity activity, SharedPreferences prefs, File fontsDir) throws Exception {
        GoogleAutoSync.requireHomeVisible();
        File out = File.createTempFile("wow-reader-state-", ".zip", activity.getCacheDir());
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            byte[] state = exportPreferences(prefs).toString().getBytes(StandardCharsets.UTF_8);
            ZipEntry entry = new ZipEntry("state.json");
            zip.putNextEntry(entry); zip.write(state); zip.closeEntry();
            ReaderStateDb db = readyDatabase(activity, prefs, new File(activity.getFilesDir(), "library"));
            File dbFile = db.createSnapshot(activity);
            try { addFile(zip, dbFile, "state/reader.db"); }
            finally { dbFile.delete(); }
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
            if (key == null || key.startsWith("google_") || CloudMergePolicy.isDeviceLocal(key)) continue;
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
        String json = new String(readAllLimited(new FileInputStream(stateFile), MAX_STATE_JSON_BYTES), StandardCharsets.UTF_8);
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
        String json = new String(readAllLimited(new FileInputStream(stateFile), MAX_STATE_JSON_BYTES), StandardCharsets.UTF_8);
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
            while ((entry = zip.getNextEntry()) != null) { checkCancelled();
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

    private static void createNamedZip(String token, String name, File archive) throws Exception { withDriveAuth(token, () -> { createNamedZipOnce(token,name,archive); return null; }); }

    private static void createNamedZipOnce(String token, String name, File archive) throws Exception {
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

    private static void updateNamedFile(String token, String id, String mime, File file) throws Exception { withDriveAuth(token, () -> { updateNamedFileOnce(token,id,mime,file); return null; }); }

    private static void updateNamedFileOnce(String token, String id, String mime, File file) throws Exception {
        HttpURLConnection c = open("https://www.googleapis.com/upload/drive/v3/files/" + id + "?uploadType=media&fields=id", "POST", token);
        c.setRequestProperty("X-HTTP-Method-Override", "PATCH"); c.setRequestProperty("Content-Type", mime); c.setDoOutput(true);
        try (OutputStream out = new BufferedOutputStream(c.getOutputStream())) { copy(new FileInputStream(file), out); }
        ensureSuccess(c); c.disconnect();
    }

    private static void downloadFileBounded(String token, String id, File destination, long maxBytes) throws Exception { withDriveAuth(token, () -> { downloadFileBoundedOnce(token,id,destination,maxBytes); return null; }); }

    private static void downloadFileBoundedOnce(String token, String id, File destination, long maxBytes) throws Exception {
        HttpURLConnection c = open("https://www.googleapis.com/drive/v3/files/" + id + "?alt=media", "GET", token);
        try {
            ensureSuccess(c);
            // getContentLengthLong() is API 24; keep Android 6/API 23 supported.
            long length = -1L;
            try { String value = c.getHeaderField("Content-Length"); if (value != null) length = Long.parseLong(value); }
            catch (NumberFormatException ignored) {}
            if (length > maxBytes) throw new Exception("Google state archive is too large");
            try (InputStream in = new BufferedInputStream(c.getInputStream());
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
                copyBounded(in, out, maxBytes);
            }
        } finally { c.disconnect(); }
    }

    private static void downloadFile(String token, String id, File destination) throws Exception { withDriveAuth(token, () -> { downloadFileOnce(token,id,destination); return null; }); }

    private static void downloadFileOnce(String token, String id, File destination) throws Exception {
        HttpURLConnection c = open("https://www.googleapis.com/drive/v3/files/" + id + "?alt=media", "GET", token);
        boolean complete = false;
        try {
            ensureSuccess(c);
            try (InputStream in = new BufferedInputStream(c.getInputStream());
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) { copy(in, out); }
            complete = true;
        } finally {
            c.disconnect();
            if (!complete) destination.delete();
        }
    }

    private static void copyBounded(InputStream in, OutputStream out, long maxBytes) throws Exception {
        byte[] buffer = new byte[64 * 1024]; long total = 0L; int n;
        while ((n = in.read(buffer)) > 0) { checkCancelled();
            GoogleAutoSync.requireHomeVisible();
            total += n;
            if (total > maxBytes) throw new Exception("Google state archive is too large");
            out.write(buffer, 0, n);
        }
    }

    private static byte[] readAllLimited(InputStream in, long maxBytes) throws Exception {
        try (InputStream source = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[32 * 1024]; long total = 0L; int n;
            while ((n = source.read(buffer)) > 0) { total += n; if (total > maxBytes) throw new Exception("Google state metadata is too large"); out.write(buffer, 0, n); }
            return out.toByteArray();
        }
    }

    private static void unzipSafelyBounded(File zipFile, File destination, long maxBytes) throws Exception {
        String root = destination.getCanonicalPath() + File.separator; long total = 0L;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry; byte[] buffer = new byte[64 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                File out = new File(destination, entry.getName());
                if (!out.getCanonicalPath().startsWith(root)) throw new Exception("Unsafe backup archive");
                if (entry.isDirectory()) { out.mkdirs(); zip.closeEntry(); continue; }
                File parent = out.getParentFile(); if (parent != null) parent.mkdirs();
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    int n; while ((n = zip.read(buffer)) > 0) { total += n; if (total > maxBytes) throw new Exception("Google state archive expands too far"); os.write(buffer, 0, n); }
                }
                zip.closeEntry();
            }
        }
    }

    private static JSONObject authorizedJson(String url, String token) throws Exception { return withDriveAuth(token, () -> authorizedJsonOnce(url,token)); }

    private static JSONObject authorizedJsonOnce(String url, String token) throws Exception {
        HttpURLConnection c = open(url, "GET", token);
        try {
            ensureSuccess(c);
            byte[] data;
            try (InputStream in = new BufferedInputStream(c.getInputStream())) { data = readAll(in); }
            return new JSONObject(new String(data, StandardCharsets.UTF_8));
        } finally { c.disconnect(); }
    }

    private static HttpURLConnection open(String url, String method, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20_000);
        c.setReadTimeout(120_000);
        c.setUseCaches(false);
        DriveAuthRetry session = credentials(token);
        String bearer = session == null ? token : session.token();
        c.setInstanceFollowRedirects(false); // Never forward bearer credentials to another origin.
        c.setRequestProperty("Authorization", "Bearer " + bearer);
        requestTokens.put(c, bearer);
        c.setRequestProperty("Accept", "application/json");
        return c;
    }

    private static void ensureSuccess(HttpURLConnection c) throws Exception {
        int code = responseCode(c);
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
            while ((n = source.read(buffer)) > 0) { checkCancelled(); out.write(buffer, 0, n); }
            return out.toByteArray();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        try (InputStream source = in) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = source.read(buffer)) > 0) { checkCancelled(); out.write(buffer, 0, n); }
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
