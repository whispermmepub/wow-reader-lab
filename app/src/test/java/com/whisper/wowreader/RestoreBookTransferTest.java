package com.whisper.wowreader;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class RestoreBookTransferTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private boolean deleted;
    private boolean tombstone;
    private final AtomicInteger downloads = new AtomicInteger(), binds = new AtomicInteger();
    private final DriveDeletionGuard.Remote remote = new DriveDeletionGuard.Remote() {
        public boolean hasMarker(String hash) { return deleted; }
        public void publishMarker(String hash) { fail("restore must not publish"); }
        public void deleteAssets(String hash) { fail("restore must not delete cloud assets"); }
    };
    private final DriveDeletionGuard.Local local = new DriveDeletionGuard.Local() {
        public boolean hasTombstone(String hash) { return tombstone; }
        public void rememberDeletion(String hash) { tombstone = true; }
    };
    private File library() throws Exception { File d = new File(temporary.getRoot(), "library"); d.mkdirs(); return d; }
    private File cache() throws Exception { File d = new File(temporary.getRoot(), "cache"); d.mkdirs(); return d; }
    private File source() throws Exception {
        File file = new File(temporary.getRoot(), "cloud.epub");
        Files.write(file.toPath(), "verified book contents".getBytes("UTF-8")); return file;
    }
    private RestoreBookTransfer task(File source, String hash, RestoreBookTransfer.Download download) throws Exception {
        File dir = library();
        return new RestoreBookTransfer(hash, remote, local,
                () -> BookRestoreFiles.destination(dir, "My Book.epub", hash, "epub"),
                scratch -> { downloads.incrementAndGet(); download.run(scratch); },
                target -> { assertTrue(target.isFile()); assertEquals(hash, FileIdentityUtil.sha256(target)); binds.incrementAndGet(); });
    }
    private RestoreBookTransfer task(File source, String hash) throws Exception {
        return task(source, hash, scratch -> Files.copy(source.toPath(), scratch.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING));
    }

    @Test public void firstRestoreInstallsExactBytesAndRepeatAvoidsMediaDownload() throws Exception {
        File source = source(); String hash = FileIdentityUtil.sha256(source);
        assertEquals(1, RestoreTransfers.run(cache(), Collections.singletonList(task(source, hash))));
        assertEquals(0, RestoreTransfers.run(cache(), Collections.singletonList(task(source, hash))));
        assertEquals(1, downloads.get()); assertEquals(2, binds.get());
        assertArrayEquals(Files.readAllBytes(source.toPath()), Files.readAllBytes(new File(library(), "My Book.epub").toPath()));
        assertEquals(0, cache().list().length);
    }

    @Test public void corruptAndEmptyTransfersNeverCreateLibraryRows() throws Exception {
        File source = source(); String hash = FileIdentityUtil.sha256(source);
        for (byte[] bytes : new byte[][]{new byte[]{4, 5}, new byte[0]}) {
            RestoreBookTransfer task = task(source, hash, scratch -> Files.write(scratch.toPath(), bytes));
            assertThrows(java.io.IOException.class, () -> RestoreTransfers.run(cache(), Collections.singletonList(task)));
            assertEquals(0, binds.get()); assertEquals(0, library().list().length); assertEquals(0, cache().list().length);
        }
    }

    @Test public void deleteBeforeTransferSkipsMediaAndBinding() throws Exception {
        File source = source(); deleted = true;
        assertEquals(0, RestoreTransfers.run(cache(), Collections.singletonList(task(source, FileIdentityUtil.sha256(source)))));
        assertEquals(0, downloads.get()); assertEquals(0, binds.get()); assertTrue(tombstone);
    }

    @Test public void deleteDuringDownloadSuppressesInstallation() throws Exception {
        File source = source(); String hash = FileIdentityUtil.sha256(source);
        RestoreBookTransfer task = task(source, hash, scratch -> {
            Files.copy(source.toPath(), scratch.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); deleted = true;
        });
        assertEquals(0, RestoreTransfers.run(cache(), Collections.singletonList(task)));
        assertEquals(0, binds.get()); assertTrue(tombstone); assertEquals(0, library().list().length);
    }

    @Test public void changedLocalMatchIsNotBoundAsCloudBook() throws Exception {
        File source = source(); String hash = FileIdentityUtil.sha256(source);
        File original = new File(library(), "My Book.epub");
        Files.copy(source.toPath(), original.toPath());
        RestoreBookTransfer task = task(source, hash);
        File scratch = File.createTempFile("test-", ".pending", cache());
        assertTrue(task.download(scratch)); assertEquals(0, downloads.get());
        Files.write(original.toPath(), "different local book".getBytes("UTF-8"));
        assertTrue(task.install(scratch));
        assertEquals("different local book", new String(Files.readAllBytes(original.toPath()), "UTF-8"));
        assertEquals(1, downloads.get()); assertEquals(1, binds.get()); scratch.delete();
    }

    @Test public void differentLocalBookWithSameNameSurvivesRestore() throws Exception {
        File source = source(); String hash = FileIdentityUtil.sha256(source);
        File original = new File(library(), "My Book.epub"); Files.write(original.toPath(), new byte[]{7, 8, 9});
        assertEquals(1, RestoreTransfers.run(cache(), Collections.singletonList(task(source, hash))));
        assertArrayEquals(new byte[]{7, 8, 9}, Files.readAllBytes(original.toPath()));
        assertTrue(new File(library(), hash + ".epub").isFile()); assertEquals(1, binds.get());
    }

    @Test public void duplicateObjectsReuseVerifiedLocalFileAndPreserveOnlyOneCopy() throws Exception {
        File source = source(); String hash = FileIdentityUtil.sha256(source);
        java.util.List<RestoreTransfers.Task> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) tasks.add(task(source, hash));
        RestoreTransfers.run(cache(), tasks);
        assertEquals(1, library().list().length); assertEquals(7, binds.get());
        // Only the first bounded batch can prefetch duplicates before a verified copy exists.
        assertTrue(downloads.get() <= RestoreTransfers.MAX_PARALLEL);
    }
}
