package com.whisper.wowreader;

import android.os.Looper;
import android.webkit.WebView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 31, 36}, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ChapterStageIntegrationTest {
    private BookReaderActivity activity;
    private WebView owner;
    private Object bridge;
    private ChapterTransitionTrace trace;
    @Before public void setUp() throws Exception {
        activity = Robolectric.buildActivity(BookReaderActivity.class).get();
        owner = new WebView(activity);
        put("webView", owner);
        put("chapterLoadGeneration", 7);
        trace = (ChapterTransitionTrace) get("chapterTransitionTrace");
        trace.begin(7, 100, 100, "cold");
        Class<?> c = Class.forName("com.whisper.wowreader.BookReaderActivity$ReaderBridge");
        Constructor<?> ctor = c.getDeclaredConstructor(BookReaderActivity.class, WebView.class);
        ctor.setAccessible(true);
        bridge = ctor.newInstance(activity, owner);
    }
    @Test public void acceptsOnlyMatchingActiveOwnerAndGeneration() throws Exception {
        stage(7, "images_wait", 12);
        assertTrue(trace.summary(7).contains("images_wait_ms=12"));
        stage(6, "fonts_wait", 99);
        assertFalse(trace.summary(7).contains("fonts_wait"));
        put("webView", new WebView(activity));
        stage(7, "page_scan", 77);
        assertFalse(trace.summary(7).contains("page_scan"));
    }
    @Test public void rejectsArbitraryStringsAndUnboundedNumbers() throws Exception {
        stage(7, "file:///private/chapter.xhtml", 22);
        stage(7, "image_decode", Double.NaN);
        stage(7, "page_scan", -1);
        stage(7, "layout_prepare", 200001);
        assertEquals("generation=7 route=cold", trace.summary(7));
    }
    @Test public void cancellationInvalidatesPendingWarmupAndDropsQueuedWork() throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1));
        try {
            executor.getQueue().add(() -> {});
            put("resourceWarmupFuture", future);
            put("resourceWarmupExecutor", executor);
            put("resourceWarmupSpine", 3);
            put("resourceWarmupGeneration", 9);
            Method m = BookReaderActivity.class.getDeclaredMethod("cancelResourceWarmup");
            m.setAccessible(true); m.invoke(activity);
            assertTrue(future.isCancelled());
            assertNull(get("resourceWarmupFuture"));
            assertEquals(10, get("resourceWarmupGeneration"));
            assertEquals(-1, get("resourceWarmupSpine"));
            assertTrue(executor.getQueue().isEmpty());
        } finally { executor.shutdownNow(); }
    }
    @Test public void stageTelemetryDoesNotWriteReaderPreferences() throws Exception {
        android.content.SharedPreferences prefs = activity.getSharedPreferences("stage-state", 0);
        prefs.edit().putInt("resume", 777).putString("bookmarks", ",3,").commit();
        put("prefs", prefs);
        java.util.Map<String, ?> before = new java.util.HashMap<>(prefs.getAll());
        stage(7, "page_scan", 24);
        assertEquals(before, prefs.getAll());
    }
    private void stage(int generation, String stage, double duration) throws Exception {
        Method m = bridge.getClass().getDeclaredMethod("onChapterStage", int.class, String.class, double.class);
        m.setAccessible(true); m.invoke(bridge, generation, stage, duration);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private void put(String name, Object value) throws Exception {
        Field f = BookReaderActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(activity, value);
    }
    private Object get(String name) throws Exception {
        Field f = BookReaderActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity);
    }
}
