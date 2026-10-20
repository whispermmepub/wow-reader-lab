package com.whisper.wowreader;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Disposable visual preview controller. EPUB rendering is delegated to the active reader engine. */
public final class ReaderPagePreview {
    public interface EpubRenderer { void prepare(WebView view, int generation); }

    private static final int MAX_CHAPTER_BYTES = PagePreviewPolicy.MAX_CHAPTER_BYTES;
    private static final int MAX_CACHE_ENTRIES = 4;
    private static final int PREVIEW_WIDTH_DP = 288, PREVIEW_HEIGHT_DP = 408;
    private static final long PDF_DEBOUNCE_MS = 35L;
    // Keep exactly one inactive EPUB DOM. It is the expensive pagination result;
    // the source/document cap and one-WebView bound keep warm reuse predictable.
    private static final int ANCHOR_GAP_DP = 8;

    private final Activity activity;
    private final FrameLayout root, panel;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
        Thread worker=new Thread(() -> {android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);task.run();},"wow-page-preview");
        worker.setDaemon(true);return worker;
    });
    private final Map<String, Bitmap> pdfCache = new LinkedHashMap<String, Bitmap>(8, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) { return size() > MAX_CACHE_ENTRIES; }
    };
    private final Map<String, String> epubCache = new LinkedHashMap<String, String>(4, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> e) { return size() > 2; }
    };
    private final ImageView pdfImage;
    private final TextView label;
    private final ProgressBar epubLoading;
    private volatile WebView epubWebView;
    private volatile File previewDocument;
    private final Set<File> stagedDocuments=new HashSet<>();
    private EpubRenderer epubRenderer;
    private File publicationRoot;
    private String epubCurrentKey = "";
    private String pendingEpubKey = "";
    private int epubTargetPermille;
    private int viewportWidth, viewportHeight, textZoom;
    private volatile long generation;
    private volatile long loadedGeneration;
    private String requestedEpubKey="";
    private String epubStyleKey="";
    private Runnable captureTask;
    private long dispatchDueUptime;
    private boolean panelUpdateScheduled;
    private final Runnable panelUpdateTask=() -> {panelUpdateScheduled=false;if(!this.destroyed){scaleWebView();updatePanelPosition();}};
    private final PdfPreviewRenderer pdfPreviewRenderer = new PdfPreviewRenderer();
    // Evicted images are GC-owned: an ImageView/render thread can still reference them.
    // Eight 400x560 ARGB thumbnails bound the retained pixel cache to at most 7 MiB.
    private final Map<String, Bitmap> renderedCache = new LinkedHashMap<String, Bitmap>(12,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Bitmap> e) { return size()>8; }
    };
    private boolean seekScheduled;
    private long seekSerial;
    private final Runnable seekTask = () -> { seekScheduled=false; seekLatestNow(); };
    private volatile boolean destroyed;
    private boolean engineReady;
    private View anchorView;
    private ScheduledFuture<?> pending;
    private final View.OnLayoutChangeListener boundsListener = (v,l,t,r,b,ol,ot,or,ob) -> updatePanelPosition();
    private final View.OnLayoutChangeListener panelListener = (v,l,t,r,b,ol,ot,or,ob) -> scaleWebView();

    public ReaderPagePreview(Activity activity, FrameLayout root) {
        if (activity == null || root == null) throw new IllegalArgumentException("activity/root required");
        this.activity = activity; this.root = root;
        float d = activity.getResources().getDisplayMetrics().density;
        panel = new FrameLayout(activity); panel.setBackgroundColor(Color.WHITE); panel.setElevation(8f * d); panel.setVisibility(View.GONE);
        pdfImage = new ImageView(activity); pdfImage.setScaleType(ImageView.ScaleType.FIT_CENTER); pdfImage.setBackgroundColor(Color.WHITE);
        FrameLayout.LayoutParams imageLp = new FrameLayout.LayoutParams(-1, -1, Gravity.TOP); imageLp.bottomMargin = dp(30); panel.addView(pdfImage, imageLp);
        label = new TextView(activity); label.setTextColor(Color.DKGRAY); label.setTextSize(11f); label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding((int)(7*d), 0, (int)(7*d), 0); label.setBackgroundColor(0xF2FFFFFF);
        panel.addView(label, new FrameLayout.LayoutParams(-1, dp(30), Gravity.BOTTOM));
        epubLoading=new ProgressBar(activity);epubLoading.setVisibility(View.GONE);panel.addView(epubLoading,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(dp(PREVIEW_WIDTH_DP), dp(PREVIEW_HEIGHT_DP), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        panelLp.bottomMargin = dp(58); root.addView(panel, panelLp); executor.setRemoveOnCancelPolicy(true);
        root.addOnLayoutChangeListener(boundsListener);
        panel.addOnLayoutChangeListener(panelListener);
        updatePanelPosition();
    }

    public void setAnchorView(View anchor) {
        if (destroyed) return;
        if (anchorView != null) anchorView.removeOnLayoutChangeListener(boundsListener);
        anchorView = anchor;
        if (anchorView != null) anchorView.addOnLayoutChangeListener(boundsListener);
        updatePanelPosition();
        root.post(this::updatePanelPosition);
    }

    /** Configures the same physical reader viewport; changing any argument invalidates the EPUB view/cache. */
    public void configureEpubRenderer(EpubRenderer renderer, int viewportWidth, int viewportHeight, int textZoom, File publicationRoot) {
        if (destroyed) return;
        boolean changed = this.epubRenderer != renderer || this.viewportWidth != viewportWidth || this.viewportHeight != viewportHeight
                || this.textZoom != textZoom || !sameFile(this.publicationRoot, publicationRoot);
        this.epubRenderer = renderer; this.viewportWidth = Math.max(1, viewportWidth); this.viewportHeight = Math.max(1, viewportHeight);
        this.textZoom = textZoom; this.publicationRoot = canonicalOrNull(publicationRoot);
        if (changed) { dispatchDueUptime=0;renderedCache.clear(); ++generation; cancelPending(); pendingEpubKey=""; epubCurrentKey = ""; engineReady = false; if (epubWebView != null) { panel.removeView(epubWebView); epubWebView.destroy(); epubWebView = null; } releaseDocument(previewDocument);previewDocument=null; }
    }

    public void setEpubStyleKey(String key) {
        if(destroyed)return;
        String next=key==null?"":key;
        if(!next.equals(epubStyleKey)){
            hide();
            discardWarmEpub();
            renderedCache.clear();
            epubStyleKey=next;
        }
    }

    public boolean ownsView(WebView view) { return !destroyed && view != null && view == epubWebView; }

    public void showPdf(final File pdf, final int pageZeroBased, final int pageCount, int backgroundColor) {
        if (destroyed || pdf == null || pageCount <= 0) return;
        final int page = PagePreviewPolicy.pdfPage(pageZeroBased, pageCount), request = (int) ++generation;
        cancelPending(); showPanel(backgroundColor);epubLoading.setVisibility(View.GONE); pdfImage.setVisibility(View.VISIBLE);
        label.setText(String.format(Locale.US, "Page %d of %d", page + 1, pageCount));
        final String key = pdf.getAbsolutePath()+":"+pdf.length()+":"+pdf.lastModified()+":"+backgroundColor+"#"+page;
        synchronized (pdfCache) { Bitmap cached = pdfCache.get(key); if (cached != null && !cached.isRecycled()) { pdfImage.setImageBitmap(cached);dispatchDueUptime=0; return; } }
        pdfImage.setImageDrawable(null);
        pending = executor.schedule(() -> {
            if (destroyed || request != (int)generation) return;
            Bitmap bitmap = null;
            try { bitmap = pdfPreviewRenderer.render(pdf,page,backgroundColor); }
            catch (Exception ignored) { }
            final Bitmap result = bitmap; main.post(() -> { if (destroyed || request != (int)generation) { if (result != null) result.recycle(); return; } pending = null;dispatchDueUptime=0; if (result != null) { synchronized (pdfCache) { pdfCache.put(key, result); } pdfImage.setImageBitmap(result); } });
        }, nextDispatchDelay(PDF_DEBOUNCE_MS), TimeUnit.MILLISECONDS);
    }

    public void showEpub(final File chapter, int chapterIndex, int chapterCount, int chapterPermille, String chapterTitle, int textColor, int backgroundColor) {
        if (destroyed || chapter == null) return;
        showPanel(backgroundColor);
        label.setText("Chapter preview · " + PagePreviewPolicy.percentLabel(chapterPermille) + (chapterTitle == null || chapterTitle.trim().isEmpty() ? "" : " · " + chapterTitle.trim()));
        epubTargetPermille = PagePreviewPolicy.clampPermille(chapterPermille);
        final String rootKey = publicationRoot == null ? "" : publicationRoot.getPath();
        final String key = chapter.getAbsolutePath()+":"+chapter.length()+":"+chapter.lastModified()+":"+textColor+":"+backgroundColor+":"+viewportWidth+":"+viewportHeight+":"+textZoom+":"+rootKey+":"+epubStyleKey;
        requestedEpubKey=key;
        Bitmap cached = renderedCache.get(key+"#"+epubTargetPermille);
        pdfImage.setImageBitmap(cached);
        pdfImage.setVisibility(cached==null?View.GONE:View.VISIBLE);
        if(cached!=null) {
            ++generation;cancelPending();dispatchDueUptime=0;pendingEpubKey="";epubLoading.setVisibility(View.GONE);
            if(epubWebView!=null)epubWebView.setVisibility(View.INVISIBLE);
            return; // A rendered cache hit must not paginate the chapter again.
        }
        if (epubWebView != null && key.equals(epubCurrentKey)) {
            // Returning to the active chapter must also cancel a different chapter's queued load.
            if (!pendingEpubKey.isEmpty()) { cancelPending(); pendingEpubKey=""; }
            dispatchDueUptime=0;
            if (!engineReady) {
                // The DOM is retained across hide; its page map remains valid. A
                // lightweight probe restores readiness without invoking prepare or
                // rebuilding pagination. The generation guard rejects stale bridges.
                final WebView warm=epubWebView;
                final long warmGeneration=++generation;
                warm.evaluateJavascript("(function(){var e=window.__wowPageEngine;return !!(e&&e.mode==='page'&&e.pageMap&&e.count>0);})()", value -> {
                    if (destroyed || warm != epubWebView || warmGeneration != generation || !key.equals(requestedEpubKey)) return;
                    if (!"true".equals(value)) {
                        discardWarmEpub();
                        showEpub(chapter, chapterIndex, chapterCount, chapterPermille, chapterTitle, textColor, backgroundColor);
                        return;
                    }
                    engineReady=true;
                    warm.setVisibility(View.VISIBLE);
                    epubLoading.setVisibility(View.GONE);
                    seekLatest();
                });
                epubWebView.setVisibility(View.INVISIBLE);
                epubLoading.setVisibility(View.VISIBLE);
            } else {
                epubWebView.setVisibility(View.VISIBLE);
                epubLoading.setVisibility(View.GONE);
                seekLatest();
            }
            return;
        }
        if(key.equals(pendingEpubKey))return;
        final long request=++generation;cancelPending();pendingEpubKey=key;epubLoading.setVisibility(cached==null?View.VISIBLE:View.GONE);
        if(epubWebView!=null)epubWebView.setVisibility(View.INVISIBLE);
        pending = executor.schedule(() -> {
            if(obsolete(request))return;
            String html; synchronized (epubCache) { html = epubCache.get(key); }
            if (html == null) { html = readBoundedAndSanitize(chapter, textColor, backgroundColor); if(obsolete(request))return; synchronized (epubCache) { epubCache.put(key, html); } }
            if(obsolete(request))return;
            final File document=html==null?null:createPreviewDocument(chapter,html);
            if(obsolete(request)){releaseDocument(document);return;}
            main.post(() -> {
                if(destroyed||request!=generation||!key.equals(pendingEpubKey)){releaseDocument(document);return;}
                pending=null;dispatchDueUptime=0;pendingEpubKey="";
                if(document==null){unavailable();return;}
                releaseDocument(previewDocument);previewDocument=document;
                ensureWebView(backgroundColor,chapter);epubCurrentKey=key;engineReady=false;
                epubWebView.stopLoading();
                epubWebView.setBackgroundColor(backgroundColor);
                bindDocument(chapter,document,request);
                epubWebView.loadUrl(Uri.fromFile(document).toString());
            });
        // Keep the first uncached request responsive. The WebView still waits for the
        // same complete chapter/page-map readiness callback; this is only coalescing.
        }, nextDispatchDelay(PagePreviewPolicy.FIRST_PREVIEW_DISPATCH_MS), TimeUnit.MILLISECONDS);
    }
    private long nextDispatchDelay(long interval) {
        long now=android.os.SystemClock.uptimeMillis();
        if(dispatchDueUptime==0)dispatchDueUptime=now+interval;
        return Math.max(0L,dispatchDueUptime-now);
    }
    private boolean obsolete(long request) { return destroyed||request!=generation||Thread.currentThread().isInterrupted(); }

    private void ensureWebView(int backgroundColor, File chapter) {
        if (epubWebView != null) return;
        epubWebView = new WebView(activity); WebSettings s = epubWebView.getSettings(); s.setJavaScriptEnabled(epubRenderer != null); s.setTextZoom(textZoom); s.setAllowFileAccess(true); s.setDefaultTextEncodingName("UTF-8");s.setUseWideViewPort(false);s.setLoadWithOverviewMode(false);s.setAllowFileAccessFromFileURLs(true); s.setAllowUniversalAccessFromFileURLs(false); s.setAllowContentAccess(false); s.setDomStorageEnabled(false); s.setSupportZoom(false); s.setBuiltInZoomControls(false);
        epubWebView.setOnTouchListener((v,event)->true);
        epubWebView.setBackgroundColor(backgroundColor);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(viewportWidth, viewportHeight, Gravity.TOP); panel.addView(epubWebView, 0, lp); scaleWebView();
    }
    private void bindDocument(File chapter,File document,long request) {
        loadedGeneration=request;
        epubWebView.addJavascriptInterface(new PreviewBridge(epubWebView), "WoW");
        final String expectedUrl=Uri.fromFile(document).toString();
        final File allowedRoot = publicationRoot != null ? publicationRoot : canonicalOrNull(chapter.getParentFile());
        epubWebView.setWebViewClient(new WebViewClient() {
            private boolean prepared;
            @Override public void onPageFinished(WebView view, String url) {
                if (!ownsView(view)||request!=loadedGeneration||!expectedUrl.equals(url)||prepared) return;
                prepared=true;
                if (epubRenderer != null) epubRenderer.prepare(view, -(int)request);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return true; }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String u) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) { return resource(v, r == null ? null : r.getUrl(), allowedRoot, document, request); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, String u) { return resource(v, u == null ? null : Uri.parse(u), allowedRoot, document, request); }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, android.webkit.WebResourceError e) { if (ownsView(v)&&request==loadedGeneration&&(r == null || r.isForMainFrame()))main.post(()->{if(ownsView(v)&&request==loadedGeneration)unavailable();}); }
        });
    }

    private WebResourceResponse resource(WebView v, Uri uri, File allowed, File document, long request) {
        try {
            if(!ownsView(v)||request!=loadedGeneration||uri==null||allowed==null)throw new IOException();
            String source=uri.toString().toLowerCase(Locale.ROOT);
            if(source.startsWith("data:image/")||source.startsWith("data:font/")||source.startsWith("data:application/font")||source.startsWith("data:application/x-font"))return null;
            if(!"file".equalsIgnoreCase(uri.getScheme()))throw new IOException();
            File f = new File(uri.getPath()).getCanonicalFile();
            boolean bundled=uri.getPath().startsWith("/android_asset/fonts/")&&!uri.getPath().contains("..");
            if (!bundled&&!inside(f, allowed)&&!sameFile(f,document)) throw new IOException();
            if(f.getName().toLowerCase(Locale.ROOT).endsWith(".js"))throw new IOException();
            // null delegates an allowed request to WebView's normal file loader.
            return null;
        } catch (Exception e) { return new WebResourceResponse("text/plain", "UTF-8", null); }
    }

    private void scaleWebView() {
        if(epubWebView==null)return;
        // LayoutParams already contain the new target during a resize, before the next layout pass.
        int w=Math.max(1,panel.getLayoutParams().width),h=Math.max(1,panel.getLayoutParams().height-dp(30));
        float scale=Math.min(1f,Math.min(w/(float)Math.max(1,viewportWidth),h/(float)Math.max(1,viewportHeight)));
        epubWebView.setPivotX(0);epubWebView.setPivotY(0);epubWebView.setScaleX(scale);epubWebView.setScaleY(scale);
        epubWebView.setTranslationX(Math.max(0,(w-viewportWidth*scale)/2f));epubWebView.setTranslationY(Math.max(0,(h-viewportHeight*scale)/2f));
    }
    private void seekLatest() {
        if(!ownsView(epubWebView)||!engineReady||seekScheduled)return;
        if(captureTask!=null){main.removeCallbacks(captureTask);captureTask=null;}
        ++seekSerial;seekScheduled=true;main.postDelayed(seekTask,32L);
    }
    private void seekLatestNow() {
        if(!ownsView(epubWebView)||!engineReady)return;
        int p=epubTargetPermille;
        final WebView owner=epubWebView; final long request=generation, serial=++seekSerial;
        final String cacheKey=epubCurrentKey+"#"+p;
        epubWebView.evaluateJavascript("(function(){var e=window.__wowPageEngine;if(e&&e.mode==='page'&&e.pageMap&&e.count>0){e.page=Math.max(0,Math.min(e.count-1,Math.round((e.count-1)*"+p+"/1000)));e.apply(false);}else{var m=Math.max(0,document.documentElement.scrollHeight-window.innerHeight);window.scrollTo(0,m*"+p+"/1000);}})();",value -> {
            if(!ownsView(owner)||request!=generation||serial!=seekSerial)return;
            captureTask=() -> owner.postVisualStateCallback(serial,new WebView.VisualStateCallback(){
                @Override public void onComplete(long id) {
                    if(!ownsView(owner)||request!=generation||serial!=seekSerial||p!=epubTargetPermille||panel.getVisibility()!=View.VISIBLE)return;
                    owner.setVisibility(View.VISIBLE);pdfImage.setVisibility(View.GONE);
                    try {
                        float scale=Math.min(400f/viewportWidth,560f/viewportHeight);
                        Bitmap image=Bitmap.createBitmap(Math.max(1,Math.round(viewportWidth*scale)),Math.max(1,Math.round(viewportHeight*scale)),Bitmap.Config.ARGB_8888);
                        Canvas canvas=new Canvas(image);canvas.scale(scale,scale);owner.draw(canvas);
                        renderedCache.put(cacheKey,image);
                    } catch (RuntimeException ignored) { }
                }
            });
            // VisualStateCallback already waits for the committed physical page. A
            // short frame grace period is enough; 120 ms made every uncached drag
            // feel like an excerpt loader without improving pagination accuracy.
            main.postDelayed(captureTask,32L);
        });
    }
    private final class PreviewBridge {
        private final WebView owner;
        private final long request;
        PreviewBridge(WebView owner){this.owner=owner;this.request=loadedGeneration;}
        private boolean valid(int gen){return ownsView(owner)&&request==loadedGeneration&&gen==-(int)request;}
        private void ready(int gen){main.post(()->{if(valid(gen)){engineReady=true;if(pendingEpubKey.isEmpty()&&epubCurrentKey.equals(requestedEpubKey)&&pdfImage.getVisibility()!=View.VISIBLE){epubLoading.setVisibility(View.GONE);owner.setVisibility(View.VISIBLE);seekLatest();}}});}
        @JavascriptInterface public void onPage(int a,int b,int c,int d) { }
        @JavascriptInterface public void onScroll(int a,int b) { }
        @JavascriptInterface public void onPageReady(int gen,int page,int count,int p) { ready(gen); }
        @JavascriptInterface public void onScrollReady(int gen) { ready(gen); }
        @JavascriptInterface public void pageEngineFailed(int gen,String reason) {main.post(()->{if(valid(gen))unavailable();});}
        @JavascriptInterface public void onEmptyChapter(int gen) {main.post(()->{if(valid(gen)){epubLoading.setVisibility(View.GONE);label.setText("Empty chapter");}});}
    }

    /** Local documents avoid Android data-URL parsing/length failures. Files stay
     * private, preserve the chapter resource base, and are deleted on replacement. */
    private File createPreviewDocument(File chapter,String html){
        File result=null;
        try{
            if (html == null || !PagePreviewPolicy.safeSanitizedLength(html.length())) return null;
            String base=android.text.TextUtils.htmlEncode(Uri.fromFile(chapter.getParentFile()).toString()+"/");
            String tag="<meta charset=\"utf-8\"/><base href=\""+base+"\"/>";
            java.util.regex.Matcher head=java.util.regex.Pattern.compile("(?is)<head\\b[^>]*>").matcher(html);
            if(head.find())html=html.substring(0,head.end())+tag+html.substring(head.end());
            else{
                java.util.regex.Matcher root=java.util.regex.Pattern.compile("(?is)<html\\b[^>]*>").matcher(html);
                if(root.find())html=html.substring(0,root.end())+"<head>"+tag+"</head>"+html.substring(root.end());
                else html="<html><head>"+tag+"</head><body>"+html+"</body></html>";
            }
            result=File.createTempFile("reader-preview-",".html",activity.getCacheDir());
            synchronized(stagedDocuments){if(destroyed){result.delete();return null;}stagedDocuments.add(result);}
            byte[] encoded=html.getBytes(StandardCharsets.UTF_8);
            // The source cap and a UTF-16 cap are intentionally independent. The latter
            // prevents a pathological sanitizer expansion from becoming an unbounded
            // staged WebView document.
            if (!PagePreviewPolicy.safeChapterByteLength(encoded.length)) { releaseDocument(result); return null; }
            try(FileOutputStream out=new FileOutputStream(result)){out.write(encoded);}
            return result;
        }catch(IOException error){releaseDocument(result);return null;}
    }
    private void releaseDocument(File file){if(file==null)return;file.delete();synchronized(stagedDocuments){stagedDocuments.remove(file);}}

    private String readBoundedAndSanitize(File file, int textColor, int backgroundColor) {
        if (file == null || !file.isFile() || !PagePreviewPolicy.safeChapterByteLength(file.length())) return null;
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[32 * 1024]; int total=0,n; while ((n=in.read(b)) != -1) { if(destroyed||Thread.currentThread().isInterrupted())return null; if (n > MAX_CHAPTER_BYTES-total) return null; total += n; out.write(b,0,n); }
            String body = new String(out.toByteArray(), StandardCharsets.UTF_8);
            if (!PagePreviewPolicy.safeSanitizedLength(body.length())) return null;
            body = body.replaceAll("(?is)<script\\b[^>]*>.*?</script\\s*>", "")
                    .replaceAll("(?is)<script\\b[^>]*/>", "")
                    .replaceAll("(?is)<script\\b[^>]*>.*$", "")
                    .replaceAll("(?is)<(iframe|object|form|embed|base)\\b[^>]*>.*?</\\1\\s*>", "")
                    .replaceAll("(?is)<(iframe|object|form|embed|base)\\b[^>]*/?>", "")
                    .replaceAll("(?is)<meta\\b[^>]*http-equiv\\s*=\\s*['\"]?refresh[^>]*>", "")
                    .replaceAll("(?i)\\s+on[a-z]+\\s*=\\s*(['\"]).*?\\1", "")
                    .replaceAll("(?is)\\s+on[a-z]+\\s*=\\s*[^\\s\\x22\\x27<>`]+", "")
                    .replaceAll("(?i)(javascript\\s*:|vbscript\\s*:)", "about:blank");
            if (!PagePreviewPolicy.safeSanitizedLength(body.length())) return null;
            if (!body.toLowerCase(Locale.ROOT).contains("<html")) body = "<article>" + body + "</article>";
            if (!PagePreviewPolicy.safeSanitizedLength(body.length())) return null;
            return body;
        } catch (IOException e) { return null; }
    }

    private void discardWarmEpub() {
        if (epubWebView != null) {
            panel.removeView(epubWebView);
            epubWebView.destroy();
            epubWebView=null;
        }
        epubCurrentKey="";
        pendingEpubKey="";
        engineReady=false;
        loadedGeneration=0;
        releaseDocument(previewDocument);
        previewDocument=null;
    }
    private void unavailable() { label.setText("Chapter preview unavailable"); epubLoading.setVisibility(View.GONE); discardWarmEpub(); }
    private void showPanel(int color) {
        panel.setBackgroundColor(color);
        if(panel.getVisibility()!=View.VISIBLE){panel.setVisibility(View.VISIBLE);panel.setAlpha(1f);panel.bringToFront();}
        if(!panelUpdateScheduled){panelUpdateScheduled=true;main.post(panelUpdateTask);}
    }
    public void hide() {
        if (destroyed) return;
        dispatchDueUptime=0;
        main.removeCallbacks(panelUpdateTask);
        panelUpdateScheduled=false;
        ++generation;
        cancelPending();
        // Invalidate callbacks from the old bridge, but retain one fully prepared
        // DOM/document. Recreating the WebView would throw away the physical page
        // map and make the next uncached drag pay pagination again.
        if (!engineReady) discardWarmEpub();
        loadedGeneration=0;
        panel.setVisibility(View.GONE);
        epubLoading.setVisibility(View.GONE);
        if (epubWebView != null) epubWebView.setVisibility(View.INVISIBLE);
        // Keep epubCurrentKey so the next show can identify this warm DOM.
        pendingEpubKey="";
        engineReady=false;
        pdfImage.setImageDrawable(null);
    }
    public void destroy() {
        if(destroyed)return;
        destroyed=true;++generation;cancelPending();executor.execute(pdfPreviewRenderer::close);executor.shutdown();main.removeCallbacksAndMessages(null);
        root.removeOnLayoutChangeListener(boundsListener);
        panel.removeOnLayoutChangeListener(panelListener);
        if(anchorView!=null)anchorView.removeOnLayoutChangeListener(boundsListener);
        if(epubWebView!=null){epubWebView.destroy();epubWebView=null;}
        root.removeView(panel);
        synchronized(stagedDocuments){for(File file:stagedDocuments)file.delete();stagedDocuments.clear();}
        previewDocument=null;
        pdfImage.setImageDrawable(null);
        synchronized(pdfCache){for(Bitmap bitmap:pdfCache.values())bitmap.recycle();pdfCache.clear();}
        synchronized(epubCache){epubCache.clear();}
        renderedCache.clear();
    }
    private void cancelPending(){if(captureTask!=null){main.removeCallbacks(captureTask);captureTask=null;}++seekSerial;main.removeCallbacks(seekTask);seekScheduled=false;if(pending!=null){pending.cancel(true);pending=null;}}
    private int dp(int v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
    private void updatePanelPosition(){
        if(destroyed||root.getWidth()<=0||root.getHeight()<=0)return;
        int safeLeft=root.getPaddingLeft(),safeTop=root.getPaddingTop();
        int safeRight=root.getPaddingRight(),safeBottom=root.getPaddingBottom();
        androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(root);
        if(insets!=null){
            androidx.core.graphics.Insets bars=insets.getInsetsIgnoringVisibility(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()|androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            safeLeft=Math.max(safeLeft,bars.left);safeTop=Math.max(safeTop,bars.top);
            safeRight=Math.max(safeRight,bars.right);safeBottom=Math.max(safeBottom,bars.bottom);
        }
        int bottom=safeBottom+dp(58);
        if(anchorView!=null&&anchorView.getHeight()>0){
            int[] a=new int[2],r=new int[2];root.getLocationOnScreen(r);anchorView.getLocationOnScreen(a);
            int anchorTop=a[1]-r[1];
            // A detached/off-screen seek bar can occur during rotation or inset relayout.
            // Do not move the entire panel above the visible root in that transient state.
            if(anchorTop>safeTop+dp(ANCHOR_GAP_DP)+dp(12)&&anchorTop<root.getHeight()-safeBottom)
                bottom=Math.max(safeBottom,PagePreviewPolicy.bottomMarginAboveAnchor(root.getHeight(),anchorTop,dp(ANCHOR_GAP_DP)));
        }
        int availableWidth=Math.max(0,root.getWidth()-safeLeft-safeRight-dp(24));
        int availableHeight=Math.max(0,Math.min(root.getHeight()-bottom-safeTop-dp(12),
                (int)((root.getHeight()-safeTop-safeBottom)*.62f)));
        int[] size=PagePreviewPolicy.fitPanel(availableWidth,availableHeight,dp(PREVIEW_WIDTH_DP),dp(PREVIEW_HEIGHT_DP));
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)panel.getLayoutParams();
        int marginBottom=bottom-root.getPaddingBottom();
        int marginLeft=safeLeft-root.getPaddingLeft()+dp(12),marginRight=safeRight-root.getPaddingRight()+dp(12);
        if(lp.width!=size[0]||lp.height!=size[1]||lp.bottomMargin!=marginBottom||lp.leftMargin!=marginLeft||lp.rightMargin!=marginRight){
            lp.width=size[0];lp.height=size[1];lp.bottomMargin=marginBottom;lp.leftMargin=marginLeft;lp.rightMargin=marginRight;
            panel.setLayoutParams(lp);
        }
        scaleWebView();
    }
    private static File canonicalOrNull(File f){try{return f==null?null:f.getCanonicalFile();}catch(IOException e){return null;}}
    private static boolean sameFile(File a,File b){File c=canonicalOrNull(b);return a==null?c==null:c!=null&&a.equals(c);}
    private static boolean inside(File f,File root){return f.equals(root)||f.getPath().startsWith(root.getPath()+File.separator);}
}
