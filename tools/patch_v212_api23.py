from pathlib import Path
p = Path('app/src/main/java/com/whisper/wowreader/GoogleDriveSync.java')
s = p.read_text(encoding='utf-8')
old = '''    interface SyncCallback {
        void onSuccess(String message);
        void onError(String message);
    }
'''
new = '''    interface SyncCallback {
        void onSuccess(String message);
        void onError(String message);
    }

    interface BackupCheckCallback {
        void onResult(boolean found);
    }
'''
if old not in s:
    raise SystemExit('sync callback anchor missing')
s = s.replace(old, new, 1)
old = '''    static void hasBackup(Activity activity, String token, java.util.function.Consumer<Boolean> callback) {
        new Thread(() -> {
            boolean found = false;
            try { found = findBackupId(token) != null; } catch (Exception ignored) {}
            final boolean value = found;
            activity.runOnUiThread(() -> callback.accept(value));
        }, "wow-google-backup-check").start();
    }
'''
new = '''    static void hasBackup(Activity activity, String token, BackupCheckCallback callback) {
        new Thread(() -> {
            boolean found = false;
            try { found = findBackupId(token) != null; } catch (Exception ignored) {}
            final boolean value = found;
            activity.runOnUiThread(() -> callback.onResult(value));
        }, "wow-google-backup-check").start();
    }
'''
if old not in s:
    raise SystemExit('backup callback anchor missing')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
print('Removed java.util.function dependency for API 23')
