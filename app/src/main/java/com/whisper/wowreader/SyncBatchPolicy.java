package com.whisper.wowreader;

final class SyncBatchPolicy {
    private SyncBatchPolicy() {}
    static final int BOOK_BATCH = 25;
    static final int DELETE_BATCH = 25;
    static final int COVER_BATCH = 25;
    static final int CHUNK_BYTES = 4 * 1024 * 1024;
    static boolean hasMore(int pendingBooks, int pendingDeletes, int pendingCovers) { return pendingBooks + pendingDeletes + pendingCovers > 0; }
}
