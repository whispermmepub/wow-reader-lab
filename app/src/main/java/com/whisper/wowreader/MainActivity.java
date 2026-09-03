package com.whisper.wowreader;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.text.Collator;
import java.util.Locale;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MainActivity extends Activity {
    private static final int REQ_IMPORT = 1001;
    private static final int REQ_BACKUP = 1002;
    private static final int REQ_RESTORE = 1003;
    private File libraryDir;
    private File coverCacheDir;
    private LinearLayout booksContainer;
    private RecyclerView libraryRecycler;
    private LibraryAdapter libraryAdapter;
    private final List<File> visibleBooks = new ArrayList<>();
    private EditText searchInput;
    private TextView floatingAdd;
    private int libraryColumns = 2;
    private TextView countView;
    private TextView viewModeButton;
    private SharedPreferences prefs;
    private boolean gridMode;
    private String searchQuery = "";
    private Typeface pyidaungsuTypeface;
    private TextView sortButton;
    private TextView authorButton;
    private ProfileAvatarView accountButton;
    private TextView themeButton;
    private TextView statsSummaryView;
    private TextView streakSummaryView;
    private TextView notesSummaryView;
    private String appTheme = "white";
    private String sortMode = "added";
    private String authorFilter = "";
    private String libraryStatusFilter = "all";
    private String shelfFilter = "";
    private TextView statusAllChip;
    private TextView statusReadingChip;
    private TextView statusUnreadChip;
    private TextView statusFinishedChip;
    private TextView shelfChip;
    private GoogleDriveSync googleDrive;
    private GoogleAccountAuth googleAccount;
    private GoogleDriveSync.Profile googleProfile;
    private boolean googleSyncBusy = false;
    private long lastAutoSyncAttemptMs = 0L;
    private Runnable googleSyncRetryRunnable;
    private volatile boolean metadataWarmupRunning = false;
    private boolean homeMode = true;
    private final Collator myanmarCollator = Collator.getInstance(new Locale("my", "MM"));
    private final Collator englishCollator = Collator.getInstance(Locale.ENGLISH);

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(247, 248, 251));
        getWindow().setNavigationBarColor(Color.rgb(247, 248, 251));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        libraryDir = new File(getFilesDir(), "library");
        coverCacheDir = new File(getFilesDir(), "cover_cache");
        if (!libraryDir.exists()) libraryDir.mkdirs();
        if (!coverCacheDir.exists()) coverCacheDir.mkdirs();
        prefs = getSharedPreferences("wow_reader", MODE_PRIVATE);
        appTheme = prefs.getString("app_theme", "white");
        if (!"white".equals(appTheme) && !"black".equals(appTheme) && !"navy".equals(appTheme)) appTheme = "white";
        applySystemBarTheme();
        googleAccount = new GoogleAccountAuth(this);
        googleDrive = new GoogleDriveSync(this);
        restoreStoredGoogleProfile();
        gridMode = prefs.getBoolean("library_grid", true);
        sortMode = prefs.getString("library_sort", "added");
        if (!"added".equals(sortMode) && !"opened".equals(sortMode) &&
                !"title_asc".equals(sortMode) && !"title_desc".equals(sortMode))
            sortMode = "added";
        myanmarCollator.setStrength(Collator.PRIMARY);
        englishCollator.setStrength(Collator.PRIMARY);
        try {
            pyidaungsuTypeface = Typeface.createFromAsset(getAssets(), "fonts/pyidaungsu_native.ttf");
        } catch (Exception ignored) {
            pyidaungsuTypeface = null;
        }
        buildUi();
        handleIncomingIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleIncomingIntent(intent); }
    @Override protected void onResume() {
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

    private void buildUi() {
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

    private TextView iconButton(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(20);
        v.setTextColor(themePrimaryText());
        v.setGravity(Gravity.CENTER);
        v.setBackground(roundRect(themeControlSurface(), dp(22), dp(1), themeStroke()));
        v.setClickable(true);
        v.setElevation(dp(1));
        return v;
    }



    private void addComingSoonSection(LinearLayout root) {
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(2), dp(14), dp(2), dp(8));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Coming Soon");
        title.setTextSize(17.5f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(themePrimaryText());
        TextView sub = new TextView(this);
        sub.setText("Latest book notes from 3 WoW sources");
        sub.setTextSize(10.5f);
        sub.setTextColor(themeSecondaryText());
        titles.addView(title);
        titles.addView(sub);
        heading.addView(titles, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView all = new TextView(this);
        all.setText("View all  ›");
        all.setTextSize(12.5f);
        all.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        all.setTextColor(themeAccent());
        all.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        all.setOnClickListener(v -> showExploreHome());
        heading.addView(all, new LinearLayout.LayoutParams(dp(84), dp(48)));
        root.addView(heading);

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(0, 0, dp(12), dp(2));
        scroller.addView(strip, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView loading = new TextView(this);
        loading.setText("Loading latest posts…");
        loading.setTextSize(12.5f);
        loading.setTextColor(themeSecondaryText());
        loading.setGravity(Gravity.CENTER_VERTICAL);
        loading.setPadding(dp(18), 0, dp(18), 0);
        loading.setBackground(roundRect(themeCardSurface(), dp(20), dp(1), themeStroke()));
        strip.addView(loading, new LinearLayout.LayoutParams(dp(260), dp(132)));

        root.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(138)));

        new Thread(() -> {
            List<ComingSoonFeed.Post> posts = ComingSoonFeed.fetchLatest(this, 6, 6);
            runOnUiThread(() -> {
                if (isFinishing() || !homeMode) return;
                strip.removeAllViews();
                if (posts == null || posts.isEmpty()) {
                    TextView empty = new TextView(this);
                    empty.setText("Coming Soon posts are unavailable right now");
                    empty.setTextSize(12.5f);
                    empty.setTextColor(themeSecondaryText());
                    empty.setGravity(Gravity.CENTER_VERTICAL);
                    empty.setPadding(dp(18), 0, dp(18), 0);
                    empty.setBackground(roundRect(themeCardSurface(), dp(20), dp(1), themeStroke()));
                    empty.setOnClickListener(v -> showExploreHome());
                    strip.addView(empty, new LinearLayout.LayoutParams(dp(280), dp(132)));
                    return;
                }
                for (int i = 0; i < posts.size(); i++) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(286), dp(132));
                    if (i > 0) lp.leftMargin = dp(10);
                    strip.addView(buildComingSoonPreviewCard(posts.get(i)), lp);
                }
            });
        }, "wow-home-coming-soon").start();
    }

    private View buildComingSoonPreviewCard(ComingSoonFeed.Post post) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(9), dp(9), dp(11), dp(9));
        card.setBackground(roundRect(themeCardSurface(), dp(20), dp(1), themeStroke()));
        card.setElevation(dp(1));
        card.setClickable(true);
        card.setOnClickListener(v -> openComingSoonPost(post));

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(roundRect(themeControlSurface(), dp(13), 0, 0));
        cover.setClipToOutline(true);
        card.addView(cover, new LinearLayout.LayoutParams(dp(78), dp(114)));
        ComingSoonImageLoader.load(this, post.imageUrl, cover);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(12), dp(1), 0, dp(1));

        TextView source = new TextView(this);
        source.setText(post.source + (post.published.isEmpty() ? "" : " · " + post.published));
        source.setTextSize(9.5f);
        source.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        source.setTextColor(themeAccent());
        source.setSingleLine(true);
        source.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(source);

        TextView title = new TextView(this);
        title.setText(post.title);
        title.setTextSize(14.5f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(themePrimaryText());
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(0, dp(4), 0, 0);
        applyBookTitleTypeface(title);
        copy.addView(title);

        TextView excerpt = new TextView(this);
        excerpt.setText(post.excerpt);
        excerpt.setTextSize(10.5f);
        excerpt.setTextColor(themeSecondaryText());
        excerpt.setMaxLines(3);
        excerpt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        excerpt.setPadding(0, dp(5), 0, 0);
        copy.addView(excerpt);

        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return card;
    }

    private void openComingSoonPost(ComingSoonFeed.Post post) {
        if (post == null) return;
        Intent intent = new Intent(this, ComingSoonDetailActivity.class);
        intent.putExtra("url", post.url);
        intent.putExtra("title", post.title);
        intent.putExtra("source", post.source);
        intent.putExtra("date", post.published);
        intent.putExtra("image", post.imageUrl);
        startActivity(intent);
    }

    private void addDiscoverySection(LinearLayout root) {
        TextView heading = new TextView(this);
        heading.setText("Quick links");
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
                {"review", "Book Reviews", "Coming Soon feed", "wow://coming-soon"}
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

    private View discoveryCard(String kind, String title, String subtitle, int background, String url) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(9), dp(8), dp(9));
        card.setBackground(roundRect(themeDiscoverySurface(background), dp(18), dp(1), themeStroke()));
        card.setClickable(true);
        card.setElevation(dp(1));
        card.setOnClickListener(v -> {
            if ("wow://coming-soon".equals(url)) showExploreHome();
            else openExternal(url);
        });
        card.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN)
                v.animate().scaleX(0.975f).scaleY(0.975f).setDuration(80L).start();
            else if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP || e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL)
                v.animate().scaleX(1f).scaleY(1f).setDuration(120L).start();
            return false;
        });

        ExploreLogoView badge = new ExploreLogoView(this, kind);
        card.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(9), 0, 0, 0);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(12.5f);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setTextColor(themePrimaryText());
        t.setMaxLines(1);
        TextView sub = new TextView(this);
        sub.setText(subtitle);
        sub.setTextSize(9.5f);
        sub.setTextColor(themeSecondaryText());
        sub.setMaxLines(1);
        copy.addView(t);
        copy.addView(sub);
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return card;
    }


    private void openExternal(String url) {
        if (url == null || url.trim().isEmpty()) return;

        Uri parsed = Uri.parse(url.trim());
        String host = parsed.getHost();
        if (host != null && (host.equalsIgnoreCase("t.me") || host.equalsIgnoreCase("telegram.me"))) {
            if (openTelegramDeepLink(parsed)) return;
            String alternate = telegramWebFallback(parsed);
            if (alternate != null && openViewIntent(alternate)) return;
        }

        if (!openViewIntent(url))
            Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show();
    }

    private boolean openTelegramDeepLink(Uri webUri) {
        try {
            String path = webUri.getPath();
            if (path == null) return false;
            String clean = path.startsWith("/") ? path.substring(1) : path;
            if (clean.isEmpty()) return false;

            Uri deepLink;
            if (clean.startsWith("+")) {
                String invite = clean.substring(1);
                if (invite.isEmpty()) return false;
                deepLink = Uri.parse("tg://join?invite=" + Uri.encode(invite));
            } else if (clean.startsWith("joinchat/")) {
                String invite = clean.substring("joinchat/".length());
                if (invite.isEmpty()) return false;
                deepLink = Uri.parse("tg://join?invite=" + Uri.encode(invite));
            } else {
                int slash = clean.indexOf('/');
                String username = slash >= 0 ? clean.substring(0, slash) : clean;
                if (username.isEmpty()) return false;
                deepLink = Uri.parse("tg://resolve?domain=" + Uri.encode(username));
            }

            startActivity(new Intent(Intent.ACTION_VIEW, deepLink));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String telegramWebFallback(Uri original) {
        String path = original.getPath();
        if (path == null || path.isEmpty()) return null;
        String clean = path.startsWith("/") ? path.substring(1) : path;
        if (clean.startsWith("+"))
            return "https://telegram.me/joinchat/" + clean.substring(1);
        return "https://telegram.me/" + clean;
    }

    private boolean openViewIntent(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void refreshLibrary() {
        File[] all = libraryDir.listFiles(file -> file.isFile() && isBook(file.getName()));
        if (all == null) all = new File[0];
        sortLibraryFiles(all);

        visibleBooks.clear();
        for (File f : all) {
            String cachedTitle = cachedLibraryTitle(f).toLowerCase(Locale.ROOT);
            String fileTitle = stripExtension(f.getName()).toLowerCase(Locale.ROOT);
            String author = cachedLibraryAuthor(f);
            String authorLower = author.toLowerCase(Locale.ROOT);
            if (!authorFilter.isEmpty() && !authorFilter.equals(author)) continue;
            int progress = prefs.getInt("percent_" + f.getName(), 0);
            if (!matchesLibraryStatus(progress)) continue;
            if (!shelfFilter.isEmpty() && !LibraryShelfStore.contains(prefs, shelfFilter, f.getName())) continue;
            if (searchQuery.isEmpty() || cachedTitle.contains(searchQuery) || fileTitle.contains(searchQuery) || authorLower.contains(searchQuery))
                visibleBooks.add(f);
        }
        if (libraryAdapter != null) libraryAdapter.submit(visibleBooks);
        if (countView != null) {
            String suffix = visibleBooks.size() == 1 ? " book" : " books";
            String filters = libraryFilterDescription();
            countView.setText(visibleBooks.size() + suffix + (filters.isEmpty() ? "" : " · " + filters));
        }
        if (sortButton != null) sortButton.setText(sortButtonLabel());
        if (authorButton != null) authorButton.setText(authorButtonLabel());
        updateLibraryFilterChips();
        updateReadingStatsSummary();
        updateNotesHubSummary();

        warmSortMetadataIfNeeded(all);
    }

    private void sortLibraryFiles(File[] files) {
        if (files == null || files.length < 2) return;
        Arrays.sort(files, (a, b) -> {
            if ("title_asc".equals(sortMode)) return compareBookTitles(a, b);
            if ("title_desc".equals(sortMode)) return -compareBookTitles(a, b);
            if ("opened".equals(sortMode)) {
                int c = Long.compare(openedTime(b), openedTime(a));
                return c != 0 ? c : compareBookTitles(a, b);
            }
            int c = Long.compare(addedTime(b), addedTime(a));
            return c != 0 ? c : compareBookTitles(a, b);
        });
    }

    private long addedTime(File file) {
        return prefs.getLong("added_at_" + file.getName(), file.lastModified());
    }

    private long openedTime(File file) {
        return prefs.getLong("last_opened_" + file.getName(), 0L);
    }

    private boolean isAlphabeticalSort() {
        return "title_asc".equals(sortMode) || "title_desc".equals(sortMode);
    }

    private String cachedLibraryTitle(File file) {
        String fallback = stripExtension(file.getName());
        String value = prefs.getString("library_title_" + file.getName(), fallback);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String cachedLibraryAuthor(File file) {
        String value = prefs.getString("library_author_" + file.getName(), "");
        return value == null ? "" : value.trim();
    }

    private int compareBookTitles(File a, File b) {
        String ta = normalizeSortTitle(cachedLibraryTitle(a));
        String tb = normalizeSortTitle(cachedLibraryTitle(b));
        int ga = titleScriptGroup(ta);
        int gb = titleScriptGroup(tb);
        if (ga != gb) return Integer.compare(ga, gb);
        int c;
        if (ga == 0) c = myanmarCollator.compare(ta, tb);
        else c = englishCollator.compare(ta, tb);
        if (c != 0) return c;
        return ta.compareToIgnoreCase(tb);
    }

    private String normalizeSortTitle(String value) {
        if (value == null) return "";
        String s = value.trim();
        int offset = 0;
        while (offset < s.length()) {
            int cp = s.codePointAt(offset);
            if (Character.isLetterOrDigit(cp) || isMyanmarCodePoint(cp)) break;
            offset += Character.charCount(cp);
        }
        return offset >= s.length() ? s : s.substring(offset);
    }

    private int titleScriptGroup(String value) {
        if (value == null || value.isEmpty()) return 3;
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            if (isMyanmarCodePoint(cp)) return 0;
            if ((cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z')) return 1;
            if (Character.isDigit(cp)) return 2;
            if (Character.isLetter(cp)) return 2;
            i += Character.charCount(cp);
        }
        return 3;
    }

    private boolean isMyanmarCodePoint(int cp) {
        return (cp >= 0x1000 && cp <= 0x109F) ||
                (cp >= 0xA9E0 && cp <= 0xA9FF) ||
                (cp >= 0xAA60 && cp <= 0xAA7F);
    }

    private void warmSortMetadataIfNeeded(File[] files) {
        if (metadataWarmupRunning || files == null || files.length == 0) return;
        boolean missing = false;
        for (File f : files) {
            if (f.getName().toLowerCase(Locale.ROOT).endsWith(".epub") &&
                    (!prefs.contains("library_title_" + f.getName()) ||
                     !prefs.contains("library_author_" + f.getName()))) {
                missing = true;
                break;
            }
        }
        if (!missing) return;
        metadataWarmupRunning = true;
        final File[] snapshot = files.clone();
        new Thread(() -> {
            SharedPreferences.Editor edit = prefs.edit();
            boolean changed = false;
            for (File f : snapshot) {
                if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".epub")) continue;
                if (prefs.contains("library_title_" + f.getName()) && prefs.contains("library_author_" + f.getName())) continue;
                String title = stripExtension(f.getName());
                String author = "";
                try {
                    EpubUtil.Summary summary = EpubUtil.extractSummary(f, coverCacheDir);
                    if (summary.title != null && !summary.title.trim().isEmpty()) title = summary.title.trim();
                    if (summary.author != null && !summary.author.trim().isEmpty()) author = summary.author.trim();
                } catch (Exception ignored) {}
                edit.putString("library_title_" + f.getName(), title);
                edit.putString("library_author_" + f.getName(), author);
                changed = true;
            }
            edit.apply();
            final boolean shouldRefresh = changed;
            runOnUiThread(() -> {
                metadataWarmupRunning = false;
                if (shouldRefresh) refreshLibrary();
            });
        }, "wow-library-metadata").start();
    }

    private void addGrid(List<File> files) {
        // Retained for binary/source compatibility. The v2.6 library uses RecyclerView.
        if (libraryAdapter != null) libraryAdapter.submit(files);
    }


    private View createGridCard(File file,int cellWidth) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(7), dp(7), dp(7), dp(9));
        card.setBackground(roundRect(themeCardSurface(), dp(18), dp(1), themeStroke()));
        card.setElevation(dp(1));
        card.setClickable(true);
        card.setOnClickListener(v -> openBook(file));
        card.setOnLongClickListener(v -> { showBookActions(file, v); return true; });
        card.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN)
                v.animate().scaleX(0.985f).scaleY(0.985f).setDuration(70L).start();
            else if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP || e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL)
                v.animate().scaleX(1f).scaleY(1f).setDuration(120L).start();
            return false;
        });

        int innerWidth = Math.max(dp(96), cellWidth - dp(26));
        int coverHeight = Math.round(innerWidth * 1.47f);
        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        String initial = stripExtension(file.getName());
        cover.setImageBitmap(placeholderBitmap(initial, Math.max(220, innerWidth), Math.max(320, coverHeight)));
        cover.setBackground(roundRect(Color.rgb(235, 237, 242), dp(13), 0, 0));
        cover.setClipToOutline(true);
        card.addView(cover, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, coverHeight));

        TextView title = new TextView(this);
        title.setText(initial);
        title.setTextSize(14.5f);
        title.setTextColor(themePrimaryText());
        applyBookTitleTypeface(title);
        title.setMaxLines(2);
        title.setLineSpacing(0f, 1.05f);
        title.setPadding(dp(2), dp(9), dp(2), 0);
        card.addView(title);

        int progress = prefs.getInt("percent_" + file.getName(), 0);
        TextView meta = new TextView(this);
        meta.setText((file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf") ? "PDF" : "EPUB") + " · " + progress + "%");
        meta.setTextSize(10.5f);
        meta.setTextColor(themeSecondaryText());
        meta.setSingleLine(true);
        meta.setPadding(dp(2), dp(5), dp(2), dp(6));
        card.addView(meta);

        LinearLayout track = new LinearLayout(this);
        track.setGravity(Gravity.START);
        track.setBackground(roundRect(themeTrackColor(), dp(2), 0, 0));
        View fill = new View(this);
        fill.setBackground(roundRect(themeAccent(), dp(2), 0, 0));
        int trackWidth = Math.max(1, innerWidth - dp(2));
        track.addView(fill, new LinearLayout.LayoutParams(Math.max(0, Math.round(trackWidth * progress / 100f)), dp(3)));
        card.addView(track, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        loadBookVisual(file, cover, title, meta);
        return card;
    }


    private View createListCard(File file) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(10), dp(12), dp(10));
        card.setBackground(roundRect(themeCardSurface(), dp(18), dp(1), themeStroke()));
        card.setElevation(dp(1));
        card.setOnClickListener(v -> openBook(file));
        card.setOnLongClickListener(v -> { showBookActions(file, v); return true; });
        card.setOnTouchListener((v, e) -> {
            int action = e.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                v.animate().cancel();
                v.animate().scaleX(0.986f).scaleY(0.986f).setDuration(65L).start();
            } else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                v.animate().cancel();
                v.animate().scaleX(1f).scaleY(1f).setDuration(145L)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            }
            return false;
        });

        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        String initial = stripExtension(file.getName());
        cover.setImageBitmap(placeholderBitmap(initial, 210, 300));
        cover.setBackground(roundRect(Color.rgb(235, 237, 242), dp(12), 0, 0));
        cover.setClipToOutline(true);
        card.addView(cover, new LinearLayout.LayoutParams(dp(76), dp(110)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(14), dp(2), dp(4), dp(2));
        TextView title = new TextView(this);
        title.setText(initial);
        title.setTextSize(16);
        title.setTextColor(themePrimaryText());
        applyBookTitleTypeface(title);
        title.setMaxLines(2);
        text.addView(title);

        int progress = prefs.getInt("percent_" + file.getName(), 0);
        TextView meta = new TextView(this);
        meta.setText((file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf") ? "PDF" : "EPUB") + " · " + progress + "% read");
        meta.setTextSize(12);
        meta.setTextColor(themeSecondaryText());
        meta.setPadding(0, dp(7), 0, 0);
        text.addView(meta);

        TextView action = new TextView(this);
        action.setText(progress > 0 ? "Continue reading  ›" : "Start reading  ›");
        action.setTextSize(12.5f);
        action.setTextColor(themeAccent());
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        action.setPadding(0, dp(10), 0, 0);
        text.addView(action);
        card.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        loadBookVisual(file, cover, title, meta);
        return card;
    }



    private int calculateLibraryColumns(int widthPx) {
        if (!gridMode) return 1;
        float density = Math.max(1f, getResources().getDisplayMetrics().density);
        float widthDp = Math.max(1f, widthPx / density);
        // Keep covers at a comfortable book-like size while using all available space.
        // This naturally produces 2 columns on phones and 3–6 on tablets/foldables/landscape.
        final float sideDp = 28f;
        final float gapDp = 12f;
        final float minCardDp = 154f;
        float usable = Math.max(minCardDp, widthDp - sideDp);
        int columns = (int) Math.floor((usable + gapDp) / (minCardDp + gapDp));
        return Math.max(2, Math.min(6, columns));
    }

    private void configureLibraryLayout() {
        if (libraryRecycler == null) return;
        int width = libraryRecycler.getWidth() > 0
                ? libraryRecycler.getWidth() : getResources().getDisplayMetrics().widthPixels;
        libraryColumns = calculateLibraryColumns(width);

        GridLayoutManager layout = new GridLayoutManager(this, libraryColumns);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                if (position <= 1) return libraryColumns;
                if (visibleBooks.isEmpty() && position == 2) return libraryColumns;
                return 1;
            }
        });
        libraryRecycler.setLayoutManager(layout);
    }

    private void updateLibraryColumnsForWidth(int widthPx) {
        if (libraryRecycler == null || widthPx <= 0) return;
        int wanted = calculateLibraryColumns(widthPx);
        if (wanted == libraryColumns) return;
        libraryColumns = wanted;
        GridLayoutManager layout = new GridLayoutManager(this, libraryColumns);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                if (position <= 1) return libraryColumns;
                if (visibleBooks.isEmpty() && position == 2) return libraryColumns;
                return 1;
            }
        });
        libraryRecycler.setLayoutManager(layout);
        if (libraryAdapter != null) libraryAdapter.notifyDataSetChanged();
    }

    private int libraryCardWidth() {
        int screen = libraryRecycler != null && libraryRecycler.getWidth() > 0
                ? libraryRecycler.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int gap = dp(12);
        int side = dp(14);
        int columns = Math.max(1, libraryColumns);
        return Math.max(dp(118), (screen - side * 2 - gap * (columns - 1)) / columns);
    }

    private View buildLibraryHeader() {
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
        addComingSoonSection(outer);
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
        all.setOnClickListener(v -> switchToLibrary());
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
        startActivity(new Intent(this, ComingSoonActivity.class));
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

    private void updateNotesHubSummary() {
        if (notesSummaryView == null || prefs == null || libraryDir == null) return;
        File[] books = libraryDir.listFiles(file -> file.isFile() && isBook(file.getName()));
        int itemCount = 0;
        if (books != null) for (File book : books) itemCount += ReaderAnnotationStore.count(prefs, book.getName());
        notesSummaryView.setText(String.valueOf(itemCount));
    }

    private void showNotesHighlightsHub() {
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

    private void openBookAnnotations(File file) {
        if (file == null || !file.isFile()) return;
        prefs.edit().putLong("last_opened_" + file.getName(), System.currentTimeMillis()).apply();
        Intent i = new Intent(this, BookReaderActivity.class);
        i.putExtra("path", file.getAbsolutePath());
        i.putExtra("open_annotations", true);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void updateReadingStatsSummary() {
        if (prefs == null) return;
        ReadingStatsStore.Snapshot stats = ReadingStatsStore.snapshot(prefs);
        if (statsSummaryView != null) statsSummaryView.setText(formatReadingTime(stats.todayMs));
        if (streakSummaryView != null)
            streakSummaryView.setText(stats.currentStreak + (stats.currentStreak == 1 ? " day" : " days"));
    }

    private void showReadingStatsDialog() {
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

    private String formatReadingTime(long milliseconds) {
        long minutes = Math.max(0L, milliseconds) / 60_000L;
        if (minutes < 60L) return minutes + "m";
        long hours = minutes / 60L;
        long rest = minutes % 60L;
        return rest == 0L ? hours + "h" : hours + "h " + rest + "m";
    }

    private String formatReadingTimeLong(long milliseconds) {
        long minutes = Math.max(0L, milliseconds) / 60_000L;
        if (minutes == 0L) return "Less than a minute";
        if (minutes < 60L) return minutes + (minutes == 1L ? " minute" : " minutes");
        long hours = minutes / 60L;
        long rest = minutes % 60L;
        String result = hours + (hours == 1L ? " hour" : " hours");
        if (rest > 0L) result += " " + rest + (rest == 1L ? " minute" : " minutes");
        return result;
    }

    private void showLibraryFilterSheet() {
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

    private TextView libraryFilterChip(String label) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(11.5f);
        chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(dp(13), 0, dp(13), 0);
        chip.setClickable(true);
        return chip;
    }

    private void addFilterChip(LinearLayout strip, TextView chip) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        lp.rightMargin = dp(7);
        strip.addView(chip, lp);
    }

    private void setLibraryStatusFilter(String value) {
        libraryStatusFilter = value == null ? "all" : value;
        updateLibraryFilterChips();
        refreshLibrary();
    }

    private boolean matchesLibraryStatus(int progress) {
        if ("reading".equals(libraryStatusFilter)) return progress > 0 && progress < 100;
        if ("unread".equals(libraryStatusFilter)) return progress <= 0;
        if ("finished".equals(libraryStatusFilter)) return progress >= 100;
        return true;
    }

    private String libraryFilterDescription() {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (!authorFilter.isEmpty()) parts.add(authorFilter);
        if ("reading".equals(libraryStatusFilter)) parts.add("Reading");
        else if ("unread".equals(libraryStatusFilter)) parts.add("Unread");
        else if ("finished".equals(libraryStatusFilter)) parts.add("Finished");
        if (!shelfFilter.isEmpty()) parts.add(shelfFilter);
        return android.text.TextUtils.join(" · ", parts);
    }

    private void updateLibraryFilterChips() {
        styleLibraryFilterChip(statusAllChip, "all".equals(libraryStatusFilter));
        styleLibraryFilterChip(statusReadingChip, "reading".equals(libraryStatusFilter));
        styleLibraryFilterChip(statusUnreadChip, "unread".equals(libraryStatusFilter));
        styleLibraryFilterChip(statusFinishedChip, "finished".equals(libraryStatusFilter));
        if (shelfChip != null) shelfChip.setText(shelfFilter.isEmpty() ? "Shelves  ▾" : shelfFilter + "  ×");
        styleLibraryFilterChip(shelfChip, !shelfFilter.isEmpty());
    }

    private void styleLibraryFilterChip(TextView chip, boolean active) {
        if (chip == null) return;
        int fill = active ? themeAccent() : themeControlSurface();
        chip.setTextColor(active ? Color.WHITE : themeSecondaryText());
        chip.setBackground(roundRect(fill, dp(17), dp(1), active ? themeAccent() : themeStroke()));
        chip.setElevation(active ? dp(2) : 0);
    }

    private void showShelvesDialog() {
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

    private void showCreateShelfDialog(File bookToAdd) {
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

    private void showBookActions(File file) {
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

    private void showBookShelves(File file) {
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

    private View buildLibraryOnlyHeader() {
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

    private View buildLibrarySectionHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(7), dp(16), dp(9));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(this);
        label.setText("Library");
        label.setTextSize(18);
        label.setTextColor(themePrimaryText());
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        copy.addView(label);

        countView = new TextView(this);
        countView.setTextSize(10.5f);
        countView.setTextColor(themeSecondaryText());
        countView.setPadding(0, dp(1), 0, 0);
        copy.addView(countView);
        row.addView(copy, new LinearLayout.LayoutParams(0, dp(48), 1f));

        sortButton = new TextView(this);
        sortButton.setText(sortButtonLabel());
        sortButton.setTextSize(11.5f);
        sortButton.setTextColor(themeAccent());
        sortButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sortButton.setGravity(Gravity.CENTER);
        sortButton.setPadding(dp(12), 0, dp(12), 0);
        sortButton.setSingleLine(true);
        sortButton.setBackground(roundRect(themeControlSurface(), dp(19), dp(1), themeStroke()));
        sortButton.setElevation(dp(1));
        sortButton.setOnClickListener(v -> showSortDialog());
        sortButton.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN)
                v.animate().scaleX(0.965f).scaleY(0.965f).setDuration(70L).start();
            else if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP || e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL)
                v.animate().scaleX(1f).scaleY(1f).setDuration(110L).start();
            return false;
        });
        authorButton = new TextView(this);
        authorButton.setText(authorButtonLabel());
        authorButton.setTextSize(11.5f);
        authorButton.setTextColor(themeAccent());
        authorButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        authorButton.setGravity(Gravity.CENTER);
        authorButton.setPadding(dp(10), 0, dp(10), 0);
        authorButton.setSingleLine(true);
        authorButton.setMaxWidth(dp(126));
        authorButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
        authorButton.setBackground(roundRect(themeControlSurface(), dp(19), dp(1), themeStroke()));
        authorButton.setOnClickListener(v -> showAuthorsDialog());
        LinearLayout.LayoutParams authorLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        authorLp.rightMargin = dp(7);
        row.addView(authorButton, authorLp);

        row.addView(sortButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
        return row;
    }

    private String authorButtonLabel() {
        return authorFilter.isEmpty() ? "Authors  ▾" : authorFilter + "  ×";
    }

    private void showAuthorsDialog() {
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

    private String sortButtonLabel() {
        if ("opened".equals(sortMode)) return "Recently opened  ▾";
        if ("title_asc".equals(sortMode)) return "က–အ · A–Z  ▾";
        if ("title_desc".equals(sortMode)) return "အ–က · Z–A  ▾";
        return "Recently added  ▾";
    }

    private void showSortDialog() {
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

    private View buildEmptyState() {
        TextView empty = new TextView(this);
        empty.setTextSize(15);
        empty.setTextColor(themeSecondaryText());
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(30), dp(72), dp(30), dp(96));
        return empty;
    }

    private boolean isBlackAppTheme() { return "black".equals(appTheme); }
    private boolean isNavyAppTheme() { return "navy".equals(appTheme); }

    private int themeBackground() {
        if (isBlackAppTheme()) return Color.rgb(12, 13, 16);
        if (isNavyAppTheme()) return Color.rgb(3, 28, 48);
        return Color.rgb(247, 248, 251);
    }

    private int themeCardSurface() {
        if (isBlackAppTheme()) return Color.rgb(27, 29, 34);
        if (isNavyAppTheme()) return Color.rgb(7, 44, 70);
        return Color.WHITE;
    }

    private int themeControlSurface() {
        if (isBlackAppTheme()) return Color.rgb(35, 37, 43);
        if (isNavyAppTheme()) return Color.rgb(10, 51, 79);
        return Color.argb(232, 255, 255, 255);
    }

    private int themeSearchSurface() {
        if (isBlackAppTheme()) return Color.rgb(28, 30, 35);
        if (isNavyAppTheme()) return Color.rgb(6, 42, 67);
        return Color.argb(232, 255, 255, 255);
    }

    private int themePrimaryText() {
        return (isBlackAppTheme() || isNavyAppTheme()) ? Color.rgb(244, 247, 250) : Color.rgb(31, 34, 40);
    }

    private int themeSecondaryText() {
        if (isBlackAppTheme()) return Color.rgb(178, 183, 192);
        if (isNavyAppTheme()) return Color.rgb(165, 196, 213);
        return Color.rgb(105, 110, 122);
    }

    private int themeAccent() {
        if (isBlackAppTheme()) return Color.rgb(151, 166, 255);
        if (isNavyAppTheme()) return Color.rgb(239, 194, 91);
        return Color.rgb(82, 82, 214);
    }

    private int themeStroke() {
        if (isBlackAppTheme()) return Color.rgb(55, 59, 68);
        if (isNavyAppTheme()) return Color.rgb(26, 91, 120);
        return Color.rgb(224, 227, 234);
    }

    private int themeTrackColor() {
        if (isBlackAppTheme()) return Color.rgb(50, 53, 61);
        if (isNavyAppTheme()) return Color.rgb(18, 67, 91);
        return Color.rgb(236, 238, 243);
    }

    private int[] themeHeroColors() {
        if (isBlackAppTheme()) return new int[]{Color.rgb(30, 32, 39), Color.rgb(19, 20, 25)};
        if (isNavyAppTheme()) return new int[]{Color.rgb(4, 45, 73), Color.rgb(2, 29, 51), Color.rgb(4, 52, 74)};
        return new int[]{Color.rgb(239, 243, 255), Color.rgb(255, 247, 242)};
    }

    private int[] themeFabColors() {
        if (isBlackAppTheme()) return new int[]{Color.rgb(104, 91, 226), Color.rgb(63, 79, 170)};
        if (isNavyAppTheme()) return new int[]{Color.rgb(8, 174, 199), Color.rgb(10, 105, 145)};
        return new int[]{Color.rgb(92, 76, 226), Color.rgb(71, 113, 236)};
    }

    private int themeDiscoverySurface(int lightFallback) {
        if (isBlackAppTheme()) return Color.rgb(29, 32, 38);
        if (isNavyAppTheme()) return Color.rgb(7, 49, 77);
        return lightFallback;
    }

    private void applySystemBarTheme() {
        int bg = themeBackground();
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        int flags = 0;
        if (!isBlackAppTheme() && !isNavyAppTheme()) flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    // WOW_UX_REFRESH_V214
    private void showAppThemeDialog() {
        final String[] labels = {"White", "Black", "Navy Premium"};
        final String[] values = {"white", "black", "navy"};
        final String[] icons = {"☀", "☾", "✦"};
        int selected = isBlackAppTheme() ? 1 : (isNavyAppTheme() ? 2 : 0);

        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        int panel = themeCardSurface();
        int text = themePrimaryText();
        int sub = themeSecondaryText();
        int stroke = themeStroke();
        int accent = Color.rgb(111, 78, 202);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(20), dp(18), dp(16));
        card.setBackground(roundRect(panel, dp(28), dp(1), stroke));
        card.setElevation(dp(14));

        TextView title = new TextView(this);
        title.setText("App Theme");
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(text);
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            boolean active = i == selected;
            int rowAccent = i == 2 ? accent : themeAccent();
            int fill;
            if (active) {
                fill = isBlackAppTheme() ? Color.rgb(43, 40, 58)
                        : isNavyAppTheme() ? Color.rgb(18, 48, 75)
                        : Color.rgb(248, 246, 255);
            } else fill = themeControlSurface();

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(15), dp(6), dp(14), dp(6));
            row.setMinimumHeight(dp(74));
            row.setClickable(true);
            row.setBackground(roundRect(fill, dp(20), dp(active ? 2 : 1), active ? rowAccent : stroke));
            row.setElevation(active ? dp(4) : dp(1));

            TextView radio = new TextView(this);
            radio.setText(active ? "◉" : "○");
            radio.setTextSize(active ? 29 : 31);
            radio.setTextColor(active ? rowAccent : sub);
            radio.setGravity(Gravity.CENTER);
            row.addView(radio, new LinearLayout.LayoutParams(dp(54), dp(58)));

            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(18);
            label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            label.setTextColor(text);
            label.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(label, new LinearLayout.LayoutParams(0, dp(58), 1f));

            TextView icon = new TextView(this);
            icon.setText(icons[i]);
            icon.setTextSize(i == 2 ? 25 : 24);
            icon.setTextColor(active ? rowAccent : sub);
            icon.setGravity(Gravity.CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(58)));

            row.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN)
                    v.animate().scaleX(0.985f).scaleY(0.985f).setDuration(60L).start();
                else if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP ||
                        e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(115L).start();
                return false;
            });
            row.setOnClickListener(v -> {
                String chosen = values[which];
                dialog.dismiss();
                if (!chosen.equals(appTheme)) {
                    appTheme = chosen;
                    prefs.edit().putString("app_theme", appTheme).apply();
                    recreate();
                }
            });

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(74));
            rowLp.topMargin = dp(i == 0 ? 10 : 9);
            card.addView(row, rowLp);
        }

        TextView cancel = new TextView(this);
        cancel.setText("CANCEL");
        cancel.setTextSize(14);
        cancel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        cancel.setTextColor(accent);
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        cancelLp.topMargin = dp(8);
        card.addView(cancel, cancelLp);

        dialog.setContentView(card);
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.48f);
            int sw = getResources().getDisplayMetrics().widthPixels;
            window.setLayout(Math.min(sw - dp(28), dp(520)), ViewGroup.LayoutParams.WRAP_CONTENT);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                window.setBackgroundBlurRadius(dp(24));
            }
        }
    }

    private GradientDrawable gradientRoundRect(int[] colors, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setCornerRadius(radius);
        return d;
    }

    private final class LibraryAdapter extends RecyclerView.Adapter<LibraryHolder> {
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

    private static final class LibraryHolder extends RecyclerView.ViewHolder {
        LibraryHolder(View itemView) { super(itemView); }
    }

    private void loadBookVisual(File file,ImageView cover,TextView titleView,TextView metaView){
        new Thread(()->{ String title=stripExtension(file.getName()),author=cachedLibraryAuthor(file); Bitmap bitmap=null; try{ if(file.getName().toLowerCase(Locale.ROOT).endsWith(".epub")){ EpubUtil.Summary s=EpubUtil.extractSummary(file,coverCacheDir); if(s.title!=null&&!s.title.isEmpty()) title=s.title; if(s.author!=null&&!s.author.trim().isEmpty()) author=s.author.trim(); if(s.cover!=null&&s.cover.isFile()) bitmap=BitmapFactory.decodeFile(s.cover.getAbsolutePath()); } else bitmap=renderPdfCover(file); }catch(Exception ignored){}
            prefs.edit().putString("library_title_" + file.getName(), title).putString("library_author_" + file.getName(), author).apply();
            String ft=title,fa=author; Bitmap fb=bitmap; int progress=prefs.getInt("percent_"+file.getName(),0); runOnUiThread(()->{ if(fb!=null) cover.setImageBitmap(fb); titleView.setText(ft); applyBookTitleTypeface(titleView); String type=file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")?"PDF":"EPUB"; metaView.setText(fa.isEmpty()?type+" · "+progress+"%":fa+" · "+progress+"%"); if(!fa.isEmpty()){ if(pyidaungsuTypeface!=null) metaView.setTypeface(pyidaungsuTypeface); metaView.setClickable(true); metaView.setOnClickListener(v->{authorFilter=fa;refreshLibrary();}); } }); }).start();
    }

    private Bitmap renderPdfCover(File file){ ParcelFileDescriptor pfd=null; PdfRenderer renderer=null; PdfRenderer.Page page=null; try{ pfd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY); renderer=new PdfRenderer(pfd); if(renderer.getPageCount()==0)return null; page=renderer.openPage(0); int width=360,height=Math.max(1,Math.round(width*(page.getHeight()/(float)page.getWidth()))); Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888); b.eraseColor(Color.WHITE); page.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); return b; }catch(Exception e){return null;} finally{try{if(page!=null)page.close();}catch(Exception ignored){} try{if(renderer!=null)renderer.close();}catch(Exception ignored){} try{if(pfd!=null)pfd.close();}catch(Exception ignored){}} }

    private Bitmap placeholderBitmap(String title,int width,int height){ Bitmap b=Bitmap.createBitmap(Math.max(1,width),Math.max(1,height),Bitmap.Config.ARGB_8888); Canvas c=new Canvas(b); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(colorForName(title)); c.drawRect(0,0,b.getWidth(),b.getHeight(),p); p.setColor(Color.WHITE); p.setTypeface(Typeface.create(pyidaungsuTypeface != null ? pyidaungsuTypeface : Typeface.DEFAULT,Typeface.BOLD)); p.setTextSize(Math.min(width,height)*.25f); p.setTextAlign(Paint.Align.CENTER); String letter=title==null||title.trim().isEmpty()?"W":title.trim().substring(0,1).toUpperCase(Locale.ROOT); Paint.FontMetrics fm=p.getFontMetrics(); float y=height/2f-(fm.ascent+fm.descent)/2f; c.drawText(letter,width/2f,y,p); return b; }

    private void chooseBook(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/epub+zip","application/pdf"}); startActivityForResult(i,REQ_IMPORT); }
    private void handleIncomingIntent(Intent intent){
        if(intent==null)return;
        Uri data=null;
        String action=intent.getAction();
        if(Intent.ACTION_VIEW.equals(action)) data=intent.getData();
        else if(Intent.ACTION_SEND.equals(action)){
            try{Object stream=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(stream instanceof Uri)data=(Uri)stream;}catch(Exception ignored){}
        }
        if(data!=null){
            intent.setAction(null);
            importBook(data,false);
        }
    }

    private void importBook(Uri uri,boolean openAfter){
        new Thread(()->{
            try{
                String name=queryDisplayName(uri);
                if(name==null||name.trim().isEmpty())name="book_"+System.currentTimeMillis();
                String lower=name.toLowerCase(Locale.ROOT),mime=getContentResolver().getType(uri);
                if(!lower.endsWith(".epub")&&!lower.endsWith(".pdf")){
                    if("application/pdf".equals(mime))name+=".pdf";
                    else if("application/epub+zip".equals(mime))name+=".epub";
                    else throw new Exception("Only EPUB and PDF files are supported");
                }
                File out=uniqueFile(name);
                try(InputStream in=getContentResolver().openInputStream(uri);OutputStream os=new FileOutputStream(out)){
                    if(in==null)throw new Exception("Unable to open file");
                    copy(in,os);
                }
                String displayTitle=stripExtension(out.getName());
                String displayAuthor="";
                if(out.getName().toLowerCase(Locale.ROOT).endsWith(".epub")){
                    try{
                        EpubUtil.Summary summary=EpubUtil.extractSummary(out,coverCacheDir);
                        if(summary.title!=null&&!summary.title.trim().isEmpty())displayTitle=summary.title.trim();
                        if(summary.author!=null&&!summary.author.trim().isEmpty())displayAuthor=summary.author.trim();
                    }catch(Exception ignored){}
                }
                prefs.edit()
                        .putLong("added_at_"+out.getName(),System.currentTimeMillis())
                        .putString("library_title_"+out.getName(),displayTitle)
                        .putString("library_author_"+out.getName(),displayAuthor)
                        .putBoolean("library_owned_"+out.getName(),true)
                        .putLong("sync_updated_ms",System.currentTimeMillis())
                        .apply();
                runOnUiThread(()->{
                    Toast.makeText(this,"Added to Library · local copy saved",Toast.LENGTH_SHORT).show();
                    refreshLibrary();
                    maybeAutoGoogleSync();
                });
            }catch(Exception e){
                runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());
            }
        },"wow-import-book").start();
    }

    private void applyBookTitleTypeface(TextView view){
        if(view==null)return;
        if(pyidaungsuTypeface!=null)view.setTypeface(pyidaungsuTypeface,Typeface.BOLD);
        else view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    }

    private String queryDisplayName(Uri uri){ if("file".equalsIgnoreCase(uri.getScheme()))return new File(uri.getPath()).getName(); Cursor c=null; try{c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null);if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}finally{if(c!=null)c.close();}return null; }
    private File uniqueFile(String originalName){ String safe=originalName.replaceAll("[\\\\/:*?\"<>|]","_"); File f=new File(libraryDir,safe);if(!f.exists())return f;int dot=safe.lastIndexOf('.');String base=dot>0?safe.substring(0,dot):safe,ext=dot>0?safe.substring(dot):"";return new File(libraryDir,base+"_"+System.currentTimeMillis()+ext); }
    private void openBook(File file){prefs.edit().putLong("last_opened_"+file.getName(),System.currentTimeMillis()).apply();Intent i=new Intent(this,BookReaderActivity.class);i.putExtra("path",file.getAbsolutePath());startActivity(i);overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}
    private void confirmDelete(File file) {
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

    private void restoreStoredGoogleProfile(){
        GoogleDriveSync.Profile signedIn=googleAccount==null?null:googleAccount.currentProfile();
        if(signedIn!=null){
            googleProfile=signedIn;
            rememberGoogleProfile(signedIn,false);
            return;
        }
        if(!prefs.getBoolean("google_sync_connected",false)&&
                prefs.getString("google_account_email","").isEmpty())return;
        googleProfile=new GoogleDriveSync.Profile();
        googleProfile.uid=prefs.getString("google_account_uid","");
        googleProfile.name=prefs.getString("google_account_name","Google account");
        googleProfile.email=prefs.getString("google_account_email","");
        googleProfile.picture=prefs.getString("google_account_picture","");
    }

    private void updateAccountButton(){
        if(accountButton==null)return;
        boolean connected=prefs!=null&&prefs.getBoolean("google_sync_connected",false);
        String name=googleProfile==null?prefs.getString("google_account_name",""):googleProfile.name;
        String picture=googleProfile==null?prefs.getString("google_account_picture",""):googleProfile.picture;
        boolean signedIn=(googleAccount!=null&&googleAccount.isSignedIn())||
                (googleProfile!=null&&googleProfile.email!=null&&!googleProfile.email.isEmpty());
        accountButton.setProfile(name,picture,signedIn,connected);
        accountButton.setContentDescription(connected?"Google account connected and cloud sync on":
                signedIn?"Google profile signed in; finish cloud sync setup":"Connect Google account");
    }

    private void showAccountMenu(){
        boolean connected=prefs.getBoolean("google_sync_connected",false);
        boolean signedIn=googleAccount!=null&&googleAccount.isSignedIn();
        if(!signedIn&&!connected){
            showAccountActionDialog(
                    "Account & backup",
                    "Sign in with Google, then allow private Drive app-data access to sync books, notes, highlights and reading progress.",
                    new String[]{"Sign in with Google","Manual folder backup","Manual folder restore"},
                    w->{if(w==0)connectGoogleAccount(true);else openManualCloudPicker(w==1);}
            );
            return;
        }
        String name=prefs.getString("google_account_name","Google account");
        String email=prefs.getString("google_account_email","");
        if(!connected){
            String[] items={"Enable Google Drive sync","Switch Google account","Sign out","Manual folder backup","Manual folder restore"};
            showAccountActionDialog(
                    name,
                    (email.isEmpty()?"":email+"\n")+"Profile sign-in is complete. Allow private Drive app-data access to turn on cloud sync.",
                    items,
                    w->{
                        if(w==0)authorizeGoogleDrive(googleProfile);
                        else if(w==1)switchGoogleAccount();
                        else if(w==2)disconnectGoogleAccount();
                        else openManualCloudPicker(w==3);
                    }
            );
            return;
        }
        boolean auto=prefs.getBoolean("google_sync_enabled",true);
        String[] items={"Sync now","Restore from Google Drive","Auto sync: "+(auto?"On":"Off"),"Switch Google account","Disconnect Google account","Manual folder backup","Manual folder restore"};
        showAccountActionDialog(
                name,
                (email.isEmpty()?"":email+"\n")+"WoW Reader data is stored privately in this account's Google Drive app data.",
                items,
                w->{
                    if(w==0)performGoogleBackup(true);
                    else if(w==1)confirmGoogleRestore();
                    else if(w==2){boolean enabled=!auto;prefs.edit().putBoolean("google_sync_enabled",enabled).apply();if(enabled)maybeAutoGoogleSync();else GoogleAutoSync.cancelPending();Toast.makeText(this,"Auto sync "+(enabled?"on":"off"),Toast.LENGTH_SHORT).show();}
                    else if(w==3)switchGoogleAccount();
                    else if(w==4)disconnectGoogleAccount();
                    else openManualCloudPicker(w==5);
                }
        );
    }

    private interface AccountMenuAction { void onAction(int which); }

    private void showAccountActionDialog(String title,String message,String[] items,AccountMenuAction action){
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        int panel = themeCardSurface();
        int text = themePrimaryText();
        int sub = themeSecondaryText();
        int stroke = themeStroke();
        int surface = themeControlSurface();
        int accent = themeAccent();

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(18));
        card.setBackground(roundRect(panel, dp(26), dp(1), stroke));
        card.setElevation(dp(14));
        scroll.addView(card, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView avatar = new TextView(this);
        String initial = title == null || title.trim().isEmpty() ? "W" : title.trim().substring(0, 1).toUpperCase(Locale.ROOT);
        avatar.setText(initial);
        avatar.setTextSize(20);
        avatar.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        avatar.setTextColor(Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(roundRect(accent, dp(24), 0, Color.TRANSPARENT));
        header.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout headerCopy = new LinearLayout(this);
        headerCopy.setOrientation(LinearLayout.VERTICAL);
        headerCopy.setPadding(dp(12), 0, dp(8), 0);
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(21);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextColor(text);
        headerCopy.addView(heading);
        TextView cloudState = new TextView(this);
        cloudState.setText(prefs.getBoolean("google_sync_connected", false) ? "Google Drive backup connected" : "Account & backup");
        cloudState.setTextSize(11.5f);
        cloudState.setTextColor(sub);
        headerCopy.addView(cloudState);
        header.addView(headerCopy, new LinearLayout.LayoutParams(0, dp(52), 1f));

        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(24);
        close.setTextColor(sub);
        close.setGravity(Gravity.CENTER);
        close.setBackground(roundRect(surface, dp(18), dp(1), stroke));
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        card.addView(header);

        String detailText = message == null ? "" : message;
        String emailText = "";
        int nl = detailText.indexOf('\n');
        if (nl > 0 && detailText.substring(0, nl).contains("@")) {
            emailText = detailText.substring(0, nl).trim();
            detailText = detailText.substring(nl + 1).trim();
        }
        if (!emailText.isEmpty()) {
            TextView email = new TextView(this);
            email.setText(emailText);
            email.setTextSize(13);
            email.setTextColor(accent);
            email.setPadding(dp(2), dp(9), dp(2), 0);
            card.addView(email);
        }

        TextView description = new TextView(this);
        description.setText(detailText);
        description.setTextSize(13.5f);
        description.setTextColor(sub);
        description.setLineSpacing(dp(2), 1.12f);
        description.setPadding(dp(2), dp(7), dp(2), dp(8));
        card.addView(description, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        boolean accountSectionAdded = false;
        boolean localSectionAdded = false;
        for (int i = 0; i < items.length; i++) {
            String label = items[i];
            if (!accountSectionAdded && (label.startsWith("Switch Google") || label.equals("Sign out") ||
                    label.startsWith("Disconnect Google"))) {
                accountSectionAdded = true;
                TextView section = new TextView(this);
                section.setText("ACCOUNT");
                section.setTextSize(10.5f);
                section.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                section.setTextColor(sub);
                section.setPadding(dp(2), dp(14), dp(2), dp(4));
                card.addView(section);
            }
            if (!localSectionAdded && label.startsWith("Manual folder")) {
                localSectionAdded = true;
                TextView section = new TextView(this);
                section.setText("LOCAL BACKUP");
                section.setTextSize(10.5f);
                section.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                section.setTextColor(sub);
                section.setPadding(dp(2), dp(14), dp(2), dp(4));
                card.addView(section);
            }

            final int which = i;
            boolean primary = label.equals("Sync now") || label.equals("Sign in with Google") ||
                    label.equals("Enable Google Drive sync");
            boolean danger = label.startsWith("Disconnect Google") || label.equals("Sign out");

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(4), dp(12), dp(4));
            row.setMinimumHeight(dp(58));
            int rowFill = primary ? (isBlackAppTheme() ? Color.rgb(54, 60, 103)
                    : isNavyAppTheme() ? Color.rgb(8, 79, 105) : Color.rgb(239, 241, 255)) : surface;
            int rowStroke = primary ? accent : stroke;
            if (danger) {
                rowFill = isBlackAppTheme() ? Color.rgb(55, 35, 38) : Color.rgb(255, 246, 246);
                rowStroke = Color.rgb(210, 92, 92);
            }
            row.setBackground(roundRect(rowFill, dp(18), dp(1), rowStroke));
            row.setClickable(true);

            String iconText = "•";
            if (label.startsWith("Sync")) iconText = "↻";
            else if (label.startsWith("Restore from")) iconText = "↺";
            else if (label.startsWith("Auto sync")) iconText = "⟳";
            else if (label.startsWith("Switch")) iconText = "⇄";
            else if (label.startsWith("Disconnect") || label.equals("Sign out")) iconText = "×";
            else if (label.startsWith("Manual folder backup")) iconText = "↑";
            else if (label.startsWith("Manual folder restore")) iconText = "↓";
            else if (label.startsWith("Sign in")) iconText = "G";
            else if (label.startsWith("Enable Google Drive")) iconText = "☁";

            TextView icon = new TextView(this);
            icon.setText(iconText);
            icon.setTextSize(19);
            icon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            icon.setTextColor(danger ? Color.rgb(198, 68, 68) : accent);
            icon.setGravity(Gravity.CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(46)));

            TextView labelView = new TextView(this);
            labelView.setText(label.startsWith("Auto sync:") ? "Auto sync" : label);
            labelView.setTextSize(15);
            labelView.setTypeface(Typeface.DEFAULT, primary ? Typeface.BOLD : Typeface.NORMAL);
            labelView.setTextColor(danger ? Color.rgb(198, 68, 68) : text);
            labelView.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(labelView, new LinearLayout.LayoutParams(0, dp(46), 1f));

            if (label.startsWith("Auto sync:")) {
                boolean on = label.endsWith("On");
                TextView state = new TextView(this);
                state.setText(on ? "ON" : "OFF");
                state.setTextSize(10.5f);
                state.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                state.setTextColor(on ? Color.WHITE : sub);
                state.setGravity(Gravity.CENTER);
                state.setBackground(roundRect(on ? accent : themeTrackColor(), dp(15), dp(1), on ? accent : stroke));
                row.addView(state, new LinearLayout.LayoutParams(dp(52), dp(30)));
            } else {
                TextView arrow = new TextView(this);
                arrow.setText("›");
                arrow.setTextSize(22);
                arrow.setTextColor(sub);
                arrow.setGravity(Gravity.CENTER);
                row.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(44)));
            }

            row.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN)
                    v.animate().scaleX(0.988f).scaleY(0.988f).setDuration(55L).start();
                else if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP ||
                        e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(105L).start();
                return false;
            });
            row.setOnClickListener(v -> {
                dialog.dismiss();
                action.onAction(which);
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
            lp.topMargin = dp(8);
            card.addView(row, lp);
        }

        dialog.setContentView(scroll);
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.44f);
            int sw = getResources().getDisplayMetrics().widthPixels;
            int sh = getResources().getDisplayMetrics().heightPixels;
            window.setLayout(Math.min(sw - dp(24), dp(560)), Math.min((int)(sh * 0.88f), dp(720)));
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                window.setBackgroundBlurRadius(dp(22));
            }
        }
    }


    private void connectGoogleAccount(boolean chooseAccount){
        if(googleSyncBusy)return;
        googleSyncBusy=true;
        if(googleAccount==null)googleAccount=new GoogleAccountAuth(this);
        if(googleDrive==null)googleDrive=new GoogleDriveSync(this);
        googleAccount.signIn(chooseAccount,new GoogleAccountAuth.Callback(){
            @Override public void onReady(GoogleDriveSync.Profile profile){
                rememberGoogleProfile(profile,false);
                authorizeGoogleDrive(profile);
            }
            @Override public void onError(String message){googleSyncBusy=false;Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();}
        });
    }

    private void authorizeGoogleDrive(GoogleDriveSync.Profile identityProfile){
        if(identityProfile==null&&googleAccount!=null)identityProfile=googleAccount.currentProfile();
        if(identityProfile==null){googleSyncBusy=false;connectGoogleAccount(true);return;}
        if(googleDrive==null)googleDrive=new GoogleDriveSync(this);
        googleSyncBusy=true;
        final GoogleDriveSync.Profile signedInProfile=identityProfile;
        googleDrive.authorize(false,new GoogleDriveSync.AuthCallback(){
            @Override public void onReady(GoogleDriveSync.Profile driveProfile){
                signedInProfile.accessToken=driveProfile.accessToken;
                rememberGoogleProfile(signedInProfile,true);
                googleSyncBusy=false;
                File[] local=libraryDir.listFiles(file->file.isFile()&&isBook(file.getName()));
                boolean empty=local==null||local.length==0;
                if(!empty&&prefs.getLong("sync_updated_ms",0L)==0L)
                    prefs.edit().putLong("sync_updated_ms",System.currentTimeMillis()).apply();
                GoogleDriveSync.hasBackup(MainActivity.this,driveProfile.accessToken,found->{
                    if(found&&empty){
                        new AlertDialog.Builder(MainActivity.this).setTitle("Restore your library?")
                                .setMessage("A WoW Reader backup was found in this Google Drive. Restore your books, notes and highlights to this device?")
                                .setNegativeButton("Not now",null).setPositiveButton("Restore",(d,w)->performGoogleRestore()).show();
                    }else{
                        new AlertDialog.Builder(MainActivity.this).setTitle("Google Drive connected")
                                .setMessage("Auto sync is on. WoW Reader will back up changes automatically while keeping every imported book available offline.")
                                .setPositiveButton("OK",null).show();
                        maybeAutoGoogleSync();
                    }
                });
            }
            @Override public void onError(String message){googleSyncBusy=false;Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();}
        });
    }

    private void rememberGoogleProfile(GoogleDriveSync.Profile profile,boolean driveConnected){
        if(profile==null)return;
        googleProfile=profile;
        SharedPreferences.Editor edit=prefs.edit()
                .putString("google_account_uid",profile.uid==null?"":profile.uid)
                .putString("google_account_name",profile.name==null?"Google account":profile.name)
                .putString("google_account_email",profile.email==null?"":profile.email)
                .putString("google_account_picture",profile.picture==null?"":profile.picture);
        if(driveConnected)edit.putBoolean("google_sync_connected",true).putBoolean("google_sync_enabled",true);
        edit.apply();
        updateAccountButton();
    }

    private GoogleDriveSync.Profile resolvedProfile(GoogleDriveSync.Profile driveProfile){
        GoogleDriveSync.Profile profile=googleAccount==null?null:googleAccount.currentProfile();
        if(profile==null)profile=googleProfile;
        if(profile==null)profile=new GoogleDriveSync.Profile();
        if(driveProfile!=null)profile.accessToken=driveProfile.accessToken;
        return profile;
    }

    private File readerFontsDir(){File d=new File(getFilesDir(),"reader_fonts");if(!d.exists())d.mkdirs();return d;}

    private void performGoogleBackup(boolean showToast){
        if(GoogleAutoSync.isBusy()){if(showToast)Toast.makeText(this,"Auto sync is already running",Toast.LENGTH_SHORT).show();return;}
        GoogleAutoSync.cancelPending();
        if(googleSyncBusy){scheduleGoogleSyncRetry(12000L);return;}
        googleSyncBusy=true;
        final long requestedChangeMs=prefs.getLong("sync_updated_ms",0L);
        googleDrive.authorize(false,new GoogleDriveSync.AuthCallback(){
            @Override public void onReady(GoogleDriveSync.Profile driveProfile){
                GoogleDriveSync.Profile profile=resolvedProfile(driveProfile);
                rememberGoogleProfile(profile,true);
                GoogleDriveSync.smartBackup(MainActivity.this,driveProfile.accessToken,libraryDir,readerFontsDir(),prefs,new GoogleDriveSync.SyncCallback(){
                    @Override public void onSuccess(String message){prefs.edit().putLong("google_last_synced_change_ms",requestedChangeMs).apply();googleSyncBusy=false;if(showToast)Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();maybeAutoGoogleSync();}
                    @Override public void onError(String message){googleSyncBusy=false;if(showToast)Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();}
                });
            }
            @Override public void onError(String message){googleSyncBusy=false;if(showToast)Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();}
        });
    }

    private void confirmGoogleRestore(){
        new AlertDialog.Builder(this).setTitle("Restore from Google Drive?")
                .setMessage("Books with the same stored name will be replaced. Notes, highlights, reading progress and reader settings from the backup will be restored.")
                .setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->performGoogleRestore()).show();
    }

    private void performGoogleRestore(){
        if(googleSyncBusy)return;
        googleSyncBusy=true;
        googleDrive.authorize(false,new GoogleDriveSync.AuthCallback(){
            @Override public void onReady(GoogleDriveSync.Profile driveProfile){
                GoogleDriveSync.Profile profile=resolvedProfile(driveProfile);
                rememberGoogleProfile(profile,true);
                GoogleDriveSync.restore(MainActivity.this,driveProfile.accessToken,libraryDir,readerFontsDir(),prefs,new GoogleDriveSync.SyncCallback(){
                    @Override public void onSuccess(String message){googleSyncBusy=false;authorFilter="";refreshLibrary();Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();}
                    @Override public void onError(String message){googleSyncBusy=false;Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();}
                });
            }
            @Override public void onError(String message){googleSyncBusy=false;Toast.makeText(MainActivity.this,message,Toast.LENGTH_LONG).show();}
        });
    }

    private void maybeAutoGoogleSync(){
        GoogleAutoSync.scheduleSoon(this);
    }

    private void scheduleGoogleSyncRetry(long delayMs){
        if(libraryRecycler==null)return;
        if(googleSyncRetryRunnable!=null)libraryRecycler.removeCallbacks(googleSyncRetryRunnable);
        googleSyncRetryRunnable=()->{googleSyncRetryRunnable=null;maybeAutoGoogleSync();};
        libraryRecycler.postDelayed(googleSyncRetryRunnable,Math.max(1500L,delayMs));
    }

    private void disconnectGoogleAccount(){
        GoogleAutoSync.cancelPending();
        GoogleDriveSync.Profile profile=googleProfile;
        Runnable signOut=()->{
            if(googleAccount!=null)googleAccount.signOut(this::clearGoogleAccountState);
            else clearGoogleAccountState();
        };
        if(googleDrive!=null)googleDrive.revoke(profile,signOut);else signOut.run();
    }

    private void switchGoogleAccount(){
        GoogleAutoSync.cancelPending();
        GoogleDriveSync.Profile profile=googleProfile;
        Runnable signOut=()->{
            if(googleAccount!=null)googleAccount.signOut(()->runOnUiThread(()->{
                clearGoogleAccountState(false);
                connectGoogleAccount(true);
            }));
            else runOnUiThread(()->{clearGoogleAccountState(false);connectGoogleAccount(true);});
        };
        if(googleDrive!=null)googleDrive.revoke(profile,signOut);else signOut.run();
    }

    private void clearGoogleAccountState(){clearGoogleAccountState(true);}

    private void clearGoogleAccountState(boolean showToast){
        runOnUiThread(()->{
            googleProfile=null;
            googleSyncBusy=false;
            prefs.edit().remove("google_sync_connected").remove("google_sync_enabled")
                    .remove("google_account_uid").remove("google_account_name")
                    .remove("google_account_email").remove("google_account_picture")
                    .remove("google_last_synced_change_ms").apply();
            updateAccountButton();
            if(showToast)Toast.makeText(this,"Google account disconnected",Toast.LENGTH_SHORT).show();
        });
    }

    private void openManualCloudPicker(boolean backup){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,backup?REQ_BACKUP:REQ_RESTORE);
    }

    private void showCloudMenu(){new AlertDialog.Builder(this).setTitle("Backup & restore").setItems(new String[]{"Backup library","Restore books"},(dialog,which)->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,which==0?REQ_BACKUP:REQ_RESTORE);}).show();}
    @SuppressLint("WrongConstant")
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(googleDrive!=null&&googleDrive.handleActivityResult(requestCode,resultCode,data))return;
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(requestCode==REQ_IMPORT){importBook(uri,false);return;}
        try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}catch(Exception ignored){}
        if(requestCode==REQ_BACKUP)backupLibrary(uri);else if(requestCode==REQ_RESTORE)restoreLibrary(uri);
    }

    private void backupLibrary(Uri treeUri){new Thread(()->{int count=0;try{File[] files=libraryDir.listFiles();if(files!=null)for(File file:files){if(!isBook(file.getName()))continue;Uri target=findChild(treeUri,file.getName());if(target==null){String mime=file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")?"application/pdf":"application/epub+zip";target=DocumentsContract.createDocument(getContentResolver(),treeDocumentUri(treeUri),mime,file.getName());}if(target!=null)try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(target,"wt")){if(out!=null){copy(in,out);count++;}}}int n=count;runOnUiThread(()->Toast.makeText(this,"Backup complete: "+n+" books",Toast.LENGTH_LONG).show());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Backup failed: "+e.getMessage(),Toast.LENGTH_LONG).show());}}).start();}
    private void restoreLibrary(Uri treeUri){new Thread(()->{int count=0;Cursor c=null;try{Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,DocumentsContract.getTreeDocumentId(treeUri));c=getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null);if(c!=null)while(c.moveToNext()){String id=c.getString(0),name=c.getString(1);if(!isBook(name))continue;Uri doc=DocumentsContract.buildDocumentUriUsingTree(treeUri,id);File out=new File(libraryDir,name.replaceAll("[\\\\/:*?\"<>|]","_"));try(InputStream in=getContentResolver().openInputStream(doc);OutputStream os=new FileOutputStream(out)){if(in!=null){copy(in,os);prefs.edit().putLong("added_at_"+out.getName(),System.currentTimeMillis()).apply();count++;}}}int n=count;runOnUiThread(()->{refreshLibrary();Toast.makeText(this,"Restored: "+n+" books",Toast.LENGTH_LONG).show();});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Restore failed: "+e.getMessage(),Toast.LENGTH_LONG).show());}finally{if(c!=null)c.close();}}).start();}
    private Uri findChild(Uri treeUri,String name){Cursor c=null;try{Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,DocumentsContract.getTreeDocumentId(treeUri));c=getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null);if(c!=null)while(c.moveToNext())if(name.equals(c.getString(1)))return DocumentsContract.buildDocumentUriUsingTree(treeUri,c.getString(0));}catch(Exception ignored){}finally{if(c!=null)c.close();}return null;}
    private Uri treeDocumentUri(Uri treeUri){return DocumentsContract.buildDocumentUriUsingTree(treeUri,DocumentsContract.getTreeDocumentId(treeUri));}
    private boolean isBook(String n){String s=n==null?"":n.toLowerCase(Locale.ROOT);return s.endsWith(".epub")||s.endsWith(".pdf");}
    private static void copy(InputStream in,OutputStream out)throws Exception{byte[] b=new byte[64*1024];int n;while((n=in.read(b))>0)out.write(b,0,n);}
    private String stripExtension(String name){int dot=name.lastIndexOf('.');return dot>0?name.substring(0,dot):name;}
    private int colorForName(String name){int[] colors={Color.rgb(96,74,139),Color.rgb(55,102,136),Color.rgb(151,78,74),Color.rgb(76,111,82),Color.rgb(130,89,55)};return colors[Math.abs(name==null?0:name.hashCode())%colors.length];}
    private GradientDrawable roundRect(int color,float radius,int strokeWidth,int strokeColor){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);if(strokeWidth>0)g.setStroke(strokeWidth,strokeColor);return g;}
    private final class ProfileAvatarView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clipPath=new Path();
        private Bitmap photo;
        private String requestedUrl="";
        private String initials="G";
        private boolean signedIn;
        private boolean connected;
        private int requestGeneration;

        ProfileAvatarView(android.content.Context context){
            super(context);
            setClickable(true);
            setFocusable(true);
            setElevation(dp(2));
            border.setStyle(Paint.Style.STROKE);
        }

        void setProfile(String name,String pictureUrl,boolean hasProfile,boolean driveConnected){
            signedIn=hasProfile;
            connected=driveConnected;
            initials=profileInitials(name);
            String next=pictureUrl==null?"":pictureUrl.trim();
            if(next.equals(requestedUrl)){
                invalidate();
                return;
            }
            requestedUrl=next;
            photo=null;
            int generation=++requestGeneration;
            if(next.isEmpty()){
                invalidate();
                return;
            }
            File cached=new File(coverCacheDir,"google_profile_"+Integer.toHexString(next.hashCode())+".png");
            Bitmap local=cached.isFile()?BitmapFactory.decodeFile(cached.getAbsolutePath()):null;
            if(local!=null){
                photo=local;
                invalidate();
                return;
            }
            new Thread(()->loadPhoto(next,cached,generation),"wow-profile-photo").start();
        }

        private void loadPhoto(String url,File cached,int generation){
            HttpURLConnection connection=null;
            try{
                connection=(HttpURLConnection)new URL(url).openConnection();
                connection.setConnectTimeout(12_000);
                connection.setReadTimeout(18_000);
                connection.setUseCaches(true);
                connection.setInstanceFollowRedirects(true);
                if(connection.getResponseCode()<200||connection.getResponseCode()>=300)return;
                Bitmap loaded;
                try(InputStream in=connection.getInputStream()){loaded=BitmapFactory.decodeStream(in);}
                if(loaded==null)return;
                try(OutputStream out=new FileOutputStream(cached)){loaded.compress(Bitmap.CompressFormat.PNG,95,out);}catch(Exception ignored){}
                Bitmap ready=loaded;
                runOnUiThread(()->{
                    if(generation!=requestGeneration||!url.equals(requestedUrl))return;
                    photo=ready;
                    invalidate();
                });
            }catch(Exception ignored){
            }finally{
                if(connection!=null)connection.disconnect();
            }
        }

        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.43f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(signedIn?Color.rgb(79,91,213):Color.argb(235,247,248,255));
            canvas.drawCircle(cx,cy,r,paint);
            if(photo!=null&&!photo.isRecycled()){
                int save=canvas.save();
                clipPath.reset();
                clipPath.addCircle(cx,cy,r,Path.Direction.CW);
                canvas.clipPath(clipPath);
                float diameter=r*2f;
                float scale=Math.max(diameter/photo.getWidth(),diameter/photo.getHeight());
                float drawWidth=photo.getWidth()*scale,drawHeight=photo.getHeight()*scale;
                RectF destination=new RectF(cx-drawWidth/2f,cy-drawHeight/2f,cx+drawWidth/2f,cy+drawHeight/2f);
                canvas.drawBitmap(photo,null,destination,paint);
                canvas.restoreToCount(save);
            }else{
                paint.setColor(signedIn?Color.WHITE:Color.rgb(67,68,190));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
                paint.setTextSize(r*(initials.length()>1?.68f:.82f));
                Paint.FontMetrics metrics=paint.getFontMetrics();
                canvas.drawText(initials,cx,cy-(metrics.ascent+metrics.descent)/2f,paint);
            }
            border.setStrokeWidth(dp(1));
            border.setColor(signedIn?Color.WHITE:Color.argb(90,92,103,160));
            canvas.drawCircle(cx,cy,r,border);
            if(signedIn){
                float dotR=Math.max(dp(4),r*.18f);
                float dotX=cx+r*.70f,dotY=cy+r*.70f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(dotX,dotY,dotR+dp(2),paint);
                paint.setColor(connected?Color.rgb(36,179,104):Color.rgb(245,158,11));
                canvas.drawCircle(dotX,dotY,dotR,paint);
            }
        }

        private String profileInitials(String name){
            String value=name==null?"":name.trim();
            if(value.isEmpty()||"Google account".equalsIgnoreCase(value))return "G";
            String[] words=value.split("\\s+");
            StringBuilder result=new StringBuilder();
            appendFirstCodePoint(result,words[0]);
            if(words.length>1)appendFirstCodePoint(result,words[words.length-1]);
            return result.toString().toUpperCase(Locale.ROOT);
        }

        private void appendFirstCodePoint(StringBuilder target,String value){
            if(value==null||value.isEmpty())return;
            target.appendCodePoint(value.codePointAt(0));
        }
    }

    private final class ExploreLogoView extends View {
        private final String kind;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        ExploreLogoView(android.content.Context context, String kind) {
            super(context);
            this.kind = kind == null ? "website" : kind;
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            float cx = w * .5f, cy = h * .5f, r = Math.min(w, h) * .47f;
            if ("telegram".equals(kind) || "discussion".equals(kind)) drawTelegram(c, cx, cy, r);
            else if ("review".equals(kind)) drawReview(c, cx, cy, r);
            else drawWebsite(c, cx, cy, r);
        }

        private void drawTelegram(Canvas c, float cx, float cy, float r) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(42, 171, 238));
            c.drawCircle(cx, cy, r, p);
            path.reset();
            path.moveTo(cx - r * .57f, cy - r * .03f);
            path.lineTo(cx + r * .61f, cy - r * .49f);
            path.lineTo(cx + r * .28f, cy + r * .58f);
            path.lineTo(cx - r * .08f, cy + r * .27f);
            path.lineTo(cx - r * .31f, cy + r * .43f);
            path.lineTo(cx - r * .22f, cy + r * .13f);
            path.close();
            p.setColor(Color.WHITE);
            c.drawPath(path, p);
            p.setColor(Color.argb(88, 15, 105, 160));
            path.reset();
            path.moveTo(cx - r * .22f, cy + r * .13f);
            path.lineTo(cx + r * .39f, cy - r * .31f);
            path.lineTo(cx - r * .08f, cy + r * .27f);
            path.close();
            c.drawPath(path, p);
            if ("discussion".equals(kind)) {
                p.setColor(Color.WHITE);
                c.drawCircle(cx + r * .48f, cy + r * .47f, r * .24f, p);
                p.setColor(Color.rgb(74, 112, 226));
                c.drawCircle(cx + r * .48f, cy + r * .47f, r * .16f, p);
                p.setColor(Color.WHITE);
                c.drawCircle(cx + r * .43f, cy + r * .45f, r * .025f, p);
                c.drawCircle(cx + r * .50f, cy + r * .45f, r * .025f, p);
                c.drawCircle(cx + r * .57f, cy + r * .45f, r * .025f, p);
            }
        }

        private void drawWebsite(Canvas c, float cx, float cy, float r) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(39, 166, 124));
            c.drawCircle(cx, cy, r, p);
            stroke.setColor(Color.WHITE);
            stroke.setStrokeWidth(Math.max(1.6f, r * .10f));
            c.drawCircle(cx, cy, r * .58f, stroke);
            c.drawLine(cx - r * .55f, cy, cx + r * .55f, cy, stroke);
            c.drawOval(cx - r * .28f, cy - r * .58f, cx + r * .28f, cy + r * .58f, stroke);
        }

        private void drawReview(Canvas c, float cx, float cy, float r) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(239, 133, 72));
            c.drawCircle(cx, cy, r, p);
            p.setColor(Color.WHITE);
            float left = cx - r * .52f, top = cy - r * .45f, right = cx + r * .45f, bottom = cy + r * .49f;
            c.drawRoundRect(left, top, right, bottom, r * .10f, r * .10f, p);
            p.setColor(Color.rgb(239, 133, 72));
            c.drawRect(cx - r * .07f, top + r * .09f, cx + r * .01f, bottom - r * .08f, p);
            stroke.setColor(Color.rgb(239, 133, 72));
            stroke.setStrokeWidth(Math.max(1.4f, r * .075f));
            c.drawLine(left + r * .13f, cy - r * .12f, cx - r * .16f, cy - r * .12f, stroke);
            c.drawLine(cx + r * .10f, cy - r * .12f, right - r * .12f, cy - r * .12f, stroke);
            c.drawLine(left + r * .13f, cy + r * .13f, cx - r * .16f, cy + r * .13f, stroke);
            c.drawLine(cx + r * .10f, cy + r * .13f, right - r * .12f, cy + r * .13f, stroke);
        }
    }

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
