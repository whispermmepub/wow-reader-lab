package com.whisper.wowreader;

/** Plain Java policy tests; parent runner may include this class with ReadingReminderPolicy.java. */
public final class ReadingReminderPolicyTest {
    private static int tests;
    private static void check(boolean value, String message) {
        tests++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        long now = 1700000000000L;
        long due = ReadingReminderPolicy.nextLocalDateTime(now, 20, 0);
        check(due > now, "next due is future");
        check(ReadingReminderPolicy.nextLocalDateTime(due, 20, 0) > due, "equal local time rolls one day");
        check(!ReadingReminderPolicy.decide(1L, 4, true, true).deliver, "read today suppresses unread-only reminder");
        ReadingReminderPolicy.Decision risk = ReadingReminderPolicy.decide(0L, 4, true, true);
        check(risk.deliver && risk.streakAtRisk, "active streak is correctly marked at risk");
        ReadingReminderPolicy.Decision neutral = ReadingReminderPolicy.decide(0L, 0, false, true);
        check(neutral.deliver && !neutral.streakAtRisk && !neutral.message.contains("lose"), "no false streak warning");
        String deliveredDay = "2026-03-29";
        check(ReadingReminderPolicy.alreadyDeliveredForDay(deliveredDay, deliveredDay), "same local day is duplicate-safe");
        check(!ReadingReminderPolicy.alreadyDeliveredForDay(deliveredDay, "2026-03-30"), "next local day can deliver");
        System.out.println("READING_REMINDER_POLICY_TEST_PASS " + tests);
    }
}
