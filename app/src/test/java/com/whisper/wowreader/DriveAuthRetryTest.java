package com.whisper.wowreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/** Plain JVM tests for the account-scoped Drive authorization retry policy. */
public class DriveAuthRetryTest {
    @Test
    public void successfulRequestDoesNotRefresh() throws Exception {
        AtomicInteger refreshes = new AtomicInteger();
        DriveAuthRetry auth = new DriveAuthRetry("old", rejected -> {
            refreshes.incrementAndGet();
            return "new";
        });

        String result = auth.execute(() -> "ok");

        assertEquals("ok", result);
        assertEquals(0, refreshes.get());
        assertEquals("old", auth.token());
    }

    @Test
    public void unauthorizedRefreshesAndReplaysExactlyOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        DriveAuthRetry auth = new DriveAuthRetry("old", rejected -> {
            assertEquals("old", rejected);
            refreshes.incrementAndGet();
            return "new";
        });

        String result = auth.execute(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new DriveAuthRetry.Unauthorized("old");
            }
            return "recovered";
        });

        assertEquals("recovered", result);
        assertEquals(2, calls.get());
        assertEquals(1, refreshes.get());
        assertEquals("new", auth.token());
    }

    @Test
    public void secondUnauthorizedStopsWithoutAnotherRefreshOrReplay() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        DriveAuthRetry auth = new DriveAuthRetry("old", rejected -> {
            refreshes.incrementAndGet();
            return "new";
        });

        try {
            auth.execute(() -> {
                calls.incrementAndGet();
                throw new DriveAuthRetry.Unauthorized(calls.get() == 1 ? "old" : "new");
            });
            fail("expected second unauthorized response");
        } catch (DriveAuthRetry.Unauthorized expected) {
            assertEquals("new", expected.rejectedToken);
        }

        assertEquals(2, calls.get());
        assertEquals(1, refreshes.get());
        assertEquals("new", auth.token());
    }

    @Test
    public void nonUnauthorizedFailureIsNotRefreshedOrReplayed() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        RuntimeException failure = new RuntimeException("network");
        DriveAuthRetry auth = new DriveAuthRetry("old", rejected -> {
            refreshes.incrementAndGet();
            return "new";
        });

        try {
            auth.execute(() -> {
                calls.incrementAndGet();
                throw failure;
            });
            fail("expected original failure");
        } catch (RuntimeException actual) {
            assertSame(failure, actual);
        }

        assertEquals(1, calls.get());
        assertEquals(0, refreshes.get());
        assertEquals("old", auth.token());
    }

    @Test
    public void nullRefreshedTokenIsRejectedWithoutReplay() throws Exception {
        assertInvalidRefreshedToken(null);
    }

    @Test
    public void blankRefreshedTokenIsRejectedWithoutReplay() throws Exception {
        assertInvalidRefreshedToken(" \t\n");
    }

    @Test
    public void sameRefreshedTokenIsRejectedWithoutReplay() throws Exception {
        assertInvalidRefreshedToken("old");
    }

    private void assertInvalidRefreshedToken(String refreshedToken) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        DriveAuthRetry auth = new DriveAuthRetry("old", rejected -> {
            refreshes.incrementAndGet();
            return refreshedToken;
        });

        try {
            auth.execute(() -> {
                calls.incrementAndGet();
                throw new DriveAuthRetry.Unauthorized("old");
            });
            fail("expected invalid refreshed token to stop execution");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("reconnect"));
        }

        assertEquals(1, calls.get());
        assertEquals(1, refreshes.get());
        assertEquals("old", auth.token());
    }

    @Test
    public void refreshExceptionStopsWithoutReplay() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        Exception failure = new Exception("refresh failed");
        DriveAuthRetry auth = new DriveAuthRetry("old", rejected -> {
            refreshes.incrementAndGet();
            throw failure;
        });

        try {
            auth.execute(() -> {
                calls.incrementAndGet();
                throw new DriveAuthRetry.Unauthorized("old");
            });
            fail("expected refresh failure");
        } catch (Exception actual) {
            assertSame(failure, actual);
        }

        assertEquals(1, calls.get());
        assertEquals(1, refreshes.get());
        assertEquals("old", auth.token());
    }

    @Test
    public void interruptedUnauthorizedRequestDoesNotRefreshOrReplay() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        DriveAuthRetry auth = new DriveAuthRetry("old", rejected -> {
            refreshes.incrementAndGet();
            return "new";
        });
        Thread.currentThread().interrupt();
        try {
            auth.execute(() -> {
                calls.incrementAndGet();
                throw new DriveAuthRetry.Unauthorized("old");
            });
            fail("expected interruption");
        } catch (InterruptedException expected) {
            // The policy intentionally converts the interrupted request to a checked stop.
        } finally {
            // Do not leak this test's interrupt status into the JUnit runner.
            Thread.interrupted();
        }

        assertEquals(1, calls.get());
        assertEquals(0, refreshes.get());
        assertEquals("old", auth.token());
    }

    @Test
    public void concurrentWorkersRejectedWithOldTokenShareOneRefresh() throws Exception {
        final int workers = 8;
        AtomicInteger refreshes = new AtomicInteger();
        CountDownLatch firstAttempts = new CountDownLatch(workers);
        CountDownLatch releaseFirstAttempts = new CountDownLatch(1);
        DriveAuthRetry auth = new DriveAuthRetry("old", rejected -> {
            assertEquals("old", rejected);
            refreshes.incrementAndGet();
            return "new";
        });
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> auth.execute(new DriveAuthRetry.Request<String>() {
                    private boolean first = true;

                    @Override
                    public String run() throws Exception {
                        if (first) {
                            first = false;
                            firstAttempts.countDown();
                            if (!releaseFirstAttempts.await(5, TimeUnit.SECONDS)) {
                                throw new AssertionError("timed out releasing initial requests");
                            }
                            throw new DriveAuthRetry.Unauthorized("old");
                        }
                        return auth.token();
                    }
                })));
            }
            assertTrue("workers did not reach their first request", firstAttempts.await(5, TimeUnit.SECONDS));
            releaseFirstAttempts.countDown();

            for (Future<String> future : futures) {
                assertEquals("new", future.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, refreshes.get());
            assertEquals("new", auth.token());
        } finally {
            releaseFirstAttempts.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void replayAfterRefreshObservesNewToken() throws Exception {
        List<String> tokensUsed = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger calls = new AtomicInteger();
        DriveAuthRetry auth = new DriveAuthRetry("old", rejected -> "new");

        String result = auth.execute(() -> {
            tokensUsed.add(auth.token());
            if (calls.getAndIncrement() == 0) {
                throw new DriveAuthRetry.Unauthorized("old");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, calls.get());
        assertEquals(List.of("old", "new"), tokensUsed);
    }
}
