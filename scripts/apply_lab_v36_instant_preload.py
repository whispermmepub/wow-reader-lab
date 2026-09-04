from pathlib import Path
import re

ROOT = Path('.')
R = ROOT / 'app/src/main/java/com/whisper/wowreader/BookReaderActivity.java'
G = ROOT / 'app/build.gradle'
text = R.read_text()

def once(old, new, label):
    global text
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 match, got {n}')
    text = text.replace(old, new, 1)

# v36 dual-WebView state: one active reader + one adjacent chapter warmer.
once(
    '    private WebView webView;\n',
    '    private WebView webView;\n'
    '    private ReaderWebView preloadWebView;\n'
    '    private FrameLayout epubWebContent;\n'
    '    private View.OnTouchListener readerTouchListener;\n'
    '    private int preloadedSpine = -1;\n'
    '    private boolean preloadReady = false;\n'
    '    private boolean preloadLoading = false;\n'
    '    private int preloadGeneration = 0;\n'
    '    private int preferredPreloadDirection = 1;\n',
    'preload fields')

# The same bridge lives on both WebViews; callbacks from the hidden warmer are ignored
# until that exact WebView becomes the active reader.
once(
    '    private class ReaderBridge {\n',
    '    private class ReaderBridge {\n'
    '        private final WebView owner;\n\n'
    '        ReaderBridge(WebView owner) {\n'
    '            this.owner = owner;\n'
    '        }\n\n',
    'ReaderBridge constructor')

bridge_methods = [
    'public void onSelection(String text, int start, int end) {',
    'public void onScroll(int p) {',
    'public void onScrollReady(int generation) {',
    'public void onPage(int page, int count, int p) {',
    'public void onPageReady(int generation, int page, int count, int p) {',
    'public void onStyleReady(int token) {',
    'public void onPageTurnComplete(int page, int count, int p) {',
    'public void onEmptyChapter() {',
    'public void pageEngineFailed(String message) {',
    'public void requestChapter(int delta) {',
]
for sig in bridge_methods:
    old = '        ' + sig + '\n'
    new = old + '            if (owner != webView) return;\n'
    once(old, new, 'bridge guard ' + sig)

once('webView.addJavascriptInterface(new ReaderBridge(), "WoW");',
     'webView.addJavascriptInterface(new ReaderBridge(webView), "WoW");',
     'active bridge owner')

# Reuse touch handling when the warmed WebView is promoted to active.
old_touch = '''        webView.setOnTouchListener((v, event) -> {
            // Legacy v2.4/v2.5 paper-curl gesture is intentionally retired.
            // None/Slide are the only live page animations.
            readerTapDetector.onTouchEvent(event);
            return false;
        });
'''
new_touch = '''        readerTouchListener = (v, event) -> {
            // Legacy v2.4/v2.5 paper-curl gesture is intentionally retired.
            // None/Slide are the only live page animations.
            readerTapDetector.onTouchEvent(event);
            return false;
        };
        webView.setOnTouchListener(readerTouchListener);
'''
once(old_touch, new_touch, 'shared touch listener')

# Turn the current inline WebViewClient into a reusable factory, so both WebViews
# run the same reader client. Hidden-preload page finishes branch out before any
# active-reader state is touched.
start = text.index('        webView.setWebViewClient(new WebViewClient() {')
end_marker = '\n        });\n\n        content.addView(webView'
end = text.index(end_marker, start)
client_block = text[start:end + len('\n        });')]
client_body = client_block.replace(
    '        webView.setWebViewClient(new WebViewClient() {',
    '    private WebViewClient createReaderWebViewClient() {\n        return new WebViewClient() {', 1)
client_body = client_body[:-len('        });')] + '        };\n    }'
needle = '                super.onPageFinished(view, url);\n'
if needle not in client_body:
    raise SystemExit('client factory: onPageFinished anchor missing')
client_body = client_body.replace(
    needle,
    needle +
    '                if (view != webView) {\n'
    '                    handlePreloadPageFinished(view, url);\n'
    '                    return;\n'
    '                }\n',
    1)
text = text[:start] + '        webView.setWebViewClient(createReaderWebViewClient());' + text[end + len('\n        });'):]
insert_at = text.index('    private void setupWebView(FrameLayout content) {')
text = text[:insert_at] + client_body + '\n\n' + text[insert_at:]

# Remember the reader content layer and build a same-size hidden WebView behind the
# active one. INVISIBLE WebViews can be throttled on some devices, so keep it attached
# and nearly transparent behind the opaque active reader; it is disabled for input.
once(
    '    private void setupWebView(FrameLayout content) {\n        webView = new ReaderWebView(this);\n',
    '    private void setupWebView(FrameLayout content) {\n        epubWebContent = content;\n        webView = new ReaderWebView(this);\n',
    'content field')

active_add = '''        content.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
'''
preload_add = active_add + '''
        preloadWebView = createPreloadWebView();
        if (preloadWebView != null) {
            preloadWebView.setOnTouchListener(readerTouchListener);
            preloadWebView.setWebViewClient(createReaderWebViewClient());
            preloadWebView.setEnabled(false);
            preloadWebView.setAlpha(0.01f);
            preloadWebView.setVisibility(View.VISIBLE);
            content.addView(preloadWebView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            webView.bringToFront();
        }
'''
once(active_add, preload_add, 'preload view attachment')

# Helpers for the hidden chapter WebView. It gets the exact reader WebSettings and a
# lightweight warm layout pass; the expensive full reader style/page map is applied
# only after promotion, when the document/images are already parsed and hot.
helpers = r'''
    private ReaderWebView createPreloadWebView() {
        try {
            ReaderWebView view = new ReaderWebView(this);
            WebSettings s = view.getSettings();
            s.setJavaScriptEnabled(true);
            s.setUseWideViewPort(false);
            s.setLoadWithOverviewMode(false);
            s.setTextZoom(Math.max(80, Math.min(200, fontPercent)));
            s.setAllowFileAccess(true);
            s.setAllowContentAccess(true);
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);
            s.setDefaultTextEncodingName("UTF-8");
            s.setBuiltInZoomControls(false);
            s.setDisplayZoomControls(false);
            s.setSupportZoom(false);
            view.setOverScrollMode(View.OVER_SCROLL_NEVER);
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            view.setHorizontalScrollBarEnabled(false);
            view.setVerticalScrollBarEnabled(false);
            view.addJavascriptInterface(new ReaderBridge(view), "WoW");
            int solid = readerTheme == 2 ? Color.rgb(18, 18, 18) :
                    (readerTheme == 1 ? Color.rgb(244, 236, 216) : Color.WHITE);
            view.setBackgroundColor(solid);
            return view;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void handlePreloadPageFinished(WebView view, String url) {
        if (view == null || view != preloadWebView || !preloadLoading || preloadedSpine < 0) return;
        final int token = preloadGeneration;
        warmPreloadedChapter(view, token);
    }

    private void warmPreloadedChapter(WebView view, int token) {
        if (view == null || view != preloadWebView || token != preloadGeneration || preloadedSpine < 0) return;
        try { view.getSettings().setTextZoom(Math.max(80, Math.min(200, fontPercent))); }
        catch (Exception ignored) {}

        String bg = readerTheme == 2 ? "#121212" : (readerTheme == 1 ? "#F4ECD8" : "#FFFFFF");
        String fg = readerTheme == 2 ? "#E8EAED" : (readerTheme == 1 ? "#4A4033" : "#202124");
        double line = lineSpacing / 100.0;
        int safeMargin = Math.max(3, Math.min(14, marginPercent));
        String script;
        if ("page".equals(readingMode)) {
            String css = "html,body{height:100% !important;width:100% !important;margin:0 !important;padding:0 !important;overflow:hidden !important;background:" + bg + " !important;color:" + fg + " !important;transform:none !important;zoom:1 !important;}" +
                    "body{font-size:100% !important;line-height:" + line + " !important;max-width:none !important;}" +
                    "#wow-page-viewport{position:absolute !important;left:0 !important;top:0 !important;width:100vw !important;height:100vh !important;overflow:hidden !important;}" +
                    "#wow-page-flow{position:absolute !important;left:0 !important;top:0 !important;height:100vh !important;margin:0 !important;padding:4.2vh 0 5.2vh 0 !important;box-sizing:border-box !important;overflow:visible !important;column-fill:auto !important;transform-origin:0 0 !important;}" +
                    "#wow-page-flow img,#wow-page-flow svg,#wow-page-flow video,#wow-page-flow table{max-width:100% !important;height:auto !important;}";
            script = "(function(){try{" +
                    "var s=document.getElementById('wow-preload-style');if(!s){s=document.createElement('style');s.id='wow-preload-style';document.head.appendChild(s);}s.innerHTML=" + jsQuote(css) + ";" +
                    "var vp=document.getElementById('wow-page-viewport'),flow=document.getElementById('wow-page-flow');" +
                    "if(!vp){vp=document.createElement('div');vp.id='wow-page-viewport';if(!flow){flow=document.createElement('div');flow.id='wow-page-flow';while(document.body.firstChild)flow.appendChild(document.body.firstChild);}vp.appendChild(flow);document.body.appendChild(vp);}" +
                    "var w=Math.max(1,vp.clientWidth||window.innerWidth),m=Math.max(0,Math.round(w*" + (safeMargin / 100.0) + ")),pw=Math.max(1,w-2*m),gap=Math.max(0,w-pw);" +
                    "flow.style.width=pw+'px';flow.style.minWidth=pw+'px';flow.style.columnWidth=pw+'px';flow.style.columnGap=gap+'px';flow.style.transform='translate3d('+m+'px,0,0)';" +
                    "var wraps=flow.querySelectorAll('div,section,article,main,p,blockquote,dd,dt');for(var i=0;i<wraps.length;i++){var n=wraps[i],t=(n.textContent||'').replace(/\\s+/g,' ').trim();if(t.length<120)continue;var r=n.getBoundingClientRect();if(r.width>0&&r.width<pw*.90){n.style.setProperty('width','auto','important');n.style.setProperty('max-width','none','important');n.style.setProperty('margin-left','0','important');n.style.setProperty('margin-right','0','important');}}" +
                    "return true;}catch(e){return false;}})()";
        } else {
            String css = "html{overflow-x:hidden !important;background:" + bg + " !important;color:" + fg + " !important;}" +
                    "body{font-size:100% !important;line-height:" + line + " !important;padding:5vh " + safeMargin + "vw 12vh " + safeMargin + "vw !important;height:auto !important;max-width:900px !important;margin:auto !important;box-sizing:border-box !important;background:" + bg + " !important;color:" + fg + " !important;column-width:auto !important;column-gap:normal !important;transform:none !important;}" +
                    "body *{max-width:100%;}img,svg,video{max-width:100% !important;height:auto !important;}";
            script = "(function(){try{var vp=document.getElementById('wow-page-viewport'),flow=document.getElementById('wow-page-flow');if(flow){var before=vp||flow;while(flow.firstChild)document.body.insertBefore(flow.firstChild,before);if(vp)vp.remove();else flow.remove();}" +
                    "var s=document.getElementById('wow-preload-style');if(!s){s=document.createElement('style');s.id='wow-preload-style';document.head.appendChild(s);}s.innerHTML=" + jsQuote(css) + ";return true;}catch(e){return false;}})()";
        }

        try {
            view.evaluateJavascript(script, result -> view.postOnAnimation(() -> view.postOnAnimation(() -> {
                if (view != preloadWebView || token != preloadGeneration || !preloadLoading || preloadedSpine < 0) return;
                preloadReady = true;
                preloadLoading = false;
            })));
        } catch (Exception ignored) {
            if (view == preloadWebView && token == preloadGeneration) {
                preloadReady = true;
                preloadLoading = false;
            }
        }
    }

    private void scheduleAdjacentChapterPreload(int direction) {
        if (isPdf || preloadWebView == null || spine.isEmpty() || chapterLoading || isFinishing()) return;
        int dir = direction < 0 ? -1 : 1;
        int target = currentSpine + dir;
        if (target < 0 || target >= spine.size()) {
            dir = -dir;
            target = currentSpine + dir;
        }
        if (target < 0 || target >= spine.size() || target == currentSpine) return;
        if (preloadedSpine == target && (preloadReady || preloadLoading)) return;

        preferredPreloadDirection = dir;
        preloadGeneration++;
        preloadedSpine = target;
        preloadReady = false;
        preloadLoading = true;
        try { preloadWebView.stopLoading(); } catch (Exception ignored) {}
        preloadWebView.setEnabled(false);
        preloadWebView.setVisibility(View.VISIBLE);
        preloadWebView.setAlpha(0.01f);
        preloadWebView.setScaleX(1f);
        preloadWebView.setScaleY(1f);
        preloadWebView.setTranslationX(0f);
        try { preloadWebView.getSettings().setTextZoom(Math.max(80, Math.min(200, fontPercent))); }
        catch (Exception ignored) {}
        try {
            preloadWebView.loadUrl(Uri.fromFile(spine.get(target)).toString());
        } catch (Exception e) {
            cancelChapterPreload();
        }
    }

    private void cancelChapterPreload() {
        preloadGeneration++;
        preloadReady = false;
        preloadLoading = false;
        preloadedSpine = -1;
        if (preloadWebView != null) {
            try { preloadWebView.stopLoading(); } catch (Exception ignored) {}
            preloadWebView.setEnabled(false);
            preloadWebView.setAlpha(0.01f);
        }
    }

    private boolean activatePreloadedChapterIfReady() {
        if (preloadWebView == null || !preloadReady || preloadedSpine != currentSpine) return false;
        if (!(webView instanceof ReaderWebView)) return false;

        ReaderWebView incoming = preloadWebView;
        ReaderWebView outgoing = (ReaderWebView) webView;
        preloadGeneration++;
        preloadReady = false;
        preloadLoading = false;
        preloadedSpine = -1;

        webView = incoming;
        preloadWebView = outgoing;
        currentSelection = null;
        hideSelectionBar();
        final int generation = ++chapterLoadGeneration;
        chapterLoading = true;
        pageTurnLocked = "page".equals(readingMode);
        currentPageInChapter = 1;
        pageCountInChapter = 1;

        outgoing.animate().cancel();
        outgoing.setEnabled(false);
        outgoing.setAlpha(0.01f);
        outgoing.setVisibility(View.VISIBLE);
        outgoing.setScaleX(1f);
        outgoing.setScaleY(1f);
        outgoing.setTranslationX(0f);

        incoming.animate().cancel();
        incoming.setEnabled(true);
        incoming.setVisibility(View.VISIBLE);
        incoming.setScaleX(1f);
        incoming.setScaleY(1f);
        incoming.setTranslationX(0f);
        incoming.setAlpha(0f);
        incoming.bringToFront();
        if (chapterTransitionOverlay != null && chapterTransitionOverlay.getVisibility() == View.VISIBLE)
            chapterTransitionOverlay.bringToFront();
        if (readerStyleOverlay != null && readerStyleOverlay.getVisibility() == View.VISIBLE)
            readerStyleOverlay.bringToFront();
        if (pageSlideOverlay != null && pageSlideOverlay.getVisibility() == View.VISIBLE)
            pageSlideOverlay.bringToFront();

        updateEpubProgress(currentProgressPermille);
        updateBookmarkIcon();
        applyReaderStyle(true);
        webView.postDelayed(() -> {
            if (generation == chapterLoadGeneration) applySavedAnnotations();
        }, 260L);
        webView.postDelayed(() -> {
            if (generation == chapterLoadGeneration) installSelectionWatcher();
        }, 320L);
        webView.postDelayed(() -> {
            if (generation == chapterLoadGeneration && chapterLoading && "scroll".equals(readingMode))
                completePageReady(generation);
        }, 850L);
        webView.postDelayed(() -> {
            if (generation == chapterLoadGeneration && chapterLoading && "page".equals(readingMode))
                forceChapterRepaginate(generation);
        }, 1050L);
        return true;
    }
'''

helper_anchor = '    private void setupWebView(FrameLayout content) {'
idx = text.index(helper_anchor)
text = text[:idx] + helpers + '\n' + text[idx:]

# Promote the warm WebView instead of reloading when possible. If the warmer is only
# a few milliseconds from ready, give it a tiny grace window before falling back.
load_anchor = '''        chapterTransitionLoadDeferred = false;

        currentSelection = null;
'''
load_replacement = '''        chapterTransitionLoadDeferred = false;

        if (activatePreloadedChapterIfReady()) return;
        if (preloadWebView != null && preloadedSpine == currentSpine && preloadLoading) {
            final int waitToken = preloadGeneration;
            chapterLoading = true;
            pageTurnLocked = "page".equals(readingMode);
            webView.postDelayed(() -> {
                if (waitToken != preloadGeneration) return;
                if (activatePreloadedChapterIfReady()) return;
                cancelChapterPreload();
                loadCurrentEpubChapter();
            }, 100L);
            return;
        }
        cancelChapterPreload();

        currentSelection = null;
'''
once(load_anchor, load_replacement, 'activate preload in loadCurrentEpubChapter')

# Track navigation direction so repeated forward/back chapter movement remains hot.
once(
    '        prepareChapterTransition(delta);\n        lastChapterNavMs = now;\n',
    '        preferredPreloadDirection = delta < 0 ? -1 : 1;\n        prepareChapterTransition(delta);\n        lastChapterNavMs = now;\n',
    'navigation preload direction')
once(
    '                    int direction = spineIndex > currentSpine ? 1 : -1;\n                    prepareChapterTransition(direction);\n',
    '                    int direction = spineIndex > currentSpine ? 1 : -1;\n                    preferredPreloadDirection = direction;\n                    prepareChapterTransition(direction);\n',
    'TOC preload direction')

# Start warming the next likely chapter as soon as the active chapter is stable.
once(
    '        finishChapterFadeImmediate();\n        prewarmAdjacentChapters();\n    }\n\n    private void prewarmAdjacentChapters() {',
    '        finishChapterFadeImmediate();\n        prewarmAdjacentChapters();\n        scheduleAdjacentChapterPreload(preferredPreloadDirection);\n    }\n\n    private void prewarmAdjacentChapters() {',
    'schedule preload after stable reveal')

# Keep hidden warmer theme-aligned and release it safely.
once(
    '        if (webView != null) webView.setBackgroundColor(solid);\n        updateNightLightOverlay();\n',
    '        if (webView != null) webView.setBackgroundColor(solid);\n        if (preloadWebView != null) preloadWebView.setBackgroundColor(solid);\n        updateNightLightOverlay();\n',
    'preload theme')

on_destroy_anchor = '''        if (webView != null) {
            try { webView.removeJavascriptInterface("WoW"); } catch (Exception ignored) {}
            try { webView.stopLoading(); } catch (Exception ignored) {}
            try { webView.destroy(); } catch (Exception ignored) {}
        }
'''
on_destroy_repl = on_destroy_anchor + '''        if (preloadWebView != null) {
            try { preloadWebView.removeJavascriptInterface("WoW"); } catch (Exception ignored) {}
            try { preloadWebView.stopLoading(); } catch (Exception ignored) {}
            try { preloadWebView.destroy(); } catch (Exception ignored) {}
            preloadWebView = null;
        }
'''
once(on_destroy_anchor, on_destroy_repl, 'destroy preload view')

# Low-memory devices keep correctness: abandon speculative work, never the active reader.
on_pause_anchor = '    @Override\n    protected void onPause() {'
trim_method = '''    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
            cancelChapterPreload();
    }

'''
idx = text.index(on_pause_anchor)
text = text[:idx] + trim_method + text[idx:]

R.write_text(text)

# Version bump.
g = G.read_text()
if 'versionCode 35' not in g or "versionName '2.16.5-lab-v35'" not in g:
    raise SystemExit('version anchors missing')
g = g.replace('versionCode 35', 'versionCode 36', 1)
g = g.replace("versionName '2.16.5-lab-v35'", "versionName '2.16.6-lab-v36'", 1)
G.write_text(g)

print('v36 instant chapter preload patch applied')
