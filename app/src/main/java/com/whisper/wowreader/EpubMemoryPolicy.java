package com.whisper.wowreader;

/** Resource scheduling only: these thresholds never reject a book or truncate content. */
final class EpubMemoryPolicy {
    private EpubMemoryPolicy() {}

    static boolean serializeRenderers(long archiveBytes, long largestChapterBytes,
                                      boolean lowRam, boolean memoryPressure) {
        // Compressed size alone is not a memory estimate. A single expanded XHTML
        // can be expensive even in a small ZIP, and OEMs need not flag low-RAM devices.
        return lowRam || memoryPressure || archiveBytes >= 8L * 1024 * 1024
                || largestChapterBytes >= 1024L * 1024;
    }
}
