package com.whisper.wowreader;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReadingRecapActivity extends Activity {
    private ReadingCalendarUi ui;
    private SharedPreferences prefs;
    private ReadingRecapData.Summary summary;
    private LinearLayout shareCard;
    private String mode;
    private String periodLabel;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new ReadingCalendarUi(this);
        prefs = getSharedPreferences("wow_reader", MODE_PRIVATE);
        long start = getIntent().getLongExtra("start_ms", System.currentTimeMillis());
        long end = getIntent().getLongExtra("end_ms", System.currentTimeMillis());
        mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "month";
        periodLabel = getIntent().getStringExtra("period_label");
        if (periodLabel == null || periodLabel.trim().isEmpty()) periodLabel = defaultPeriodLabel(start);
        summary = ReadingRecapData.summarize(this, prefs, start, end);
        render();
    }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ui.background);
        root.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = label("‹", 30, ui.accent, false);
        back.setGravity(Gravity.CENTER);
        back.setBackground(ui.rounded(ui.control, 18, 1, ui.stroke));
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(ui.dp(46), ui.dp(46)));
        TextView title = label("Reading Recap", 20, ui.primary, true);
        title.setPadding(ui.dp(12), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, ui.dp(48), 1f));
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, ui.dp(8), 0, ui.dp(20));
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        shareCard = buildShareCard();
        body.addView(shareCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView share = label("↗  Share Image", 15, Color.WHITE, true);
        share.setGravity(Gravity.CENTER);
        share.setBackground(ui.rounded(Color.rgb(101, 91, 235), 22, 0, 0));
        share.setOnClickListener(v -> share.post(this::shareImage));
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(52));
        shareLp.topMargin = ui.dp(12);
        body.addView(share, shareLp);

        TextView hint = label("Only books completed in this period are shown.", 10.5f, ui.secondary, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, ui.dp(8), 0, 0);
        body.addView(hint);

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        AppWindowInsets.apply(this, root, ui.background, ui.darkSystemIcons);
    }

    private LinearLayout buildShareCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(ui.dp(18), ui.dp(24), ui.dp(18), ui.dp(22));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(18, 31, 64), Color.rgb(32, 48, 88), Color.rgb(44, 35, 82)});
        bg.setCornerRadius(ui.dp(24));
        card.setBackground(bg);

        TextView kicker = label(recapKicker(), 13, Color.rgb(190, 198, 224), false);
        kicker.setGravity(Gravity.CENTER);
        card.addView(kicker);

        TextView title = label(recapTitle(), 28, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, ui.dp(2), 0, 0);
        card.addView(title);

        TextView date = label(periodLabel, 11.5f, Color.rgb(190, 198, 224), false);
        date.setGravity(Gravity.CENTER);
        date.setPadding(0, ui.dp(3), 0, ui.dp(18));
        card.addView(date);

        if (!summary.finishedBooks.isEmpty()) {
            GridLayout covers = new GridLayout(this);
            covers.setColumnCount(4);
            covers.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
            int show = Math.min(12, summary.finishedBooks.size());
            for (int i = 0; i < show; i++) {
                ReadingRecapData.FinishedBook book = summary.finishedBooks.get(i);
                FrameLayout wrap = new FrameLayout(this);
                ImageView cover = new ImageView(this);
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cover.setBackground(ui.rounded(Color.rgb(42, 52, 83), 7, 0, 0));
                cover.setClipToOutline(true);
                wrap.addView(cover, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                BookVisualUtil.loadCover(this, book.file, cover, ui.dp(68), ui.dp(96));
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = 0;
                lp.height = ui.dp(104);
                lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                lp.setMargins(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3));
                covers.addView(wrap, lp);
            }
            card.addView(covers, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (summary.finishedBooks.size() > show) {
                TextView more = label("+" + (summary.finishedBooks.size() - show) + " more finished books", 10.5f,
                        Color.rgb(190, 198, 224), true);
                more.setGravity(Gravity.CENTER);
                more.setPadding(0, ui.dp(5), 0, 0);
                card.addView(more);
            }
        } else {
            TextView empty = label("No finished books in this period yet", 14, Color.rgb(207, 214, 235), true);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(ui.dp(12), ui.dp(30), ui.dp(12), ui.dp(30));
            empty.setBackground(ui.rounded(Color.rgb(34, 47, 80), 18, 1, Color.rgb(65, 78, 115)));
            card.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout stats = new LinearLayout(this);
        stats.setGravity(Gravity.CENTER);
        stats.setPadding(0, ui.dp(20), 0, ui.dp(12));
        stats.addView(metric(String.valueOf(summary.finishedBooks.size()), "Finished"), new LinearLayout.LayoutParams(0, ui.dp(56), 1f));
        stats.addView(metric(String.valueOf(summary.activeDays), "Reading days"), new LinearLayout.LayoutParams(0, ui.dp(56), 1f));
        stats.addView(metric(ui.formatDuration(summary.readingMs), "Read time"), new LinearLayout.LayoutParams(0, ui.dp(56), 1f));
        card.addView(stats, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(78)));

        TextView quote = label("“A little reading becomes a life of stories.”", 12.5f,
                Color.rgb(218, 221, 239), false);
        quote.setGravity(Gravity.CENTER);
        quote.setPadding(ui.dp(8), ui.dp(5), ui.dp(8), ui.dp(10));
        card.addView(quote);

        TextView brand = label("▣  WoW Reader", 11.5f, Color.WHITE, true);
        brand.setGravity(Gravity.CENTER);
        card.addView(brand);
        return card;
    }

    private View metric(String value, String caption) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView number = label(value, 18, Color.WHITE, true);
        number.setGravity(Gravity.CENTER);
        TextView sub = label(caption, 9, Color.rgb(190, 198, 224), false);
        sub.setGravity(Gravity.CENTER);
        box.addView(number);
        box.addView(sub);
        return box;
    }

    private String recapKicker() {
        if ("week".equals(mode)) return "MY WEEK IN BOOKS";
        if ("year".equals(mode)) return "MY READING YEAR";
        return "MY MONTH IN BOOKS";
    }

    private String recapTitle() {
        if ("year".equals(mode)) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTimeInMillis(summary.startMs);
            return c.get(java.util.Calendar.YEAR) + " in Books";
        }
        if ("week".equals(mode)) return "Week in Books";
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(summary.startMs);
        return new SimpleDateFormat("MMMM", Locale.ENGLISH).format(c.getTime()) + " in Books";
    }

    private String defaultPeriodLabel(long startMs) {
        if ("year".equals(mode)) return new SimpleDateFormat("yyyy", Locale.ENGLISH).format(new Date(startMs));
        return new SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(new Date(startMs));
    }

    private void shareImage() {
        if (shareCard == null || shareCard.getWidth() <= 0 || shareCard.getHeight() <= 0) return;
        try {
            Bitmap raw = Bitmap.createBitmap(shareCard.getWidth(), shareCard.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(raw);
            shareCard.draw(canvas);
            int targetWidth = 1080;
            int targetHeight = Math.max(1, Math.round(raw.getHeight() * (targetWidth / (float) raw.getWidth())));
            Bitmap output = raw.getWidth() == targetWidth ? raw : Bitmap.createScaledBitmap(raw, targetWidth, targetHeight, true);

            File dir = new File(getCacheDir(), "shared_images");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create share folder");
            File file = new File(dir, "wow-reading-recap.png");
            try (FileOutputStream out = new FileOutputStream(file)) {
                if (!output.compress(Bitmap.CompressFormat.PNG, 100, out))
                    throw new IllegalStateException("Cannot create image");
            }
            if (output != raw) output.recycle();
            raw.recycle();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/png");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_TEXT, recapTitle() + " · WoW Reader");
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Share reading recap"));
        } catch (Exception e) {
            Toast.makeText(this, "Could not create recap image", Toast.LENGTH_SHORT).show();
        }
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text == null ? "" : text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(ui.myanmarTypeface, bold ? Typeface.BOLD : Typeface.NORMAL);
        v.setIncludeFontPadding(true);
        return v;
    }
}
