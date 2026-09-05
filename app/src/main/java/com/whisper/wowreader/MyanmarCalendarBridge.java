package com.whisper.wowreader;

import java.util.ArrayList;
import java.util.List;

import mmcalendar.Astro;
import mmcalendar.HolidayCalculator;
import mmcalendar.MyanmarDate;

final class MyanmarCalendarBridge {
    private MyanmarCalendarBridge() {}

    static final class Info {
        final String year;
        final String monthName;
        final String moonPhase;
        final String fortnightDay;
        final String weekDay;
        final String sabbath;
        final List<String> holidays;

        Info(String year, String monthName, String moonPhase, String fortnightDay,
             String weekDay, String sabbath, List<String> holidays) {
            this.year = clean(year); this.monthName = clean(monthName); this.moonPhase = clean(moonPhase);
            this.fortnightDay = clean(fortnightDay); this.weekDay = clean(weekDay); this.sabbath = clean(sabbath);
            this.holidays = holidays == null ? new ArrayList<>() : holidays;
        }
    }

    static Info info(int year, int monthOneBased, int day) {
        try {
            MyanmarDate md = MyanmarDate.of(year, monthOneBased, day);
            Astro astro = Astro.of(md);
            List<String> holidays;
            try { holidays = new ArrayList<>(HolidayCalculator.getHoliday(md)); }
            catch (Exception ignored) { holidays = new ArrayList<>(); }
            return new Info(String.valueOf(md.getYear()), md.getMonthName(), md.getMoonPhase(),
                    md.getFortnightDay(), md.getWeekDay(), astro == null ? "" : astro.getSabbath(), holidays);
        } catch (Exception ignored) {
            return new Info("", "", "", "", "", "", new ArrayList<>());
        }
    }

    private static String clean(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        return "null".equalsIgnoreCase(s) ? "" : s.trim();
    }
}
