package com.whisper.wowreader;

import java.util.Locale;

final class DriveObjectNamer {
    private DriveObjectNamer() {}
    static String bookName(String hash, String format) {
        String h = hash == null ? "" : hash.trim().toLowerCase(Locale.ROOT);
        String f = "pdf".equalsIgnoreCase(format) ? "pdf" : "epub";
        return "wow_book_" + h + "." + f;
    }
    static String coverName(String hash) {
        String h = hash == null ? "" : hash.trim().toLowerCase(Locale.ROOT);
        return "wow_cover_" + h + ".jpg";
    }
}
