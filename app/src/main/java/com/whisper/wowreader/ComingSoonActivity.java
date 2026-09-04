package com.whisper.wowreader;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public class ComingSoonActivity extends Activity {
    private LinearLayout feedContainer;
    private TextView status;
    private SharedPreferences prefs;
    private String appTheme = "white";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("wow_reader", MODE_PRIVATE);
        appTheme = prefs.getString("app_theme", "white");
        applyBars();
        buildUi();
        loadFeed();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bg());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(30));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = smallButton("‹");
        back.setTextSize(28);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(10), dp(2), 0, dp(2));
        TextView title = text("Coming Soon", 22, primary(), true);
        TextView sub = text("Latest book notes from WoW sources", 11.5f, secondary(), false);
        titles.addView(title);
        titles.addView(sub);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView reload = smallButton("↻");
        reload.setTextSize(20);
        reload.setContentDescription("Refresh Coming Soon");
        reload.setOnClickListener(v -> loadFeed());
        header.addView(reload, new LinearLayout.LayoutParams(dp(44), dp(44)));
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));

        status = text("Loading latest posts…", 12.5f, secondary(), false);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(4), dp(4), dp(4), dp(8));
        content.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        feedContainer = new LinearLayout(this);
        feedContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(feedContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        AppWindowInsets.apply(this, root, bg(), !"black".equals(appTheme) && !"navy".equals(appTheme));
    }

    private void loadFeed() {
        if (feedContainer == null) return;
        status.setText("Loading latest posts…");
        feedContainer.removeAllViews();
        new Thread(() -> {
            List<ComingSoonFeed.Post> posts = ComingSoonFeed.fetchLatest(this, 18, 42);
            runOnUiThread(() -> render(posts));
        }, "wow-coming-soon").start();
    }

    private void render(List<ComingSoonFeed.Post> posts) {
        if (posts == null || posts.isEmpty()) {
            status.setText("No posts available right now. Check your internet connection and try again.");
            return;
        }
        status.setText(posts.size() + " latest posts · 3 sources");
        for (ComingSoonFeed.Post post : posts) {
            View card = postCard(post);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(148));
            lp.bottomMargin = dp(12);
            feedContainer.addView(card, lp);
        }
    }

    private View postCard(ComingSoonFeed.Post post) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(10), dp(12), dp(10));
        card.setBackground(roundRect(card(), dp(20), dp(1), stroke()));
        card.setElevation(dp(1));
        card.setClickable(true);
        card.setOnClickListener(v -> open(post));

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(roundRect(control(), dp(14), 0, 0));
        cover.setClipToOutline(true);
        card.addView(cover, new LinearLayout.LayoutParams(dp(86), dp(126)));
        ComingSoonImageLoader.load(this, post.imageUrl, cover);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(13), dp(2), 0, dp(2));

        TextView source = text(post.source + (post.published.isEmpty() ? "" : " · " + post.published),
                10.5f, accent(), true);
        source.setMaxLines(1);
        copy.addView(source);

        TextView title = text(post.title, 16, primary(), true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(0, dp(5), 0, 0);
        copy.addView(title);

        TextView excerpt = text(post.excerpt, 11.5f, secondary(), false);
        excerpt.setMaxLines(3);
        excerpt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        excerpt.setPadding(0, dp(6), 0, 0);
        copy.addView(excerpt);

        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return card;
    }

    private void open(ComingSoonFeed.Post post) {
        Intent intent = new Intent(this, ComingSoonDetailActivity.class);
        intent.putExtra("url", post.url);
        intent.putExtra("title", post.title);
        intent.putExtra("source", post.source);
        intent.putExtra("date", post.published);
        intent.putExtra("image", post.imageUrl);
        startActivity(intent);
    }

    private TextView smallButton(String value) {
        TextView v = text(value, 18, primary(), false);
        v.setGravity(Gravity.CENTER);
        v.setBackground(roundRect(control(), dp(18), dp(1), stroke()));
        v.setClickable(true);
        return v;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private GradientDrawable roundRect(int fill, int radius, int strokeWidth, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private void applyBars() {
        getWindow().setStatusBarColor(bg());
        getWindow().setNavigationBarColor(bg());
        if (!"black".equals(appTheme) && !"navy".equals(appTheme))
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        else getWindow().getDecorView().setSystemUiVisibility(0);
    }

    private int bg() {
        if ("black".equals(appTheme)) return Color.rgb(17, 18, 20);
        if ("navy".equals(appTheme)) return Color.rgb(19, 25, 43);
        return Color.rgb(247, 248, 251);
    }
    private int card() {
        if ("black".equals(appTheme)) return Color.rgb(28, 29, 32);
        if ("navy".equals(appTheme)) return Color.rgb(28, 36, 59);
        return Color.WHITE;
    }
    private int control() {
        if ("black".equals(appTheme)) return Color.rgb(38, 39, 43);
        if ("navy".equals(appTheme)) return Color.rgb(38, 47, 73);
        return Color.rgb(244, 245, 249);
    }
    private int primary() {
        return ("black".equals(appTheme) || "navy".equals(appTheme)) ? Color.rgb(245, 246, 249) : Color.rgb(31, 33, 40);
    }
    private int secondary() {
        return ("black".equals(appTheme) || "navy".equals(appTheme)) ? Color.rgb(176, 181, 194) : Color.rgb(104, 109, 124);
    }
    private int stroke() {
        return ("black".equals(appTheme) || "navy".equals(appTheme)) ? Color.rgb(55, 60, 74) : Color.rgb(226, 228, 236);
    }
    private int accent() { return Color.rgb(111, 78, 209); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}