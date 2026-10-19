package com.whisper.wowreader;

import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Per-book typography values. Legacy values are preserved but never activated implicitly. */
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

    /** An override is active only after the user explicitly chooses Only this book and saves. */
    public static boolean hasOverride(SharedPreferences prefs, String bookName) {
        return hasSaved(prefs, bookName) && prefs.getBoolean(prefix(bookName) + "enabled", false);
    }

    public static boolean hasLegacySaved(SharedPreferences prefs, String bookName) {
        return prefs != null && prefs.getBoolean(legacyPrefix(bookName) + "saved", false);
    }

    public static void setEnabled(SharedPreferences prefs, String bookName, boolean enabled) {
        if (prefs == null || bookName == null || bookName.isEmpty()) return;
        String p = prefix(bookName);
        prefs.edit().putBoolean(p + "enabled", enabled).putBoolean(p + "saved", true).apply();
    }

    public static void clearOverride(SharedPreferences prefs, String bookName) {
        if (prefs == null || bookName == null || bookName.isEmpty()) return;
        String p = prefix(bookName);
        prefs.edit().remove(p + "enabled").remove(p + "saved").remove(p + "font_percent")
                .remove(p + "font_choice").remove(p + "line_spacing").remove(p + "margin_percent")
                .remove(p + "text_alignment").remove(p + "auto_spacing").apply();
    }

    public static Values load(SharedPreferences prefs, String bookName, int fontPercent, String fontChoice,
                              int lineSpacing, int marginPercent, String textAlignment, boolean autoSpacing) {
        Values values = defaults(fontPercent, fontChoice, lineSpacing, marginPercent, textAlignment, autoSpacing);
        if (prefs == null || !hasOverride(prefs, bookName)) return values;
        return read(prefs, prefix(bookName), values);
    }

    /** Explicit import only: copies a pre-v65 hash-keyed record without deleting it. */
    public static boolean importLegacy(SharedPreferences prefs, String bookName) {
        if (prefs == null || bookName == null || !hasLegacySaved(prefs, bookName)) return false;
        String old = legacyPrefix(bookName), fresh = prefix(bookName);
        SharedPreferences.Editor e = prefs.edit().putBoolean(fresh + "saved", true).putBoolean(fresh + "enabled", true);
        copy(e, prefs, old, fresh, "font_percent"); copy(e, prefs, old, fresh, "font_choice");
        copy(e, prefs, old, fresh, "line_spacing"); copy(e, prefs, old, fresh, "margin_percent");
        copy(e, prefs, old, fresh, "text_alignment"); copy(e, prefs, old, fresh, "auto_spacing");
        e.putLong("sync_updated_ms", System.currentTimeMillis()).apply();
        return true;
    }

    public static void save(SharedPreferences prefs, String bookName, int fontPercent, String fontChoice,
                            int lineSpacing, int marginPercent, String textAlignment, boolean autoSpacing) {
        if (prefs == null || bookName == null || bookName.isEmpty()) return;
        String p = prefix(bookName);
        prefs.edit().putBoolean(p + "enabled", true).putBoolean(p + "saved", true)
                .putInt(p + "font_percent", fontPercent)
                .putString(p + "font_choice", fontChoice == null ? "publisher" : fontChoice)
                .putInt(p + "line_spacing", lineSpacing).putInt(p + "margin_percent", marginPercent)
                .putString(p + "text_alignment", textAlignment == null ? "justify" : textAlignment)
                .putBoolean(p + "auto_spacing", autoSpacing)
                .putLong("sync_updated_ms", System.currentTimeMillis()).apply();
    }

    private static Values defaults(int f, String c, int l, int m, String a, boolean s) {
        Values v = new Values(); v.fontPercent=f; v.fontChoice=c; v.lineSpacing=l; v.marginPercent=m; v.textAlignment=a; v.autoSpacing=s; return v;
    }
    private static Values read(SharedPreferences p, String k, Values v) {
        v.fontPercent=p.getInt(k+"font_percent",v.fontPercent); v.fontChoice=p.getString(k+"font_choice",v.fontChoice);
        v.lineSpacing=p.getInt(k+"line_spacing",v.lineSpacing); v.marginPercent=p.getInt(k+"margin_percent",v.marginPercent);
        v.textAlignment=p.getString(k+"text_alignment",v.textAlignment); v.autoSpacing=p.getBoolean(k+"auto_spacing",v.autoSpacing); return v;
    }
    private static void copy(SharedPreferences.Editor e, SharedPreferences p, String old, String fresh, String key) {
        if (!p.contains(old+key)) return;
        Object value=p.getAll().get(old+key); if (value instanceof Boolean) e.putBoolean(fresh+key,(Boolean)value); else if (value instanceof Integer) e.putInt(fresh+key,(Integer)value); else if (value instanceof String) e.putString(fresh+key,(String)value);
    }
    private static String prefix(String name) { return "book_typography_" + sha256(name == null ? "book" : name) + "_"; }
    private static String legacyPrefix(String name) { return "book_typography_" + Integer.toHexString((name == null ? "book" : name).hashCode()) + "_"; }
    private static String sha256(String value) {
        try { byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder s=new StringBuilder(); for(byte x:b)s.append(String.format("%02x",x)); return s.toString(); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
