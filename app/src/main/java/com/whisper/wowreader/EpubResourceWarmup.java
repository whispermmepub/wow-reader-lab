package com.whisper.wowreader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small, deliberately boring page-cache warmer for one EPUB chapter.
 *
 * <p>This class does not parse a book, decode an image, or retain resource bytes. The caller
 * chooses the direction by passing the requested chapter; this helper never looks up siblings.</p>
 */
public final class EpubResourceWarmup {
    public static final int DEFAULT_CHAPTER_SCAN_BYTES = 256 * 1024;
    public static final int DEFAULT_MAX_BYTES = 4 * 1024 * 1024;
    public static final int LOW_MEMORY_MAX_BYTES = 1024 * 1024;
    private static final int BUFFER_BYTES = 16 * 1024;

    private static final Pattern IMAGE_TAG = Pattern.compile(
            "<\\s*(?:img|image|use|source)\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "\\b(?:src|href|xlink:href)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>();

    static {
        Collections.addAll(IMAGE_EXTENSIONS, "jpg", "jpeg", "png", "gif", "webp", "avif", "svg");
    }

    private EpubResourceWarmup() { }

    /** Reads at most the requested budget. A negative budget is treated as zero. */
    public static Result warm(File epubPublicationRoot, File chapter, int budgetBytes,
                              BooleanSupplier cancelled) {
        BooleanSupplier stop = cancelled == null ? () -> false : cancelled;
        int budget = Math.min(DEFAULT_MAX_BYTES, Math.max(0, budgetBytes));
        ResultBuilder result = new ResultBuilder();
        if (isCancelled(stop) || epubPublicationRoot == null || chapter == null || budget == 0) {
            result.cancelled = isCancelled(stop);
            return result.build();
        }

        final File root;
        final File chapterFile;
        try {
            root = epubPublicationRoot.getCanonicalFile();
            chapterFile = chapter.getCanonicalFile();
        } catch (IOException ignored) {
            return result.build();
        }
        if (!isInside(root, root) || !isInside(root, chapterFile) || !chapterFile.isFile()) {
            return result.build();
        }

        // The prefix is both the XHTML warmup and the only text retained for discovery.
        int scanBytes = Math.min(DEFAULT_CHAPTER_SCAN_BYTES, budget);
        ReadResult chapterRead = readAtMost(chapterFile, scanBytes, stop, true);
        result.chapterBytes = chapterRead.bytes;
        result.usedBytes += chapterRead.bytes;
        if (chapterRead.bytes > 0) result.add(chapterFile);
        if (chapterRead.cancelled) {
            result.cancelled = true;
            return result.build();
        }

        String text = new String(chapterRead.data, StandardCharsets.UTF_8);
        Matcher tags = IMAGE_TAG.matcher(text);
        Set<String> seen = new HashSet<>();
        while (tags.find() && result.usedBytes < budget) {
            if (isCancelled(stop)) {
                result.cancelled = true;
                break;
            }
            Matcher attrs = ATTRIBUTE.matcher(tags.group());
            while (attrs.find() && result.usedBytes < budget) {
                String reference = attrs.group(1) != null ? attrs.group(1)
                        : attrs.group(2) != null ? attrs.group(2) : attrs.group(3);
                File image = resolveLocalImage(root, chapterFile.getParentFile(), reference);
                if (image == null || !seen.add(image.getPath())) continue;
                int remaining = budget - result.usedBytes;
                ReadResult imageRead = readAtMost(image, remaining, stop, false);
                result.imageBytes += imageRead.bytes;
                result.usedBytes += imageRead.bytes;
                if (imageRead.bytes > 0) result.add(image);
                if (imageRead.cancelled) {
                    result.cancelled = true;
                    break;
                }
            }
        }
        if (!result.cancelled && isCancelled(stop)) result.cancelled = true;
        return result.build();
    }

    private static ReadResult readAtMost(File file, int limit, BooleanSupplier cancelled,
                                         boolean retainForDiscovery) {
        if (limit <= 0 || isCancelled(cancelled)) return new ReadResult(new byte[0], 0, true);
        ByteArrayOutputStream discovered = retainForDiscovery
                ? new ByteArrayOutputStream(Math.min(limit, DEFAULT_CHAPTER_SCAN_BYTES)) : null;
        byte[] buffer = new byte[Math.min(BUFFER_BYTES, limit)];
        int total = 0;
        try (InputStream input = new FileInputStream(file)) {
            while (total < limit) {
                if (isCancelled(cancelled)) return new ReadResult(
                        discovered == null ? new byte[0] : discovered.toByteArray(), total, true);
                int wanted = Math.min(buffer.length, limit - total);
                int read = input.read(buffer, 0, wanted);
                if (read < 0) break;
                if (read == 0) continue;
                if (discovered != null) discovered.write(buffer, 0, read);
                total += read;
            }
        } catch (IOException ignored) {
            // A missing/unreadable image is not allowed to abort warming the chapter.
        }
        return new ReadResult(discovered == null ? new byte[0] : discovered.toByteArray(), total,
                isCancelled(cancelled));
    }

    private static File resolveLocalImage(File root, File chapterParent, String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String reference = decodeBasicEntities(raw.trim());
        if (reference.isEmpty() || reference.indexOf('?') >= 0) {
            // Fragments are harmless, but skipping them avoids accidentally changing URI semantics
            // for malformed EPUB references. Query-bearing resources are deliberately unsupported.
            return null;
        }
        try {
            int fragment = reference.indexOf('#');
            if (fragment >= 0) reference = reference.substring(0, fragment);
            URI uri = new URI(reference);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawQuery() != null) return null;
            String path = uri.getPath();
            if (path == null || path.isEmpty() || path.indexOf('\0') >= 0 || !isImagePath(path)) return null;
            File candidate = new File(path.startsWith("/") ? root : chapterParent, path);
            File canonical = candidate.getCanonicalFile();
            if (!isInside(root, canonical) || !canonical.isFile()) return null;
            return canonical;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isImagePath(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return false;
        return IMAGE_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static String decodeBasicEntities(String value) {
        return value.replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&#39;", "'").replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static boolean isInside(File root, File candidate) {
        String rootPath = root.getPath();
        String candidatePath = candidate.getPath();
        return candidatePath.equals(rootPath)
                || candidatePath.startsWith(rootPath + File.separator);
    }

    private static boolean isCancelled(BooleanSupplier cancellation) {
        try { return cancellation.getAsBoolean(); } catch (RuntimeException ignored) { return true; }
    }

    public static final class Result {
        public final int chapterBytes;
        public final int imageBytes;
        public final int totalBytes;
        public final int warmedResourceCount;
        public final boolean cancelled;
        private final List<File> warmedFiles;

        private Result(ResultBuilder b) {
            chapterBytes = b.chapterBytes; imageBytes = b.imageBytes; totalBytes = b.usedBytes;
            warmedResourceCount = b.files.size(); cancelled = b.cancelled;
            warmedFiles = Collections.unmodifiableList(new ArrayList<>(b.files));
        }
        public int getChapterBytes() { return chapterBytes; }
        public int getImageBytes() { return imageBytes; }
        public int getTotalBytes() { return totalBytes; }
        public int getWarmedResourceCount() { return warmedResourceCount; }
        public boolean isCancelled() { return cancelled; }
        public List<File> getWarmedFiles() { return warmedFiles; }
    }

    private static final class ResultBuilder {
        int chapterBytes, imageBytes, usedBytes;
        boolean cancelled;
        final List<File> files = new ArrayList<>();
        void add(File file) { files.add(file); }
        Result build() { return new Result(this); }
    }

    private static final class ReadResult {
        final byte[] data; final int bytes; final boolean cancelled;
        ReadResult(byte[] data, int bytes, boolean cancelled) {
            this.data = data; this.bytes = bytes; this.cancelled = cancelled;
        }
    }
}
