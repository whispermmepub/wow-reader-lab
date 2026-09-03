package com.whisper.wowreader;

import android.content.SharedPreferences;

/** Local-first typography overrides for one library book. */
public final class BookTypographyStore {
    private BookTypographyStore() {}

    public static final class Values {
        public int fontPercent;
        public String fontChoice;
        public int lineSpacing;
        public int marginPercent;
        public String textAlignment;
        public boolean autoSpacing;
    }

    public static boolean hasSaved(SharedPreferences prefs, String bookName) {
        return prefs != null && prefs.getBoolean(prefix(bookName) + "saved", false);
    }

    public static Values load(SharedPreferences prefs, String bookName,
                              int fontPercent, String fontChoice, int lineSpacing,
                              int marginPercent, String textAlignment, boolean autoSpacing) {
        Values values = new Values();
        values.fontPercent = fontPercent;
        values.fontChoice = fontChoice;
        values.lineSpacing = lineSpacing;
        values.marginPercent = marginPercent;
        values.textAlignment = textAlignment;
        values.autoSpacing = autoSpacing;
        if (prefs == null || !hasSaved(prefs, bookName)) return values;

        String p = prefix(bookName);
        values.fontPercent = prefs.getInt(p + "font_percent", fontPercent);
        values.fontChoice = prefs.getString(p + "font_choice", fontChoice);
        values.lineSpacing = prefs.getInt(p + "line_spacing", lineSpacing);
        values.marginPercent = prefs.getInt(p + "margin_percent", marginPercent);
        values.textAlignment = prefs.getString(p + "text_alignment", textAlignment);
        values.autoSpacing = prefs.getBoolean(p + "auto_spacing", autoSpacing);
        return values;
    }

    public static void save(SharedPreferences prefs, String bookName,
                            int fontPercent, String fontChoice, int lineSpacing,
                            int marginPercent, String textAlignment, boolean autoSpacing) {
        if (prefs == null || bookName == null || bookName.isEmpty()) return;
        String p = prefix(bookName);
        prefs.edit()
                .putBoolean(p + "saved", true)
                .putInt(p + "font_percent", fontPercent)
                .putString(p + "font_choice", fontChoice == null ? "publisher" : fontChoice)
                .putInt(p + "line_spacing", lineSpacing)
                .putInt(p + "margin_percent", marginPercent)
                .putString(p + "text_alignment", textAlignment == null ? "justify" : textAlignment)
                .putBoolean(p + "auto_spacing", autoSpacing)
                .putLong("sync_updated_ms", System.currentTimeMillis())
                .apply();
    }

    private static String prefix(String bookName) {
        String value = bookName == null ? "book" : bookName;
        return "book_typography_" + Integer.toHexString(value.hashCode()) + "_";
    }
}
