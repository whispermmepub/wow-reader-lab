from pathlib import Path


def one(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing anchor: {label}")
    return text.replace(old, new, 1)


# Version bump.
p = Path("app/build.gradle")
s = p.read_text()
s = one(s, "versionCode 50", "versionCode 51", "versionCode")
s = one(s, "versionName '2.18.0'", "versionName '2.18.1'", "versionName")
p.write_text(s)

# Reader: auto-scroll chapter return + font size 300%.
p = Path("app/src/main/java/com/whisper/wowreader/BookReaderActivity.java")
s = p.read_text()

fields_old = '''    private boolean autoScrollAdvancePending = false;\n    private boolean eyeBreakReminderEnabled = true;'''
fields_new = '''    private boolean autoScrollAdvancePending = false;\n    // Remembers a user-initiated Scroll-mode Next so Previous can return to the prior reading point.\n    private int autoScrollManualReturnSpine = -1;\n    private int autoScrollManualReturnProgress = 0;\n    private int autoScrollManualReturnFromSpine = -1;\n    private boolean eyeBreakReminderEnabled = true;'''
s = one(s, fields_old, fields_new, "auto-scroll return fields")

s = one(
    s,
    '        fontPercent = prefs.getInt("epub_font", 115);',
    '        fontPercent = Math.max(80, Math.min(300, prefs.getInt("epub_font", 115)));',
    "font pref clamp",
)

s = one(
    s,
    '            fontPercent = bookStyle.fontPercent;',
    '            fontPercent = Math.max(80, Math.min(300, bookStyle.fontPercent));',
    "book font clamp",
)

s = one(
    s,
    '        plusFont.setOnClickListener(v -> { fontPercent = Math.min(200, fontPercent + 10); fontValue.setText(fontPercent + "%"); saveReaderPreferences(); applyReaderStyleSmooth(true); });',
    '        plusFont.setOnClickListener(v -> { fontPercent = Math.min(300, fontPercent + 10); fontValue.setText(fontPercent + "%"); saveReaderPreferences(); applyReaderStyleSmooth(true); });',
    "font plus max",
)

s = one(
    s,
    '        final int[] values = {80, 90, 100, 110, 115, 125, 140, 160, 180, 200};',
    '        final int[] values = {80, 90, 100, 110, 115, 125, 140, 160, 180, 200, 220, 240, 260, 280, 300};',
    "font dialog values",
)

nav_old = '''        int target = currentSpine + delta;\n        if (target < 0 || target >= spine.size()) {\n            pageTurnLocked = false;\n            return;\n        }\n\n        preferredPreloadDirection = delta < 0 ? -1 : 1;\n        prepareChapterTransition(delta);\n        lastChapterNavMs = now;\n        currentSpine = target;\n        currentProgressPermille = restoreEnd ? 1000 : 0;\n        saveEpubStateOnly();\n        loadCurrentEpubChapter();'''
nav_new = '''        int target = currentSpine + delta;\n        if (target < 0 || target >= spine.size()) {\n            pageTurnLocked = false;\n            return;\n        }\n\n        int targetProgressPermille = restoreEnd ? 1000 : 0;\n        if (\"scroll\".equals(readingMode) && autoScrollEnabled) {\n            final boolean automaticForward = delta > 0 && autoScrollAdvancePending;\n            if (delta > 0 && !automaticForward) {\n                // User pressed Next: remember this exact location for a possible immediate Previous.\n                autoScrollManualReturnSpine = currentSpine;\n                autoScrollManualReturnProgress = currentProgressPermille;\n                autoScrollManualReturnFromSpine = target;\n            } else if (delta > 0) {\n                // Automatic chapter advance must not leave stale manual-return history behind.\n                autoScrollManualReturnSpine = -1;\n                autoScrollManualReturnProgress = 0;\n                autoScrollManualReturnFromSpine = -1;\n            } else if (delta < 0) {\n                if (currentSpine == autoScrollManualReturnFromSpine && target == autoScrollManualReturnSpine) {\n                    int remembered = Math.max(0, Math.min(1000, autoScrollManualReturnProgress));\n                    // A remembered literal chapter end would immediately bounce forward again.\n                    targetProgressPermille = remembered >= 995 ? 0 : remembered;\n                } else {\n                    // With Auto scroll enabled, Previous opens from the chapter start instead of its end.\n                    targetProgressPermille = 0;\n                }\n                autoScrollManualReturnSpine = -1;\n                autoScrollManualReturnProgress = 0;\n                autoScrollManualReturnFromSpine = -1;\n            }\n            autoScrollResumeToken++;\n            stopAutoScrollEngine();\n        }\n\n        preferredPreloadDirection = delta < 0 ? -1 : 1;\n        prepareChapterTransition(delta);\n        lastChapterNavMs = now;\n        currentSpine = target;\n        currentProgressPermille = targetProgressPermille;\n        saveEpubStateOnly();\n        loadCurrentEpubChapter();'''
s = one(s, nav_old, nav_new, "navigateChapter auto-scroll return")

p.write_text(s)

# Library: custom title/author editor with protected metadata override.
p = Path("app/src/main/java/com/whisper/wowreader/MainActivity.java")
s = p.read_text()

action_old = '''        addCompactPopupAction(panel, popup, "▷", "Continue reading", false, () -> openBook(file));\n        addCompactPopupAction(panel, popup, "▥", "Add to shelf", false, () -> showBookShelves(file));\n        addCompactPopupAction(panel, popup, "✎", "Notes & highlights", false, () -> openBookAnnotations(file));'''
action_new = '''        addCompactPopupAction(panel, popup, "▷", "Continue reading", false, () -> openBook(file));\n        addCompactPopupAction(panel, popup, "▥", "Add to shelf", false, () -> showBookShelves(file));\n        addCompactPopupAction(panel, popup, "✐", "Edit title & author", false, () -> showEditBookMetadata(file));\n        addCompactPopupAction(panel, popup, "✎", "Notes & highlights", false, () -> openBookAnnotations(file));'''
s = one(s, action_old, action_new, "book action metadata editor")

share_anchor = "    private void shareBookReference(File file) {"
editor_methods = r'''    private String customMetadataFlag(File file) {
        return "library_metadata_custom_" + (file == null ? "" : file.getName());
    }

    private void showEditBookMetadata(File file) {
        if (file == null) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(4), dp(22), dp(2));

        TextView titleLabel = new TextView(this);
        titleLabel.setText("Book title");
        titleLabel.setTextSize(12.5f);
        titleLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleLabel.setTextColor(themeSecondaryText());
        box.addView(titleLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        EditText titleInput = new EditText(this);
        titleInput.setSingleLine(true);
        titleInput.setText(cachedLibraryTitle(file));
        titleInput.setSelection(titleInput.length());
        titleInput.setTextSize(15f);
        titleInput.setTextColor(themePrimaryText());
        titleInput.setHintTextColor(themeSecondaryText());
        titleInput.setHint("Book title");
        applyBookTitleTypeface(titleInput);
        box.addView(titleInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        TextView authorLabel = new TextView(this);
        authorLabel.setText("Author name");
        authorLabel.setTextSize(12.5f);
        authorLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        authorLabel.setTextColor(themeSecondaryText());
        LinearLayout.LayoutParams authorLabelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
        authorLabelLp.topMargin = dp(8);
        box.addView(authorLabel, authorLabelLp);

        EditText authorInput = new EditText(this);
        authorInput.setSingleLine(true);
        authorInput.setText(cachedLibraryAuthor(file));
        authorInput.setSelection(authorInput.length());
        authorInput.setTextSize(15f);
        authorInput.setTextColor(themePrimaryText());
        authorInput.setHintTextColor(themeSecondaryText());
        authorInput.setHint("Author name (optional)");
        if (pyidaungsuTypeface != null) authorInput.setTypeface(pyidaungsuTypeface);
        box.addView(authorInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit book details")
                .setMessage("Custom title and author are saved in WoW Reader and included in backup/restore.")
                .setView(box)
                .setNeutralButton("Use book metadata", null)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String title = titleInput.getText() == null ? "" : titleInput.getText().toString().trim();
                String author = authorInput.getText() == null ? "" : authorInput.getText().toString().trim();
                if (title.isEmpty()) {
                    titleInput.setError("Book title is required");
                    titleInput.requestFocus();
                    return;
                }
                prefs.edit()
                        .putString("library_title_" + file.getName(), title)
                        .putString("library_author_" + file.getName(), author)
                        .putBoolean(customMetadataFlag(file), true)
                        .putLong("sync_updated_ms", System.currentTimeMillis())
                        .apply();
                dialog.dismiss();
                if (homeMode) buildUi(); else refreshLibrary();
                maybeAutoGoogleSync();
                Toast.makeText(this, "Book details saved", Toast.LENGTH_SHORT).show();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dialog.dismiss();
                resetBookMetadataFromSource(file);
            });
        });
        dialog.show();
    }

    private void resetBookMetadataFromSource(File file) {
        if (file == null) return;
        new Thread(() -> {
            String title = stripExtension(file.getName());
            String author = "";
            try {
                if (file.getName().toLowerCase(Locale.ROOT).endsWith(".epub")) {
                    EpubUtil.Summary summary = EpubUtil.extractSummary(file, coverCacheDir);
                    if (summary.title != null && !summary.title.trim().isEmpty()) title = summary.title.trim();
                    if (summary.author != null && !summary.author.trim().isEmpty()) author = summary.author.trim();
                }
            } catch (Exception ignored) {}
            final String resolvedTitle = title;
            final String resolvedAuthor = author;
            prefs.edit()
                    .putString("library_title_" + file.getName(), resolvedTitle)
                    .putString("library_author_" + file.getName(), resolvedAuthor)
                    .remove(customMetadataFlag(file))
                    .putLong("sync_updated_ms", System.currentTimeMillis())
                    .apply();
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (homeMode) buildUi(); else refreshLibrary();
                maybeAutoGoogleSync();
                Toast.makeText(this, "Book metadata restored", Toast.LENGTH_SHORT).show();
            });
        }, "wow-book-metadata-reset").start();
    }

'''
if "private void showEditBookMetadata(File file)" not in s:
    s = one(s, share_anchor, editor_methods + share_anchor, "metadata editor methods")

# Replace the visual loader so custom title/author are never overwritten by EPUB metadata refresh.
start = s.find("    private void loadBookVisual(File file,ImageView cover,TextView titleView,TextView metaView){")
end = s.find("    private Bitmap renderPdfCover", start)
if start < 0 or end < 0:
    raise SystemExit("missing anchor: loadBookVisual")
new_loader = r'''    private void loadBookVisual(File file, ImageView cover, TextView titleView, TextView metaView) {
        new Thread(() -> {
            boolean customMetadata = prefs.getBoolean(customMetadataFlag(file), false);
            String title = cachedLibraryTitle(file);
            String author = cachedLibraryAuthor(file);
            Bitmap bitmap = null;
            try {
                if (file.getName().toLowerCase(Locale.ROOT).endsWith(".epub")) {
                    EpubUtil.Summary summary = EpubUtil.extractSummary(file, coverCacheDir);
                    if (!customMetadata) {
                        if (summary.title != null && !summary.title.trim().isEmpty()) title = summary.title.trim();
                        if (summary.author != null && !summary.author.trim().isEmpty()) author = summary.author.trim();
                    }
                    if (summary.cover != null && summary.cover.isFile())
                        bitmap = BitmapFactory.decodeFile(summary.cover.getAbsolutePath());
                } else {
                    bitmap = renderPdfCover(file);
                }
            } catch (Exception ignored) {}

            if (!customMetadata) {
                prefs.edit()
                        .putString("library_title_" + file.getName(), title)
                        .putString("library_author_" + file.getName(), author)
                        .apply();
            }

            final String finalTitle = title;
            final String finalAuthor = author;
            final Bitmap finalBitmap = bitmap;
            final int progress = ReadingProgressStore.get(prefs, file.getName());
            runOnUiThread(() -> {
                if (finalBitmap != null) cover.setImageBitmap(finalBitmap);
                titleView.setText(finalTitle);
                applyBookTitleTypeface(titleView);
                String type = file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf") ? "PDF" : "EPUB";
                metaView.setText(finalAuthor.isEmpty() ? type + " · " + progress + "%" : finalAuthor + " · " + progress + "%");
                if (!finalAuthor.isEmpty()) {
                    if (pyidaungsuTypeface != null) metaView.setTypeface(pyidaungsuTypeface);
                    metaView.setClickable(true);
                    metaView.setOnClickListener(v -> {
                        authorFilter = finalAuthor;
                        refreshLibrary();
                    });
                } else {
                    metaView.setClickable(false);
                    metaView.setOnClickListener(null);
                }
            });
        }, "wow-book-visual").start();
    }

'''
s = s[:start] + new_loader + s[end:]

s = one(
    s,
    '                prefs.edit().remove("percent_" + file.getName()).remove("library_title_" + file.getName())\n                        .remove("library_author_" + file.getName()).remove("library_owned_" + file.getName())',
    '                prefs.edit().remove("percent_" + file.getName()).remove("library_title_" + file.getName())\n                        .remove("library_author_" + file.getName()).remove(customMetadataFlag(file)).remove("library_owned_" + file.getName())',
    "delete custom metadata flag",
)

p.write_text(s)

print("v51 patch applied")
