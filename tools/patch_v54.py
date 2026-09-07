from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/whisper/wowreader/MainActivity.java"
READER = ROOT / "app/src/main/java/com/whisper/wowreader/BookReaderActivity.java"
GRADLE = ROOT / "app/build.gradle"
BUILD_WF = ROOT / ".github/workflows/build-apk.yml"


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 match, got {count}")
    return text.replace(old, new, 1)


def replace_between(text, start_marker, end_marker, replacement, label):
    start = text.find(start_marker)
    if start < 0:
        raise RuntimeError(f"{label}: start marker missing")
    end = text.find(end_marker, start)
    if end < 0:
        raise RuntimeError(f"{label}: end marker missing")
    return text[:start] + replacement + text[end:]


main = MAIN.read_text(encoding="utf-8")

metadata_method = r'''    private void showEditBookMetadata(File file) {
        if (file == null) return;
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = premiumSheet("Edit book details",
                "Custom title and author are saved in WoW Reader and included in backup/restore.", dialog);

        TextView titleLabel = new TextView(this);
        titleLabel.setText("Book title");
        titleLabel.setTextSize(12.5f);
        titleLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleLabel.setTextColor(themeSecondaryText());
        titleLabel.setPadding(dp(2), dp(6), dp(2), dp(5));
        sheet.addView(titleLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));

        EditText titleInput = new EditText(this);
        titleInput.setSingleLine(true);
        titleInput.setText(cachedLibraryTitle(file));
        titleInput.setSelection(titleInput.length());
        titleInput.setTextSize(15f);
        titleInput.setTextColor(themePrimaryText());
        titleInput.setHintTextColor(themeSecondaryText());
        titleInput.setHint("Book title");
        titleInput.setPadding(dp(14), 0, dp(14), 0);
        titleInput.setBackground(roundRect(themeControlSurface(), dp(16), dp(1), themeStroke()));
        applyBookTitleTypeface(titleInput);
        sheet.addView(titleInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        TextView authorLabel = new TextView(this);
        authorLabel.setText("Author name");
        authorLabel.setTextSize(12.5f);
        authorLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        authorLabel.setTextColor(themeSecondaryText());
        authorLabel.setPadding(dp(2), dp(8), dp(2), dp(5));
        LinearLayout.LayoutParams authorLabelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        authorLabelLp.topMargin = dp(4);
        sheet.addView(authorLabel, authorLabelLp);

        EditText authorInput = new EditText(this);
        authorInput.setSingleLine(true);
        authorInput.setText(cachedLibraryAuthor(file));
        authorInput.setSelection(authorInput.length());
        authorInput.setTextSize(15f);
        authorInput.setTextColor(themePrimaryText());
        authorInput.setHintTextColor(themeSecondaryText());
        authorInput.setHint("Author name (optional)");
        authorInput.setPadding(dp(14), 0, dp(14), 0);
        authorInput.setBackground(roundRect(themeControlSurface(), dp(16), dp(1), themeStroke()));
        if (pyidaungsuTypeface != null) authorInput.setTypeface(pyidaungsuTypeface);
        sheet.addView(authorInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        TextView source = filterChoice("Use book metadata", false);
        source.setGravity(Gravity.CENTER);
        source.setOnClickListener(v -> {
            dialog.dismiss();
            resetBookMetadataFromSource(file);
        });
        LinearLayout.LayoutParams sourceLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        sourceLp.topMargin = dp(11);
        sheet.addView(source, sourceLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = filterChoice("Cancel", false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        TextView save = filterChoice("Save", true);
        save.setTextColor(Color.WHITE);
        save.setBackground(roundRect(themeAccent(), dp(17), 0, 0));
        save.setOnClickListener(v -> {
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
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(dp(96), dp(40));
        cancelLp.rightMargin = dp(8);
        actions.addView(cancel, cancelLp);
        actions.addView(save, new LinearLayout.LayoutParams(dp(96), dp(40)));
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        actionLp.topMargin = dp(7);
        sheet.addView(actions, actionLp);

        presentBottomSheet(dialog, sheet, 0.74f);
        titleInput.requestFocus();
    }

'''
main = replace_between(
    main,
    "    private void showEditBookMetadata(File file) {",
    "    private void resetBookMetadataFromSource(File file) {",
    metadata_method,
    "themed metadata editor",
)

old_import = '''                String lower=name.toLowerCase(Locale.ROOT),mime=getContentResolver().getType(uri);\n                if(!lower.endsWith(".epub")&&!lower.endsWith(".pdf")){\n                    if("application/pdf".equals(mime))name+=".pdf";\n                    else if("application/epub+zip".equals(mime))name+=".epub";\n                    else throw new Exception("Only EPUB and PDF files are supported");\n                }\n'''
new_import = '''                String lower=name.toLowerCase(Locale.ROOT),mime=getContentResolver().getType(uri);\n                String detectedExtension=BookImportTypeDetector.resolveExtension(this,uri,name,mime);\n                if(!lower.endsWith(".epub")&&!lower.endsWith(".pdf")){\n                    if(detectedExtension!=null)name+=detectedExtension;\n                    else throw new Exception("Only EPUB and PDF files are supported");\n                }\n'''
main = replace_once(main, old_import, new_import, "provider-independent import detection")

old_delete = '''                LibraryShelfStore.removeBookFromAll(prefs, file.getName());\n                prefs.edit().remove("percent_" + file.getName()).remove("library_title_" + file.getName())\n'''
new_delete = '''                LibraryShelfStore.removeBookFromAll(prefs, file.getName());\n                ReadingProgressStore.remove(prefs, file.getName());\n                prefs.edit().remove("library_title_" + file.getName())\n'''
main = replace_once(main, old_delete, new_delete, "delete completion cleanup")
MAIN.write_text(main, encoding="utf-8")

reader = READER.read_text(encoding="utf-8")

reader = replace_once(
    reader,
    "    private ImageView pdfImage;\n    private int currentPdfPage = 0;",
    "    private ImageView pdfImage;\n    private PdfContinuousView pdfContinuousView;\n    private int currentPdfPage = 0;",
    "pdf continuous field",
)

reader = replace_once(
    reader,
    '        readingMode = prefs.getString("epub_reading_mode", "page");',
    '        readingMode = prefs.getString(isPdf ? "pdf_reading_mode" : "epub_reading_mode", "page");',
    "per-format reading mode",
)

pdf_setup = r'''    private void setupPdfView(FrameLayout content) {
        pdfImage = new ImageView(this);
        pdfImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pdfImage.setBackgroundColor(Color.rgb(48, 49, 52));
        content.addView(pdfImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        pdfContinuousView = new PdfContinuousView(this);
        pdfContinuousView.setVisibility(View.GONE);
        pdfContinuousView.setListener(new PdfContinuousView.Listener() {
            @Override public void onPageChanged(int pageZeroBased, int count) {
                onPdfContinuousPageChanged(pageZeroBased, count);
            }
            @Override public void onTap() { toggleControls(); }
            @Override public void onUserInteraction() { pauseAutoScrollForUserInteraction(); }
        });
        content.addView(pdfContinuousView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        pdfScaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        pdfScale *= detector.getScaleFactor();
                        pdfScale = Math.max(1f, Math.min(pdfScale, 4f));
                        pdfImage.setScaleX(pdfScale);
                        pdfImage.setScaleY(pdfScale);
                        return true;
                    }
                });

        pdfGestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDown(MotionEvent e) { return true; }

                    @Override public boolean onDoubleTap(MotionEvent e) {
                        if (pdfScale > 1.05f) {
                            resetPdfZoom();
                        } else {
                            pdfScale = 2f;
                            pdfImage.setPivotX(e.getX());
                            pdfImage.setPivotY(e.getY());
                            pdfImage.setScaleX(pdfScale);
                            pdfImage.setScaleY(pdfScale);
                        }
                        return true;
                    }

                    @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                        float r = e.getX() / Math.max(1f, pdfImage.getWidth());
                        if (pdfScale <= 1.05f && r < 0.24f) previous();
                        else if (pdfScale <= 1.05f && r > 0.76f) next();
                        else toggleControls();
                        return true;
                    }
                });

        pdfImage.setOnTouchListener((v, event) -> {
            pdfScaleDetector.onTouchEvent(event);
            pdfGestureDetector.onTouchEvent(event);

            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastTouchX = event.getX();
                lastTouchY = event.getY();
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE &&
                    pdfScale > 1.01f && !pdfScaleDetector.isInProgress()) {
                float dx = event.getX() - lastTouchX;
                float dy = event.getY() - lastTouchY;
                pdfImage.setTranslationX(pdfImage.getTranslationX() + dx);
                pdfImage.setTranslationY(pdfImage.getTranslationY() + dy);
                lastTouchX = event.getX();
                lastTouchY = event.getY();
            }
            return true;
        });
    }

    private void applyPdfReadingMode() {
        if (!isPdf || pdfRenderer == null) return;
        boolean scroll = "scroll".equals(readingMode);
        if (pdfImage != null) pdfImage.setVisibility(scroll ? View.GONE : View.VISIBLE);
        if (pdfContinuousView != null) pdfContinuousView.setVisibility(scroll ? View.VISIBLE : View.GONE);
        if (scroll) {
            try { if (pdfPage != null) pdfPage.close(); } catch (Exception ignored) {}
            pdfPage = null;
            if (pdfContinuousView != null) pdfContinuousView.scrollToPage(currentPdfPage);
            updatePdfProgressState(currentPdfPage, pdfRenderer.getPageCount());
            updateAutoScrollState();
        } else {
            if (pdfContinuousView != null) pdfContinuousView.stopAutoScroll();
            renderPdfPage();
        }
    }

    private void onPdfContinuousPageChanged(int pageZeroBased, int count) {
        if (!isPdf || !"scroll".equals(readingMode)) return;
        currentPdfPage = Math.max(0, Math.min(Math.max(0, count - 1), pageZeroBased));
        updatePdfProgressState(currentPdfPage, count);
    }

    private void updatePdfProgressState(int pageZeroBased, int count) {
        if (bookFile == null || count <= 0) return;
        currentPdfPage = Math.max(0, Math.min(count - 1, pageZeroBased));
        int percent = Math.max(0, Math.min(100, (int) Math.round(
                ((currentPdfPage + 1.0) / count) * 100.0)));
        if (positionView != null) positionView.setText(
                "Page " + (currentPdfPage + 1) + " / " + count + " · " + percent + "%");
        if (readingSeek != null && !readingSeekDragging) {
            int seek = count <= 1 ? 1000 : (int) Math.round((currentPdfPage / (double) (count - 1)) * 1000.0);
            readingSeek.setProgress(Math.max(0, Math.min(1000, seek)));
        }
        ReadingProgressStore.set(prefs, bookFile.getName(), percent);
        prefs.edit()
                .putInt("pdf_page_" + bookFile.getName(), currentPdfPage)
                .putLong("sync_updated_ms", System.currentTimeMillis())
                .apply();
        updateBookmarkIcon();
    }

    private void goToPdfPage(int pageZeroBased) {
        if (pdfRenderer == null || pdfRenderer.getPageCount() <= 0) return;
        currentPdfPage = Math.max(0, Math.min(pdfRenderer.getPageCount() - 1, pageZeroBased));
        if ("scroll".equals(readingMode) && pdfContinuousView != null) {
            pdfContinuousView.scrollToPage(currentPdfPage);
            updatePdfProgressState(currentPdfPage, pdfRenderer.getPageCount());
        } else {
            renderPdfPage();
        }
    }

'''
reader = replace_between(
    reader,
    "    private void setupPdfView(FrameLayout content) {",
    "    private void openEpub() {",
    pdf_setup + "    private void openEpub() {",
    "continuous pdf setup",
)
# replace_between retains end marker; replacement above includes it, so remove duplicated marker once.
reader = reader.replace("    private void openEpub() {    private void openEpub() {", "    private void openEpub() {", 1)

reader = replace_once(
    reader,
    '''        back.setOnClickListener(v -> {\n            if (!isPdf) saveEpubState();\n            finish();\n        });''',
    '''        back.setOnClickListener(v -> {\n            saveReaderLocationDurable();\n            finish();\n        });''',
    "durable toolbar exit",
)

old_previous = '''    private void previous() {\n        if (isPdf) {\n            if (currentPdfPage > 0) {\n                currentPdfPage--;\n                renderPdfPage();\n            }\n        } else {'''
new_previous = '''    private void previous() {\n        if (isPdf) {\n            if (currentPdfPage > 0) goToPdfPage(currentPdfPage - 1);\n        } else {'''
reader = replace_once(reader, old_previous, new_previous, "pdf previous")

old_next = '''    private void next() {\n        if (isPdf) {\n            if (pdfRenderer != null && currentPdfPage < pdfRenderer.getPageCount() - 1) {\n                currentPdfPage++;\n                renderPdfPage();\n            }\n        } else {'''
new_next = '''    private void next() {\n        if (isPdf) {\n            if (pdfRenderer != null && currentPdfPage < pdfRenderer.getPageCount() - 1)\n                goToPdfPage(currentPdfPage + 1);\n        } else {'''
reader = replace_once(reader, old_next, new_next, "pdf next")

old_seek_pdf = '''        if (isPdf) {\n            if (pdfRenderer == null || pdfRenderer.getPageCount() <= 0) return;\n            int target = Math.max(0, Math.min(pdfRenderer.getPageCount() - 1,\n                    (int) Math.round((p / 1000.0) * (pdfRenderer.getPageCount() - 1))));\n            if (target != currentPdfPage) {\n                currentPdfPage = target;\n                renderPdfPage();\n            }\n            return;\n        }'''
new_seek_pdf = '''        if (isPdf) {\n            if (pdfRenderer == null || pdfRenderer.getPageCount() <= 0) return;\n            int target = Math.max(0, Math.min(pdfRenderer.getPageCount() - 1,\n                    (int) Math.round((p / 1000.0) * (pdfRenderer.getPageCount() - 1))));\n            goToPdfPage(target);\n            return;\n        }'''
reader = replace_once(reader, old_seek_pdf, new_seek_pdf, "pdf seek")

pdf_settings = r'''    private void showPdfSettings() {
        String[] options = new String[]{
                "Reading mode · " + ("scroll".equals(readingMode) ? "Vertical scroll" : "Pages"),
                "Auto scroll · " + onOff(autoScrollEnabled),
                "Auto scroll speed · " + autoScrollSpeedDisplay(),
                "Brightness · " + brightnessDisplayName(),
                "Keep screen on · " + onOff(keepScreenOn),
                "Lock orientation · " + onOff(lockOrientation)
        };

        new AlertDialog.Builder(this)
                .setTitle("PDF reader settings")
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        readingMode = "scroll".equals(readingMode) ? "page" : "scroll";
                        saveReaderPreferences();
                        applyPdfReadingMode();
                        showPdfSettings();
                    } else if (which == 1) {
                        autoScrollEnabled = !autoScrollEnabled;
                        saveReaderPreferences();
                        if (autoScrollEnabled && !"scroll".equals(readingMode))
                            Toast.makeText(this, "Auto scroll runs in Scroll mode", Toast.LENGTH_SHORT).show();
                        autoScrollResumeToken++;
                        updateAutoScrollState();
                        showPdfSettings();
                    } else if (which == 2) {
                        showAutoScrollSpeedDialog();
                    } else if (which == 3) {
                        showBrightnessDialog();
                    } else if (which == 4) {
                        keepScreenOn = !keepScreenOn;
                        saveReaderPreferences();
                        applyWindowPreferences();
                        showPdfSettings();
                    } else if (which == 5) {
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
reader = replace_between(
    reader,
    "    private void showPdfSettings() {",
    "    private void showFontSizeDialog() {",
    pdf_settings,
    "pdf settings",
)

reader = replace_once(
    reader,
    '.putString("epub_reading_mode", readingMode)',
    '.putString(isPdf ? "pdf_reading_mode" : "epub_reading_mode", readingMode)',
    "save per-format mode",
)

open_pdf = r'''    private void openPdf() {
        try {
            pdfDescriptor = ParcelFileDescriptor.open(bookFile, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(pdfDescriptor);

            if (pdfRenderer.getPageCount() == 0)
                throw new Exception("PDF has no pages");

            currentPdfPage = Math.max(0, Math.min(
                    prefs.getInt("pdf_page_" + bookFile.getName(), 0),
                    pdfRenderer.getPageCount() - 1));

            if (pdfContinuousView != null) pdfContinuousView.open(bookFile, currentPdfPage);
            applyPdfReadingMode();

        } catch (Exception e) {
            Toast.makeText(this, "PDF error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

'''
reader = replace_between(
    reader,
    "    private void openPdf() {",
    "    private void renderPdfPage() {",
    open_pdf,
    "open continuous pdf",
)

old_pdf_progress = '''            int percent = (int) Math.round(\n                    ((currentPdfPage + 1.0) / pdfRenderer.getPageCount()) * 100.0);\n\n            positionView.setText(\n                    "Page " + (currentPdfPage + 1) +\n                    " / " + pdfRenderer.getPageCount() +\n                    " · " + percent + "%");\n            if (readingSeek != null && !readingSeekDragging && pdfRenderer.getPageCount() > 1)\n                readingSeek.setProgress((int) Math.round((currentPdfPage / (double) (pdfRenderer.getPageCount() - 1)) * 1000.0));\n\n            prefs.edit()\n                    .putInt("pdf_page_" + bookFile.getName(), currentPdfPage)\n                    .putInt("percent_" + bookFile.getName(), percent)\n                    .putLong("sync_updated_ms", System.currentTimeMillis())\n                    .apply();\n\n            updateBookmarkIcon();'''
reader = replace_once(
    reader,
    old_pdf_progress,
    '''            updatePdfProgressState(currentPdfPage, pdfRenderer.getPageCount());''',
    "pdf progress single source",
)

# Durable OEM/process-death checkpoint helpers are inserted before seekToOverallProgress.
durable = r'''    private void saveEpubStateDurable() {
        if (prefs == null || bookFile == null || spine.isEmpty()) return;
        int saveSpine = currentSpine;
        int saveProgress = currentProgressPermille;
        if (searchNavigationActive && searchReturnSpine >= 0) {
            saveSpine = searchReturnSpine;
            saveProgress = searchReturnProgressPermille;
        } else if ((footnoteNavigationActive || footnoteReturnPending || footnoteReturnArmed) && footnoteReturnSpine >= 0) {
            saveSpine = footnoteReturnSpine;
            saveProgress = footnoteReturnProgressPermille;
        }
        saveSpine = Math.max(0, Math.min(spine.size() - 1, saveSpine));
        saveProgress = Math.max(0, Math.min(1000, saveProgress));
        double overall = (saveSpine + saveProgress / 1000.0) / spine.size();
        int percent = Math.max(0, Math.min(100, (int) Math.round(overall * 100.0)));
        ReadingProgressStore.set(prefs, bookFile.getName(), percent);
        prefs.edit()
                .putInt("epub_chapter_" + bookFile.getName(), saveSpine)
                .putInt("epub_scroll_" + bookFile.getName(), saveProgress)
                .putLong("sync_updated_ms", System.currentTimeMillis())
                .commit();
    }

    private void savePdfStateDurable() {
        if (prefs == null || bookFile == null) return;
        int count = pdfRenderer == null ? 0 : pdfRenderer.getPageCount();
        if (count > 0) {
            int percent = Math.max(0, Math.min(100, (int) Math.round(
                    ((currentPdfPage + 1.0) / count) * 100.0)));
            ReadingProgressStore.set(prefs, bookFile.getName(), percent);
        }
        prefs.edit()
                .putInt("pdf_page_" + bookFile.getName(), Math.max(0, currentPdfPage))
                .putLong("sync_updated_ms", System.currentTimeMillis())
                .commit();
    }

    private void saveReaderLocationDurable() {
        if (bookFile == null || prefs == null) return;
        if (isPdf) savePdfStateDurable();
        else saveEpubStateDurable();
    }

'''
reader = replace_once(
    reader,
    "    private void seekToOverallProgress(int permille) {",
    durable + "    private void seekToOverallProgress(int permille) {",
    "durable reader checkpoints",
)

reader = replace_once(
    reader,
    '''        if (!isPdf) saveEpubState();\n        finish();\n        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);''',
    '''        saveReaderLocationDurable();\n        finish();\n        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);''',
    "durable hardware back",
)

old_lifecycle = '''    @Override\n    public void onTrimMemory(int level) {\n        super.onTrimMemory(level);\n        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)\n            cancelChapterPreload();\n    }\n\n    @Override\n    protected void onPause() {\n        ReadingStatsStore.finishSession(prefs, bookFile == null ? null : bookFile.getName(), readingSessionStartedElapsedMs);\n        readingSessionStartedElapsedMs = 0L;\n        cancelEyeBreakReminder();\n        stopAutoScrollEngine();\n        if (!isPdf) saveEpubState();\n        GoogleAutoSync.flush(this);\n        super.onPause();\n    }\n\n    private int autoScrollPixelsPerSecond() {\n        return 8 + Math.max(1, Math.min(10, autoScrollSpeed)) * 8;\n    }\n'''
new_lifecycle = '''    @Override\n    public void onTrimMemory(int level) {\n        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) saveReaderLocationDurable();\n        super.onTrimMemory(level);\n        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)\n            cancelChapterPreload();\n    }\n\n    @Override\n    protected void onUserLeaveHint() {\n        saveReaderLocationDurable();\n        super.onUserLeaveHint();\n    }\n\n    @Override\n    protected void onSaveInstanceState(Bundle outState) {\n        saveReaderLocationDurable();\n        super.onSaveInstanceState(outState);\n    }\n\n    @Override\n    protected void onStop() {\n        saveReaderLocationDurable();\n        super.onStop();\n    }\n\n    @Override\n    protected void onPause() {\n        ReadingStatsStore.finishSession(prefs, bookFile == null ? null : bookFile.getName(), readingSessionStartedElapsedMs);\n        readingSessionStartedElapsedMs = 0L;\n        cancelEyeBreakReminder();\n        stopAutoScrollEngine();\n        saveReaderLocationDurable();\n        GoogleAutoSync.flush(this);\n        super.onPause();\n    }\n\n    private int autoScrollPixelsPerSecond() {\n        final int[] speeds = {0, 2, 4, 8, 16, 24, 32, 48, 64, 80, 96};\n        return speeds[Math.max(1, Math.min(10, autoScrollSpeed))];\n    }\n'''
reader = replace_once(reader, old_lifecycle, new_lifecycle, "OEM lifecycle checkpoint and slower autoscroll")

old_engines_start = "    private void stopAutoScrollEngine() {\n        if (isPdf || webView == null) return;"
new_engines_start = "    private void stopAutoScrollEngine() {\n        if (isPdf) {\n            if (pdfContinuousView != null) pdfContinuousView.stopAutoScroll();\n            return;\n        }\n        if (webView == null) return;"
reader = replace_once(reader, old_engines_start, new_engines_start, "pdf stop auto scroll")

old_update_start = '''    private void updateAutoScrollState() {\n        if (isPdf || webView == null) return;\n        boolean canRun = autoScrollEnabled && "scroll".equals(readingMode) && !chapterLoading &&'''
new_update_start = '''    private void updateAutoScrollState() {\n        if (isPdf) {\n            if (pdfContinuousView == null) return;\n            boolean canRunPdf = autoScrollEnabled && "scroll".equals(readingMode) &&\n                    pdfContinuousView.getVisibility() == View.VISIBLE && eyeBreakOverlay == null && !isFinishing();\n            if (canRunPdf) pdfContinuousView.startAutoScroll(autoScrollPixelsPerSecond());\n            else pdfContinuousView.stopAutoScroll();\n            return;\n        }\n        if (webView == null) return;\n        boolean canRun = autoScrollEnabled && "scroll".equals(readingMode) && !chapterLoading &&'''
reader = replace_once(reader, old_update_start, new_update_start, "pdf auto scroll engine")

reader = replace_once(
    reader,
    '        if (!isPdf && root != null) root.postDelayed(this::updateAutoScrollState, 260L);',
    '        if (root != null) root.postDelayed(this::updateAutoScrollState, 260L);',
    "resume pdf autoscroll",
)

reader = replace_once(
    reader,
    '.setMessage("1 is slowest · 10 is fastest")',
    '.setMessage("1 is extra slow · 10 is fastest")',
    "slow speed label",
)

reader = replace_once(
    reader,
    '''        try { if (pdfPage != null) pdfPage.close(); } catch (Exception ignored) {}\n        try { if (pdfRenderer != null) pdfRenderer.close(); } catch (Exception ignored) {}\n        try { if (pdfDescriptor != null) pdfDescriptor.close(); } catch (Exception ignored) {}\n''',
    '''        if (pdfContinuousView != null) {\n            try { pdfContinuousView.close(); } catch (Exception ignored) {}\n            pdfContinuousView = null;\n        }\n        try { if (pdfPage != null) pdfPage.close(); } catch (Exception ignored) {}\n        try { if (pdfRenderer != null) pdfRenderer.close(); } catch (Exception ignored) {}\n        try { if (pdfDescriptor != null) pdfDescriptor.close(); } catch (Exception ignored) {}\n''',
    "close continuous pdf",
)

READER.write_text(reader, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = replace_once(gradle, "        versionCode 53", "        versionCode 54", "versionCode")
gradle = replace_once(gradle, "        versionName '2.18.3'", "        versionName '2.18.4'", "versionName")
GRADLE.write_text(gradle, encoding="utf-8")

wf = BUILD_WF.read_text(encoding="utf-8")
wf = replace_once(wf, "name: Build WoW Reader Lab v2.18.3 APK and AAB", "name: Build WoW Reader Lab v2.18.4 APK and AAB", "workflow name")
wf = replace_once(
    wf,
    "branches: [main, play-store-api36, fix/v52-unified-install, feature/v53-reading-calendar-recap]",
    "branches: [main, play-store-api36, fix/v52-unified-install, feature/v53-reading-calendar-recap, fix/v54-reader-pdf-oem]",
    "workflow branch",
)
wf = replace_once(wf, '          test -s app/src/main/java/com/whisper/wowreader/ReadingProgressStore.java\n',
                  '          test -s app/src/main/java/com/whisper/wowreader/ReadingProgressStore.java\n          test -s app/src/main/java/com/whisper/wowreader/PdfContinuousView.java\n          test -s app/src/main/java/com/whisper/wowreader/BookImportTypeDetector.java\n',
                  "v54 source checks")
wf = wf.replace('grep -q "versionCode 53" app/build.gradle', 'grep -q "versionCode 54" app/build.gradle')
wf = wf.replace('grep -q "versionName \'2.18.3\'" app/build.gradle', 'grep -q "versionName \'2.18.4\'" app/build.gradle')
wf = wf.replace('versionCode=\'53\'', 'versionCode=\'54\'')
wf = wf.replace('versionName=\'2.18.3\'', 'versionName=\'2.18.4\'')
wf = wf.replace("WoW-Reader-Lab-v2.18.3-v53-unsigned.apk", "WoW-Reader-Lab-v2.18.4-v54-unsigned.apk")
wf = wf.replace("WoW-Reader-Lab-v2.18.3-v53-unsigned.aab", "WoW-Reader-Lab-v2.18.4-v54-unsigned.aab")
wf = wf.replace("name: WoW-Reader-Lab-v2.18.3-v53", "name: WoW-Reader-Lab-v2.18.4-v54")
needle = '          grep -q "showAutoScrollSpeedDialog" app/src/main/java/com/whisper/wowreader/BookReaderActivity.java\n'
extra = needle + '''          grep -q "saveReaderLocationDurable" app/src/main/java/com/whisper/wowreader/BookReaderActivity.java\n          grep -q "pdf_reading_mode" app/src/main/java/com/whisper/wowreader/BookReaderActivity.java\n          grep -q "PdfContinuousView" app/src/main/java/com/whisper/wowreader/BookReaderActivity.java\n          grep -q "BookImportTypeDetector.resolveExtension" app/src/main/java/com/whisper/wowreader/MainActivity.java\n'''
wf = replace_once(wf, needle, extra, "v54 feature guards")
BUILD_WF.write_text(wf, encoding="utf-8")

print("v54 patch applied")
