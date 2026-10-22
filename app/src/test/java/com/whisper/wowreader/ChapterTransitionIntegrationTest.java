package com.whisper.wowreader;

import android.content.SharedPreferences;
import android.os.Looper;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={24,28,35,36}, manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ChapterTransitionIntegrationTest {
    private BookReaderActivity activity;
    private SharedPreferences prefs;
    private WebView active;
    private WebView warm;

    @Before public void setup() throws Exception {
        activity = Robolectric.buildActivity(BookReaderActivity.class).get();
        // These tests exercise the warm two-view path. Constrained/large-book
        // serialization is covered separately by EpubMemoryIntegrationTest.
        android.app.ActivityManager am = (android.app.ActivityManager)
                activity.getSystemService(android.content.Context.ACTIVITY_SERVICE);
        Shadows.shadowOf(am).setMemoryClass(256);
        Shadows.shadowOf(am).setIsLowRamDevice(false);
        prefs = activity.getSharedPreferences("chapter-transition-test",0);
        prefs.edit().clear().commit();
        put("prefs", prefs);
        active = (WebView) call("createPreloadWebView", new Class<?>[0]);
        warm = (WebView) call("createPreloadWebView", new Class<?>[0]);
        active.layout(0,0,400,800); warm.layout(0,0,400,800);
        put("webView", active); put("preloadWebView", warm);
        put("readingMode", "page");
        put("publisherStyles",false);
        put("bookFile", new File(activity.getCacheDir(), "fixture.epub"));
        put("preloadGeneration", 7);
        put("chapterLoadGeneration", 2);
        put("currentSpine", 1);
        put("preloadedSpine", 1);
    }

    @Test public void readyWarmChapterCanActivateWhileNavigationIsWaiting() throws Exception {
        put("preloadReady", true); put("chapterLoading", true); put("currentProgressPermille",1000);
        assertEquals(true,call("activatePreloadedChapterIfReady",new Class<?>[0]));
        assertSame(warm,get("webView"));
        assertSame(active,get("preloadWebView"));
        assertEquals(1f,active.getAlpha(),0f);
        assertEquals(0.01f,warm.getAlpha(),0f);
        assertEquals(3,get("chapterLoadGeneration"));
        String js=Shadows.shadowOf(warm).getLastEvaluatedJavascript();
        assertTrue(js.contains("st.generation=3"));
        assertTrue(js.contains("st.pageMap"));
        assertFalse(js.contains("st.measure("));
        export("promote-ready-end.js",js);
    }

    @Test public void inFlightPreloadIsAdoptedWithoutCancellingOrReloadingTheDocument() throws Exception {
        put("preloadReady", false); put("preloadLoading", true); put("chapterLoading", true); put("currentProgressPermille",1000);
        assertEquals(true,call("activatePreloadedChapter",new Class<?>[]{boolean.class},true));
        assertSame(warm,get("webView"));
        assertEquals(1f,active.getAlpha(),0f);
        assertEquals(0.01f,warm.getAlpha(),0f);
        String js=Shadows.shadowOf(warm).getLastEvaluatedJavascript();
        assertTrue(js.contains("pendingRestoreRatio"));
        assertTrue(js.contains("'pending'"));
        export("promote-pending-end.js",js);
        assertNull(Shadows.shadowOf(warm).getLastLoadedUrl());
    }

    @Test public void warmReadinessWaitsForMatchingGenerationAndCaptureCompletion() throws Exception {
        put("preloadLoading",true); put("chapterLoading",true);
        call("markPreloadedChapterReady",new Class<?>[]{WebView.class,int.class},warm,-6);
        assertFalse((Boolean)get("preloadReady"));
        assertSame(active,get("webView"));
        put("chapterTransitionCapturePending",true);
        call("markPreloadedChapterReady",new Class<?>[]{WebView.class,int.class},warm,-7);
        assertTrue((Boolean)get("preloadReady"));
        assertSame(active,get("webView"));
        put("chapterTransitionCapturePending",false);
        assertEquals(true,call("activatePreloadedChapterIfReady",new Class<?>[0]));
        assertSame(warm,get("webView"));
    }

    @Test public void settingsInvalidateWarmPageAndAlwaysSaveGlobals() throws Exception {
        BookTypographyStore.save(prefs,"fixture.epub",90,"publisher",120,2,"left",true);
        put("fontPercent",180); put("fontChoice","pyidaungsu"); put("fontWeight",600);
        put("lineSpacing",190); put("marginPercent",9); put("textAlignment","right");
        put("autoSpacingAdjustment",false); put("preloadReady",true); put("preloadLoading",true);
        call("saveReaderPreferences",new Class<?>[0]);
        assertEquals(180,prefs.getInt("epub_font",0));
        assertEquals("pyidaungsu",prefs.getString("epub_font_choice",""));
        assertEquals(600,prefs.getInt("epub_font_weight",0));
        assertEquals(190,prefs.getInt("epub_line_spacing",0));
        assertEquals(9,prefs.getInt("epub_margin",0));
        assertEquals("right",prefs.getString("epub_text_alignment",""));
        assertFalse(prefs.getBoolean("epub_auto_spacing",true));
        assertEquals(-1,get("preloadedSpine"));
        assertFalse((Boolean)get("preloadReady"));
        assertFalse((Boolean)get("preloadLoading"));
        // The old override is retained, not deleted or silently rewritten.
        assertEquals(90,BookTypographyStore.load(prefs,"fixture.epub",100,"publisher",160,5,"justify",true).fontPercent);
    }

    @Test public void missingRendererReadyCannotKeepNavigationLockedForever() throws Exception {
        put("chapterLoading",true); put("pageTurnLocked",true);
        call("scheduleChapterLoadWatchdog",new Class<?>[]{WebView.class,int.class},active,2);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8));
        assertFalse((Boolean)get("chapterLoading"));
        assertFalse((Boolean)get("pageTurnLocked"));
        assertEquals(1f,active.getAlpha(),0f);
        assertEquals(2,get("chapterFailedGeneration"));
        assertNotEquals(2,get("chapterReadyGeneration"));
    }

    @Test public void obsoleteWatchdogCannotUnlockAnotherChapter() throws Exception {
        put("chapterLoading",true); put("pageTurnLocked",true);
        call("scheduleChapterLoadWatchdog",new Class<?>[]{WebView.class,int.class},active,1);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8));
        assertTrue((Boolean)get("chapterLoading"));
        assertTrue((Boolean)get("pageTurnLocked"));
    }

    @Test public void fastReverseNavigationIsNotDroppedByOldTimeDebounce() throws Exception {
        @SuppressWarnings("unchecked") List<File> spine=(List<File>)get("spine");
        spine.add(new File(activity.getCacheDir(),"a.xhtml"));
        spine.add(new File(activity.getCacheDir(),"b.xhtml"));
        put("lastChapterNavMs",System.currentTimeMillis());
        put("chapterLoading",false); put("preloadReady",false); put("preloadLoading",false);
        call("navigateChapter",new Class<?>[]{int.class,boolean.class},-1,true);
        assertEquals(0,get("currentSpine"));
        assertEquals(1000,get("currentProgressPermille"));
        assertTrue((Boolean)get("chapterLoading"));
        assertTrue(Shadows.shadowOf((WebView)get("webView")).getLastLoadedUrl().endsWith("a.xhtml"));
    }

    @Test public void coldNoneChapterLoadsSpareAndRetainsOutgoingUntilCommit() throws Exception {
        @SuppressWarnings("unchecked") List<File> spine=(List<File>)get("spine");
        File a=new File(activity.getCacheDir(),"a.xhtml"),b=new File(activity.getCacheDir(),"b.xhtml");
        spine.add(a);spine.add(b);
        active.loadUrl(android.net.Uri.fromFile(a).toString());
        warm.loadUrl(android.net.Uri.fromFile(a).toString());
        put("preloadedSpine",-1);put("preloadReady",false);put("preloadLoading",false);put("pageAnimation","none");
        call("loadCurrentEpubChapter",new Class<?>[0]);
        assertSame(warm,get("webView"));assertSame(active,get("preloadWebView"));
        assertEquals(1f,active.getAlpha(),0f);assertEquals(0.01f,warm.getAlpha(),0f);
        assertTrue(Shadows.shadowOf(active).getLastLoadedUrl().endsWith("a.xhtml"));
        assertTrue(Shadows.shadowOf(warm).getLastLoadedUrl().endsWith("b.xhtml"));
        assertNull(get("chapterTransitionBitmap"));
        assertTrue(Shadows.shadowOf(warm).getLastEvaluatedJavascript().contains("st.generation!==-9"));
    }

    @Test public void stalePageFinishedCannotStyleTheWrongDocument() throws Exception {
        @SuppressWarnings("unchecked") List<File> spine=(List<File>)get("spine");
        File a=new File(activity.getCacheDir(),"a.xhtml"), b=new File(activity.getCacheDir(),"b.xhtml");
        spine.add(a); spine.add(b);
        active.loadUrl(android.net.Uri.fromFile(b).toString());
        assertEquals(false,call("isChapterUrl",new Class<?>[]{WebView.class,String.class,int.class},active,android.net.Uri.fromFile(a).toString(),1));
        assertEquals(true,call("isChapterUrl",new Class<?>[]{WebView.class,String.class,int.class},active,android.net.Uri.fromFile(b).toString(),1));
    }

    @Test public void exportActualReaderScriptsForChromiumRegression() throws Exception {
        CapturingWebView capture = new CapturingWebView(activity);
        File dir=new File("build/reader-engine-fixtures");
        assertTrue(dir.isDirectory()||dir.mkdirs());
        put("fontPercent",160); put("lineSpacing",180); put("marginPercent",7);
        put("textAlignment","left"); put("fontChoice","publisher");
        for(String mode:new String[]{"page","scroll"}) {
            put("readingMode",mode);
            call("applyReaderStyle",new Class<?>[]{WebView.class,boolean.class,int.class,int.class},capture,false,0,-7);
            assertNotNull(capture.script);
            assertEquals(160,capture.getSettings().getTextZoom());
            assertTrue(capture.script.contains("st.generation=-7"));
            export(mode+".js",capture.script);
        }
        put("readingMode","page");put("textAlignment","right");put("lineSpacing",200);put("marginPercent",11);
        call("applyReaderStyle",new Class<?>[]{WebView.class,boolean.class,int.class,int.class},capture,false,0,-8);
        export("page-right.js",capture.script);
        put("fontChoice","pyidaungsu");put("fontWeight",600);
        call("applyReaderStyle",new Class<?>[]{WebView.class,boolean.class,int.class,int.class},capture,false,0,-9);
        export("page-selected-font.js",capture.script);
        put("publisherStyles",true);put("textAlignment","publisher");put("fontChoice","publisher");
        for(String mode:new String[]{"page","scroll"}) {
            put("readingMode",mode);
            call("applyReaderStyle",new Class<?>[]{WebView.class,boolean.class,int.class,int.class},capture,false,0,-10);
            export(mode+"-publisher.js",capture.script);
        }
        put("publisherStyles",false);put("textAlignment","publisher");put("readingMode","page");
        call("applyReaderStyle",new Class<?>[]{WebView.class,boolean.class,int.class,int.class},capture,false,0,-11);
        export("page-custom-original-align.js",capture.script);
    }

    private void export(String name,String script)throws Exception {
        File dir=new File("build/reader-engine-fixtures");
        assertTrue(dir.isDirectory()||dir.mkdirs());
        Files.write(new File(dir,name).toPath(),script.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static class CapturingWebView extends WebView {
        String script;
        CapturingWebView(android.content.Context context){super(context);}
        @Override public void evaluateJavascript(String script,ValueCallback<String> callback){this.script=script;}
    }
    private Object call(String name,Class<?>[] types,Object...args)throws Exception {
        Method m=BookReaderActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);
    }
    private Object get(String name)throws Exception{Field f=BookReaderActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void put(String name,Object value)throws Exception{Field f=BookReaderActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
}
