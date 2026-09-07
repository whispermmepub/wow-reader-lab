package com.whisper.wowreader;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Builds recap data entirely from the existing local library, progress and calendar records. */
final class ReadingRecapData {
    private ReadingRecapData() {}

    private static final String KEY_DAY_BOOKS = "reading_stats_day_books_json";

    static final class FinishedBook {
        final File file;
        final String title;
        final String author;
        final long finishedAt;

        FinishedBook(File file, String title, String author, long finishedAt) {
            this.file = file;
            this.title = title;
            this.author = author;
            this.finishedAt = finishedAt;
        }
    }

    static final class Summary {
        final long startMs;
        final long endMs;
        final long readingMs;
        final int activeDays;
        final int touchedBooks;
        final List<FinishedBook> finishedBooks;

        Summary(long startMs, long endMs, long readingMs, int activeDays,
                int touchedBooks, List<FinishedBook> finishedBooks) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.readingMs = readingMs;
            this.activeDays = activeDays;
            this.touchedBooks = touchedBooks;
            this.finishedBooks = finishedBooks;
        }
    }

    static Summary summarize(Context context, SharedPreferences prefs, long rawStartMs, long rawEndMs) {
        long startMs = startOfDay(rawStartMs);
        long endMs = endOfDay(rawEndMs);
        long readingMs = 0L;
        int activeDays = 0;
        Set<String> touched = new HashSet<>();

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startMs);
        while (c.getTimeInMillis() <= endMs) {
            String key = ReadingStatsStore.dayKey(
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
            long dayMs = ReadingStatsStore.dayTime(prefs, key);
            if (dayMs > 0L) activeDays++;
            if (Long.MAX_VALUE - readingMs < dayMs) readingMs = Long.MAX_VALUE;
            else readingMs += Math.max(0L, dayMs);
            for (ReadingStatsStore.DayBook book : ReadingStatsStore.booksForDay(prefs, key))
                touched.add(book.fileName);
            c.add(Calendar.DAY_OF_MONTH, 1);
        }

        List<FinishedBook> finished = finishedBooks(context, prefs, startMs, endMs);
        return new Summary(startMs, endMs, readingMs, activeDays, touched.size(), finished);
    }

    static List<FinishedBook> finishedBooks(Context context, SharedPreferences prefs, long startMs, long endMs) {
        List<FinishedBook> out = new ArrayList<>();
        File library = new File(context.getFilesDir(), "library");
        File[] files = library.listFiles();
        if (files == null) return out;

        for (File file : files) {
            if (file == null || !file.isFile()) continue;
            String lower = file.getName().toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".epub") && !lower.endsWith(".pdf")) continue;

            long finishedAt = ReadingProgressStore.finishedAt(prefs, file.getName());
            if (finishedAt <= 0L && ReadingProgressStore.get(prefs, file.getName()) >= 100) {
                finishedAt = inferLegacyFinishedAt(prefs, file.getName());
                if (finishedAt > 0L) ReadingProgressStore.backfillFinishedAt(prefs, file.getName(), finishedAt);
            }
            if (finishedAt < startMs || finishedAt > endMs) continue;

            String title = prefs.getString("library_title_" + file.getName(), "");
            if (title == null || title.trim().isEmpty()) title = stripExtension(file.getName());
            String author = prefs.getString("library_author_" + file.getName(), "");
            if (author == null) author = "";
            out.add(new FinishedBook(file, title.trim(), author.trim(), finishedAt));
        }

        Collections.sort(out, new Comparator<FinishedBook>() {
            @Override public int compare(FinishedBook a, FinishedBook b) {
                return Long.compare(b.finishedAt, a.finishedAt);
            }
        });
        return out;
    }

    private static long inferLegacyFinishedAt(SharedPreferences prefs, String fileName) {
        String latestDay = "";
        try {
            JSONObject root = new JSONObject(prefs.getString(KEY_DAY_BOOKS, "{}"));
            Iterator<String> days = root.keys();
            while (days.hasNext()) {
                String day = days.next();
                JSONObject books = root.optJSONObject(day);
                if (books != null && books.optLong(fileName, 0L) > 0L && day.compareTo(latestDay) > 0)
                    latestDay = day;
            }
            if (!latestDay.isEmpty()) {
                SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                fmt.setLenient(false);
                java.util.Date parsed = fmt.parse(latestDay);
                if (parsed != null) return endOfDay(parsed.getTime());
            }
        } catch (Exception ignored) {}
        return Math.max(0L, prefs.getLong("last_opened_" + fileName, 0L));
    }

    static long startOfWeek(long whenMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(whenMs);
        c.setFirstDayOfWeek(Calendar.MONDAY);
        int day = c.get(Calendar.DAY_OF_WEEK);
        int delta = day == Calendar.SUNDAY ? -6 : Calendar.MONDAY - day;
        c.add(Calendar.DAY_OF_MONTH, delta);
        return startOfDay(c.getTimeInMillis());
    }

    static long endOfWeek(long whenMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startOfWeek(whenMs));
        c.add(Calendar.DAY_OF_MONTH, 6);
        return endOfDay(c.getTimeInMillis());
    }

    static long startOfMonth(long whenMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(whenMs);
        c.set(Calendar.DAY_OF_MONTH, 1);
        return startOfDay(c.getTimeInMillis());
    }

    static long endOfMonth(long whenMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(whenMs);
        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
        return endOfDay(c.getTimeInMillis());
    }

    static long startOfYear(long whenMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(whenMs);
        c.set(Calendar.MONTH, Calendar.JANUARY);
        c.set(Calendar.DAY_OF_MONTH, 1);
        return startOfDay(c.getTimeInMillis());
    }

    static long endOfYear(long whenMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(whenMs);
        c.set(Calendar.MONTH, Calendar.DECEMBER);
        c.set(Calendar.DAY_OF_MONTH, 31);
        return endOfDay(c.getTimeInMillis());
    }

    static long startOfDay(long whenMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(whenMs);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static long endOfDay(long whenMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startOfDay(whenMs));
        c.add(Calendar.DAY_OF_MONTH, 1);
        c.add(Calendar.MILLISECOND, -1);
        return c.getTimeInMillis();
    }

    private static String stripExtension(String value) {
        if (value == null) return "Book";
        int dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }
}
