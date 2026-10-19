package com.whisper.wowreader;

/** Plain Java policy checks; no Android test runner is required. */
public final class HomeContinuePolicyTest {
    private static int tests;
    private static void check(boolean value, String message) {
        tests++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        java.util.List<HomeContinuePolicy.Candidate> rows = new java.util.ArrayList<>();
        rows.add(new HomeContinuePolicy.Candidate("just-opened.epub", "/tmp/just-opened.epub", 0, true));
        rows.add(new HomeContinuePolicy.Candidate("finished.epub", "/tmp/finished.epub", 100, true));
        rows.add(new HomeContinuePolicy.Candidate("missing.epub", "/tmp/missing.epub", 20, false));
        rows.add(new HomeContinuePolicy.Candidate("just-opened.epub", "/tmp/duplicate.epub", 0, true));
        for (int i = 0; i < 10; i++)
            rows.add(new HomeContinuePolicy.Candidate("book-" + i + ".epub", "/tmp/book-" + i, 10, true));

        java.util.List<HomeContinuePolicy.Candidate> selected = HomeContinuePolicy.select(rows, 99);
        check(selected.size() == HomeContinuePolicy.MAX_HOME_BOOKS, "Home is bounded to six cards");
        check("just-opened.epub".equals(selected.get(0).fileName), "just-opened 0% book is retained");
        check(!contains(selected, "finished.epub"), "finished books are excluded");
        check(!contains(selected, "missing.epub"), "missing files are excluded");
        check(count(selected, "just-opened.epub") == 1, "duplicate rows are removed");
        System.out.println("HOME_CONTINUE_POLICY_TEST_PASS " + tests);
    }

    private static boolean contains(java.util.List<HomeContinuePolicy.Candidate> rows, String name) {
        return count(rows, name) > 0;
    }
    private static int count(java.util.List<HomeContinuePolicy.Candidate> rows, String name) {
        int count = 0;
        for (HomeContinuePolicy.Candidate row : rows) if (name.equals(row.fileName)) count++;
        return count;
    }
}
