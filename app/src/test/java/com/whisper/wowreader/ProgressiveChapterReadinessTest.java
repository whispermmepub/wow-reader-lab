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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Regression coverage for progressive page usability versus authoritative pagination. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 28, 35, 36}, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ProgressiveChapterReadinessTest {
    private BookReaderActivity activity;
    private SharedPreferences prefs;
    private CapturingWebView owner;
    private Object bridge;
    private static final String BOOK = "progressive-fixture.epub";

    @Before public void setUp() throws Exception {
        activity = Robolectric.buildActivity(BookReaderActivity.class).get();
        prefs = activity.getSharedPreferences("progressive-readiness-test", 0);
        prefs.edit().clear().commit();
        owner = new CapturingWebView(activity);
        owner.layout(0, 0, 400, 800);
        put("prefs", prefs);
        put("bookFile", new File(activity.getCacheDir(), BOOK));
        put("contentBookId", BOOK);
        put("webView", owner);
        put("readingMode", "page");
        put("chapterLoadGeneration", 7);
        put("chapterLoading", false);
        put("chapterPaginationPending", false);
        put("currentSpine", 0);
        put("currentPageInChapter", 1);
        put("pageCountInChapter", 1);
        put("currentProgressPermille", 0);
        put("epubProgressModel", EpubProgressModel.uniform(2));
        put("spine", new ArrayList<File>());
        @SuppressWarnings("unchecked") List<File> spine = (List<File>) get("spine");
        spine.add(new File(activity.getCacheDir(), "one.xhtml"));
        spine.add(new File(activity.getCacheDir(), "two.xhtml"));
        put("chapterTitles", new ArrayList<String>());
        Object model = get("wholeBookPageModel");
        model.getClass().getDeclaredMethod("reset", int.class).invoke(model, 2);
        Class<?> c = Class.forName("com.whisper.wowreader.BookReaderActivity$ReaderBridge");
        Constructor<?> ctor = c.getDeclaredConstructor(BookReaderActivity.class, WebView.class);
        ctor.setAccessible(true);
        bridge = ctor.newInstance(activity, owner);
    }

    @Test public void matchingUsablePageSetsPendingAndReadyGeneration() throws Exception {
        put("chapterLoading", true);
        put("pageTurnLocked", true);
        owner.setAlpha(0.01f);
        invokeBridge("onPageUsable", 7, 2, 4, 250);
        idle();
        idleFor(220);
        assertTrue((Boolean) get("chapterPaginationPending"));
        assertFalse((Boolean) get("chapterLoading"));
        assertFalse((Boolean) get("pageTurnLocked"));
        assertEquals(2, get("currentPageInChapter"));
        assertEquals(4, get("pageCountInChapter"));
        assertEquals(7, get("chapterReadyGeneration"));
    }

    @Test public void staleGenerationAndOwnerUsableCallbacksAreIgnored() throws Exception {
        invokeBridge("onPageUsable", 6, 2, 4, 250);
        idle();
        assertFalse((Boolean) get("chapterPaginationPending"));
        assertEquals(1, get("currentPageInChapter"));
        CapturingWebView replacement = new CapturingWebView(activity);
        Object staleOwnerBridge = newBridge(replacement);
        invoke(staleOwnerBridge, "onPageUsable", 7, 3, 8, 400);
        idle();
        assertFalse((Boolean) get("chapterPaginationPending"));
        assertEquals(1, get("currentPageInChapter"));
    }

    @Test public void partialUsableCountDoesNotEnterWholeBookModelOrPersistLocation() throws Exception {
        invokeBridge("onPageUsable", 7, 1, 9, 500);
        idle();
        Object model = get("wholeBookPageModel");
        assertEquals(-1, model.getClass().getDeclaredMethod("wholePageIfKnown", int.class, int.class).invoke(model, 0, 1));
        assertEquals(0, ReadingProgressStore.get(prefs, BOOK));
        assertFalse(prefs.contains("percent_" + BOOK));
        assertFalse(prefs.contains("epub_location_" + BOOK + "chapter"));
    }

    @Test public void authoritativePageReadyClearsPendingAndUpdatesModelAndProgress() throws Exception {
        put("chapterPaginationPending", true);
        invokeBridge("onPageReady", 7, 3, 6, 1000);
        idle();
        assertFalse((Boolean) get("chapterPaginationPending"));
        assertEquals(3, get("currentPageInChapter"));
        assertEquals(6, get("pageCountInChapter"));
        Object model = get("wholeBookPageModel");
        assertEquals(3, model.getClass().getDeclaredMethod("wholePageIfKnown", int.class, int.class).invoke(model, 0, 3));
        // Two spine chapters are configured; chapter zero at 100% is 50% overall.
        assertEquals(50, ReadingProgressStore.get(prefs, BOOK));
    }

    @Test public void pendingTurnCompletionOnlyUpdatesProvisionalAndUnlocks() throws Exception {
        put("chapterPaginationPending", true);
        put("pageTurnLocked", true);
        invokeBridge("onPageTurnComplete", 7, 2, 5, 600);
        idle();
        assertTrue((Boolean) get("chapterPaginationPending"));
        assertFalse((Boolean) get("pageTurnLocked"));
        assertEquals(2, get("currentPageInChapter"));
        assertEquals(5, get("pageCountInChapter"));
        assertEquals(0, ReadingProgressStore.get(prefs, BOOK));
    }

    @Test public void pendingTurnRoutesToJavascriptNotPrematureNativeChapterNext() throws Exception {
        put("chapterPaginationPending", true);
        put("pageTurnLocked", false);
        put("lastPageTurnMs", 0L);
        put("currentPageInChapter", 1);
        put("pageCountInChapter", 1);
        invoke(activity, "turnPage", 1);
        assertEquals(0, get("currentSpine"));
        assertTrue(owner.script.contains("__wowPageEngine.turn(1)"));
        owner.script = null;
        put("pageTurnLocked", false);
        put("lastPageTurnMs", 0L);
        invoke(activity, "turnPage", -1);
        assertEquals(0, get("currentSpine"));
        assertTrue(owner.script.contains("__wowPageEngine.turn(-1)"));
        assertFalse(owner.script.contains("requestChapter"));
        /* The script contract independently guards a partial frontier. */
        String js = ReaderProgressivePaginationScripts.measureAndTurnJs(0);
        assertTrue(js.contains("!st.paginationComplete"));
        assertTrue(js.contains("WoW.onPageTurnComplete"));
        assertTrue(js.contains("return 'pending'"));
        assertFalse(js.contains("WoW.requestChapter(st.generation,1);return 'pending'"));
    }

    @Test public void obsoleteChapterCancellationCarriesEpochAndCollectorCancellation() throws Exception {
        invoke(activity, "cancelObsoleteChapterWork", owner);
        assertNotNull(owner.script);
        assertTrue(owner.script.contains("st.cancelled=true"));
        assertTrue(owner.script.contains("st.measureEpoch=(st.measureEpoch||0)+1"));
        assertTrue(owner.script.contains("st.cancelCollect()"));
        assertTrue(owner.script.contains("st.pendingRestoreRatio=null"));
    }

    @Test public void rejectedFirstAndLastChapterRequestsResetBoundaryAndUnlock() throws Exception {
        put("pageTurnLocked", true);
        invokeBridge("requestChapter", 7, -1);
        idle();
        assertFalse((Boolean) get("pageTurnLocked"));
        assertTrue(owner.script.contains("boundaryRequested=false"));
        owner.script = null;
        put("pageTurnLocked", true);
        put("currentSpine", 1);
        invokeBridge("requestChapter", 7, 1);
        idle();
        assertFalse((Boolean) get("pageTurnLocked"));
        assertNotNull(owner.script);
        assertTrue(owner.script.contains("boundaryRequested=false"));
    }

    @Test public void fastForwardAndReverseNativeChecksHonorPendingAndBoundaries() throws Exception {
        put("chapterPaginationPending", true);
        put("pageTurnLocked", true);
        invoke(activity, "turnPage", 1);
        assertTrue((Boolean) get("pageTurnLocked"));
        put("chapterPaginationPending", false);
        put("lastPageTurnMs", 0L);
        put("currentSpine", 1);
        invoke(activity, "turnPage", -1);
        assertTrue((Boolean) get("pageTurnLocked"));
    }

    @Test public void durableLifecycleSaveRetainsAuthoritativeLocationDuringPartialScan() throws Exception {
        prefs.edit().putInt("percent_" + BOOK, 37)
                .putInt("epub_location_" + BOOK + "chapter", 0)
                .putInt("epub_location_" + BOOK + "permille", 740)
                .putString("marks_" + BOOK, ",0,").commit();
        java.util.Map<String, ?> before = new java.util.HashMap<>(prefs.getAll());
        put("chapterPaginationPending", true);
        put("currentSpine", 1);
        put("currentProgressPermille", 999);
        invoke(activity, "saveEpubStateDurable");
        assertEquals(before, prefs.getAll());
    }

    @Test public void styleInjectionKeepsDurableWritesGatedUntilAuthoritativeReady() throws Exception {
        put("chapterPaginationPending", true);
        invoke(activity, "applyReaderStyle", owner, true, 0, 7);
        assertTrue((Boolean) get("chapterPaginationPending"));
        assertNotNull(owner.script);
        assertTrue(owner.script.contains("st.styleEpoch"));
    }

    @Test public void exactTocDestinationDoesNotRevealProvisionalStartPage() throws Exception {
        put("chapterLoading", true);
        put("pageTurnLocked", true);
        put("pendingTocFragment", "late-anchor");
        owner.setAlpha(0.01f);
        invokeBridge("onPageUsable", 7, 1, 4, 0);
        idleFor(220);
        assertTrue((Boolean) get("chapterLoading"));
        assertTrue((Boolean) get("pageTurnLocked"));
        assertEquals(0.01f, owner.getAlpha(), 0f);
    }

    private Object newBridge(WebView view) throws Exception {
        Class<?> c = Class.forName("com.whisper.wowreader.BookReaderActivity$ReaderBridge");
        Constructor<?> ctor = c.getDeclaredConstructor(BookReaderActivity.class, WebView.class);
        ctor.setAccessible(true);
        return ctor.newInstance(activity, view);
    }
    private void invokeBridge(String name, Object... args) throws Exception { invoke(bridge, name, args); }
    private void invoke(Object target, String name, Object... args) throws Exception {
        Class<?> owner = target instanceof BookReaderActivity ? BookReaderActivity.class : target.getClass();
        Method m = null;
        for (Method candidate : owner.getDeclaredMethods()) {
            if (!candidate.getName().equals(name) || candidate.getParameterTypes().length != args.length) continue;
            boolean match = true;
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Class) continue;
                Class<?> p = candidate.getParameterTypes()[i];
                Object a = args[i];
                if (!(p.isInstance(a) || (p == int.class && a instanceof Integer) ||
                        (p == long.class && a instanceof Long) || (p == boolean.class && a instanceof Boolean))) {
                    match = false; break;
                }
            }
            if (match) { m = candidate; break; }
        }
        if (m == null) throw new NoSuchMethodException(name);
        m.setAccessible(true);
        Object[] actual = args;
        for (int i = 0; i < args.length; i++) if (args[i] instanceof Class) actual[i] = null;
        m.invoke(target, actual);
    }
    private Object get(String name) throws Exception { Field f = BookReaderActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
    private void put(String name, Object value) throws Exception { Field f = BookReaderActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(activity, value); }
    private void idle() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(30)); }
    private void idleFor(long millis) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis)); }

    static class CapturingWebView extends WebView {
        String script;
        CapturingWebView(android.content.Context context) { super(context); }
        @Override public void evaluateJavascript(String script, ValueCallback<String> callback) { this.script = script; }
        @Override public void postVisualStateCallback(long requestId, VisualStateCallback callback) { callback.onComplete(requestId); }
    }
}
