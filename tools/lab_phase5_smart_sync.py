from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DRIVE = ROOT / "app/src/main/java/com/whisper/wowreader/GoogleDriveSync.java"
AUTO = ROOT / "app/src/main/java/com/whisper/wowreader/GoogleAutoSync.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


drive = DRIVE.read_text(encoding="utf-8")

drive = replace_once(
    drive,
    '''    static void restore(Activity activity, String token, File libraryDir, File fontsDir,\n                        SharedPreferences prefs, SyncCallback callback) {''',
    '''    static void smartBackup(Activity activity, String token, File libraryDir, File fontsDir,\n                           SharedPreferences prefs, SyncCallback callback) {\n        new Thread(() -> {\n            File remoteArchive = null;\n            File temp = null;\n            File mergedArchive = null;\n            try {\n                BackupInfo remote = findBackupInfo(token);\n                String seenRemote = prefs.getString("google_last_seen_remote_modified", "");\n\n                if (remote != null && (seenRemote.isEmpty() || !seenRemote.equals(remote.modifiedTime))) {\n                    remoteArchive = File.createTempFile("wow-smart-sync-", ".zip", activity.getCacheDir());\n                    downloadBackup(token, remote.id, remoteArchive);\n                    temp = new File(activity.getCacheDir(), "wow_smart_merge_" + System.currentTimeMillis());\n                    if (!temp.mkdirs()) throw new Exception("Unable to prepare cloud merge folder");\n                    unzipSafely(remoteArchive, temp);\n                    mergeMissingFiles(new File(temp, "books"), libraryDir);\n                    mergeMissingFiles(new File(temp, "fonts"), fontsDir);\n                    CloudMergePolicy.mergePreferences(readPreferenceValues(new File(temp, "state.json")), prefs);\n                }\n\n                mergedArchive = buildBackup(activity, libraryDir, fontsDir, prefs);\n                BackupInfo latest = findBackupInfo(token);\n                if (remote == null) {\n                    if (latest != null) throw new Exception("Cloud library changed during sync; retrying safely");\n                    createBackup(token, mergedArchive);\n                } else {\n                    if (latest == null || !remote.id.equals(latest.id) ||\n                            !remote.modifiedTime.equals(latest.modifiedTime))\n                        throw new Exception("Cloud library changed during sync; retrying safely");\n                    updateBackup(token, remote.id, mergedArchive);\n                }\n\n                BackupInfo updated = findBackupInfo(token);\n                SharedPreferences.Editor done = prefs.edit()\n                        .putLong("google_last_backup_ms", System.currentTimeMillis());\n                if (updated != null && updated.modifiedTime != null)\n                    done.putString("google_last_seen_remote_modified", updated.modifiedTime);\n                done.apply();\n                activity.runOnUiThread(() -> callback.onSuccess("Google Drive smart sync is up to date"));\n            } catch (Exception e) {\n                String message = friendly(e);\n                activity.runOnUiThread(() -> callback.onError(message));\n            } finally {\n                if (remoteArchive != null) remoteArchive.delete();\n                if (mergedArchive != null) mergedArchive.delete();\n                deleteRecursively(temp);\n            }\n        }, "wow-google-smart-sync").start();\n    }\n\n    static void restore(Activity activity, String token, File libraryDir, File fontsDir,\n                        SharedPreferences prefs, SyncCallback callback) {''',
    'smart backup method',
)

drive = replace_once(
    drive,
    '''    private static void restoreFiles(File source, File destination) throws Exception {''',
    '''    private static JSONObject readPreferenceValues(File stateFile) throws Exception {\n        if (stateFile == null || !stateFile.isFile()) return new JSONObject();\n        String json = new String(readAll(new FileInputStream(stateFile)), StandardCharsets.UTF_8);\n        JSONObject values = new JSONObject(json).optJSONObject("prefs");\n        return values == null ? new JSONObject() : values;\n    }\n\n    private static void mergeMissingFiles(File source, File destination) throws Exception {\n        if (source == null || !source.isDirectory()) return;\n        if (!destination.exists() && !destination.mkdirs())\n            throw new Exception("Unable to create cloud merge destination");\n        File[] files = source.listFiles();\n        if (files == null) return;\n        byte[] buffer = new byte[64 * 1024];\n        for (File file : files) {\n            if (!file.isFile()) continue;\n            File out = new File(destination, file.getName());\n            if (out.exists()) continue;\n            try (InputStream in = new BufferedInputStream(new FileInputStream(file));\n                 OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {\n                int n;\n                while ((n = in.read(buffer)) > 0) os.write(buffer, 0, n);\n            }\n        }\n    }\n\n    private static void restoreFiles(File source, File destination) throws Exception {''',
    'smart merge helpers',
)

drive = replace_once(
    drive,
    '''    private static String findBackupId(String token) throws Exception {\n        String q = "name='" + BACKUP_NAME + "' and trashed=false";\n        String url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&pageSize=10&fields=files(id,name,modifiedTime)&q=" +\n                URLEncoder.encode(q, "UTF-8");\n        JSONObject result = authorizedJson(url, token);\n        JSONArray files = result.optJSONArray("files");\n        if (files == null || files.length() == 0) return null;\n        return files.optJSONObject(0).optString("id", null);\n    }''',
    '''    private static final class BackupInfo {\n        String id = "";\n        String modifiedTime = "";\n    }\n\n    private static BackupInfo findBackupInfo(String token) throws Exception {\n        String q = "name='" + BACKUP_NAME + "' and trashed=false";\n        String url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&pageSize=10&orderBy=modifiedTime%20desc&fields=files(id,name,modifiedTime)&q=" +\n                URLEncoder.encode(q, "UTF-8");\n        JSONObject result = authorizedJson(url, token);\n        JSONArray files = result.optJSONArray("files");\n        if (files == null || files.length() == 0) return null;\n        JSONObject first = files.optJSONObject(0);\n        if (first == null) return null;\n        BackupInfo info = new BackupInfo();\n        info.id = first.optString("id", "");\n        info.modifiedTime = first.optString("modifiedTime", "");\n        return info.id.isEmpty() ? null : info;\n    }\n\n    private static String findBackupId(String token) throws Exception {\n        BackupInfo info = findBackupInfo(token);\n        return info == null ? null : info.id;\n    }''',
    'backup metadata lookup',
)

DRIVE.write_text(drive, encoding="utf-8")

auto = AUTO.read_text(encoding="utf-8")
auto = replace_once(
    auto,
    '                GoogleDriveSync.backup(activity, profile.accessToken, library, fonts, prefs,',
    '                GoogleDriveSync.smartBackup(activity, profile.accessToken, library, fonts, prefs,',
    'route auto sync through smart merge',
)
AUTO.write_text(auto, encoding="utf-8")
print("Smart Sync merge patch applied successfully.")
