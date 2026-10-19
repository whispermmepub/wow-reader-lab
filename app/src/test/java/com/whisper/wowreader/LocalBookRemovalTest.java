package com.whisper.wowreader;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LocalBookRemovalTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void healthyRemovalStagesBeforeRemovingRowAndDeletesOriginal() throws Exception {
        File library = temporary.newFolder("library");
        File book = write(library, "book.epub", "original bytes");
        AtomicBoolean rowCalled = new AtomicBoolean();
        boolean result = LocalBookRemoval.remove(book, library, () -> {
            rowCalled.set(true);
            assertFalse("the source must already be staged", book.exists());
            File[] pending = library.listFiles((dir, name) -> name.endsWith(".pending"));
            assertEquals(1, pending.length);
            assertArrayEquals("original bytes".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(pending[0].toPath()));
            return true;
        });
        assertTrue(result);
        assertTrue(rowCalled.get());
        assertFalse(book.exists());
        assertEquals(0, library.listFiles().length);
    }

    @Test public void missingFileStillRemovesIndexedRow() throws Exception {
        File library = temporary.newFolder("library");
        File missing = new File(library, "missing.pdf");
        AtomicInteger calls = new AtomicInteger();
        assertTrue(LocalBookRemoval.remove(missing, library, () -> { calls.incrementAndGet(); return true; }));
        assertEquals(1, calls.get());
    }

    @Test public void falseRowRemovalRestoresExactOriginalBytes() throws Exception {
        File library = temporary.newFolder("library");
        File book = write(library, "book.pdf", "bytes that must return");
        byte[] before = Files.readAllBytes(book.toPath());
        assertFalse(LocalBookRemoval.remove(book, library, () -> false));
        assertTrue(book.isFile());
        assertArrayEquals(before, Files.readAllBytes(book.toPath()));
        assertEquals(1, library.listFiles().length);
    }

    @Test public void throwingRowRemovalRestoresExactOriginalBytes() throws Exception {
        File library = temporary.newFolder("library");
        File book = write(library, "book.epub", "rollback bytes");
        byte[] before = Files.readAllBytes(book.toPath());
        try {
            LocalBookRemoval.remove(book, library, () -> { throw new IOException("database unavailable"); });
            fail("expected callback exception");
        } catch (IOException expected) {
            assertEquals("database unavailable", expected.getMessage());
        }
        assertTrue(book.isFile());
        assertArrayEquals(before, Files.readAllBytes(book.toPath()));
        assertEquals(1, library.listFiles().length);
    }

    @Test public void externalFileAndSymlinkEscapeAreRejectedWithoutCallbackOrDeletion() throws Exception {
        File library = temporary.newFolder("library");
        File outside = temporary.newFile("outside.epub");
        Files.write(outside.toPath(), "outside".getBytes(StandardCharsets.UTF_8));
        AtomicInteger calls = new AtomicInteger();
        try {
            LocalBookRemoval.remove(outside, library, () -> { calls.incrementAndGet(); return true; });
            fail("expected external path rejection");
        } catch (IOException expected) { }
        assertEquals(0, calls.get());
        assertTrue(outside.exists());

        File link = new File(library, "linked.epub");
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath());
        } catch (UnsupportedOperationException e) {
            return;
        }
        try {
            LocalBookRemoval.remove(link, library, () -> { calls.incrementAndGet(); return true; });
            fail("expected symlink escape rejection");
        } catch (IOException expected) { }
        assertEquals(0, calls.get());
        assertTrue(outside.exists());
        assertTrue(Files.isSymbolicLink(link.toPath()));
    }

    @Test public void directoryIsRejectedWithoutCallback() throws Exception {
        File library = temporary.newFolder("library");
        File directory = new File(library, "not-a-book.epub");
        assertTrue(directory.mkdir());
        AtomicBoolean called = new AtomicBoolean();
        try {
            LocalBookRemoval.remove(directory, library, () -> { called.set(true); return true; });
            fail("expected directory rejection");
        } catch (IOException expected) { }
        assertFalse(called.get());
        assertTrue(directory.isDirectory());
    }

    @Test public void callbackFalseRetainsFileWhenBytesContainBinaryData() throws Exception {
        File library = temporary.newFolder("library");
        File book = library.toPath().resolve("binary.epub").toFile();
        byte[] bytes = {0, 1, 2, (byte) 0xff, 9, 0, 42};
        Files.write(book.toPath(), bytes);
        assertFalse(LocalBookRemoval.remove(book, library, () -> false));
        assertArrayEquals(bytes, Files.readAllBytes(book.toPath()));
    }

    private static File write(File parent, String name, String contents) throws Exception {
        File file = new File(parent, name);
        Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
