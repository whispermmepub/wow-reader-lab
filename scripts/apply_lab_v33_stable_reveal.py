from pathlib import Path

P = Path('app/src/main/java/com/whisper/wowreader/BookReaderActivity.java')
B = Path('app/build.gradle')
text = P.read_text(encoding='utf-8')

old = '''    private void revealStableChapter() {
        if (webView != null) {
            webView.animate().cancel();
            webView.setScaleX(1f);
            webView.setScaleY(1f);
            webView.setAlpha(1f);
            // Chapter boundaries are intentionally animation-free.
            // None/Slide still applies to normal pages inside a chapter.
            webView.setTranslationX(0f);
        }
        hideInitialReaderLoading();
        pageTurnLocked = false;
        chapterLoading = false;
        pendingChapterCurlDirection = 0;
        if (pageCurlView != null && !pageCurlView.isBusy()) pageCurlView.release();
        finishChapterFade();
        prewarmAdjacentChapters();
    }'''

new = '''    private void revealStableChapter() {
        // Never expose the new chapter's first WebView paint. Some EPUBs briefly
        // render a narrow/shifted column before the page engine finishes its final
        // viewport + typography pass. Keep the previous chapter snapshot visible
        // and the WebView hidden until two consecutive layout samples are stable.
        final int generation = chapterLoadGeneration;
        confirmStableChapterReveal(generation, 0, -1, -1);
    }

    private void confirmStableChapterReveal(int generation, int attempt, int previousWidth, int previousLeft) {
        if (webView == null || generation != chapterLoadGeneration || !chapterLoading) return;
        webView.setAlpha(0f);
        webView.setTranslationX(0f);
        webView.setScaleX(1f);
        webView.setScaleY(1f);

        final String probe = "(function(){try{" +
                "var root=document.getElementById('wow-page-flow')||document.body;" +
                "if(!root)return '-1,-1';" +
                "var de=document.documentElement,b=document.body;" +
                "if(de){de.style.setProperty('zoom','1','important');de.style.setProperty('transform','none','important');}" +
                "if(b){b.style.setProperty('zoom','1','important');b.style.setProperty('transform','none','important');}" +
                "void root.offsetWidth;var r=root.getBoundingClientRect();" +
                "return Math.round(r.width)+','+Math.round(r.left);" +
                "}catch(e){return '-1,-1';}})()";

        webView.evaluateJavascript(probe, value -> {
            if (generation != chapterLoadGeneration || !chapterLoading) return;
            int width = -1, left = -1;
            try {
                String clean = value == null ? "" : value.replace("\\\"", "").replace("\"", "");
                String[] parts = clean.split(",");
                if (parts.length >= 2) {
                    width = Integer.parseInt(parts[0].trim());
                    left = Integer.parseInt(parts[1].trim());
                }
            } catch (Exception ignored) {}

            int viewport = Math.max(1, webView.getWidth());
            boolean saneWidth = width > 0 && width >= Math.round(viewport * 0.82f);
            boolean sameAsPrevious = previousWidth > 0 &&
                    Math.abs(width - previousWidth) <= Math.max(2, Math.round(viewport * 0.01f)) &&
                    Math.abs(left - previousLeft) <= Math.max(2, Math.round(viewport * 0.01f));

            // Require a sane full-width layout and two matching samples. The hard
            // fallback is deliberately conservative so slow devices still never
            // flash the transient narrow chapter frame seen in the v32 test video.
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

    private void finishStableChapterReveal() {
        if (webView != null) {
            webView.animate().cancel();
            webView.setScaleX(1f);
            webView.setScaleY(1f);
            webView.setTranslationX(0f);
            webView.setAlpha(1f);
        }
        hideInitialReaderLoading();
        pageTurnLocked = false;
        chapterLoading = false;
        pendingChapterCurlDirection = 0;
        if (pageCurlView != null && !pageCurlView.isBusy()) pageCurlView.release();
        finishChapterFadeImmediate();
        prewarmAdjacentChapters();
    }'''

if old not in text:
    raise SystemExit('v32 revealStableChapter block not found')
text = text.replace(old, new, 1)

# The old chapter snapshot must remain visible until finishStableChapterReveal.
# prepareChapterTransition already makes the new WebView transparent; keep that
# contract explicit and fail if it regressed.
for token in [
    'webView.setAlpha(0f);',
    'chapterTransitionOverlay.setImageBitmap(shot);',
    'pendingChapterFade = true;',
    'confirmStableChapterReveal(generation, 0, -1, -1);',
    'finishChapterFadeImmediate();',
]:
    if token not in text:
        raise SystemExit('Required stable reveal token missing: ' + token)

P.write_text(text, encoding='utf-8')

build = B.read_text(encoding='utf-8')
build = build.replace('versionCode 32', 'versionCode 33', 1)
build = build.replace("versionName '2.16.2-lab-v32'", "versionName '2.16.3-lab-v33'", 1)
if 'versionCode 33' not in build or "versionName '2.16.3-lab-v33'" not in build:
    raise SystemExit('version bump failed')
B.write_text(build, encoding='utf-8')
print('v33 stable chapter reveal patch applied')
