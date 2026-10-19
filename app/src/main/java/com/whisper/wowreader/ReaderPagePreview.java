package com.whisper.wowreader;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
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
    public interface EpubRenderer { void prepare(WebView view); }

    private static final int MAX_CHAPTER_BYTES = PagePreviewPolicy.MAX_CHAPTER_BYTES;
    private static final int MAX_CACHE_ENTRIES = 4;
    private static final int PREVIEW_WIDTH_DP = 264, PREVIEW_HEIGHT_DP = 374;
    private static final long PDF_DEBOUNCE_MS = 35L;
    private static final int ANCHOR_GAP_DP = 8;

    private final Activity activity;
    private final FrameLayout root, panel;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
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
    private long generation;
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
        if (changed) { ++generation; cancelPending(); pendingEpubKey=""; epubCurrentKey = ""; engineReady = false; if (epubWebView != null) { panel.removeView(epubWebView); epubWebView.destroy(); epubWebView = null; } releaseDocument(previewDocument);previewDocument=null; }
    }

    public boolean ownsView(WebView view) { return !destroyed && view != null && view == epubWebView; }

    public void showPdf(final File pdf, final int pageZeroBased, final int pageCount, int backgroundColor) {
        if (destroyed || pdf == null || pageCount <= 0) return;
        final int page = PagePreviewPolicy.pdfPage(pageZeroBased, pageCount), request = (int) ++generation;
        cancelPending(); showPanel(backgroundColor);epubLoading.setVisibility(View.GONE); pdfImage.setVisibility(View.VISIBLE);
        label.setText(String.format(Locale.US, "Page %d of %d", page + 1, pageCount));
        final String key = pdf.getAbsolutePath() + "#" + page;
        synchronized (pdfCache) { Bitmap cached = pdfCache.get(key); if (cached != null && !cached.isRecycled()) { pdfImage.setImageBitmap(cached); return; } }
        pending = executor.schedule(() -> {
            Bitmap bitmap = null;
            try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)) {
                PdfRenderer r = new PdfRenderer(fd); if (page < r.getPageCount()) { PdfRenderer.Page p = r.openPage(page); int w = Math.max(1, Math.min(400, p.getWidth())), h = Math.max(1, Math.min(560, p.getHeight())); float s = Math.min(400f/w, 560f/h); bitmap = Bitmap.createBitmap(Math.max(1, Math.round(w*s)), Math.max(1, Math.round(h*s)), Bitmap.Config.ARGB_8888); bitmap.eraseColor(backgroundColor); p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); p.close(); } r.close();
            } catch (Exception ignored) { }
            final Bitmap result = bitmap; main.post(() -> { if (destroyed || request != (int)generation) { if (result != null) result.recycle(); return; } pending = null; if (result != null) { synchronized (pdfCache) { pdfCache.put(key, result); } pdfImage.setImageBitmap(result); } });
        }, PDF_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    public void showEpub(final File chapter, int chapterIndex, int chapterCount, int chapterPermille, String chapterTitle, int textColor, int backgroundColor) {
        if (destroyed || chapter == null) return;
        showPanel(backgroundColor); pdfImage.setVisibility(View.GONE);
        label.setText("Chapter preview · " + PagePreviewPolicy.percentLabel(chapterPermille) + (chapterTitle == null || chapterTitle.trim().isEmpty() ? "" : " · " + chapterTitle.trim()));
        epubTargetPermille = PagePreviewPolicy.clampPermille(chapterPermille);
        final String rootKey = publicationRoot == null ? "" : publicationRoot.getPath();
        final String key = chapter.getAbsolutePath()+":"+chapter.length()+":"+chapter.lastModified()+":"+textColor+":"+backgroundColor+":"+viewportWidth+":"+viewportHeight+":"+textZoom+":"+rootKey;
        if (epubWebView != null && key.equals(epubCurrentKey)) { seekLatest(); return; }
        if(key.equals(pendingEpubKey))return;
        final long request=++generation;cancelPending();pendingEpubKey=key;epubLoading.setVisibility(View.VISIBLE);
        pending = executor.schedule(() -> {
            String html; synchronized (epubCache) { html = epubCache.get(key); }
            if (html == null) { html = readBoundedAndSanitize(chapter, textColor, backgroundColor); synchronized (epubCache) { epubCache.put(key, html); } }
            final File document=html==null?null:createPreviewDocument(chapter,html);
            main.post(() -> {
                if(destroyed||request!=generation){releaseDocument(document);return;}
                pending=null;pendingEpubKey="";
                if(document==null){unavailable();return;}
                if(epubWebView!=null){panel.removeView(epubWebView);epubWebView.destroy();epubWebView=null;}
                releaseDocument(previewDocument);previewDocument=document;
                ensureWebView(backgroundColor,chapter);epubCurrentKey=key;engineReady=false;
                epubWebView.loadUrl(Uri.fromFile(document).toString());
            });
        }, 70L, TimeUnit.MILLISECONDS);
    }

    private void ensureWebView(int backgroundColor, File chapter) {
        if (epubWebView != null) return;
        epubWebView = new WebView(activity); WebSettings s = epubWebView.getSettings(); s.setJavaScriptEnabled(epubRenderer != null); s.setTextZoom(textZoom); s.setAllowFileAccess(true); s.setDefaultTextEncodingName("UTF-8");s.setUseWideViewPort(false);s.setLoadWithOverviewMode(false);s.setAllowFileAccessFromFileURLs(true); s.setAllowUniversalAccessFromFileURLs(false); s.setAllowContentAccess(false); s.setDomStorageEnabled(false); s.setSupportZoom(false); s.setBuiltInZoomControls(false);
        epubWebView.setOnTouchListener((v,event)->true);
        epubWebView.setBackgroundColor(backgroundColor); epubWebView.addJavascriptInterface(new PreviewBridge(epubWebView), "WoW");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(viewportWidth, viewportHeight, Gravity.TOP); panel.addView(epubWebView, 0, lp); scaleWebView();
        final File allowedRoot = publicationRoot != null ? publicationRoot : canonicalOrNull(chapter.getParentFile());
        epubWebView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) { if (!ownsView(view)) return; if (epubRenderer != null) epubRenderer.prepare(view); }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return true; }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String u) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) { return resource(v, r == null ? null : r.getUrl(), allowedRoot); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, String u) { return resource(v, u == null ? null : Uri.parse(u), allowedRoot); }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, android.webkit.WebResourceError e) { if (ownsView(v)&&(r == null || r.isForMainFrame()))main.post(()->{if(ownsView(v))unavailable();}); }
        });
    }

    private WebResourceResponse resource(WebView v, Uri uri, File allowed) {
        try {
            if(!ownsView(v)||uri==null||allowed==null)throw new IOException();
            String source=uri.toString().toLowerCase(Locale.ROOT);
            if(source.startsWith("data:image/")||source.startsWith("data:font/")||source.startsWith("data:application/font")||source.startsWith("data:application/x-font"))return null;
            if(!"file".equalsIgnoreCase(uri.getScheme()))throw new IOException();
            File f = new File(uri.getPath()).getCanonicalFile();
            boolean bundled=uri.getPath().startsWith("/android_asset/fonts/")&&!uri.getPath().contains("..");
            if (!bundled&&!inside(f, allowed)&&!sameFile(f,previewDocument)) throw new IOException();
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
        if(!ownsView(epubWebView)||!engineReady)return;
        int p=epubTargetPermille;
        epubWebView.evaluateJavascript("(function(){var e=window.__wowPageEngine;if(e&&e.mode==='page'&&e.pageMap&&e.count>0){e.page=Math.max(0,Math.min(e.count-1,Math.round((e.count-1)*"+p+"/1000)));e.apply(false);}else{var m=Math.max(0,document.documentElement.scrollHeight-window.innerHeight);window.scrollTo(0,m*"+p+"/1000);}})();",null);
    }
    private final class PreviewBridge {
        private final WebView owner;
        PreviewBridge(WebView owner){this.owner=owner;}
        private void ready(){main.post(()->{if(ownsView(owner)){engineReady=true;epubLoading.setVisibility(View.GONE);seekLatest();}});}
        @JavascriptInterface public void onPage(int a,int b,int c,int d) { }
        @JavascriptInterface public void onScroll(int a,int b) { }
        @JavascriptInterface public void onPageReady(int gen,int page,int count,int p) { ready(); }
        @JavascriptInterface public void onScrollReady(int gen) { ready(); }
        @JavascriptInterface public void pageEngineFailed(int gen,String reason) {main.post(()->{if(ownsView(owner))unavailable();});}
        @JavascriptInterface public void onEmptyChapter(int gen) {main.post(()->{if(ownsView(owner)){epubLoading.setVisibility(View.GONE);label.setText("Empty chapter");}});}
    }

    /** Local documents avoid Android data-URL parsing/length failures. Files stay
     * private, preserve the chapter resource base, and are deleted on replacement. */
    private File createPreviewDocument(File chapter,String html){
        File result=null;
        try{
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
            try(FileOutputStream out=new FileOutputStream(result)){out.write(html.getBytes(StandardCharsets.UTF_8));}
            return result;
        }catch(IOException error){releaseDocument(result);return null;}
    }
    private void releaseDocument(File file){if(file==null)return;file.delete();synchronized(stagedDocuments){stagedDocuments.remove(file);}}

    private String readBoundedAndSanitize(File file, int textColor, int backgroundColor) {
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int total=0,n; while ((n=in.read(b)) != -1) { total += n; if (total > MAX_CHAPTER_BYTES) return null; out.write(b,0,n); }
            String body = new String(out.toByteArray(), StandardCharsets.UTF_8);
            body = body.replaceAll("(?is)<script\\b[^>]*>.*?</script\\s*>", "")
                    .replaceAll("(?is)<script\\b[^>]*/>", "")
                    .replaceAll("(?is)<script\\b[^>]*>.*$", "")
                    .replaceAll("(?is)<(iframe|object|form|embed|base)\\b[^>]*>.*?</\\1\\s*>", "")
                    .replaceAll("(?is)<(iframe|object|form|embed|base)\\b[^>]*/?>", "")
                    .replaceAll("(?is)<meta\\b[^>]*http-equiv\\s*=\\s*['\"]?refresh[^>]*>", "")
                    .replaceAll("(?i)\\s+on[a-z]+\\s*=\\s*(['\"]).*?\\1", "")
                    .replaceAll("(?is)\\s+on[a-z]+\\s*=\\s*[^\\s\\x22\\x27<>`]+", "")
                    .replaceAll("(?i)(javascript\\s*:|vbscript\\s*:)", "about:blank");
            if (!body.toLowerCase(Locale.ROOT).contains("<html")) body = "<article>" + body + "</article>";
            return body;
        } catch (IOException e) { return null; }
    }

    private void unavailable() { label.setText("Chapter preview unavailable"); engineReady=false;epubLoading.setVisibility(View.GONE);if(epubWebView!=null){panel.removeView(epubWebView);epubWebView.destroy();epubWebView=null;}epubCurrentKey="";pendingEpubKey="";releaseDocument(previewDocument);previewDocument=null; }
    private void showPanel(int color) { panel.setBackgroundColor(color); panel.setVisibility(View.VISIBLE); panel.setAlpha(1f); panel.bringToFront(); root.post(() -> { scaleWebView(); updatePanelPosition(); }); }
    public void hide() { if (destroyed) return; ++generation; cancelPending(); panel.setVisibility(View.GONE);epubLoading.setVisibility(View.GONE); if (epubWebView != null) { panel.removeView(epubWebView); epubWebView.destroy(); epubWebView=null; } epubCurrentKey="";pendingEpubKey=""; engineReady=false;releaseDocument(previewDocument);previewDocument=null; pdfImage.setImageDrawable(null); }
    public void destroy() {
        if(destroyed)return;
        destroyed=true;++generation;cancelPending();executor.shutdownNow();main.removeCallbacksAndMessages(null);
        root.removeOnLayoutChangeListener(boundsListener);
        panel.removeOnLayoutChangeListener(panelListener);
        if(anchorView!=null)anchorView.removeOnLayoutChangeListener(boundsListener);
        if(epubWebView!=null){epubWebView.destroy();epubWebView=null;}
        root.removeView(panel);
        synchronized(stagedDocuments){for(File file:stagedDocuments)file.delete();stagedDocuments.clear();}
        previewDocument=null;
        synchronized(pdfCache){for(Bitmap bitmap:pdfCache.values())bitmap.recycle();pdfCache.clear();}
        synchronized(epubCache){epubCache.clear();}
    }
    private void cancelPending(){if(pending!=null){pending.cancel(false);pending=null;}}
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
