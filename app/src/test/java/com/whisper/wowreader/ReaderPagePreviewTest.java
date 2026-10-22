package com.whisper.wowreader;

import android.app.Activity;
import android.net.Uri;
import android.os.Looper;
import android.webkit.WebView;
import android.widget.FrameLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={24,35,36},manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ReaderPagePreviewTest {
    private Activity activity;
    private FrameLayout root;
    private ReaderPagePreview preview;
    private File publication,chapter;
    private final ReaderPagePreview.EpubRenderer renderer=(view,generation)->{};
    @Before public void setup()throws Exception{
        activity=Robolectric.buildActivity(Activity.class).setup().get();
        root=new FrameLayout(activity);root.layout(0,0,400,800);
        preview=new ReaderPagePreview(activity,root);
        publication=Files.createTempDirectory("EPUB preview #% စာအုပ်").toFile();
        File text=new File(publication,"Text chapter #%");assertTrue(text.mkdirs());
        chapter=new File(text,"chapter #%.xhtml");
        Files.write(chapter.toPath(),"<html><head><link rel='stylesheet' href='../Styles/author.css'/></head><body><p>Author text</p></body></html>".getBytes(StandardCharsets.UTF_8));
        preview.configureEpubRenderer(renderer,800,1600,160,publication);
    }
    @After public void cleanup(){preview.destroy();activity.finish();}
    private Object get(String name)throws Exception{Field f=ReaderPagePreview.class.getDeclaredField(name);f.setAccessible(true);return f.get(preview);}
    private Object call(String name,Class<?>[]types,Object...args)throws Exception{Method m=ReaderPagePreview.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(preview,args);}
    private void show(int p){preview.showEpub(chapter,0,1,p,"Title",0xff000000,0xffffffff);}
    private WebView awaitView()throws Exception{
        long until=System.nanoTime()+3_000_000_000L;
        while(System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();WebView v=(WebView)get("epubWebView");if(v!=null)return v;Thread.sleep(15);}
        throw new AssertionError("preview loader did not create a WebView");
    }
    private Object bridge(WebView owner)throws Exception{
        for(Class<?> c:ReaderPagePreview.class.getDeclaredClasses())if(c.getSimpleName().equals("PreviewBridge")){
            Constructor<?> constructor=c.getDeclaredConstructor(ReaderPagePreview.class,WebView.class);constructor.setAccessible(true);return constructor.newInstance(preview,owner);
        }
        throw new AssertionError("preview bridge missing");
    }
    private void ready(Object bridge)throws Exception{
        Method m=bridge.getClass().getDeclaredMethod("onPageReady",int.class,int.class,int.class,int.class);m.setAccessible(true);m.invoke(bridge,-((Long)get("loadedGeneration")).intValue(),1,10,0);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(40));
    }
    @Test public void sanitizesActiveContentButKeepsAuthorCssAndImages()throws Exception{
        Files.write(chapter.toPath(),("<html><head><style>.author{color:red}</style><meta http-equiv='refresh' content='0;url=https://evil'></head>"+
                "<body onclick=evil()><img onerror='evil()' src='../Styles/p.png'/><script>evil()</script><script src='bad.js'/><iframe src='x'></iframe><p style='text-align:center'>Authored center</p></body></html>").getBytes(StandardCharsets.UTF_8));
        String html=(String)call("readBoundedAndSanitize",new Class[]{File.class,int.class,int.class},chapter,0,0);
        assertTrue(html.contains(".author{color:red}"));assertTrue(html.contains("../Styles/p.png"));assertTrue(html.contains("text-align:center"));
        assertFalse(html.contains("evil()"));assertFalse(html.contains("<script"));assertFalse(html.toLowerCase().contains("http-equiv='refresh'"));
    }
    @Test public void canonicalBoundaryAllowsParentStylesButRejectsSiblingAndTraversal()throws Exception{
        Method m=ReaderPagePreview.class.getDeclaredMethod("inside",File.class,File.class);m.setAccessible(true);
        assertTrue((Boolean)m.invoke(null,new File(publication,"Styles/a.css").getCanonicalFile(),publication.getCanonicalFile()));
        assertFalse((Boolean)m.invoke(null,new File(publication.getParentFile(),publication.getName()+"-outside/a.css").getCanonicalFile(),publication.getCanonicalFile()));
        assertFalse((Boolean)m.invoke(null,new File(publication,"../outside/a.css").getCanonicalFile(),publication.getCanonicalFile()));
    }
    @Test public void escapedChapterDirectoryIsBaseAndFullViewportIsScaledToFit()throws Exception{
        show(500);WebView v=awaitView();
        String loaded=Shadows.shadowOf(v).getLastLoadedUrl();
        assertTrue(loaded.startsWith("file:"));assertFalse(loaded.startsWith("data:"));
        File document=new File(Uri.parse(loaded).getPath());assertTrue(document.isFile());
        String html=new String(Files.readAllBytes(document.toPath()),StandardCharsets.UTF_8);
        String base=Uri.fromFile(chapter.getParentFile()).toString()+"/";
        assertTrue(html.contains("<base href=\""+base+"\"/>"));
        assertTrue(base.contains("%23")&&base.contains("%25")&&base.contains("%20"));
        assertEquals(800,v.getLayoutParams().width);assertEquals(1600,v.getLayoutParams().height);
        assertEquals(160,v.getSettings().getTextZoom());assertTrue(v.getSettings().getJavaScriptEnabled());
        assertTrue(v.getScaleX()>0&&v.getScaleX()<1);assertEquals(v.getScaleX(),v.getScaleY(),0f);
        FrameLayout panel=(FrameLayout)get("panel");assertTrue(1600*v.getScaleY()<=panel.getLayoutParams().height);
    }
    @Test public void continuousSameChapterDragDoesNotCancelLoadingAndSeeksOnlyAfterReady()throws Exception{
        show(100);long token=(Long)get("generation");show(300);show(800);assertEquals(token,(long)(Long)get("generation"));
        WebView v=awaitView();assertFalse((Boolean)get("engineReady"));assertNull(Shadows.shadowOf(v).getLastEvaluatedJavascript());
        ready(bridge(v));String script=Shadows.shadowOf(v).getLastEvaluatedJavascript();
        assertTrue((Boolean)get("engineReady"));assertTrue(script.contains("*800/1000"));assertTrue(script.contains("e.apply(false)"));assertFalse(script.contains("e.report("));
        show(900);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(40));assertSame(v,get("epubWebView"));assertTrue(Shadows.shadowOf(v).getLastEvaluatedJavascript().contains("*900/1000"));
    }
    @Test public void provisionalPageUsabilityDoesNotMarkPreviewEngineReady()throws Exception{
        show(100);WebView v=awaitView();Object b=bridge(v);
        Method m=b.getClass().getDeclaredMethod("onPageUsable",int.class,int.class,int.class,int.class);m.setAccessible(true);
        m.invoke(b,-((Long)get("loadedGeneration")).intValue(),1,3,250);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(40));
        assertFalse((Boolean)get("engineReady"));
    }
    @Test public void staleBridgeCannotReviveHiddenOrReconfiguredPreview()throws Exception{
        show(100);WebView old=awaitView();Object stale=bridge(old);ready(stale);File oldFile=(File)get("previewDocument");preview.hide();assertTrue(oldFile.exists());ready(stale);assertFalse((Boolean)get("engineReady"));assertTrue(preview.ownsView(old));
        show(300);WebView current=awaitView();ready(stale);assertFalse((Boolean)get("engineReady"));assertTrue(preview.ownsView(current));
        preview.configureEpubRenderer(renderer,900,1600,160,publication);assertFalse(preview.ownsView(current));ready(bridge(current));assertFalse((Boolean)get("engineReady"));
    }
    @Test public void hideDiscardsUnfinishedPaginationRatherThanReusingIncompleteMap()throws Exception{
        show(100);WebView old=awaitView();File document=(File)get("previewDocument");
        assertFalse((Boolean)get("engineReady"));preview.hide();
        assertNull(get("epubWebView"));assertFalse(document.exists());
        show(300);assertNotSame(old,awaitView());assertFalse((Boolean)get("engineReady"));
    }
    @Test public void destroyDeletesActiveAndStagedDocuments()throws Exception{
        show(500);awaitView();File file=(File)get("previewDocument");assertTrue(file.exists());
        preview.destroy();assertFalse(file.exists());assertTrue(((java.util.Set<?>)get("stagedDocuments")).isEmpty());
    }
    @Test public void chaptersAboveLegacyTwoMiBLimitAreReadWithoutTruncation()throws Exception{
        byte[] bytes=new byte[2*1024*1024+1];
        java.util.Arrays.fill(bytes,(byte)'x');
        Files.write(chapter.toPath(),bytes);
        String html=(String)call("readBoundedAndSanitize",new Class[]{File.class,int.class,int.class},chapter,0,0);
        assertNotNull(html);assertTrue(html.length()>=bytes.length);
    }
    @Test public void oversizedChapterIsExplicitlyUnavailableInsteadOfTruncated()throws Exception{
        Files.write(chapter.toPath(),new byte[PagePreviewPolicy.MAX_CHAPTER_BYTES+1]);
        assertNull(call("readBoundedAndSanitize",new Class[]{File.class,int.class,int.class},chapter,0,0));
    }
    @Test public void validXhtmlAboveTwoMiBIsSanitizedWithoutTruncation()throws Exception{
        StringBuilder b=new StringBuilder(2*1024*1024+512);
        b.append("<html><head><title>Large</title></head><body>");
        while(b.length()<2*1024*1024+1)b.append("<p class='author'>Physical pagination content</p>");
        b.append("</body></html>");
        Files.write(chapter.toPath(),b.toString().getBytes(StandardCharsets.UTF_8));
        String html=(String)call("readBoundedAndSanitize",new Class[]{File.class,int.class,int.class},chapter,0,0);
        assertNotNull(html);assertTrue(html.length()>=2*1024*1024);assertTrue(html.contains("Physical pagination content"));
    }
    @Test public void interruptedLargeXhtmlReadCancelsInsteadOfPublishingPartialMarkup()throws Exception{
        byte[] bytes=new byte[3*1024*1024];java.util.Arrays.fill(bytes,(byte)'x');
        Files.write(chapter.toPath(),bytes);
        final String[] result=new String[1];
        Thread t=new Thread(()->{Thread.currentThread().interrupt();try{result[0]=(String)call("readBoundedAndSanitize",new Class[]{File.class,int.class,int.class},chapter,0,0);}catch(Exception e){throw new AssertionError(e);}});
        t.start();t.join();assertNull(result[0]);
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    @Test public void portraitPreviewIsLargerAndStaysAboveSeekBar()throws Exception{
        activity.setContentView(root);
        root.layout(0,0,dp(360),dp(800));
        android.view.View anchor=new android.view.View(activity);root.addView(anchor);
        anchor.layout(0,dp(700),dp(360),dp(730));preview.setAnchorView(anchor);
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)((FrameLayout)get("panel")).getLayoutParams();
        assertEquals(dp(288),lp.width);assertEquals(dp(408),lp.height);
        assertEquals(dp(108),lp.bottomMargin);
        assertTrue(lp.width>dp(264));assertTrue(lp.height>dp(374));
    }
    @Test public void smallLandscapeAndMultiWindowRemainInsidePaddedRoot()throws Exception{
        for(int[] screen:new int[][]{{320,480},{800,360},{240,320},{180,200}}){
            root.setPadding(dp(16),dp(24),dp(28),dp(20));
            root.layout(0,0,dp(screen[0]),dp(screen[1]));
            call("updatePanelPosition",new Class[]{});
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)((FrameLayout)get("panel")).getLayoutParams();
            assertTrue(lp.width>0);assertTrue(lp.height>0);
            assertTrue(lp.width+lp.leftMargin+lp.rightMargin+root.getPaddingLeft()+root.getPaddingRight()<=root.getWidth());
            assertTrue(lp.height+lp.bottomMargin+root.getPaddingBottom()+root.getPaddingTop()+dp(12)<=root.getHeight());
        }
    }
    @Test public void resizingKeepsSamePreviewAndPhysicalPaginationViewport()throws Exception{
        root.layout(0,0,dp(360),dp(800));show(500);WebView view=awaitView();
        float portrait=view.getScaleX();long token=(Long)get("generation");
        root.layout(0,0,dp(640),dp(300));
        assertSame(view,get("epubWebView"));assertEquals(token,(long)(Long)get("generation"));
        assertEquals(800,view.getLayoutParams().width);assertEquals(1600,view.getLayoutParams().height);
        assertTrue(view.getScaleX()<portrait);assertEquals(view.getScaleX(),view.getScaleY(),0f);
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)((FrameLayout)get("panel")).getLayoutParams();
        assertTrue(view.getScaleY()*1600<=Math.max(1,lp.height-dp(30))+.1f);
    }
    @Test public void anchorMovementRepositionsAndDestroyRemovesLayoutListeners()throws Exception{
        activity.setContentView(root);
        root.layout(0,0,dp(360),dp(800));
        android.view.View anchor=new android.view.View(activity);root.addView(anchor);
        anchor.layout(0,dp(700),dp(360),dp(730));preview.setAnchorView(anchor);
        FrameLayout panel=(FrameLayout)get("panel");
        anchor.layout(0,dp(500),dp(360),dp(530));
        assertEquals(dp(308),((FrameLayout.LayoutParams)panel.getLayoutParams()).bottomMargin);
        preview.destroy();int height=panel.getLayoutParams().height;
        root.layout(0,0,dp(640),dp(200));anchor.layout(0,dp(100),dp(640),dp(130));
        assertEquals(height,panel.getLayoutParams().height);assertEquals(-1,root.indexOfChild(panel));
    }
    @Test public void offscreenAnchorFallsBackInsideVisibleRoot()throws Exception{
        activity.setContentView(root);root.layout(0,0,dp(360),dp(800));
        android.view.View anchor=new android.view.View(activity);root.addView(anchor);
        anchor.layout(0,-dp(100),dp(360),-dp(70));preview.setAnchorView(anchor);
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)((FrameLayout)get("panel")).getLayoutParams();
        assertTrue(lp.width>0);assertTrue(lp.height>0);
        assertTrue(lp.height+lp.bottomMargin<=root.getHeight());
    }
    private File otherChapter(String name)throws Exception{
        File f=new File(chapter.getParentFile(),name);Files.write(f.toPath(),"<html><body><p>Other chapter</p></body></html>".getBytes(StandardCharsets.UTF_8));return f;
    }
    private void awaitDocumentChange(File old)throws Exception{
        long until=System.nanoTime()+3_000_000_000L;
        while(System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();if(get("previewDocument")!=null&&!old.equals(get("previewDocument")))return;Thread.sleep(15);}
        fail("chapter document did not change");
    }
    @Test public void chapterSwitchReusesWebViewAndRejectsOldBridge()throws Exception{
        show(100);WebView view=awaitView();Object stale=bridge(view);File old=(File)get("previewDocument");
        preview.showEpub(otherChapter("second.xhtml"),1,2,200,"Second",0xff000000,0xffffffff);
        awaitDocumentChange(old);assertSame(view,get("epubWebView"));assertFalse(old.exists());
        ready(stale);assertFalse((Boolean)get("engineReady"));ready(bridge(view));assertTrue((Boolean)get("engineReady"));
    }
    @Test public void returningToActiveChapterCancelsQueuedOtherChapter()throws Exception{
        show(100);WebView view=awaitView();ready(bridge(view));File document=(File)get("previewDocument");
        preview.showEpub(otherChapter("queued.xhtml"),1,2,0,"Queued",0xff000000,0xffffffff);
        show(700);Thread.sleep(150);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(40));
        assertSame(view,get("epubWebView"));assertEquals(document,get("previewDocument"));assertEquals("",get("pendingEpubKey"));
        assertTrue((Boolean)get("engineReady"));assertTrue(Shadows.shadowOf(view).getLastEvaluatedJavascript().contains("*700/1000"));
    }
    @Test public void rapidSameChapterDragCoalescesJavascriptToLatestTarget()throws Exception{
        show(0);WebView view=awaitView();ready(bridge(view));
        String before=Shadows.shadowOf(view).getLastEvaluatedJavascript();
        show(200);show(400);show(999);assertEquals(before,Shadows.shadowOf(view).getLastEvaluatedJavascript());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(40));
        assertTrue(Shadows.shadowOf(view).getLastEvaluatedJavascript().contains("*999/1000"));
    }
    @Test public void renderedCacheHitDoesNotReloadChapterAndConfigChangeInvalidatesIt()throws Exception{
        show(350);WebView warm=awaitView();ready(bridge(warm));String key=(String)get("epubCurrentKey");
        java.util.Map<String,android.graphics.Bitmap> cache=(java.util.Map<String,android.graphics.Bitmap>)get("renderedCache");
        cache.put(key+"#350",android.graphics.Bitmap.createBitmap(20,30,android.graphics.Bitmap.Config.ARGB_8888));
        preview.hide();show(350);assertNotNull(get("epubWebView"));assertNull(get("pending"));
        assertEquals(android.view.View.VISIBLE,((android.widget.ImageView)get("pdfImage")).getVisibility());
        preview.configureEpubRenderer(renderer,801,1600,160,publication);assertTrue(cache.isEmpty());
    }
    @Test public void rapidChapterChangesOnlyLoadLastRequest()throws Exception{
        File b=otherChapter("b.xhtml"),c=otherChapter("c.xhtml");show(0);
        preview.showEpub(b,1,3,100,"B",0xff000000,0xffffffff);preview.showEpub(c,2,3,900,"C",0xff000000,0xffffffff);
        WebView view=awaitView();assertTrue(((String)get("epubCurrentKey")).startsWith(c.getAbsolutePath()));
        assertEquals(1,((java.util.Set<?>)get("stagedDocuments")).size());ready(bridge(view));
        assertTrue(Shadows.shadowOf(view).getLastEvaluatedJavascript().contains("*900/1000"));
    }

    @Test public void styleChangesInvalidateRenderedAndLivePreviews()throws Exception{
        preview.setEpubStyleKey("publisher:page");show(100);WebView old=awaitView();
        java.util.Map<String,android.graphics.Bitmap> cache=(java.util.Map<String,android.graphics.Bitmap>)get("renderedCache");
        cache.put("old",android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888));
        preview.setEpubStyleKey("custom-font:scroll");assertTrue(cache.isEmpty());assertFalse(preview.ownsView(old));assertNull(get("previewDocument"));
    }
    @Test public void renderedPreviewCacheIsBoundedToEightEntries()throws Exception{
        java.util.Map<String,android.graphics.Bitmap> cache=(java.util.Map<String,android.graphics.Bitmap>)get("renderedCache");
        for(int i=0;i<20;i++)cache.put("p"+i,android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888));
        assertEquals(8,cache.size());assertFalse(cache.containsKey("p0"));assertTrue(cache.containsKey("p19"));
    }

    @Test public void largerPreviewAdaptsToPhoneTabletAndLandscapeAboveAnchor()throws Exception{
        activity.setContentView(root);
        android.view.View anchor=new android.view.View(activity);root.addView(anchor);preview.setAnchorView(anchor);
        for(int[] screen:new int[][]{{360,800},{320,640},{240,320},{600,960},{840,480},{480,320}}){
            int w=dp(screen[0]),h=dp(screen[1]);
            root.setPadding(dp(12),dp(24),dp(12),dp(20));root.layout(0,0,w,h);
            int anchorTop=h-dp(80);anchor.layout(0,anchorTop,w,anchorTop+dp(32));
            call("updatePanelPosition",new Class[]{});
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)((FrameLayout)get("panel")).getLayoutParams();
            assertTrue(lp.width>0&&lp.width<=dp(288));assertTrue(lp.height>0&&lp.height<=dp(408));
            assertTrue(lp.width+lp.leftMargin+lp.rightMargin+root.getPaddingLeft()+root.getPaddingRight()<=w);
            int panelBottom=h-root.getPaddingBottom()-lp.bottomMargin;
            assertTrue(panelBottom<=anchorTop-dp(8));assertTrue(panelBottom-lp.height>=root.getPaddingTop()+dp(12));
            if(screen[0]==360&&screen[1]==800){assertEquals(dp(288),lp.width);assertEquals(dp(408),lp.height);}
            if(screen[0]==480&&screen[1]==320){assertTrue(lp.height<dp(408));assertTrue(lp.width<dp(288));}
        }
    }

    @Test public void replacedRequestsCannotPostponeTheDispatchDeadlineForever()throws Exception{
        long first=(Long)call("nextDispatchDelay",new Class[]{long.class},70L);
        long due=(Long)get("dispatchDueUptime");assertEquals(70L,first);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(30));
        assertEquals(40L,(long)(Long)call("nextDispatchDelay",new Class[]{long.class},70L));
        assertEquals(due,(long)(Long)get("dispatchDueUptime"));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(60));
        assertEquals(0L,(long)(Long)call("nextDispatchDelay",new Class[]{long.class},70L));
        preview.hide();assertEquals(0L,(long)(Long)get("dispatchDueUptime"));
        assertEquals(35L,(long)(Long)call("nextDispatchDelay",new Class[]{long.class},35L));
    }
    @Test public void continuousSameChapterDragKeepsOneSeekAndOneLayoutUpdateQueued()throws Exception{
        show(0);WebView view=awaitView();ready(bridge(view));long token=(Long)get("generation");
        for(int batch=0;batch<40;batch++){
            for(int i=0;i<25;i++)show((batch*25+i)%1001);
            assertTrue((Boolean)get("seekScheduled"));assertTrue((Boolean)get("panelUpdateScheduled"));
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32));
            assertFalse((Boolean)get("seekScheduled"));assertSame(view,get("epubWebView"));
            assertTrue(Shadows.shadowOf(view).getLastEvaluatedJavascript().contains("*"+((batch*25+24)%1001)+"/1000"));
        }
        assertEquals(token,(long)(Long)get("generation"));assertNull(get("pending"));
    }
    @Test public void replacedChapterRequestsKeepOneWorkerQueueEntryAndLatestTargetWins()throws Exception{
        File b=otherChapter("continuous-b.xhtml"),c=otherChapter("continuous-c.xhtml");
        for(int i=0;i<150;i++){
            preview.showEpub(i%2==0?b:c,i%2,2,i,"Continuous",0xff000000,0xffffffff);
            java.util.concurrent.ScheduledThreadPoolExecutor worker=(java.util.concurrent.ScheduledThreadPoolExecutor)get("executor");
            assertTrue(worker.getQueue().size()<=1);
        }
        WebView view=awaitView();assertTrue(((String)get("epubCurrentKey")).startsWith(c.getAbsolutePath()));
        assertEquals(0L,(long)(Long)get("dispatchDueUptime"));ready(bridge(view));
        assertTrue(Shadows.shadowOf(view).getLastEvaluatedJavascript().contains("*149/1000"));
        assertEquals(1,((java.util.Set<?>)get("stagedDocuments")).size());
    }

    @Test public void slowSteadyDragUpdatesEveryStepWhileFingerIsStillDown()throws Exception{
        show(0);WebView view=awaitView();ready(bridge(view));long token=(Long)get("generation");
        FrameLayout panel=(FrameLayout)get("panel");
        // The finger stays down throughout: no hide/release/committed reader seek is called.
        for(int target=50;target<=600;target+=50){
            String previous=Shadows.shadowOf(view).getLastEvaluatedJavascript();show(target);
            assertEquals(previous,Shadows.shadowOf(view).getLastEvaluatedJavascript());
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32));
            assertTrue(Shadows.shadowOf(view).getLastEvaluatedJavascript().contains("*"+target+"/1000"));
            assertEquals(android.view.View.VISIBLE,panel.getVisibility());assertSame(view,get("epubWebView"));
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(88));
        }
        assertEquals(token,(long)(Long)get("generation"));assertNull(get("pending"));
    }
    @Test public void slowDragCrossingChapterUpdatesWithoutWaitingForRelease()throws Exception{
        show(900);WebView view=awaitView();ready(bridge(view));File old=(File)get("previewDocument");
        File next=otherChapter("slow-next.xhtml");
        preview.showEpub(next,1,2,50,"Next",0xff000000,0xffffffff);awaitDocumentChange(old);ready(bridge(view));
        assertTrue(Shadows.shadowOf(view).getLastEvaluatedJavascript().contains("*50/1000"));
        for(int target=100;target<=300;target+=100){
            preview.showEpub(next,1,2,target,"Next",0xff000000,0xffffffff);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(120));
            assertTrue(Shadows.shadowOf(view).getLastEvaluatedJavascript().contains("*"+target+"/1000"));
            assertEquals(android.view.View.VISIBLE,((FrameLayout)get("panel")).getVisibility());
        }
        assertSame(view,get("epubWebView"));assertNull(get("pending"));
    }

}
