package com.whisper.wowreader;

import java.util.Calendar;

/** Pure, side-effect-free decisions for the local reading reminder. */
public final class ReadingReminderPolicy {
    private ReadingReminderPolicy() {}

    public static final int DEFAULT_HOUR = 20;
    public static final int DEFAULT_MINUTE = 0;
    public static final int MIN_STREAK_NUDGE = 1;
    public static final long MIN_READING_MS = 1L;

    public static final class Decision {
        public final boolean deliver;
        public final boolean streakAtRisk;
        public final String message;
        Decision(boolean deliver, boolean streakAtRisk, String message) {
            this.deliver = deliver;
            this.streakAtRisk = streakAtRisk;
            this.message = message;
        }
    }

    /** Returns the next occurrence of hour:minute in the device timezone, strictly after now. */
    public static long nextLocalDateTime(long nowMs, int hour, int minute) {
        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(nowMs);
        next.set(Calendar.HOUR_OF_DAY, clamp(hour, 0, 23));
        next.set(Calendar.MINUTE, clamp(minute, 0, 59));
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= nowMs) next.add(Calendar.DAY_OF_YEAR, 1);
        return next.getTimeInMillis();
    }

    public static boolean hasReadToday(long todayMs) {
        return todayMs >= MIN_READING_MS;
    }

    /** Daily reminder risk is only real when the current streak is nonzero and today has no reading. */
    public static boolean isStreakAtRisk(long todayMs, int currentStreak) {
        return todayMs < MIN_READING_MS && currentStreak >= MIN_STREAK_NUDGE;
    }

    public static boolean alreadyDeliveredForDay(String lastDeliveredDay, String today) {
        return today != null && today.equals(lastDeliveredDay);
    }

    public static Decision decide(long todayMs, int currentStreak, boolean onlyIfUnread, boolean streakNudge) {
        boolean read = hasReadToday(todayMs);
        if (onlyIfUnread && read) return new Decision(false, false, "");
        boolean atRisk = isStreakAtRisk(todayMs, currentStreak);
        if (streakNudge && atRisk) {
            return new Decision(true, true, "You're about to lose your streak. A few minutes of reading keeps it going.");
        }
        return new Decision(true, false, "Keep your reading habit going with a few quiet minutes today.");
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
