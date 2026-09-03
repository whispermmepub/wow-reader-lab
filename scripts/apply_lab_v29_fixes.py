from pathlib import Path

MAIN = Path('app/src/main/java/com/whisper/wowreader/MainActivity.java')
READER = Path('app/src/main/java/com/whisper/wowreader/BookReaderActivity.java')
GRADLE = Path('app/build.gradle')


def must_replace(text, old, new, label, count=1):
    found = text.count(old)
    if found < count:
        raise SystemExit(f'{label}: expected at least {count}, found {found}')
    return text.replace(old, new, count)


def replace_between(text, start, end, replacement, label):
    a = text.find(start)
    if a < 0:
        raise SystemExit(f'{label}: missing start')
    b = text.find(end, a)
    if b < 0:
        raise SystemExit(f'{label}: missing end')
    return text[:a] + replacement.rstrip() + '\n\n' + text[b:]


# ---------------- Main / Home / Library ----------------
main = MAIN.read_text(encoding='utf-8')

main = must_replace(
    main,
    '    private volatile boolean metadataWarmupRunning = false;\n',
    '    private volatile boolean metadataWarmupRunning = false;\n    private boolean homeMode = true;\n',
    'home mode field')

main = must_replace(
    main,
    '''    @Override protected void onResume() {
        super.onResume();
        if (libraryRecycler != null) refreshLibrary();
        maybeAutoGoogleSync();
    }
''',
    '''    @Override protected void onResume() {
        super.onResume();
        if (libraryRecycler != null) refreshLibrary();
        maybeAutoGoogleSync();
    }

    @Override public void onBackPressed() {
        if (!homeMode) {
            switchToHome();
            return;
        }
        finish();
    }
''',
    'main back behavior')

new_build_ui = r'''    private void buildUi() {
        // Rebuild only the presentation layer when switching Home/Library.
        // Account/auth/sync state remains in the Activity and SharedPreferences.
        countView = null;
        sortButton = null;
        authorButton = null;
        searchInput = null;
        floatingAdd = null;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(themeBackground());

        libraryRecycler = new RecyclerView(this);
        libraryRecycler.setBackgroundColor(Color.TRANSPARENT);
        libraryRecycler.setClipToPadding(false);
        libraryRecycler.setOverScrollMode(View.OVER_SCROLL_NEVER);
        androidx.recyclerview.widget.DefaultItemAnimator itemAnimator = new androidx.recyclerview.widget.DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setAddDuration(120L);
        itemAnimator.setRemoveDuration(100L);
        itemAnimator.setMoveDuration(150L);
        libraryRecycler.setItemAnimator(itemAnimator);
        libraryRecycler.setItemViewCacheSize(20);
        libraryRecycler.setHasFixedSize(false);
        libraryRecycler.setPadding(0, 0, 0, dp(86));

        libraryAdapter = new LibraryAdapter();
        configureLibraryLayout();
        libraryRecycler.setAdapter(libraryAdapter);
        libraryRecycler.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                   oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            if (width > 0 && width != oldRight - oldLeft)
                libraryRecycler.post(() -> updateLibraryColumnsForWidth(width));
        });
        root.addView(libraryRecycler, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View premiumBottomNav = buildBottomNavigation();
        FrameLayout.LayoutParams bottomNavLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64), Gravity.BOTTOM);
        bottomNavLp.leftMargin = dp(10);
        bottomNavLp.rightMargin = dp(10);
        bottomNavLp.bottomMargin = dp(6);
        root.addView(premiumBottomNav, bottomNavLp);

        setContentView(root);
        refreshLibrary();
    }
'''
main = replace_between(main, '    private void buildUi() {', '    private TextView iconButton(String text) {', new_build_ui, 'buildUi')

new_discovery = r'''    private void addDiscoverySection(LinearLayout root) {
        TextView heading = new TextView(this);
        heading.setText("Explore");
        heading.setTextSize(14);
        heading.setTextColor(themeSecondaryText());
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setPadding(dp(2), dp(12), dp(2), dp(8));
        root.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(dp(1), 0, dp(12), dp(2));
        scroller.addView(strip, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String[][] data = {
                {"telegram", "Telegram", "New books", "https://t.me/TheBookR"},
                {"discussion", "Discussion", "Reader community", "https://t.me/+rUiqzi2mdhNiNGZl"},
                {"website", "Book Website", "saroatsin.com", "https://saroatsin.com"},
                {"review", "Book Reviews", "အညွှန်း & review", "https://whispermmepub.github.io/Review/"}
        };
        int[] colors = {
                Color.rgb(232, 245, 255), Color.rgb(239, 238, 255),
                Color.rgb(235, 247, 239), Color.rgb(255, 241, 232)
        };
        for (int i = 0; i < data.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(154), dp(74));
            if (i > 0) lp.leftMargin = dp(10);
            strip.addView(discoveryCard(data[i][0], data[i][1], data[i][2], colors[i], data[i][3]), lp);
        }
        root.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));
    }
'''
main = replace_between(main, '    private void addDiscoverySection(LinearLayout root) {', '    private View discoveryCard(', new_discovery, 'Explore restoration')

main = must_replace(
    main,
    '''        all.setOnClickListener(v -> {
            if (libraryRecycler != null) libraryRecycler.smoothScrollToPosition(1);
        });''',
    '''        all.setOnClickListener(v -> switchToLibrary());''',
    'continue reading view all')

new_bottom = r'''    private View buildBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(4), dp(8), dp(3));
        nav.setBackground(roundRect(themeCardSurface(), dp(24), dp(1), themeStroke()));
        nav.setElevation(dp(9));
        nav.addView(bottomNavItem("⌂", "Home", homeMode, this::switchToHome), new LinearLayout.LayoutParams(0, dp(56), 1f));
        nav.addView(bottomNavItem("▥", "Library", !homeMode, this::switchToLibrary), new LinearLayout.LayoutParams(0, dp(56), 1f));
        nav.addView(bottomNavItem("✎", "Notes", false, this::showNotesHighlightsHub), new LinearLayout.LayoutParams(0, dp(56), 1f));
        nav.addView(bottomNavItem("◈", "Explore", false, this::showExploreHome), new LinearLayout.LayoutParams(0, dp(56), 1f));
        nav.addView(bottomNavItem("＋", "Add book", false, this::chooseBook), new LinearLayout.LayoutParams(0, dp(56), 1f));
        return nav;
    }

    private void switchToHome() {
        if (homeMode && libraryRecycler != null) {
            libraryRecycler.smoothScrollToPosition(0);
            return;
        }
        homeMode = true;
        buildUi();
    }

    private void switchToLibrary() {
        if (!homeMode && libraryRecycler != null) {
            libraryRecycler.smoothScrollToPosition(0);
            return;
        }
        homeMode = false;
        buildUi();
    }

    private void showExploreHome() {
        if (!homeMode) {
            homeMode = true;
            buildUi();
        }
        if (libraryRecycler != null) libraryRecycler.smoothScrollToPosition(0);
    }
'''
main = replace_between(main, '    private View buildBottomNavigation() {', '    private View bottomNavItem(', new_bottom, 'bottom navigation')

# Add a dedicated full-Library header and a lightweight Home preview header.
new_headers = r'''    private View buildLibraryOnlyHeader() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(14), dp(12), dp(14), dp(5));

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

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Library");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(themePrimaryText());
        titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView home = new TextView(this);
        home.setText("Home  ›");
        home.setTextSize(11.5f);
        home.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        home.setTextColor(themeAccent());
        home.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        home.setOnClickListener(v -> switchToHome());
        titleRow.addView(home, new LinearLayout.LayoutParams(dp(78), dp(42)));
        outer.addView(titleRow);

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
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        searchLp.topMargin = dp(4);
        outer.addView(searchRow, searchLp);
        return outer;
    }

    private View buildHomeBooksSectionHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(10), dp(18), dp(8));
        TextView title = new TextView(this);
        title.setText("Recent library");
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(themePrimaryText());
        row.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView all = new TextView(this);
        all.setText("View library  ›");
        all.setTextSize(11.5f);
        all.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        all.setTextColor(themeAccent());
        all.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        all.setOnClickListener(v -> switchToLibrary());
        row.addView(all, new LinearLayout.LayoutParams(dp(112), dp(42)));
        return row;
    }

'''
marker = '    private View buildLibrarySectionHeader() {'
if marker not in main:
    raise SystemExit('header insertion marker missing')
main = main.replace(marker, new_headers + marker, 1)

new_adapter = r'''    private final class LibraryAdapter extends RecyclerView.Adapter<LibraryHolder> {
        private static final int HOME_HEADER = 0;
        private static final int LIBRARY_SECTION = 1;
        private static final int BOOK = 2;
        private static final int EMPTY = 3;
        private static final int LIBRARY_HEADER = 4;
        private static final int HOME_SECTION = 5;
        private final List<File> items = new ArrayList<>();

        void submit(List<File> next) {
            items.clear();
            if (next != null) items.addAll(next);
            notifyDataSetChanged();
        }

        private int shownBookCount() {
            return homeMode ? Math.min(4, items.size()) : items.size();
        }

        @Override public int getItemCount() {
            int shown = shownBookCount();
            return 2 + (shown == 0 ? 1 : shown);
        }

        @Override public int getItemViewType(int position) {
            if (position == 0) return homeMode ? HOME_HEADER : LIBRARY_HEADER;
            if (position == 1) return homeMode ? HOME_SECTION : LIBRARY_SECTION;
            if (shownBookCount() == 0) return EMPTY;
            return BOOK;
        }

        @Override public LibraryHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == HOME_HEADER) return new LibraryHolder(buildLibraryHeader());
            if (viewType == LIBRARY_HEADER) return new LibraryHolder(buildLibraryOnlyHeader());
            if (viewType == HOME_SECTION) return new LibraryHolder(buildHomeBooksSectionHeader());
            if (viewType == LIBRARY_SECTION) return new LibraryHolder(buildLibrarySectionHeader());
            if (viewType == EMPTY) return new LibraryHolder(buildEmptyState());
            FrameLayout shell = new FrameLayout(MainActivity.this);
            shell.setPadding(dp(7), 0, dp(7), dp(14));
            return new LibraryHolder(shell);
        }

        @Override public void onBindViewHolder(LibraryHolder holder, int position) {
            int type = getItemViewType(position);
            if (type == LIBRARY_SECTION) {
                if (countView != null) countView.setText(items.size() + (items.size() == 1 ? " book" : " books"));
                return;
            }
            if (type == HOME_SECTION || type == HOME_HEADER || type == LIBRARY_HEADER) return;
            if (type == EMPTY) {
                ((TextView) holder.itemView).setText(searchQuery.isEmpty()
                        ? "Your library is ready.\nTap Add book to add an EPUB or PDF."
                        : "No books match your search.");
                return;
            }
            if (type != BOOK) return;
            int index = position - 2;
            if (index < 0 || index >= shownBookCount()) return;
            File file = items.get(index);
            FrameLayout shell = (FrameLayout) holder.itemView;
            shell.removeAllViews();
            View card = gridMode ? createGridCard(file, libraryCardWidth()) : createListCard(file);
            shell.addView(card, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }
'''
main = replace_between(main, '    private final class LibraryAdapter extends RecyclerView.Adapter<LibraryHolder> {', '    private static final class LibraryHolder extends RecyclerView.ViewHolder {', new_adapter, 'adapter home/library split')

MAIN.write_text(main, encoding='utf-8')

# ---------------- Reader fixes ----------------
reader = READER.read_text(encoding='utf-8')

reader = must_replace(
    reader,
    '    private SelectionData currentSelection;\n',
    '    private SelectionData currentSelection;\n    private ActionMode nativeSelectionActionMode;\n    private Runnable hideNativeSelectionRunnable;\n',
    'selection action mode fields')

reader = must_replace(
    reader,
    '        webView.addJavascriptInterface(new ReaderBridge(), "WoW");\n',
    '        webView.addJavascriptInterface(new ReaderBridge(), "WoW");\n        webView.setCustomSelectionActionModeCallback(createSelectionActionModeCallback());\n',
    'custom selection callback install')

new_selection_callback = r'''    private void keepNativeSelectionToolbarHidden() {
        if (nativeSelectionActionMode != null) {
            try { nativeSelectionActionMode.hide(3000L); } catch (Exception ignored) {}
        }
        if (root == null) return;
        if (hideNativeSelectionRunnable != null) root.removeCallbacks(hideNativeSelectionRunnable);
        if (currentSelection == null) {
            hideNativeSelectionRunnable = null;
            return;
        }
        hideNativeSelectionRunnable = () -> {
            hideNativeSelectionRunnable = null;
            if (currentSelection != null) keepNativeSelectionToolbarHidden();
        };
        root.postDelayed(hideNativeSelectionRunnable, 2200L);
    }

    private ActionMode.Callback createSelectionActionModeCallback() {
        return new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                nativeSelectionActionMode = mode;
                menu.clear();
                try { mode.hide(3000L); } catch (Exception ignored) {}
                return true;
            }

            @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                nativeSelectionActionMode = mode;
                menu.clear();
                try { mode.hide(3000L); } catch (Exception ignored) {}
                return true;
            }

            @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                // WoW's compact toolbar owns Highlight / Note / Translate / Copy.
                return true;
            }

            @Override public void onDestroyActionMode(ActionMode mode) {
                if (nativeSelectionActionMode == mode) nativeSelectionActionMode = null;
            }
        };
    }
'''
reader = replace_between(reader, '    private ActionMode.Callback createSelectionActionModeCallback() {', '    private void captureCurrentSelection(int action, ActionMode mode) {', new_selection_callback, 'selection callback')

reader = must_replace(
    reader,
    '''        currentSelection = data;
        showSelectionBar();
    }
''',
    '''        currentSelection = data;
        keepNativeSelectionToolbarHidden();
        showSelectionBar();
    }
''',
    'hide native selection toolbar on selection')

reader = must_replace(
    reader,
    '''    private void hideSelectionBar() {
        if (selectionBar != null) {
''',
    '''    private void hideSelectionBar() {
        if (root != null && hideNativeSelectionRunnable != null) root.removeCallbacks(hideNativeSelectionRunnable);
        hideNativeSelectionRunnable = null;
        if (selectionBar != null) {
''',
    'selection hide cleanup')

reader = must_replace(
    reader,
    '''        webView.animate().cancel();
        webView.setTranslationX(0f);
        webView.setAlpha(firstOpen ? 0f : 1f);
''',
    '''        webView.animate().cancel();
        webView.setScaleX(1f);
        webView.setScaleY(1f);
        webView.setTranslationX(0f);
        // Never expose a newly loaded chapter until fonts + pagination settle.
        // The previous chapter snapshot (or the initial loading screen) stays visible.
        webView.setAlpha(0f);
''',
    'hide unstable chapter frame')

reader = must_replace(
    reader,
    '''                if ("scroll".equals(readingMode)) {
                    webView.postDelayed(() -> {
                        if (generation != chapterLoadGeneration) return;
                        revealStableChapter();
                    }, 110L);
                } else {
''',
    '''                if ("scroll".equals(readingMode)) {
                    // Scroll mode now reveals through WoW.onScrollReady after fonts and two animation frames.
                    // Keep a guarded fallback for unusually broken EPUB scripts.
                    webView.postDelayed(() -> {
                        if (generation == chapterLoadGeneration && chapterLoading && "scroll".equals(readingMode))
                            completePageReady(generation);
                    }, 1600L);
                } else {
''',
    'scroll stable reveal')

# Stronger page measurement stability before revealing a new chapter.
reader = must_replace(reader, 'if(stableHits<1&&attempt<7){setTimeout(run,76);return;}', 'if(stableHits<2&&attempt<9){setTimeout(run,76);return;}', 'page stability hits')
reader = must_replace(reader, 'if(sig2!==sig&&attempt<9){lastSig=sig2;stableHits=0;setTimeout(run,64);return;}', 'if(sig2!==sig&&attempt<11){lastSig=sig2;stableHits=0;setTimeout(run,64);return;}', 'page verification attempts')

# Scroll-mode JS reports ready only after fonts and two RAFs.
old_scroll_finish = '''                    "var finishWowStyle=function(){requestAnimationFrame(function(){requestAnimationFrame(function(){" +
                    (restore >= 0 ? "var h=Math.max(0,document.documentElement.scrollHeight-window.innerHeight);window.scrollTo(0,h*" + ratio + ");" : "") +
                    (styleToken > 0 ? "WoW.onStyleReady(" + styleToken + ");" : "") +
                    "});});};if(document.fonts&&document.fonts.ready)document.fonts.ready.then(finishWowStyle);else finishWowStyle();" +
'''
new_scroll_finish = '''                    "var finishWowStyle=function(){requestAnimationFrame(function(){requestAnimationFrame(function(){" +
                    (restore >= 0 ? "var h=Math.max(0,document.documentElement.scrollHeight-window.innerHeight);window.scrollTo(0,h*" + ratio + ");" : "") +
                    "WoW.onScrollReady(" + styleGeneration + ");" +
                    (styleToken > 0 ? "WoW.onStyleReady(" + styleToken + ");" : "") +
                    "});});};if(document.fonts&&document.fonts.ready)document.fonts.ready.then(finishWowStyle);else finishWowStyle();" +
'''
reader = must_replace(reader, old_scroll_finish, new_scroll_finish, 'scroll ready bridge JS')

# Sepia is a full reading palette, not just a beige background.
reader = must_replace(
    reader,
    '''        String fg = readerTheme == 2 ? "#E8EAED" : "#202124";
        String headingFg = readerTheme == 2 ? "#F1F3F4" : fg;
        String link = readerTheme == 2 ? "#AECBFA" : "#1967D2";
''',
    '''        String fg = readerTheme == 2 ? "#E8EAED" :
                readerTheme == 1 ? "#4A4033" : "#202124";
        String headingFg = readerTheme == 2 ? "#F1F3F4" :
                readerTheme == 1 ? "#3B3128" : fg;
        String link = readerTheme == 2 ? "#AECBFA" :
                readerTheme == 1 ? "#8A5A35" : "#1967D2";
''',
    'sepia palette')

forced_start = '        String darkCss = readerTheme == 2\n'
forced_end = '        String commonCss =\n'
a = reader.find(forced_start)
b = reader.find(forced_end, a)
if a < 0 or b < 0:
    raise SystemExit('forced text css block not found')
forced = r'''        String darkCss = readerTheme == 2
                ? "body,body p,body div,body span,body section,body article,body li,body dd,body dt,body blockquote,body td,body th,body figcaption{color:" + fg + " !important;}" +
                  "h1,h2,h3,h4,h5,h6,strong,b{color:" + headingFg + " !important;}"
                : readerTheme == 1
                ? "body,body p,body div,body span,body section,body article,body li,body dd,body dt,body blockquote,body td,body th,body figcaption{color:" + fg + " !important;}" +
                  "h1,h2,h3,h4,h5,h6,strong,b{color:" + headingFg + " !important;}" +
                  "a{color:" + link + " !important;}"
                : "";

'''
reader = reader[:a] + forced + reader[b:]

reader = must_replace(
    reader,
    '''        } else if (readerTheme == 1) {
            solid = Color.rgb(244, 236, 216);
            fg = Color.rgb(32, 33, 36);
''',
    '''        } else if (readerTheme == 1) {
            solid = Color.rgb(244, 236, 216);
            fg = Color.rgb(74, 64, 51);
''',
    'sepia chrome')

reader = must_replace(
    reader,
    '''        if (webView != null) {
            webView.animate().cancel();
            webView.setAlpha(1f);
''',
    '''        if (webView != null) {
            webView.animate().cancel();
            webView.setScaleX(1f);
            webView.setScaleY(1f);
            webView.setAlpha(1f);
''',
    'stable reveal scale')

# Reader bridge for scroll-layout readiness.
bridge_marker = '''        @JavascriptInterface
        public void onPage(int page, int count, int p) {
'''
bridge_insert = '''        @JavascriptInterface
        public void onScrollReady(int generation) {
            runOnUiThread(() -> {
                if (!"scroll".equals(readingMode) || generation != chapterLoadGeneration) return;
                completePageReady(generation);
            });
        }

'''
if bridge_marker not in reader:
    raise SystemExit('bridge insertion marker missing')
reader = reader.replace(bridge_marker, bridge_insert + bridge_marker, 1)

# If page engine falls back to Scroll, keep the transition covered until Scroll reports ready.
reader = must_replace(
    reader,
    '''                readingMode = "scroll";
                pageTurnLocked = false;
                chapterLoading = false;
                pendingChapterCurlDirection = 0;
                if (pageCurlView != null) pageCurlView.release();
                finishChapterFade();
                prefs.edit().putString("epub_reading_mode", "scroll").apply();
                applyReaderStyle(true);
''',
    '''                readingMode = "scroll";
                pageTurnLocked = false;
                chapterLoading = true;
                pendingChapterCurlDirection = 0;
                if (pageCurlView != null) pageCurlView.release();
                prefs.edit().putString("epub_reading_mode", "scroll").apply();
                applyReaderStyle(true);
''',
    'page engine fallback reveal')

old_reader_back = '''    @Override
    public void onBackPressed() {
        // Back gestures never leave a book accidentally. Use the visible ‹ button to exit.
        if (!controlsVisible) showControls();
        else hideControls();
        enterImmersive();
    }
'''
new_reader_back = '''    @Override
    public void onBackPressed() {
        if (!isPdf) saveEpubState();
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
'''
reader = must_replace(reader, old_reader_back, new_reader_back, 'reader back behavior')

READER.write_text(reader, encoding='utf-8')

# ---------------- Test build identity ----------------
gradle = GRADLE.read_text(encoding='utf-8')
gradle = must_replace(gradle, "        versionCode 28\n        versionName '2.15.0-lab-premium-a'\n", "        versionCode 29\n        versionName '2.15.0-lab-v29'\n", 'v29 identity')
GRADLE.write_text(gradle, encoding='utf-8')

# ---------------- Final source guards ----------------
main_now = MAIN.read_text(encoding='utf-8')
reader_now = READER.read_text(encoding='utf-8')
for needle in [
    'private boolean homeMode = true;',
    '"Add book", false, this::chooseBook',
    'https://t.me/TheBookR',
    'https://t.me/+rUiqzi2mdhNiNGZl',
    'https://saroatsin.com',
    'https://whispermmepub.github.io/Review/',
    'private View buildLibraryOnlyHeader()',
    'return homeMode ? Math.min(4, items.size()) : items.size();'
]:
    if needle not in main_now:
        raise SystemExit('Main guard missing: ' + needle)
for needle in [
    'setCustomSelectionActionModeCallback(createSelectionActionModeCallback())',
    'keepNativeSelectionToolbarHidden()',
    'webView.setAlpha(0f);',
    'public void onScrollReady(int generation)',
    '#4A4033',
    'stableHits<2&&attempt<9',
    'finish();\n        overridePendingTransition'
]:
    if needle not in reader_now:
        raise SystemExit('Reader guard missing: ' + needle)
print('v29 guarded transformation complete')
