package com.whisper.wowreader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure selection rules for the bounded Home Continue reading strip. */
final class HomeContinuePolicy {
    static final int MAX_HOME_BOOKS = 6;

    static final class Candidate {
        final String fileName;
        final String filePath;
        final int progress;
        final boolean filePresent;

        Candidate(String fileName, String filePath, int progress, boolean filePresent) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.progress = progress;
            this.filePresent = filePresent;
        }
    }

    private HomeContinuePolicy() {}

    static List<Candidate> select(List<Candidate> rows, int limit) {
        int max = Math.min(MAX_HOME_BOOKS, Math.max(0, limit));
        List<Candidate> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (rows == null) return out;
        for (Candidate row : rows) {
            if (row == null || !row.filePresent || row.fileName == null || row.fileName.isEmpty()) continue;
            // 100% is the finished state; 0% is intentionally allowed for a book just opened.
            if (row.progress < 0 || row.progress >= 100 || !seen.add(row.fileName)) continue;
            out.add(row);
            if (out.size() == max) break;
        }
        return out;
    }
}
