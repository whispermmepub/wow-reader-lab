package com.whisper.wowreader;

import android.content.SharedPreferences;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;

import static org.junit.Assert.*;

/** Regression coverage for compositor-gated chapter reveal and animation prefs. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 28, 35, 36}, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ChapterVisualCommitTest {
    private BookReaderActivity activity;
    private SharedPreferences prefs;
    private WebView owner;

    @Before public void setUp() throws Exception {
        activity = Robolectric.buildActivity(BookReaderActivity.class).get();
        prefs = activity.getSharedPreferences("chapter-visual-commit-test", 0);
        prefs.edit().clear().commit();
        put("prefs", prefs);
        owner = new CallbackRecordingWebView(activity);
        owner.layout(0, 0, 400, 800);
        put("webView", owner);
        put("chapterLoadGeneration", 17);
        put("chapterReadyGeneration", 17);
        put("chapterLoading", true);
        put("pageAnimation", "none");
        owner.setAlpha(0.01f);
    }

    @Test public void visualCommitKeepsIncomingHiddenBeforeCallbackOrFallback() throws Exception {
        call("revealStableChapter");
        assertEquals(0.01f, owner.getAlpha(), 0f);
        assertTrue((Boolean) get("chapterLoading"));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(179));
        assertEquals(0.01f, owner.getAlpha(), 0f);
        assertTrue((Boolean) get("chapterLoading"));
    }

    @Test public void visualCommitRevealsImmediatelyWithoutGeometryPolling() throws Exception {
        call("revealStableChapter");
        ((CallbackRecordingWebView)owner).complete(17);
        assertEquals(1f, owner.getAlpha(), 0f);
        assertFalse((Boolean)get("chapterLoading"));
        assertEquals(17,get("chapterReadyGeneration"));
        ((CallbackRecordingWebView)owner).complete(17);
        assertEquals(1f, owner.getAlpha(), 0f);
    }

    @Test public void engineNotReadyDoesNotArmVisualCallbackOrFallback() throws Exception {
        put("chapterReadyGeneration", 16);
        call("revealStableChapter");
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(220));
        assertEquals(0.01f, owner.getAlpha(), 0f);
        assertTrue((Boolean) get("chapterLoading"));
    }

    @Test public void visualCommitFallbackRevealsOnlyAfterItsBoundedDelay() throws Exception {
        call("revealStableChapter");
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(180));
        assertEquals(1f, owner.getAlpha(), 0f);
        assertFalse((Boolean) get("chapterLoading"));
    }

    @Test public void duplicateCompletionIsIdempotent() throws Exception {
        call("revealStableChapter");
        call("revealStableChapter");
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(180));
        assertFalse((Boolean) get("chapterLoading"));
        assertEquals(1f, owner.getAlpha(), 0f);
    }

    @Test public void staleGenerationCannotUnlockTheNewChapter() throws Exception {
        call("revealStableChapter");
        put("chapterLoadGeneration", 18);
        put("chapterLoading", true);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(180));
        assertEquals(0.01f, owner.getAlpha(), 0f);
        assertTrue((Boolean) get("chapterLoading"));
    }

    @Test public void staleOwnerCannotRevealAnotherChapter() throws Exception {
        call("revealStableChapter");
        WebView replacement = (WebView) call("createPreloadWebView");
        replacement.layout(0, 0, 400, 800);
        replacement.setAlpha(0.01f);
        put("webView", replacement);
        put("chapterLoadGeneration", 18);
        put("chapterLoading", true);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(180));
        assertEquals(0.01f, replacement.getAlpha(), 0f);
        assertTrue((Boolean) get("chapterLoading"));
    }

    @Test public void setPageAnimationPersistsNoneWithoutInvalidatingPreloadState() throws Exception {
        put("preloadReady", true);
        put("preloadLoading", true);
        put("preloadGeneration", 31);
        put("chapterLoading", true);
        call("setPageAnimation", String.class, "none");
        assertEquals("none", get("pageAnimation"));
        assertEquals("none", prefs.getString("epub_page_animation", null));
        assertTrue((Boolean) get("preloadReady"));
        assertTrue((Boolean) get("preloadLoading"));
        assertEquals(31, get("preloadGeneration"));
        assertTrue((Boolean) get("chapterLoading"));
    }

    @Test public void setPageAnimationStoresSlideAndStillPreservesInflightGeneration() throws Exception {
        put("preloadReady", false);
        put("preloadLoading", true);
        put("preloadGeneration", 44);
        put("chapterLoading", true);
        call("setPageAnimation", String.class, "slide");
        assertEquals("slide", get("pageAnimation"));
        assertEquals("slide", prefs.getString("epub_page_animation", null));
        assertFalse((Boolean) get("preloadReady"));
        assertTrue((Boolean) get("preloadLoading"));
        assertEquals(44, get("preloadGeneration"));
        assertTrue((Boolean) get("chapterLoading"));
    }

    @Test public void obsoleteSlideCannotUnlockOrRevealNewChapter() throws Exception {
        put("pageSlideGeneration", 8);put("pageTurnLocked", true);
        Method finish=BookReaderActivity.class.getDeclaredMethod("finishNativeSlideIfCurrent",WebView.class,int.class,int.class);
        finish.setAccessible(true);finish.invoke(activity,owner,16,8);
        assertTrue((Boolean)get("pageTurnLocked"));assertEquals(0.01f,owner.getAlpha(),0f);
        finish.invoke(activity,owner,17,7);
        assertTrue((Boolean)get("pageTurnLocked"));assertEquals(0.01f,owner.getAlpha(),0f);
        finish.invoke(activity,owner,17,8); // even a matching slide may not finish a loading chapter
        assertTrue((Boolean)get("pageTurnLocked"));assertEquals(0.01f,owner.getAlpha(),0f);
    }

    @Test public void selectingNoneInvalidatesQueuedSlideEvenDuringChapterLoading() throws Exception {
        put("pageSlideGeneration",11);put("preloadLoading",true);put("pageTurnLocked",true);
        call("setPageAnimation",String.class,"none");
        assertEquals(12,get("pageSlideGeneration"));assertTrue((Boolean)get("pageTurnLocked"));
        assertEquals(0.01f,owner.getAlpha(),0f);
    }

    /** A callback-recording WebView documents the test seam used by the real engine. */
    static class CallbackRecordingWebView extends WebView {
        VisualStateCallback callback;
        CallbackRecordingWebView(android.content.Context context) { super(context); }
        @Override public void postVisualStateCallback(long requestId, VisualStateCallback cb) {
            callback = cb;
        }
        @Override public boolean postDelayed(Runnable action, long delayMillis) {
            return new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(action, delayMillis);
        }
        void complete(long requestId) { assertNotNull(callback); callback.onComplete(requestId); }
    }

    private Object call(String name, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) types[i] = args[i] instanceof Class ? (Class<?>) args[i] : args[i].getClass();
        if (args.length == 2 && args[0] == String.class) {
            Method m = BookReaderActivity.class.getDeclaredMethod(name, String.class);
            m.setAccessible(true); return m.invoke(activity, args[1]);
        }
        Method m = BookReaderActivity.class.getDeclaredMethod(name, types);
        m.setAccessible(true); return m.invoke(activity, args.length == 0 ? new Object[0] : args);
    }
    private Object get(String name) throws Exception {
        Field f = BookReaderActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity);
    }
    private void put(String name, Object value) throws Exception {
        Field f = BookReaderActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(activity, value);
    }
}
