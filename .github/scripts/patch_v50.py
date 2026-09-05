from pathlib import Path


def one(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing anchor: {label}")
    return text.replace(old, new, 1)


# Version
p = Path("app/build.gradle")
s = p.read_text()
s = one(s, "versionCode 49", "versionCode 50", "versionCode")
s = one(s, "versionName '2.17.9'", "versionName '2.18.0'", "versionName")
p.write_text(s)

# Home search enter/IME crash guard. First search row is Home; Library search is kept intact.
p = Path("app/src/main/java/com/whisper/wowreader/MainActivity.java")
s = p.read_text()
search_anchor = "        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, dp(50), 1f));"
search_guard = '''        // Home search submit is consumed; Enter/Search must never finish the Activity.
        searchInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean submit = actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER &&
                            event.getAction() == android.view.KeyEvent.ACTION_UP);
            if (!submit) return false;
            CharSequence raw = v.getText();
            searchQuery = raw == null ? "" : raw.toString().trim().toLowerCase(Locale.ROOT);
            refreshLibrary();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            v.clearFocus();
            return true;
        });
'''
if "Home search submit is consumed" not in s:
    s = one(s, search_anchor, search_guard + search_anchor, "home search row")
p.write_text(s)

# Reader features.
p = Path("app/src/main/java/com/whisper/wowreader/BookReaderActivity.java")
s = p.read_text()

fields_old = "    private long readingSessionStartedElapsedMs = 0L;\n"
fields_new = '''    private long readingSessionStartedElapsedMs = 0L;
    private boolean autoScrollEnabled = false;
    private int autoScrollSpeed = 4;
    private long autoScrollResumeToken = 0L;
    private boolean autoScrollAdvancePending = false;
    private boolean eyeBreakReminderEnabled = true;
    private Runnable eyeBreakReminderRunnable;
    private FrameLayout eyeBreakOverlay;
    private static final long EYE_BREAK_INTERVAL_MS = 30L * 60L * 1000L;
'''
s = one(s, fields_old, fields_new, "reader feature fields")

pref_old = '''        volumeChapterKeys = prefs.getBoolean("reader_volume_chapter", false);

        readingMode = prefs.getString("epub_reading_mode", "page");'''
pref_new = '''        volumeChapterKeys = prefs.getBoolean("reader_volume_chapter", false);
        autoScrollEnabled = prefs.getBoolean("reader_auto_scroll_enabled", false);
        autoScrollSpeed = Math.max(1, Math.min(10, prefs.getInt("reader_auto_scroll_speed", 4)));
        eyeBreakReminderEnabled = prefs.getBoolean("reader_eye_break_reminder", true);

        readingMode = prefs.getString("epub_reading_mode", "page");'''
s = one(s, pref_old, pref_new, "reader preferences load")

resume_old = '''        applyWindowPreferences();
        updateNightLightOverlay();
        GoogleAutoSync.schedule(this);'''
resume_new = '''        applyWindowPreferences();
        updateNightLightOverlay();
        scheduleEyeBreakReminder();
        if (!isPdf && root != null) root.postDelayed(this::updateAutoScrollState, 260L);
        GoogleAutoSync.schedule(this);'''
s = one(s, resume_old, resume_new, "onResume helpers")

pause_old = '''        ReadingStatsStore.finishSession(prefs, bookFile == null ? null : bookFile.getName(), readingSessionStartedElapsedMs);
        readingSessionStartedElapsedMs = 0L;
        if (!isPdf) saveEpubState();'''
pause_new = '''        ReadingStatsStore.finishSession(prefs, bookFile == null ? null : bookFile.getName(), readingSessionStartedElapsedMs);
        readingSessionStartedElapsedMs = 0L;
        cancelEyeBreakReminder();
        stopAutoScrollEngine();
        if (!isPdf) saveEpubState();'''
s = one(s, pause_old, pause_new, "onPause helpers")

helpers = r'''    private int autoScrollPixelsPerSecond() {
        return 8 + Math.max(1, Math.min(10, autoScrollSpeed)) * 8;
    }

    private void stopAutoScrollEngine() {
        if (isPdf || webView == null) return;
        try {
            webView.evaluateJavascript("(function(){try{var a=window.__wowAutoScroll;if(a){a.running=false;if(a.raf)cancelAnimationFrame(a.raf);a.raf=0;}return true;}catch(e){return false;}})()", null);
        } catch (Exception ignored) {}
    }

    private void updateAutoScrollState() {
        if (isPdf || webView == null) return;
        boolean canRun = autoScrollEnabled && "scroll".equals(readingMode) && !chapterLoading &&
                !footnoteNavigationActive && !footnoteReturnPending && !footnoteExactBacklinkPending &&
                footnotePreviewOverlay == null && eyeBreakOverlay == null && !searchNavigationActive;
        if (!canRun) {
            stopAutoScrollEngine();
            return;
        }
        autoScrollAdvancePending = false;
        int speed = autoScrollPixelsPerSecond();
        String js = "(function(){try{" +
                "var a=window.__wowAutoScroll||{};window.__wowAutoScroll=a;" +
                "if(a.raf)cancelAnimationFrame(a.raf);a.running=true;a.speed=" + speed + ";a.last=0;a.sent=false;" +
                "a.tick=function(t){if(!a.running)return;if(!a.last)a.last=t;var dt=Math.min(80,Math.max(0,t-a.last));a.last=t;" +
                "var max=Math.max(0,document.documentElement.scrollHeight-window.innerHeight);" +
                "if(max<=1||window.scrollY>=max-1){a.running=false;if(!a.sent){a.sent=true;setTimeout(function(){try{WoW.onAutoScrollEnd();}catch(e){}},350);}return;}" +
                "window.scrollBy(0,(a.speed*dt)/1000);a.raf=requestAnimationFrame(a.tick);};" +
                "a.raf=requestAnimationFrame(a.tick);return true;}catch(e){return false;}})()";
        try { webView.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

    private void pauseAutoScrollForUserInteraction() {
        if (!autoScrollEnabled || !"scroll".equals(readingMode) || root == null) return;
        stopAutoScrollEngine();
        final long token = ++autoScrollResumeToken;
        root.postDelayed(() -> {
            if (token == autoScrollResumeToken && !isFinishing()) updateAutoScrollState();
        }, 2800L);
    }

    private String autoScrollSpeedDisplay() {
        return Math.max(1, Math.min(10, autoScrollSpeed)) + " / 10";
    }

    private void showAutoScrollSpeedDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), dp(4));
        TextView value = new TextView(this);
        value.setText("Speed · " + autoScrollSpeedDisplay());
        value.setTextSize(15f);
        value.setTextColor(readerPanelText());
        value.setGravity(Gravity.CENTER);
        box.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        SeekBar seek = new SeekBar(this);
        seek.setMax(9);
        seek.setProgress(Math.max(0, Math.min(9, autoScrollSpeed - 1)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                autoScrollSpeed = progress + 1;
                value.setText("Speed · " + autoScrollSpeedDisplay());
                saveReaderPreferences();
                updateAutoScrollState();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { stopAutoScrollEngine(); }
            @Override public void onStopTrackingTouch(SeekBar bar) { updateAutoScrollState(); }
        });
        box.addView(seek, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        new AlertDialog.Builder(this)
                .setTitle("Auto scroll speed")
                .setMessage("1 is slowest · 10 is fastest")
                .setView(box)
                .setPositiveButton("Done", (d, w) -> updateAutoScrollState())
                .show();
    }

    private void cancelEyeBreakReminder() {
        if (root != null && eyeBreakReminderRunnable != null) root.removeCallbacks(eyeBreakReminderRunnable);
        eyeBreakReminderRunnable = null;
    }

    private void scheduleEyeBreakReminder() {
        cancelEyeBreakReminder();
        if (!eyeBreakReminderEnabled || root == null || isFinishing()) return;
        eyeBreakReminderRunnable = () -> {
            eyeBreakReminderRunnable = null;
            if (isFinishing() || !eyeBreakReminderEnabled) return;
            if (footnotePreviewOverlay != null || (bookSearchDialog != null && bookSearchDialog.isShowing())) {
                eyeBreakReminderRunnable = this::showEyeBreakReminder;
                root.postDelayed(eyeBreakReminderRunnable, 60_000L);
                return;
            }
            showEyeBreakReminder();
        };
        root.postDelayed(eyeBreakReminderRunnable, EYE_BREAK_INTERVAL_MS);
    }

    private void dismissEyeBreakReminder(boolean reschedule) {
        if (eyeBreakOverlay != null) {
            android.view.ViewParent parent = eyeBreakOverlay.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(eyeBreakOverlay);
            eyeBreakOverlay = null;
        }
        if (reschedule) scheduleEyeBreakReminder();
        if (!isFinishing()) updateAutoScrollState();
    }

    private void showEyeBreakReminder() {
        if (!eyeBreakReminderEnabled || root == null || isFinishing() || eyeBreakOverlay != null) return;
        stopAutoScrollEngine();
        FrameLayout overlay = new FrameLayout(this);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setBackgroundColor(Color.argb(112, 0, 0, 0));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(20), dp(24), dp(20));
        card.setBackground(glassPanel(readerPanelBase(), dp(26), readerPanelStroke()));
        card.setElevation(dp(14));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = new TextView(this);
        icon.setText("◉");
        icon.setTextSize(24f);
        icon.setTextColor(readerAccent());
        icon.setGravity(Gravity.CENTER);
        head.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = new TextView(this);
        title.setText("Eye break");
        title.setTextSize(21f);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setTextColor(readerPanelText());
        title.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(24f);
        close.setTextColor(readerPanelSubText());
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dismissEyeBreakReminder(true));
        head.addView(close, new LinearLayout.LayoutParams(dp(42), dp(44)));
        card.addView(head);

        TextView body = new TextView(this);
        body.setText("You’ve been reading for 30 minutes.\nLook away at something far away for a moment, blink slowly, and let your eyes rest.");
        body.setTextSize(15f);
        body.setTextColor(readerPanelText());
        body.setLineSpacing(dp(3), 1.08f);
        body.setPadding(dp(4), dp(12), dp(4), dp(14));
        card.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView button = new TextView(this);
        button.setText("Rest my eyes");
        button.setTextSize(15f);
        button.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        button.setTextColor(readerAccent());
        button.setGravity(Gravity.CENTER);
        button.setBackground(glassPanel(readerSelectedSurface(), dp(22), readerPanelStroke()));
        button.setOnClickListener(v -> dismissEyeBreakReminder(true));
        card.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        lp.leftMargin = dp(20);
        lp.rightMargin = dp(20);
        overlay.addView(card, lp);
        eyeBreakOverlay = overlay;
        root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.bringToFront();
    }

'''
destroy_anchor = "    @Override\n    protected void onDestroy() {"
s = one(s, destroy_anchor, helpers + destroy_anchor, "helper insertion")

s = one(
    s,
    "        dismissFootnotePreview();\n        cancelChromeAutoHide();",
    "        dismissFootnotePreview();\n        dismissEyeBreakReminder(false);\n        cancelEyeBreakReminder();\n        stopAutoScrollEngine();\n        cancelChromeAutoHide();",
    "onDestroy cleanup",
)

touch_old = '''            if (action == MotionEvent.ACTION_DOWN) {
                readerTouchStartedOnLink = anchorHit;
                if (!anchorHit) readerTapDetector.onTouchEvent(event);
                return false;
            }'''
touch_new = '''            if (action == MotionEvent.ACTION_DOWN) {
                readerTouchStartedOnLink = anchorHit;
                if ("scroll".equals(readingMode) && autoScrollEnabled) pauseAutoScrollForUserInteraction();
                if (!anchorHit) readerTapDetector.onTouchEvent(event);
                return false;
            }'''
s = one(s, touch_old, touch_new, "touch auto-scroll pause")

s = one(
    s,
    "        scheduleAdjacentChapterPreload(preferredPreloadDirection);\n    }",
    "        scheduleAdjacentChapterPreload(preferredPreloadDirection);\n        autoScrollAdvancePending = false;\n        updateAutoScrollState();\n    }",
    "stable chapter auto-scroll restart",
)

bridge_anchor = "        @JavascriptInterface\n        public void onEmptyChapter() {"
bridge_new = '''        @JavascriptInterface
        public void onAutoScrollEnd() {
            if (owner != webView) return;
            runOnUiThread(() -> {
                if (!autoScrollEnabled || !"scroll".equals(readingMode) || chapterLoading || autoScrollAdvancePending ||
                        footnoteNavigationActive || footnotePreviewOverlay != null || eyeBreakOverlay != null) return;
                if (currentSpine >= 0 && currentSpine < spine.size() - 1) {
                    autoScrollAdvancePending = true;
                    navigateChapter(1, false);
                } else {
                    autoScrollAdvancePending = false;
                    stopAutoScrollEngine();
                }
            });
        }

'''
s = one(s, bridge_anchor, bridge_new + bridge_anchor, "auto-scroll bridge")

mode_anchor = '''        card.addView(modeRow);

        addSheetLabel(card, "Page animation", sub);'''
mode_new = '''        card.addView(modeRow);

        addSheetLabel(card, "Auto scroll", sub);
        LinearLayout autoScrollRow = sheetRow();
        TextView autoToggle = sheetChip(autoScrollEnabled ? "On" : "Off", autoScrollEnabled);
        TextView autoSpeed = sheetChip("Speed · " + autoScrollSpeedDisplay(), false);
        autoToggle.setOnClickListener(v -> {
            autoScrollEnabled = !autoScrollEnabled;
            autoToggle.setText(autoScrollEnabled ? "On" : "Off");
            styleSheetChip(autoToggle, autoScrollEnabled);
            saveReaderPreferences();
            if (autoScrollEnabled && !"scroll".equals(readingMode))
                Toast.makeText(this, "Auto scroll runs in Scroll mode", Toast.LENGTH_SHORT).show();
            autoScrollResumeToken++;
            updateAutoScrollState();
        });
        autoSpeed.setOnClickListener(v -> { dialog.dismiss(); showAutoScrollSpeedDialog(); });
        autoScrollRow.addView(autoToggle, sheetChipLp(false));
        autoScrollRow.addView(autoSpeed, sheetChipLp(true));
        card.addView(autoScrollRow);

        addSheetLabel(card, "Eye care", sub);
        LinearLayout eyeRow = sheetRow();
        TextView eyeToggle = sheetChip(eyeBreakReminderEnabled ? "30 min reminder · On" : "30 min reminder · Off", eyeBreakReminderEnabled);
        eyeToggle.setOnClickListener(v -> {
            eyeBreakReminderEnabled = !eyeBreakReminderEnabled;
            eyeToggle.setText(eyeBreakReminderEnabled ? "30 min reminder · On" : "30 min reminder · Off");
            styleSheetChip(eyeToggle, eyeBreakReminderEnabled);
            saveReaderPreferences();
            if (eyeBreakReminderEnabled) scheduleEyeBreakReminder(); else cancelEyeBreakReminder();
        });
        eyeRow.addView(eyeToggle, sheetChipLp(false));
        card.addView(eyeRow);

        addSheetLabel(card, "Page animation", sub);'''
s = one(s, mode_anchor, mode_new, "settings auto-scroll/eye-care controls")

chip_old = 'modeChips[i].setOnClickListener(v -> { readingMode = idx == 0 ? "page" : "scroll"; pageTurnLocked = false; saveReaderPreferences(); applyReaderStyleSmooth(true); selectSheetChip(modeChips, idx); });'
chip_new = 'modeChips[i].setOnClickListener(v -> { readingMode = idx == 0 ? "page" : "scroll"; pageTurnLocked = false; saveReaderPreferences(); applyReaderStyleSmooth(true); selectSheetChip(modeChips, idx); autoScrollResumeToken++; updateAutoScrollState(); });'
s = one(s, chip_old, chip_new, "mode chip auto-scroll")

# Legacy list, if present.
options_old = '''                "Volume keys navigate · " + onOff(volumeChapterKeys),
                "Reset reader settings"'''
if options_old in s:
    s = s.replace(
        options_old,
        '''                "Volume keys navigate · " + onOff(volumeChapterKeys),
                "Auto scroll · " + onOff(autoScrollEnabled),
                "Auto scroll speed · " + autoScrollSpeedDisplay(),
                "30-minute eye reminder · " + onOff(eyeBreakReminderEnabled),
                "Reset reader settings"''',
        1,
    )
    switch_old = '''                        case 11:
                            volumeChapterKeys = !volumeChapterKeys;
                            saveReaderPreferences();
                            showReaderSettings();
                            break;
                        case 12: resetReaderPreferences(); break;'''
    switch_new = '''                        case 11:
                            volumeChapterKeys = !volumeChapterKeys;
                            saveReaderPreferences();
                            showReaderSettings();
                            break;
                        case 12:
                            autoScrollEnabled = !autoScrollEnabled;
                            saveReaderPreferences();
                            autoScrollResumeToken++;
                            updateAutoScrollState();
                            showReaderSettings();
                            break;
                        case 13: showAutoScrollSpeedDialog(); break;
                        case 14:
                            eyeBreakReminderEnabled = !eyeBreakReminderEnabled;
                            saveReaderPreferences();
                            if (eyeBreakReminderEnabled) scheduleEyeBreakReminder(); else cancelEyeBreakReminder();
                            showReaderSettings();
                            break;
                        case 15: resetReaderPreferences(); break;'''
    s = one(s, switch_old, switch_new, "legacy settings switch")

# Reading-mode dialog: first matching save/apply pair within this section.
mode_dialog_old = '''                        saveReaderPreferences();
                        applyReaderStyle(true);
                    }
                    dialog.dismiss();'''
mode_dialog_new = '''                        saveReaderPreferences();
                        applyReaderStyle(true);
                        autoScrollResumeToken++;
                        updateAutoScrollState();
                    }
                    dialog.dismiss();'''
s = one(s, mode_dialog_old, mode_dialog_new, "reading mode dialog")

reset_old = '''        volumeChapterKeys = false;
        readingMode = "page";
        pageTurnLocked = false;'''
reset_new = '''        volumeChapterKeys = false;
        autoScrollEnabled = false;
        autoScrollSpeed = 4;
        eyeBreakReminderEnabled = true;
        autoScrollResumeToken++;
        stopAutoScrollEngine();
        scheduleEyeBreakReminder();
        readingMode = "page";
        pageTurnLocked = false;'''
s = one(s, reset_old, reset_new, "reset new prefs")

save_old = '''                .putBoolean("reader_volume_chapter", volumeChapterKeys)
                .putString("epub_reading_mode", readingMode)'''
save_new = '''                .putBoolean("reader_volume_chapter", volumeChapterKeys)
                .putBoolean("reader_auto_scroll_enabled", autoScrollEnabled)
                .putInt("reader_auto_scroll_speed", Math.max(1, Math.min(10, autoScrollSpeed)))
                .putBoolean("reader_eye_break_reminder", eyeBreakReminderEnabled)
                .putString("epub_reading_mode", readingMode)'''
s = one(s, save_old, save_new, "save new prefs")

p.write_text(s)

print("v50 patch applied")
