package com.whisper.wowreader;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class ReaderResourceReadinessTest {
    @Test public void readinessPreservesFullImagesAndDoesNotForceDecodedBitmapCache() {
        String js = ReaderResourceReadinessScripts.pageReadyJs(0.0);
        assertTrue(js.contains("im.decoding='async'"));
        assertTrue(js.contains("im.decoding==='auto'"));
        assertFalse(js.contains(".decode("));
        assertFalse(js.contains(".src="));
        assertFalse(js.contains(".width="));
        assertFalse(js.contains(".height="));
        assertFalse(js.contains("loading="));
        assertFalse(js.contains("canvas"));
        assertFalse(js.contains("Promise.all"));
    }
    @Test public void resourceWaitsRemoveListenersAndInvalidateOldEpochs() {
        String js = ReaderResourceReadinessScripts.pageReadyJs(0.615);
        assertTrue(js.contains("st.cancelResources=cancelResources"));
        assertTrue(js.contains("styleEpoch===st.styleEpoch"));
        assertTrue(js.contains("!st.cancelled"));
        assertTrue(js.contains("removeEventListener('load'"));
        assertTrue(js.contains("removeEventListener('error'"));
        assertTrue(js.contains("clearTimeout(fontTimer)"));
        assertTrue(js.contains("clearTimeout(imageTimer)"));
        assertTrue(js.contains("st.measure(0.615)"));
    }
    @Test public void resourceMetricsRemainOptionalAndBounded() {
        String js = ReaderResourceReadinessScripts.pageReadyJs(0);
        assertTrue(js.contains("typeof WoW.onChapterStage==='function'"));
        assertTrue(js.contains("d>=0&&d<=120000"));
        assertTrue(js.contains("resourceStage('fonts_wait'"));
        assertTrue(js.contains("resourceStage('images_wait'"));
        assertTrue(js.contains("setTimeout(measureOnce,900)"));
        assertTrue(js.contains("setTimeout(startResources,1200)"));
    }
    @Test public void exportActualHelperForBrowserContractTest() throws Exception {
        Path dir = java.nio.file.Paths.get("build/reader-engine-fixtures");
        Files.createDirectories(dir);
        Files.write(dir.resolve("resource-ready.js"), ReaderResourceReadinessScripts.pageReadyJs(0.0).getBytes(StandardCharsets.UTF_8));
    }
}
