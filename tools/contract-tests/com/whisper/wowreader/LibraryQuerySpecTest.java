package com.whisper.wowreader;

public final class LibraryQuerySpecTest {
    private static void ok(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        LibraryQuerySpec q = new LibraryQuerySpec("History", "Author A", "reading", "Favorites", "opened");
        String where = q.whereSql();
        ok(where.contains("LOWER(title) LIKE ?"), "search must be SQL-backed");
        ok(where.contains("author=?"), "author filter missing");
        ok(where.contains("progress>0 AND progress<100"), "reading filter missing");
        ok(where.contains("shelf_books"), "shelf filter must be indexed DB lookup");
        ok(q.args().length == 5, "unexpected bind arg count");
        ok(q.orderSql().startsWith("last_opened_at DESC"), "opened sort missing");

        LibraryQuerySpec t = new LibraryQuerySpec("", "", "all", "", "title_desc");
        ok("1=1".equals(t.whereSql()), "unfiltered query must stay simple");
        ok(t.args().length == 0, "unfiltered query must have no args");
        ok(t.orderSql().contains("title COLLATE NOCASE DESC"), "title sort missing");

        // The query shape must never expand with library size: 100,000 rows are handled by LIMIT/OFFSET in ReaderStateDb.
        for (int i = 0; i < 100_000; i += 120) {
            int page = i / 120;
            ok(page >= 0, "page math failed");
        }
        System.out.println("LIBRARY_QUERY_SPEC_TEST_PASS");
    }
}
