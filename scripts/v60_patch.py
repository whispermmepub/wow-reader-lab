from pathlib import Path
import re


def replace_once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise SystemExit(f"{label}: expected 1 match, got {n}")
    return text.replace(old, new, 1)

# Version
p = Path('app/build.gradle')
s = p.read_text()
s = replace_once(s, 'versionCode 59', 'versionCode 60', 'versionCode')
s = replace_once(s, "versionName '2.18.9'", "versionName '2.19.0'", 'versionName')
p.write_text(s)

# Reader whole-book auto scroll
p = Path('app/src/main/java/com/whisper/wowreader/BookReaderActivity.java')
s = p.read_text()

s = replace_once(s,
'''    private boolean autoScrollEnabled = false;
    private int autoScrollSpeed = 4;''',
'''    private boolean autoScrollEnabled = false;
    // Whole-book scope keeps auto scroll running across EPUB spine/chapter boundaries.
    private boolean autoScrollWholeBook = true;
    // True only for a preloaded automatic handoff, so no chapter-transition overlay is shown.
    private boolean autoScrollSeamlessHandoff = false;
    private int autoScrollSpeed = 4;''',
'auto scroll fields')

s = replace_once(s,
'''        autoScrollEnabled = prefs.getBoolean("reader_auto_scroll_enabled", false);
        autoScrollSpeed = Math.max(1, Math.min(10, prefs.getInt("reader_auto_scroll_speed", 4)));''',
'''        autoScrollEnabled = prefs.getBoolean("reader_auto_scroll_enabled", false);
        autoScrollWholeBook = prefs.getBoolean("reader_auto_scroll_whole_book", true);
        autoScrollSpeed = Math.max(1, Math.min(10, prefs.getInt("reader_auto_scroll_speed", 4)));''',
'load auto scroll scope')

s = replace_once(s,
'''        autoScrollEnabled = false;
        autoScrollSpeed = 4;''',
'''        autoScrollEnabled = false;
        autoScrollWholeBook = true;
        autoScrollSeamlessHandoff = false;
        autoScrollSpeed = 4;''',
'reset auto scroll scope')

s = replace_once(s,
'''                .putBoolean("reader_auto_scroll_enabled", autoScrollEnabled)
                .putInt("reader_auto_scroll_speed", Math.max(1, Math.min(10, autoScrollSpeed)))''',
'''                .putBoolean("reader_auto_scroll_enabled", autoScrollEnabled)
                .putBoolean("reader_auto_scroll_whole_book", autoScrollWholeBook)
                .putInt("reader_auto_scroll_speed", Math.max(1, Math.min(10, autoScrollSpeed)))''',
'save auto scroll scope')

# Display Options: add a third Auto scroll chip for Chapter/Whole book scope.
s = replace_once(s,
'''        TextView autoToggle = sheetChip(autoScrollEnabled ? "On" : "Off", autoScrollEnabled);
        TextView autoSpeed = sheetChip("Speed · " + autoScrollSpeedDisplay(), false);''',
'''        TextView autoToggle = sheetChip(autoScrollEnabled ? "On" : "Off", autoScrollEnabled);
        TextView autoSpeed = sheetChip("Speed · " + autoScrollSpeedDisplay(), false);
        TextView autoScope = sheetChip(autoScrollWholeBook ? "Whole book" : "Chapter", autoScrollWholeBook);''',
'display auto scroll scope chip')

s = replace_once(s,
'''        autoSpeed.setOnClickListener(v -> { dialog.dismiss(); showAutoScrollSpeedDialog(); });
        autoScrollRow.addView(autoToggle, sheetChipLp(false));
        autoScrollRow.addView(autoSpeed, sheetChipLp(true));
        card.addView(autoScrollRow);''',
'''        autoSpeed.setOnClickListener(v -> { dialog.dismiss(); showAutoScrollSpeedDialog(); });
        autoScope.setOnClickListener(v -> {
            autoScrollWholeBook = !autoScrollWholeBook;
            autoScope.setText(autoScrollWholeBook ? "Whole book" : "Chapter");
            styleSheetChip(autoScope, autoScrollWholeBook);
            saveReaderPreferences();
            autoScrollResumeToken++;
            updateAutoScrollState();
        });
        autoScrollRow.addView(autoToggle, sheetChipLp(false));
        autoScrollRow.addView(autoSpeed, sheetChipLp(true));
        autoScrollRow.addView(autoScope, sheetChipLp(true));
        card.addView(autoScrollRow);''',
'display auto scroll scope listener')

# Advanced settings gets scope row.
s = replace_once(s,
'''                "Auto scroll · " + onOff(autoScrollEnabled),
                "Auto scroll speed · " + autoScrollSpeedDisplay(),
                "30-minute eye reminder · " + onOff(eyeBreakReminderEnabled),
                "Reset reader settings"''',
'''                "Auto scroll · " + onOff(autoScrollEnabled),
                "Auto scroll speed · " + autoScrollSpeedDisplay(),
                "Auto scroll scope · " + (autoScrollWholeBook ? "Whole book" : "Chapter"),
                "30-minute eye reminder · " + onOff(eyeBreakReminderEnabled),
                "Reset reader settings"''',
'advanced scope row')

s = replace_once(s,
'''                case 14: showAutoScrollSpeedDialog(); break;
                case 15:
                    eyeBreakReminderEnabled = !eyeBreakReminderEnabled; saveReaderPreferences();
                    if (eyeBreakReminderEnabled) scheduleEyeBreakReminder(); else cancelEyeBreakReminder(); break;
                case 16: resetReaderPreferences(); break;''',
'''                case 14: showAutoScrollSpeedDialog(); break;
                case 15:
                    autoScrollWholeBook = !autoScrollWholeBook; saveReaderPreferences(); autoScrollResumeToken++; updateAutoScrollState(); break;
                case 16:
                    eyeBreakReminderEnabled = !eyeBreakReminderEnabled; saveReaderPreferences();
                    if (eyeBreakReminderEnabled) scheduleEyeBreakReminder(); else cancelEyeBreakReminder(); break;
                case 17: resetReaderPreferences(); break;''',
'advanced scope switch')

# Whole-book end behavior. Chapter scope stops at the current chapter; Whole-book continues.
s = replace_once(s,
'''                if (currentSpine >= 0 && currentSpine < spine.size() - 1) {
                    autoScrollAdvancePending = true;
                    navigateChapter(1, false);
                } else {
                    autoScrollAdvancePending = false;
                    stopAutoScrollEngine();
                }''',
'''                updateEpubProgress(1000);
                saveEpubStateOnly();
                if (autoScrollWholeBook && currentSpine >= 0 && currentSpine < spine.size() - 1) {
                    autoScrollAdvancePending = true;
                    navigateChapter(1, false);
                } else {
                    autoScrollAdvancePending = false;
                    autoScrollSeamlessHandoff = false;
                    stopAutoScrollEngine();
                }''',
'auto scroll whole book end behavior')

# Promote automaticForward to method scope and use preloaded chapter as a seamless handoff.
s = replace_once(s,
'''        int targetProgressPermille = restoreEnd ? 1000 : 0;
        if ("scroll".equals(readingMode) && autoScrollEnabled) {
            final boolean automaticForward = delta > 0 && autoScrollAdvancePending;''',
'''        int targetProgressPermille = restoreEnd ? 1000 : 0;
        final boolean automaticForward = "scroll".equals(readingMode) && autoScrollEnabled && delta > 0 && autoScrollAdvancePending;
        if ("scroll".equals(readingMode) && autoScrollEnabled) {''',
'navigate automatic forward scope')

s = replace_once(s,
'''        preferredPreloadDirection = delta < 0 ? -1 : 1;
        prepareChapterTransition(delta);
        lastChapterNavMs = now;''',
'''        preferredPreloadDirection = delta < 0 ? -1 : 1;
        boolean seamlessReady = automaticForward && autoScrollWholeBook && preloadReady && preloadedSpine == target;
        autoScrollSeamlessHandoff = seamlessReady;
        if (!seamlessReady) prepareChapterTransition(delta);
        lastChapterNavMs = now;''',
'seamless preloaded navigation')

# Give a loading prefetch a little longer to finish during whole-book auto-scroll.
s = replace_once(s,
'''            }, 100L);
            return;
        }
        cancelChapterPreload();''',
'''            }, autoScrollWholeBook && autoScrollAdvancePending ? 260L : 100L);
            return;
        }
        autoScrollSeamlessHandoff = false;
        cancelChapterPreload();''',
'preload wait')

# A preloaded whole-book handoff is already laid out. Show it directly and complete quickly.
s = replace_once(s,
'''        incoming.setTranslationX(0f);
        incoming.setAlpha(0f);
        incoming.bringToFront();''',
'''        incoming.setTranslationX(0f);
        incoming.setAlpha(autoScrollSeamlessHandoff && "scroll".equals(readingMode) ? 1f : 0f);
        incoming.bringToFront();''',
'preload handoff alpha')

s = replace_once(s,
'''        webView.postDelayed(() -> {
            if (generation == chapterLoadGeneration && chapterLoading && "scroll".equals(readingMode))
                completePageReady(generation);
        }, 850L);''',
'''        webView.postDelayed(() -> {
            if (generation == chapterLoadGeneration && chapterLoading && "scroll".equals(readingMode))
                completePageReady(generation);
        }, autoScrollSeamlessHandoff ? 140L : 850L);''',
'preload handoff ready delay')

# Skip the multi-frame hidden reveal check for an already-preloaded automatic whole-book handoff.
s = replace_once(s,
'''            if (finishPendingChapterCurl()) return;
            revealStableChapter();
        });''',
'''            if (finishPendingChapterCurl()) return;
            if (autoScrollSeamlessHandoff && "scroll".equals(readingMode)) {
                finishStableChapterReveal();
                return;
            }
            revealStableChapter();
        });''',
'seamless ready reveal')

s = replace_once(s,
'''        prewarmAdjacentChapters();
        scheduleAdjacentChapterPreload(preferredPreloadDirection);
        autoScrollAdvancePending = false;
        updateAutoScrollState();''',
'''        prewarmAdjacentChapters();
        scheduleAdjacentChapterPreload(preferredPreloadDirection);
        autoScrollAdvancePending = false;
        autoScrollSeamlessHandoff = false;
        updateAutoScrollState();''',
'reset seamless state')

p.write_text(s)

# Kindle dictionary: normalize old visual-order Myanmar Unicode used by many legacy Kindle dictionaries.
p = Path('app/src/main/java/com/whisper/wowreader/KindleDictionaryStore.java')
s = p.read_text()

old_html = '''    private static String htmlToText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            CharSequence cs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY)
                    : Html.fromHtml(raw);
            return cs.toString().replace('\\u00A0', ' ').replaceAll("[ \\\\t]+", " ")
                    .replaceAll("\\\\n[ \\\\t]+", "\\\\n").replaceAll("\\\\n{3,}", "\\\\n\\\\n").trim();
        } catch (Exception ignored) {
            return raw.replaceAll("<[^>]+>", " ").replaceAll("\\\\s+", " ").trim();
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\\\s+", " ");
    }'''

new_html = '''    private static String htmlToText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            CharSequence cs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY)
                    : Html.fromHtml(raw);
            String clean = cs.toString().replace('\\u00A0', ' ').replaceAll("[ \\\\t]+", " ")
                    .replaceAll("\\\\n[ \\\\t]+", "\\\\n").replaceAll("\\\\n{3,}", "\\\\n\\\\n").trim();
            return normalizeLegacyMyanmarOrder(clean);
        } catch (Exception ignored) {
            String clean = raw.replaceAll("<[^>]+>", " ").replaceAll("\\\\s+", " ").trim();
            return normalizeLegacyMyanmarOrder(clean);
        }
    }

    /**
     * Some older Myanmar Kindle dictionaries label themselves Unicode but store glyphs in
     * visual order: U+1031 (ေ) and medial RA U+103C (ြ) can appear before the consonant.
     * Modern Android shaping expects logical Unicode order. Convert only those unambiguous
     * legacy patterns and remove dictionary-only zero-width syllable separators.
     */
    static String normalizeLegacyMyanmarOrder(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder out = new StringBuilder(value.length());
        int i = 0;
        while (i < value.length()) {
            char ch = value.charAt(i);
            if (ch == '\\u200B') { i++; continue; }

            if (ch == '\\u1031') {
                int j = i + 1;
                boolean preRa = false;
                if (j < value.length() && value.charAt(j) == '\\u103C') { preRa = true; j++; }
                if (j < value.length() && isMyanmarBase(value.charAt(j))) {
                    char base = value.charAt(j++);
                    boolean ya = false, ra = preRa, wa = false, ha = false;
                    while (j < value.length() && isMyanmarMedial(value.charAt(j))) {
                        char m = value.charAt(j++);
                        if (m == '\\u103B') ya = true;
                        else if (m == '\\u103C') ra = true;
                        else if (m == '\\u103D') wa = true;
                        else if (m == '\\u103E') ha = true;
                    }
                    out.append(base);
                    if (ya) out.append('\\u103B');
                    if (ra) out.append('\\u103C');
                    if (wa) out.append('\\u103D');
                    if (ha) out.append('\\u103E');
                    out.append('\\u1031');
                    i = j;
                    continue;
                }
            }

            // Medial RA is the other sign legacy visual-order dictionaries commonly place
            // before its consonant. In valid modern text it directly follows its base.
            if (ch == '\\u103C' && i + 1 < value.length() && isMyanmarBase(value.charAt(i + 1))) {
                char previous = i > 0 ? value.charAt(i - 1) : 0;
                if (i == 0 || !isMyanmarBase(previous)) {
                    char base = value.charAt(i + 1);
                    int j = i + 2;
                    boolean ya = false, ra = true, wa = false, ha = false;
                    while (j < value.length() && isMyanmarMedial(value.charAt(j))) {
                        char m = value.charAt(j++);
                        if (m == '\\u103B') ya = true;
                        else if (m == '\\u103C') ra = true;
                        else if (m == '\\u103D') wa = true;
                        else if (m == '\\u103E') ha = true;
                    }
                    out.append(base);
                    if (ya) out.append('\\u103B');
                    if (ra) out.append('\\u103C');
                    if (wa) out.append('\\u103D');
                    if (ha) out.append('\\u103E');
                    i = j;
                    continue;
                }
            }

            out.append(ch);
            i++;
        }
        return out.toString();
    }

    private static boolean isMyanmarBase(char ch) {
        return (ch >= '\\u1000' && ch <= '\\u102A') || ch == '\\u103F' || ch == '\\u104E';
    }

    private static boolean isMyanmarMedial(char ch) {
        return ch >= '\\u103B' && ch <= '\\u103E';
    }

    private static String normalize(String value) {
        return value == null ? "" : normalizeLegacyMyanmarOrder(value).trim().toLowerCase(Locale.ROOT).replaceAll("\\\\s+", " ");
    }'''

s = replace_once(s, old_html, new_html, 'dictionary Myanmar normalization')

# Clarify compression failure for unsupported modern HuffDic MOBI dictionaries.
s = replace_once(s,
'''            if (compression != 1 && compression != 2)
                throw new Exception("This Kindle dictionary compression is not supported");''',
'''            if (compression == 17480)
                throw new Exception("This MOBI dictionary uses HUFF/CDIC compression, which is not supported yet");
            if (compression != 1 && compression != 2)
                throw new Exception("This Kindle dictionary compression is not supported");''',
'compression message')

p.write_text(s)

# Dictionary manager: explicitly expose PRC and MOBI in picker/UI and common Android MIME types.
p = Path('app/src/main/java/com/whisper/wowreader/DictionaryManagerActivity.java')
s = p.read_text()
s = replace_once(s,
'''        TextView importButton = button(installed ? "Replace Kindle dictionary" : "Import Kindle dictionary");''',
'''        TextView importButton = button(installed ? "Replace PRC / MOBI dictionary" : "Import PRC / MOBI dictionary");''',
'dictionary import button')

s = replace_once(s,
'''        TextView note = label("Kindle import supports unencrypted Unicode PalmDOC/MOBI7 dictionaries. " +
                        "The imported file is indexed privately; WoW Reader does not upload it.",''',
'''        TextView note = label("Kindle import supports DRM-free Unicode .prc and .mobi PalmDOC/MOBI7 dictionaries. " +
                        "Legacy Myanmar visual-order text is normalized for modern Android display. The imported file is indexed privately; WoW Reader does not upload it.",''',
'dictionary import note')

s = replace_once(s,
'''        pick.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "application/x-mobipocket-ebook", "application/vnd.amazon.ebook"});''',
'''        pick.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/octet-stream", "application/x-mobipocket-ebook", "application/vnd.amazon.ebook",
                "application/x-mobi", "application/mobi", "application/vnd.amazon.mobi8-ebook"
        });''',
'mobi MIME types')

p.write_text(s)
