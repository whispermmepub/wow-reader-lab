package com.whisper.wowreader;

import android.content.SharedPreferences;
import android.os.SystemClock;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Local-first reading statistics plus calendar/memory data. */
public final class ReadingStatsStore {
    private ReadingStatsStore() {}

    private static final String KEY_TOTAL_MS = "reading_stats_total_ms";
    private static final String KEY_DAYS = "reading_stats_days_json";
    private static final String KEY_BOOKS = "reading_stats_books_json";
    private static final String KEY_DAY_BOOKS = "reading_stats_day_books_json";
    private static final String KEY_DAY_NOTES = "reading_stats_day_notes_json";
    private static final String KEY_BOOK_DAY_NOTES = "reading_stats_book_day_notes_json";
    private static final String KEY_CURRENT_STREAK = "reading_stats_current_streak";
    private static final String KEY_LONGEST_STREAK = "reading_stats_longest_streak";
    private static final String KEY_LAST_DAY = "reading_stats_last_day";

    private static final long MIN_SESSION_MS = 5_000L;
    private static final long MAX_SESSION_MS = 6L * 60L * 60L * 1000L;

    public static final class Snapshot {
        public long todayMs;
        public long totalMs;
        public long bookMs;
        public int currentStreak;
        public int longestStreak;
        public int activeDays;
    }

    public static final class DayBook {
        public final String fileName;
        public final long durationMs;
        DayBook(String fileName, long durationMs) {
            this.fileName = fileName;
            this.durationMs = durationMs;
        }
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
            JSONObject dayBooks = object(prefs.getString(KEY_DAY_BOOKS, "{}"));

            days.put(today, safeAdd(days.optLong(today, 0L), durationMs));
            String cleanBook = bookName == null ? "" : bookName.trim();
            if (!cleanBook.isEmpty()) {
                String hashedBook = bookKey(cleanBook);
                books.put(hashedBook, safeAdd(books.optLong(hashedBook, 0L), durationMs));
                JSONObject todayBooks = dayBooks.optJSONObject(today);
                if (todayBooks == null) todayBooks = new JSONObject();
                todayBooks.put(cleanBook, safeAdd(todayBooks.optLong(cleanBook, 0L), durationMs));
                dayBooks.put(today, todayBooks);
            }

            int current = prefs.getInt(KEY_CURRENT_STREAK, 0);
            int longest = prefs.getInt(KEY_LONGEST_STREAK, 0);
            String lastDay = prefs.getString(KEY_LAST_DAY, "");
            if (!today.equals(lastDay)) {
                if (lastDay != null && lastDay.equals(previousDayKey(wallClockMs))) current = Math.max(1, current + 1);
                else current = 1;
                longest = Math.max(longest, current);
                lastDay = today;
            }

            prefs.edit()
                    .putLong(KEY_TOTAL_MS, safeAdd(prefs.getLong(KEY_TOTAL_MS, 0L), durationMs))
                    .putString(KEY_DAYS, days.toString())
                    .putString(KEY_BOOKS, books.toString())
                    .putString(KEY_DAY_BOOKS, dayBooks.toString())
                    .putInt(KEY_CURRENT_STREAK, current)
                    .putInt(KEY_LONGEST_STREAK, longest)
                    .putString(KEY_LAST_DAY, lastDay == null ? today : lastDay)
                    .putLong("sync_updated_ms", System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {}
    }

    public static Snapshot snapshot(SharedPreferences prefs, String bookName) {
        Snapshot s = new Snapshot();
        if (prefs == null) return s;
        try {
            JSONObject days = object(prefs.getString(KEY_DAYS, "{}"));
            JSONObject books = object(prefs.getString(KEY_BOOKS, "{}"));
            s.todayMs = days.optLong(dayKey(System.currentTimeMillis()), 0L);
            s.totalMs = prefs.getLong(KEY_TOTAL_MS, 0L);
            s.bookMs = bookName == null ? 0L : books.optLong(bookKey(bookName), 0L);
            s.currentStreak = calculateCurrentStreak(days);
            s.longestStreak = Math.max(prefs.getInt(KEY_LONGEST_STREAK, 0), calculateLongestStreak(days));
            s.activeDays = countPositiveDays(days);
        } catch (Exception ignored) {}
        return s;
    }

    public static Snapshot snapshot(SharedPreferences prefs) { return snapshot(prefs, null); }

    public static String dayKey(int year, int monthOneBased, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, Math.max(0, monthOneBased - 1), day, 12, 0, 0);
        return dayKey(c.getTimeInMillis());
    }

    public static long dayTime(SharedPreferences prefs, String key) {
        if (prefs == null || key == null) return 0L;
        return object(prefs.getString(KEY_DAYS, "{}")).optLong(key, 0L);
    }

    public static long bookTimeForDay(SharedPreferences prefs, String key, String fileName) {
        if (prefs == null || key == null || fileName == null) return 0L;
        JSONObject date = object(prefs.getString(KEY_DAY_BOOKS, "{}")).optJSONObject(key);
        return date == null ? 0L : date.optLong(fileName, 0L);
    }

    public static long totalBookTime(SharedPreferences prefs, String fileName) {
        if (prefs == null || fileName == null) return 0L;
        return object(prefs.getString(KEY_BOOKS, "{}")).optLong(bookKey(fileName), 0L);
    }

    public static List<DayBook> booksForDay(SharedPreferences prefs, String key) {
        List<DayBook> out = new ArrayList<>();
        if (prefs == null || key == null) return out;
        JSONObject date = object(prefs.getString(KEY_DAY_BOOKS, "{}")).optJSONObject(key);
        if (date == null) return out;
        Iterator<String> names = date.keys();
        while (names.hasNext()) {
            String name = names.next();
            long ms = date.optLong(name, 0L);
            if (name != null && !name.trim().isEmpty() && ms > 0L) out.add(new DayBook(name, ms));
        }
        Collections.sort(out, new Comparator<DayBook>() {
            @Override public int compare(DayBook a, DayBook b) { return Long.compare(b.durationMs, a.durationMs); }
        });
        return out;
    }

    public static long readingTimeForMonth(SharedPreferences prefs, int year, int monthOneBased) {
        if (prefs == null) return 0L;
        String prefix = monthPrefix(year, monthOneBased);
        JSONObject days = object(prefs.getString(KEY_DAYS, "{}"));
        long total = 0L;
        Iterator<String> keys = days.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.startsWith(prefix)) total = safeAdd(total, days.optLong(key, 0L));
        }
        return total;
    }

    public static int activeDaysForMonth(SharedPreferences prefs, int year, int monthOneBased) {
        if (prefs == null) return 0;
        String prefix = monthPrefix(year, monthOneBased);
        JSONObject days = object(prefs.getString(KEY_DAYS, "{}"));
        int count = 0;
        Iterator<String> keys = days.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.startsWith(prefix) && days.optLong(key, 0L) > 0L) count++;
        }
        return count;
    }

    public static int uniqueBooksForMonth(SharedPreferences prefs, int year, int monthOneBased) {
        if (prefs == null) return 0;
        String prefix = monthPrefix(year, monthOneBased);
        JSONObject root = object(prefs.getString(KEY_DAY_BOOKS, "{}"));
        Set<String> books = new HashSet<>();
        Iterator<String> days = root.keys();
        while (days.hasNext()) {
            String key = days.next();
            if (!key.startsWith(prefix)) continue;
            JSONObject item = root.optJSONObject(key);
            if (item == null) continue;
            Iterator<String> names = item.keys();
            while (names.hasNext()) {
                String name = names.next();
                if (item.optLong(name, 0L) > 0L) books.add(name);
            }
        }
        return books.size();
    }

    public static String dailyNote(SharedPreferences prefs, String key) {
        if (prefs == null || key == null) return "";
        return object(prefs.getString(KEY_DAY_NOTES, "{}")).optString(key, "");
    }

    public static void setDailyNote(SharedPreferences prefs, String key, String note) {
        if (prefs == null || key == null) return;
        try {
            JSONObject root = object(prefs.getString(KEY_DAY_NOTES, "{}"));
            String clean = note == null ? "" : note.trim();
            if (clean.isEmpty()) root.remove(key); else root.put(key, clean);
            saveJson(prefs, KEY_DAY_NOTES, root);
        } catch (Exception ignored) {}
    }

    public static String bookDayNote(SharedPreferences prefs, String key, String fileName) {
        if (prefs == null || key == null || fileName == null) return "";
        JSONObject date = object(prefs.getString(KEY_BOOK_DAY_NOTES, "{}")).optJSONObject(key);
        return date == null ? "" : date.optString(fileName, "");
    }

    public static void setBookDayNote(SharedPreferences prefs, String key, String fileName, String note) {
        if (prefs == null || key == null || fileName == null) return;
        try {
            JSONObject root = object(prefs.getString(KEY_BOOK_DAY_NOTES, "{}"));
            JSONObject date = root.optJSONObject(key);
            if (date == null) date = new JSONObject();
            String clean = note == null ? "" : note.trim();
            if (clean.isEmpty()) date.remove(fileName); else date.put(fileName, clean);
            if (date.length() == 0) root.remove(key); else root.put(key, date);
            saveJson(prefs, KEY_BOOK_DAY_NOTES, root);
        } catch (Exception ignored) {}
    }

    private static void saveJson(SharedPreferences prefs, String key, JSONObject value) {
        prefs.edit().putString(key, value.toString())
                .putLong("sync_updated_ms", System.currentTimeMillis()).apply();
    }

    private static String monthPrefix(int year, int monthOneBased) {
        return String.format(Locale.US, "%04d-%02d-", year, monthOneBased);
    }

    private static String bookKey(String bookName) {
        return Integer.toHexString((bookName == null ? "book" : bookName).hashCode());
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
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -3650);
        int longest = 0;
        int running = 0;
        for (int i = 0; i <= 3650; i++) {
            if (days.optLong(dayKey(c.getTimeInMillis()), 0L) > 0L) {
                running++;
                longest = Math.max(longest, running);
            } else running = 0;
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return longest;
    }

    private static String previousDayKey(long wallClockMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(wallClockMs);
        c.add(Calendar.DAY_OF_YEAR, -1);
        return dayKey(c.getTimeInMillis());
    }

    private static String dayKey(long wallClockMs) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(wallClockMs));
    }

    private static long safeAdd(long a, long b) {
        if (b > 0L && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        return Math.max(0L, a + b);
    }
}
