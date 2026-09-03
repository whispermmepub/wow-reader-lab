package com.whisper.wowreader;

import android.content.SharedPreferences;
import android.os.SystemClock;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

/**
 * Lightweight, local-first reading statistics store for WoW Reader Lab.
 *
 * This class is original WoW Reader code inspired only by the general idea of
 * reader statistics. It deliberately uses the app's existing SharedPreferences
 * so the data is automatically included by the current backup/sync pipeline.
 */
public final class ReadingStatsStore {
    private ReadingStatsStore() {}

    private static final String KEY_TOTAL_MS = "reading_stats_total_ms";
    private static final String KEY_DAYS = "reading_stats_days_json";
    private static final String KEY_BOOKS = "reading_stats_books_json";
    private static final String KEY_CURRENT_STREAK = "reading_stats_current_streak";
    private static final String KEY_LONGEST_STREAK = "reading_stats_longest_streak";
    private static final String KEY_LAST_DAY = "reading_stats_last_day";

    // Tiny launches, permission interruptions, etc. should not count as reading.
    private static final long MIN_SESSION_MS = 5_000L;
    // Guard against a stale lifecycle timestamp after process/device anomalies.
    private static final long MAX_SESSION_MS = 6L * 60L * 60L * 1000L;

    public static final class Snapshot {
        public long todayMs;
        public long totalMs;
        public long bookMs;
        public int currentStreak;
        public int longestStreak;
        public int activeDays;
    }

    public static long beginSession() {
        return SystemClock.elapsedRealtime();
    }

    public static void finishSession(SharedPreferences prefs, String bookName, long startedElapsedMs) {
        if (prefs == null || startedElapsedMs <= 0L) return;
        long duration = SystemClock.elapsedRealtime() - startedElapsedMs;
        if (duration < MIN_SESSION_MS) return;
        duration = Math.min(duration, MAX_SESSION_MS);
        record(prefs, bookName, duration, System.currentTimeMillis());
    }

    static void record(SharedPreferences prefs, String bookName, long durationMs, long wallClockMs) {
        if (prefs == null || durationMs <= 0L) return;
        try {
            String today = dayKey(wallClockMs);
            JSONObject days = object(prefs.getString(KEY_DAYS, "{}"));
            JSONObject books = object(prefs.getString(KEY_BOOKS, "{}"));

            days.put(today, safeAdd(days.optLong(today, 0L), durationMs));
            String bookKey = Integer.toHexString((bookName == null ? "book" : bookName).hashCode());
            books.put(bookKey, safeAdd(books.optLong(bookKey, 0L), durationMs));

            int current = prefs.getInt(KEY_CURRENT_STREAK, 0);
            int longest = prefs.getInt(KEY_LONGEST_STREAK, 0);
            String lastDay = prefs.getString(KEY_LAST_DAY, "");

            if (!today.equals(lastDay)) {
                if (lastDay != null && lastDay.equals(dayKey(wallClockMs - 24L * 60L * 60L * 1000L))) {
                    current = Math.max(1, current + 1);
                } else {
                    current = 1;
                }
                longest = Math.max(longest, current);
                lastDay = today;
            }

            prefs.edit()
                    .putLong(KEY_TOTAL_MS, safeAdd(prefs.getLong(KEY_TOTAL_MS, 0L), durationMs))
                    .putString(KEY_DAYS, days.toString())
                    .putString(KEY_BOOKS, books.toString())
                    .putInt(KEY_CURRENT_STREAK, current)
                    .putInt(KEY_LONGEST_STREAK, longest)
                    .putString(KEY_LAST_DAY, lastDay == null ? today : lastDay)
                    .putLong("sync_updated_ms", System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public static Snapshot snapshot(SharedPreferences prefs, String bookName) {
        Snapshot s = new Snapshot();
        if (prefs == null) return s;
        try {
            JSONObject days = object(prefs.getString(KEY_DAYS, "{}"));
            JSONObject books = object(prefs.getString(KEY_BOOKS, "{}"));
            s.todayMs = days.optLong(dayKey(System.currentTimeMillis()), 0L);
            s.totalMs = prefs.getLong(KEY_TOTAL_MS, 0L);
            String bookKey = Integer.toHexString((bookName == null ? "book" : bookName).hashCode());
            s.bookMs = books.optLong(bookKey, 0L);
            s.currentStreak = calculateCurrentStreak(days);
            s.longestStreak = Math.max(prefs.getInt(KEY_LONGEST_STREAK, 0), calculateLongestStreak(days));
            s.activeDays = countPositiveDays(days);
        } catch (Exception ignored) {
        }
        return s;
    }

    public static Snapshot snapshot(SharedPreferences prefs) {
        return snapshot(prefs, null);
    }

    private static JSONObject object(String raw) {
        try { return new JSONObject(raw == null ? "{}" : raw); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    private static int countPositiveDays(JSONObject days) {
        int count = 0;
        Iterator<String> keys = days.keys();
        while (keys.hasNext()) if (days.optLong(keys.next(), 0L) > 0L) count++;
        return count;
    }

    private static int calculateCurrentStreak(JSONObject days) {
        Calendar c = Calendar.getInstance();
        String today = dayKey(c.getTimeInMillis());
        if (days.optLong(today, 0L) <= 0L) {
            c.add(Calendar.DAY_OF_YEAR, -1);
            if (days.optLong(dayKey(c.getTimeInMillis()), 0L) <= 0L) return 0;
        }
        int count = 0;
        while (count < 3660 && days.optLong(dayKey(c.getTimeInMillis()), 0L) > 0L) {
            count++;
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
        return count;
    }

    private static int calculateLongestStreak(JSONObject days) {
        if (days.length() == 0) return 0;
        long now = System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.add(Calendar.DAY_OF_YEAR, -3650);
        int longest = 0;
        int running = 0;
        for (int i = 0; i <= 3650; i++) {
            if (days.optLong(dayKey(c.getTimeInMillis()), 0L) > 0L) {
                running++;
                longest = Math.max(longest, running);
            } else {
                running = 0;
            }
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return longest;
    }

    private static String dayKey(long wallClockMs) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(wallClockMs));
    }

    private static long safeAdd(long a, long b) {
        if (b > 0L && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        return Math.max(0L, a + b);
    }
}
