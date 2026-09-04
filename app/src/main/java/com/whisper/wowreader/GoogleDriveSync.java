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
        new Thread(() -> {
            File remoteArchive = null;
            File temp = null;
            File mergedArchive = null;
            try {
                BackupInfo remote = findBackupInfo(token);
                String seenRemote = prefs.getString("google_last_seen_remote_modified", "");

                if (remote != null && (seenRemote.isEmpty() || !seenRemote.equals(remote.modifiedTime))) {
                    remoteArchive = File.createTempFile("wow-smart-sync-", ".zip", activity.getCacheDir());
                    downloadBackup(token, remote.id, remoteArchive);
                    temp = new File(activity.getCacheDir(), "wow_smart_merge_" + System.currentTimeMillis());
                    if (!temp.mkdirs()) throw new Exception("Unable to prepare cloud merge folder");
                    unzipSafely(remoteArchive, temp);
                    mergeMissingFiles(new File(temp, "books"), libraryDir);
                    mergeMissingFiles(new File(temp, "fonts"), fontsDir);
                    CloudMergePolicy.mergePreferences(readPreferenceValues(new File(temp, "state.json")), prefs);
                }

                mergedArchive = buildBackup(activity, libraryDir, fontsDir, prefs);
                BackupInfo latest = findBackupInfo(token);
                if (remote == null) {
                    if (latest != null) throw new Exception("Cloud library changed during sync; retrying safely");
                    createBackup(token, mergedArchive);
                } else {
                    if (latest == null || !remote.id.equals(latest.id) ||
                            !remote.modifiedTime.equals(latest.modifiedTime))
                        throw new Exception("Cloud library changed during sync; retrying safely");
                    updateBackup(token, remote.id, mergedArchive);
                }

                BackupInfo updated = findBackupInfo(token);
                SharedPreferences.Editor done = prefs.edit()
                        .putLong("google_last_backup_ms", System.currentTimeMillis());
                if (updated != null && updated.modifiedTime != null)
                    done.putString("google_last_seen_remote_modified", updated.modifiedTime);
                done.apply();
                activity.runOnUiThread(() -> callback.onSuccess("Google Drive smart sync is up to date"));
            } catch (Exception e) {
                String message = friendly(e);
                activity.runOnUiThread(() -> callback.onError(message));
            } finally {
                if (remoteArchive != null) remoteArchive.delete();
                if (mergedArchive != null) mergedArchive.delete();
                deleteRecursively(temp);
            }
        }, "wow-google-smart-sync").start();
    }

    static void restore(Activity activity, String token, File libraryDir, File fontsDir,
                        SharedPreferences prefs, SyncCallback callback) {
        new Thread(() -> {
            File archive = null;
            File temp = null;
            try {
                String id = findBackupId(token);
                if (id == null) throw new Exception("No WoW Reader backup was found in this Google Drive");
                archive = File.createTempFile("wow-drive-restore-", ".zip", activity.getCacheDir());
                downloadBackup(token, id, archive);
                temp = new File(activity.getCacheDir(), "wow_restore_" + System.currentTimeMillis());
                if (!temp.mkdirs()) throw new Exception("Unable to prepare restore folder");
                unzipSafely(archive, temp);
                restoreFiles(new File(temp, "books"), libraryDir);
                restoreFiles(new File(temp, "fonts"), fontsDir);
                restorePreferences(new File(temp, "state.json"), prefs);
                prefs.edit()
                        .putLong("google_last_backup_ms", System.currentTimeMillis())
                        .putLong("sync_updated_ms", System.currentTimeMillis())
                        .apply();
                activity.runOnUiThread(() -> callback.onSuccess("Books, notes and reading data restored"));
            } catch (Exception e) {
                String message = friendly(e);
                activity.runOnUiThread(() -> callback.onError(message));
            } finally {
                if (archive != null) archive.delete();
                deleteRecursively(temp);
            }
        }, "wow-google-restore").start();
    }

    static void hasBackup(Activity activity, String token, BackupCheckCallback callback) {
        new Thread(() -> {
            boolean found = false;
            try { found = findBackupId(token) != null; } catch (Exception ignored) {}
            final boolean value = found;
            activity.runOnUiThread(() -> callback.onResult(value));
        }, "wow-google-backup-check").start();
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

    private static BackupInfo findBackupInfo(String token) throws Exception {
        String q = "name='" + BACKUP_NAME + "' and trashed=false";
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

    private static void createBackup(String token, File archive) throws Exception {
        String boundary = "wowreader_" + System.currentTimeMillis();
        HttpURLConnection c = open("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", "POST", token);
        c.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
        c.setDoOutput(true);
        try (OutputStream out = new BufferedOutputStream(c.getOutputStream())) {
            String metadata = "{\"name\":" + JSONObject.quote(BACKUP_NAME) + ",\"parents\":[\"appDataFolder\"],\"mimeType\":\"application/zip\"}";
            writeUtf8(out, "--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + metadata + "\r\n");
            writeUtf8(out, "--" + boundary + "\r\nContent-Type: application/zip\r\n\r\n");
            copy(new FileInputStream(archive), out);
            writeUtf8(out, "\r\n--" + boundary + "--\r\n");
        }
        ensureSuccess(c);
        c.disconnect();
    }

    private static void updateBackup(String token, String id, File archive) throws Exception {
        HttpURLConnection c = open("https://www.googleapis.com/upload/drive/v3/files/" + id + "?uploadType=media&fields=id", "POST", token);
        c.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        c.setRequestProperty("Content-Type", "application/zip");
        c.setDoOutput(true);
        try (OutputStream out = new BufferedOutputStream(c.getOutputStream())) {
            copy(new FileInputStream(archive), out);
        }
        ensureSuccess(c);
        c.disconnect();
    }

    private static void downloadBackup(String token, String id, File destination) throws Exception {
        HttpURLConnection c = open("https://www.googleapis.com/drive/v3/files/" + id + "?alt=media", "GET", token);
        ensureSuccess(c);
        try (InputStream in = new BufferedInputStream(c.getInputStream());
             OutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
            copy(in, out);
        }
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
