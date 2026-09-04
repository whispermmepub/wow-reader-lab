from pathlib import Path

p = Path('app/src/main/java/com/whisper/wowreader/BookReaderActivity.java')
s = p.read_text()
if 'private void showPdfSettings()' in s:
    print('showPdfSettings already present')
    raise SystemExit(0)

marker = '''    private void showFontSizeDialog() {'''
if marker not in s:
    raise SystemExit('showFontSizeDialog marker missing')

method = '''    private void showPdfSettings() {
        String[] options = new String[]{
                "Brightness · " + brightnessDisplayName(),
                "Keep screen on · " + onOff(keepScreenOn),
                "Lock orientation · " + onOff(lockOrientation)
        };

        new AlertDialog.Builder(this)
                .setTitle("Reader settings")
                .setItems(options, (d, which) -> {
                    if (which == 0) showBrightnessDialog();
                    else if (which == 1) {
                        keepScreenOn = !keepScreenOn;
                        saveReaderPreferences();
                        applyWindowPreferences();
                        showPdfSettings();
                    } else if (which == 2) {
                        lockOrientation = !lockOrientation;
                        saveReaderPreferences();
                        applyWindowPreferences();
                        showPdfSettings();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

'''
p.write_text(s.replace(marker, method + marker, 1))
print('restored showPdfSettings')
