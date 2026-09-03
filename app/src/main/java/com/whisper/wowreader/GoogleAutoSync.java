package com.whisper.wowreader;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * App-wide, local-first Google Drive auto sync coordinator.
 *
 * Reading data is always saved locally first. Cloud backup is delayed/coalesced so
 * page turns do not continuously rebuild and upload the whole library snapshot.
 */
final class GoogleAutoSync {
    private static final long SOON_DELAY_MS = 8_000L;
    private static final long NORMAL_DELAY_MS = 90_000L;
    private static final long RETRY_DELAY_MS = 120_000L;
    private static final long MIN_ATTEMPT_INTERVAL_MS = 30_000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> activityRef = new WeakReference<>(null);
    private static Runnable pendingRunnable;
    private static long pendingAtMs = 0L;
    private static boolean busy = false;
    private static long lastAttemptMs = 0L;

    private GoogleAutoSync() {}

    static void schedule(Activity activity) {
        scheduleWithDelay(activity, NORMAL_DELAY_MS);
    }

    static void scheduleSoon(Activity activity) {
        scheduleWithDelay(activity, SOON_DELAY_MS);
    }

    static void flush(Activity activity) {
        if (!needsSync(activity)) return;
        cancelPending();
        long remaining;
        synchronized (GoogleAutoSync.class) {
            if (busy) return;
            remaining = MIN_ATTEMPT_INTERVAL_MS - (System.currentTimeMillis() - lastAttemptMs);
        }
        if (remaining > 0L) scheduleWithDelay(activity, remaining);
        else runSync(activity);
    }

    static synchronized boolean isBusy() {
        return busy;
    }

    static void cancelPending() {
        synchronized (GoogleAutoSync.class) {
            if (pendingRunnable != null) MAIN.removeCallbacks(pendingRunnable);
            pendingRunnable = null;
            pendingAtMs = 0L;
        }
    }

    private static void scheduleWithDelay(Activity activity, long delayMs) {
        if (!needsSync(activity)) return;
        long now = System.currentTimeMillis();
        long due = now + Math.max(1_500L, delayMs);
        synchronized (GoogleAutoSync.class) {
            activityRef = new WeakReference<>(activity);
            if (pendingRunnable != null && pendingAtMs > 0L && pendingAtMs <= due) return;
            if (pendingRunnable != null) MAIN.removeCallbacks(pendingRunnable);
            pendingAtMs = due;
            pendingRunnable = () -> {
                Activity target;
                synchronized (GoogleAutoSync.class) {
                    pendingRunnable = null;
                    pendingAtMs = 0L;
                    target = activityRef.get();
                }
                if (target != null) runSync(target);
            };
            MAIN.postDelayed(pendingRunnable, Math.max(1_500L, delayMs));
        }
    }

    private static boolean needsSync(Activity activity) {
        if (activity == null) return false;
        SharedPreferences prefs = activity.getSharedPreferences("wow_reader", Activity.MODE_PRIVATE);
        if (!prefs.getBoolean("google_sync_connected", false) ||
                !prefs.getBoolean("google_sync_enabled", true)) return false;
        long changed = prefs.getLong("sync_updated_ms", 0L);
        long synced = prefs.getLong("google_last_synced_change_ms",
                prefs.getLong("google_last_backup_ms", 0L));
        return changed > synced;
    }

    private static void runSync(Activity activity) {
        if (!needsSync(activity)) return;
        SharedPreferences prefs = activity.getSharedPreferences("wow_reader", Activity.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        synchronized (GoogleAutoSync.class) {
            if (busy) {
                scheduleWithDelay(activity, SOON_DELAY_MS);
                return;
            }
            long remaining = MIN_ATTEMPT_INTERVAL_MS - (now - lastAttemptMs);
            if (remaining > 0L) {
                scheduleWithDelay(activity, remaining);
                return;
            }
            busy = true;
            lastAttemptMs = now;
        }

        final long requestedChangeMs = prefs.getLong("sync_updated_ms", 0L);
        final GoogleDriveSync drive = new GoogleDriveSync(activity);
        drive.authorizeSilently(new GoogleDriveSync.AuthCallback() {
            @Override public void onReady(GoogleDriveSync.Profile profile) {
                File library = new File(activity.getFilesDir(), "library");
                File fonts = new File(activity.getFilesDir(), "reader_fonts");
                if (!library.exists()) library.mkdirs();
                if (!fonts.exists()) fonts.mkdirs();
                GoogleDriveSync.smartBackup(activity, profile.accessToken, library, fonts, prefs,
                        new GoogleDriveSync.SyncCallback() {
                            @Override public void onSuccess(String message) {
                                prefs.edit().putLong("google_last_synced_change_ms", requestedChangeMs).apply();
                                finish(activity, prefs, requestedChangeMs, true);
                            }

                            @Override public void onError(String message) {
                                finish(activity, prefs, requestedChangeMs, false);
                            }
                        });
            }

            @Override public void onError(String message) {
                finish(activity, prefs, requestedChangeMs, false);
            }
        });
    }

    private static void finish(Activity activity, SharedPreferences prefs,
                               long requestedChangeMs, boolean success) {
        synchronized (GoogleAutoSync.class) {
            busy = false;
        }
        long latest = prefs.getLong("sync_updated_ms", 0L);
        if (success) {
            if (latest > requestedChangeMs) scheduleSoon(activity);
        } else {
            scheduleWithDelay(activity, RETRY_DELAY_MS);
        }
    }
}
