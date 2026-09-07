package com.whisper.wowreader;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DictionaryDialog {
    private static final String ASSET_DB = "dictionary/wow_dictionary.db";
    private static final String DB_FILE = "wow_dictionary_v2.db";

    private DictionaryDialog() {}

    static void show(Activity activity, String selectedText) {
        show(activity, selectedText, null);
    }

    static void show(Activity activity, String selectedText, Runnable onDismiss) {
        if (activity == null || activity.isFinishing()) return;
        String query = cleanQuery(selectedText);
        if (query.isEmpty()) {
            Toast.makeText(activity, "Select a word first", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean myanmar = hasMyanmar(query);
        int direction = myanmar ? 1 : 0; // 0 = English→Myanmar, 1 = Myanmar→English
        List<Entry> entries = lookup(activity, query, direction);
        if (!myanmar) {
            List<KindleDictionaryStore.Result> imported = KindleDictionaryStore.lookup(activity, query, 6);
            for (int i = imported.size() - 1; i >= 0; i--) {
                KindleDictionaryStore.Result r = imported.get(i);
                entries.add(0, new Entry(r.headword, "Kindle Dictionary · Offline", r.meaning, "", ""));
            }
        }
        MyanmarSpellingStore.Match spelling = myanmar ? MyanmarSpellingStore.lookup(activity, query) : null;

        SharedPreferences prefs = activity.getSharedPreferences("wow_reader", Context.MODE_PRIVATE);
        String sourceBook = "";
        try {
            String sourcePath = activity.getIntent() == null ? null : activity.getIntent().getStringExtra("path");
            if (sourcePath != null) sourceBook = new File(sourcePath).getName();
        } catch (Exception ignored) {}
        if (query.split("\\s+").length <= 3) VocabularyStore.record(prefs, query, direction, sourceBook);
        int theme = prefs.getInt("reader_theme", 0);
        Palette p = palette(theme);

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(activity, 18), dp(activity, 14), dp(activity, 18), dp(activity, 16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(p.surface);
        bg.setCornerRadii(new float[]{dp(activity, 24), dp(activity, 24), dp(activity, 24), dp(activity, 24), 0, 0, 0, 0});
        sheet.setBackground(bg);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text(activity, "Dictionary", 15, p.subText, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = text(activity, "✕", 19, p.subText, Typeface.NORMAL);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(activity, 12), dp(activity, 6), dp(activity, 4), dp(activity, 6));
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sheet.addView(header);

        TextView word = text(activity, query, 25, p.text, Typeface.BOLD);
        word.setPadding(0, dp(activity, 8), 0, dp(activity, 3));
        sheet.addView(word);

        String directionLabel = myanmar ? "မြန်မာ → English" : "English → မြန်မာ";
        TextView source = text(activity, directionLabel + "  •  Offline", 12.5f, p.accent, Typeface.BOLD);
        source.setPadding(0, 0, 0, dp(activity, 10));
        sheet.addView(source);

        LinearLayout tabs = new LinearLayout(activity);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, 0, 0, dp(activity, 8));
        TextView offlineTab = pill(activity, "Offline", p.accent, p.surface, p.accent, true);
        TextView onlineTab = pill(activity, "Wiktionary", p.text, p.surface, p.border, false);
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, dp(activity, 40), 1f);
        tabLp.rightMargin = dp(activity, 7);
        tabs.addView(offlineTab, tabLp);
        tabs.addView(onlineTab, new LinearLayout.LayoutParams(0, dp(activity, 40), 1f));
        sheet.addView(tabs);

        FrameLayout body = new FrameLayout(activity);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        bodyLp.topMargin = dp(activity, 2);
        sheet.addView(body, bodyLp);

        ScrollView offlineScroll = new ScrollView(activity);
        offlineScroll.setFillViewport(true);
        LinearLayout results = new LinearLayout(activity);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, dp(activity, 2), 0, dp(activity, 12));
        offlineScroll.addView(results, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(offlineScroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (spelling != null) {
            LinearLayout spellCard = new LinearLayout(activity);
            spellCard.setOrientation(LinearLayout.VERTICAL);
            spellCard.setPadding(dp(activity, 13), dp(activity, 11), dp(activity, 13), dp(activity, 11));
            GradientDrawable spellBg = new GradientDrawable();
            spellBg.setColor(p.card); spellBg.setCornerRadius(dp(activity, 14)); spellBg.setStroke(dp(activity, 1), p.border);
            spellCard.setBackground(spellBg);
            TextView sh = text(activity, "မြန်မာစာလုံးပေါင်း သတ်ပုံကျမ်း", 14.5f, p.text, Typeface.BOLD);
            spellCard.addView(sh);
            String message = spelling.found ? "✓ စာလုံးပေါင်းစာရင်းတွင် တွေ့ရှိသည်" : "စာလုံးပေါင်းစာရင်းတွင် မတွေ့ပါ";
            if (!spelling.suggestions.isEmpty())
                message += "\nအနီးစပ်ဆုံး: " + android.text.TextUtils.join(" · ", spelling.suggestions);
            TextView sm = text(activity, message, 13.5f, spelling.found ? p.accent : p.subText, Typeface.NORMAL);
            sm.setPadding(0, dp(activity, 5), 0, 0); spellCard.addView(sm);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.bottomMargin = dp(activity, 8); results.addView(spellCard, slp);
        }

        if (entries.isEmpty()) {
            TextView empty = text(activity,
                    "No offline entry found.\n\nTap Online to look it up inside WoW Reader.",
                    15, p.subText, Typeface.NORMAL);
            empty.setPadding(0, dp(activity, 18), 0, dp(activity, 18));
            results.addView(empty);
        } else {
            int index = 1;
            for (Entry e : entries) {
                LinearLayout card = new LinearLayout(activity);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(activity, 13), dp(activity, 11), dp(activity, 13), dp(activity, 11));
                GradientDrawable cardBg = new GradientDrawable();
                cardBg.setColor(p.card);
                cardBg.setCornerRadius(dp(activity, 14));
                cardBg.setStroke(dp(activity, 1), p.border);
                card.setBackground(cardBg);

                String heading = entries.size() > 1 ? (index + ". " + e.headword) : e.headword;
                TextView h = text(activity, heading, 16, p.text, Typeface.BOLD);
                card.addView(h);
                if (!e.pos.isEmpty()) {
                    TextView pos = text(activity, e.pos, 11.5f, p.accent, Typeface.BOLD);
                    pos.setPadding(0, dp(activity, 2), 0, dp(activity, 5));
                    card.addView(pos);
                }
                TextView meaning = text(activity, e.meaning, 16, p.text, Typeface.NORMAL);
                meaning.setLineSpacing(0f, 1.2f);
                card.addView(meaning);
                if (!e.sense.isEmpty()) {
                    TextView sense = text(activity, e.sense, 12.5f, p.subText, Typeface.NORMAL);
                    sense.setPadding(0, dp(activity, 5), 0, 0);
                    card.addView(sense);
                }
                if (!e.roman.isEmpty()) {
                    TextView roman = text(activity, e.roman, 12.5f, p.subText, Typeface.NORMAL);
                    roman.setPadding(0, dp(activity, 4), 0, 0);
                    card.addView(roman);
                }

                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardLp.bottomMargin = dp(activity, 8);
                results.addView(card, cardLp);
                index++;
            }
        }

        TextView attribution = text(activity,
                "Offline data: Wiktionary via Kaikki.org • Myanmar spelling list • imported Kindle dictionary when installed",
                10.5f, p.subText, Typeface.NORMAL);
        attribution.setPadding(0, dp(activity, 4), 0, dp(activity, 6));
        results.addView(attribution);

        WebView online = new WebView(activity);
        online.setBackgroundColor(p.surface);
        WebSettings ws = online.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        ws.setTextZoom(92);
        online.setWebViewClient(new WebViewClient());
        online.setVisibility(View.GONE);
        body.addView(online, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        offlineTab.setOnClickListener(v -> {
            online.setVisibility(View.GONE);
            offlineScroll.setVisibility(View.VISIBLE);
            source.setText(directionLabel + "  •  Offline");
            stylePill(offlineTab, p.accent, p.surface, p.accent, true);
            stylePill(onlineTab, p.text, p.surface, p.border, false);
        });

        onlineTab.setOnClickListener(v -> {
            offlineScroll.setVisibility(View.GONE);
            online.setVisibility(View.VISIBLE);
            source.setText(directionLabel + "  •  Wiktionary Online");
            stylePill(offlineTab, p.text, p.surface, p.border, false);
            stylePill(onlineTab, p.accent, p.surface, p.accent, true);
            if (online.getUrl() == null) {
                online.loadUrl((myanmar ? "https://my.wiktionary.org/wiki/" : "https://en.wiktionary.org/wiki/") + Uri.encode(query));
            }
        });

        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(activity, 8), 0, 0);

        TextView sources = pill(activity, "Sources", p.accent, p.card, p.border, false);
        sources.setOnClickListener(v -> {
            dialog.dismiss();
            activity.startActivity(new Intent(activity, DictionaryManagerActivity.class));
        });
        LinearLayout.LayoutParams sourceLp = new LinearLayout.LayoutParams(0, dp(activity, 38), 1f);
        sourceLp.rightMargin = dp(activity, 7);
        footer.addView(sources, sourceLp);

        TextView copy = pill(activity, "Copy", p.text, p.card, p.border, false);
        copy.setOnClickListener(v -> {
            try {
                ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("WoW Reader Dictionary", query));
                Toast.makeText(activity, "Copied", Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {}
        });
        footer.addView(copy, new LinearLayout.LayoutParams(0, dp(activity, 38), 1f));
        sheet.addView(footer);

        dialog.setContentView(sheet);
        dialog.setOnDismissListener(d -> {
            try {
                online.stopLoading();
                online.loadUrl("about:blank");
                online.destroy();
            } catch (Exception ignored) {}
            if (onDismiss != null) {
                try { onDismiss.run(); } catch (Exception ignored) {}
            }
        });
        dialog.show();

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.BOTTOM);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = Math.min((int) (activity.getResources().getDisplayMetrics().heightPixels * 0.72f), dp(activity, 620));
            lp.dimAmount = 0.35f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    private static List<Entry> lookup(Context context, String query, int direction) {
        List<Entry> out = new ArrayList<>();
        SQLiteDatabase db = null;
        try {
            File target = ensureDb(context);
            db = SQLiteDatabase.openDatabase(target.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            String norm = normalize(query);
            Cursor c = db.rawQuery(
                    "SELECT headword, pos, meaning, sense, roman FROM entries " +
                            "WHERE direction=? AND headword_norm=? LIMIT 10",
                    new String[]{String.valueOf(direction), norm});
            while (c.moveToNext()) out.add(readEntry(c));
            c.close();

            if (out.isEmpty() && norm.indexOf(' ') < 0) {
                c = db.rawQuery(
                        "SELECT headword, pos, meaning, sense, roman FROM entries " +
                                "WHERE direction=? AND headword_norm LIKE ? ORDER BY length(headword_norm), headword_norm LIMIT 8",
                        new String[]{String.valueOf(direction), norm + "%"});
                while (c.moveToNext()) out.add(readEntry(c));
                c.close();
            }
        } catch (Exception ignored) {
        } finally {
            if (db != null) try { db.close(); } catch (Exception ignored) {}
        }
        return out;
    }

    private static Entry readEntry(Cursor c) {
        return new Entry(
                value(c, 0), value(c, 1), value(c, 2), value(c, 3), value(c, 4));
    }

    private static String value(Cursor c, int i) {
        return c.isNull(i) ? "" : c.getString(i);
    }

    private static File ensureDb(Context context) throws Exception {
        File dir = new File(context.getFilesDir(), "dictionary");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("dictionary dir");
        File target = new File(dir, DB_FILE);
        if (target.exists() && target.length() > 1000000L && validBundledDb(target)) return target;

        File tmp = new File(dir, DB_FILE + ".tmp");
        try (InputStream in = context.getAssets().open(ASSET_DB);
             FileOutputStream out = new FileOutputStream(tmp, false)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.getFD().sync();
        }
        if (target.exists()) target.delete();
        if (!tmp.renameTo(target)) throw new IllegalStateException("dictionary install");
        return target;
    }

    private static boolean validBundledDb(File target) {
        SQLiteDatabase db = null; Cursor c = null;
        try {
            db = SQLiteDatabase.openDatabase(target.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            c = db.rawQuery("SELECT direction,count(*) FROM entries GROUP BY direction", null);
            boolean en = false, my = false;
            while (c.moveToNext()) {
                int d = c.getInt(0), n = c.getInt(1);
                if (d == 0 && n > 1000) en = true;
                if (d == 1 && n > 1000) my = true;
            }
            return en && my;
        } catch (Exception ignored) { return false; }
        finally {
            if (c != null) try { c.close(); } catch (Exception ignored) {}
            if (db != null) try { db.close(); } catch (Exception ignored) {}
        }
    }

    private static String cleanQuery(String text) {
        if (text == null) return "";
        String q = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        q = q.replaceAll("\\s+", " ");
        q = q.replaceAll("^[\\p{Punct}“”‘’]+|[\\p{Punct}“”‘’]+$", "").trim();
        if (q.length() > 120) q = q.substring(0, 120).trim();
        return q;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static boolean hasMyanmar(String text) {
        return text != null && text.matches("(?s).*[\\u1000-\\u109F\\uA9E0-\\uA9FF\\uAA60-\\uAA7F].*");
    }

    private static TextView text(Context c, String s, float size, int color, int style) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT, style);
        v.setTextIsSelectable(false);
        return v;
    }

    private static TextView pill(Context c, String s, int textColor, int fill, int stroke, boolean selected) {
        TextView v = text(c, s, 13.5f, textColor, selected ? Typeface.BOLD : Typeface.NORMAL);
        v.setGravity(Gravity.CENTER);
        stylePill(v, textColor, fill, stroke, selected);
        return v;
    }

    private static void stylePill(TextView v, int textColor, int fill, int stroke, boolean selected) {
        v.setTextColor(textColor);
        v.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(v.getContext(), 12));
        g.setStroke(dp(v.getContext(), selected ? 2 : 1), stroke);
        v.setBackground(g);
    }

    private static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    private static Palette palette(int theme) {
        if (theme == 2) {
            return new Palette(Color.rgb(24, 26, 31), Color.rgb(31, 34, 40),
                    Color.rgb(238, 240, 244), Color.rgb(165, 171, 182),
                    Color.rgb(115, 170, 255), Color.rgb(58, 63, 73));
        }
        if (theme == 1) {
            return new Palette(Color.rgb(244, 235, 214), Color.rgb(238, 226, 199),
                    Color.rgb(61, 48, 34), Color.rgb(113, 92, 67),
                    Color.rgb(145, 95, 45), Color.rgb(207, 188, 154));
        }
        return new Palette(Color.WHITE, Color.rgb(249, 249, 251),
                Color.rgb(31, 34, 39), Color.rgb(100, 106, 116),
                Color.rgb(68, 112, 214), Color.rgb(222, 225, 231));
    }

    private static final class Entry {
        final String headword;
        final String pos;
        final String meaning;
        final String sense;
        final String roman;
        Entry(String headword, String pos, String meaning, String sense, String roman) {
            this.headword = headword;
            this.pos = pos;
            this.meaning = meaning;
            this.sense = sense;
            this.roman = roman;
        }
    }

    private static final class Palette {
        final int surface, card, text, subText, accent, border;
        Palette(int surface, int card, int text, int subText, int accent, int border) {
            this.surface = surface;
            this.card = card;
            this.text = text;
            this.subText = subText;
            this.accent = accent;
            this.border = border;
        }
    }
}
