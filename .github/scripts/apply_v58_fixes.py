from pathlib import Path


def rep(text, old, new, label, count=1):
    found = text.count(old)
    if found < count:
        raise SystemExit(f"missing marker {label}: found {found}, need {count}")
    return text.replace(old, new, count)


def method_block(text, start_marker, end_marker):
    a = text.find(start_marker)
    if a < 0:
        raise SystemExit("missing method start: " + start_marker)
    b = text.find(end_marker, a)
    if b < 0:
        raise SystemExit("missing method end: " + end_marker)
    return a, b, text[a:b]

# Version
p = Path('app/build.gradle')
s = p.read_text(encoding='utf-8')
s = rep(s, 'versionCode 57', 'versionCode 58', 'versionCode')
s = rep(s, "versionName '2.18.7'", "versionName '2.18.8'", 'versionName')
p.write_text(s, encoding='utf-8')

# Kindle PRC/MOBI import: journal_mode PRAGMA returns a row and must not be execSQL().
p = Path('app/src/main/java/com/whisper/wowreader/KindleDictionaryStore.java')
s = p.read_text(encoding='utf-8')
s = rep(s, '            db.execSQL("PRAGMA journal_mode=DELETE");\n', '', 'remove result-producing pragma')
p.write_text(s, encoding='utf-8')

# Reading ruler: 1..20 lines and proportional band height.
p = Path('app/src/main/java/com/whisper/wowreader/ReadingRulerView.java')
s = p.read_text(encoding='utf-8')
s = rep(s,
        '        lineCount = lines <= 1 ? 1 : lines <= 3 ? 3 : 5;',
        '        lineCount = Math.max(1, Math.min(20, lines));',
        'ruler line clamp')
s = rep(s,
        '        float bandHeight = (lineCount == 1 ? 38f : lineCount == 3 ? 74f : 110f) * density;',
        '        float bandHeight = Math.min(getHeight() * 0.92f, (38f + (lineCount - 1) * 18f) * density);',
        'ruler band height')
p.write_text(s, encoding='utf-8')

# PDF continuous auto-scroll: do not treat transient RecyclerView relayout as document end.
p = Path('app/src/main/java/com/whisper/wowreader/PdfContinuousView.java')
s = p.read_text(encoding='utf-8')
s = rep(s,
'''    void startAutoScroll(int pixelsPerSecond) {
        int speed = Math.max(1, pixelsPerSecond);
        if (pageCount <= 0 || !canScrollVertically(1)) {
            stopAutoScroll();
            return;
        }
        autoPixelsPerSecond = speed;''',
'''    void startAutoScroll(int pixelsPerSecond) {
        int speed = Math.max(1, pixelsPerSecond);
        if (pageCount <= 0) {
            stopAutoScroll();
            return;
        }
        autoPixelsPerSecond = speed;''', 'pdf start autoscroll')
s = rep(s,
'''            int pixels = (int) autoCarry;
            if (pixels > 0) {
                autoCarry -= pixels;
                scrollBy(0, pixels);
            }
            if (!canScrollVertically(1)) {
                stopAutoScroll();
                reportCurrentPage();
                return;
            }
            postOnAnimation(this);''',
'''            int pixels = (int) autoCarry;
            if (pixels > 0 && canScrollVertically(1)) {
                autoCarry -= pixels;
                scrollBy(0, pixels);
            } else if (!canScrollVertically(1) && isAtDocumentEnd()) {
                stopAutoScroll();
                reportCurrentPage();
                return;
            }
            postOnAnimation(this);''', 'pdf autoscroll tick')
s = rep(s,
'''    private void reportCurrentPage() {
        if (pageCount <= 0) return;''',
'''    private boolean isAtDocumentEnd() {
        if (pageCount <= 0 || getChildCount() == 0) return false;
        int last = layout.findLastVisibleItemPosition();
        if (last < pageCount - 1) return false;
        View end = layout.findViewByPosition(pageCount - 1);
        if (end == null) return false;
        return end.getBottom() <= getHeight() - getPaddingBottom() + dp(2);
    }

    private void reportCurrentPage() {
        if (pageCount <= 0) return;''', 'pdf real end helper')
p.write_text(s, encoding='utf-8')

# Xiaomi/HyperOS manual Sync now: never launch an implicit authorization resolution from Sync now.
p = Path('app/src/main/java/com/whisper/wowreader/MainActivity.java')
s = p.read_text(encoding='utf-8')
a, b, block = method_block(s, '    private void performGoogleBackup(boolean showToast){', '    private void confirmGoogleRestore(){')
block = rep(block,
            '        googleDrive.authorize(false,new GoogleDriveSync.AuthCallback(){',
            '        googleDrive.authorizeSilently(new GoogleDriveSync.AuthCallback(){',
            'manual sync silent authorization')
s = s[:a] + block + s[b:]
p.write_text(s, encoding='utf-8')

# Reader fixes.
p = Path('app/src/main/java/com/whisper/wowreader/BookReaderActivity.java')
s = p.read_text(encoding='utf-8')

s = rep(s,
        '        readingRulerLines = savedRulerLines <= 1 ? 1 : savedRulerLines <= 3 ? 3 : 5;',
        '        readingRulerLines = Math.max(1, Math.min(20, savedRulerLines));',
        'load ruler size')

# Display Options must hold auto-scroll paused for the entire sheet lifetime.
s = rep(s,
'''        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);''',
'''        beginAutoScrollInteraction();
        final Dialog dialog = new Dialog(this);
        dialog.setOnDismissListener(d -> endAutoScrollInteraction());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);''', 'display options autoscroll lock')

# Ruler size gets a slider instead of the old 1/3/5 cycle.
s = rep(s,
'''        rulerSize.setOnClickListener(v -> {
            readingRulerLines = readingRulerLines == 1 ? 3 : readingRulerLines == 3 ? 5 : 1;
            rulerSize.setText("Lines · " + readingRulerLines);
            if (readingRulerView != null) readingRulerView.configure(readingRulerEnabled, readingRulerLines, readerTheme);
            saveReaderPreferences();
        });''',
'''        rulerSize.setOnClickListener(v -> showReadingRulerSizeDialog(rulerSize));''', 'ruler slider launch')

# Keep More Reader Settings itself locked while it is open.
s = rep(s,
'''        java.util.ArrayList<String> rows = new java.util.ArrayList<>();
        for (String item : buildAdvancedReaderOptions()) rows.add(item);''',
'''        beginAutoScrollInteraction();
        java.util.ArrayList<String> rows = new java.util.ArrayList<>();
        for (String item : buildAdvancedReaderOptions()) rows.add(item);''', 'advanced settings lock')
s = rep(s,
'''        advancedReaderDialog.setOnDismissListener(d -> { advancedReaderDialog = null; advancedReaderAdapter = null; });''',
'''        advancedReaderDialog.setOnDismissListener(d -> {
            advancedReaderDialog = null;
            advancedReaderAdapter = null;
            endAutoScrollInteraction();
        });''', 'advanced settings unlock')

# PDF settings must also pause auto-scroll.
a, b, block = method_block(s, '    private void showPdfSettings() {', '    private void showFontSizeDialog() {')
block = rep(block,
'''    private void showPdfSettings() {
        String[] options = new String[]{''',
'''    private void showPdfSettings() {
        beginAutoScrollInteraction();
        String[] options = new String[]{''', 'pdf settings lock')
block = rep(block,
'''        new AlertDialog.Builder(this)
                .setTitle("PDF reader settings")''',
'''        AlertDialog pdfSettingsDialog = new AlertDialog.Builder(this)
                .setTitle("PDF reader settings")''', 'pdf settings dialog var')
block = rep(block,
'''                .setNegativeButton("Close", null)
                .show();''',
'''                .setNegativeButton("Close", null)
                .create();
        pdfSettingsDialog.setOnDismissListener(d -> endAutoScrollInteraction());
        pdfSettingsDialog.show();''', 'pdf settings unlock')
s = s[:a] + block + s[b:]

# Auto-scroll speed picker may be opened after Display Options dismisses.
a, b, block = method_block(s, '    private void showAutoScrollSpeedDialog() {', '    private int autoScrollPixelsPerSecond() {')
block = rep(block,
'''    private void showAutoScrollSpeedDialog() {
        LinearLayout box = new LinearLayout(this);''',
'''    private void showAutoScrollSpeedDialog() {
        beginAutoScrollInteraction();
        LinearLayout box = new LinearLayout(this);''', 'speed dialog lock')
block = rep(block,
'''        new AlertDialog.Builder(this)
                .setTitle("Auto scroll speed")''',
'''        AlertDialog speedDialog = new AlertDialog.Builder(this)
                .setTitle("Auto scroll speed")''', 'speed dialog var')
block = rep(block,
'''                .setPositiveButton("Done", (d, w) -> updateAutoScrollState())
                .show();''',
'''                .setPositiveButton("Done", (d, w) -> updateAutoScrollState())
                .create();
        speedDialog.setOnDismissListener(d -> endAutoScrollInteraction());
        speedDialog.show();''', 'speed dialog unlock')
s = s[:a] + block + s[b:]

# Font picker may also be opened after the main sheet dismisses.
a, b, block = method_block(s, '    private void showFontDialog() {', '    private void pickCustomFont() {')
block = rep(block,
'''    private void showFontDialog() {
        List<ReaderFontStore.FontEntry> custom = ReaderFontStore.list(this);''',
'''    private void showFontDialog() {
        beginAutoScrollInteraction();
        List<ReaderFontStore.FontEntry> custom = ReaderFontStore.list(this);''', 'font dialog lock')
block = rep(block,
'''        new AlertDialog.Builder(this)
                .setTitle("Font")''',
'''        AlertDialog fontDialog = new AlertDialog.Builder(this)
                .setTitle("Font")''', 'font dialog var')
block = rep(block,
'''                .setNegativeButton("Cancel", null)
                .show();''',
'''                .setNegativeButton("Cancel", null)
                .create();
        fontDialog.setOnDismissListener(d -> endAutoScrollInteraction());
        fontDialog.show();''', 'font dialog unlock')
s = s[:a] + block + s[b:]

# Brightness can be reached from PDF settings after that list dialog auto-dismisses.
a, b, block = method_block(s, '    private void showBrightnessDialog() {', '    private void resetReaderPreferences() {')
block = rep(block,
'''    private void showBrightnessDialog() {
        final int[] values = {-1, 40, 60, 80, 100};''',
'''    private void showBrightnessDialog() {
        beginAutoScrollInteraction();
        final int[] values = {-1, 40, 60, 80, 100};''', 'brightness lock')
block = rep(block,
'''        new AlertDialog.Builder(this)
                .setTitle("Brightness")''',
'''        AlertDialog brightnessDialog = new AlertDialog.Builder(this)
                .setTitle("Brightness")''', 'brightness var')
block = rep(block,
'''                .setNegativeButton("Cancel", null)
                .show();''',
'''                .setNegativeButton("Cancel", null)
                .create();
        brightnessDialog.setOnDismissListener(d -> endAutoScrollInteraction());
        brightnessDialog.show();''', 'brightness unlock')
s = s[:a] + block + s[b:]

# Final page of final EPUB spine is always 100%, including a one-page final chapter.
s = rep(s,
'''    private void updateEpubPageProgress(int page, int count, int p) {
        currentPageInChapter = Math.max(1, page);
        pageCountInChapter = Math.max(1, count);
        updateEpubProgress(p);
        saveEpubStateOnly();
    }''',
'''    private void updateEpubPageProgress(int page, int count, int p) {
        currentPageInChapter = Math.max(1, page);
        pageCountInChapter = Math.max(1, count);
        int effectiveProgress = p;
        if (!spine.isEmpty() && currentSpine == spine.size() - 1 && currentPageInChapter >= pageCountInChapter)
            effectiveProgress = 1000;
        updateEpubProgress(effectiveProgress);
        saveEpubStateOnly();
    }''', 'page mode finished state')

# Larger-screen margins: use a CSS-pixel cap rather than unbounded viewport percentage.
s = s.replace('        int safeMargin = Math.max(3, Math.min(14, marginPercent));',
              '        int safeMargin = Math.max(1, Math.min(14, marginPercent));\n        int adaptiveMargin = adaptiveReaderMarginCssPx(safeMargin);')
if s.count('int adaptiveMargin = adaptiveReaderMarginCssPx(safeMargin);') != 2:
    raise SystemExit('expected two adaptive margin sites')
s = rep(s,
        'Math.round(w*' + '" + (safeMargin / 100.0) + "' + ')',
        'Math.min(Math.round(w*' + '" + (safeMargin / 100.0) + "' + '),' + '" + adaptiveMargin + "' + ')',
        'preload page margin cap')
s = rep(s,
        '"padding:5vh " + safeMargin + "vw 12vh " + safeMargin + "vw !important;',
        '"padding:5vh " + adaptiveMargin + "px 12vh " + adaptiveMargin + "px !important;',
        'preload scroll margin')
s = rep(s,
        'st.marginRatio=' + '" + (safeMargin / 100.0) + "' + ';',
        'st.marginRatio=' + '" + (safeMargin / 100.0) + "' + ';st.marginCap=' + '" + adaptiveMargin + "' + ';',
        'page engine margin cap property')
s = rep(s,
        'm=Math.max(0,Math.round(w*st.marginRatio))',
        'm=Math.max(0,Math.min(Math.round(w*st.marginRatio),st.marginCap||9999))',
        'page engine margin cap use')
s = rep(s,
        '"padding:5vh " + safeMargin + "vw 12vh " + safeMargin + "vw !important;" +',
        '"padding:5vh " + adaptiveMargin + "px 12vh " + adaptiveMargin + "px !important;" +',
        'reader scroll margin')

s = rep(s,
        '        int[] marginValues = {4, 7, 11};',
        '        int[] marginValues = {2, 5, 10};', 'display margin values')
s = rep(s,
        '        int marginSelected = marginPercent <= 5 ? 0 : (marginPercent >= 9 ? 2 : 1);',
        '        int marginSelected = marginPercent <= 3 ? 0 : (marginPercent >= 8 ? 2 : 1);', 'display margin selected')
s = rep(s,
        '        final int[] values = {3, 5, 7, 9, 12};\n        String[] labels = {"Extra narrow", "Narrow · default", "Medium", "Wide", "Extra wide"};',
        '        final int[] values = {1, 2, 5, 8, 12};\n        String[] labels = {"Extra narrow", "Narrow", "Medium · default", "Wide", "Extra wide"};',
        'advanced margin values')

# Add ruler-size dialog and adaptive margin helper before addSheetLabel.
marker = '    private void addSheetLabel(LinearLayout parent, String label, int color) {'
helper = '''    private void showReadingRulerSizeDialog(TextView target) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), dp(4));
        TextView value = new TextView(this);
        value.setText("Lines · " + readingRulerLines);
        value.setTextSize(14f);
        value.setTextColor(readerPanelText());
        value.setGravity(Gravity.CENTER);
        box.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        SeekBar seek = new SeekBar(this);
        seek.setMax(19);
        seek.setProgress(Math.max(0, Math.min(19, readingRulerLines - 1)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                readingRulerLines = progress + 1;
                value.setText("Lines · " + readingRulerLines);
                if (target != null) target.setText("Lines · " + readingRulerLines);
                if (readingRulerView != null)
                    readingRulerView.configure(readingRulerEnabled, readingRulerLines, readerTheme);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) { saveReaderPreferences(); }
        });
        box.addView(seek, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        new AlertDialog.Builder(this)
                .setTitle("Reading ruler size")
                .setMessage("Choose from 1 to 20 text lines")
                .setView(box)
                .setPositiveButton("Done", (d, w) -> saveReaderPreferences())
                .show();
    }

    private int adaptiveReaderMarginCssPx(int percent) {
        int p = Math.max(1, Math.min(14, percent));
        int width = Math.max(1, getResources().getConfiguration().screenWidthDp);
        int desired = Math.max(4, Math.round(width * (p / 100f)));
        int cap;
        if (p <= 1) cap = 10;
        else if (p <= 2) cap = 18;
        else if (p <= 5) cap = 40;
        else if (p <= 8) cap = 68;
        else cap = 96;
        return Math.max(4, Math.min(desired, cap));
    }

''' + marker
s = rep(s, marker, helper, 'reader helper insertion')

p.write_text(s, encoding='utf-8')
print('v58 fixes applied')
