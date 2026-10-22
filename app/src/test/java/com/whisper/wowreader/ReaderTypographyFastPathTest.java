package com.whisper.wowreader;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReaderTypographyFastPathTest {
    @Test public void pristinePublisherAvoidsPerNodeMutationButCustomAlwaysNormalizes() {
        String js = ReaderTypographyCss.normalizationJs();
        assertTrue(js.contains("var normalizeProse=!original||st.readerTypographyCustomized||flow.querySelector("));
        assertTrue(js.contains("if(normalizeProse){var prose="));
        assertTrue(js.contains("st.readerTypographyCustomized=!original"));
    }
    @Test public void existingOverrideSentinelsStillForceRestorationWithoutDirtyFlag() {
        String js = ReaderTypographyCss.normalizationJs();
        for (String field : new String[]{"font-family", "font-weight", "font-size", "line-height", "text-align"})
            assertTrue(js.contains("[data-wow-original-" + field + "]"));
        for (String marker : new String[]{"style-absent", "class-absent", "lang-added"})
            assertTrue(js.contains("[data-wow-" + marker + "]"));
        assertTrue(js.contains(".wow-reader-prose"));
        assertTrue(js.contains(".wow-mm-smart"));
        assertTrue(js.contains(".wow-rhythm-indent"));
    }
    @Test public void restorationStillPreservesPublisherInlineStyleAndAbsenceMetadata() {
        String js = ReaderTypographyCss.normalizationJs();
        assertTrue(js.contains("n.getAttribute(pri)||''"));
        assertTrue(js.contains("pn.removeAttribute('class')"));
        assertTrue(js.contains("n.removeAttribute('style')"));
        assertTrue(js.contains("pn.removeAttribute('lang')"));
        assertTrue(js.contains("pn.closest("));
    }
}
