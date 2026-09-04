package com.whisper.wowreader;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;


public class ComingSoonDetailActivity extends Activity {
    private SharedPreferences prefs;
    private String appTheme = "white";
    private LinearLayout bodyRoot;
    private TextView status;
    private String postUrl;
    private String fallbackTitle;
    private String fallbackSource;
    private String fallbackDate;
    private String fallbackImage;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("wow_reader", MODE_PRIVATE);
        appTheme = prefs.getString("app_theme", "white");
        if (!AppThemePalette.isSupportedTheme(appTheme)) appTheme = "white";
        postUrl = getIntent().getStringExtra("url");
        fallbackTitle = safe(getIntent().getStringExtra("title"));
        fallbackSource = safe(getIntent().getStringExtra("source"));
        fallbackDate = safe(getIntent().getStringExtra("date"));
        fallbackImage = safe(getIntent().getStringExtra("image"));
        applyBars();
        buildUi();
        loadPost();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bg());
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        bodyRoot = new LinearLayout(this);
        bodyRoot.setOrientation(LinearLayout.VERTICAL);
        bodyRoot.setPadding(dp(16), dp(12), dp(16), dp(34));
        scroll.addView(bodyRoot, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = smallButton("‹");
        back.setTextSize(28);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView label = text("Coming Soon", 15, primary(), true);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(dp(12), 0, 0, 0);
        header.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1f));

        bodyRoot.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        status = text("Loading post…", 12.5f, secondary(), false);
        status.setPadding(dp(2), dp(14), dp(2), dp(10));
        bodyRoot.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        if (!fallbackTitle.isEmpty()) renderSkeleton();
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        AppWindowInsets.apply(this, root, bg(), useDarkSystemIcons());
    }

    private void renderSkeleton() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(16));
        card.setBackground(roundRect(card(), dp(22), dp(1), stroke()));

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(roundRect(control(), dp(16), 0, 0));
        cover.setClipToOutline(true);
        card.addView(cover, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(270)));
        ComingSoonImageLoader.load(this, fallbackImage, cover);

        TextView source = text(fallbackSource + (fallbackDate.isEmpty() ? "" : " · " + fallbackDate), 10.5f, accent(), true);
        source.setPadding(dp(2), dp(14), dp(2), 0);
        card.addView(source);
        TextView title = text(fallbackTitle, 22, primary(), true);
        title.setPadding(dp(2), dp(8), dp(2), dp(4));
        card.addView(title);
        bodyRoot.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void loadPost() {
        new Thread(() -> {
            ComingSoonFeed.Post post = ComingSoonFeed.fetchPost(this, postUrl);
            runOnUiThread(() -> {
                if (post == null) {
                    status.setText("Full post is not available right now. Showing the cached preview when available.");
                    return;
                }
                renderPost(post);
            });
        }, "wow-coming-soon-detail").start();
    }

    private void renderPost(ComingSoonFeed.Post post) {
        status.setVisibility(View.GONE);
        while (bodyRoot.getChildCount() > 1) bodyRoot.removeViewAt(1);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(18));
        card.setBackground(roundRect(card(), dp(22), dp(1), stroke()));
        card.setElevation(dp(1));

        if (post.imageUrl != null && !post.imageUrl.isEmpty()) {
            ImageView cover = new ImageView(this);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(roundRect(control(), dp(16), 0, 0));
            cover.setClipToOutline(true);
            card.addView(cover, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(280)));
            ComingSoonImageLoader.load(this, post.imageUrl, cover);
        }

        TextView source = text(post.source + (post.published.isEmpty() ? "" : " · " + post.published), 10.5f, accent(), true);
        source.setPadding(dp(2), dp(14), dp(2), 0);
        card.addView(source);

        TextView title = text(post.title, 22, primary(), true);
        title.setLineSpacing(0f, 1.08f);
        title.setPadding(dp(2), dp(8), dp(2), dp(6));
        card.addView(title);

        View divider = new View(this);
        divider.setBackgroundColor(stroke());
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerLp.topMargin = dp(8);
        dividerLp.bottomMargin = dp(9);
        card.addView(divider, dividerLp);

        TextView body = text("", 15, primary(), false);
        body.setLineSpacing(dp(2), 1.10f);
        if (Build.VERSION.SDK_INT >= 26) {
            // Android's layout engine expands available word/phrase spacing so
            // Myanmar review lines read with the same balanced feel as Auto-spacing Justify.
            body.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD);
        }
        body.setText(ComingSoonFeed.richText(post.contentHtml));
        body.setMovementMethod(null);
        body.setLinksClickable(false);
        body.setLinkTextColor(primary());
        body.setTextIsSelectable(true);
        body.setPadding(dp(2), 0, dp(2), dp(6));
        card.addView(body);

        bodyRoot.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        if (useDarkSystemIcons())
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        else getWindow().getDecorView().setSystemUiVisibility(0);
    }

    private boolean customTheme() { return "custom".equals(appTheme); }
    private AppThemePalette customPalette() { return AppThemePalette.custom(prefs); }
    private boolean useDarkSystemIcons() {
        if (customTheme()) return customPalette().darkSystemIcons;
        return !"black".equals(appTheme) && !"navy".equals(appTheme);
    }

    private int bg() {
        if (customTheme()) return customPalette().background;
        if ("black".equals(appTheme)) return Color.rgb(17, 18, 20);
        if ("navy".equals(appTheme)) return Color.rgb(19, 25, 43);
        return Color.rgb(247, 248, 251);
    }
    private int card() {
        if (customTheme()) return customPalette().card;
        if ("black".equals(appTheme)) return Color.rgb(28, 29, 32);
        if ("navy".equals(appTheme)) return Color.rgb(28, 36, 59);
        return Color.WHITE;
    }
    private int control() {
        if (customTheme()) return customPalette().control;
        if ("black".equals(appTheme)) return Color.rgb(38, 39, 43);
        if ("navy".equals(appTheme)) return Color.rgb(38, 47, 73);
        return Color.rgb(244, 245, 249);
    }
    private int primary() { return customTheme() ? customPalette().primary : (("black".equals(appTheme) || "navy".equals(appTheme)) ? Color.rgb(245, 246, 249) : Color.rgb(31, 33, 40)); }
    private int secondary() { return customTheme() ? customPalette().secondary : (("black".equals(appTheme) || "navy".equals(appTheme)) ? Color.rgb(176, 181, 194) : Color.rgb(104, 109, 124)); }
    private int stroke() { return customTheme() ? customPalette().stroke : (("black".equals(appTheme) || "navy".equals(appTheme)) ? Color.rgb(55, 60, 74) : Color.rgb(226, 228, 236)); }
    private int accent() { return customTheme() ? customPalette().accent : Color.rgb(111, 78, 209); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String safe(String value) { return value == null ? "" : value; }
}