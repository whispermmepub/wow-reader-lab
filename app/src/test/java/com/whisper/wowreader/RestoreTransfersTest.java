package com.whisper.wowreader;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class RestoreTransfersTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void largeLibraryOverlapsExactlyThreeTransfersButInstallsSeriallyInOrder() throws Exception {
        AtomicInteger active = new AtomicInteger(), peak = new AtomicInteger();
        CountDownLatch firstBatch = new CountDownLatch(3);
        Thread caller = Thread.currentThread();
        List<Integer> order = new ArrayList<>();
        List<RestoreTransfers.Task> tasks = new ArrayList<>();
        for (int i = 0; i < 1003; i++) {
            final int index = i;
            tasks.add(new RestoreTransfers.Task() {
                public boolean download(File scratch) throws Exception {
                    int count = active.incrementAndGet();
                    peak.accumulateAndGet(count, Math::max);
                    try {
                        if (index < 3) {
                            firstBatch.countDown();
                            assertTrue("Downloads must overlap", firstBatch.await(5, TimeUnit.SECONDS));
                        }
                        Files.write(scratch.toPath(), ("book-" + index).getBytes("UTF-8"));
                        return true;
                    } finally { active.decrementAndGet(); }
                }
                public boolean install(File scratch) throws Exception {
                    assertSame(caller, Thread.currentThread());
                    assertEquals("book-" + index, new String(Files.readAllBytes(scratch.toPath()), "UTF-8"));
                    order.add(index);
                    return true;
                }
            });
        }
        assertEquals(1003, RestoreTransfers.run(temporary.getRoot(), tasks));
        assertEquals(3, peak.get());
        for (int i = 0; i < 1003; i++) assertEquals(i, (int) order.get(i));
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test public void failedDownloadJoinsOtherWritersAndNeverInstallsFailedBatch() throws Exception {
        AtomicInteger finished = new AtomicInteger(), installed = new AtomicInteger();
        List<RestoreTransfers.Task> tasks = new ArrayList<>();
        CountDownLatch started = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            final int index = i;
            tasks.add(new RestoreTransfers.Task() {
                public boolean download(File scratch) throws Exception {
                    started.countDown();
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                    try {
                        if (index == 0) throw new java.io.IOException("network failure");
                        Thread.sleep(30);
                        Files.write(scratch.toPath(), new byte[]{1, 2, 3});
                        return true;
                    } finally { finished.incrementAndGet(); }
                }
                public boolean install(File scratch) { installed.incrementAndGet(); return true; }
            });
        }
        try { RestoreTransfers.run(temporary.getRoot(), tasks); fail("failure expected"); }
        catch (java.io.IOException expected) { assertEquals("network failure", expected.getMessage()); }
        assertEquals(3, finished.get());
        assertEquals(0, installed.get());
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test public void deletedAndInvalidObjectsAreNotInstalledOrCounted() throws Exception {
        AtomicInteger installs = new AtomicInteger();
        RestoreTransfers.Task task = new RestoreTransfers.Task() {
            public boolean download(File scratch) { return false; }
            public boolean install(File scratch) { installs.incrementAndGet(); return true; }
        };
        assertEquals(0, RestoreTransfers.run(temporary.getRoot(), Collections.nCopies(7, task)));
        assertEquals(0, installs.get());
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test public void installFailureCleansEveryPrefetchedFileAndStopsLaterBatches() throws Exception {
        AtomicInteger downloads = new AtomicInteger(), installs = new AtomicInteger();
        RestoreTransfers.Task task = new RestoreTransfers.Task() {
            public boolean download(File scratch) throws Exception {
                downloads.incrementAndGet(); Files.write(scratch.toPath(), new byte[]{1}); return true;
            }
            public boolean install(File scratch) throws Exception { installs.incrementAndGet(); throw new java.io.IOException("integrity"); }
        };
        assertThrows(java.io.IOException.class, () -> RestoreTransfers.run(temporary.getRoot(), Collections.nCopies(30, task)));
        assertEquals(3, downloads.get()); assertEquals(1, installs.get());
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test public void rejectedCommitDoesNotCountAsRestored() throws Exception {
        RestoreTransfers.Task task = new RestoreTransfers.Task() {
            public boolean download(File scratch) { return true; }
            public boolean install(File scratch) { return false; }
        };
        assertEquals(0, RestoreTransfers.run(temporary.getRoot(), Collections.singletonList(task)));
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test public void emptyLibraryCreatesNoScratch() throws Exception {
        assertEquals(0, RestoreTransfers.run(temporary.getRoot(), Collections.emptyList()));
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test public void progressReportsEachCompletedTransferAndKnownTotal() throws Exception {
        List<RestoreTransfers.Task> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) tasks.add(new RestoreTransfers.Task() {
            public boolean download(File scratch) throws Exception { Files.write(scratch.toPath(), new byte[]{1}); return true; }
            public boolean install(File scratch) { return true; }
        });
        List<String> updates = new ArrayList<>();
        assertEquals(4, RestoreTransfers.run(temporary.getRoot(), tasks,
                (completed, total) -> updates.add(completed + "/" + total)));
        assertEquals(java.util.Arrays.asList("1/4", "2/4", "3/4", "4/4"), updates);
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test public void cancelledBatchJoinsWritersAndDoesNotInstallOrStartNextBatch() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        AtomicInteger downloaded = new AtomicInteger(), installed = new AtomicInteger();
        RestoreTransfers.Task task = new RestoreTransfers.Task() {
            public boolean download(File scratch) throws Exception {
                Files.write(scratch.toPath(), new byte[]{1});
                downloaded.incrementAndGet(); cancelled.set(true); return true;
            }
            public boolean install(File scratch) { installed.incrementAndGet(); return true; }
        };
        assertThrows(java.io.InterruptedIOException.class, () -> RestoreTransfers.run(temporary.getRoot(),
                Collections.nCopies(10, task), null,
                () -> { if (cancelled.get()) throw new java.io.InterruptedIOException("cancelled"); }));
        assertEquals(3, downloaded.get());assertEquals(0, installed.get());
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test public void interruptedCallerJoinsWorkersAndCleansScratchBeforeReturning() throws Exception {
        RestoreTransfers.Task task = new RestoreTransfers.Task() {
            public boolean download(File scratch) throws Exception { Thread.sleep(50); return true; }
            public boolean install(File scratch) { fail("interrupted restore must not install"); return true; }
        };
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> RestoreTransfers.run(temporary.getRoot(), Collections.singletonList(task)));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(0, temporary.getRoot().list().length);
        } finally { Thread.interrupted(); }
    }
}
