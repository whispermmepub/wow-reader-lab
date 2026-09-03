from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/whisper/wowreader/MainActivity.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


text = MAIN.read_text(encoding="utf-8")

text = replace_once(
    text,
    '    private String authorFilter = "";\n',
    '    private String authorFilter = "";\n    private String libraryStatusFilter = "all";\n    private String shelfFilter = "";\n    private TextView statusAllChip;\n    private TextView statusReadingChip;\n    private TextView statusUnreadChip;\n    private TextView statusFinishedChip;\n    private TextView shelfChip;\n',
    "smart library fields",
)

text = replace_once(
    text,
    '''            if (!authorFilter.isEmpty() && !authorFilter.equals(author)) continue;\n            if (searchQuery.isEmpty() || cachedTitle.contains(searchQuery) || fileTitle.contains(searchQuery) || authorLower.contains(searchQuery))\n                visibleBooks.add(f);''',
    '''            if (!authorFilter.isEmpty() && !authorFilter.equals(author)) continue;\n            int progress = prefs.getInt("percent_" + f.getName(), 0);\n            if (!matchesLibraryStatus(progress)) continue;\n            if (!shelfFilter.isEmpty() && !LibraryShelfStore.contains(prefs, shelfFilter, f.getName())) continue;\n            if (searchQuery.isEmpty() || cachedTitle.contains(searchQuery) || fileTitle.contains(searchQuery) || authorLower.contains(searchQuery))\n                visibleBooks.add(f);''',
    "smart library filtering",
)

text = replace_once(
    text,
    '''            String suffix = visibleBooks.size() == 1 ? " book" : " books";\n            countView.setText(visibleBooks.size() + suffix + (authorFilter.isEmpty() ? "" : " · " + authorFilter));\n        }\n        if (sortButton != null) sortButton.setText(sortButtonLabel());\n        if (authorButton != null) authorButton.setText(authorButtonLabel());\n        updateReadingStatsSummary();''',
    '''            String suffix = visibleBooks.size() == 1 ? " book" : " books";\n            String filters = libraryFilterDescription();\n            countView.setText(visibleBooks.size() + suffix + (filters.isEmpty() ? "" : " · " + filters));\n        }\n        if (sortButton != null) sortButton.setText(sortButtonLabel());\n        if (authorButton != null) authorButton.setText(authorButtonLabel());\n        updateLibraryFilterChips();\n        updateReadingStatsSummary();''',
    "smart library count",
)

text = replace_once(
    text,
    '        card.setOnLongClickListener(v -> { confirmDelete(file); return true; });',
    '        card.setOnLongClickListener(v -> { showBookActions(file); return true; });',
    "grid long press",
)
text = replace_once(
    text,
    '        card.setOnLongClickListener(v -> { confirmDelete(file); return true; });',
    '        card.setOnLongClickListener(v -> { showBookActions(file); return true; });',
    "list long press",
)

text = replace_once(
    text,
    '''        hero.addView(readingStatsCard, statsLp);\n        updateReadingStatsSummary();\n\n        outer.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));''',
    '''        hero.addView(readingStatsCard, statsLp);\n        updateReadingStatsSummary();\n\n        HorizontalScrollView smartFilters = new HorizontalScrollView(this);\n        smartFilters.setHorizontalScrollBarEnabled(false);\n        smartFilters.setOverScrollMode(View.OVER_SCROLL_NEVER);\n        LinearLayout filterStrip = new LinearLayout(this);\n        filterStrip.setOrientation(LinearLayout.HORIZONTAL);\n        filterStrip.setGravity(Gravity.CENTER_VERTICAL);\n        filterStrip.setPadding(0, 0, dp(8), 0);\n        smartFilters.addView(filterStrip, new HorizontalScrollView.LayoutParams(\n                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));\n\n        statusAllChip = libraryFilterChip("All");\n        statusReadingChip = libraryFilterChip("Reading");\n        statusUnreadChip = libraryFilterChip("Unread");\n        statusFinishedChip = libraryFilterChip("Finished");\n        shelfChip = libraryFilterChip("Shelves  ▾");\n        statusAllChip.setOnClickListener(v -> setLibraryStatusFilter("all"));\n        statusReadingChip.setOnClickListener(v -> setLibraryStatusFilter("reading"));\n        statusUnreadChip.setOnClickListener(v -> setLibraryStatusFilter("unread"));\n        statusFinishedChip.setOnClickListener(v -> setLibraryStatusFilter("finished"));\n        shelfChip.setOnClickListener(v -> showShelvesDialog());\n        addFilterChip(filterStrip, statusAllChip);\n        addFilterChip(filterStrip, statusReadingChip);\n        addFilterChip(filterStrip, statusUnreadChip);\n        addFilterChip(filterStrip, statusFinishedChip);\n        addFilterChip(filterStrip, shelfChip);\n        LinearLayout.LayoutParams filtersLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));\n        filtersLp.topMargin = dp(7);\n        hero.addView(smartFilters, filtersLp);\n        updateLibraryFilterChips();\n\n        outer.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));''',
    "smart filter strip",
)

text = replace_once(
    text,
    '    private View buildLibrarySectionHeader() {',
    '''    private TextView libraryFilterChip(String label) {\n        TextView chip = new TextView(this);\n        chip.setText(label);\n        chip.setTextSize(11.5f);\n        chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);\n        chip.setGravity(Gravity.CENTER);\n        chip.setSingleLine(true);\n        chip.setPadding(dp(13), 0, dp(13), 0);\n        chip.setClickable(true);\n        return chip;\n    }\n\n    private void addFilterChip(LinearLayout strip, TextView chip) {\n        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));\n        lp.rightMargin = dp(7);\n        strip.addView(chip, lp);\n    }\n\n    private void setLibraryStatusFilter(String value) {\n        libraryStatusFilter = value == null ? "all" : value;\n        updateLibraryFilterChips();\n        refreshLibrary();\n    }\n\n    private boolean matchesLibraryStatus(int progress) {\n        if ("reading".equals(libraryStatusFilter)) return progress > 0 && progress < 100;\n        if ("unread".equals(libraryStatusFilter)) return progress <= 0;\n        if ("finished".equals(libraryStatusFilter)) return progress >= 100;\n        return true;\n    }\n\n    private String libraryFilterDescription() {\n        java.util.List<String> parts = new java.util.ArrayList<>();\n        if (!authorFilter.isEmpty()) parts.add(authorFilter);\n        if ("reading".equals(libraryStatusFilter)) parts.add("Reading");\n        else if ("unread".equals(libraryStatusFilter)) parts.add("Unread");\n        else if ("finished".equals(libraryStatusFilter)) parts.add("Finished");\n        if (!shelfFilter.isEmpty()) parts.add(shelfFilter);\n        return android.text.TextUtils.join(" · ", parts);\n    }\n\n    private void updateLibraryFilterChips() {\n        styleLibraryFilterChip(statusAllChip, "all".equals(libraryStatusFilter));\n        styleLibraryFilterChip(statusReadingChip, "reading".equals(libraryStatusFilter));\n        styleLibraryFilterChip(statusUnreadChip, "unread".equals(libraryStatusFilter));\n        styleLibraryFilterChip(statusFinishedChip, "finished".equals(libraryStatusFilter));\n        if (shelfChip != null) shelfChip.setText(shelfFilter.isEmpty() ? "Shelves  ▾" : shelfFilter + "  ×");\n        styleLibraryFilterChip(shelfChip, !shelfFilter.isEmpty());\n    }\n\n    private void styleLibraryFilterChip(TextView chip, boolean active) {\n        if (chip == null) return;\n        int fill = active ? themeAccent() : themeControlSurface();\n        chip.setTextColor(active ? Color.WHITE : themeSecondaryText());\n        chip.setBackground(roundRect(fill, dp(17), dp(1), active ? themeAccent() : themeStroke()));\n        chip.setElevation(active ? dp(2) : 0);\n    }\n\n    private void showShelvesDialog() {\n        java.util.List<String> shelves = LibraryShelfStore.shelves(prefs);\n        String[] labels = new String[shelves.size() + 2];\n        labels[0] = "All shelves";\n        for (int i = 0; i < shelves.size(); i++) {\n            String name = shelves.get(i);\n            labels[i + 1] = name + " · " + LibraryShelfStore.count(prefs, name) + " books";\n        }\n        labels[labels.length - 1] = "＋ New shelf";\n        new AlertDialog.Builder(this)\n                .setTitle("Shelves")\n                .setItems(labels, (dialog, which) -> {\n                    if (which == 0) {\n                        shelfFilter = "";\n                        refreshLibrary();\n                    } else if (which == labels.length - 1) {\n                        showCreateShelfDialog(null);\n                    } else {\n                        shelfFilter = shelves.get(which - 1);\n                        refreshLibrary();\n                    }\n                })\n                .setNegativeButton("Cancel", null)\n                .show();\n    }\n\n    private void showCreateShelfDialog(File bookToAdd) {\n        EditText input = new EditText(this);\n        input.setSingleLine(true);\n        input.setHint("Shelf name");\n        input.setPadding(dp(14), 0, dp(14), 0);\n        new AlertDialog.Builder(this)\n                .setTitle("New shelf")\n                .setView(input)\n                .setNegativeButton("Cancel", null)\n                .setPositiveButton("Create", (dialog, which) -> {\n                    String name = input.getText().toString().trim();\n                    if (!LibraryShelfStore.createShelf(prefs, name)) {\n                        Toast.makeText(this, "Enter a shelf name", Toast.LENGTH_SHORT).show();\n                        return;\n                    }\n                    if (bookToAdd != null) LibraryShelfStore.setMembership(prefs, name, bookToAdd.getName(), true);\n                    shelfFilter = name;\n                    refreshLibrary();\n                    maybeAutoGoogleSync();\n                })\n                .show();\n    }\n\n    private void showBookActions(File file) {\n        if (file == null) return;\n        String[] actions = {"Manage shelves", "Remove from WoW Reader"};\n        new AlertDialog.Builder(this)\n                .setTitle(cachedLibraryTitle(file))\n                .setItems(actions, (dialog, which) -> {\n                    if (which == 0) showBookShelves(file);\n                    else confirmDelete(file);\n                })\n                .setNegativeButton("Cancel", null)\n                .show();\n    }\n\n    private void showBookShelves(File file) {\n        java.util.List<String> shelves = LibraryShelfStore.shelves(prefs);\n        if (shelves.isEmpty()) {\n            new AlertDialog.Builder(this)\n                    .setTitle("No shelves yet")\n                    .setMessage("Create a shelf and add this book to it.")\n                    .setNegativeButton("Cancel", null)\n                    .setPositiveButton("Create shelf", (d, w) -> showCreateShelfDialog(file))\n                    .show();\n            return;\n        }\n        String[] labels = shelves.toArray(new String[0]);\n        boolean[] checked = new boolean[labels.length];\n        for (int i = 0; i < labels.length; i++) checked[i] = LibraryShelfStore.contains(prefs, labels[i], file.getName());\n        new AlertDialog.Builder(this)\n                .setTitle("Add to shelves")\n                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)\n                .setNeutralButton("New shelf", (dialog, which) -> showCreateShelfDialog(file))\n                .setNegativeButton("Cancel", null)\n                .setPositiveButton("Done", (dialog, which) -> {\n                    for (int i = 0; i < labels.length; i++)\n                        LibraryShelfStore.setMembership(prefs, labels[i], file.getName(), checked[i]);\n                    refreshLibrary();\n                    maybeAutoGoogleSync();\n                })\n                .show();\n    }\n\n    private View buildLibrarySectionHeader() {''',
    "smart library methods",
)

text = replace_once(
    text,
    'private void confirmDelete(File file){new AlertDialog.Builder(this).setTitle("Remove from WoW Reader?").setMessage(stripExtension(file.getName())+"\\n\\nThis deletes WoW Reader\'s saved local copy. The original file you imported from Downloads or another folder is not changed.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->{if(file.delete()){prefs.edit().remove("percent_"+file.getName()).remove("library_title_"+file.getName()).remove("library_author_"+file.getName()).remove("library_owned_"+file.getName()).remove("added_at_"+file.getName()).remove("last_opened_"+file.getName()).putLong("sync_updated_ms",System.currentTimeMillis()).apply();refreshLibrary();maybeAutoGoogleSync();}}).show();}',
    'private void confirmDelete(File file){new AlertDialog.Builder(this).setTitle("Remove from WoW Reader?").setMessage(stripExtension(file.getName())+"\\n\\nThis deletes WoW Reader\'s saved local copy. The original file you imported from Downloads or another folder is not changed.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->{if(file.delete()){LibraryShelfStore.removeBookFromAll(prefs,file.getName());prefs.edit().remove("percent_"+file.getName()).remove("library_title_"+file.getName()).remove("library_author_"+file.getName()).remove("library_owned_"+file.getName()).remove("added_at_"+file.getName()).remove("last_opened_"+file.getName()).putLong("sync_updated_ms",System.currentTimeMillis()).apply();refreshLibrary();maybeAutoGoogleSync();}}).show();}',
    "remove deleted book from shelves",
)

MAIN.write_text(text, encoding="utf-8")
print("Smart library filters and shelves patch applied successfully.")
