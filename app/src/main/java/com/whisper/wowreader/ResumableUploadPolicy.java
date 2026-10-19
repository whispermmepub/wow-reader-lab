package com.whisper.wowreader;

/** Server acknowledgements, not sent bytes, determine resumable upload progress. */
final class ResumableUploadPolicy {
    static final int MAX_STALLED_ACKS = 3;
    private ResumableUploadPolicy() {}
    static long acknowledgedOffset(String range, long total) {
        if (total < 0L) throw new IllegalArgumentException("Invalid upload length");
        if (range == null || range.trim().isEmpty()) return 0L;
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("(?i)^bytes=0-([0-9]+)$").matcher(range.trim());
        if (!match.matches()) throw new IllegalArgumentException("Invalid upload acknowledgement");
        try {
            long end = Long.parseLong(match.group(1));
            if (end < 0L || end >= total) throw new IllegalArgumentException("Upload acknowledgement exceeds file size");
            return end + 1L;
        } catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid upload acknowledgement", invalid); }
    }
    static int stalledAcknowledgements(long previous, long acknowledged, int stalled) {
        return acknowledged > previous ? 0 : stalled + 1;
    }
}
