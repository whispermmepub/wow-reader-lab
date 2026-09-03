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
    '    private TextView statsSummaryView;\n',
    '    private TextView statsSummaryView;\n    private TextView notesSummaryView;\n',
    'notes summary field',
)

main = replace_once(
    main,
    '''        updateLibraryFilterChips();\n        updateReadingStatsSummary();\n\n        warmSortMetadataIfNeeded(all);''',
    '''        updateLibraryFilterChips();\n        updateReadingStatsSummary();\n        updateNotesHubSummary();\n\n        warmSortMetadataIfNeeded(all);''',
    'refresh notes summary',
)

main = replace_once(
    main,
    '''        hero.addView(readingStatsCard, statsLp);\n        updateReadingStatsSummary();\n\n        HorizontalScrollView smartFilters = new HorizontalScrollView(this);''',
    '''        hero.addView(readingStatsCard, statsLp);\n        updateReadingStatsSummary();\n\n        LinearLayout notesHubCard = new LinearLayout(this);\n        notesHubCard.setOrientation(LinearLayout.HORIZONTAL);\n        notesHubCard.setGravity(Gravity.CENTER_VERTICAL);\n        notesHubCard.setPadding(dp(13), 0, dp(12), 0);\n        notesHubCard.setBackground(roundRect(themeControlSurface(), dp(19), dp(1), themeStroke()));\n        notesHubCard.setClickable(true);\n        notesHubCard.setElevation(dp(1));\n        notesHubCard.setContentDescription("Notes and highlights hub");\n        notesHubCard.setOnClickListener(v -> showNotesHighlightsHub());\n\n        TextView notesIcon = new TextView(this);\n        notesIcon.setText("✎");\n        notesIcon.setTextSize(18);\n        notesIcon.setTextColor(themeAccent());\n        notesIcon.setGravity(Gravity.CENTER);\n        notesHubCard.addView(notesIcon, new LinearLayout.LayoutParams(dp(34), dp(44)));\n\n        LinearLayout notesCopy = new LinearLayout(this);\n        notesCopy.setOrientation(LinearLayout.VERTICAL);\n        notesCopy.setGravity(Gravity.CENTER_VERTICAL);\n        TextView notesTitle = new TextView(this);\n        notesTitle.setText("Notes & highlights");\n        notesTitle.setTextSize(12.5f);\n        notesTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);\n        notesTitle.setTextColor(themePrimaryText());\n        notesCopy.addView(notesTitle);\n        notesSummaryView = new TextView(this);\n        notesSummaryView.setTextSize(10.5f);\n        notesSummaryView.setTextColor(themeSecondaryText());\n        notesSummaryView.setSingleLine(true);\n        notesCopy.addView(notesSummaryView);\n        notesHubCard.addView(notesCopy, new LinearLayout.LayoutParams(0, dp(44), 1f));\n\n        TextView notesArrow = new TextView(this);\n        notesArrow.setText("›");\n        notesArrow.setTextSize(22);\n        notesArrow.setTextColor(themeSecondaryText());\n        notesArrow.setGravity(Gravity.CENTER);\n        notesHubCard.addView(notesArrow, new LinearLayout.LayoutParams(dp(28), dp(44)));\n\n        LinearLayout.LayoutParams notesLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));\n        notesLp.topMargin = dp(7);\n        hero.addView(notesHubCard, notesLp);\n        updateNotesHubSummary();\n\n        HorizontalScrollView smartFilters = new HorizontalScrollView(this);''',
    'notes hub card',
)

main = replace_once(
    main,
    '    private void updateReadingStatsSummary() {',
    '''    private void updateNotesHubSummary() {\n        if (notesSummaryView == null || prefs == null || libraryDir == null) return;\n        File[] books = libraryDir.listFiles(file -> file.isFile() && isBook(file.getName()));\n        if (books == null || books.length == 0) {\n            notesSummaryView.setText("No saved notes yet");\n            return;\n        }\n        int itemCount = 0;\n        int bookCount = 0;\n        for (File book : books) {\n            int count = ReaderAnnotationStore.count(prefs, book.getName());\n            if (count <= 0) continue;\n            itemCount += count;\n            bookCount++;\n        }\n        if (itemCount <= 0) {\n            notesSummaryView.setText("No saved notes yet");\n            return;\n        }\n        String itemText = itemCount == 1 ? "1 item" : itemCount + " items";\n        String bookText = bookCount == 1 ? "1 book" : bookCount + " books";\n        notesSummaryView.setText(itemText + "  ·  " + bookText);\n    }\n\n    private void showNotesHighlightsHub() {\n        File[] books = libraryDir.listFiles(file -> file.isFile() && isBook(file.getName()));\n        if (books == null) books = new File[0];\n        sortLibraryFiles(books);\n        java.util.List<File> annotatedBooks = new java.util.ArrayList<>();\n        java.util.List<String> labels = new java.util.ArrayList<>();\n        for (File book : books) {\n            int count = ReaderAnnotationStore.count(prefs, book.getName());\n            if (count <= 0) continue;\n            annotatedBooks.add(book);\n            labels.add(cachedLibraryTitle(book) + "  ·  " + count + (count == 1 ? " item" : " items"));\n        }\n        if (annotatedBooks.isEmpty()) {\n            new AlertDialog.Builder(this)\n                    .setTitle("Notes & highlights")\n                    .setMessage("Your saved highlights and notes will appear here. Select text while reading an EPUB and choose Highlight or Note.")\n                    .setPositiveButton("Done", null)\n                    .show();\n            return;\n        }\n        new AlertDialog.Builder(this)\n                .setTitle("Notes & highlights")\n                .setItems(labels.toArray(new String[0]), (dialog, which) -> openBookAnnotations(annotatedBooks.get(which)))\n                .setNegativeButton("Close", null)\n                .show();\n    }\n\n    private void openBookAnnotations(File file) {\n        if (file == null || !file.isFile()) return;\n        prefs.edit().putLong("last_opened_" + file.getName(), System.currentTimeMillis()).apply();\n        Intent i = new Intent(this, BookReaderActivity.class);\n        i.putExtra("path", file.getAbsolutePath());\n        i.putExtra("open_annotations", true);\n        startActivity(i);\n        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);\n    }\n\n    private void updateReadingStatsSummary() {''',
    'notes hub methods',
)

MAIN.write_text(main, encoding="utf-8")

reader = READER.read_text(encoding="utf-8")
reader = replace_once(
    reader,
    '''        applyWindowPreferences();\n        buildReaderUi();\n        if (isPdf) openPdf(); else openEpub();\n    }''',
    '''        applyWindowPreferences();\n        buildReaderUi();\n        if (isPdf) openPdf(); else openEpub();\n        if (!isPdf && getIntent().getBooleanExtra("open_annotations", false)) {\n            root.postDelayed(() -> {\n                if (!isFinishing()) showAnnotations();\n            }, 700L);\n        }\n    }''',
    'open annotations from library hub',
)

READER.write_text(reader, encoding="utf-8")
print("Notes and Highlights Hub patch applied successfully.")
