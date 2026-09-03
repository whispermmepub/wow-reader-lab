from pathlib import Path
import re

MAIN = Path('app/src/main/java/com/whisper/wowreader/MainActivity.java')
text = MAIN.read_text(encoding='utf-8')


def replace_block(src, start, end, replacement, label):
    a = src.find(start)
    if a < 0:
        raise SystemExit(f'missing start marker: {label}')
    b = src.find(end, a)
    if b < 0:
        raise SystemExit(f'missing end marker: {label}')
    return src[:a] + replacement.rstrip() + '\n\n' + src[b:]

# New compact metric field used by the premium reading strip.
needle = '    private TextView statsSummaryView;\n    private TextView notesSummaryView;'
if needle in text:
    text = text.replace(needle,
        '    private TextView statsSummaryView;\n'
        '    private TextView streakSummaryView;\n'
        '    private TextView notesSummaryView;', 1)
elif 'private TextView streakSummaryView;' not in text:
    raise SystemExit('stats field anchor missing')

# Leave more room for the persistent bottom navigation.
text = text.replace('libraryRecycler.setPadding(0, 0, 0, dp(96));',
                    'libraryRecycler.setPadding(0, 0, 0, dp(138));')

old_fab = '''        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(dp(124), dp(58), Gravity.END | Gravity.BOTTOM);\n        fabLp.rightMargin = dp(16);\n        fabLp.bottomMargin = dp(20);\n        root.addView(floatingAdd, fabLp);'''
new_fab = '''        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(dp(116), dp(48), Gravity.END | Gravity.BOTTOM);\n        fabLp.rightMargin = dp(16);\n        fabLp.bottomMargin = dp(74);\n        root.addView(floatingAdd, fabLp);'''
if old_fab in text:
    text = text.replace(old_fab, new_fab, 1)
elif 'fabLp.bottomMargin = dp(74);' not in text:
    raise SystemExit('FAB anchor missing')

old_set = '''        setContentView(root);\n        refreshLibrary();'''
new_set = '''        View premiumBottomNav = buildBottomNavigation();\n        FrameLayout.LayoutParams bottomNavLp = new FrameLayout.LayoutParams(\n                ViewGroup.LayoutParams.MATCH_PARENT, dp(64), Gravity.BOTTOM);\n        bottomNavLp.leftMargin = dp(10);\n        bottomNavLp.rightMargin = dp(10);\n        bottomNavLp.bottomMargin = dp(6);\n        root.addView(premiumBottomNav, bottomNavLp);\n\n        setContentView(root);\n        refreshLibrary();'''
if old_set in text:
    text = text.replace(old_set, new_set, 1)
elif 'buildBottomNavigation();' not in text:
    raise SystemExit('setContentView anchor missing')

# Long press should anchor the compact contextual popup to the touched card.
text = text.replace('card.setOnLongClickListener(v -> { showBookActions(file); return true; });',
                    'card.setOnLongClickListener(v -> { showBookActions(file, v); return true; });')

header = r'''    private View buildLibraryHeader() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(14), dp(12), dp(14), dp(4));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setPadding(dp(4), 0, dp(2), 0);

        TextView brand = new TextView(this);
        brand.setText("WoW");
        brand.setTextSize(34);
        brand.setTextColor(themePrimaryText());
        brand.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.addView(brand, new LinearLayout.LayoutParams(0, dp(58), 1f));

        accountButton = new ProfileAvatarView(this);
        accountButton.setContentDescription("Google account & cloud library");
        accountButton.setOnClickListener(v -> showAccountMenu());
        brandRow.addView(accountButton, new LinearLayout.LayoutParams(dp(46), dp(46)));
        updateAccountButton();

        themeButton = iconButton("navy".equals(appTheme) ? "✦" : "◐");
        themeButton.setTextSize(16);
        themeButton.setContentDescription("App theme");
        themeButton.setOnClickListener(v -> showAppThemeDialog());
        LinearLayout.LayoutParams themeLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        themeLp.leftMargin = dp(8);
        brandRow.addView(themeButton, themeLp);

        viewModeButton = iconButton(gridMode ? "▦" : "☷");
        viewModeButton.setTextSize(16);
        viewModeButton.setContentDescription("Change library view");
        viewModeButton.setOnClickListener(v -> {
            gridMode = !gridMode;
            prefs.edit().putBoolean("library_grid", gridMode).apply();
            viewModeButton.setText(gridMode ? "▦" : "☷");
            configureLibraryLayout();
            if (libraryAdapter != null) libraryAdapter.notifyDataSetChanged();
        });
        LinearLayout.LayoutParams viewLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        viewLp.leftMargin = dp(8);
        brandRow.addView(viewModeButton, viewLp);
        outer.addView(brandRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(2), 0, dp(2), 0);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("Search title or author");
        searchInput.setTextSize(14.5f);
        searchInput.setTextColor(themePrimaryText());
        searchInput.setHintTextColor(themeSecondaryText());
        searchInput.setPadding(dp(17), 0, dp(17), 0);
        searchInput.setBackground(roundRect(themeSearchSurface(), dp(25), dp(1), themeStroke()));
        if (!searchQuery.isEmpty()) {
            searchInput.setText(searchQuery);
            searchInput.setSelection(searchInput.length());
        }
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim().toLowerCase(Locale.ROOT);
                refreshLibrary();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, dp(50), 1f));

        TextView filter = iconButton("⌁");
        filter.setTextSize(19);
        filter.setContentDescription("Filter and sort library");
        filter.setOnClickListener(v -> showLibraryFilterSheet());
        LinearLayout.LayoutParams filterLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        filterLp.leftMargin = dp(8);
        searchRow.addView(filter, filterLp);
        LinearLayout.LayoutParams searchRowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        searchRowLp.topMargin = dp(6);
        outer.addView(searchRow, searchRowLp);

        addContinueReadingSection(outer);
        addPremiumReadingStrip(outer);
        addDiscoverySection(outer);
        return outer;
    }

    private void addContinueReadingSection(LinearLayout root) {
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(2), dp(18), dp(2), dp(8));
        TextView title = new TextView(this);
        title.setText("Continue reading");
        title.setTextSize(17.5f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(themePrimaryText());
        heading.addView(title, new LinearLayout.LayoutParams(0, dp(38), 1f));
        TextView all = new TextView(this);
        all.setText("View all  ›");
        all.setTextSize(12.5f);
        all.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        all.setTextColor(themeAccent());
        all.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        all.setOnClickListener(v -> {
            if (libraryRecycler != null) libraryRecycler.smoothScrollToPosition(1);
        });
        heading.addView(all, new LinearLayout.LayoutParams(dp(84), dp(38)));
        root.addView(heading);

        File[] books = libraryDir.listFiles(file -> file.isFile() && isBook(file.getName()));
        if (books == null) books = new File[0];
        java.util.Arrays.sort(books, (a, b) -> {
            long ao = openedTime(a), bo = openedTime(b);
            if (ao != bo) return Long.compare(bo, ao);
            return Long.compare(addedTime(b), addedTime(a));
        });
        java.util.List<File> preferred = new java.util.ArrayList<>();
        for (File f : books) {
            int p = prefs.getInt("percent_" + f.getName(), 0);
            if (p > 0 && p < 100) preferred.add(f);
        }
        if (preferred.isEmpty()) {
            for (File f : books) preferred.add(f);
        }

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(0, 0, dp(12), dp(2));
        scroller.addView(strip, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (preferred.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Add an EPUB or PDF to start your library");
            empty.setTextSize(13);
            empty.setTextColor(themeSecondaryText());
            empty.setGravity(Gravity.CENTER_VERTICAL);
            empty.setPadding(dp(18), 0, dp(18), 0);
            empty.setBackground(roundRect(themeCardSurface(), dp(20), dp(1), themeStroke()));
            empty.setOnClickListener(v -> chooseBook());
            strip.addView(empty, new LinearLayout.LayoutParams(dp(280), dp(100)));
        } else {
            int max = Math.min(preferred.size(), 8);
            int screen = getResources().getDisplayMetrics().widthPixels;
            int featuredWidth = Math.max(dp(258), Math.min(dp(326), screen - dp(92)));
            for (int i = 0; i < max; i++) {
                boolean featured = i == 0;
                View card = buildContinueBookCard(preferred.get(i), featured);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(featured ? featuredWidth : dp(146), dp(204));
                if (i > 0) lp.leftMargin = dp(10);
                strip.addView(card, lp);
            }
        }
        root.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(208)));
    }

    private View buildContinueBookCard(File file, boolean featured) {
        FrameLayout shell = new FrameLayout(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(featured ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setGravity(featured ? Gravity.CENTER_VERTICAL : Gravity.TOP);
        card.setBackground(roundRect(themeCardSurface(), dp(20), dp(1), themeStroke()));
        card.setElevation(dp(1));
        card.setClickable(true);
        card.setOnClickListener(v -> openBook(file));
        card.setOnLongClickListener(v -> { showBookActions(file, v); return true; });
        shell.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        String initial = cachedLibraryTitle(file);
        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setImageBitmap(placeholderBitmap(initial, 220, 330));
        cover.setBackground(roundRect(Color.rgb(235, 237, 242), dp(13), 0, 0));
        cover.setClipToOutline(true);
        if (featured) {
            card.addView(cover, new LinearLayout.LayoutParams(dp(108), dp(166)));
        } else {
            LinearLayout.LayoutParams coverLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(116));
            card.addView(cover, coverLp);
        }

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(featured ? dp(12) : dp(2), featured ? dp(2) : dp(7), dp(2), 0);
        TextView title = new TextView(this);
        title.setText(initial);
        title.setTextSize(featured ? 16.5f : 12.5f);
        title.setTextColor(themePrimaryText());
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        applyBookTitleTypeface(title);
        copy.addView(title);

        TextView meta = new TextView(this);
        meta.setText(cachedLibraryAuthor(file));
        meta.setTextSize(featured ? 11.5f : 9.5f);
        meta.setTextColor(themeSecondaryText());
        meta.setSingleLine(true);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        meta.setPadding(0, dp(5), 0, 0);
        copy.addView(meta);

        int progress = prefs.getInt("percent_" + file.getName(), 0);
        TextView progressText = new TextView(this);
        progressText.setText(progress + (featured ? "% complete" : "%"));
        progressText.setTextSize(featured ? 11.5f : 10.5f);
        progressText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        progressText.setTextColor(themeAccent());
        progressText.setPadding(0, dp(featured ? 12 : 6), 0, dp(4));
        copy.addView(progressText);

        LinearLayout track = new LinearLayout(this);
        track.setGravity(Gravity.START);
        track.setBackground(roundRect(themeTrackColor(), dp(2), 0, 0));
        View fill = new View(this);
        fill.setBackground(roundRect(themeAccent(), dp(2), 0, 0));
        int trackWidth = featured ? dp(144) : dp(112);
        track.addView(fill, new LinearLayout.LayoutParams(Math.max(dp(2), Math.round(trackWidth * progress / 100f)), dp(3)));
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(featured ? dp(144) : ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        copy.addView(track, trackLp);

        if (featured) {
            TextView continueButton = new TextView(this);
            continueButton.setText(progress > 0 ? "Continue reading  ›" : "Start reading  ›");
            continueButton.setTextSize(11.5f);
            continueButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            continueButton.setTextColor(themeAccent());
            continueButton.setGravity(Gravity.CENTER);
            continueButton.setPadding(dp(10), 0, dp(10), 0);
            continueButton.setBackground(roundRect(themeControlSurface(), dp(17), dp(1), themeStroke()));
            continueButton.setOnClickListener(v -> openBook(file));
            LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
            actionLp.topMargin = dp(10);
            copy.addView(continueButton, actionLp);
            card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        } else {
            card.addView(copy, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        TextView more = new TextView(this);
        more.setText("⋮");
        more.setTextSize(19);
        more.setTextColor(themeSecondaryText());
        more.setGravity(Gravity.CENTER);
        more.setContentDescription("Book actions");
        more.setBackground(roundRect(themeControlSurface(), dp(14), 0, 0));
        more.setOnClickListener(v -> showBookActions(file, v));
        FrameLayout.LayoutParams moreLp = new FrameLayout.LayoutParams(dp(30), dp(34), Gravity.TOP | Gravity.END);
        moreLp.topMargin = dp(5);
        moreLp.rightMargin = dp(5);
        shell.addView(more, moreLp);

        loadBookVisual(file, cover, title, meta);
        return shell;
    }

    private void addPremiumReadingStrip(LinearLayout root) {
        ReadingStatsStore.Snapshot stats = ReadingStatsStore.snapshot(prefs);
        int annotationCount = 0;
        File[] books = libraryDir.listFiles(file -> file.isFile() && isBook(file.getName()));
        if (books != null) for (File f : books) annotationCount += ReaderAnnotationStore.count(prefs, f.getName());

        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(8), dp(4), dp(8), dp(4));
        strip.setBackground(roundRect(themeCardSurface(), dp(20), dp(1), themeStroke()));
        strip.setElevation(dp(1));

        strip.addView(premiumMetric("◷", "Today", formatReadingTime(stats.todayMs), 0, this::showReadingStatsDialog),
                new LinearLayout.LayoutParams(0, dp(58), 1f));
        strip.addView(premiumDivider(), new LinearLayout.LayoutParams(dp(1), dp(34)));
        strip.addView(premiumMetric("♨", "Streak", stats.currentStreak + (stats.currentStreak == 1 ? " day" : " days"), 1, this::showReadingStatsDialog),
                new LinearLayout.LayoutParams(0, dp(58), 1f));
        strip.addView(premiumDivider(), new LinearLayout.LayoutParams(dp(1), dp(34)));
        strip.addView(premiumMetric("✎", "Notes", String.valueOf(annotationCount), 2, this::showNotesHighlightsHub),
                new LinearLayout.LayoutParams(0, dp(58), 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66));
        lp.topMargin = dp(8);
        root.addView(strip, lp);
    }

    private View premiumMetric(String iconText, String label, String value, int slot, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(4), 0, dp(4), 0);
        TextView icon = new TextView(this);
        icon.setText(iconText);
        icon.setTextSize(19);
        icon.setTextColor(themeAccent());
        icon.setGravity(Gravity.CENTER);
        item.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(42)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(9.5f);
        labelView.setTextColor(themeSecondaryText());
        copy.addView(labelView);
        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(12.5f);
        valueView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        valueView.setTextColor(themePrimaryText());
        copy.addView(valueView);
        item.addView(copy, new LinearLayout.LayoutParams(0, dp(42), 1f));
        if (slot == 0) statsSummaryView = valueView;
        else if (slot == 1) streakSummaryView = valueView;
        else notesSummaryView = valueView;
        item.setClickable(true);
        item.setOnClickListener(v -> action.run());
        return item;
    }

    private View premiumDivider() {
        View v = new View(this);
        v.setBackgroundColor(themeStroke());
        return v;
    }

    private View buildBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(4), dp(8), dp(3));
        nav.setBackground(roundRect(themeCardSurface(), dp(24), dp(1), themeStroke()));
        nav.setElevation(dp(9));
        nav.addView(bottomNavItem("⌂", "Home", true, () -> libraryRecycler.smoothScrollToPosition(0)), new LinearLayout.LayoutParams(0, dp(56), 1f));
        nav.addView(bottomNavItem("▥", "Library", false, () -> libraryRecycler.smoothScrollToPosition(1)), new LinearLayout.LayoutParams(0, dp(56), 1f));
        nav.addView(bottomNavItem("✎", "Notes", false, this::showNotesHighlightsHub), new LinearLayout.LayoutParams(0, dp(56), 1f));
        nav.addView(bottomNavItem("◈", "Explore", false, () -> libraryRecycler.smoothScrollToPosition(0)), new LinearLayout.LayoutParams(0, dp(56), 1f));
        nav.addView(bottomNavItem("○", "Profile", false, this::showAccountMenu), new LinearLayout.LayoutParams(0, dp(56), 1f));
        return nav;
    }

    private View bottomNavItem(String iconText, String label, boolean active, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        TextView icon = new TextView(this);
        icon.setText(iconText);
        icon.setTextSize(18);
        icon.setTextColor(active ? themeAccent() : themeSecondaryText());
        icon.setGravity(Gravity.CENTER);
        item.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(28)));
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(9.5f);
        text.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        text.setTextColor(active ? themeAccent() : themeSecondaryText());
        text.setGravity(Gravity.CENTER);
        item.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));
        item.setOnClickListener(v -> action.run());
        return item;
    }
'''
text = replace_block(text, '    private View buildLibraryHeader() {', '    private void updateNotesHubSummary() {', header,
                     'premium home header')

# Premium two-card Explore area keeps Telegram and the website visible without crowding the home screen.
discovery = r'''    private void addDiscoverySection(LinearLayout root) {
        TextView heading = new TextView(this);
        heading.setText("Explore");
        heading.setTextSize(15.5f);
        heading.setTextColor(themePrimaryText());
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setPadding(dp(2), dp(15), dp(2), dp(8));
        root.addView(heading);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View telegram = discoveryCard("telegram", "Telegram Group", "Join discussion",
                Color.rgb(239, 238, 255), "https://t.me/+rUiqzi2mdhNiNGZl");
        View website = discoveryCard("website", "WoW Website", "Visit our site",
                Color.rgb(235, 247, 239), "https://saroatsin.com");
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(72), 1f);
        left.rightMargin = dp(7);
        row.addView(telegram, left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(72), 1f);
        right.leftMargin = dp(7);
        row.addView(website, right);
        root.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(74)));
    }
'''
text = replace_block(text, '    private void addDiscoverySection(LinearLayout root) {', '    private View discoveryCard(', discovery,
                     'premium discovery')

# Update compact strip values without turning them back into long strings.
stats_update = r'''    private void updateReadingStatsSummary() {
        if (prefs == null) return;
        ReadingStatsStore.Snapshot stats = ReadingStatsStore.snapshot(prefs);
        if (statsSummaryView != null) statsSummaryView.setText(formatReadingTime(stats.todayMs));
        if (streakSummaryView != null)
            streakSummaryView.setText(stats.currentStreak + (stats.currentStreak == 1 ? " day" : " days"));
    }
'''
text = replace_block(text, '    private void updateReadingStatsSummary() {', '    private void showReadingStatsDialog() {', stats_update,
                     'stats updater')

notes_summary = r'''    private void updateNotesHubSummary() {
        if (notesSummaryView == null || prefs == null || libraryDir == null) return;
        File[] books = libraryDir.listFiles(file -> file.isFile() && isBook(file.getName()));
        int itemCount = 0;
        if (books != null) for (File book : books) itemCount += ReaderAnnotationStore.count(prefs, book.getName());
        notesSummaryView.setText(String.valueOf(itemCount));
    }
'''
text = replace_block(text, '    private void updateNotesHubSummary() {', '    private void showNotesHighlightsHub() {', notes_summary,
                     'notes updater')

stats_dialog = r'''    private void showReadingStatsDialog() {
        ReadingStatsStore.Snapshot stats = ReadingStatsStore.snapshot(prefs);
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout sheet = premiumSheet("Reading statistics", "Your reading activity", dialog);
        sheet.addView(statSheetRow("◷", "Today", "Time spent reading today", formatReadingTimeLong(stats.todayMs), themeAccent()));
        sheet.addView(statSheetRow("♨", "Current streak", "Keep the reading habit going",
                stats.currentStreak + (stats.currentStreak == 1 ? " day" : " days"), Color.rgb(231, 111, 55)));
        sheet.addView(statSheetRow("♛", "Longest streak", "Your best reading streak so far",
                stats.longestStreak + (stats.longestStreak == 1 ? " day" : " days"), Color.rgb(205, 151, 43)));
        sheet.addView(statSheetRow("□", "Active reading days", "Days with reading activity",
                stats.activeDays + (stats.activeDays == 1 ? " day" : " days"), Color.rgb(54, 157, 85)));
        sheet.addView(statSheetRow("◷", "Total reading time", "All time spent reading",
                formatReadingTimeLong(stats.totalMs), themeAccent()));
        presentBottomSheet(dialog, sheet, 0.82f);
    }

    private View statSheetRow(String iconText, String title, String subtitle, String value, int accent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(5), dp(10), dp(5));
        row.setBackground(roundRect(themeControlSurface(), dp(16), dp(1), themeStroke()));
        TextView icon = new TextView(this);
        icon.setText(iconText);
        icon.setTextSize(19);
        icon.setTextColor(accent);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundRect(themeCardSurface(), dp(14), 0, 0));
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, dp(8), 0);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(13.5f);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setTextColor(themePrimaryText());
        copy.addView(t);
        TextView sub = new TextView(this);
        sub.setText(subtitle);
        sub.setTextSize(9.5f);
        sub.setTextColor(themeSecondaryText());
        copy.addView(sub);
        row.addView(copy, new LinearLayout.LayoutParams(0, dp(46), 1f));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(13f);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setTextColor(accent);
        v.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.addView(v, new LinearLayout.LayoutParams(dp(112), dp(46)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        lp.topMargin = dp(7);
        row.setLayoutParams(lp);
        return row;
    }
'''
text = replace_block(text, '    private void showReadingStatsDialog() {', '    private String formatReadingTime(long milliseconds) {', stats_dialog,
                     'stats bottom sheet')

notes_hub = r'''    private void showNotesHighlightsHub() {
        File[] books = libraryDir.listFiles(file -> file.isFile() && isBook(file.getName()));
        if (books == null) books = new File[0];
        sortLibraryFiles(books);
        java.util.List<File> annotatedBooks = new java.util.ArrayList<>();
        for (File book : books) if (ReaderAnnotationStore.count(prefs, book.getName()) > 0) annotatedBooks.add(book);

        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = premiumSheet("Notes & highlights", "Organized by book", dialog);

        if (annotatedBooks.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No saved notes yet\nSelect text while reading an EPUB and choose Highlight or Note.");
            empty.setTextSize(13);
            empty.setTextColor(themeSecondaryText());
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(22), dp(26), dp(22), dp(26));
            sheet.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(116)));
        } else {
            ScrollView scroll = new ScrollView(this);
            scroll.setVerticalScrollBarEnabled(false);
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            for (File book : annotatedBooks) {
                java.util.List<ReaderAnnotationStore.Annotation> annotations = ReaderAnnotationStore.load(prefs, book.getName());
                int notes = 0;
                for (ReaderAnnotationStore.Annotation a : annotations)
                    if (a.note != null && !a.note.trim().isEmpty()) notes++;
                int highlights = annotations.size();
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(9), dp(7), dp(9), dp(7));
                row.setBackground(roundRect(themeControlSurface(), dp(16), dp(1), themeStroke()));
                row.setClickable(true);

                ImageView cover = new ImageView(this);
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                String initial = cachedLibraryTitle(book);
                cover.setImageBitmap(placeholderBitmap(initial, 120, 170));
                cover.setBackground(roundRect(Color.rgb(235, 237, 242), dp(9), 0, 0));
                cover.setClipToOutline(true);
                row.addView(cover, new LinearLayout.LayoutParams(dp(48), dp(66)));

                LinearLayout copy = new LinearLayout(this);
                copy.setOrientation(LinearLayout.VERTICAL);
                copy.setPadding(dp(10), 0, dp(6), 0);
                TextView title = new TextView(this);
                title.setText(initial);
                title.setTextSize(12.5f);
                title.setMaxLines(2);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                title.setTextColor(themePrimaryText());
                applyBookTitleTypeface(title);
                copy.addView(title);
                TextView counts = new TextView(this);
                counts.setText(notes + (notes == 1 ? " note" : " notes") + "  ·  " + highlights + (highlights == 1 ? " highlight" : " highlights"));
                counts.setTextSize(9.5f);
                counts.setTextColor(themeSecondaryText());
                counts.setPadding(0, dp(4), 0, 0);
                copy.addView(counts);
                row.addView(copy, new LinearLayout.LayoutParams(0, dp(66), 1f));

                TextView arrow = new TextView(this);
                arrow.setText("›");
                arrow.setTextSize(21);
                arrow.setTextColor(themeSecondaryText());
                arrow.setGravity(Gravity.CENTER);
                row.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(54)));
                TextView dummyMeta = new TextView(this);
                loadBookVisual(book, cover, title, dummyMeta);
                row.setOnClickListener(v -> { dialog.dismiss(); openBookAnnotations(book); });
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80));
                rowLp.topMargin = dp(7);
                list.addView(row, rowLp);
            }
            int h = Math.min(dp(414), Math.max(dp(104), annotatedBooks.size() * dp(87)));
            sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        }
        presentBottomSheet(dialog, sheet, 0.84f);
    }

    private LinearLayout premiumSheet(String title, String subtitle, android.app.Dialog dialog) {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(18), dp(10), dp(18), dp(20));
        sheet.setBackground(roundRect(themeCardSurface(), dp(28), dp(1), themeStroke()));
        sheet.setElevation(dp(14));

        TextView handle = new TextView(this);
        handle.setBackground(roundRect(themeSecondaryText(), dp(2), 0, 0));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(54), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(12);
        sheet.addView(handle, handleLp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(21);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextColor(themePrimaryText());
        copy.addView(heading);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(subtitle);
            sub.setTextSize(10.5f);
            sub.setTextColor(themeSecondaryText());
            sub.setPadding(0, dp(2), 0, 0);
            copy.addView(sub);
        }
        head.addView(copy, new LinearLayout.LayoutParams(0, dp(56), 1f));
        TextView close = iconButton("×");
        close.setTextSize(20);
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        sheet.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        return sheet;
    }

    private void presentBottomSheet(android.app.Dialog dialog, View sheet, float maxFraction) {
        dialog.setContentView(sheet);
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.38f);
        window.setGravity(Gravity.BOTTOM);
        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;
        window.setLayout(Math.min(sw, dp(720)), ViewGroup.LayoutParams.WRAP_CONTENT);
        android.view.WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.width = Math.min(sw, dp(720));
        attrs.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        attrs.gravity = Gravity.BOTTOM;
        window.setAttributes(attrs);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            window.setBackgroundBlurRadius(dp(18));
        }
    }
'''
text = replace_block(text, '    private void showNotesHighlightsHub() {', '    private void openBookAnnotations(File file) {', notes_hub,
                     'notes bottom sheet')

book_actions = r'''    private void showBookActions(File file) {
        showBookActions(file, floatingAdd != null ? floatingAdd : libraryRecycler);
    }

    private void showBookActions(File file, View anchor) {
        if (file == null || anchor == null) return;
        final android.widget.PopupWindow popup = new android.widget.PopupWindow(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.setBackground(roundRect(themeCardSurface(), dp(18), dp(1), themeStroke()));
        panel.setElevation(dp(12));
        addCompactPopupAction(panel, popup, "▷", "Continue reading", false, () -> openBook(file));
        addCompactPopupAction(panel, popup, "▥", "Add to shelf", false, () -> showBookShelves(file));
        addCompactPopupAction(panel, popup, "✎", "Notes & highlights", false, () -> openBookAnnotations(file));
        addCompactPopupAction(panel, popup, "Aa", "Reading settings", false, () -> openBookSettings(file));
        addCompactPopupAction(panel, popup, "↗", "Share", false, () -> shareBookReference(file));
        addCompactPopupAction(panel, popup, "⌫", "Delete book", true, () -> confirmDelete(file));
        int width = dp(238);
        popup.setContentView(panel);
        popup.setWidth(width);
        popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setFocusable(true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        if (android.os.Build.VERSION.SDK_INT >= 21) popup.setElevation(dp(12));

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;
        int estimateH = dp(292);
        int x = Math.max(dp(8), Math.min(sw - width - dp(8), loc[0] + anchor.getWidth() - width));
        int y = loc[1] + anchor.getHeight() + dp(3);
        if (y + estimateH > sh - dp(12)) y = Math.max(dp(68), loc[1] - estimateH - dp(3));
        popup.showAtLocation(anchor, Gravity.TOP | Gravity.START, x, y);
    }

    private void addCompactPopupAction(LinearLayout panel, android.widget.PopupWindow popup,
                                       String iconText, String label, boolean danger, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), 0, dp(6), 0);
        row.setClickable(true);
        if (danger) row.setBackground(roundRect(isBlackAppTheme() ? Color.rgb(55, 35, 38) : Color.rgb(255, 247, 247), dp(12), 0, 0));
        TextView icon = new TextView(this);
        icon.setText(iconText);
        icon.setTextSize(16);
        icon.setTextColor(danger ? Color.rgb(211, 65, 65) : themeAccent());
        icon.setGravity(Gravity.CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(42)));
        TextView textView = new TextView(this);
        textView.setText(label);
        textView.setTextSize(12.5f);
        textView.setTextColor(danger ? Color.rgb(211, 65, 65) : themePrimaryText());
        textView.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(textView, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(18);
        arrow.setTextColor(danger ? Color.rgb(211, 65, 65) : themeSecondaryText());
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(42)));
        row.setOnClickListener(v -> { popup.dismiss(); action.run(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        if (panel.getChildCount() > 0) lp.topMargin = dp(2);
        panel.addView(row, lp);
    }

    private void openBookSettings(File file) {
        if (file == null || !file.isFile()) return;
        prefs.edit().putLong("last_opened_" + file.getName(), System.currentTimeMillis()).apply();
        Intent i = new Intent(this, BookReaderActivity.class);
        i.putExtra("path", file.getAbsolutePath());
        i.putExtra("open_reader_settings", true);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void shareBookReference(File file) {
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            String author = cachedLibraryAuthor(file);
            String text = cachedLibraryTitle(file) + (author.isEmpty() ? "" : " — " + author) + "\nShared from WoW Reader";
            send.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(send, "Share book"));
        } catch (Exception e) {
            Toast.makeText(this, "Unable to share", Toast.LENGTH_SHORT).show();
        }
    }
'''
text = replace_block(text, '    private void showBookActions(File file) {', '    private void showBookShelves(File file) {', book_actions,
                     'compact book actions')

# A compact filter sheet exposes status/shelves without reintroducing the large chip row at the top of Home.
filter_sheet = r'''    private void showLibraryFilterSheet() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = premiumSheet("Filter & sort", "Library view and filters", dialog);

        TextView viewTitle = sheetSectionLabel("View");
        sheet.addView(viewTitle);
        LinearLayout viewRow = new LinearLayout(this);
        viewRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView grid = filterChoice("Grid", gridMode);
        TextView list = filterChoice("List", !gridMode);
        grid.setOnClickListener(v -> { if (!gridMode) { gridMode = true; prefs.edit().putBoolean("library_grid", true).apply(); configureLibraryLayout(); refreshLibrary(); dialog.dismiss(); } });
        list.setOnClickListener(v -> { if (gridMode) { gridMode = false; prefs.edit().putBoolean("library_grid", false).apply(); configureLibraryLayout(); refreshLibrary(); dialog.dismiss(); } });
        LinearLayout.LayoutParams half1 = new LinearLayout.LayoutParams(0, dp(40), 1f); half1.rightMargin = dp(5);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(40), 1f); half2.leftMargin = dp(5);
        viewRow.addView(grid, half1); viewRow.addView(list, half2); sheet.addView(viewRow);

        sheet.addView(sheetSectionLabel("Status"));
        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.HORIZONTAL);
        String[] statusNames = {"All", "Reading", "Unread", "Finished"};
        String[] statusValues = {"all", "reading", "unread", "finished"};
        for (int i = 0; i < statusNames.length; i++) {
            final String value = statusValues[i];
            TextView chip = filterChoice(statusNames[i], value.equals(libraryStatusFilter));
            chip.setOnClickListener(v -> { libraryStatusFilter = value; refreshLibrary(); dialog.dismiss(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1f);
            if (i > 0) lp.leftMargin = dp(5);
            status.addView(chip, lp);
        }
        sheet.addView(status);

        sheet.addView(sheetSectionLabel("Shelves"));
        java.util.List<String> shelves = LibraryShelfStore.shelves(prefs);
        HorizontalScrollView shelfScroll = new HorizontalScrollView(this);
        shelfScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout shelfRow = new LinearLayout(this);
        shelfRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView allShelf = filterChoice("All", shelfFilter.isEmpty());
        allShelf.setOnClickListener(v -> { shelfFilter = ""; refreshLibrary(); dialog.dismiss(); });
        shelfRow.addView(allShelf, new LinearLayout.LayoutParams(dp(68), dp(38)));
        for (String shelf : shelves) {
            TextView chip = filterChoice(shelf, shelf.equals(shelfFilter));
            chip.setOnClickListener(v -> { shelfFilter = shelf; refreshLibrary(); dialog.dismiss(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
            lp.leftMargin = dp(6); shelfRow.addView(chip, lp);
        }
        TextView newShelf = filterChoice("＋ New", false);
        newShelf.setOnClickListener(v -> { dialog.dismiss(); showCreateShelfDialog(null); });
        LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(dp(82), dp(38)); newLp.leftMargin = dp(6); shelfRow.addView(newShelf, newLp);
        shelfScroll.addView(shelfRow);
        sheet.addView(shelfScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        presentBottomSheet(dialog, sheet, 0.82f);
    }

    private TextView sheetSectionLabel(String value) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(11f);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setTextColor(themeSecondaryText());
        label.setPadding(dp(1), dp(10), dp(1), dp(6));
        return label;
    }

    private TextView filterChoice(String value, boolean selected) {
        TextView chip = new TextView(this);
        chip.setText(value);
        chip.setTextSize(11.5f);
        chip.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        chip.setTextColor(selected ? themeAccent() : themePrimaryText());
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), 0, dp(12), 0);
        chip.setSingleLine(true);
        chip.setBackground(roundRect(selected ? (isBlackAppTheme() ? Color.rgb(49, 48, 75) : Color.rgb(245, 243, 255)) : themeControlSurface(),
                dp(17), dp(1), selected ? themeAccent() : themeStroke()));
        return chip;
    }
'''
# Insert before libraryFilterChip to keep helper methods close to filter logic.
anchor = '    private TextView libraryFilterChip(String label) {'
if 'private void showLibraryFilterSheet()' not in text:
    pos = text.find(anchor)
    if pos < 0:
        raise SystemExit('library filter insertion anchor missing')
    text = text[:pos] + filter_sheet.rstrip() + '\n\n' + text[pos:]

MAIN.write_text(text, encoding='utf-8')
print('Premium A MainActivity patch applied')
