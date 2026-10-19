package com.whisper.wowreader;

import android.content.SharedPreferences;

/** Global reader typography profile; deliberately has no book identity or override behavior. */
public final class GlobalTypographySettings {
    private GlobalTypographySettings() {}

    public static final class Values {
        public int fontPercent = 100;
        public int fontWeight = 400;
        public String fontChoice = "publisher";
        public int lineSpacing = 160;
        public int marginPercent = 5;
        public String textAlignment = "publisher";
        public boolean publisherStyles = true;
        public boolean autoSpacing = true;
    }

    public static Values load(SharedPreferences prefs) {
        Values v = new Values();
        if (prefs == null) return v;
        v.fontPercent = clamp(prefs.getInt("epub_font", 100), 80, 300);
        v.fontWeight = normalizeWeight(prefs.getInt("epub_font_weight", 400));
        v.fontChoice = prefs.getString("epub_font_choice", "publisher");
        if (v.fontChoice == null || v.fontChoice.length() == 0) v.fontChoice = "publisher";
        v.lineSpacing = clamp(prefs.getInt("epub_line_spacing", 160), 120, 220);
        v.marginPercent = clamp(prefs.getInt("epub_margin", 5), 1, 14);
        // Existing explicit font/spacing choices remain global and effective.
        // Migrate only the old forced alignment to author alignment; new installs
        // with no customized profile start in full Original EPUB formatting.
        boolean customized = v.fontPercent != 100 || v.fontWeight != 400 ||
                !"publisher".equals(v.fontChoice) || v.lineSpacing != 160;
        v.publisherStyles = prefs.getBoolean("epub_publisher_styles", !customized);
        v.textAlignment = prefs.contains("epub_publisher_styles") ? prefs.getString("epub_text_alignment", "publisher") : "publisher";
        if (!"justify".equals(v.textAlignment) && !"left".equals(v.textAlignment)
                && !"right".equals(v.textAlignment) && !"publisher".equals(v.textAlignment)) v.textAlignment = "publisher";
        v.autoSpacing = prefs.getBoolean("epub_auto_spacing", true);
        return v;
    }

    public static void write(SharedPreferences.Editor editor, Values values) {
        if (editor == null || values == null) return;
        String choice = values.fontChoice == null || values.fontChoice.length() == 0
                ? "publisher" : values.fontChoice;
        String alignment = "left".equals(values.textAlignment) || "right".equals(values.textAlignment) || "justify".equals(values.textAlignment)
                ? values.textAlignment : "publisher";
        editor.putInt("epub_font", clamp(values.fontPercent, 80, 300))
                .putInt("epub_font_weight", normalizeWeight(values.fontWeight))
                .putString("epub_font_choice", choice)
                .putInt("epub_line_spacing", clamp(values.lineSpacing, 120, 220))
                .putInt("epub_margin", clamp(values.marginPercent, 1, 14))
                .putBoolean("epub_publisher_styles", values.publisherStyles)
                .putString("epub_text_alignment", alignment)
                .putBoolean("epub_auto_spacing", values.autoSpacing);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int normalizeWeight(int value) {
        return clamp(Math.round(value / 100f) * 100, 200, 800);
    }
}
