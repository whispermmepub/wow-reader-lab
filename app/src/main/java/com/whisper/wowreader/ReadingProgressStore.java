package com.whisper.wowreader;

import android.content.SharedPreferences;

/** Single source of truth for per-book reading percentage and completion time. */
final class ReadingProgressStore {
    private ReadingProgressStore() {}

    private static String percentKey(String fileName) { return "percent_" + fileName; }
    private static String finishedKey(String fileName) { return "finished_at_" + fileName; }

    static int get(SharedPreferences prefs, String fileName) {
        if (prefs == null || fileName == null) return 0;
        return clamp(prefs.getInt(percentKey(fileName), 0));
    }

    static void set(SharedPreferences prefs, String fileName, int percent) {
        if (prefs == null || fileName == null) return;
        int clean = clamp(percent);
        int previous = get(prefs, fileName);
        long addedAt = prefs.getLong("added_at_" + fileName, 0L);
        long finishedAt = prefs.getLong(finishedKey(fileName), 0L);
        SharedPreferences.Editor edit = prefs.edit().putInt(percentKey(fileName), clean);
        // Completion is historical: moving back to an earlier page after finishing must not erase it.
        // A stale timestamp from a deleted/re-imported same-name file is replaced after the new import.
        if (clean >= 100 && (previous < 100 || finishedAt <= 0L || (addedAt > 0L && finishedAt < addedAt))) {
            edit.putLong(finishedKey(fileName), System.currentTimeMillis());
        }
        edit.apply();
    }

    static long finishedAt(SharedPreferences prefs, String fileName) {
        if (prefs == null || fileName == null) return 0L;
        long value = prefs.getLong(finishedKey(fileName), 0L);
        long addedAt = prefs.getLong("added_at_" + fileName, 0L);
        return addedAt > 0L && value > 0L && value < addedAt ? 0L : Math.max(0L, value);
    }

    static void backfillFinishedAt(SharedPreferences prefs, String fileName, long whenMs) {
        if (prefs == null || fileName == null || whenMs <= 0L || get(prefs, fileName) < 100) return;
        if (finishedAt(prefs, fileName) > 0L) return;
        long addedAt = prefs.getLong("added_at_" + fileName, 0L);
        if (addedAt > 0L && whenMs < addedAt) return;
        prefs.edit().putLong(finishedKey(fileName), whenMs).apply();
    }

    static void remove(SharedPreferences prefs, String fileName) {
        if (prefs == null || fileName == null) return;
        prefs.edit().remove(percentKey(fileName)).remove(finishedKey(fileName)).apply();
    }

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
}
