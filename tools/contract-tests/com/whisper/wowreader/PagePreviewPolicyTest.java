package com.whisper.wowreader;

/** Plain-Java contract test; run by tools/run-contract-tests.sh or directly with javac/java. */
public final class PagePreviewPolicyTest {
    public static void main(String[] args) {
        assertEquals(0, PagePreviewPolicy.clampPermille(-5), "negative permille clamps");
        assertEquals(1000, PagePreviewPolicy.clampPermille(2000), "large permille clamps");
        assertNear(0f, PagePreviewPolicy.fraction(0), "fraction start");
        assertNear(1f, PagePreviewPolicy.fraction(1000), "fraction end");
        assertEquals(0, PagePreviewPolicy.pdfPage(-3, 12), "pdf first bound");
        assertEquals(11, PagePreviewPolicy.pdfPage(99, 12), "pdf last bound");
        assertEquals(4, PagePreviewPolicy.pdfPage(4, 12), "pdf interior mapping");
        assertEquals(0, PagePreviewPolicy.chapterPermilleForFraction(Float.NaN), "nan is safe");
        assertEquals(1000, PagePreviewPolicy.chapterPermilleForFraction(2f), "fraction upper bound");
        assertEquals(0, PagePreviewPolicy.chapterPermilleForFraction(-1f), "fraction lower bound");
        assertTrue(PagePreviewPolicy.validPdfTarget(0, 1), "valid pdf target");
        assertTrue(!PagePreviewPolicy.validPdfTarget(1, 1), "invalid pdf target");
        assertTrue(PagePreviewPolicy.validEpubTarget(1, 3, 500), "valid epub target");
        assertTrue(!PagePreviewPolicy.validEpubTarget(3, 3, 500), "invalid epub chapter");
        assertTrue(PagePreviewPolicy.isNewer(8, 7), "new request wins");
        assertTrue(!PagePreviewPolicy.isNewer(7, 7), "same request coalesces");
        assertTrue(!PagePreviewPolicy.isNewer(6, 7), "stale request discarded");
        assertTrue(PagePreviewPolicy.safeChapterByteLength(2L * 1024 * 1024 + 1), "legacy 2 MiB cap is removed");
        assertTrue(!PagePreviewPolicy.safeChapterByteLength((long) PagePreviewPolicy.MAX_CHAPTER_BYTES + 1), "hard chapter bound remains");
        assertTrue(PagePreviewPolicy.safeSanitizedLength(PagePreviewPolicy.MAX_SANITIZED_CHARS), "sanitized bound inclusive");
        assertTrue(PagePreviewPolicy.FIRST_PREVIEW_DISPATCH_MS < 70L, "first uncached request is not needlessly delayed");
        assertEquals(208, PagePreviewPolicy.bottomMarginAboveAnchor(1000, 800, 8), "preview sits above anchor");
        assertEquals(0, PagePreviewPolicy.bottomMarginAboveAnchor(0, 800, 8), "unlaid root is safe");
        int[] large = PagePreviewPolicy.fitPanel(336, 480, 288, 408);
        assertEquals(288, large[0], "larger preferred width");
        assertEquals(408, large[1], "larger preferred height");
        assertEquals(0, PagePreviewPolicy.fitPanel(0, 300, 288, 408)[0], "no space is safe");
        for (int density=1; density<=4; density++) {
            for (int width=1; width<=1000; width+=13) for (int height=1; height<=1000; height+=17) {
                int[] size=PagePreviewPolicy.fitPanel(width*density,height*density,288*density,408*density);
                assertTrue(size[0]>0 && size[0]<=width*density && size[0]<=288*density, "width always fits");
                assertTrue(size[1]>0 && size[1]<=height*density && size[1]<=408*density, "height always fits");
                assertTrue(Math.abs(size[0]*408d/288-size[1])<2.5, "page aspect ratio within pixel rounding");
            }
        }
        System.out.println("PAGE_PREVIEW_POLICY_TEST_PASS");
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
    private static void assertNear(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > .0001f) throw new AssertionError(message);
    }
    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
