package com.whisper.wowreader;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.webkit.WebView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

/** Memory-pressure scheduling, renderer recovery, and host EPUB extraction regressions. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 28, 31, 35, 36}, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class EpubMemoryIntegrationTest {
    private BookReaderActivity activity;
    private SharedPreferences prefs;
    private File book;

    @Before public void setUp() throws Exception {
        book = new File(RuntimeEnvironment.getApplication().getCacheDir(), "memory-fixture.epub");
        activity = Robolectric.buildActivity(BookReaderActivity.class,
                new Intent(RuntimeEnvironment.getApplication(), BookReaderActivity.class)
                        .putExtra("path", book.getAbsolutePath())).get();
        prefs = activity.getSharedPreferences("wow-reader-memory-test", 0);
        prefs.edit().clear().commit();
        put("prefs", prefs);
        put("bookFile", book);
        put("isPdf", false);
        put("contentBookId", "memory-book-id");
        FrameLayout container = new FrameLayout(activity);
        put("root", container);
        put("epubWebContent", container);
    }

    @Test public void policyThresholdsAreSchedulingOnlyAndHugeArchivesRemainAccepted() {
        assertFalse(EpubMemoryPolicy.serializeRenderers(8L * 1024 * 1024 - 1, 0, false, false));
        assertTrue(EpubMemoryPolicy.serializeRenderers(8L * 1024 * 1024, 0, false, false));
        assertFalse(EpubMemoryPolicy.serializeRenderers(0, 1024L * 1024 - 1, false, false));
        assertTrue(EpubMemoryPolicy.serializeRenderers(0, 1024L * 1024, false, false));
        assertTrue(EpubMemoryPolicy.serializeRenderers(33L * 1024 * 1024, 2L * 1024 * 1024, false, false));
        assertTrue(EpubMemoryPolicy.serializeRenderers(1, 1, true, false));
        assertTrue(EpubMemoryPolicy.serializeRenderers(1, 1, false, true));
    }

    @Test public void releaseSpeculativeRenderersDestroysSpareButPreservesActiveAndState() throws Exception {
        WebView active = newWebView();
        WebView spare = newWebView();
        WebView counter = newWebView();
        List<File> spine = spine("one.xhtml", "two.xhtml");
        FrameLayout container = (FrameLayout) get("epubWebContent");
        container.addView(active); container.addView(spare); container.addView(counter);
        put("webView", active); put("preloadWebView", (Object) spare);
        put("wholeBookCounterWebView", (Object) counter); put("spine", spine);
        put("currentSpine", 1); put("currentProgressPermille", 615);
        prefs.edit().putString("marks_memory-book-id", ",1,")
                .putString("library_sentinel", "untouched").commit();
        call("releaseSpeculativeEpubRenderers");
        assertSame(active, get("webView"));
        assertNull(get("preloadWebView"));
        assertNull(get("wholeBookCounterWebView"));
        assertEquals(2, ((List<?>) get("spine")).size());
        assertEquals(1, get("currentSpine"));
        assertEquals(615, get("currentProgressPermille"));
        assertEquals(",1,", prefs.getString("marks_memory-book-id", null));
        assertEquals("untouched", prefs.getString("library_sentinel", null));
        assertTrue(spare.getParent() == null && counter.getParent() == null);
        assertSame(container, active.getParent());
        assertEquals(1, container.getChildCount());
    }

    @Test public void runningLowLatchesPressureReleasesSpareAndPreservesResume() throws Exception {
        WebView active = newWebView();
        WebView spare = newWebView();
        List<File> spine = spine("chapter-a.xhtml", "chapter-b.xhtml");
        put("webView", active); put("preloadWebView", (Object) spare); put("spine", spine);
        put("currentSpine", 1); put("currentProgressPermille", 730);
        prefs.edit().putString("marks_memory-book-id", ",1,")
                .putString("resume_sentinel", "kept").commit();
        activity.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);
        assertTrue((Boolean) get("epubMemoryPressure"));
        assertNull(get("preloadWebView"));
        assertSame(active, get("webView"));
        assertEquals(1, get("currentSpine"));
        assertEquals(730, get("currentProgressPermille"));
        assertEquals("kept", prefs.getString("resume_sentinel", null));
        assertEquals(",1,", prefs.getString("marks_memory-book-id", null));
        String key = (String) call("contentLocationKey");
        assertEquals(1, prefs.getInt(key + "chapter", -1));
        assertEquals(730, prefs.getInt(key + "permille", -1));
    }

    @Test public void activeRendererLossRecoversOnceWithSameChapterAndProgress() throws Exception {
        WebView lost = newWebView();
        List<File> spine = spine("first.xhtml", "second.xhtml");
        put("webView", lost); put("spine", spine); put("currentSpine", 1);
        put("currentProgressPermille", 444); put("chapterLoadGeneration", 8);
        prefs.edit().putString("marks_memory-book-id", ",1,")
                .putString("library_sentinel", "stable").commit();
        assertTrue((Boolean) call("handleEpubRendererGone", new Class<?>[]{WebView.class}, lost));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        WebView replacement = (WebView) get("webView");
        assertNotNull(replacement);
        assertNotSame(lost, replacement);
        assertEquals(1, get("currentSpine"));
        assertEquals(444, get("currentProgressPermille"));
        assertTrue(Shadows.shadowOf(replacement).getLastLoadedUrl().endsWith("second.xhtml"));
        assertEquals(",1,", prefs.getString("marks_memory-book-id", null));
        assertEquals("stable", prefs.getString("library_sentinel", null));

        assertTrue((Boolean) call("handleEpubRendererGone", new Class<?>[]{WebView.class}, replacement));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue((Boolean) get("epubRendererRecoveryAttempted"));
        assertNotNull(get("chapterErrorCard"));
        assertEquals(1, get("currentSpine"));
        assertEquals(444, get("currentProgressPermille"));
        assertEquals(",1,", prefs.getString("marks_memory-book-id", null));
    }

    @Test public void staleSpareRendererCallbackCannotClobberActiveRenderer() throws Exception {
        WebView active = newWebView();
        WebView stale = newWebView();
        put("webView", active); put("preloadWebView", (Object) stale);
        put("spine", spine("only.xhtml")); put("currentSpine", 0); put("currentProgressPermille", 222);
        call("releaseSpeculativeEpubRenderers");
        assertTrue((Boolean) call("handleEpubRendererGone", new Class<?>[]{WebView.class}, stale));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertSame(active, get("webView"));
        assertEquals(0, get("currentSpine"));
        assertEquals(222, get("currentProgressPermille"));
    }

    @Test public void lowRamColdNavigationReusesOnlyActiveRenderer() throws Exception {
        android.app.ActivityManager am = (android.app.ActivityManager)
                activity.getSystemService(android.content.Context.ACTIVITY_SERVICE);
        Shadows.shadowOf(am).setMemoryClass(128);
        WebView active = newWebView(); WebView spare = newWebView(); WebView counter = newWebView();
        put("webView", active); put("preloadWebView", spare); put("wholeBookCounterWebView", counter);
        put("spine", spine("previous.xhtml", "next.xhtml")); put("currentSpine", 1);
        put("currentProgressPermille", 0);
        active.loadUrl("file:///previous.xhtml");
        call("loadCurrentEpubChapter");
        assertSame(active, get("webView"));
        assertNull(get("preloadWebView")); assertNull(get("wholeBookCounterWebView"));
        assertTrue(Shadows.shadowOf(active).getLastLoadedUrl().endsWith("next.xhtml"));
    }

    @Test public void retryRecreatesMissingActiveViewInsteadOfReturningForever() throws Exception {
        put("spine", spine("retry.xhtml")); put("currentSpine", 0);
        put("currentProgressPermille", 654); put("webView", null);
        call("loadCurrentEpubChapter");
        WebView replacement = (WebView) get("webView");
        assertNotNull(replacement);
        assertTrue(Shadows.shadowOf(replacement).getLastLoadedUrl().endsWith("retry.xhtml"));
        assertEquals(654, get("currentProgressPermille"));
    }

    @Test public void uiHiddenAloneIsNotALowMemoryWarning() throws Exception {
        WebView active = newWebView(); WebView spare = newWebView();
        put("webView", active); put("preloadWebView", spare);
        put("spine", spine("background.xhtml"));
        activity.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
        assertFalse((Boolean) get("epubMemoryPressure"));
        assertSame(active, get("webView"));
        assertSame(spare, get("preloadWebView"));
    }

    @Test public void previewMemoryReleaseDiscardsOnlyDisposableState() throws Exception {
        ReaderPagePreview preview = new ReaderPagePreview(activity, (FrameLayout) get("root"));
        WebView warm = newWebView();
        Field panelField = ReaderPagePreview.class.getDeclaredField("panel"); panelField.setAccessible(true);
        ((FrameLayout) panelField.get(preview)).addView(warm);
        Field viewField = ReaderPagePreview.class.getDeclaredField("epubWebView"); viewField.setAccessible(true);
        viewField.set(preview, warm);
        Field readyField = ReaderPagePreview.class.getDeclaredField("engineReady"); readyField.setAccessible(true);
        readyField.set(preview, true);
        File original = new File(activity.getCacheDir(), "preserved-original.xhtml");
        java.nio.file.Files.write(original.toPath(), "ORIGINAL BOOK CONTENT".getBytes(StandardCharsets.UTF_8));
        prefs.edit().putString("library_sentinel", "preserved").commit();
        preview.releaseMemory();
        assertNull(viewField.get(preview)); assertNull(warm.getParent());
        assertTrue(original.isFile());
        assertEquals("ORIGINAL BOOK CONTENT", new String(java.nio.file.Files.readAllBytes(original.toPath()), StandardCharsets.UTF_8));
        assertEquals("preserved", prefs.getString("library_sentinel", null));
        preview.destroy();
    }

    @Test @Config(sdk = 31)
    public void streamingLargeStoredArchiveRetainsSpineOrderAndFinalMarker() throws Exception {
        File epub = new File(activity.getCacheDir(), "large-stored.epub");
        writeLargeEpub(epub);
        assertTrue(epub.length() > 32L * 1024 * 1024);
        File extracted = new File(activity.getCacheDir(), "large-extracted");
        assertTrue(extracted.mkdirs());
        call("unzipEpub", new Class<?>[]{File.class, File.class}, epub, extracted);
        EpubUtil.BookInfo info = EpubUtil.parseExtracted(extracted);
        assertEquals(Arrays.asList("First", "Second"), info.chapterTitles);
        assertEquals(2, info.spine.size());
        assertEquals("chapter1.xhtml", info.spine.get(0).getName());
        assertEquals("chapter2.xhtml", info.spine.get(1).getName());
        EpubProgressModel model = EpubProgressModel.loadOrBuild(extracted, info.spine, info.chapterTitles);
        assertNotNull(model);
        assertTrue(model.overallFraction(1, 1000) > model.overallFraction(0, 0));
        assertEquals(1, model.positionForOverall(1000).spineIndex);
        try (java.io.RandomAccessFile media = new java.io.RandomAccessFile(new File(extracted, "media.bin"), "r")) {
            media.seek(media.length() - 1);
            assertEquals('Z', media.read());
        }
        assertTrue(info.spine.get(1).length() > 2L * 1024 * 1024);
        try (java.io.RandomAccessFile chapter = new java.io.RandomAccessFile(info.spine.get(1), "r")) {
            byte[] tail = new byte[100];
            chapter.seek(chapter.length() - tail.length); chapter.readFully(tail);
            assertTrue(new String(tail, StandardCharsets.UTF_8).contains("FINAL-CHAPTER-MARKER"));
        }
    }

    private WebView newWebView() throws Exception { return (WebView) call("createPreloadWebView"); }
    private List<File> spine(String... names) {
        List<File> out = new ArrayList<>();
        for (String name : names) out.add(new File(activity.getCacheDir(), name));
        return out;
    }
    private Object call(String name, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) types[i] = args[i] instanceof WebView ? WebView.class : args[i] instanceof File ? File.class : args[i].getClass();
        Method m = BookReaderActivity.class.getDeclaredMethod(name, types); m.setAccessible(true);
        return m.invoke(activity, args);
    }
    private Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = BookReaderActivity.class.getDeclaredMethod(name, types); m.setAccessible(true);
        return m.invoke(activity, args);
    }
    private Object get(String name) throws Exception { Field f = BookReaderActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
    private void put(String name, Object value) throws Exception { Field f = BookReaderActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(activity, value); }

    private static void writeLargeEpub(File file) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            entry(zip, "mimetype", "application/epub+zip".getBytes(StandardCharsets.UTF_8), ZipEntry.STORED);
            entry(zip, "META-INF/container.xml", ("<?xml version=\"1.0\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>").getBytes(StandardCharsets.UTF_8), ZipEntry.DEFLATED);
            entry(zip, "OEBPS/content.opf", ("<?xml version=\"1.0\"?><package xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"id\" version=\"2.0\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>Large fixture</dc:title></metadata><manifest><item id=\"c1\" href=\"chapter1.xhtml\" media-type=\"application/xhtml+xml\"/><item id=\"c2\" href=\"chapter2.xhtml\" media-type=\"application/xhtml+xml\"/><item id=\"media\" href=\"media.bin\" media-type=\"application/octet-stream\"/></manifest><spine><itemref idref=\"c1\"/><itemref idref=\"c2\"/></spine></package>").getBytes(StandardCharsets.UTF_8), ZipEntry.DEFLATED);
            entry(zip, "OEBPS/chapter1.xhtml", "<html><head><title>First</title></head><body>First</body></html>".getBytes(StandardCharsets.UTF_8), ZipEntry.DEFLATED);
            zip.putNextEntry(new ZipEntry("OEBPS/chapter2.xhtml"));
            zip.write("<html><head><title>Second</title></head><body><p>".getBytes(StandardCharsets.UTF_8));
            byte[] text = new byte[64 * 1024]; Arrays.fill(text, (byte) 'a');
            for (int i = 0; i < 32; i++) zip.write(text);
            zip.write("</p><p>FINAL-CHAPTER-MARKER</p></body></html>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            // STORED bytes make the archive itself >32MiB, without a huge test array.
            byte[] block = new byte[64 * 1024];
            int blocks = 33 * 1024 * 1024 / block.length;
            CRC32 crc = new CRC32(); Arrays.fill(block, (byte) 7);
            for (int i = 0; i < blocks; i++) {
                if (i == blocks - 1) block[block.length - 1] = 'Z';
                crc.update(block);
            }
            ZipEntry media = new ZipEntry("media.bin"); media.setMethod(ZipEntry.STORED);
            media.setSize(33L * 1024 * 1024); media.setCrc(crc.getValue());
            zip.putNextEntry(media); Arrays.fill(block, (byte) 7);
            for (int i = 0; i < blocks; i++) {
                if (i == blocks - 1) block[block.length - 1] = 'Z';
                zip.write(block);
            }
            zip.closeEntry();
        }
    }
    private static void entry(ZipOutputStream zip, String name, byte[] data, int method) throws IOException {
        ZipEntry e = new ZipEntry(name); e.setMethod(method); if (method == ZipEntry.STORED) { CRC32 crc = new CRC32(); crc.update(data); e.setSize(data.length); e.setCrc(crc.getValue()); } zip.putNextEntry(e); zip.write(data); zip.closeEntry();
    }
}
