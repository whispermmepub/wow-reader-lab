from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/whisper/wowreader/MainActivity.java"
READER = ROOT / "app/src/main/java/com/whisper/wowreader/BookReaderActivity.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


main = MAIN.read_text(encoding="utf-8")
main = replace_once(
    main,
    '    private TextView themeButton;\n',
    '    private TextView themeButton;\n    private TextView statsSummaryView;\n',
    "stats summary field",
)

main = replace_once(
    main,
    '        if (authorButton != null) authorButton.setText(authorButtonLabel());\n\n        warmSortMetadataIfNeeded(all);',
    '        if (authorButton != null) authorButton.setText(authorButtonLabel());\n        updateReadingStatsSummary();\n\n        warmSortMetadataIfNeeded(all);',
    "refresh stats summary",
)

main = replace_once(
    main,
    '        hero.addView(searchInput, searchLp);\n        outer.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));',
    '''        hero.addView(searchInput, searchLp);\n\n        LinearLayout readingStatsCard = new LinearLayout(this);\n        readingStatsCard.setOrientation(LinearLayout.HORIZONTAL);\n        readingStatsCard.setGravity(Gravity.CENTER_VERTICAL);\n        readingStatsCard.setPadding(dp(13), 0, dp(12), 0);\n        readingStatsCard.setBackground(roundRect(themeControlSurface(), dp(19), dp(1), themeStroke()));\n        readingStatsCard.setClickable(true);\n        readingStatsCard.setElevation(dp(1));\n        readingStatsCard.setContentDescription("Reading statistics");\n        readingStatsCard.setOnClickListener(v -> showReadingStatsDialog());\n\n        TextView statsIcon = new TextView(this);\n        statsIcon.setText("◷");\n        statsIcon.setTextSize(20);\n        statsIcon.setTextColor(themeAccent());\n        statsIcon.setGravity(Gravity.CENTER);\n        readingStatsCard.addView(statsIcon, new LinearLayout.LayoutParams(dp(34), dp(44)));\n\n        LinearLayout statsCopy = new LinearLayout(this);\n        statsCopy.setOrientation(LinearLayout.VERTICAL);\n        statsCopy.setGravity(Gravity.CENTER_VERTICAL);\n        TextView statsTitle = new TextView(this);\n        statsTitle.setText("Reading");\n        statsTitle.setTextSize(12.5f);\n        statsTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);\n        statsTitle.setTextColor(themePrimaryText());\n        statsCopy.addView(statsTitle);\n        statsSummaryView = new TextView(this);\n        statsSummaryView.setTextSize(10.5f);\n        statsSummaryView.setTextColor(themeSecondaryText());\n        statsSummaryView.setSingleLine(true);\n        statsCopy.addView(statsSummaryView);\n        readingStatsCard.addView(statsCopy, new LinearLayout.LayoutParams(0, dp(44), 1f));\n\n        TextView statsArrow = new TextView(this);\n        statsArrow.setText("›");\n        statsArrow.setTextSize(22);\n        statsArrow.setTextColor(themeSecondaryText());\n        statsArrow.setGravity(Gravity.CENTER);\n        readingStatsCard.addView(statsArrow, new LinearLayout.LayoutParams(dp(28), dp(44)));\n\n        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));\n        statsLp.topMargin = dp(8);\n        hero.addView(readingStatsCard, statsLp);\n        updateReadingStatsSummary();\n\n        outer.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));''',
    "reading stats card",
)

main = replace_once(
    main,
    '    private View buildLibrarySectionHeader() {',
    '''    private void updateReadingStatsSummary() {\n        if (statsSummaryView == null || prefs == null) return;\n        ReadingStatsStore.Snapshot stats = ReadingStatsStore.snapshot(prefs);\n        String streak = stats.currentStreak == 1 ? "1 day streak" : stats.currentStreak + " day streak";\n        statsSummaryView.setText("Today " + formatReadingTime(stats.todayMs) + "  ·  " + streak + "  ·  Total " + formatReadingTime(stats.totalMs));\n    }\n\n    private void showReadingStatsDialog() {\n        ReadingStatsStore.Snapshot stats = ReadingStatsStore.snapshot(prefs);\n        String message = "Today\\n" + formatReadingTimeLong(stats.todayMs) +\n                "\\n\\nCurrent streak\\n" + stats.currentStreak + (stats.currentStreak == 1 ? " day" : " days") +\n                "\\n\\nLongest streak\\n" + stats.longestStreak + (stats.longestStreak == 1 ? " day" : " days") +\n                "\\n\\nActive reading days\\n" + stats.activeDays +\n                "\\n\\nTotal reading time\\n" + formatReadingTimeLong(stats.totalMs);\n        new AlertDialog.Builder(this)\n                .setTitle("Reading statistics")\n                .setMessage(message)\n                .setPositiveButton("Done", null)\n                .show();\n    }\n\n    private String formatReadingTime(long milliseconds) {\n        long minutes = Math.max(0L, milliseconds) / 60_000L;\n        if (minutes < 60L) return minutes + "m";\n        long hours = minutes / 60L;\n        long rest = minutes % 60L;\n        return rest == 0L ? hours + "h" : hours + "h " + rest + "m";\n    }\n\n    private String formatReadingTimeLong(long milliseconds) {\n        long minutes = Math.max(0L, milliseconds) / 60_000L;\n        if (minutes == 0L) return "Less than a minute";\n        if (minutes < 60L) return minutes + (minutes == 1L ? " minute" : " minutes");\n        long hours = minutes / 60L;\n        long rest = minutes % 60L;\n        String result = hours + (hours == 1L ? " hour" : " hours");\n        if (rest > 0L) result += " " + rest + (rest == 1L ? " minute" : " minutes");\n        return result;\n    }\n\n    private View buildLibrarySectionHeader() {''',
    "reading stats methods",
)

MAIN.write_text(main, encoding="utf-8")

reader = READER.read_text(encoding="utf-8")
reader = replace_once(
    reader,
    '    private int chapterLoadGeneration = 0;\n',
    '    private int chapterLoadGeneration = 0;\n    private long readingSessionStartedElapsedMs = 0L;\n',
    "reader session field",
)

reader = replace_once(
    reader,
    '''    protected void onResume() {\n        super.onResume();\n        applyWindowPreferences();''',
    '''    protected void onResume() {\n        super.onResume();\n        if (readingSessionStartedElapsedMs <= 0L)\n            readingSessionStartedElapsedMs = ReadingStatsStore.beginSession();\n        applyWindowPreferences();''',
    "reader onResume stats",
)

reader = replace_once(
    reader,
    '''    protected void onPause() {\n        if (!isPdf) saveEpubState();\n        GoogleAutoSync.flush(this);\n        super.onPause();\n    }''',
    '''    protected void onPause() {\n        ReadingStatsStore.finishSession(prefs, bookFile == null ? null : bookFile.getName(), readingSessionStartedElapsedMs);\n        readingSessionStartedElapsedMs = 0L;\n        if (!isPdf) saveEpubState();\n        GoogleAutoSync.flush(this);\n        super.onPause();\n    }''',
    "reader onPause stats",
)

READER.write_text(reader, encoding="utf-8")
print("Reading statistics integration patch applied successfully.")
