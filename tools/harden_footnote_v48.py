from pathlib import Path

p = Path('app/src/main/java/com/whisper/wowreader/BookReaderActivity.java')
s = p.read_text()

# Preserve the exact source anchor for footnote resolution; card-only mode no longer arms return state.
old = '''    private void requestFootnotePreview(String href, String label) {
        if (webView == null || href == null || href.trim().isEmpty() || spine.isEmpty()) return;
        footnotePreviewHref = href.trim();
        footnotePreviewLabel = label == null ? "" : label.trim();
        final int sourceSpine = currentSpine;
        final String sourceId = footnoteReturnSourceId;
        new Thread(() -> {
            ReaderSearchIndex.Footnote note = ReaderSearchIndex.resolveFootnote(spine, sourceSpine, footnotePreviewHref, sourceId);'''
assert old in s
new = '''    private void requestFootnotePreview(String href, String label, String sourceId) {
        if (webView == null || href == null || href.trim().isEmpty() || spine.isEmpty()) return;
        footnotePreviewHref = href.trim();
        footnotePreviewLabel = label == null ? "" : label.trim();
        final int sourceSpine = currentSpine;
        final String previewSourceId = sourceId == null ? "" : sourceId;
        new Thread(() -> {
            ReaderSearchIndex.Footnote note = ReaderSearchIndex.resolveFootnote(spine, sourceSpine, footnotePreviewHref, previewSourceId);'''
s = s.replace(old, new, 1)
s = s.replace('requestFootnotePreview(targetHref, targetLabel);', 'requestFootnotePreview(targetHref, targetLabel, targetSourceId);', 1)

# Defense-in-depth suppression: even if WebView native hit testing misses an anchor, a recognized
# footnote click blocks page-turn code long enough for the card overlay to take over.
field = '    private volatile long lastReaderLinkTapMs = 0L;\n    private boolean readerTouchStartedOnLink = false;'
assert field in s
s = s.replace(field, field + '\n    private volatile long footnoteTapSuppressUntilMs = 0L;', 1)

sig = '    private void handleReaderTap(float x, float y) {\n        if (root == null) return;'
assert sig in s
s = s.replace(sig, '    private void handleReaderTap(float x, float y) {\n        if (android.os.SystemClock.uptimeMillis() < footnoteTapSuppressUntilMs) return;\n        if (root == null) return;', 1)

turn = '    private void turnPageFromTap(int delta, float tapY) {\n        if (webView == null || chapterLoading || !"page".equals(readingMode) || delta == 0) return;'
assert turn in s
s = s.replace(turn, '    private void turnPageFromTap(int delta, float tapY) {\n        if (android.os.SystemClock.uptimeMillis() < footnoteTapSuppressUntilMs) return;\n        if (webView == null || chapterLoading || !"page".equals(readingMode) || delta == 0) return;', 1)

needle = '''            if (looksLikeFootnoteReference(href, epubType, role, rel, cssClass)) {
                final String targetHref = href == null ? "" : href;'''
assert needle in s
s = s.replace(needle, '''            if (looksLikeFootnoteReference(href, epubType, role, rel, cssClass)) {
                footnoteTapSuppressUntilMs = android.os.SystemClock.uptimeMillis() + 1600L;
                final String targetHref = href == null ? "" : href;''', 1)

# Clean resolver/backlink noise from card text without changing the actual footnote content.
body_old = '''        TextView body = new TextView(this);
        String text = note.text == null ? "" : note.text.trim();
        text = text.replaceFirst("(?i)^\\\\s*Unknown\\\\s*", "").trim();'''
assert body_old in s
body_new = '''        TextView body = new TextView(this);
        String text = cleanFootnoteDisplayText(note.text);'''
s = s.replace(body_old, body_new, 1)

insert_at = s.index('    private void dismissFootnotePreview() {')
helper = '''    private String cleanFootnoteDisplayText(String raw) {
        if (raw == null) return "";
        String text = raw.replaceAll("\\\\s+", " ").trim();
        text = text.replaceFirst("(?i)^unknown\\\\s*", "");
        text = text.replaceFirst("^\\\\[\\\\s*[←↩↵]?\\\\s*-?\\\\s*\\\\d+\\\\s*\\\\]\\\\s*", "");
        text = text.replaceFirst("^[←↩↵]\\\\s*-?\\\\s*\\\\d+\\\\s*", "");
        return text.trim();
    }

'''
s = s[:insert_at] + helper + s[insert_at:]

p.write_text(s)
