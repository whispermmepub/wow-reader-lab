package com.whisper.wowreader;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/** Contract coverage for v80 pagination scheduling without changing reader layout semantics. */
public class ReaderPaginationSchedulingTest {
    @Test public void collectorUsesBoundedSlicesAndAvoidsEverySliceSort() {
        String js = ReaderPaginationScripts.pageMapJs();
        assertTrue(js.contains("count<768"));
        assertTrue(js.contains("Date.now()-started>=10"));
        assertTrue(js.contains("partialSerial===1||partialSerial%3===0"));
        assertTrue(js.contains("dirty?result():cached"));
        assertTrue(js.contains("st.cancelled||epoch!==st.measureEpoch"));
    }

    @Test public void collectorStillMeasuresAllMediaAndDoesNotDownsampleOrCrop() {
        String js = ReaderPaginationScripts.pageMapJs();
        assertTrue(js.contains("IMG|svg|SVG|VIDEO|AUDIO|OBJECT|EMBED|TABLE|math|MATH|CANVAS|HR"));
        assertTrue(js.contains("getBoundingClientRect()"));
        assertFalse(js.contains("naturalWidth"));
        assertFalse(js.contains("decode=\"none\""));
        assertFalse(js.contains("object-fit:cover"));
        assertFalse(js.contains("scaleDown"));
    }

    @Test public void progressiveEngineStillSeparatesPartialUsabilityFromAuthoritativeReady() {
        String js = ReaderProgressivePaginationScripts.measureAndTurnJs(0);
        assertTrue(js.contains("st.paginationComplete=false"));
        assertTrue(js.contains("st.report()"));
        assertTrue(js.contains("WoW.onPageReady"));
        assertTrue(js.contains("!st.paginationComplete"));
        assertTrue(js.contains("st.cancelCollect()"));
        assertTrue(js.contains("WoW.onChapterStage"));
        assertTrue(js.contains("stage('layout_prepare'"));
        assertTrue(js.contains("stage('page_scan'"));
        assertTrue(js.contains("d<=120000"));
    }
}
