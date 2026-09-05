package com.whisper.wowreader;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

final class ReadingCalendarUi {
    final Context context;
    final SharedPreferences prefs;
    final String mode;
    final int background;
    final int card;
    final int control;
    final int primary;
    final int secondary;
    final int accent;
    final int stroke;
    final boolean darkSystemIcons;
    final Typeface myanmarTypeface;

    ReadingCalendarUi(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences("wow_reader", Context.MODE_PRIVATE);
        String stored = prefs.getString("app_theme", "white");
        mode = AppThemePalette.isSupportedTheme(stored) ? stored : "white";
        if ("custom".equals(mode)) {
            AppThemePalette p = AppThemePalette.custom(prefs);
            background = p.background; card = p.card; control = p.control;
            primary = p.primary; secondary = p.secondary; accent = p.accent; stroke = p.stroke;
            darkSystemIcons = p.darkSystemIcons;
        } else if ("black".equals(mode)) {
            background = Color.rgb(12, 13, 16); card = Color.rgb(27, 29, 34); control = Color.rgb(35, 37, 43);
            primary = Color.rgb(244, 247, 250); secondary = Color.rgb(178, 183, 192);
            accent = Color.rgb(151, 166, 255); stroke = Color.rgb(55, 59, 68); darkSystemIcons = false;
        } else if ("navy".equals(mode)) {
            background = Color.rgb(3, 28, 48); card = Color.rgb(7, 44, 70); control = Color.rgb(10, 51, 79);
            primary = Color.rgb(244, 247, 250); secondary = Color.rgb(165, 196, 213);
            accent = Color.rgb(239, 194, 91); stroke = Color.rgb(26, 91, 120); darkSystemIcons = false;
        } else {
            background = Color.rgb(247, 248, 251); card = Color.WHITE; control = Color.rgb(251, 251, 253);
            primary = Color.rgb(31, 34, 40); secondary = Color.rgb(105, 110, 122);
            accent = Color.rgb(82, 82, 214); stroke = Color.rgb(224, 227, 234); darkSystemIcons = true;
        }
        Typeface tf;
        try { tf = Typeface.createFromAsset(context.getAssets(), "fonts/pyidaungsu_native.ttf"); }
        catch (Exception ignored) { tf = Typeface.DEFAULT; }
        myanmarTypeface = tf;
    }

    int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    GradientDrawable rounded(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    void text(TextView v, float size, int color, boolean bold) {
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(myanmarTypeface, bold ? Typeface.BOLD : Typeface.NORMAL);
        v.setIncludeFontPadding(true);
    }

    String formatDuration(long ms) {
        long minutes = Math.max(0L, ms) / 60_000L;
        if (minutes < 1L) return "<1m";
        if (minutes < 60L) return minutes + "m";
        long h = minutes / 60L, m = minutes % 60L;
        return m == 0L ? h + "h" : h + "h " + m + "m";
    }
}
