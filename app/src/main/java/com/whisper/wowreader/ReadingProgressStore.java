package com.whisper.wowreader;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/** Single source of truth for per-book reading percentage and completion time. */
final class ReadingProgressStore {
    private ReadingProgressStore() {}

    static void init(Context context, SharedPreferences prefs) {
        if (context == null || prefs == null) return;
        ReaderStateDb.initialize(context, prefs, new File(context.getFilesDir(), "library"));
    }

    private static String percentKey(String fileName) { return "percent_" + fileName; }
    private static String finishedKey(String fileName) { return "finished_at_" + fileName; }

    static int get(SharedPreferences prefs, String fileName) {
        if (prefs == null || fileName == null) return 0;
        return clamp(prefs.getInt(percentKey(fileName), 0));
    }

    static void set(SharedPreferences prefs, String fileName, int percent) {
        if (prefs == null || fileName == null) return;
        int clean = clamp(percent);
        int old = clamp(prefs.getInt(percentKey(fileName), 0));
        long addedAt = prefs.getLong("added_at_" + fileName, 0L);
        long finishedAt = prefs.getLong(finishedKey(fileName), 0L);
        boolean shouldFinish = clean >= 100 && (finishedAt <= 0L || (addedAt > 0L && finishedAt < addedAt));
        if (old == clean && !shouldFinish) return;
        SharedPreferences.Editor edit = prefs.edit().putInt(percentKey(fileName), clean);
        if (shouldFinish) {
            finishedAt = System.currentTimeMillis();
            edit.putLong(finishedKey(fileName), finishedAt);
        }
        edit.apply();
        ReaderStateDb db = ReaderStateDb.peek();
        if (db != null) db.updateProgress(fileName, clean, finishedAt);
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
        ReaderStateDb db = ReaderStateDb.peek();
        if (db != null) db.updateProgress(fileName, get(prefs, fileName), whenMs);
    }

    static void remove(SharedPreferences prefs, String fileName) {
        if (prefs == null || fileName == null) return;
        prefs.edit().remove(percentKey(fileName)).remove(finishedKey(fileName)).apply();
        ReaderStateDb db = ReaderStateDb.peek();
        if (db != null) db.removeBook(fileName);
    }

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
}
