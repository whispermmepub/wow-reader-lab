from pathlib import Path

P = Path('app/src/main/java/com/whisper/wowreader/BookReaderActivity.java')
B = Path('app/build.gradle')
text = P.read_text(encoding='utf-8')

# 1) Transition capture state.
old_fields = '''    private int pendingChapterCurlDirection = 0;
    private boolean pendingChapterFade = false;
    private int pendingChapterDirection = 0;
    private GestureDetector readerTapDetector;'''
new_fields = '''    private int pendingChapterCurlDirection = 0;
    private boolean pendingChapterFade = false;
    private int pendingChapterDirection = 0;
    private boolean chapterTransitionCapturePending = false;
    private boolean chapterTransitionLoadDeferred = false;
    private int chapterTransitionCaptureToken = 0;
    private GestureDetector readerTapDetector;'''
if old_fields not in text:
    raise SystemExit('transition fields anchor missing')
text = text.replace(old_fields, new_fields, 1)

# 2) Snapshot overlay must map exact WebView bounds; PixelCopy already returns the exact viewport bitmap.
old_scale = '''        chapterTransitionOverlay = new ImageView(this);
        chapterTransitionOverlay.setScaleType(ImageView.ScaleType.CENTER_CROP);'''
new_scale = '''        chapterTransitionOverlay = new ImageView(this);
        // PixelCopy captures the exact WebView viewport. Map that bitmap 1:1 to the
        // same MATCH_PARENT bounds; never crop/zoom the outgoing chapter frame.
        chapterTransitionOverlay.setScaleType(ImageView.ScaleType.FIT_XY);'''
if old_scale not in text:
    raise SystemExit('chapter overlay scale anchor missing')
text = text.replace(old_scale, new_scale, 1)

# 3) Defer the actual load until compositor capture completes. This keeps the live
# old chapter untouched while PixelCopy is taking its exact on-screen frame.
old_load = '''    private void loadCurrentEpubChapter() {
        if (spine.isEmpty() || webView == null) return;

        currentSelection = null;'''
new_load = '''    private void loadCurrentEpubChapter() {
        if (spine.isEmpty() || webView == null) return;
        if (chapterTransitionCapturePending) {
            chapterTransitionLoadDeferred = true;
            chapterLoading = true;
            pageTurnLocked = "page".equals(readingMode);
            return;
        }
        chapterTransitionLoadDeferred = false;

        currentSelection = null;'''
if old_load not in text:
    raise SystemExit('loadCurrentEpubChapter anchor missing')
text = text.replace(old_load, new_load, 1)

# 4) Replace software WebView.draw chapter freeze with compositor PixelCopy on API 26+.
old_prepare = '''    private void prepareChapterTransition(int direction) {
        if (webView == null || webView.getUrl() == null || chapterTransitionOverlay == null) return;
        Bitmap shot = captureWebViewBitmap();
        if (shot == null) return;

        pendingChapterDirection = direction < 0 ? -1 : 1;
        pendingChapterCurlDirection = 0;
        if (chapterTransitionBitmap != null && !chapterTransitionBitmap.isRecycled())
            chapterTransitionBitmap.recycle();
        chapterTransitionBitmap = shot;
        chapterTransitionOverlay.setImageBitmap(shot);
        chapterTransitionOverlay.animate().cancel();
        chapterTransitionOverlay.setAlpha(1f);
        chapterTransitionOverlay.setTranslationX(0f);
        chapterTransitionOverlay.setVisibility(View.VISIBLE);
        chapterTransitionOverlay.bringToFront();
        if (webView != null) {
            webView.animate().cancel();
            webView.setAlpha(0f);
        }
        pendingChapterFade = true;
    }'''

new_prepare = '''    private void prepareChapterTransition(int direction) {
        if (webView == null || webView.getUrl() == null || chapterTransitionOverlay == null) return;
        pendingChapterDirection = direction < 0 ? -1 : 1;
        pendingChapterCurlDirection = 0;
        chapterTransitionLoadDeferred = false;

        // WebView.draw(Canvas) is a software render and can use a different internal
        // page scale from the hardware-composited frame visible on screen. That was
        // the source of the outgoing chapter suddenly shrinking before navigation.
        // PixelCopy copies the already-composited window pixels instead, so the old
        // chapter is frozen at the exact size the reader was looking at.
        if (Build.VERSION.SDK_INT >= 26 && webView.getWidth() > 0 && webView.getHeight() > 0) {
            final int width = webView.getWidth();
            final int height = webView.getHeight();
            final Bitmap shot;
            try {
                shot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError | RuntimeException e) {
                return;
            }

            int[] location = new int[2];
            webView.getLocationInWindow(location);
            android.graphics.Rect src = new android.graphics.Rect(
                    location[0], location[1], location[0] + width, location[1] + height);
            final int token = ++chapterTransitionCaptureToken;
            chapterTransitionCapturePending = true;

            try {
                android.view.PixelCopy.request(getWindow(), src, shot, result -> {
                    if (token != chapterTransitionCaptureToken || isFinishing()) {
                        if (!shot.isRecycled()) shot.recycle();
                        return;
                    }
                    chapterTransitionCapturePending = false;
                    if (result != android.view.PixelCopy.SUCCESS) {
                        // A rare compositor miss is preferable to reintroducing the
                        // wrong-scale software WebView snapshot. Use a stable reader
                        // background for that transition instead of a shrunken page.
                        shot.eraseColor(readerTheme == 2 ? Color.rgb(18, 18, 18) :
                                (readerTheme == 1 ? Color.rgb(244, 236, 216) : Color.WHITE));
                    }
                    installChapterTransitionSnapshot(shot);
                    if (chapterTransitionLoadDeferred) {
                        chapterTransitionLoadDeferred = false;
                        loadCurrentEpubChapter();
                    }
                }, new android.os.Handler(android.os.Looper.getMainLooper()));
                return;
            } catch (RuntimeException e) {
                chapterTransitionCapturePending = false;
                if (!shot.isRecycled()) shot.recycle();
            }
        }

        // Android 6/7 fallback. Modern devices never use this software path.
        Bitmap fallback = captureWebViewBitmap();
        if (fallback != null) installChapterTransitionSnapshot(fallback);
    }

    private void installChapterTransitionSnapshot(Bitmap shot) {
        if (shot == null || chapterTransitionOverlay == null) return;
        if (chapterTransitionBitmap != null && chapterTransitionBitmap != shot &&
                !chapterTransitionBitmap.isRecycled()) chapterTransitionBitmap.recycle();
        chapterTransitionBitmap = shot;
        chapterTransitionOverlay.animate().cancel();
        chapterTransitionOverlay.setImageBitmap(shot);
        chapterTransitionOverlay.setAlpha(1f);
        chapterTransitionOverlay.setTranslationX(0f);
        chapterTransitionOverlay.setScaleX(1f);
        chapterTransitionOverlay.setScaleY(1f);
        chapterTransitionOverlay.setVisibility(View.VISIBLE);
        chapterTransitionOverlay.bringToFront();
        webView.animate().cancel();
        webView.setScaleX(1f);
        webView.setScaleY(1f);
        webView.setTranslationX(0f);
        webView.setAlpha(0f);
        pendingChapterFade = true;
    }'''
if old_prepare not in text:
    raise SystemExit('prepareChapterTransition block missing')
text = text.replace(old_prepare, new_prepare, 1)

P.write_text(text, encoding='utf-8')

build = B.read_text(encoding='utf-8')
build = build.replace('versionCode 33', 'versionCode 34', 1)
build = build.replace("versionName '2.16.3-lab-v33'", "versionName '2.16.4-lab-v34'", 1)
if 'versionCode 34' not in build or "versionName '2.16.4-lab-v34'" not in build:
    raise SystemExit('v34 version bump failed')
B.write_text(build, encoding='utf-8')

print('v34 compositor-exact outgoing chapter freeze patch applied')
