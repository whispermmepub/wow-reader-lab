from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/whisper/wowreader/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")
old = 'GoogleDriveSync.backup(MainActivity.this,driveProfile.accessToken,libraryDir,readerFontsDir(),prefs,new GoogleDriveSync.SyncCallback(){'
new = 'GoogleDriveSync.smartBackup(MainActivity.this,driveProfile.accessToken,libraryDir,readerFontsDir(),prefs,new GoogleDriveSync.SyncCallback(){'
count = text.count(old)
if count != 1:
    raise RuntimeError(f"manual backup route: expected exactly one anchor, found {count}")
MAIN.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Manual backup now uses Smart Sync merge.")
