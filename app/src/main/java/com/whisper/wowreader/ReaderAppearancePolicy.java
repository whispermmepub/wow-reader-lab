package com.whisper.wowreader;

import java.util.Locale;

/** Pure decisions shared by reader UI and tests; PDF callers should not apply typography. */
public final class ReaderAppearancePolicy {
    public static final int ALL_BOOKS = 0;
    public static final int THIS_BOOK = 1;
    private ReaderAppearancePolicy() {}

    public static boolean usesBookOverride(int scope, boolean enabled, boolean saved) {
        return scope == THIS_BOOK && enabled && saved;
    }

    public static boolean safeHex(String value) {
        if (value == null) return false;
        String v = value.trim();
        return v.matches("#[0-9a-fA-F]{6}");
    }

    public static String normalizedHex(String value) {
        return safeHex(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }
}
