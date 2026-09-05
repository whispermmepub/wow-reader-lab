from pathlib import Path
import re


def replace_once(text, pattern, replacement, label, flags=re.S):
    out, n = re.subn(pattern, replacement, text, count=1, flags=flags)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 replacement, got {n}')
    return out

# Version bump
gradle = Path('app/build.gradle')
g = gradle.read_text()
g = g.replace('versionCode 45', 'versionCode 46', 1)
g = g.replace("versionName '2.17.5'", "versionName '2.17.6'", 1)
if 'versionCode 46' not in g or "versionName '2.17.6'" not in g:
    raise SystemExit('version bump failed')
gradle.write_text(g)

# Resolve arbitrary same-book link targets so non-standard Notes chapter links are recognized.
idx = Path('app/src/main/java/com/whisper/wowreader/ReaderSearchIndex.java')
s = idx.read_text()
if 'static int resolveTargetSpine(' not in s:
    marker = '    static Footnote resolveFootnote(List<File> spine, int sourceSpine, String href, String sourceId) {'
    if marker not in s:
        raise SystemExit('resolveFootnote marker missing')
    helper = '''    static int resolveTargetSpine(List<File> spine, int sourceSpine, String href) {
        if (spine == null || spine.isEmpty() || sourceSpine < 0 || sourceSpine >= spine.size()) return -1;
        String raw = href == null ? "" : href.trim();
        int hash = raw.indexOf('#');
        String filePart = hash >= 0 ? raw.substring(0, hash) : raw;
        if (filePart.isEmpty()) return sourceSpine;
        try {
            String decoded = Uri.decode(filePart);
            String lower = decoded.toLowerCase(Locale.ROOT);
            if (lower.startsWith("http://") || lower.startsWith("https://") ||
                    lower.startsWith("mailto:") || lower.startsWith("tel:")) return -1;
            File source = spine.get(sourceSpine);
            File target;
            if (decoded.startsWith("file://")) target = new File(Uri.parse(decoded).getPath());
            else target = new File(source.getParentFile(), decoded);
            String wanted = target.getCanonicalPath();
            for (int i = 0; i < spine.size(); i++) {
                if (wanted.equals(spine.get(i).getCanonicalPath())) return i;
            }
        } catch (Exception ignored) {}
        return -1;
    }

'''
    s = s.replace(marker, helper + marker, 1)
idx.write_text(s)

p = Path('app/src/main/java/com/whisper/wowreader/BookReaderActivity.java')
s = p.read_text()

# Footnote recognition: retain standards-based detection, plus recognize dedicated Notes/Footnotes/Endnotes spine items.
ref_replacement = '''    private boolean looksLikeFootnoteReference(String href, String epubType, String role, String rel, String cssClass) {
        String meta = navLower(epubType + " " + role + " " + rel + " " + cssClass);
        if (meta.contains("noteref") || meta.contains("doc-noteref") || meta.contains("footnote-ref") ||
                meta.contains("footnoteref") || meta.contains("fnref") || meta.contains("endnote-ref")) return true;
        String h = navLower(href);
        int hash = h.indexOf('#');
        String frag = hash >= 0 ? h.substring(hash + 1) : "";
        frag = Uri.decode(frag).toLowerCase(Locale.ROOT);
        boolean named = frag.startsWith("fn") || frag.startsWith("_fn") || frag.startsWith("ftn") || frag.startsWith("_ftn") ||
                frag.contains("footnote") || frag.contains("noteref") || frag.startsWith("note") ||
                frag.startsWith("endnote") || frag.startsWith("_edn");
        return named || looksLikeFootnoteDestination(href);
    }

    private boolean looksLikeFootnoteDestination(String href) {
        if (href == null || href.indexOf('#') < 0 || spine.isEmpty()) return false;
        int target = ReaderSearchIndex.resolveTargetSpine(spine, currentSpine, href);
        if (target < 0 || target >= spine.size()) return false;
        String title = target < chapterTitles.size() ? chapterTitles.get(target) : "";
        String file = spine.get(target) == null ? "" : spine.get(target).getName();
        String meta = navLower(title + " " + file).replace('_', ' ').replace('-', ' ').replace('.', ' ');
        return meta.matches(".*\\\\b(footnotes?|endnotes?|notes?)\\\\b.*");
    }

'''
s = replace_once(
    s,
    r'    private boolean looksLikeFootnoteReference\(String href, String epubType, String role, String rel, String cssClass\) \{.*?\n    \}\n\n(?=    private boolean looksLikeFootnoteBacklink)',
    ref_replacement,
    'footnote reference method')

back_replacement = '''    private boolean looksLikeFootnoteBacklink(String href, String epubType, String role, String rel, String cssClass) {
        String meta = navLower(epubType + " " + role + " " + rel + " " + cssClass);
        if (meta.contains("backlink") || meta.contains("doc-backlink") || meta.contains("footnote-back") ||
                meta.contains("note-back") || meta.contains("fnback")) return true;
        String source = footnoteReturnSourceId == null ? "" : footnoteReturnSourceId.trim();
        if (!source.isEmpty() && href != null) {
            int hash = href.indexOf('#');
            if (hash >= 0 && hash + 1 < href.length()) {
                String fragment = Uri.decode(href.substring(hash + 1));
                if (source.equals(fragment)) return true;
            }
        }
        // Dedicated Notes chapters often use opaque backlink ids. If the note is open and
        // the tapped internal link resolves back to the exact source spine, treat it as Return.
        if (footnoteNavigationActive && href != null && href.indexOf('#') >= 0 &&
                currentSpine != footnoteReturnSpine && footnoteReturnSpine >= 0) {
            int target = ReaderSearchIndex.resolveTargetSpine(spine, currentSpine, href);
            if (target == footnoteReturnSpine) return true;
        }
        return false;
    }

'''
s = replace_once(
    s,
    r'    private boolean looksLikeFootnoteBacklink\(String href, String epubType, String role, String rel, String cssClass\) \{.*?\n    \}\n\n(?=    private synchronized void armFootnoteReturn)',
    back_replacement,
    'footnote backlink method')

# JavaScriptInterface callbacks run on WebView's bridge thread. Do not touch WebView there.
# Return true immediately so the browser default navigation is prevented, then snapshot/show UI on the main thread.
bridge_replacement = '''        @JavascriptInterface
        public boolean onReaderLinkTap(String href, String epubType, String role, String rel, String cssClass, String sourceId, String label) {
            if (owner != webView) return false;
            if (footnoteNavigationActive && looksLikeFootnoteBacklink(href, epubType, role, rel, cssClass)) {
                runOnUiThread(BookReaderActivity.this::restoreFootnoteReturn);
                return true;
            }
            if (looksLikeFootnoteReference(href, epubType, role, rel, cssClass)) {
                final String targetHref = href == null ? "" : href;
                final String targetLabel = label == null ? "" : label;
                final String targetSourceId = sourceId == null ? "" : sourceId;
                runOnUiThread(() -> {
                    if (isFinishing() || owner != webView) return;
                    armFootnoteReturn(targetSourceId);
                    requestFootnotePreview(targetHref, targetLabel);
                });
                return true;
            }
            return false;
        }

'''
s = replace_once(
    s,
    r'        @JavascriptInterface\n        public boolean onReaderLinkTap\(String href, String epubType, String role, String rel, String cssClass, String sourceId, String label\) \{.*?\n        \}\n\n(?=        @JavascriptInterface\n        public void onSelection)',
    bridge_replacement,
    'ReaderBridge onReaderLinkTap')

# Search screen follows the active reader theme instead of forcing a black UI.
s = s.replace('final Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);',
              'final Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);', 1)
old_colors = '''        int bg = Color.rgb(22, 23, 26);
        int surface = Color.rgb(35, 36, 40);
        int text = Color.rgb(244, 245, 247);
        int sub = Color.rgb(178, 181, 189);
        int accent = Color.rgb(128, 203, 196);'''
new_colors = '''        int bg = readerTheme == 2 ? Color.rgb(18, 18, 18) :
                (readerTheme == 1 ? Color.rgb(244, 236, 216) : Color.WHITE);
        int surface = readerPanelBase();
        int text = readerPanelText();
        int sub = readerPanelSubText();
        int accent = readerAccent();'''
if old_colors not in s:
    raise SystemExit('search hardcoded color block missing')
s = s.replace(old_colors, new_colors, 1)
s = s.replace('header.setBackground(glassPanel(surface, dp(22), Color.rgb(55, 57, 64)));',
              'header.setBackground(glassPanel(surface, dp(22), readerPanelStroke()));', 1)
s = s.replace('row.setBackground(glassPanel(surface, dp(15), Color.rgb(52, 54, 60)));',
              'row.setBackground(glassPanel(surface, dp(15), readerPanelStroke()));', 1)
s = s.replace('bar.setBackground(glassPanel(Color.rgb(28, 29, 32), dp(20), Color.rgb(70, 72, 78)));',
              'bar.setBackground(glassPanel(readerPanelBase(), dp(20), readerPanelStroke()));', 1)
# Scope these replacements to the search navigation block by replacing only the first matching instances after its marker.
nav_start = s.find('    private void showSearchNavigationBar() {')
nav_end = s.find('    private void updateSearchNavigationLabel()', nav_start)
if nav_start < 0 or nav_end < 0:
    raise SystemExit('search navigation block missing')
nav = s[nav_start:nav_end]
nav = nav.replace('close.setTextColor(Color.WHITE);', 'close.setTextColor(readerPanelText());', 1)
nav = nav.replace('searchNavigationLabel.setTextColor(Color.WHITE);', 'searchNavigationLabel.setTextColor(readerPanelText());', 1)
nav = nav.replace('prev.setTextColor(Color.WHITE);', 'prev.setTextColor(readerPanelText());', 1)
nav = nav.replace('next.setTextColor(Color.WHITE);', 'next.setTextColor(readerPanelText());', 1)
needle = '''        searchNavigationBar.setVisibility(View.VISIBLE);
        searchNavigationBar.bringToFront();'''
repl = '''        searchNavigationBar.setBackground(glassPanel(readerPanelBase(), dp(20), readerPanelStroke()));
        tintChromeChildren(searchNavigationBar, readerPanelText());
        searchNavigationBar.setVisibility(View.VISIBLE);
        searchNavigationBar.bringToFront();'''
if needle not in nav:
    raise SystemExit('search navigation visibility block missing')
nav = nav.replace(needle, repl, 1)
s = s[:nav_start] + nav + s[nav_end:]

p.write_text(s)

# Source invariants
assert 'versionCode 46' in g
assert "versionName '2.17.6'" in g
assert 'looksLikeFootnoteDestination' in s
assert 'runOnUiThread(BookReaderActivity.this::restoreFootnoteReturn)' in s
assert 'Theme_Black_NoTitleBar_Fullscreen' not in s
assert 'Theme_DeviceDefault_NoActionBar_Fullscreen' in s
print('v46 patch applied')
