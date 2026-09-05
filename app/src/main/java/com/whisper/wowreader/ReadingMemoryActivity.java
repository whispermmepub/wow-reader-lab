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
import java.util.Locale;

public class ReadingMemoryActivity extends Activity {
    private SharedPreferences prefs;
    private ReadingCalendarUi ui;
    private String bookName, dayKey;
    private int year, month, day;
    private File bookFile;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("wow_reader", MODE_PRIVATE); ui = new ReadingCalendarUi(this);
        bookName = getIntent().getStringExtra("book_name"); dayKey = getIntent().getStringExtra("day_key");
        year = getIntent().getIntExtra("year", Calendar.getInstance().get(Calendar.YEAR)); month = getIntent().getIntExtra("month", Calendar.getInstance().get(Calendar.MONTH) + 1); day = getIntent().getIntExtra("day", Calendar.getInstance().get(Calendar.DAY_OF_MONTH));
        if (dayKey == null) dayKey = ReadingStatsStore.dayKey(year, month, day);
        bookFile = new File(new File(getFilesDir(), "library"), bookName == null ? "" : bookName);
        render();
    }

    @Override protected void onRestart() { super.onRestart(); if (ui != null) render(); }

    private void render() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12)); root.setBackgroundColor(ui.background);
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = smallButton("‹", 28); back.setOnClickListener(v -> finish()); top.addView(back, new LinearLayout.LayoutParams(ui.dp(46), ui.dp(46)));
        TextView head = text("Book Details", 20, ui.primary, true); top.addView(head, new LinearLayout.LayoutParams(0, ui.dp(46), 1f)); root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(52)));

        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false); LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(0, ui.dp(5), 0, ui.dp(18)); scroll.addView(content);
        LinearLayout bookCard = card(); bookCard.setOrientation(LinearLayout.HORIZONTAL); bookCard.setGravity(Gravity.CENTER_VERTICAL); bookCard.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        ImageView cover = new ImageView(this); cover.setScaleType(ImageView.ScaleType.CENTER_CROP); cover.setClipToOutline(true); cover.setBackground(ui.rounded(ui.control, 10, 0, 0)); bookCard.addView(cover, new LinearLayout.LayoutParams(ui.dp(92), ui.dp(134))); BookVisualUtil.loadCover(this, bookFile, cover, ui.dp(230), ui.dp(340));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); copy.setPadding(ui.dp(14), 0, 0, 0); copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(BookVisualUtil.title(prefs, bookName), 17, ui.primary, true); title.setMaxLines(3); copy.addView(title);
        String author = BookVisualUtil.author(prefs, bookName); TextView a = text(author.isEmpty() ? "Unknown author" : author, 11, ui.secondary, false); a.setPadding(0, ui.dp(5), 0, 0); copy.addView(a);
        int progress = prefs.getInt("percent_" + bookName, 0); TextView p = text(progress >= 100 ? "✓ Finished · 100%" : progress + "% read", 11.5f, ui.accent, true); p.setPadding(0, ui.dp(11), 0, 0); copy.addView(p);
        bookCard.addView(copy, new LinearLayout.LayoutParams(0, ui.dp(134), 1f)); content.addView(bookCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(158)));

        Calendar date = Calendar.getInstance(); date.clear(); date.set(year, month - 1, day, 12, 0, 0); MyanmarCalendarBridge.Info info = MyanmarCalendarBridge.info(year, month, day);
        TextView dateTitle = text((info.monthName.isEmpty() ? "" : info.monthName + " · ") + new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(date.getTime()), 12, ui.secondary, true); dateTitle.setPadding(ui.dp(3), ui.dp(12), ui.dp(3), ui.dp(5)); content.addView(dateTitle);
        LinearLayout stats = new LinearLayout(this); stats.setGravity(Gravity.CENTER); stats.setBackground(ui.rounded(ui.card, 16, 1, ui.stroke));
        stats.addView(metric(ui.formatDuration(ReadingStatsStore.bookTimeForDay(prefs, dayKey, bookName)), "This day"), new LinearLayout.LayoutParams(0, ui.dp(54), 1f));
        stats.addView(metric(ui.formatDuration(ReadingStatsStore.totalBookTime(prefs, bookName)), "Total reading"), new LinearLayout.LayoutParams(0, ui.dp(54), 1f)); content.addView(stats, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(62)));

        LinearLayout memory = card(); memory.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(14));
        LinearLayout mh = new LinearLayout(this); mh.setGravity(Gravity.CENTER_VERTICAL); TextView mt = text("Reading Memory", 15.5f, ui.primary, true); mh.addView(mt, new LinearLayout.LayoutParams(0, ui.dp(38), 1f)); TextView edit = text("Edit", 11.5f, ui.accent, true); edit.setGravity(Gravity.CENTER); edit.setBackground(ui.rounded(ui.control, 15, 1, ui.stroke)); edit.setOnClickListener(v -> editMemory()); mh.addView(edit, new LinearLayout.LayoutParams(ui.dp(62), ui.dp(34))); memory.addView(mh);
        String note = ReadingStatsStore.bookDayNote(prefs, dayKey, bookName); TextView body = text(note.isEmpty() ? "ဒီနေ့ ဒီစာအုပ်ဖတ်ရင်း မှတ်ထားချင်တာကို ရေးနိုင်ပါတယ်။" : note, 12.5f, note.isEmpty() ? ui.secondary : ui.primary, false); body.setLineSpacing(ui.dp(2), 1.14f); body.setPadding(ui.dp(2), ui.dp(8), ui.dp(2), ui.dp(4)); memory.addView(body); LinearLayout.LayoutParams memLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); memLp.topMargin = ui.dp(12); content.addView(memory, memLp);

        if (bookFile.isFile()) { TextView open = text("Open book  ›", 13, Color.WHITE, true); open.setGravity(Gravity.CENTER); open.setBackground(ui.rounded(ui.accent, 19, 0, 0)); open.setOnClickListener(v -> { Intent i = new Intent(this, BookReaderActivity.class); i.putExtra("path", bookFile.getAbsolutePath()); startActivity(i); }); LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(48)); op.topMargin = ui.dp(12); content.addView(open, op); }
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)); setContentView(root); AppWindowInsets.apply(this, root, ui.background, ui.darkSystemIcons);
    }

    private void editMemory() {
        Dialog dialog = new Dialog(this); dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE); LinearLayout box = card(); box.setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(16)); box.addView(text("Reading Memory", 19, ui.primary, true));
        EditText input = new EditText(this); input.setText(ReadingStatsStore.bookDayNote(prefs, dayKey, bookName)); input.setHint("ဒီစာအုပ်ဖတ်ရင်း ခံစားချက်၊ မှတ်ချက်…"); input.setTextSize(13); input.setTextColor(ui.primary); input.setHintTextColor(ui.secondary); input.setGravity(Gravity.TOP); input.setTypeface(ui.myanmarTypeface); input.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10)); input.setBackground(ui.rounded(ui.control, 15, 1, ui.stroke)); box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(150)));
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); TextView cancel = action("Cancel", false); cancel.setOnClickListener(v -> dialog.dismiss()); TextView save = action("Save", true); save.setOnClickListener(v -> { ReadingStatsStore.setBookDayNote(prefs, dayKey, bookName, input.getText().toString()); dialog.dismiss(); render(); GoogleAutoSync.scheduleSoon(this); }); LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ui.dp(88), ui.dp(40)); cp.rightMargin = ui.dp(8); actions.addView(cancel, cp); actions.addView(save, new LinearLayout.LayoutParams(ui.dp(90), ui.dp(40))); box.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(54))); dialog.setContentView(box); dialog.show(); styleDialog(dialog);
    }

    private LinearLayout card() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setBackground(ui.rounded(ui.card, 18, 1, ui.stroke)); return v; }
    private TextView text(String value, float size, int color, boolean bold) { TextView v = new TextView(this); v.setText(value == null ? "" : value); ui.text(v, size, color, bold); return v; }
    private TextView smallButton(String value, float size) { TextView v = text(value, size, ui.accent, false); v.setGravity(Gravity.CENTER); v.setBackground(ui.rounded(ui.control, 18, 1, ui.stroke)); return v; }
    private View metric(String value, String label) { LinearLayout b = new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL); b.setGravity(Gravity.CENTER); TextView v = text(value, 14, ui.primary, true); v.setGravity(Gravity.CENTER); TextView l = text(label, 9, ui.secondary, false); l.setGravity(Gravity.CENTER); b.addView(v); b.addView(l); return b; }
    private TextView action(String value, boolean primary) { TextView v = text(value, 12, primary ? Color.WHITE : ui.primary, true); v.setGravity(Gravity.CENTER); v.setBackground(ui.rounded(primary ? ui.accent : ui.control, 16, 1, primary ? ui.accent : ui.stroke)); return v; }
    private void styleDialog(Dialog dialog) { android.view.Window w = dialog.getWindow(); if (w == null) return; w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); w.setDimAmount(.42f); w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND); int sw = getResources().getDisplayMetrics().widthPixels; w.setLayout(Math.min((int)(sw * .90f), ui.dp(620)), ViewGroup.LayoutParams.WRAP_CONTENT); }
}
