package com.whisper.wowreader;

import android.content.SharedPreferences;

/** Single source of truth for per-book reading percentage. */
final class ReadingProgressStore {
    private ReadingProgressStore() {}

    static int get(SharedPreferences prefs, String fileName) {
        if (prefs == null || fileName == null) return 0;
        return clamp(prefs.getInt("percent_" + fileName, 0));
    }

    static void set(SharedPreferences prefs, String fileName, int percent) {
        if (prefs == null || fileName == null) return;
        prefs.edit().putInt("percent_" + fileName, clamp(percent)).apply();
    }

    static void remove(SharedPreferences prefs, String fileName) {
        if (prefs == null || fileName == null) return;
        prefs.edit().remove("percent_" + fileName).apply();
    }

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
}
