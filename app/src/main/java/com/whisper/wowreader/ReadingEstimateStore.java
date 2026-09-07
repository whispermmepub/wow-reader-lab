package com.whisper.wowreader;

import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.Locale;

/** Learns a rough reading pace from real book progress and time already spent. */
public final class ReadingEstimateStore {
    private ReadingEstimateStore() {}

    private static final long MIN_SAMPLE_MS = 90_000L;
    private static final double MIN_PROGRESS = 0.02;
    private static final long MAX_TOTAL_MS = 72L * 60L * 60L * 1000L;

    public static final class Estimate {
        public final boolean ready;
        public final long chapterRemainingMs;
        public final long bookRemainingMs;
        Estimate(boolean ready, long chapterRemainingMs, long bookRemainingMs) {
            this.ready = ready;
            this.chapterRemainingMs = Math.max(0L, chapterRemainingMs);
            this.bookRemainingMs = Math.max(0L, bookRemainingMs);
        }
    }

    public static Estimate estimate(SharedPreferences prefs, String bookName,
                                    double overallProgress, double chapterProgress,
                                    int chapterCount, long currentSessionStartedElapsedMs) {
        double overall = clamp01(overallProgress);
        double chapter = clamp01(chapterProgress);
        long spent = ReadingStatsStore.totalBookTime(prefs, bookName);
        if (currentSessionStartedElapsedMs > 0L) {
            long current = Math.max(0L, SystemClock.elapsedRealtime() - currentSessionStartedElapsedMs);
            spent = safeAdd(spent, Math.min(current, 6L * 60L * 60L * 1000L));
        }
        if (spent < MIN_SAMPLE_MS || overall < MIN_PROGRESS) return new Estimate(false, 0L, 0L);

        long total = (long) Math.min(MAX_TOTAL_MS, Math.max(spent, spent / Math.max(MIN_PROGRESS, overall)));
        long bookRemaining = (long) Math.max(0d, total * (1d - overall));
        int count = Math.max(1, chapterCount);
        long averageChapter = Math.max(1L, total / count);
        long chapterRemaining = (long) Math.max(0d, averageChapter * (1d - chapter));
        return new Estimate(true, chapterRemaining, bookRemaining);
    }

    public static String chapterLabel(Estimate e) {
        if (e == null || !e.ready) return "Calculating reading time…";
        return format(e.chapterRemainingMs) + " left in chapter";
    }

    public static String bookLabel(Estimate e) {
        if (e == null || !e.ready) return "Calculating reading time…";
        return format(e.bookRemainingMs) + " left in book";
    }

    public static String format(long ms) {
        if (ms < 60_000L) return "< 1 min";
        long minutes = Math.max(1L, Math.round(ms / 60_000d));
        if (minutes < 60L) return minutes + " min";
        long hours = minutes / 60L;
        long rem = minutes % 60L;
        if (rem == 0L) return hours + (hours == 1L ? " hr" : " hrs");
        return String.format(Locale.US, "%d hr %d min", hours, rem);
    }

    private static double clamp01(double v) { return Math.max(0d, Math.min(1d, v)); }
    private static long safeAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        return Math.max(0L, a + b);
    }
}
