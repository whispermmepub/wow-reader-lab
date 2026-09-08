package com.whisper.wowreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.Locale;

/** Manage bundled and user-imported dictionary sources without leaving WoW Reader. */
public class DictionaryManagerActivity extends Activity {
    private static final int REQ_KINDLE_DICT = 9701;
    private LinearLayout root;
    private boolean importing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(248, 249, 251));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView back = label("‹  Dictionary sources", 21, Color.rgb(30, 33, 38), Typeface.BOLD);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        TextView sub = label("Offline sources stay on this device. Online Wiktionary opens inside WoW Reader.",
                13, Color.rgb(100, 105, 115), Typeface.NORMAL);
        sub.setPadding(0, 0, 0, dp(14));
        root.addView(sub);

        addSource("Wiktionary offline", "Built in · English ↔ မြန်မာ", "Ready");
        int spelling = MyanmarSpellingStore.count(this);
        addSource("မြန်မာစာလုံးပေါင်း သတ်ပုံကျမ်း", "Offline spelling word list",
                spelling > 0 ? number(spelling) + " words" : "Unavailable");

        boolean installed = KindleDictionaryStore.isInstalled(this);
        int count = installed ? KindleDictionaryStore.count(this) : 0;
        String source = installed ? KindleDictionaryStore.sourceName(this) : "No Kindle dictionary imported";
        addSource("Kindle dictionary (.prc/.mobi)", source,
                installed ? number(count) + " entries" : "Optional");

        TextView importButton = button(installed ? "Replace PRC / MOBI dictionary" : "Import PRC / MOBI dictionary");
        importButton.setOnClickListener(v -> pickKindleDictionary());
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        importLp.topMargin = dp(16);
        root.addView(importButton, importLp);

        if (installed) {
            TextView remove = button("Remove imported dictionary");
            remove.setTextColor(Color.rgb(180, 55, 55));
            remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Remove imported dictionary?")
                    .setMessage("The original PRC/MOBI file will not be deleted.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove", (d, w) -> {
                        KindleDictionaryStore.remove(this);
                        buildUi();
                    }).show());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
            lp.topMargin = dp(8);
            root.addView(remove, lp);
        }

        TextView note = label("Kindle import supports DRM-free Unicode .prc and .mobi PalmDOC/MOBI7 dictionaries. " +
                        "Legacy Myanmar visual-order text is normalized for modern Android display. The imported file is indexed privately; WoW Reader does not upload it.",
                11.5f, Color.rgb(112, 116, 124), Typeface.NORMAL);
        note.setPadding(dp(2), dp(18), dp(2), 0);
        root.addView(note);

        setContentView(scroll);
    }

    private void addSource(String title, String detail, String status) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(13), dp(16), dp(13));
        card.setBackground(roundRect(Color.WHITE, dp(18), dp(1), Color.rgb(222, 225, 231)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = label(title, 15.5f, Color.rgb(35, 38, 44), Typeface.BOLD);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView s = label(status, 11.5f, Color.rgb(64, 105, 190), Typeface.BOLD);
        s.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        card.addView(row);
        TextView d = label(detail, 12, Color.rgb(101, 106, 116), Typeface.NORMAL);
        d.setPadding(0, dp(2), 0, 0);
        card.addView(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(9);
        root.addView(card, lp);
    }

    private void pickKindleDictionary() {
        if (importing) return;
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        pick.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/octet-stream", "application/x-mobipocket-ebook", "application/vnd.amazon.ebook",
                "application/x-mobi", "application/mobi", "application/vnd.amazon.mobi8-ebook"
        });
        try { startActivityForResult(pick, REQ_KINDLE_DICT); }
        catch (Exception e) { Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_KINDLE_DICT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) {}
        importing = true;
        final AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("Importing Kindle dictionary")
                .setMessage("Building the offline index… This can take a little while for a large dictionary.")
                .setCancelable(false).create();
        progress.show();
        new Thread(() -> {
            try {
                KindleDictionaryStore.ImportResult result = KindleDictionaryStore.importFromUri(this, uri);
                runOnUiThread(() -> {
                    importing = false;
                    progress.dismiss();
                    Toast.makeText(this, result.sourceName + " · " + number(result.entryCount) + " entries", Toast.LENGTH_LONG).show();
                    buildUi();
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Dictionary import failed" : e.getMessage();
                runOnUiThread(() -> {
                    importing = false;
                    progress.dismiss();
                    new AlertDialog.Builder(this).setTitle("Dictionary import failed")
                            .setMessage(message).setPositiveButton("OK", null).show();
                });
            }
        }, "wow-kindle-dictionary-import").start();
    }

    private GradientDrawable roundRect(int fill, int radius, int strokeWidth, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (strokeWidth > 0) g.setStroke(strokeWidth, strokeColor);
        return g;
    }

    private TextView button(String text) {
        TextView v = label(text, 14, Color.rgb(55, 91, 174), Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setBackground(roundRect(Color.WHITE, dp(16), dp(1), Color.rgb(205, 211, 224)));
        return v;
    }

    private TextView label(String text, float size, int color, int style) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextSize(size); v.setTextColor(color); v.setTypeface(Typeface.DEFAULT, style);
        return v;
    }

    private String number(int value) { return NumberFormat.getIntegerInstance(Locale.US).format(value); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
