package com.whisper.wowreader;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Detects imported book types without trusting a single document-provider metadata field.
 * Some OEM/cloud file pickers return application/octet-stream or omit the filename extension.
 */
final class BookImportTypeDetector {
    private BookImportTypeDetector() {}

    static String resolveExtension(Context context, Uri uri, String displayName, String mimeHint) {
        String name = displayName == null ? "" : displayName.trim().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return ".pdf";
        if (name.endsWith(".epub")) return ".epub";

        String mime = mimeHint == null ? "" : mimeHint.trim().toLowerCase(Locale.ROOT);
        if (mime.contains("pdf")) return ".pdf";
        if (mime.contains("epub")) return ".epub";

        if (context == null || uri == null) return null;
        if (looksLikePdf(context, uri)) return ".pdf";
        if (looksLikeEpub(context, uri)) return ".epub";
        return null;
    }

    private static boolean looksLikePdf(Context context, Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return false;
            byte[] header = new byte[5];
            int read = 0;
            while (read < header.length) {
                int n = in.read(header, read, header.length - read);
                if (n < 0) break;
                read += n;
            }
            return read == 5 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' &&
                    header[3] == 'F' && header[4] == '-';
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean looksLikeEpub(Context context, Uri uri) {
        try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
            if (raw == null) return false;
            try (ZipInputStream zip = new ZipInputStream(raw)) {
                boolean hasContainer = false;
                int inspected = 0;
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null && inspected++ < 80) {
                    String entryName = entry.getName() == null ? "" : entry.getName().replace('\\', '/');
                    if ("mimetype".equals(entryName)) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buffer = new byte[128];
                        int total = 0;
                        int n;
                        while (total < 256 && (n = zip.read(buffer, 0, Math.min(buffer.length, 256 - total))) > 0) {
                            out.write(buffer, 0, n);
                            total += n;
                        }
                        String value = new String(out.toByteArray(), StandardCharsets.US_ASCII).trim();
                        if ("application/epub+zip".equalsIgnoreCase(value)) return true;
                    } else if ("META-INF/container.xml".equalsIgnoreCase(entryName)) {
                        hasContainer = true;
                    }
                    zip.closeEntry();
                }
                // A few real-world EPUBs omit/mangle the mimetype entry but still contain
                // the EPUB container descriptor. This is specific enough for an imported book.
                return hasContainer;
            }
        } catch (Exception ignored) {
            return false;
        }
    }
}
