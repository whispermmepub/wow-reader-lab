from pathlib import Path

P = Path('app/src/main/java/com/whisper/wowreader/BookReaderActivity.java')
text = P.read_text(encoding='utf-8')
start = text.find('    private void confirmStableChapterReveal(')
end = text.find('    private void finishStableChapterReveal()', start)
if start < 0 or end < 0:
    raise SystemExit('stable reveal method boundaries not found')

replacement = '''    private void confirmStableChapterReveal(int generation, int attempt, int previousWidth, int previousLeft) {
        if (webView == null || generation != chapterLoadGeneration || !chapterLoading) return;
        webView.setAlpha(0f);
        webView.setTranslationX(0f);
        webView.setScaleX(1f);
        webView.setScaleY(1f);

        final String probe = "(function(){try{" +
                "var root=document.getElementById('wow-page-flow')||document.body;" +
                "if(!root)return [-1,-1,-1];" +
                "var de=document.documentElement,b=document.body;" +
                "if(de){de.style.setProperty('zoom','1','important');de.style.setProperty('transform','none','important');}" +
                "if(b){b.style.setProperty('zoom','1','important');b.style.setProperty('transform','none','important');}" +
                "void root.offsetWidth;var r=root.getBoundingClientRect();" +
                "return [Math.round(r.width),Math.round(r.left),Math.round(window.innerWidth||0)];" +
                "}catch(e){return [-1,-1,-1];}})()";

        webView.evaluateJavascript(probe, value -> {
            if (generation != chapterLoadGeneration || !chapterLoading) return;
            int width = -1, left = -1, viewportCss = -1;
            try {
                String clean = value == null ? "" : value.replace("[", "").replace("]", "");
                String[] parts = clean.split(",");
                if (parts.length >= 3) {
                    width = Integer.parseInt(parts[0].trim());
                    left = Integer.parseInt(parts[1].trim());
                    viewportCss = Integer.parseInt(parts[2].trim());
                }
            } catch (Exception ignored) {}

            int tolerance = Math.max(2, Math.round(Math.max(1, viewportCss) * 0.01f));
            boolean saneWidth = width > 0 && viewportCss > 0 && width >= Math.round(viewportCss * 0.82f);
            boolean sameAsPrevious = previousWidth > 0 &&
                    Math.abs(width - previousWidth) <= tolerance &&
                    Math.abs(left - previousLeft) <= tolerance;

            // At least two matching, full-width CSS layout samples are required.
            // The previous chapter snapshot remains on top for the entire check,
            // so the transient narrow frame can never be exposed to the reader.
            if ((saneWidth && sameAsPrevious) || attempt >= 6) {
                webView.postOnAnimation(() -> webView.postOnAnimation(() -> {
                    if (generation != chapterLoadGeneration || !chapterLoading) return;
                    finishStableChapterReveal();
                }));
                return;
            }

            final int nextWidth = width;
            final int nextLeft = left;
            webView.postDelayed(() -> confirmStableChapterReveal(
                    generation, attempt + 1, nextWidth, nextLeft), 70L);
        });
    }

'''
text = text[:start] + replacement + text[end:]

for token in [
    'return [Math.round(r.width),Math.round(r.left),Math.round(window.innerWidth||0)]',
    'value.replace("[", "").replace("]", "")',
    'viewportCss * 0.82f',
    'finishStableChapterReveal();',
]:
    if token not in text:
        raise SystemExit('missing corrected token: ' + token)

if 'replace(""", "")' in text:
    raise SystemExit('broken Java quote escaping still present')
P.write_text(text, encoding='utf-8')
print('v33 stable reveal compile fix applied')
