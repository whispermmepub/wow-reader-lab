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
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ReadingCalendarActivity extends Activity {
    private SharedPreferences prefs;
    private ReadingCalendarUi ui;
    private File libraryDir;
    private int year;
    private int month;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("wow_reader", MODE_PRIVATE);
        ui = new ReadingCalendarUi(this);
        libraryDir = new File(getFilesDir(), "library");
        Calendar now = Calendar.getInstance();
        year = now.get(Calendar.YEAR);
        month = now.get(Calendar.MONTH) + 1;
        render();
    }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(10));
        root.setBackgroundColor(ui.background);

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
        TextView subtitle = label("Myanmar calendar · your books by day", 10.5f, ui.secondary, false);
        heading.addView(title);
        heading.addView(subtitle);
        top.addView(heading, new LinearLayout.LayoutParams(0, ui.dp(50), 1f));
        TextView today = label("Today", 12, ui.accent, true);
        today.setGravity(Gravity.CENTER);
        today.setBackground(ui.rounded(ui.control, 18, 1, ui.stroke));
        today.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance(); year = now.get(Calendar.YEAR); month = now.get(Calendar.MONTH) + 1; render();
        });
        top.addView(today, new LinearLayout.LayoutParams(ui.dp(68), ui.dp(38)));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(54)));

        LinearLayout monthCard = new LinearLayout(this);
        monthCard.setGravity(Gravity.CENTER_VERTICAL);
        monthCard.setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3));
        monthCard.setBackground(ui.rounded(ui.card, 18, 1, ui.stroke));
        TextView prev = icon("‹", 27);
        prev.setOnClickListener(v -> moveMonth(-1));
        monthCard.addView(prev, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(54)));

        Calendar mid = Calendar.getInstance();
        mid.clear(); mid.set(year, month - 1, 15, 12, 0, 0);
        MyanmarCalendarBridge.Info mi = MyanmarCalendarBridge.info(year, month, 15);
        LinearLayout monthCopy = new LinearLayout(this);
        monthCopy.setOrientation(LinearLayout.VERTICAL);
        monthCopy.setGravity(Gravity.CENTER);
        String mmTitle = mi.monthName.isEmpty() ? "မြန်မာ ပြက္ခဒိန်" : mi.monthName + (mi.year.isEmpty() ? "" : " · " + mi.year);
        TextView mm = label(mmTitle, 17.5f, ui.primary, true);
        mm.setGravity(Gravity.CENTER);
        TextView western = label(new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(mid.getTime()), 10.5f, ui.secondary, false);
        western.setGravity(Gravity.CENTER);
        monthCopy.addView(mm);
        monthCopy.addView(western);
        monthCard.addView(monthCopy, new LinearLayout.LayoutParams(0, ui.dp(54), 1f));
        TextView next = icon("›", 27);
        next.setOnClickListener(v -> moveMonth(1));
        monthCard.addView(next, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(54)));
        LinearLayout.LayoutParams monthLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(60));
        monthLp.topMargin = ui.dp(6); root.addView(monthCard, monthLp);

        GridLayout dow = new GridLayout(this);
        dow.setColumnCount(7);
        String[] names = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
        for (int i = 0; i < names.length; i++) {
            TextView dayName = label(names[i], 9.2f, (i == 0 || i == 6) ? ui.accent : ui.secondary, true);
            dayName.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams lp = weightedCell(ui.dp(26));
            dow.addView(dayName, lp);
        }
        root.addView(dow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(30)));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(7);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        Calendar first = Calendar.getInstance();
        first.clear(); first.set(year, month - 1, 1, 12, 0, 0);
        int offset = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int i = 0; i < offset; i++) grid.addView(new View(this), weightedCell(ui.dp(78)));
        Calendar now = Calendar.getInstance();
        for (int day = 1; day <= days; day++) grid.addView(dayCell(day, now), weightedCell(ui.dp(78)));
        int used = offset + days;
        int remaining = (7 - (used % 7)) % 7;
        for (int i = 0; i < remaining; i++) grid.addView(new View(this), weightedCell(ui.dp(78)));
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setGravity(Gravity.CENTER);
        summary.setPadding(ui.dp(8), ui.dp(7), ui.dp(8), ui.dp(7));
        summary.setBackground(ui.rounded(ui.card, 18, 1, ui.stroke));
        int active = ReadingStatsStore.activeDaysForMonth(prefs, year, month);
        int books = ReadingStatsStore.uniqueBooksForMonth(prefs, year, month);
        long time = ReadingStatsStore.readingTimeForMonth(prefs, year, month);
        summary.addView(metric(String.valueOf(active), "Days read"), new LinearLayout.LayoutParams(0, ui.dp(54), 1f));
        summary.addView(divider(), new LinearLayout.LayoutParams(ui.dp(1), ui.dp(34)));
        summary.addView(metric(String.valueOf(books), "Books"), new LinearLayout.LayoutParams(0, ui.dp(54), 1f));
        summary.addView(divider(), new LinearLayout.LayoutParams(ui.dp(1), ui.dp(34)));
        summary.addView(metric(ui.formatDuration(time), "Reading time"), new LinearLayout.LayoutParams(0, ui.dp(54), 1f));
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(68));
        sumLp.topMargin = ui.dp(7); root.addView(summary, sumLp);

        setContentView(root);
        AppWindowInsets.apply(this, root, ui.background, ui.darkSystemIcons);
    }

    private View dayCell(int day, Calendar now) {
        final String key = ReadingStatsStore.dayKey(year, month, day);
        final long activityMs = ReadingStatsStore.dayTime(prefs, key);
        final List<ReadingStatsStore.DayBook> books = ReadingStatsStore.booksForDay(prefs, key);
        boolean isToday = year == now.get(Calendar.YEAR) && month == now.get(Calendar.MONTH) + 1 && day == now.get(Calendar.DAY_OF_MONTH);

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cell.setPadding(ui.dp(2), ui.dp(3), ui.dp(2), ui.dp(2));
        int fill = isToday ? blend(ui.accent, ui.card, 0.91f) : ui.card;
        cell.setBackground(ui.rounded(fill, 8, 1, isToday ? ui.accent : ui.stroke));
        cell.setClickable(true);
        cell.setOnClickListener(v -> openDay(day, key));

        TextView number = label(String.valueOf(day), 10.8f, isToday ? ui.accent : ui.primary, true);
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
                File file = new File(libraryDir, db.fileName);
                BookVisualUtil.loadCover(this, file, cover, ui.dp(24), ui.dp(34));
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
            TextView old = label("●  " + ui.formatDuration(activityMs), 7.7f, ui.accent, true);
            old.setGravity(Gravity.CENTER);
            cell.addView(old, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(29)));
        }
        return cell;
    }

    private void openDay(int day, String key) {
        Intent i = new Intent(this, ReadingDayActivity.class);
        i.putExtra("year", year); i.putExtra("month", month); i.putExtra("day", day); i.putExtra("day_key", key);
        startActivity(i);
    }

    private void moveMonth(int delta) {
        month += delta;
        if (month < 1) { month = 12; year--; }
        if (month > 12) { month = 1; year++; }
        render();
    }

    private TextView icon(String value, float size) {
        TextView v = label(value, size, ui.accent, false);
        v.setGravity(Gravity.CENTER);
        v.setBackground(ui.rounded(ui.control, 18, 1, ui.stroke));
        return v;
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(text == null ? "" : text); ui.text(v, size, color, bold); return v;
    }

    private View metric(String value, String name) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER);
        TextView v = label(value, 14, ui.primary, true); v.setGravity(Gravity.CENTER);
        TextView n = label(name, 8.7f, ui.secondary, false); n.setGravity(Gravity.CENTER);
        box.addView(v); box.addView(n); return box;
    }

    private View divider() { View v = new View(this); v.setBackgroundColor(ui.stroke); return v; }

    private GridLayout.LayoutParams weightedCell(int h) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0; lp.height = h; lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(ui.dp(1), ui.dp(1), ui.dp(1), ui.dp(1)); return lp;
    }

    private static int blend(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(Math.round(Color.red(from) * (1f - t) + Color.red(to) * t),
                Math.round(Color.green(from) * (1f - t) + Color.green(to) * t),
                Math.round(Color.blue(from) * (1f - t) + Color.blue(to) * t));
    }
}
