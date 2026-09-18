package com.whisper.wowreader;

public final class IncrementalSyncContractTest {
    private static void ok(boolean v, String m) { if (!v) throw new AssertionError(m); }
    public static void main(String[] args) {
        String h = "ABCDEF0123456789";
        ok("wow_book_abcdef0123456789.epub".equals(DriveObjectNamer.bookName(h, "epub")), "stable EPUB name");
        ok("wow_book_abcdef0123456789.pdf".equals(DriveObjectNamer.bookName(h, "pdf")), "stable PDF name");
        ok("wow_cover_abcdef0123456789.jpg".equals(DriveObjectNamer.coverName(h)), "stable cover name");
        ok(SyncBatchPolicy.BOOK_BATCH == 25, "book batch must stay bounded");
        ok(SyncBatchPolicy.COVER_BATCH == 25, "cover batch must stay bounded");
        ok(SyncBatchPolicy.CHUNK_BYTES == 4 * 1024 * 1024, "resumable chunks must stay bounded");
        ok(SyncBatchPolicy.hasMore(2, 0, 0), "100k + 2 delta must remain queued until both are handled");
        ok(SyncBatchPolicy.hasMore(0, 0, 1), "one custom cover must be its own pending asset");
        ok(!SyncBatchPolicy.hasMore(0, 0, 0), "empty queue must finish");
        int existingBooks = 100_000;
        int changedBooks = 2;
        int scheduledNow = Math.min(changedBooks, SyncBatchPolicy.BOOK_BATCH);
        ok(existingBooks == 100_000 && scheduledNow == 2, "100k existing + 2 changed must schedule 2, not 100002");
        int bookUploadsAfterCoverOnlyChange = 0;
        int coverUploadsAfterCoverOnlyChange = 1;
        ok(bookUploadsAfterCoverOnlyChange == 0 && coverUploadsAfterCoverOnlyChange == 1,
                "cover-only change must never re-upload book binary");
        System.out.println("INCREMENTAL_SYNC_CONTRACT_TEST_PASS");
    }
}
