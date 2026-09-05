package com.whisper.wowreader;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ReadingDayActivity extends Activity {
    private SharedPreferences prefs;
    private ReadingCalendarUi ui;
    private File libraryDir;
    private int year, month, day;
    private String dayKey;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("wow_reader", MODE_PRIVATE);
        ui = new ReadingCalendarUi(this);
        libraryDir = new File(getFilesDir(), "library");
        year = getIntent().getIntExtra("year", Calendar.getInstance().get(Calendar.YEAR));
        month = getIntent().getIntExtra("month", Calendar.getInstance().get(Calendar.MONTH) + 1);
        day = getIntent().getIntExtra("day", Calendar.getInstance().get(Calendar.DAY_OF_MONTH));
        dayKey = getIntent().getStringExtra("day_key");
        if (dayKey == null || dayKey.isEmpty()) dayKey = ReadingStatsStore.dayKey(year, month, day);
        render();
    }

    @Override protected void onRestart() { super.onRestart(); if (ui != null) render(); }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ui.background);
        root.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12));

        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = button("‹", 28); back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(ui.dp(46), ui.dp(46)));
        TextView topTitle = text("Reading Day", 20, ui.primary, true);
        top.addView(topTitle, new LinearLayout.LayoutParams(0, ui.dp(46), 1f));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(52)));

        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(0, ui.dp(4), 0, ui.dp(18));
        scroll.addView(content);

        Calendar date = Calendar.getInstance(); date.clear(); date.set(year, month - 1, day, 12, 0, 0);
        MyanmarCalendarBridge.Info info = MyanmarCalendarBridge.info(year, month, day);
        LinearLayout hero = card(); hero.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14)); hero.setGravity(Gravity.CENTER);
        TextView mm = text(info.monthName + (info.moonPhase.isEmpty() ? "" : " · " + info.moonPhase + " " + info.fortnightDay), 18, ui.primary, true); mm.setGravity(Gravity.CENTER);
        TextView yr = text(info.year.isEmpty() ? "" : "မြန်မာနှစ် " + info.year, 11, ui.accent, true); yr.setGravity(Gravity.CENTER);
        TextView west = text(new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(date.getTime()), 10.5f, ui.secondary, false); west.setGravity(Gravity.CENTER);
        hero.addView(mm); hero.addView(yr); hero.addView(west);
        content.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(96)));

        final long total = ReadingStatsStore.dayTime(prefs, dayKey);
        final List<ReadingStatsStore.DayBook> books = ReadingStatsStore.booksForDay(prefs, dayKey);
        LinearLayout metrics = new LinearLayout(this); metrics.setGravity(Gravity.CENTER); metrics.setPadding(ui.dp(6), ui.dp(5), ui.dp(6), ui.dp(5)); metrics.setBackground(ui.rounded(ui.card, 18, 1, ui.stroke));
        metrics.addView(metric(String.valueOf(books.size()), "Books read"), new LinearLayout.LayoutParams(0, ui.dp(52), 1f));
        View line = new View(this); line.setBackgroundColor(ui.stroke); metrics.addView(line, new LinearLayout.LayoutParams(ui.dp(1), ui.dp(32)));
        metrics.addView(metric(ui.formatDuration(total), "Reading time"), new LinearLayout.LayoutParams(0, ui.dp(52), 1f));
        LinearLayout.LayoutParams metricsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(64)); metricsLp.topMargin = ui.dp(8); content.addView(metrics, metricsLp);

        TextView section = text("Books from this day", 15.5f, ui.primary, true); section.setPadding(ui.dp(2), ui.dp(14), ui.dp(2), ui.dp(6)); content.addView(section);
        if (books.isEmpty()) {
            TextView empty = text(total > 0 ? "Reading activity is saved for this day. Book-by-day tracking starts with the new calendar version." : "No reading activity on this day yet.", 12, ui.secondary, false);
            empty.setGravity(Gravity.CENTER); empty.setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(18)); empty.setBackground(ui.rounded(ui.card, 16, 1, ui.stroke));
            content.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(88)));
        } else {
            for (ReadingStatsStore.DayBook item : books) content.addView(bookRow(item));
        }

        LinearLayout noteCard = card(); noteCard.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        LinearLayout noteHead = new LinearLayout(this); noteHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView noteTitle = text("Daily Reading Note", 14.5f, ui.primary, true); noteHead.addView(noteTitle, new LinearLayout.LayoutParams(0, ui.dp(36), 1f));
        TextView edit = text("Edit", 11.5f, ui.accent, true); edit.setGravity(Gravity.CENTER); edit.setBackground(ui.rounded(ui.control, 16, 1, ui.stroke)); edit.setOnClickListener(v -> editDailyNote());
        noteHead.addView(edit, new LinearLayout.LayoutParams(ui.dp(62), ui.dp(34))); noteCard.addView(noteHead);
        String note = ReadingStatsStore.dailyNote(prefs, dayKey);
        TextView noteBody = text(note.isEmpty() ? "နေ့ရက်အတွက် ဖတ်ရှုမှတ်စုလေးရေးနိုင်ပါတယ်။" : note, 12.5f, note.isEmpty() ? ui.secondary : ui.primary, false);
        noteBody.setPadding(ui.dp(2), ui.dp(7), ui.dp(2), ui.dp(4)); noteBody.setLineSpacing(ui.dp(2), 1.12f); noteCard.addView(noteBody);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); noteLp.topMargin = ui.dp(14); content.addView(noteCard, noteLp);

        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root); AppWindowInsets.apply(this, root, ui.background, ui.darkSystemIcons);
    }

    private View bookRow(ReadingStatsStore.DayBook item) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(ui.dp(9), ui.dp(8), ui.dp(10), ui.dp(8)); row.setBackground(ui.rounded(ui.card, 16, 1, ui.stroke)); row.setClickable(true);
        File file = new File(libraryDir, item.fileName);
        ImageView cover = new ImageView(this); cover.setScaleType(ImageView.ScaleType.CENTER_CROP); cover.setClipToOutline(true); cover.setBackground(ui.rounded(ui.control, 8, 0, 0));
        row.addView(cover, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(74))); BookVisualUtil.loadCover(this, file, cover, ui.dp(130), ui.dp(190));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); copy.setPadding(ui.dp(12), 0, ui.dp(5), 0); copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(BookVisualUtil.title(prefs, item.fileName), 13.5f, ui.primary, true); title.setMaxLines(2); copy.addView(title);
        String author = BookVisualUtil.author(prefs, item.fileName);
        TextView meta = text((author.isEmpty() ? "" : author + " · ") + ui.formatDuration(item.durationMs), 10.3f, ui.secondary, false); meta.setPadding(0, ui.dp(4), 0, 0); copy.addView(meta);
        String memory = ReadingStatsStore.bookDayNote(prefs, dayKey, item.fileName);
        if (!memory.isEmpty()) { TextView mem = text(memory, 9.8f, ui.accent, false); mem.setMaxLines(1); mem.setPadding(0, ui.dp(5), 0, 0); copy.addView(mem); }
        row.addView(copy, new LinearLayout.LayoutParams(0, ui.dp(74), 1f));
        TextView arrow = text("›", 22, ui.secondary, false); arrow.setGravity(Gravity.CENTER); row.addView(arrow, new LinearLayout.LayoutParams(ui.dp(26), ui.dp(58)));
        row.setOnClickListener(v -> { Intent i = new Intent(this, ReadingMemoryActivity.class); i.putExtra("book_name", item.fileName); i.putExtra("day_key", dayKey); i.putExtra("year", year); i.putExtra("month", month); i.putExtra("day", day); startActivity(i); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(90)); lp.bottomMargin = ui.dp(7); row.setLayoutParams(lp); return row;
    }

    private void editDailyNote() {
        Dialog dialog = new Dialog(this); dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout box = card(); box.setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(16));
        TextView title = text("Daily Reading Note", 19, ui.primary, true); box.addView(title);
        EditText input = new EditText(this); input.setText(ReadingStatsStore.dailyNote(prefs, dayKey)); input.setHint("ဒီနေ့ ဖတ်ခဲ့တာနဲ့ ပတ်သက်ပြီး မှတ်ထားရန်…"); input.setTextSize(13); input.setTextColor(ui.primary); input.setHintTextColor(ui.secondary); input.setGravity(Gravity.TOP); input.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10)); input.setBackground(ui.rounded(ui.control, 15, 1, ui.stroke)); input.setTypeface(ui.myanmarTypeface); box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(146)));
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = action("Cancel", false); cancel.setOnClickListener(v -> dialog.dismiss());
        TextView save = action("Save", true); save.setOnClickListener(v -> { ReadingStatsStore.setDailyNote(prefs, dayKey, input.getText().toString()); dialog.dismiss(); render(); GoogleAutoSync.scheduleSoon(this); });
        LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(ui.dp(88), ui.dp(40)); c.rightMargin = ui.dp(8); actions.addView(cancel, c); actions.addView(save, new LinearLayout.LayoutParams(ui.dp(90), ui.dp(40))); box.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(54)));
        dialog.setContentView(box); dialog.show(); styleDialog(dialog, .90f);
    }

    private LinearLayout card() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setBackground(ui.rounded(ui.card, 18, 1, ui.stroke)); return v; }
    private TextView text(String value, float size, int color, boolean bold) { TextView v = new TextView(this); v.setText(value == null ? "" : value); ui.text(v, size, color, bold); return v; }
    private TextView button(String value, float size) { TextView v = text(value, size, ui.accent, false); v.setGravity(Gravity.CENTER); v.setBackground(ui.rounded(ui.control, 18, 1, ui.stroke)); return v; }
    private View metric(String value, String label) { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER); TextView a = text(value, 14, ui.primary, true); a.setGravity(Gravity.CENTER); TextView b = text(label, 9, ui.secondary, false); b.setGravity(Gravity.CENTER); box.addView(a); box.addView(b); return box; }
    private TextView action(String value, boolean primary) { TextView v = text(value, 12, primary ? Color.WHITE : ui.primary, true); v.setGravity(Gravity.CENTER); v.setBackground(ui.rounded(primary ? ui.accent : ui.control, 16, 1, primary ? ui.accent : ui.stroke)); return v; }
    private void styleDialog(Dialog dialog, float fraction) { android.view.Window w = dialog.getWindow(); if (w == null) return; w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); w.setDimAmount(.42f); w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND); int sw = getResources().getDisplayMetrics().widthPixels; w.setLayout(Math.min((int)(sw * fraction), ui.dp(620)), ViewGroup.LayoutParams.WRAP_CONTENT); }
}
