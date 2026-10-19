package com.whisper.wowreader;

/** Pure sizing/resource policy for the continuous PDF reader. */
final class PdfRenderPolicy {
    static final int MIN_PAGE_WIDTH_PX = 320;
    static final int MAX_PAGE_WIDTH_PX = 1440;
    static final int MAX_BITMAP_PIXELS = 4_000_000;
    static final int MAX_BITMAP_DIMENSION_PX = 4096;
    static final int RENDER_QUEUE_CAPACITY = 8;
    static final int TEXT_CACHE_ENTRIES = 8;

    private PdfRenderPolicy() {}

    static int pageWidth(int viewportWidthPx, int fallbackWidthPx) {
        int width = viewportWidthPx > 0 ? viewportWidthPx : fallbackWidthPx;
        return clamp(width, MIN_PAGE_WIDTH_PX, MAX_PAGE_WIDTH_PX);
    }

    static int pageHeight(int pageWidthPx, float sourceWidth, float sourceHeight) {
        int width = clamp(pageWidthPx, MIN_PAGE_WIDTH_PX, MAX_PAGE_WIDTH_PX);
        float ratio = sourceWidth > 0f && sourceHeight > 0f ? sourceHeight / sourceWidth : 1.4142f;
        if (Float.isNaN(ratio) || Float.isInfinite(ratio) || ratio <= 0f) ratio = 1.4142f;
        long height = Math.round(width * (double) ratio);
        long byPixels = Math.max(1L, MAX_BITMAP_PIXELS / (long) width);
        height = Math.min(height, Math.min(byPixels, MAX_BITMAP_DIMENSION_PX));
        return (int) Math.max(1L, height);
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
