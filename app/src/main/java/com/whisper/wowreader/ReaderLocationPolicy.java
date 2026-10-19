package com.whisper.wowreader;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Pure policy for collision-resistant reader state keys and bounded anchors. */
final class ReaderLocationPolicy {
    private ReaderLocationPolicy() {}
    static String bookId(File book) {
        if (book == null) return "unknown";
        // Filename + size survives cache relocation and timestamp changes. Legacy name keys
        // are still mirrored by the activity, while this avoids 32-bit hash collisions.
        String raw = book.getName() + ":" + book.length();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte b : digest) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (Exception ignored) { return Integer.toHexString(raw.hashCode()); }
    }
    /** Computes a stable identity from file bytes; callers must run this off the UI thread. */
    static String contentSha256(File book) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        try (FileInputStream in = new FileInputStream(book)) {
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder out = new StringBuilder(64);
        for (byte b : digest.digest()) out.append(String.format(Locale.US, "%02x", b & 0xff));
        return out.toString();
    }
    static String keyForId(String prefix, String id) { return prefix + (id == null ? "unknown" : id); }
    static String key(String prefix, File book) { return prefix + bookId(book); }
    static boolean preferLegacyLocation(boolean hasContentLocation, long contentUpdatedMs, long legacyUpdatedMs) {
        return !hasContentLocation || legacyUpdatedMs > contentUpdatedMs;
    }
    static int clampSpine(int value, int count) { return count <= 0 ? 0 : Math.max(0, Math.min(count - 1, value)); }
    static int clampPermille(int value) { return Math.max(0, Math.min(1000, value)); }
    static String anchor(int spine, int permille) {
        return String.format(Locale.US, "%d:%d", Math.max(0, spine), clampPermille(permille));
    }
}
