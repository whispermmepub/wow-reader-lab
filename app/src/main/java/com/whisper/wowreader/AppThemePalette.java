package com.whisper.wowreader;

import android.content.SharedPreferences;
import android.graphics.Color;

/**
 * Safe generated palette for the user-selectable Custom app theme.
 * The user chooses one seed colour; readable surfaces, text, strokes and accent
 * are derived automatically so arbitrary colours cannot create invisible UI.
 */
public final class AppThemePalette {
    public static final String PREF_CUSTOM_SEED = "app_custom_theme_seed";
    public static final int DEFAULT_CUSTOM_SEED = Color.rgb(111, 78, 209);

    public final int background;
    public final int card;
    public final int control;
    public final int primary;
    public final int secondary;
    public final int stroke;
    public final int accent;
    /** true means the background is light and system bars should use dark icons. */
    public final boolean darkSystemIcons;

    private AppThemePalette(int background, int card, int control, int primary,
                            int secondary, int stroke, int accent, boolean darkSystemIcons) {
        this.background = background;
        this.card = card;
        this.control = control;
        this.primary = primary;
        this.secondary = secondary;
        this.stroke = stroke;
        this.accent = accent;
        this.darkSystemIcons = darkSystemIcons;
    }

    public static boolean isSupportedTheme(String value) {
        return "white".equals(value) || "black".equals(value) ||
                "navy".equals(value) || "custom".equals(value);
    }

    public static AppThemePalette custom(SharedPreferences prefs) {
        int seed = prefs == null ? DEFAULT_CUSTOM_SEED
                : prefs.getInt(PREF_CUSTOM_SEED, DEFAULT_CUSTOM_SEED);
        return customFromSeed(seed);
    }

    public static AppThemePalette customFromSeed(int rawSeed) {
        int seed = Color.rgb(Color.red(rawSeed), Color.green(rawSeed), Color.blue(rawSeed));
        double seedLum = luminance(seed);
        boolean dark = seedLum < 0.24d;
        if (dark) {
            int background = blend(seed, Color.BLACK, 0.68f);
            int card = blend(seed, Color.BLACK, 0.50f);
            int control = blend(seed, Color.WHITE, 0.13f);
            int primary = Color.rgb(246, 248, 251);
            int secondary = Color.rgb(184, 190, 201);
            int stroke = blend(seed, Color.WHITE, 0.25f);
            int accent = ensureContrast(seed, background, true, 4.5d);
            return new AppThemePalette(background, card, control, primary,
                    secondary, stroke, accent, false);
        } else {
            int background = blend(seed, Color.WHITE, 0.92f);
            int card = blend(seed, Color.WHITE, 0.975f);
            int control = blend(seed, Color.WHITE, 0.86f);
            int primary = Color.rgb(31, 34, 40);
            int secondary = Color.rgb(101, 107, 120);
            int stroke = blend(seed, Color.WHITE, 0.72f);
            int accent = ensureContrast(seed, background, false, 4.5d);
            return new AppThemePalette(background, card, control, primary,
                    secondary, stroke, accent, true);
        }
    }

    /** Accepts #RRGGBB, RRGGBB, #RGB or RGB. Returns null for invalid input. */
    public static Integer parseHex(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 3) {
            s = "" + s.charAt(0) + s.charAt(0)
                    + s.charAt(1) + s.charAt(1)
                    + s.charAt(2) + s.charAt(2);
        }
        if (s.length() != 6) return null;
        try {
            int rgb = (int) Long.parseLong(s, 16);
            return Color.rgb((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String toHex(int color) {
        return String.format(java.util.Locale.US, "#%02X%02X%02X",
                Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int blend(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int r = Math.round(Color.red(from) * (1f - t) + Color.red(to) * t);
        int g = Math.round(Color.green(from) * (1f - t) + Color.green(to) * t);
        int b = Math.round(Color.blue(from) * (1f - t) + Color.blue(to) * t);
        return Color.rgb(r, g, b);
    }

    private static int ensureContrast(int color, int background, boolean lighten,
                                      double targetRatio) {
        int out = color;
        int target = lighten ? Color.WHITE : Color.BLACK;
        for (int i = 0; i < 18 && contrast(out, background) < targetRatio; i++) {
            out = blend(out, target, 0.12f);
        }
        return out;
    }

    private static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05d) / (lo + 0.05d);
    }

    private static double luminance(int color) {
        double r = channel(Color.red(color) / 255d);
        double g = channel(Color.green(color) / 255d);
        double b = channel(Color.blue(color) / 255d);
        return 0.2126d * r + 0.7152d * g + 0.0722d * b;
    }

    private static double channel(double c) {
        return c <= 0.04045d ? c / 12.92d : Math.pow((c + 0.055d) / 1.055d, 2.4d);
    }
}
