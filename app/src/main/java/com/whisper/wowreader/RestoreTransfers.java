package com.whisper.wowreader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Bounded download batches; only the calling thread installs files or mutates library metadata. */
final class RestoreTransfers {
    static final int MAX_PARALLEL = 3;

    interface Task {
        // False means a concurrent deletion suppressed this object before transfer.
        boolean download(File scratch) throws Exception;
        boolean install(File scratch) throws Exception;
    }

    interface Progress { void onComplete(int completed, int total); }
    interface Cancellation { void check() throws Exception; }
    static int run(File cache, List<Task> tasks) throws Exception {
        return run(cache, tasks, null);
    }
    static int run(File cache, List<Task> tasks, Progress progress) throws Exception {
        return run(cache, tasks, progress, () -> {});
    }
    static int run(File cache, List<Task> tasks, Progress progress, Cancellation cancellation) throws Exception {
        if (tasks.isEmpty()) return 0;
        ExecutorService workers = Executors.newFixedThreadPool(Math.min(MAX_PARALLEL, tasks.size()));
        int installed = 0;
        int completed = 0;
        try {
            for (int start = 0; start < tasks.size(); start += MAX_PARALLEL) {
                cancellation.check();
                List<File> scratch = new ArrayList<>();
                List<Future<Boolean>> pending = new ArrayList<>();
                try {
                    int end = Math.min(start + MAX_PARALLEL, tasks.size());
                    for (int i = start; i < end; i++) {
                        File file = File.createTempFile("wow-transfer-", ".pending", cache);
                        scratch.add(file);
                        Task task = tasks.get(i);
                        pending.add(workers.submit(() -> task.download(file)));
                    }
                    // Join every worker even on failure: no background writer may outlive scratch cleanup.
                    Exception failure = null;
                    List<Boolean> ready = new ArrayList<>();
                    boolean interrupted = false;
                    for (Future<Boolean> future : pending) {
                        for (;;) {
                            try { ready.add(future.get()); break; }
                            catch (InterruptedException e) { interrupted = true; if (failure == null) failure = e; }
                            catch (ExecutionException e) {
                                Throwable cause = e.getCause();
                                if (failure == null) failure = cause instanceof Exception
                                        ? (Exception) cause : new Exception("Restore transfer failed", cause);
                                ready.add(false);
                                break;
                            }
                        }
                    }
                    if (interrupted) Thread.currentThread().interrupt();
                    if (failure != null) throw failure;
                    for (int i = 0; i < pending.size(); i++) {
                        cancellation.check();
                        if (ready.get(i) && tasks.get(start + i).install(scratch.get(i))) installed++;
                        completed++;
                        if (progress != null) progress.onComplete(completed, tasks.size());
                    }
                } finally {
                    // Also handles executor submission/temp creation failure after an earlier submission.
                    boolean interrupted = Thread.interrupted();
                    for (Future<Boolean> future : pending) {
                        while (!future.isDone()) {
                            try { future.get(); }
                            catch (InterruptedException e) { interrupted = true; }
                            catch (ExecutionException e) { break; }
                        }
                    }
                    for (File file : scratch) file.delete();
                    if (interrupted) Thread.currentThread().interrupt();
                }
            }
            return installed;
        } finally {
            workers.shutdown();
            boolean interrupted = Thread.interrupted();
            for (;;) {
                try { if (workers.awaitTermination(1, TimeUnit.DAYS)) break; }
                catch (InterruptedException e) { interrupted = true; }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private RestoreTransfers() { }
}
