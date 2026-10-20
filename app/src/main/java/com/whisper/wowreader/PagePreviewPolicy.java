package com.whisper.wowreader;

import java.util.Locale;

/** Pure policy calculations used by ReaderPagePreview and safe to test without an Activity. */
public final class PagePreviewPolicy {
    private PagePreviewPolicy() { }

    public static final int MIN_PERMILLE = 0;
    public static final int MAX_PERMILLE = 1000;
    public static final int MAX_CACHE_ENTRIES = 4;
    /** Bounded source-file limit; large chapters are supported without retaining a book. */
    public static final int MAX_CHAPTER_BYTES = 8 * 1024 * 1024;
    /** Separate conservative bound for UTF-16 sanitising/staging overhead. */
    public static final int MAX_SANITIZED_CHARS = 10 * 1024 * 1024;
    public static final long FIRST_PREVIEW_DISPATCH_MS = 16L;

    /** Uniformly shrink the preferred panel to the usable area; never overflow it. */
    public static int[] fitPanel(int availableWidth, int availableHeight, int preferredWidth, int preferredHeight) {
        if (availableWidth <= 0 || availableHeight <= 0 || preferredWidth <= 0 || preferredHeight <= 0)
            return new int[] {0, 0};
        double scale = Math.min(1d, Math.min(availableWidth / (double) preferredWidth,
                availableHeight / (double) preferredHeight));
        return new int[] {Math.max(1, Math.min(availableWidth, (int) Math.floor(preferredWidth * scale))),
                Math.max(1, Math.min(availableHeight, (int) Math.floor(preferredHeight * scale)))};
    }

    public static int bottomMarginAboveAnchor(int rootHeight, int anchorTop, int gapPx) {
        if (rootHeight <= 0) return 0;
        return Math.max(0, rootHeight - Math.max(0, anchorTop) + Math.max(0, gapPx));
    }

    public static int clampPermille(int value) {
        return Math.max(MIN_PERMILLE, Math.min(MAX_PERMILLE, value));
    }

    public static float fraction(int permille) {
        return clampPermille(permille) / 1000f;
    }

    public static int pdfPage(int pageZeroBased, int pageCount) {
        if (pageCount <= 0) return 0;
        return Math.max(0, Math.min(pageCount - 1, pageZeroBased));
    }

    public static int chapterPermilleForFraction(float fraction) {
        if (Float.isNaN(fraction) || Float.isInfinite(fraction)) return 0;
        return clampPermille(Math.round(Math.max(0f, Math.min(1f, fraction)) * 1000f));
    }

    public static boolean safeChapterByteLength(long length) {
        return length >= 0L && length <= MAX_CHAPTER_BYTES;
    }

    public static boolean safeSanitizedLength(int length) {
        return length >= 0 && length <= MAX_SANITIZED_CHARS;
    }

    public static boolean validPdfTarget(int pageZeroBased, int pageCount) {
        return pageCount > 0 && pageZeroBased >= 0 && pageZeroBased < pageCount;
    }

    public static boolean validEpubTarget(int chapterIndex, int chapterCount, int chapterPermille) {
        return chapterCount > 0 && chapterIndex >= 0 && chapterIndex < chapterCount
                && chapterPermille >= MIN_PERMILLE && chapterPermille <= MAX_PERMILLE;
    }

    /** Request identity comparison for drag coalescing; only the newest request may render/display. */
    public static boolean isNewer(long candidateGeneration, long displayedGeneration) {
        return candidateGeneration > displayedGeneration;
    }

    public static String percentLabel(int permille) {
        return String.format(Locale.US, "%d%%", Math.round(clampPermille(permille) / 10f));
    }
}
