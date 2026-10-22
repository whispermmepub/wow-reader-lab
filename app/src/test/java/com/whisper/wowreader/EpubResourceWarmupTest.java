package com.whisper.wowreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class EpubResourceWarmupTest {
    @Test public void warmsOnlyTheRequestedChapterAndItsImagesWithinBudget() throws Exception {
        Path root = Files.createTempDirectory("epub-warmup");
        Path next = Files.createDirectories(root.resolve("Text")).resolve("next.xhtml");
        Path previous = root.resolve("Text/previous.xhtml");
        Path image = Files.createDirectories(root.resolve("Images")).resolve("cover.png");
        Files.write(next, ("<html><img src=\"../Images/cover.png\"><img src=\"missing.jpg\"></html>")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(previous, "<img src=\"../Images/cover.png\">".getBytes(StandardCharsets.UTF_8));
        byte[] original = new byte[8192];
        for (int i = 0; i < original.length; i++) original[i] = (byte) i;
        Files.write(image, original);

        EpubResourceWarmup.Result result = EpubResourceWarmup.warm(root.toFile(), next.toFile(),
                4096, () -> false);
        assertEquals(4096, result.totalBytes);
        assertTrue(result.chapterBytes > 0);
        assertTrue(result.imageBytes > 0);
        assertEquals(2, result.warmedResourceCount);
        assertFalse(result.getWarmedFiles().contains(previous.toFile().getCanonicalFile()));
        assertEquals(8192, Files.readAllBytes(image).length);
        assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(image)));
    }

    @Test public void percentEncodedSpacesAndSvgHrefsAreResolved() throws Exception {
        Path root = Files.createTempDirectory("epub-warmup-space");
        Path chapter = root.resolve("chapter.xhtml");
        Path image = root.resolve("my image.svg");
        Files.write(chapter, "<svg><image xlink:href=\"my%20image.svg\"/></svg>"
                .getBytes(StandardCharsets.UTF_8));
        Files.write(image, new byte[] {1, 2, 3});
        EpubResourceWarmup.Result result = EpubResourceWarmup.warm(root.toFile(), chapter.toFile(),
                100, () -> false);
        assertEquals(2, result.warmedResourceCount);
        assertEquals(3, result.imageBytes);
    }

    @Test public void externalTraversalAndSymlinkTargetsAreNotRead() throws Exception {
        Path root = Files.createTempDirectory("epub-warmup-safe");
        Path outside = Files.createTempFile("epub-warmup-outside", ".png");
        Files.write(outside, new byte[32]);
        Path chapter = root.resolve("chapter.xhtml");
        Files.write(chapter, ("<img src=\"file:///tmp/x.png\"><img src=\"../"
                + outside.getFileName() + "\"><img src=\"link.png\">").getBytes(StandardCharsets.UTF_8));
        try {
            Files.createSymbolicLink(root.resolve("link.png"), outside);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException ignored) {
            // The traversal and external-URI assertions remain useful on restricted hosts.
        }
        EpubResourceWarmup.Result result = EpubResourceWarmup.warm(root.toFile(), chapter.toFile(),
                1024, () -> false);
        assertEquals(1, result.warmedResourceCount);
        assertEquals(0, result.imageBytes);
    }

    @Test public void cancellationStopsBeforeStaleResourceReads() throws Exception {
        Path root = Files.createTempDirectory("epub-warmup-cancel");
        Path chapter = root.resolve("chapter.xhtml");
        Path image = root.resolve("image.jpg");
        Files.write(chapter, "<img src=\"image.jpg\">".getBytes(StandardCharsets.UTF_8));
        Files.write(image, new byte[1024 * 1024]);
        AtomicInteger checks = new AtomicInteger();
        EpubResourceWarmup.Result result = EpubResourceWarmup.warm(root.toFile(), chapter.toFile(),
                2 * 1024 * 1024, () -> checks.incrementAndGet() > 2);
        assertTrue(result.cancelled);
        assertTrue(result.totalBytes <= EpubResourceWarmup.DEFAULT_CHAPTER_SCAN_BYTES);
    }

    @Test public void hugeChapterIsPrefixScannedButBudgetIsTheOnlyReadCap() throws Exception {
        Path root = Files.createTempDirectory("epub-warmup-huge");
        Path chapter = root.resolve("chapter.xhtml");
        byte[] bytes = new byte[512 * 1024];
        String prefix = "<img src=\"none.jpg\">";
        System.arraycopy(prefix.getBytes(StandardCharsets.UTF_8), 0, bytes, 0, prefix.length());
        Files.write(chapter, bytes);
        EpubResourceWarmup.Result result = EpubResourceWarmup.warm(root.toFile(), chapter.toFile(),
                300 * 1024, () -> false);
        assertEquals(EpubResourceWarmup.DEFAULT_CHAPTER_SCAN_BYTES, result.chapterBytes);
    }
}
