package com.whisper.wowreader;

import java.io.File;

public final class ReaderLocationPolicyTest {
    private static void ok(boolean v, String m) { if (!v) throw new AssertionError(m); }
    public static void main(String[] args) {
        File a = new File("/books/a.epub");
        File b = new File("/books/b.epub");
        ok(!ReaderLocationPolicy.bookId(a).equals(ReaderLocationPolicy.bookId(b)), "distinct paths need distinct ids");
        ok(ReaderLocationPolicy.clampSpine(-2, 4) == 0, "negative spine clamps");
        ok(ReaderLocationPolicy.clampSpine(99, 4) == 3, "large spine clamps");
        ok(ReaderLocationPolicy.clampPermille(1200) == 1000, "progress clamps");
        ok("3:1000".equals(ReaderLocationPolicy.anchor(3, 1200)), "anchor is bounded");
        System.out.println("READER_LOCATION_POLICY_TEST_PASS");
    }
}
