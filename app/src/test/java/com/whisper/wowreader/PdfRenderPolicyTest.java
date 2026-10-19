package com.whisper.wowreader;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PdfRenderPolicyTest {
    @Test public void viewportWidthIsConservativelyBounded() {
        assertEquals(PdfRenderPolicy.MIN_PAGE_WIDTH_PX, PdfRenderPolicy.pageWidth(10, 10));
        assertEquals(800, PdfRenderPolicy.pageWidth(800, 1200));
        assertEquals(PdfRenderPolicy.MAX_PAGE_WIDTH_PX, PdfRenderPolicy.pageWidth(5000, 1200));
    }

    @Test public void extremePageAspectCannotCreateOversizedBitmap() {
        int width = PdfRenderPolicy.pageWidth(1200, 1200);
        int height = PdfRenderPolicy.pageHeight(width, 1f, 100f);
        assertTrue(height <= PdfRenderPolicy.MAX_BITMAP_DIMENSION_PX);
        assertTrue((long) width * height <= PdfRenderPolicy.MAX_BITMAP_PIXELS);
    }

    @Test public void invalidPageSizeUsesSafeDefaultAspect() {
        int height = PdfRenderPolicy.pageHeight(800, 0f, 0f);
        assertEquals(Math.round(800f * 1.4142f), height);
    }
}
