from pathlib import Path

P = Path('app/src/main/java/com/whisper/wowreader/MainActivity.java')
text = P.read_text(encoding='utf-8')


def replace_block(src, start, end, replacement, label):
    a = src.find(start)
    if a < 0:
        raise SystemExit(f'missing start marker: {label}')
    b = src.find(end, a)
    if b < 0:
        raise SystemExit(f'missing end marker: {label}')
    return src[:a] + replacement.rstrip() + '\n\n' + src[b:]

shelves = r'''    private void showShelvesDialog() {
        java.util.List<String> shelves = LibraryShelfStore.shelves(prefs);
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = premiumSheet("Shelves", shelves.isEmpty() ? "Create your first shelf" : shelves.size() + " shelves", dialog);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        list.addView(premiumChoiceRow("All shelves", libraryDir.listFiles(file -> file.isFile() && isBook(file.getName())) == null ? "" : "Show every book", shelfFilter.isEmpty(), () -> {
            shelfFilter = "";
            refreshLibrary();
            dialog.dismiss();
        }));
        for (String shelf : shelves) {
            int count = LibraryShelfStore.count(prefs, shelf);
            list.addView(premiumChoiceRow(shelf, count + (count == 1 ? " book" : " books"), shelf.equals(shelfFilter), () -> {
                shelfFilter = shelf;
                refreshLibrary();
                dialog.dismiss();
            }));
        }
        list.addView(premiumChoiceRow("＋ New shelf", "Create a collection", false, () -> {
            dialog.dismiss();
            showCreateShelfDialog(null);
        }));
        int h = Math.min(dp(420), Math.max(dp(120), (shelves.size() + 2) * dp(58)));
        sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        presentBottomSheet(dialog, sheet, 0.82f);
    }

    private View premiumChoiceRow(String titleText, String subtitleText, boolean selected, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(4), dp(10), dp(4));
        row.setBackground(roundRect(selected ? (isBlackAppTheme() ? Color.rgb(49, 48, 75) : Color.rgb(246, 244, 255)) : themeControlSurface(),
                dp(15), dp(1), selected ? themeAccent() : themeStroke()));
        row.setClickable(true);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(13f);
        title.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        title.setTextColor(selected ? themeAccent() : themePrimaryText());
        copy.addView(title);
        if (subtitleText != null && !subtitleText.isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(subtitleText);
            sub.setTextSize(9.5f);
            sub.setTextColor(themeSecondaryText());
            copy.addView(sub);
        }
        row.addView(copy, new LinearLayout.LayoutParams(0, dp(46), 1f));
        TextView mark = new TextView(this);
        mark.setText(selected ? "✓" : "›");
        mark.setTextSize(selected ? 17 : 20);
        mark.setTextColor(selected ? themeAccent() : themeSecondaryText());
        mark.setGravity(Gravity.CENTER);
        row.addView(mark, new LinearLayout.LayoutParams(dp(28), dp(44)));
        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        lp.topMargin = dp(6);
        row.setLayoutParams(lp);
        return row;
    }
'''
text = replace_block(text, '    private void showShelvesDialog() {', '    private void showCreateShelfDialog(File bookToAdd) {', shelves, 'shelves sheet')

create_shelf = r'''    private void showCreateShelfDialog(File bookToAdd) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = premiumSheet("New shelf", "Keep books organized your way", dialog);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Shelf name");
        input.setTextSize(14f);
        input.setTextColor(themePrimaryText());
        input.setHintTextColor(themeSecondaryText());
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(roundRect(themeControlSurface(), dp(16), dp(1), themeStroke()));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        inputLp.topMargin = dp(5);
        sheet.addView(input, inputLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = filterChoice("Cancel", false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        TextView create = filterChoice("Create", true);
        create.setTextColor(Color.WHITE);
        create.setBackground(roundRect(themeAccent(), dp(17), 0, 0));
        create.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (!LibraryShelfStore.createShelf(prefs, name)) {
                Toast.makeText(this, "Enter a shelf name", Toast.LENGTH_SHORT).show();
                return;
            }
            if (bookToAdd != null) LibraryShelfStore.setMembership(prefs, name, bookToAdd.getName(), true);
            shelfFilter = name;
            dialog.dismiss();
            refreshLibrary();
            maybeAutoGoogleSync();
        });
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(dp(94), dp(40)); cancelLp.rightMargin = dp(8);
        actions.addView(cancel, cancelLp);
        actions.addView(create, new LinearLayout.LayoutParams(dp(104), dp(40)));
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)); actionsLp.topMargin = dp(9);
        sheet.addView(actions, actionsLp);
        presentBottomSheet(dialog, sheet, 0.62f);
        input.requestFocus();
    }
'''
text = replace_block(text, '    private void showCreateShelfDialog(File bookToAdd) {', '    private void showBookActions(File file) {', create_shelf, 'new shelf sheet')

book_shelves = r'''    private void showBookShelves(File file) {
        java.util.List<String> shelves = LibraryShelfStore.shelves(prefs);
        if (shelves.isEmpty()) {
            showCreateShelfDialog(file);
            return;
        }
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = premiumSheet("Add to shelves", cachedLibraryTitle(file), dialog);
        boolean[] checked = new boolean[shelves.size()];
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < shelves.size(); i++) {
            final int which = i;
            String shelf = shelves.get(i);
            checked[i] = LibraryShelfStore.contains(prefs, shelf, file.getName());
            TextView row = new TextView(this);
            row.setText((checked[i] ? "✓  " : "○  ") + shelf + "   ·   " + LibraryShelfStore.count(prefs, shelf));
            row.setTextSize(13f);
            row.setTextColor(checked[i] ? themeAccent() : themePrimaryText());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), 0, dp(12), 0);
            row.setBackground(roundRect(themeControlSurface(), dp(15), dp(1), checked[i] ? themeAccent() : themeStroke()));
            row.setOnClickListener(v -> {
                checked[which] = !checked[which];
                ((TextView)v).setText((checked[which] ? "✓  " : "○  ") + shelves.get(which) + "   ·   " + LibraryShelfStore.count(prefs, shelves.get(which)));
                ((TextView)v).setTextColor(checked[which] ? themeAccent() : themePrimaryText());
                ((TextView)v).setBackground(roundRect(themeControlSurface(), dp(15), dp(1), checked[which] ? themeAccent() : themeStroke()));
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
            lp.topMargin = dp(6);
            list.addView(row, lp);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(list);
        sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.min(dp(350), shelves.size() * dp(52) + dp(6))));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView newShelf = filterChoice("＋ New shelf", false);
        newShelf.setOnClickListener(v -> { dialog.dismiss(); showCreateShelfDialog(file); });
        TextView done = filterChoice("Done", true);
        done.setTextColor(Color.WHITE);
        done.setBackground(roundRect(themeAccent(), dp(17), 0, 0));
        done.setOnClickListener(v -> {
            for (int i = 0; i < shelves.size(); i++)
                LibraryShelfStore.setMembership(prefs, shelves.get(i), file.getName(), checked[i]);
            dialog.dismiss();
            refreshLibrary();
            maybeAutoGoogleSync();
        });
        LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(dp(116), dp(40)); newLp.rightMargin = dp(8);
        actions.addView(newShelf, newLp);
        actions.addView(done, new LinearLayout.LayoutParams(dp(90), dp(40)));
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)); actionLp.topMargin = dp(8);
        sheet.addView(actions, actionLp);
        presentBottomSheet(dialog, sheet, 0.84f);
    }
'''
text = replace_block(text, '    private void showBookShelves(File file) {', '    private View buildLibrarySectionHeader() {', book_shelves, 'book shelves sheet')

authors = r'''    private void showAuthorsDialog() {
        File[] files = libraryDir.listFiles(file -> file.isFile() && isBook(file.getName()));
        if (files == null) files = new File[0];
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (File f : files) {
            String author = cachedLibraryAuthor(f);
            if (author.isEmpty()) continue;
            Integer oldCount = counts.get(author);
            counts.put(author, (oldCount == null ? 0 : oldCount) + 1);
        }
        java.util.List<String> authors = new java.util.ArrayList<>(counts.keySet());
        java.util.Collections.sort(authors, (a, b) -> {
            int ga = titleScriptGroup(a), gb = titleScriptGroup(b);
            if (ga != gb) return Integer.compare(ga, gb);
            return ga == 0 ? myanmarCollator.compare(a, b) : englishCollator.compare(a, b);
        });

        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = premiumSheet("Authors", authors.isEmpty() ? "No author metadata yet" : authors.size() + " authors", dialog);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        final int totalBooks = files.length;
        list.addView(premiumChoiceRow("All authors", totalBooks + (totalBooks == 1 ? " book" : " books"), authorFilter.isEmpty(), () -> {
            authorFilter = "";
            dialog.dismiss();
            refreshLibrary();
        }));
        for (String author : authors) {
            int count = counts.get(author);
            list.addView(premiumChoiceRow(author, count + (count == 1 ? " book" : " books"), author.equals(authorFilter), () -> {
                authorFilter = author;
                dialog.dismiss();
                refreshLibrary();
            }));
        }
        int h = Math.min(dp(430), Math.max(dp(110), (authors.size() + 1) * dp(58)));
        sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        presentBottomSheet(dialog, sheet, 0.84f);
        warmSortMetadataIfNeeded(files);
    }
'''
text = replace_block(text, '    private void showAuthorsDialog() {', '    private String sortButtonLabel() {', authors, 'authors sheet')

sort = r'''    private void showSortDialog() {
        String[] labels = {"Recently added", "Recently opened", "Title · က–အ / A–Z", "Title · အ–က / Z–A"};
        String[] values = {"added", "opened", "title_asc", "title_desc"};
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = premiumSheet("Sort library", "Choose how books are ordered", dialog);
        for (int i = 0; i < labels.length; i++) {
            final String value = values[i];
            sheet.addView(premiumChoiceRow(labels[i], "", value.equals(sortMode), () -> {
                sortMode = value;
                prefs.edit().putString("library_sort", sortMode).apply();
                if (sortButton != null) sortButton.setText(sortButtonLabel());
                dialog.dismiss();
                refreshLibrary();
            }));
        }
        presentBottomSheet(dialog, sheet, 0.72f);
    }
'''
text = replace_block(text, '    private void showSortDialog() {', '    private View buildEmptyState() {', sort, 'sort sheet')

confirm_delete = r'''    private void confirmDelete(File file) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = premiumSheet("Remove from WoW Reader?", cachedLibraryTitle(file), dialog);
        TextView message = new TextView(this);
        message.setText("This deletes WoW Reader's saved local copy. The original file you imported from Downloads or another folder is not changed.");
        message.setTextSize(12f);
        message.setTextColor(themeSecondaryText());
        message.setLineSpacing(dp(2), 1.12f);
        message.setPadding(dp(12), dp(10), dp(12), dp(10));
        message.setBackground(roundRect(themeControlSurface(), dp(15), dp(1), themeStroke()));
        sheet.addView(message);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = filterChoice("Cancel", false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        TextView remove = filterChoice("Remove", true);
        remove.setTextColor(Color.WHITE);
        remove.setBackground(roundRect(Color.rgb(205, 63, 63), dp(17), 0, 0));
        remove.setOnClickListener(v -> {
            dialog.dismiss();
            if (file.delete()) {
                LibraryShelfStore.removeBookFromAll(prefs, file.getName());
                prefs.edit().remove("percent_" + file.getName()).remove("library_title_" + file.getName())
                        .remove("library_author_" + file.getName()).remove("library_owned_" + file.getName())
                        .remove("added_at_" + file.getName()).remove("last_opened_" + file.getName())
                        .putLong("sync_updated_ms", System.currentTimeMillis()).apply();
                refreshLibrary();
                maybeAutoGoogleSync();
            }
        });
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(dp(96), dp(40)); cancelLp.rightMargin = dp(8);
        actions.addView(cancel, cancelLp);
        actions.addView(remove, new LinearLayout.LayoutParams(dp(106), dp(40)));
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)); actionLp.topMargin = dp(10);
        sheet.addView(actions, actionLp);
        presentBottomSheet(dialog, sheet, 0.62f);
    }
'''
text = replace_block(text, '    private void confirmDelete(File file) {', '    private void restoreStoredGoogleProfile() {', confirm_delete, 'delete confirmation')

P.write_text(text, encoding='utf-8')
print('Premium library sheets patch applied')
