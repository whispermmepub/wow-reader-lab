from pathlib import Path

p = Path('app/src/main/java/com/whisper/wowreader/BookReaderActivity.java')
s = p.read_text()

s = s.replace('private Dialog footnotePreviewDialog = null;', 'private FrameLayout footnotePreviewOverlay = null;', 1)
s = s.replace(
    'private volatile long lastReaderLinkTapMs = 0L;',
    'private volatile long lastReaderLinkTapMs = 0L;\n    private boolean readerTouchStartedOnLink = false;',
    1,
)

start = s.index('    private void showFootnotePreview(ReaderSearchIndex.Footnote note, String label) {')
end = s.index('    private void navigateToFootnote(ReaderSearchIndex.Footnote note) {', start)
new_method = '''    private void dismissFootnotePreview() {
        if (footnotePreviewOverlay == null) return;
        try {
            ViewGroup parent = (ViewGroup) footnotePreviewOverlay.getParent();
            if (parent != null) parent.removeView(footnotePreviewOverlay);
        } catch (Exception ignored) {}
        footnotePreviewOverlay = null;
    }

    private void showFootnotePreview(ReaderSearchIndex.Footnote note, String label) {
        if (isFinishing() || note == null || root == null) return;
        dismissFootnotePreview();

        final FrameLayout overlay = new FrameLayout(this);
        footnotePreviewOverlay = overlay;
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setBackgroundColor(Color.argb(22, 0, 0, 0));
        overlay.setOnClickListener(v -> dismissFootnotePreview());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(10), dp(18), dp(16));
        card.setBackground(glassPanel(readerPanelBase(), dp(24), readerPanelStroke()));
        card.setElevation(dp(16));
        card.setClickable(true);
        card.setOnClickListener(v -> { });

        View handle = new View(this);
        handle.setBackground(glassPanel(readerPanelStroke(), dp(3), Color.TRANSPARENT));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(38), dp(5));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(8);
        card.addView(handle, handleLp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        String cleanLabel = label == null ? "" : label.trim();
        title.setText(cleanLabel.isEmpty() ? "Footnote" : "Footnote " + cleanLabel);
        title.setTextSize(16f);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setTextColor(readerPanelText());
        head.addView(title, new LinearLayout.LayoutParams(0, dp(40), 1f));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(24f);
        close.setTextColor(readerPanelSubText());
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dismissFootnotePreview());
        head.addView(close, new LinearLayout.LayoutParams(dp(42), dp(40)));
        card.addView(head);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        TextView body = new TextView(this);
        String text = note.text == null ? "" : note.text.trim();
        text = text.replaceFirst("(?i)^\\\\s*Unknown\\\\s*", "").trim();
        if (text.length() > 7000) text = text.substring(0, 7000).trim() + "…";
        if (text.isEmpty()) text = "Footnote text could not be previewed.";
        body.setText(text);
        body.setTextSize(15.5f);
        body.setTextColor(readerPanelText());
        body.setLineSpacing(dp(3), 1.08f);
        body.setPadding(dp(2), dp(4), dp(2), dp(8));
        scroll.addView(body, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int maxBody = Math.max(dp(100), (int) (getResources().getDisplayMetrics().heightPixels * 0.38f));
        card.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxBody));

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        cardLp.leftMargin = dp(4);
        cardLp.rightMargin = dp(4);
        cardLp.bottomMargin = dp(4);
        overlay.addView(card, cardLp);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.bringToFront();
    }

'''
s = s[:start] + new_method + s[end:]

s = s.replace(
    '                    armFootnoteReturn(targetSourceId);\n                    requestFootnotePreview(targetHref, targetLabel);',
    '                    requestFootnotePreview(targetHref, targetLabel);',
    1,
)

old_touch = '''        readerTouchListener = (v, event) -> {
            // Legacy v2.4/v2.5 paper-curl gesture is intentionally retired.
            // None/Slide are the only live page animations.
            readerTapDetector.onTouchEvent(event);
            return false;
        };'''
assert old_touch in s
new_touch = '''        readerTouchListener = (v, event) -> {
            // A WebView link tap must never also become a reader page/edge tap.
            boolean anchorHit = false;
            if (v instanceof WebView) {
                try {
                    WebView.HitTestResult hit = ((WebView) v).getHitTestResult();
                    int type = hit == null ? WebView.HitTestResult.UNKNOWN_TYPE : hit.getType();
                    anchorHit = type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                            type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE;
                } catch (Exception ignored) {}
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                readerTouchStartedOnLink = anchorHit;
                if (!anchorHit) readerTapDetector.onTouchEvent(event);
                return false;
            }
            if (action == MotionEvent.ACTION_UP) {
                boolean linkTap = readerTouchStartedOnLink || anchorHit;
                readerTouchStartedOnLink = false;
                if (linkTap) {
                    lastReaderLinkTapMs = android.os.SystemClock.elapsedRealtime();
                    return false;
                }
                readerTapDetector.onTouchEvent(event);
                return false;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                readerTouchStartedOnLink = false;
            }
            if (!readerTouchStartedOnLink) readerTapDetector.onTouchEvent(event);
            return false;
        };'''
s = s.replace(old_touch, new_touch, 1)

old_scroll = '''                } else {
                    if (ratio < 0.24f) navigateChapter(-1, true);
                    else if (ratio > 0.76f) navigateChapter(1, false);
                    else toggleControls();
                }'''
assert old_scroll in s
s = s.replace(old_scroll, '''                } else {
                    toggleControls();
                }''', 1)

s = s.replace('        }, 120L);', '        }, 220L);', 1)

back = '''    public void onBackPressed() {
        if (bookSearchDialog != null && bookSearchDialog.isShowing())'''
assert back in s
s = s.replace(
    back,
    '''    public void onBackPressed() {
        if (footnotePreviewOverlay != null) { dismissFootnotePreview(); return; }
        if (bookSearchDialog != null && bookSearchDialog.isShowing())''',
    1,
)

destroy = '''    protected void onDestroy() {
        cancelChromeAutoHide();'''
assert destroy in s
s = s.replace(
    destroy,
    '''    protected void onDestroy() {
        dismissFootnotePreview();
        cancelChromeAutoHide();''',
    1,
)

p.write_text(s)

g = Path('app/build.gradle')
b = g.read_text()
b = b.replace('versionCode 47', 'versionCode 48', 1)
b = b.replace("versionName '2.17.7'", "versionName '2.17.8'", 1)
g.write_text(b)
