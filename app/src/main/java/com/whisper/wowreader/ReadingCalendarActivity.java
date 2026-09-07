package com.whisper.wowreader;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReadingCalendarActivity extends Activity {
    private SharedPreferences prefs;
    private ReadingCalendarUi ui;
    private File libraryDir;
    private Calendar anchor;
    private String viewMode = "month";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("wow_reader", MODE_PRIVATE);
        ui = new ReadingCalendarUi(this);
        libraryDir = new File(getFilesDir(), "library");
        anchor = Calendar.getInstance();
        render();
    }

    @Override protected void onRestart() {
        super.onRestart();
        render();
    }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(10));
        root.setBackgroundColor(ui.background);

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(54)));
        root.addView(buildModeTabs(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(46)));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, ui.dp(4), 0, ui.dp(18));
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if ("week".equals(viewMode)) renderWeek(body);
        else if ("year".equals(viewMode)) renderYear(body);
        else renderMonth(body);

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        AppWindowInsets.apply(this, root, ui.background, ui.darkSystemIcons);
    }

    private View buildTopBar() {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = icon("‹", 29);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(ui.dp(46), ui.dp(46)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("Reading Calendar", 21, ui.primary, true);
        TextView subtitle = label("Calendar · finished books · recaps", 10.5f, ui.secondary, false);
        heading.addView(title);
        heading.addView(subtitle);
        top.addView(heading, new LinearLayout.LayoutParams(0, ui.dp(50), 1f));

        TextView today = label("Today", 12, ui.accent, true);
        today.setGravity(Gravity.CENTER);
        today.setBackground(ui.rounded(ui.control, 18, 1, ui.stroke));
        today.setOnClickListener(v -> {
            anchor = Calendar.getInstance();
            render();
        });
        top.addView(today, new LinearLayout.LayoutParams(ui.dp(68), ui.dp(38)));
        return top;
    }

    private View buildModeTabs() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.CENTER);
        wrap.setPadding(ui.dp(2), ui.dp(4), ui.dp(2), ui.dp(4));
        wrap.setBackground(ui.rounded(ui.control, 20, 1, ui.stroke));
        String[] labels = {"Week", "Month", "Year"};
        String[] values = {"week", "month", "year"};
        for (int i = 0; i < values.length; i++) {
            final String value = values[i];
            boolean selected = value.equals(viewMode);
            TextView tab = label(labels[i], 12, selected ? Color.WHITE : ui.secondary, true);
            tab.setGravity(Gravity.CENTER);
            tab.setBackground(ui.rounded(selected ? ui.accent : Color.TRANSPARENT, 17, 0, 0));
            tab.setOnClickListener(v -> {
                if (!value.equals(viewMode)) {
                    viewMode = value;
                    render();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ui.dp(36), 1f);
            if (i > 0) lp.leftMargin = ui.dp(3);
            wrap.addView(tab, lp);
        }
        return wrap;
    }

    private void renderWeek(LinearLayout body) {
        long startMs = ReadingRecapData.startOfWeek(anchor.getTimeInMillis());
        long endMs = ReadingRecapData.endOfWeek(anchor.getTimeInMillis());
        String title = formatWeekRange(startMs, endMs);
        addPeriodNavigator(body, title, "Your reading week", -1, 1);

        GridLayout week = new GridLayout(this);
        week.setColumnCount(7);
        week.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        Calendar d = Calendar.getInstance();
        d.setTimeInMillis(startMs);
        for (int i = 0; i < 7; i++) {
            week.addView(weekDayCell((Calendar) d.clone()), weightedCell(ui.dp(104)));
            d.add(Calendar.DAY_OF_MONTH, 1);
        }
        LinearLayout.LayoutParams weekLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        weekLp.topMargin = ui.dp(7);
        body.addView(week, weekLp);

        ReadingRecapData.Summary summary = ReadingRecapData.summarize(this, prefs, startMs, endMs);
        addRecapCard(body, summary, "week", formatWeekRange(startMs, endMs));
    }

    private View weekDayCell(Calendar date) {
        int y = date.get(Calendar.YEAR);
        int m = date.get(Calendar.MONTH) + 1;
        int day = date.get(Calendar.DAY_OF_MONTH);
        String key = ReadingStatsStore.dayKey(y, m, day);
        List<ReadingStatsStore.DayBook> books = ReadingStatsStore.booksForDay(prefs, key);
        long readMs = ReadingStatsStore.dayTime(prefs, key);
        Calendar now = Calendar.getInstance();
        boolean today = sameDay(date, now);

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(ui.dp(2), ui.dp(5), ui.dp(2), ui.dp(4));
        cell.setBackground(ui.rounded(today ? blend(ui.accent, ui.card, 0.90f) : ui.card,
                10, 1, today ? ui.accent : ui.stroke));
        cell.setClickable(true);
        cell.setOnClickListener(v -> openDay(y, m, day, key));

        TextView dow = label(new SimpleDateFormat("EEE", Locale.ENGLISH).format(date.getTime()).toUpperCase(Locale.ENGLISH),
                7.8f, today ? ui.accent : ui.secondary, true);
        dow.setGravity(Gravity.CENTER);
        cell.addView(dow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(16)));

        TextView number = label(String.valueOf(day), 15, today ? ui.accent : ui.primary, true);
        number.setGravity(Gravity.CENTER);
        cell.addView(number, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(24)));

        if (!books.isEmpty()) {
            ImageView cover = new ImageView(this);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(ui.rounded(ui.control, 4, 0, 0));
            cover.setClipToOutline(true);
            BookVisualUtil.loadCover(this, new File(libraryDir, books.get(0).fileName), cover, ui.dp(28), ui.dp(39));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ui.dp(28), ui.dp(39));
            cp.topMargin = ui.dp(2);
            cell.addView(cover, cp);
        } else {
            TextView dot = label(readMs > 0L ? "●" : "·", 13, readMs > 0L ? ui.accent : ui.stroke, true);
            dot.setGravity(Gravity.CENTER);
            cell.addView(dot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(42)));
        }

        TextView time = label(readMs > 0L ? shortDuration(readMs) : "", 7.4f, ui.secondary, false);
        time.setGravity(Gravity.CENTER);
        time.setSingleLine(true);
        cell.addView(time, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(13)));
        return cell;
    }

    private void renderMonth(LinearLayout body) {
        int year = anchor.get(Calendar.YEAR);
        int month = anchor.get(Calendar.MONTH) + 1;
        Calendar mid = Calendar.getInstance();
        mid.clear();
        mid.set(year, month - 1, 15, 12, 0, 0);
        MyanmarCalendarBridge.Info mi = MyanmarCalendarBridge.info(year, month, 15);
        String mmTitle = mi.monthName.isEmpty() ? "မြန်မာ ပြက္ခဒိန်" :
                mi.monthName + (mi.year.isEmpty() ? "" : " · " + mi.year);
        addPeriodNavigator(body, mmTitle,
                new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(mid.getTime()), -1, 1);

        GridLayout dow = new GridLayout(this);
        dow.setColumnCount(7);
        String[] names = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
        for (int i = 0; i < names.length; i++) {
            TextView dayName = label(names[i], 9.2f, (i == 0 || i == 6) ? ui.accent : ui.secondary, true);
            dayName.setGravity(Gravity.CENTER);
            dow.addView(dayName, weightedCell(ui.dp(26)));
        }
        body.addView(dow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(30)));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(7);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        Calendar first = Calendar.getInstance();
        first.clear();
        first.set(year, month - 1, 1, 12, 0, 0);
        int offset = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int i = 0; i < offset; i++) grid.addView(new View(this), weightedCell(ui.dp(72)));
        Calendar now = Calendar.getInstance();
        for (int day = 1; day <= days; day++)
            grid.addView(monthDayCell(year, month, day, now), weightedCell(ui.dp(72)));
        int used = offset + days;
        int remaining = (7 - (used % 7)) % 7;
        for (int i = 0; i < remaining; i++) grid.addView(new View(this), weightedCell(ui.dp(72)));
        body.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout summaryRow = new LinearLayout(this);
        summaryRow.setGravity(Gravity.CENTER);
        summaryRow.setPadding(ui.dp(8), ui.dp(7), ui.dp(8), ui.dp(7));
        summaryRow.setBackground(ui.rounded(ui.card, 18, 1, ui.stroke));
        int active = ReadingStatsStore.activeDaysForMonth(prefs, year, month);
        int books = ReadingStatsStore.uniqueBooksForMonth(prefs, year, month);
        long time = ReadingStatsStore.readingTimeForMonth(prefs, year, month);
        summaryRow.addView(metric(String.valueOf(active), "Days read"), new LinearLayout.LayoutParams(0, ui.dp(54), 1f));
        summaryRow.addView(divider(), new LinearLayout.LayoutParams(ui.dp(1), ui.dp(34)));
        summaryRow.addView(metric(String.valueOf(books), "Books read"), new LinearLayout.LayoutParams(0, ui.dp(54), 1f));
        summaryRow.addView(divider(), new LinearLayout.LayoutParams(ui.dp(1), ui.dp(34)));
        summaryRow.addView(metric(ui.formatDuration(time), "Reading time"), new LinearLayout.LayoutParams(0, ui.dp(54), 1f));
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(68));
        sumLp.topMargin = ui.dp(7);
        body.addView(summaryRow, sumLp);

        long startMs = ReadingRecapData.startOfMonth(anchor.getTimeInMillis());
        long endMs = ReadingRecapData.endOfMonth(anchor.getTimeInMillis());
        ReadingRecapData.Summary recap = ReadingRecapData.summarize(this, prefs, startMs, endMs);
        String period = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(anchor.getTime());
        addRecapCard(body, recap, "month", period);
    }

    private View monthDayCell(int year, int month, int day, Calendar now) {
        final String key = ReadingStatsStore.dayKey(year, month, day);
        final long activityMs = ReadingStatsStore.dayTime(prefs, key);
        final List<ReadingStatsStore.DayBook> books = ReadingStatsStore.booksForDay(prefs, key);
        boolean isToday = year == now.get(Calendar.YEAR) && month == now.get(Calendar.MONTH) + 1 &&
                day == now.get(Calendar.DAY_OF_MONTH);

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cell.setPadding(ui.dp(2), ui.dp(3), ui.dp(2), ui.dp(2));
        int fill = isToday ? blend(ui.accent, ui.card, 0.91f) : ui.card;
        cell.setBackground(ui.rounded(fill, 8, 1, isToday ? ui.accent : ui.stroke));
        cell.setClickable(true);
        cell.setOnClickListener(v -> openDay(year, month, day, key));

        boolean hasDailyNote = !ReadingStatsStore.dailyNote(prefs, key).isEmpty();
        String numberText = hasDailyNote ? day + " •" : String.valueOf(day);
        TextView number = label(numberText, 10.8f, isToday || hasDailyNote ? ui.accent : ui.primary, true);
        number.setGravity(Gravity.CENTER);
        cell.addView(number, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(18)));
        MyanmarCalendarBridge.Info info = MyanmarCalendarBridge.info(year, month, day);
        String lunar = info.moonPhase + (info.fortnightDay.isEmpty() ? "" : " " + info.fortnightDay);
        TextView lunarText = label(lunar.trim(), 7.4f, ui.secondary, false);
        lunarText.setGravity(Gravity.CENTER);
        lunarText.setSingleLine(true);
        cell.addView(lunarText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(15)));

        if (!books.isEmpty()) {
            LinearLayout covers = new LinearLayout(this);
            covers.setGravity(Gravity.CENTER);
            int max = Math.min(2, books.size());
            for (int i = 0; i < max; i++) {
                ReadingStatsStore.DayBook db = books.get(i);
                ImageView cover = new ImageView(this);
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cover.setClipToOutline(true);
                cover.setBackground(ui.rounded(ui.control, 4, 0, 0));
                BookVisualUtil.loadCover(this, new File(libraryDir, db.fileName), cover, ui.dp(24), ui.dp(34));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ui.dp(21), ui.dp(31));
                if (i > 0) cp.leftMargin = ui.dp(2);
                covers.addView(cover, cp);
            }
            cell.addView(covers, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(33)));
            if (books.size() > 2) {
                TextView more = label("+" + (books.size() - 2), 7.5f, ui.accent, true);
                more.setGravity(Gravity.CENTER);
                cell.addView(more, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(9)));
            }
        } else if (activityMs > 0L) {
            TextView old = label("●  " + shortDuration(activityMs), 7.7f, ui.accent, true);
            old.setGravity(Gravity.CENTER);
            cell.addView(old, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(29)));
        }
        return cell;
    }

    private void renderYear(LinearLayout body) {
        int year = anchor.get(Calendar.YEAR);
        addPeriodNavigator(body, String.valueOf(year), "Your reading year at a glance", -1, 1);

        GridLayout months = new GridLayout(this);
        months.setColumnCount(3);
        months.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        for (int m = 1; m <= 12; m++) {
            final int month = m;
            Calendar mc = Calendar.getInstance();
            mc.clear();
            mc.set(year, m - 1, 15, 12, 0, 0);
            ReadingRecapData.Summary monthSummary = ReadingRecapData.summarize(this, prefs,
                    ReadingRecapData.startOfMonth(mc.getTimeInMillis()),
                    ReadingRecapData.endOfMonth(mc.getTimeInMillis()));

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(ui.dp(5), ui.dp(8), ui.dp(5), ui.dp(7));
            boolean current = year == Calendar.getInstance().get(Calendar.YEAR) &&
                    m == Calendar.getInstance().get(Calendar.MONTH) + 1;
            card.setBackground(ui.rounded(current ? blend(ui.accent, ui.card, 0.91f) : ui.card,
                    14, 1, current ? ui.accent : ui.stroke));
            card.setOnClickListener(v -> {
                anchor.set(Calendar.YEAR, year);
                anchor.set(Calendar.MONTH, month - 1);
                anchor.set(Calendar.DAY_OF_MONTH, 15);
                viewMode = "month";
                render();
            });

            TextView name = label(new SimpleDateFormat("MMM", Locale.ENGLISH).format(mc.getTime()),
                    12.5f, current ? ui.accent : ui.primary, true);
            name.setGravity(Gravity.CENTER);
            card.addView(name);
            TextView finished = label(monthSummary.finishedBooks.size() + " finished", 10, ui.accent, true);
            finished.setGravity(Gravity.CENTER);
            finished.setPadding(0, ui.dp(5), 0, 0);
            card.addView(finished);
            TextView days = label(monthSummary.activeDays + " reading days", 8.5f, ui.secondary, false);
            days.setGravity(Gravity.CENTER);
            days.setPadding(0, ui.dp(3), 0, 0);
            card.addView(days);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = ui.dp(86);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2));
            months.addView(card, lp);
        }
        LinearLayout.LayoutParams monthsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        monthsLp.topMargin = ui.dp(7);
        body.addView(months, monthsLp);

        long startMs = ReadingRecapData.startOfYear(anchor.getTimeInMillis());
        long endMs = ReadingRecapData.endOfYear(anchor.getTimeInMillis());
        ReadingRecapData.Summary recap = ReadingRecapData.summarize(this, prefs, startMs, endMs);
        addRecapCard(body, recap, "year", String.valueOf(year));
    }

    private void addPeriodNavigator(LinearLayout body, String title, String subtitle, int prevDelta, int nextDelta) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3));
        card.setBackground(ui.rounded(ui.card, 18, 1, ui.stroke));

        TextView prev = icon("‹", 27);
        prev.setOnClickListener(v -> movePeriod(prevDelta));
        card.addView(prev, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(54)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER);
        TextView t = label(title, 17.5f, ui.primary, true);
        t.setGravity(Gravity.CENTER);
        TextView s = label(subtitle, 10.5f, ui.secondary, false);
        s.setGravity(Gravity.CENTER);
        copy.addView(t);
        copy.addView(s);
        card.addView(copy, new LinearLayout.LayoutParams(0, ui.dp(54), 1f));

        TextView next = icon("›", 27);
        next.setOnClickListener(v -> movePeriod(nextDelta));
        card.addView(next, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(54)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(60));
        lp.topMargin = ui.dp(5);
        body.addView(card, lp);
    }

    private void addRecapCard(LinearLayout body, ReadingRecapData.Summary summary,
                              String mode, String periodLabel) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(ui.dp(14), ui.dp(13), ui.dp(14), ui.dp(13));
        card.setBackground(ui.rounded(ui.card, 20, 1, ui.stroke));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = label(summary.finishedBooks.isEmpty() ? "○" : "★", 24, ui.accent, true);
        icon.setGravity(Gravity.CENTER);
        head.addView(icon, new LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = label(recapTitle(mode), 16.5f, ui.primary, true);
        TextView sub = label(recapSubtitle(summary, mode), 10.5f, ui.secondary, false);
        copy.addView(title);
        copy.addView(sub);
        head.addView(copy, new LinearLayout.LayoutParams(0, ui.dp(46), 1f));
        TextView state = label(summary.endMs < ReadingRecapData.startOfDay(System.currentTimeMillis()) ?
                "Complete" : "In progress", 9, ui.accent, true);
        state.setGravity(Gravity.CENTER);
        state.setBackground(ui.rounded(ui.control, 14, 1, ui.stroke));
        head.addView(state, new LinearLayout.LayoutParams(ui.dp(72), ui.dp(30)));
        card.addView(head);

        if (!summary.finishedBooks.isEmpty()) {
            HorizontalScrollView scroller = new HorizontalScrollView(this);
            scroller.setHorizontalScrollBarEnabled(false);
            LinearLayout strip = new LinearLayout(this);
            strip.setGravity(Gravity.CENTER_VERTICAL);
            strip.setPadding(0, ui.dp(9), ui.dp(8), ui.dp(5));
            int show = Math.min(10, summary.finishedBooks.size());
            for (int i = 0; i < show; i++) {
                ReadingRecapData.FinishedBook book = summary.finishedBooks.get(i);
                ImageView cover = new ImageView(this);
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cover.setBackground(ui.rounded(ui.control, 7, 0, 0));
                cover.setClipToOutline(true);
                BookVisualUtil.loadCover(this, book.file, cover, ui.dp(45), ui.dp(64));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ui.dp(45), ui.dp(64));
                if (i > 0) cp.leftMargin = ui.dp(7);
                strip.addView(cover, cp);
            }
            scroller.addView(strip, new HorizontalScrollView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            card.addView(scroller, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(80)));
        }

        LinearLayout stats = new LinearLayout(this);
        stats.setGravity(Gravity.CENTER);
        stats.addView(metric(String.valueOf(summary.finishedBooks.size()), "Finished"), new LinearLayout.LayoutParams(0, ui.dp(50), 1f));
        stats.addView(divider(), new LinearLayout.LayoutParams(ui.dp(1), ui.dp(30)));
        stats.addView(metric(String.valueOf(summary.activeDays), "Reading days"), new LinearLayout.LayoutParams(0, ui.dp(50), 1f));
        stats.addView(divider(), new LinearLayout.LayoutParams(ui.dp(1), ui.dp(30)));
        stats.addView(metric(ui.formatDuration(summary.readingMs), "Read time"), new LinearLayout.LayoutParams(0, ui.dp(50), 1f));
        card.addView(stats, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(54)));

        TextView share = label("View & Share  ↗", 12.5f, Color.WHITE, true);
        share.setGravity(Gravity.CENTER);
        share.setBackground(ui.rounded(ui.accent, 18, 0, 0));
        share.setOnClickListener(v -> openRecap(summary, mode, periodLabel));
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(42));
        shareLp.topMargin = ui.dp(8);
        card.addView(share, shareLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ui.dp(10);
        body.addView(card, lp);
    }

    private String recapTitle(String mode) {
        if ("week".equals(mode)) return "Week in Books";
        if ("year".equals(mode)) return anchor.get(Calendar.YEAR) + " in Books";
        return new SimpleDateFormat("MMMM", Locale.ENGLISH).format(anchor.getTime()) + " in Books";
    }

    private String recapSubtitle(ReadingRecapData.Summary summary, String mode) {
        int count = summary.finishedBooks.size();
        if (count == 0) return "No finished books in this period yet";
        return "You finished " + count + (count == 1 ? " book" : " books") + " in this " + mode;
    }

    private void openRecap(ReadingRecapData.Summary summary, String mode, String periodLabel) {
        Intent i = new Intent(this, ReadingRecapActivity.class);
        i.putExtra("start_ms", summary.startMs);
        i.putExtra("end_ms", summary.endMs);
        i.putExtra("mode", mode);
        i.putExtra("period_label", periodLabel);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void openDay(int year, int month, int day, String key) {
        Intent i = new Intent(this, ReadingDayActivity.class);
        i.putExtra("year", year);
        i.putExtra("month", month);
        i.putExtra("day", day);
        i.putExtra("day_key", key);
        startActivity(i);
    }

    private void movePeriod(int delta) {
        if ("week".equals(viewMode)) anchor.add(Calendar.DAY_OF_MONTH, delta * 7);
        else if ("year".equals(viewMode)) anchor.add(Calendar.YEAR, delta);
        else anchor.add(Calendar.MONTH, delta);
        render();
    }

    private TextView icon(String value, float size) {
        TextView v = label(value, size, ui.accent, false);
        v.setGravity(Gravity.CENTER);
        v.setBackground(ui.rounded(ui.control, 18, 1, ui.stroke));
        return v;
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text == null ? "" : text);
        ui.text(v, size, color, bold);
        return v;
    }

    private View metric(String value, String name) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView v = label(value, 14, ui.primary, true);
        v.setGravity(Gravity.CENTER);
        TextView n = label(name, 8.7f, ui.secondary, false);
        n.setGravity(Gravity.CENTER);
        box.addView(v);
        box.addView(n);
        return box;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(ui.stroke);
        return v;
    }

    private GridLayout.LayoutParams weightedCell(int h) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = h;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(ui.dp(1), ui.dp(1), ui.dp(1), ui.dp(1));
        return lp;
    }

    private String shortDuration(long ms) {
        long minutes = Math.max(0L, ms) / 60_000L;
        if (minutes < 1L) return "<1m";
        if (minutes < 60L) return minutes + "m";
        return (minutes / 60L) + "h";
    }

    private String formatWeekRange(long startMs, long endMs) {
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        a.setTimeInMillis(startMs);
        b.setTimeInMillis(endMs);
        SimpleDateFormat first = new SimpleDateFormat("MMM d", Locale.ENGLISH);
        SimpleDateFormat second = new SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH);
        if (a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)) {
            return first.format(new Date(startMs)) + " – " +
                    new SimpleDateFormat("d, yyyy", Locale.ENGLISH).format(new Date(endMs));
        }
        return first.format(new Date(startMs)) + " – " + second.format(new Date(endMs));
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private static int blend(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(Math.round(Color.red(from) * (1f - t) + Color.red(to) * t),
                Math.round(Color.green(from) * (1f - t) + Color.green(to) * t),
                Math.round(Color.blue(from) * (1f - t) + Color.blue(to) * t));
    }
}
