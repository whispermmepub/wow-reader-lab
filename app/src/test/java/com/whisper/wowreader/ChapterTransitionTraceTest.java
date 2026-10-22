package com.whisper.wowreader;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChapterTransitionTraceTest {
    @Test public void tracksRequestedTimeAndOneCopyOfEachStage() {
        ChapterTransitionTrace trace = new ChapterTransitionTrace();
        trace.begin(3, 100, 130, "warm_ready");
        assertTrue(trace.mark(3, "document_finished", 140));
        assertFalse(trace.mark(3, "document_finished", 180));
        assertTrue(trace.mark(3, "visual_commit", 220));
        assertTrue(trace.duration(3, "image_decode", 12.7));
        assertEquals("generation=3 route=warm_ready document_finished_at_ms=40 visual_commit_at_ms=120 image_decode_ms=13", trace.summary(3));
    }
    @Test public void staleAndUntrustedStagesCannotContaminateActiveTrace() {
        ChapterTransitionTrace trace = new ChapterTransitionTrace();
        trace.begin(2, 0, 50, "file:///private/book.epub");
        assertFalse(trace.mark(1, "visual_commit", 99));
        assertFalse(trace.mark(2, "private book title", 99));
        assertFalse(trace.duration(2, "file:///secret", 20));
        assertEquals("", trace.summary(1));
        assertEquals("generation=2 route=cold", trace.summary(2));
    }
    @Test public void invalidDurationsAndDuplicatesAreBounded() {
        ChapterTransitionTrace trace = new ChapterTransitionTrace();
        trace.begin(1, 0, 50, "warm_inflight");
        for (double d : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, 120001})
            assertFalse(trace.duration(1, "page_scan", d));
        assertTrue(trace.duration(1, "page_scan", 120000));
        assertFalse(trace.duration(1, "page_scan", 12));
        assertTrue(trace.mark(1, "engine_usable", 49));
        assertTrue(trace.summary(1).contains("engine_usable_at_ms=0"));
    }
    @Test public void newGenerationClearsOldMapAndDurations() {
        ChapterTransitionTrace trace = new ChapterTransitionTrace();
        trace.begin(1, 10, 10, "warm_ready");
        trace.mark(1, "visual_commit", 55);
        trace.duration(1, "fonts_wait", 21);
        trace.begin(2, 500, 20, "cold");
        assertFalse(trace.duration(1, "fonts_wait", 44));
        assertTrue(trace.mark(2, "map_complete", 40));
        assertEquals("generation=2 route=cold map_complete_at_ms=20", trace.summary(2));
    }
}
