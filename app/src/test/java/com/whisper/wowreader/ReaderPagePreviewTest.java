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
@Config(sdk={23,35,36},manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ReaderPagePreviewTest {
    private Activity activity;
    private FrameLayout root;
    private ReaderPagePreview preview;
    private File publication,chapter;
    private final ReaderPagePreview.EpubRenderer renderer=view->{};
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
        Method m=bridge.getClass().getDeclaredMethod("onPageReady",int.class,int.class,int.class,int.class);m.setAccessible(true);m.invoke(bridge,-900000,1,10,0);Shadows.shadowOf(Looper.getMainLooper()).idle();
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
        show(900);assertSame(v,get("epubWebView"));assertTrue(Shadows.shadowOf(v).getLastEvaluatedJavascript().contains("*900/1000"));
    }
    @Test public void staleBridgeCannotReviveHiddenOrReconfiguredPreview()throws Exception{
        show(100);WebView old=awaitView();Object stale=bridge(old);File oldFile=(File)get("previewDocument");preview.hide();assertFalse(oldFile.exists());ready(stale);assertFalse((Boolean)get("engineReady"));assertFalse(preview.ownsView(old));
        show(300);WebView current=awaitView();ready(stale);assertFalse((Boolean)get("engineReady"));assertTrue(preview.ownsView(current));
        preview.configureEpubRenderer(renderer,900,1600,160,publication);assertFalse(preview.ownsView(current));ready(bridge(current));assertFalse((Boolean)get("engineReady"));
    }
    @Test public void destroyDeletesActiveAndStagedDocuments()throws Exception{
        show(500);awaitView();File file=(File)get("previewDocument");assertTrue(file.exists());
        preview.destroy();assertFalse(file.exists());assertTrue(((java.util.Set<?>)get("stagedDocuments")).isEmpty());
    }
    @Test public void oversizedChapterIsExplicitlyUnavailableInsteadOfTruncated()throws Exception{
        Files.write(chapter.toPath(),new byte[2*1024*1024+1]);
        assertNull(call("readBoundedAndSanitize",new Class[]{File.class,int.class,int.class},chapter,0,0));
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    @Test public void portraitPreviewIsLargerAndStaysAboveSeekBar()throws Exception{
        activity.setContentView(root);
        root.layout(0,0,dp(360),dp(800));
        android.view.View anchor=new android.view.View(activity);root.addView(anchor);
        anchor.layout(0,dp(700),dp(360),dp(730));preview.setAnchorView(anchor);
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)((FrameLayout)get("panel")).getLayoutParams();
        assertEquals(dp(264),lp.width);assertEquals(dp(374),lp.height);
        assertEquals(dp(108),lp.bottomMargin);
        assertTrue(lp.width>dp(148));assertTrue(lp.height>dp(210));
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
}
