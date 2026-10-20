package com.whisper.wowreader;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BookRestoreFilesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void legacyPreferredCollisionPreservesExistingAndUsesContentName() throws Exception {
        File root = temporary.newFolder("library");
        File source = write(temporary.getRoot(), "cloud.epub", "cloud content");
        String hash = FileIdentityUtil.sha256(source);
        File preferred = write(root, "My Book.epub", "local content");
        File chosen = BookRestoreFiles.destination(root, "My Book.epub", hash, "epub");
        assertNotEquals(preferred.getCanonicalPath(), chosen.getCanonicalPath());
        assertEquals(new File(root, hash + ".epub").getCanonicalPath(), chosen.getCanonicalPath());
        BookRestoreFiles.install(source, chosen, hash);
        assertArrayEquals("local content".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(preferred.toPath()));
        assertArrayEquals("cloud content".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(chosen.toPath()));
        assertArrayEquals("cloud content".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(source.toPath()));
    }

    @Test public void occupiedContentAddressedFallbackChoosesDifferentSuffix() throws Exception {
        File root = temporary.newFolder("library");
        File source = write(temporary.getRoot(), "cloud.pdf", "new cloud pdf");
        String hash = FileIdentityUtil.sha256(source);
        File occupied = write(root, hash + ".pdf", "different bytes");
        File chosen = BookRestoreFiles.destination(root, hash + ".pdf", hash, "pdf");
        assertEquals(new File(root, hash + "-cloud-1.pdf").getCanonicalPath(), chosen.getCanonicalPath());
        BookRestoreFiles.install(source, chosen, hash);
        assertArrayEquals("different bytes".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(occupied.toPath()));
        assertArrayEquals("new cloud pdf".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(chosen.toPath()));
    }

    @Test public void existingSameHashIsReusedUnchangedAndSourceRemains() throws Exception {
        File root = temporary.newFolder("library");
        File source = write(temporary.getRoot(), "source.epub", "same bytes");
        String hash = FileIdentityUtil.sha256(source);
        File target = write(root, hash + ".epub", "same bytes");
        byte[] targetBefore = Files.readAllBytes(target.toPath());
        BookRestoreFiles.install(source, target, hash);
        assertArrayEquals(targetBefore, Files.readAllBytes(target.toPath()));
        assertArrayEquals("same bytes".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(source.toPath()));
    }

    @Test public void installRejectsBadSourceHashAndLeavesSourceAndTargetUntouched() throws Exception {
        File root = temporary.newFolder("library");
        File source = write(temporary.getRoot(), "source.epub", "actual bytes");
        File target = new File(root, "target.epub");
        try {
            BookRestoreFiles.install(source, target, repeat('a', 64));
            fail("expected source integrity rejection");
        } catch (IOException expected) { }
        assertTrue(source.isFile());
        assertFalse(target.exists());
        File[] pending = root.listFiles((dir, name) -> name.startsWith(".wow-restore-") && name.endsWith(".pending"));
        assertEquals(0, pending == null ? 0 : pending.length);
    }

    @Test public void installNeverOverwritesDifferentExistingTarget() throws Exception {
        File root = temporary.newFolder("library");
        File source = write(temporary.getRoot(), "source.pdf", "cloud bytes");
        String hash = FileIdentityUtil.sha256(source);
        File target = write(root, "target.pdf", "valuable local bytes");
        byte[] before = Files.readAllBytes(target.toPath());
        try {
            BookRestoreFiles.install(source, target, hash);
            fail("expected collision rejection");
        } catch (IOException expected) { }
        assertArrayEquals(before, Files.readAllBytes(target.toPath()));
        assertArrayEquals("cloud bytes".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(source.toPath()));
    }

    @Test public void destinationRejectsInvalidHashAndExtension() throws Exception {
        File root = temporary.newFolder("library");
        try { BookRestoreFiles.destination(root, "book.epub", "not-a-hash", "epub"); fail("bad hash accepted"); }
        catch (IOException expected) { }
        String validHash = repeat('b', 64);
        try { BookRestoreFiles.destination(root, "book.txt", validHash, "txt"); fail("bad extension accepted"); }
        catch (IOException expected) { }
    }

    @Test public void traversalPreferredNameCannotEscapeLibrary() throws Exception {
        File root = temporary.newFolder("library");
        String hash = repeat('c', 64);
        File chosen = BookRestoreFiles.destination(root, "../../outside.epub", hash, "epub");
        assertEquals(root.getCanonicalPath(), chosen.getCanonicalFile().getParentFile().getCanonicalPath());
        assertFalse(chosen.getCanonicalPath().contains("outside/"));
    }

    @Test public void symlinkPreferredNameCannotEscapeLibrary() throws Exception {
        File root = temporary.newFolder("library");
        File outside = temporary.newFolder("outside");
        File link = new File(root, "preferred.epub");
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath());
        } catch (UnsupportedOperationException e) {
            return;
        }
        String hash = repeat('d', 64);
        File chosen = BookRestoreFiles.destination(root, "preferred.epub", hash, "epub");
        assertEquals(root.getCanonicalPath(), chosen.getCanonicalFile().getParentFile().getCanonicalPath());
        assertFalse(chosen.getCanonicalPath().equals(new File(outside, "preferred.epub").getCanonicalPath()));
    }

    @Test public void validNormalInstallWritesExactBytesAndKeepsSource() throws Exception {
        File root = temporary.newFolder("library");
        File source = write(temporary.getRoot(), "normal.epub", "exact restored bytes");
        String hash = FileIdentityUtil.sha256(source);
        File target = new File(root, "normal.epub");
        BookRestoreFiles.install(source, target, hash);
        assertArrayEquals(Files.readAllBytes(source.toPath()), Files.readAllBytes(target.toPath()));
        assertTrue(source.exists());
        assertEquals(hash, FileIdentityUtil.sha256(target));
    }

    private static File write(File parent, String name, String contents) throws Exception {
        File file = new File(parent, name);
        Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String repeat(char c, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(c);
        return result.toString();
    }
}
