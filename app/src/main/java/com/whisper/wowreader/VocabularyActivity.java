package com.whisper.wowreader;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/** Kindle-style Vocabulary Builder for words looked up while reading. */
public final class VocabularyActivity extends Activity {
    private SharedPreferences prefs;
    private int tab = 0; // 0 words, 1 flashcards, 2 learned
    private int flashIndex = 0;
    private Palette palette;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("wow_reader", MODE_PRIVATE);
        palette = Palette.from(prefs.getString("app_theme", "white"));
        getWindow().setStatusBarColor(palette.background);
        getWindow().setNavigationBarColor(palette.background);
        render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(palette.background);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = button("‹", false);
        back.setTextSize(28);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Vocabulary Builder", 23, palette.text, Typeface.BOLD);
        TextView sub = text(VocabularyStore.count(prefs) + " saved words · added automatically from Dictionary",
                11.5f, palette.sub, Typeface.NORMAL);
        titles.addView(title);
        titles.addView(sub);
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titlesLp.leftMargin = dp(10);
        header.addView(titles, titlesLp);
        root.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(18), 0, dp(14));
        String[] names = {"Words", "Flashcards", "Learned"};
        for (int i = 0; i < names.length; i++) {
            final int which = i;
            TextView chip = button(names[i], tab == i);
            chip.setOnClickListener(v -> { tab = which; render(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
            if (i > 0) lp.leftMargin = dp(7);
            tabs.addView(chip, lp);
        }
        root.addView(tabs);

        if (tab == 1) renderFlashcards(root);
        else renderWordList(root, tab == 2);

        setContentView(scroll);
        AppWindowInsets.apply(this, scroll, palette.background, palette.darkIcons);
    }

    private void renderWordList(LinearLayout root, boolean learnedOnly) {
        List<VocabularyStore.Entry> entries = learnedOnly
                ? VocabularyStore.learned(prefs, true)
                : VocabularyStore.load(prefs);
        if (entries.isEmpty()) {
            TextView empty = text(learnedOnly
                            ? "No learned words yet.\n\nMark a word as learned when you feel confident about it."
                            : "No words yet.\n\nWhile reading, long-press a word and open Dictionary. It will appear here automatically.",
                    14.5f, palette.sub, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(70), dp(20), dp(70));
            root.addView(empty);
            return;
        }

        for (VocabularyStore.Entry e : entries) {
            LinearLayout card = card();
            TextView word = text(e.word, 21, palette.text, Typeface.BOLD);
            card.addView(word);
            String direction = e.direction == 1 ? "မြန်မာ → English" : "English → မြန်မာ";
            String book = prettyBook(e.book);
            TextView meta = text(direction + (book.isEmpty() ? "" : " · " + book) +
                    " · " + e.lookups + (e.lookups == 1 ? " lookup" : " lookups"),
                    11.5f, palette.sub, Typeface.NORMAL);
            meta.setPadding(0, dp(3), 0, dp(12));
            card.addView(meta);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            TextView lookup = button("Dictionary", false);
            lookup.setOnClickListener(v -> DictionaryDialog.show(this, e.word));
            actions.addView(lookup, new LinearLayout.LayoutParams(0, dp(40), 1f));

            TextView learned = button(e.learned ? "Learned ✓" : "Mark learned", e.learned);
            learned.setOnClickListener(v -> {
                VocabularyStore.setLearned(prefs, e.word, e.direction, !e.learned);
                render();
            });
            LinearLayout.LayoutParams learnedLp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            learnedLp.leftMargin = dp(7);
            actions.addView(learned, learnedLp);

            TextView remove = button("×", false);
            remove.setTextSize(20);
            remove.setOnClickListener(v -> {
                VocabularyStore.remove(prefs, e.word, e.direction);
                render();
            });
            LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(dp(44), dp(40));
            removeLp.leftMargin = dp(7);
            actions.addView(remove, removeLp);
            card.addView(actions);

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.bottomMargin = dp(10);
            root.addView(card, cardLp);
        }
    }

    private void renderFlashcards(LinearLayout root) {
        List<VocabularyStore.Entry> entries = VocabularyStore.learned(prefs, false);
        if (entries.isEmpty()) {
            TextView done = text("All caught up ✓\n\nThere are no unlearned words right now.",
                    16, palette.sub, Typeface.NORMAL);
            done.setGravity(Gravity.CENTER);
            done.setPadding(dp(20), dp(80), dp(20), dp(80));
            root.addView(done);
            return;
        }
        flashIndex = Math.floorMod(flashIndex, entries.size());
        VocabularyStore.Entry e = entries.get(flashIndex);

        TextView progress = text((flashIndex + 1) + " / " + entries.size(), 11.5f, palette.sub, Typeface.BOLD);
        progress.setGravity(Gravity.CENTER_HORIZONTAL);
        progress.setPadding(0, dp(10), 0, dp(12));
        root.addView(progress);

        LinearLayout flash = card();
        flash.setGravity(Gravity.CENTER_HORIZONTAL);
        flash.setPadding(dp(20), dp(34), dp(20), dp(28));
        TextView word = text(e.word, 30, palette.text, Typeface.BOLD);
        word.setGravity(Gravity.CENTER);
        flash.addView(word, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView prompt = text("Think of the meaning, then reveal it.", 13, palette.sub, Typeface.NORMAL);
        prompt.setGravity(Gravity.CENTER);
        prompt.setPadding(0, dp(10), 0, dp(24));
        flash.addView(prompt);

        TextView reveal = button("Reveal in Dictionary", true);
        reveal.setOnClickListener(v -> DictionaryDialog.show(this, e.word));
        flash.addView(reveal, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(10), 0, 0);
        TextView again = button("Again", false);
        again.setOnClickListener(v -> { flashIndex = (flashIndex + 1) % entries.size(); render(); });
        actions.addView(again, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView learned = button("I know this ✓", true);
        learned.setOnClickListener(v -> {
            VocabularyStore.setLearned(prefs, e.word, e.direction, true);
            if (flashIndex >= Math.max(1, entries.size() - 1)) flashIndex = 0;
            render();
        });
        LinearLayout.LayoutParams learnedLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        learnedLp.leftMargin = dp(8);
        actions.addView(learned, learnedLp);
        flash.addView(actions);
        root.addView(flash);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(palette.card);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), palette.stroke);
        card.setBackground(bg);
        return card;
    }

    private TextView button(String label, boolean selected) {
        TextView v = text(label, 13, selected ? palette.accent : palette.text,
                selected ? Typeface.BOLD : Typeface.NORMAL);
        v.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? palette.accentSoft : palette.control);
        bg.setCornerRadius(dp(13));
        bg.setStroke(dp(selected ? 2 : 1), selected ? palette.accent : palette.stroke);
        v.setBackground(bg);
        v.setClickable(true);
        return v;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.DEFAULT, style);
        return v;
    }

    private String prettyBook(String file) {
        if (file == null || file.trim().isEmpty()) return "";
        String name = new File(file).getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Palette {
        final int background, card, control, text, sub, accent, accentSoft, stroke;
        final boolean darkIcons;
        Palette(int background, int card, int control, int text, int sub, int accent,
                int accentSoft, int stroke, boolean darkIcons) {
            this.background = background; this.card = card; this.control = control; this.text = text;
            this.sub = sub; this.accent = accent; this.accentSoft = accentSoft; this.stroke = stroke;
            this.darkIcons = darkIcons;
        }
        static Palette from(String theme) {
            if ("black".equals(theme) || "navy".equals(theme))
                return new Palette(Color.rgb(18,20,24), Color.rgb(29,32,38), Color.rgb(37,40,47),
                        Color.rgb(240,242,246), Color.rgb(164,171,183), Color.rgb(112,168,255),
                        Color.rgb(32,48,70), Color.rgb(58,64,75), false);
            if ("sepia".equals(theme))
                return new Palette(Color.rgb(244,235,214), Color.rgb(238,226,199), Color.rgb(233,219,188),
                        Color.rgb(61,48,34), Color.rgb(113,92,67), Color.rgb(145,95,45),
                        Color.rgb(239,218,189), Color.rgb(207,188,154), true);
            return new Palette(Color.rgb(247,248,251), Color.WHITE, Color.rgb(247,248,251),
                    Color.rgb(31,34,39), Color.rgb(100,106,116), Color.rgb(68,112,214),
                    Color.rgb(235,241,255), Color.rgb(222,225,231), true);
        }
    }
}
