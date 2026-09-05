package com.whisper.wowreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.ActionMode;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Calendar;
import java.util.Enumeration;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class BookReaderActivity extends Activity {
    // WOW_UX_REFRESH_V214
    private File bookFile;
    private SharedPreferences prefs;
    private boolean isPdf;

    private FrameLayout root;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private TextView titleView;
    private TextView positionView;
    private TextView bookmarkButton;
    private TextView contentsButton;
    private TextView appearanceButton;
    private TextView annotationButton;
    private String pendingAnnotationId = null;
    private LinearLayout selectionBar;
    private SeekBar readingSeek;
    private boolean readingSeekDragging = false;
    private View nightLightOverlay;
    private Runnable chromeAutoHideRunnable;
    private SelectionData currentSelection;
    private ActionMode nativeSelectionActionMode;
    private Runnable hideNativeSelectionRunnable;
    private static final int SEL_HIGHLIGHT = 9301;
    private static final int SEL_NOTE = 9302;
    private static final int SEL_TRANSLATE = 9303;
    private static final int SEL_COPY = 9304;
    private static final int REQ_IMPORT_FONT = 9401;
    private boolean controlsVisible = false;
    private FrameLayout readerLoadingOverlay;
    private ImageView readerStyleOverlay;
    private ImageView pageSlideOverlay;
    private Bitmap pageSlideBitmap;
    private Bitmap readerStyleBitmap;
    private boolean readerStyleReflowPending = false;
    private int readerStyleReflowToken = 0;
    private Runnable readerStyleApplyRunnable;

    private WebView webView;
    private ReaderWebView preloadWebView;
    private FrameLayout epubWebContent;
    private View.OnTouchListener readerTouchListener;
    private int preloadedSpine = -1;
    private boolean preloadReady = false;
    private boolean preloadLoading = false;
    private int preloadGeneration = 0;
    private int preferredPreloadDirection = 1;
    private PageCurlView pageCurlView;
    private ImageView chapterTransitionOverlay;
    private Bitmap chapterTransitionBitmap;
    private int pendingChapterCurlDirection = 0;
    private boolean pendingChapterFade = false;
    private int pendingChapterDirection = 0;
    private boolean chapterTransitionCapturePending = false;
    private boolean chapterTransitionLoadDeferred = false;
    private int chapterTransitionCaptureToken = 0;
    private GestureDetector readerTapDetector;
    private VelocityTracker pageVelocityTracker;
    private int pageTouchSlop = 12;
    private float paperDownX;
    private float paperDownY;
    private float paperProgress;
    private float paperTouchY = 0.5f;
    private float paperReleaseVelocityX;
    private boolean paperGestureCandidate;
    private boolean paperGestureActive;
    private boolean paperGestureReady;
    private boolean paperGestureReleased;
    private boolean paperGestureCommit;
    private int paperGestureDirection;
    private int paperOriginalPageZero;
    private int paperTargetPageZero;
    private boolean paperGestureChapterBoundary;
    private final List<File> spine = new ArrayList<>();
    private final List<String> chapterTitles = new ArrayList<>();
    private final List<Integer> tocSpineIndices = new ArrayList<>();
    private final List<String> tocTitles = new ArrayList<>();
    private final List<String> tocFragments = new ArrayList<>();
    private String pendingTocFragment = null;
    private int emptyChapterSkipCount = 0;
    private int currentSpine = 0;
    private int currentProgressPermille = 0;
    private int readerTheme = 0;
    private int fontPercent = 115;
    private String fontChoice = "publisher";
    private int lineSpacing = 170;
    private int marginPercent = 7;
    private int brightnessPercent = -1;
    private String nightLightMode = "off";
    private boolean keepScreenOn = false;
    private boolean lockOrientation = false;
    private boolean volumeChapterKeys = false;
    private String readingMode = "scroll";
    private String textAlignment = "justify";
    private boolean autoSpacingAdjustment = true;
    private String pageAnimation = "none";
    private int currentPageInChapter = 1;
    private int pageCountInChapter = 1;
    private boolean pageTurnLocked = false;
    private boolean tapHitTestPending = false;
    private long lastPageTurnMs = 0L;
    private boolean chapterLoading = false;
    private long lastChapterNavMs = 0L;
    private int chapterLoadGeneration = 0;
    private long readingSessionStartedElapsedMs = 0L;

    // Footnote/endnote navigation is transient reading UI, not a new reading position.
    private volatile boolean footnoteReturnArmed = false;
    private volatile boolean footnoteNavigationActive = false;
    private int footnoteReturnSpine = -1;
    private int footnoteReturnProgressPermille = 0;
    private int footnoteReturnPage = 1;
    private String footnoteReturnSourceId = "";
    private String footnoteReturnSourceUrl = "";
    private long footnoteArmToken = 0L;
    private Dialog footnotePreviewDialog = null;
    private String footnotePreviewHref = "";
    private String footnotePreviewLabel = "";
    private ReaderSearchIndex.Footnote footnotePreviewNote = null;
    private boolean footnoteReturnPending = false;
    // A recognized footnote tap must never also trigger page/chapter navigation.
    private volatile long footnoteTapSuppressUntilMs = 0L;

    // Search remains transient until the user intentionally closes it on a result page.
    private Dialog bookSearchDialog = null;
    private final List<ReaderSearchIndex.Hit> bookSearchResults = new ArrayList<>();
    private String bookSearchQuery = "";
    private boolean searchNavigationActive = false;
    private int searchCurrentIndex = -1;
    private int searchReturnSpine = -1;
    private int searchReturnProgressPermille = 0;
    private int searchReturnPage = 1;
    private String pendingSearchQuery = "";
    private int pendingSearchOccurrence = -1;
    private LinearLayout searchNavigationBar = null;
    private TextView searchNavigationLabel = null;

    private ParcelFileDescriptor pdfDescriptor;
    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page pdfPage;
    private ImageView pdfImage;
    private int currentPdfPage = 0;
    private float pdfScale = 1f;
    private ScaleGestureDetector pdfScaleDetector;
    private GestureDetector pdfGestureDetector;
    private float lastTouchX;
    private float lastTouchY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterImmersive();

        String path = getIntent().getStringExtra("path");
        if (path == null) {
            finish();
            return;
        }

        bookFile = new File(path);
        prefs = getSharedPreferences("wow_reader", MODE_PRIVATE);
        isPdf = bookFile.getName().toLowerCase(Locale.ROOT).endsWith(".pdf");

        readerTheme = prefs.getInt("reader_theme", 0);
        fontPercent = prefs.getInt("epub_font", 115);
        fontChoice = prefs.getString("epub_font_choice", "publisher");
        lineSpacing = prefs.getInt("epub_line_spacing", 170);
        marginPercent = prefs.getInt("epub_margin", 7);
        brightnessPercent = prefs.getInt("reader_brightness", -1);
        nightLightMode = prefs.getString("reader_night_light", "off");
        if (!"off".equals(nightLightMode) && !"auto".equals(nightLightMode) && !"on".equals(nightLightMode)) nightLightMode = "off";
        keepScreenOn = prefs.getBoolean("reader_keep_screen_on", false);
        lockOrientation = prefs.getBoolean("reader_lock_orientation", false);
        volumeChapterKeys = prefs.getBoolean("reader_volume_chapter", false);

        readingMode = prefs.getString("epub_reading_mode", "page");
        if (!"page".equals(readingMode) && !"scroll".equals(readingMode)) readingMode = "page";
        textAlignment = prefs.getString("epub_text_alignment", "justify");
        if (!"justify".equals(textAlignment) && !"left".equals(textAlignment) && !"right".equals(textAlignment))
            textAlignment = "justify";
        autoSpacingAdjustment = prefs.getBoolean("epub_auto_spacing", true);
        pageAnimation = prefs.getString("epub_page_animation", "none");
        if ("paper".equals(pageAnimation)) {
            pageAnimation = "none";
            prefs.edit().putString("epub_page_animation", "none").apply();
        }
        if (!"slide".equals(pageAnimation) && !"none".equals(pageAnimation))
            pageAnimation = "none";
        if (!prefs.getBoolean("reader_v20_defaults_applied", false)) {
            pageAnimation = "none";
            prefs.edit().putString("epub_page_animation", "none").putBoolean("reader_v20_defaults_applied", true).apply();
        }
        if (!prefs.getBoolean("reader_v210_animation_default_applied", false)) {
            pageAnimation = "none";
            prefs.edit().putString("epub_page_animation", "none")
                    .putBoolean("reader_v210_animation_default_applied", true).apply();
        }

        if (!prefs.getBoolean("reader_v19_defaults_applied", false)) {
            fontPercent = 100;
            lineSpacing = 160;
            marginPercent = 5;
            textAlignment = "justify";
            autoSpacingAdjustment = true;
            prefs.edit()
                    .putInt("epub_font", 100)
                    .putInt("epub_line_spacing", 160)
                    .putInt("epub_margin", 5)
                    .putString("epub_text_alignment", "justify")
                    .putBoolean("epub_auto_spacing", true)
                    .putBoolean("reader_v19_defaults_applied", true)
                    .apply();
        }

        if (!isPdf) {
            BookTypographyStore.Values bookStyle = BookTypographyStore.load(
                    prefs, bookFile.getName(), fontPercent, fontChoice, lineSpacing,
                    marginPercent, textAlignment, autoSpacingAdjustment);
            fontPercent = bookStyle.fontPercent;
            fontChoice = bookStyle.fontChoice;
            lineSpacing = bookStyle.lineSpacing;
            marginPercent = bookStyle.marginPercent;
            textAlignment = bookStyle.textAlignment;
            autoSpacingAdjustment = bookStyle.autoSpacing;
        }
        applyWindowPreferences();
        buildReaderUi();
        if (isPdf) openPdf(); else openEpub();
        if (!isPdf && getIntent().getBooleanExtra("open_annotations", false)) {
            root.postDelayed(() -> {
                if (!isFinishing()) showAnnotations();
            }, 700L);
        }
        if (!isPdf && getIntent().getBooleanExtra("open_reader_settings", false)) {
            root.postDelayed(() -> {
                if (!isFinishing()) showReaderSettings();
            }, 760L);
        }
    }

    private void buildReaderUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        if (isPdf) setupPdfView(content); else setupWebView(content);

        if (!isPdf) {
            readerLoadingOverlay = new FrameLayout(this);
            readerLoadingOverlay.setClickable(true);
            ReaderLoadingBackdropView loadingBackdrop = new ReaderLoadingBackdropView(this, readerTheme);
            readerLoadingOverlay.addView(loadingBackdrop, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout loadingCard = new LinearLayout(this);
            loadingCard.setOrientation(LinearLayout.VERTICAL);
            loadingCard.setGravity(Gravity.CENTER_HORIZONTAL);
            loadingCard.setPadding(dp(22), dp(16), dp(22), dp(18));

            FrameLayout logoHalo = new FrameLayout(this);
            GradientDrawable halo = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    readerTheme == 2
                            ? new int[]{Color.rgb(38, 105, 164), Color.rgb(75, 57, 177)}
                            : readerTheme == 1
                            ? new int[]{Color.rgb(222, 190, 127), Color.rgb(181, 137, 88)}
                            : new int[]{Color.rgb(107, 168, 244), Color.rgb(132, 101, 226)});
            halo.setShape(GradientDrawable.OVAL);
            halo.setStroke(dp(2), readerTheme == 2 ? Color.rgb(99, 170, 244)
                    : readerTheme == 1 ? Color.rgb(193, 148, 91) : Color.rgb(115, 133, 224));
            logoHalo.setBackground(halo);
            logoHalo.setPadding(dp(16), dp(16), dp(16), dp(16));
            logoHalo.setElevation(dp(10));

            ImageView loadingLogo = new ImageView(this);
            loadingLogo.setImageResource(R.drawable.wow_logo);
            loadingLogo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            loadingLogo.setAlpha(0.98f);
            logoHalo.addView(loadingLogo, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            loadingCard.addView(logoHalo, new LinearLayout.LayoutParams(dp(138), dp(138)));

            TextView loadingTitle = new TextView(this);
            loadingTitle.setText("Opening book…");
            loadingTitle.setTextSize(23);
            loadingTitle.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            loadingTitle.setTextColor(readerPanelText());
            loadingTitle.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams loadingTitleLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            loadingTitleLp.topMargin = dp(24);
            loadingCard.addView(loadingTitle, loadingTitleLp);

            TextView loadingSub = new TextView(this);
            loadingSub.setText("Preparing your reading page");
            loadingSub.setTextSize(13.5f);
            loadingSub.setTextColor(readerPanelSubText());
            loadingSub.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams loadingSubLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            loadingSubLp.topMargin = dp(8);
            loadingCard.addView(loadingSub, loadingSubLp);

            TextView loadingBook = new TextView(this);
            loadingBook.setText("▱");
            loadingBook.setTextSize(29);
            loadingBook.setTextColor(readerAccent());
            loadingBook.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams loadingBookLp = new LinearLayout.LayoutParams(dp(48), dp(46));
            loadingBookLp.topMargin = dp(18);
            loadingCard.addView(loadingBook, loadingBookLp);

            ReaderLoadingProgressView progress = new ReaderLoadingProgressView(this, readerTheme);
            LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(dp(260), dp(20));
            progressLp.topMargin = dp(2);
            loadingCard.addView(progress, progressLp);

            FrameLayout.LayoutParams loadingCardLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            loadingCardLp.topMargin = -dp(28);
            readerLoadingOverlay.addView(loadingCard, loadingCardLp);
            root.addView(readerLoadingOverlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            loadingCard.setScaleX(0.96f);
            loadingCard.setScaleY(0.96f);
            loadingCard.setAlpha(0f);
            loadingCard.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.35f)).start();
        }

        nightLightOverlay = new View(this);
        nightLightOverlay.setClickable(false);
        nightLightOverlay.setFocusable(false);
        nightLightOverlay.setBackgroundColor(Color.rgb(255, 160, 72));
        nightLightOverlay.setAlpha(0f);
        root.addView(nightLightOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(4), dp(5), dp(4), dp(5));
        topBar.setElevation(dp(5));

        TextView back = iconButton("‹", 30);
        back.setContentDescription("Back to Library");
        back.setOnClickListener(v -> {
            if (!isPdf) saveEpubState();
            finish();
        });
        topBar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(50)));

        titleView = new TextView(this);
        titleView.setText(stripExtension(bookFile.getName()));
        titleView.setTextSize(16);
        titleView.setTextColor(Color.rgb(32, 33, 36));
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, dp(50), 1);
        titleLp.leftMargin = dp(4);
        topBar.addView(titleView, titleLp);

        contentsButton = iconButton("☰", 19);
        contentsButton.setContentDescription("Table of contents");
        contentsButton.setOnClickListener(v -> showContents());
        topBar.addView(contentsButton, new LinearLayout.LayoutParams(dp(46), dp(50)));

        TextView search = iconButton("⌕", 22);
        search.setContentDescription("Search in book");
        search.setOnClickListener(v -> searchInBook());
        topBar.addView(search, new LinearLayout.LayoutParams(dp(46), dp(50)));

        bookmarkButton = iconButton("☆", 23);
        bookmarkButton.setContentDescription("Bookmark");
        bookmarkButton.setOnClickListener(v -> toggleBookmark());
        topBar.addView(bookmarkButton, new LinearLayout.LayoutParams(dp(44), dp(50)));

        annotationButton = iconButton("✎", 18);
        annotationButton.setContentDescription("Notes and highlights");
        annotationButton.setOnClickListener(v -> showAnnotations());
        topBar.addView(annotationButton, new LinearLayout.LayoutParams(dp(42), dp(50)));

        appearanceButton = iconButton("Aa", 15);
        appearanceButton.setContentDescription("Reader settings");
        appearanceButton.setOnClickListener(v -> showReaderSettings());
        topBar.addView(appearanceButton, new LinearLayout.LayoutParams(dp(48), dp(50)));

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58), Gravity.TOP);
        topLp.leftMargin = dp(10);
        topLp.rightMargin = dp(10);
        topLp.topMargin = dp(8);
        root.addView(topBar, topLp);

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(dp(8), dp(4), dp(8), dp(4));
        bottomBar.setElevation(dp(5));

        TextView prev = textButton("‹");
        prev.setTextSize(28);
        prev.setContentDescription(isPdf ? "Previous page" : "Previous chapter");
        prev.setOnClickListener(v -> previous());
        bottomBar.addView(prev, new LinearLayout.LayoutParams(dp(56), dp(50)));

        positionView = new TextView(this);
        positionView.setText("—");
        positionView.setTextSize(13);
        positionView.setTextColor(Color.rgb(95, 99, 104));
        positionView.setGravity(Gravity.CENTER);
        positionView.setSingleLine(true);
        positionView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bottomBar.addView(positionView, new LinearLayout.LayoutParams(0, dp(50), 1));

        TextView next = textButton("›");
        next.setTextSize(28);
        next.setContentDescription(isPdf ? "Next page" : "Next chapter");
        next.setOnClickListener(v -> next());
        bottomBar.addView(next, new LinearLayout.LayoutParams(dp(56), dp(50)));

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54), Gravity.BOTTOM);
        bottomLp.leftMargin = dp(34);
        bottomLp.rightMargin = dp(34);
        bottomLp.bottomMargin = dp(12);
        root.addView(bottomBar, bottomLp);

        readingSeek = new SeekBar(this);
        readingSeek.setMax(1000);
        readingSeek.setProgress(0);
        readingSeek.setPadding(dp(2), 0, dp(2), 0);
        readingSeek.setVisibility(View.GONE);
        readingSeek.setAlpha(0f);
        readingSeek.setContentDescription("Reading progress");
        readingSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || positionView == null) return;
                int percent = Math.max(0, Math.min(100, Math.round(progress / 10f)));
                positionView.setText("" + percent + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                readingSeekDragging = true;
                cancelChromeAutoHide();
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                int target = seekBar.getProgress();
                readingSeekDragging = false;
                seekToOverallProgress(target);
                scheduleChromeAutoHide();
            }
        });
        FrameLayout.LayoutParams seekLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32), Gravity.BOTTOM);
        seekLp.leftMargin = dp(48);
        seekLp.rightMargin = dp(48);
        seekLp.bottomMargin = dp(64);
        root.addView(readingSeek, seekLp);

        selectionBar = new LinearLayout(this);
        selectionBar.setOrientation(LinearLayout.HORIZONTAL);
        selectionBar.setGravity(Gravity.CENTER);
        selectionBar.setPadding(dp(6), dp(3), dp(6), dp(3));
        selectionBar.setBackground(glassPanel(readerPanelBase(), dp(18), readerPanelStroke()));
        selectionBar.setElevation(dp(12));
        selectionBar.addView(selectionActionButton("Highlight", SEL_HIGHLIGHT));
        selectionBar.addView(selectionActionButton("Note", SEL_NOTE));
        selectionBar.addView(selectionActionButton("Translate", SEL_TRANSLATE));
        selectionBar.addView(selectionActionButton("Copy", SEL_COPY));
        selectionBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams selectionLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(50), Gravity.TOP | Gravity.START);
        selectionLp.leftMargin = dp(12);
        selectionLp.topMargin = dp(100);
        root.addView(selectionBar, selectionLp);

        if (isPdf) {
            contentsButton.setVisibility(View.GONE);
            search.setVisibility(View.GONE);
            if (annotationButton != null) annotationButton.setVisibility(View.GONE);
        }

        installReaderSafeAreaHandling();
        setContentView(root);
        updateChromeTheme();
        updateNightLightOverlay();
        updateAnnotationButton();
        hideControls();
        enterImmersive();
    }

    private void hideInitialReaderLoading() {
        if (readerLoadingOverlay == null || readerLoadingOverlay.getVisibility() != View.VISIBLE) return;
        readerLoadingOverlay.animate().cancel();
        readerLoadingOverlay.animate().alpha(0f).setDuration(160L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    if (readerLoadingOverlay != null) {
                        readerLoadingOverlay.setVisibility(View.GONE);
                        readerLoadingOverlay.setAlpha(1f);
                    }
                }).start();
    }

    private TextView iconButton(String text, int size) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(Color.rgb(60, 64, 67));
        v.setGravity(Gravity.CENTER);
        v.setClickable(true);
        v.setBackgroundColor(Color.TRANSPARENT);
        return v;
    }

    private TextView textButton(String text) {
        return iconButton(text, 18);
    }

    private final class ReaderWebView extends WebView {
        ReaderWebView(android.content.Context context) {
            super(context);
        }

        private ActionMode.Callback suppressNativeToolbar(ActionMode.Callback delegate) {
            return new ActionMode.Callback() {
                @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    boolean created = delegate == null || delegate.onCreateActionMode(mode, menu);
                    nativeSelectionActionMode = mode;
                    menu.clear();
                    try { mode.hide(3000L); } catch (Exception ignored) {}
                    return created;
                }

                @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    boolean changed = delegate != null && delegate.onPrepareActionMode(mode, menu);
                    nativeSelectionActionMode = mode;
                    menu.clear();
                    try { mode.hide(3000L); } catch (Exception ignored) {}
                    return changed || true;
                }

                @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    // Native items are intentionally removed; WoW's compact bar owns actions.
                    return true;
                }

                @Override public void onDestroyActionMode(ActionMode mode) {
                    if (delegate != null) delegate.onDestroyActionMode(mode);
                    if (nativeSelectionActionMode == mode) nativeSelectionActionMode = null;
                }
            };
        }

        @Override public ActionMode startActionMode(ActionMode.Callback callback) {
            return super.startActionMode(suppressNativeToolbar(callback));
        }

        @Override public ActionMode startActionMode(ActionMode.Callback callback, int type) {
            return super.startActionMode(suppressNativeToolbar(callback), type);
        }
    }

    private WebViewClient createReaderWebViewClient() {
        return new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (view != webView) {
                    handlePreloadPageFinished(view, url);
                    return;
                }
                final int generation = chapterLoadGeneration;
                applyReaderStyle(true);
                installReaderLinkNavigation();
                webView.postDelayed(() -> {
                    if (generation == chapterLoadGeneration) applySavedAnnotations();
                }, 520L);
                webView.postDelayed(() -> {
                    if (generation == chapterLoadGeneration) applySavedAnnotations();
                }, 1450L);
                webView.postDelayed(() -> {
                    if (generation == chapterLoadGeneration) installSelectionWatcher();
                }, 560L);
                if ("scroll".equals(readingMode)) {
                    // Scroll mode now reveals through WoW.onScrollReady after fonts and two animation frames.
                    // Keep a guarded fallback for unusually broken EPUB scripts.
                    webView.postDelayed(() -> {
                        if (generation == chapterLoadGeneration && chapterLoading && "scroll".equals(readingMode))
                            completePageReady(generation);
                    }, 1600L);
                } else {
                    // Recheck if a device changes its edge-to-edge viewport just after navigation.
                    webView.postDelayed(() -> {
                        if (generation == chapterLoadGeneration && chapterLoading)
                            forceChapterRepaginate(generation);
                    }, 2100L);
                    webView.postDelayed(() -> {
                        if (generation == chapterLoadGeneration && chapterLoading)
                            forceChapterRepaginate(generation);
                    }, 3900L);
                }
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                if (view == webView) onReaderVisitedUrl(url);
            }
        };
    }

    private void installReaderLinkNavigation() {
        if (webView == null) return;
        String js = "(function(){try{" +
                "if(window.__wowReaderLinkNavInstalled)return true;window.__wowReaderLinkNavInstalled=true;" +
                "document.addEventListener('click',function(ev){try{" +
                "var t=ev.target,a=t&&t.closest?t.closest('a[href]'):null;if(!a)return;" +
                "var href=a.getAttribute('href')||'',ep=a.getAttribute('epub:type')||a.getAttribute('type')||'';" +
                "try{ep=ep||a.getAttributeNS('http://www.idpf.org/2007/ops','type')||'';}catch(_e){}" +
                "var role=a.getAttribute('role')||'',rel=a.getAttribute('rel')||'',cls=(typeof a.className==='string'?a.className:'');" +
                "var sid='',n=a;for(var i=0;i<5&&n;i++,n=n.parentElement){if(n.id){sid=n.id;break;}}" +
                "var label=(a.textContent||'').replace(/\s+/g,' ').trim();" +
                "if(WoW.onReaderLinkTap(href,ep,role,rel,cls,sid,label)){ev.preventDefault();ev.stopImmediatePropagation();return false;}" +
                "}catch(_e){}},true);return true;}catch(e){return false;}})()";
        try { webView.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

    private void requestFootnotePreview(String href, String label, String sourceId) {
        if (webView == null || href == null || href.trim().isEmpty() || spine.isEmpty()) return;
        footnotePreviewHref = href.trim();
        footnotePreviewLabel = label == null ? "" : label.trim();
        final int sourceSpine = currentSpine;
        final String previewSourceId = sourceId == null ? "" : sourceId;
        new Thread(() -> {
            ReaderSearchIndex.Footnote note = ReaderSearchIndex.resolveFootnote(spine, sourceSpine, footnotePreviewHref, previewSourceId);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                footnotePreviewNote = note;
                showFootnotePreview(note, footnotePreviewLabel);
            });
        }, "wow-footnote-preview").start();
    }

    private String cleanFootnoteDisplayText(String raw) {
        if (raw == null) return "";
        String text = raw.replaceAll("\\s+", " ").trim();
        text = text.replaceFirst("(?i)^unknown\\s*", "");
        text = text.replaceFirst("^\\[\\s*[←↩↵]?\\s*-?\\s*\\d+\\s*\\]\\s*", "");
        text = text.replaceFirst("^[←↩↵]\\s*-?\\s*\\d+\\s*", "");
        return text.trim();
    }

    private void showFootnotePreview(ReaderSearchIndex.Footnote note, String label) {
        if (isFinishing() || note == null) return;
        if (footnotePreviewDialog != null) {
            try { footnotePreviewDialog.dismiss(); } catch (Exception ignored) {}
            footnotePreviewDialog = null;
        }
        final Dialog dialog = new Dialog(this);
        footnotePreviewDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(10), dp(18), dp(14));
        card.setBackground(glassPanel(readerPanelBase(), dp(24), readerPanelStroke()));
        card.setElevation(dp(16));

        View handle = new View(this);
        handle.setBackground(glassPanel(readerPanelStroke(), dp(3), Color.TRANSPARENT));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(38), dp(5));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(8);
        card.addView(handle, handleLp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        String cleanLabel = label == null ? "" : label.trim();
        title.setText(cleanLabel.isEmpty() ? "Footnote" : "Footnote " + cleanLabel);
        title.setTextSize(16f);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setTextColor(readerPanelText());
        head.addView(title, new LinearLayout.LayoutParams(0, dp(40), 1f));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(24f);
        close.setTextColor(readerPanelSubText());
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(42), dp(40)));
        card.addView(head);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        TextView body = new TextView(this);
        String text = cleanFootnoteDisplayText(note.text);
        if (text.length() > 7000) text = text.substring(0, 7000).trim() + "…";
        if (text.isEmpty()) text = "Footnote text could not be previewed.";
        body.setText(text);
        body.setTextSize(15.5f);
        body.setTextColor(readerPanelText());
        body.setLineSpacing(dp(3), 1.08f);
        body.setPadding(dp(2), dp(4), dp(2), dp(8));
        scroll.addView(body, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int maxBody = Math.max(dp(100), (int) (getResources().getDisplayMetrics().heightPixels * 0.34f));
        card.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxBody));


        dialog.setContentView(card);
        dialog.setOnDismissListener(d -> { if (footnotePreviewDialog == dialog) footnotePreviewDialog = null; });
        dialog.show();
        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            win.setDimAmount(0.10f);
            WindowManager.LayoutParams lp = win.getAttributes();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.BOTTOM;
            win.setAttributes(lp);
        }
    }

    private void navigateToFootnote(ReaderSearchIndex.Footnote note) {
        if (webView == null || note == null || spine.isEmpty()) return;
        footnoteNavigationActive = true;
        footnoteReturnPending = false;
        footnoteReturnArmed = false;
        footnoteArmToken++;
        int target = Math.max(0, Math.min(spine.size() - 1, note.spineIndex));
        pendingTocFragment = note.fragment == null ? "" : note.fragment;
        if (target == currentSpine) {
            jumpToPendingTocFragment(() -> updateBookmarkIcon());
            return;
        }
        currentSpine = target;
        currentProgressPermille = 0;
        loadCurrentEpubChapter();
    }

    private static String navLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private boolean looksLikeFootnoteReference(String href, String epubType, String role, String rel, String cssClass) {
        String meta = navLower(epubType + " " + role + " " + rel + " " + cssClass);
        if (meta.contains("noteref") || meta.contains("doc-noteref") || meta.contains("footnote-ref") ||
                meta.contains("footnoteref") || meta.contains("fnref") || meta.contains("endnote-ref")) return true;
        String h = navLower(href);
        int hash = h.indexOf('#');
        String frag = hash >= 0 ? h.substring(hash + 1) : "";
        frag = Uri.decode(frag).toLowerCase(Locale.ROOT);
        boolean named = frag.startsWith("fn") || frag.startsWith("_fn") || frag.startsWith("ftn") || frag.startsWith("_ftn") ||
                frag.contains("footnote") || frag.contains("noteref") || frag.startsWith("note") ||
                frag.startsWith("endnote") || frag.startsWith("_edn");
        return named || looksLikeFootnoteDestination(href);
    }

    private boolean looksLikeFootnoteDestination(String href) {
        if (href == null || href.indexOf('#') < 0 || spine.isEmpty()) return false;
        int target = ReaderSearchIndex.resolveTargetSpine(spine, currentSpine, href);
        if (target < 0 || target >= spine.size()) return false;
        String title = target < chapterTitles.size() ? chapterTitles.get(target) : "";
        String file = spine.get(target) == null ? "" : spine.get(target).getName();
        String meta = navLower(title + " " + file).replace('_', ' ').replace('-', ' ').replace('.', ' ');
        return meta.matches(".*\b(footnotes?|endnotes?|notes?)\b.*");
    }

    private boolean looksLikeFootnoteBacklink(String href, String epubType, String role, String rel, String cssClass) {
        String meta = navLower(epubType + " " + role + " " + rel + " " + cssClass);
        if (meta.contains("backlink") || meta.contains("doc-backlink") || meta.contains("footnote-back") ||
                meta.contains("note-back") || meta.contains("fnback")) return true;
        String source = footnoteReturnSourceId == null ? "" : footnoteReturnSourceId.trim();
        if (!source.isEmpty() && href != null) {
            int hash = href.indexOf('#');
            if (hash >= 0 && hash + 1 < href.length()) {
                String fragment = Uri.decode(href.substring(hash + 1));
                if (source.equals(fragment)) return true;
            }
        }
        // Dedicated Notes chapters often use opaque backlink ids. If the note is open and
        // the tapped internal link resolves back to the exact source spine, treat it as Return.
        if (footnoteNavigationActive && href != null && href.indexOf('#') >= 0 &&
                currentSpine != footnoteReturnSpine && footnoteReturnSpine >= 0) {
            int target = ReaderSearchIndex.resolveTargetSpine(spine, currentSpine, href);
            if (target == footnoteReturnSpine) return true;
        }
        return false;
    }

    private synchronized void armFootnoteReturn(String sourceId) {
        if (webView == null || spine.isEmpty() || footnoteNavigationActive) return;
        footnoteReturnSpine = currentSpine;
        footnoteReturnProgressPermille = currentProgressPermille;
        footnoteReturnPage = currentPageInChapter;
        footnoteReturnSourceId = sourceId == null ? "" : sourceId;
        String url = webView.getUrl();
        footnoteReturnSourceUrl = url == null ? "" : url;
        footnoteReturnArmed = true;
        long token = ++footnoteArmToken;
        webView.postDelayed(() -> {
            synchronized (BookReaderActivity.this) {
                if (token == footnoteArmToken && footnoteReturnArmed && !footnoteNavigationActive)
                    footnoteReturnArmed = false;
            }
        }, 20000L);
    }

    private synchronized void onReaderVisitedUrl(String url) {
        if (!footnoteReturnArmed || footnoteNavigationActive || url == null || url.isEmpty()) return;
        if (!url.equals(footnoteReturnSourceUrl)) {
            footnoteNavigationActive = true;
            footnoteReturnArmed = false;
        }
    }

    private void restoreFootnoteReturn() {
        runOnUiThread(() -> {
            if ((!footnoteNavigationActive && !footnoteReturnArmed && !footnoteReturnPending) || webView == null || spine.isEmpty()) return;
            int targetSpine = Math.max(0, Math.min(spine.size() - 1, footnoteReturnSpine));
            int targetProgress = Math.max(0, Math.min(1000, footnoteReturnProgressPermille));
            int targetPage = Math.max(1, footnoteReturnPage);
            footnoteNavigationActive = false;
            footnoteReturnArmed = false;
            footnoteReturnPending = true;
            footnoteArmToken++;
            currentSpine = targetSpine;
            currentProgressPermille = targetProgress;

            String expected = Uri.fromFile(spine.get(targetSpine)).toString();
            String actual = webView.getUrl();
            if (actual != null) { int hash = actual.indexOf('#'); if (hash >= 0) actual = actual.substring(0, hash); }
            boolean sameDocument = expected.equals(actual);
            if (!sameDocument) {
                pendingTocFragment = footnoteReturnSourceId == null ? "" : footnoteReturnSourceId;
                loadCurrentEpubChapter();
                return;
            }
            finishFootnoteReturnOnReady(targetPage, targetProgress);
        });
    }

    private void finishFootnoteReturnOnReady(int targetPage, int targetProgress) {
        if (webView == null) { footnoteReturnPending = false; return; }
        if ("page".equals(readingMode)) {
            int pageZero = Math.max(0, targetPage - 1);
            String jump = "(function(){var st=window.__wowPageEngine;if(!st||st.mode!=='page')return false;" +
                    "st.page=st.clamp(" + pageZero + ",0,(st.count||1)-1);st.apply(false);st.report();return true;})()";
            try { webView.evaluateJavascript(jump, null); } catch (Exception ignored) {}
        } else {
            String jump = "(function(){var h=Math.max(0,document.documentElement.scrollHeight-window.innerHeight);" +
                    "window.scrollTo(0,h*" + (targetProgress / 1000.0) + ");return true;})()";
            try { webView.evaluateJavascript(jump, null); } catch (Exception ignored) {}
        }
        currentProgressPermille = targetProgress;
        footnoteReturnPending = false;
        updateEpubProgress(targetProgress);
        saveEpubStateOnly();
    }

    private ReaderWebView createPreloadWebView() {
        try {
            ReaderWebView view = new ReaderWebView(this);
            WebSettings s = view.getSettings();
            s.setJavaScriptEnabled(true);
            s.setUseWideViewPort(false);
            s.setLoadWithOverviewMode(false);
            s.setTextZoom(Math.max(80, Math.min(200, fontPercent)));
            s.setAllowFileAccess(true);
            s.setAllowContentAccess(true);
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);
            s.setDefaultTextEncodingName("UTF-8");
            s.setBuiltInZoomControls(false);
            s.setDisplayZoomControls(false);
            s.setSupportZoom(false);
            view.setOverScrollMode(View.OVER_SCROLL_NEVER);
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            view.setHorizontalScrollBarEnabled(false);
            view.setVerticalScrollBarEnabled(false);
            view.addJavascriptInterface(new ReaderBridge(view), "WoW");
            int solid = readerTheme == 2 ? Color.rgb(18, 18, 18) :
                    (readerTheme == 1 ? Color.rgb(244, 236, 216) : Color.WHITE);
            view.setBackgroundColor(solid);
            return view;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void handlePreloadPageFinished(WebView view, String url) {
        if (view == null || view != preloadWebView || !preloadLoading || preloadedSpine < 0) return;
        final int token = preloadGeneration;
        warmPreloadedChapter(view, token);
    }

    private void warmPreloadedChapter(WebView view, int token) {
        if (view == null || view != preloadWebView || token != preloadGeneration || preloadedSpine < 0) return;
        try { view.getSettings().setTextZoom(Math.max(80, Math.min(200, fontPercent))); }
        catch (Exception ignored) {}

        String bg = readerTheme == 2 ? "#121212" : (readerTheme == 1 ? "#F4ECD8" : "#FFFFFF");
        String fg = readerTheme == 2 ? "#E8EAED" : (readerTheme == 1 ? "#4A4033" : "#202124");
        double line = lineSpacing / 100.0;
        int safeMargin = Math.max(3, Math.min(14, marginPercent));
        String script;
        if ("page".equals(readingMode)) {
            String css = "html,body{height:100% !important;width:100% !important;margin:0 !important;padding:0 !important;overflow:hidden !important;background:" + bg + " !important;color:" + fg + " !important;transform:none !important;zoom:1 !important;}" +
                    "body{font-size:100% !important;line-height:" + line + " !important;max-width:none !important;}" +
                    "#wow-page-viewport{position:absolute !important;left:0 !important;top:0 !important;width:100vw !important;height:100vh !important;overflow:hidden !important;}" +
                    "#wow-page-flow{position:absolute !important;left:0 !important;top:0 !important;height:100vh !important;margin:0 !important;padding:4.2vh 0 5.2vh 0 !important;box-sizing:border-box !important;overflow:visible !important;column-fill:auto !important;transform-origin:0 0 !important;}" +
                    "#wow-page-flow img,#wow-page-flow svg,#wow-page-flow video,#wow-page-flow table{max-width:100% !important;height:auto !important;}";
            script = "(function(){try{" +
                    "var s=document.getElementById('wow-preload-style');if(!s){s=document.createElement('style');s.id='wow-preload-style';document.head.appendChild(s);}s.innerHTML=" + jsQuote(css) + ";" +
                    "var vp=document.getElementById('wow-page-viewport'),flow=document.getElementById('wow-page-flow');" +
                    "if(!vp){vp=document.createElement('div');vp.id='wow-page-viewport';if(!flow){flow=document.createElement('div');flow.id='wow-page-flow';while(document.body.firstChild)flow.appendChild(document.body.firstChild);}vp.appendChild(flow);document.body.appendChild(vp);}" +
                    "var w=Math.max(1,vp.clientWidth||window.innerWidth),m=Math.max(0,Math.round(w*" + (safeMargin / 100.0) + ")),pw=Math.max(1,w-2*m),gap=Math.max(0,w-pw);" +
                    "flow.style.width=pw+'px';flow.style.minWidth=pw+'px';flow.style.columnWidth=pw+'px';flow.style.columnGap=gap+'px';flow.style.transform='translate3d('+m+'px,0,0)';" +
                    "var wraps=flow.querySelectorAll('div,section,article,main,p,blockquote,dd,dt');for(var i=0;i<wraps.length;i++){var n=wraps[i],t=(n.textContent||'').replace(/\\s+/g,' ').trim();if(t.length<120)continue;var r=n.getBoundingClientRect();if(r.width>0&&r.width<pw*.90){n.style.setProperty('width','auto','important');n.style.setProperty('max-width','none','important');n.style.setProperty('margin-left','0','important');n.style.setProperty('margin-right','0','important');}}" +
                    "return true;}catch(e){return false;}})()";
        } else {
            String css = "html{overflow-x:hidden !important;background:" + bg + " !important;color:" + fg + " !important;}" +
                    "body{font-size:100% !important;line-height:" + line + " !important;padding:5vh " + safeMargin + "vw 12vh " + safeMargin + "vw !important;height:auto !important;max-width:900px !important;margin:auto !important;box-sizing:border-box !important;background:" + bg + " !important;color:" + fg + " !important;column-width:auto !important;column-gap:normal !important;transform:none !important;}" +
                    "body *{max-width:100%;}img,svg,video{max-width:100% !important;height:auto !important;}";
            script = "(function(){try{var vp=document.getElementById('wow-page-viewport'),flow=document.getElementById('wow-page-flow');if(flow){var before=vp||flow;while(flow.firstChild)document.body.insertBefore(flow.firstChild,before);if(vp)vp.remove();else flow.remove();}" +
                    "var s=document.getElementById('wow-preload-style');if(!s){s=document.createElement('style');s.id='wow-preload-style';document.head.appendChild(s);}s.innerHTML=" + jsQuote(css) + ";return true;}catch(e){return false;}})()";
        }

        try {
            view.evaluateJavascript(script, result -> view.postOnAnimation(() -> view.postOnAnimation(() -> {
                if (view != preloadWebView || token != preloadGeneration || !preloadLoading || preloadedSpine < 0) return;
                preloadReady = true;
                preloadLoading = false;
            })));
        } catch (Exception ignored) {
            if (view == preloadWebView && token == preloadGeneration) {
                preloadReady = true;
                preloadLoading = false;
            }
        }
    }

    private void scheduleAdjacentChapterPreload(int direction) {
        if (isPdf || preloadWebView == null || spine.isEmpty() || chapterLoading || isFinishing()) return;
        int dir = direction < 0 ? -1 : 1;
        int target = currentSpine + dir;
        if (target < 0 || target >= spine.size()) {
            dir = -dir;
            target = currentSpine + dir;
        }
        if (target < 0 || target >= spine.size() || target == currentSpine) return;
        if (preloadedSpine == target && (preloadReady || preloadLoading)) return;

        preferredPreloadDirection = dir;
        preloadGeneration++;
        preloadedSpine = target;
        preloadReady = false;
        preloadLoading = true;
        try { preloadWebView.stopLoading(); } catch (Exception ignored) {}
        preloadWebView.setEnabled(false);
        preloadWebView.setVisibility(View.VISIBLE);
        preloadWebView.setAlpha(0.01f);
        preloadWebView.setScaleX(1f);
        preloadWebView.setScaleY(1f);
        preloadWebView.setTranslationX(0f);
        try { preloadWebView.getSettings().setTextZoom(Math.max(80, Math.min(200, fontPercent))); }
        catch (Exception ignored) {}
        try {
            preloadWebView.loadUrl(Uri.fromFile(spine.get(target)).toString());
        } catch (Exception e) {
            cancelChapterPreload();
        }
    }

    private void cancelChapterPreload() {
        preloadGeneration++;
        preloadReady = false;
        preloadLoading = false;
        preloadedSpine = -1;
        if (preloadWebView != null) {
            try { preloadWebView.stopLoading(); } catch (Exception ignored) {}
            preloadWebView.setEnabled(false);
            preloadWebView.setAlpha(0.01f);
        }
    }

    private boolean activatePreloadedChapterIfReady() {
        if (preloadWebView == null || !preloadReady || preloadedSpine != currentSpine) return false;
        if (!(webView instanceof ReaderWebView)) return false;

        ReaderWebView incoming = preloadWebView;
        ReaderWebView outgoing = (ReaderWebView) webView;
        preloadGeneration++;
        preloadReady = false;
        preloadLoading = false;
        preloadedSpine = -1;

        webView = incoming;
        preloadWebView = outgoing;
        currentSelection = null;
        hideSelectionBar();
        final int generation = ++chapterLoadGeneration;
        chapterLoading = true;
        pageTurnLocked = "page".equals(readingMode);
        currentPageInChapter = 1;
        pageCountInChapter = 1;

        outgoing.animate().cancel();
        outgoing.setEnabled(false);
        outgoing.setAlpha(0.01f);
        outgoing.setVisibility(View.VISIBLE);
        outgoing.setScaleX(1f);
        outgoing.setScaleY(1f);
        outgoing.setTranslationX(0f);

        incoming.animate().cancel();
        incoming.setEnabled(true);
        incoming.setVisibility(View.VISIBLE);
        incoming.setScaleX(1f);
        incoming.setScaleY(1f);
        incoming.setTranslationX(0f);
        incoming.setAlpha(0f);
        incoming.bringToFront();
        if (chapterTransitionOverlay != null && chapterTransitionOverlay.getVisibility() == View.VISIBLE)
            chapterTransitionOverlay.bringToFront();
        if (readerStyleOverlay != null && readerStyleOverlay.getVisibility() == View.VISIBLE)
            readerStyleOverlay.bringToFront();
        if (pageSlideOverlay != null && pageSlideOverlay.getVisibility() == View.VISIBLE)
            pageSlideOverlay.bringToFront();

        updateEpubProgress(currentProgressPermille);
        updateBookmarkIcon();
        applyReaderStyle(true);
        webView.postDelayed(() -> {
            if (generation == chapterLoadGeneration) applySavedAnnotations();
        }, 260L);
        webView.postDelayed(() -> {
            if (generation == chapterLoadGeneration) installSelectionWatcher();
        }, 320L);
        webView.postDelayed(() -> {
            if (generation == chapterLoadGeneration && chapterLoading && "scroll".equals(readingMode))
                completePageReady(generation);
        }, 850L);
        webView.postDelayed(() -> {
            if (generation == chapterLoadGeneration && chapterLoading && "page".equals(readingMode))
                forceChapterRepaginate(generation);
        }, 1050L);
        return true;
    }

    private void setupWebView(FrameLayout content) {
        epubWebContent = content;
        webView = new ReaderWebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        // Keep every EPUB chapter at the physical WebView viewport. Author viewport
        // metadata must not trigger overview zoom when moving between spine items.
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);
        s.setTextZoom(Math.max(80, Math.min(200, fontPercent)));
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setDefaultTextEncodingName("UTF-8");
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVerticalScrollBarEnabled(false);
        webView.addJavascriptInterface(new ReaderBridge(webView), "WoW");

        pageTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        readerTapDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onSingleTapUp(MotionEvent e) {
                // Immediate edge tap: do not wait for the double-tap timeout.
                handleReaderTap(e.getX(), e.getY());
                return true;
            }

            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (!"page".equals(readingMode) || e1 == null || e2 == null || chapterLoading || pageTurnLocked)
                    return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                int edgeSafe = dp(30);
                if (e1.getX() < edgeSafe || e1.getX() > webView.getWidth() - edgeSafe) return false;
                if (Math.abs(dx) < dp(64) || Math.abs(dx) < Math.abs(dy) * 1.35f || Math.abs(velocityX) < 500f)
                    return false;
                turnPage(dx < 0 ? 1 : -1);
                return true;
            }
        });

        readerTouchListener = (v, event) -> {
            // Legacy v2.4/v2.5 paper-curl gesture is intentionally retired.
            // None/Slide are the only live page animations.
            readerTapDetector.onTouchEvent(event);
            return false;
        };
        webView.setOnTouchListener(readerTouchListener);

        webView.setWebViewClient(createReaderWebViewClient());

        content.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        preloadWebView = createPreloadWebView();
        if (preloadWebView != null) {
            preloadWebView.setOnTouchListener(readerTouchListener);
            preloadWebView.setWebViewClient(createReaderWebViewClient());
            preloadWebView.setEnabled(false);
            preloadWebView.setAlpha(0.01f);
            preloadWebView.setVisibility(View.VISIBLE);
            content.addView(preloadWebView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            webView.bringToFront();
        }

        chapterTransitionOverlay = new ImageView(this);
        // PixelCopy captures the exact WebView viewport. Map that bitmap 1:1 to the
        // same MATCH_PARENT bounds; never crop/zoom the outgoing chapter frame.
        chapterTransitionOverlay.setScaleType(ImageView.ScaleType.FIT_XY);
        chapterTransitionOverlay.setVisibility(View.GONE);
        chapterTransitionOverlay.setClickable(false);
        content.addView(chapterTransitionOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        readerStyleOverlay = new ImageView(this);
        readerStyleOverlay.setScaleType(ImageView.ScaleType.FIT_XY);
        readerStyleOverlay.setVisibility(View.GONE);
        readerStyleOverlay.setClickable(false);
        readerStyleOverlay.setFocusable(false);
        content.addView(readerStyleOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        pageSlideOverlay = new ImageView(this);
        pageSlideOverlay.setScaleType(ImageView.ScaleType.FIT_XY);
        pageSlideOverlay.setVisibility(View.GONE);
        pageSlideOverlay.setClickable(false);
        pageSlideOverlay.setFocusable(false);
        content.addView(pageSlideOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Do not attach the legacy v2.4/v2.5 PageCurlView. It used full-screen
        // bitmap transforms and is not part of the current None/Slide reader anymore.
        pageCurlView = null;
    }

    private void handleReaderTap(float x, float y) {
        if (android.os.SystemClock.uptimeMillis() < footnoteTapSuppressUntilMs) return;
        if (webView == null || chapterLoading || tapHitTestPending) return;
        // Use WebView native hit testing before the async DOM hit test. This is density-safe
        // and prevents a real anchor/footnote tap from also being treated as an edge page turn.
        try {
            android.webkit.WebView.HitTestResult nativeHit = webView.getHitTestResult();
            if (nativeHit != null) {
                int hitType = nativeHit.getType();
                if (hitType == android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                        hitType == android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) return;
            }
        } catch (Exception ignored) {}

        final float ratio = x / Math.max(1f, webView.getWidth());
        final int px = Math.round(x);
        final int py = Math.round(y);
        tapHitTestPending = true;

        String hitTest = "(function(){try{" +
                "if(window.getSelection&&String(window.getSelection()).length>0)return 'selection';" +
                "var n=document.elementFromPoint(" + px + "," + py + ");" +
                "while(n){if(n.tagName&&n.tagName.toLowerCase()==='a')return 'link';n=n.parentElement;}" +
                "return 'plain';}catch(e){return 'plain';}})()";

        try {
            webView.evaluateJavascript(hitTest, result -> {
                tapHitTestPending = false;
                if (android.os.SystemClock.uptimeMillis() < footnoteTapSuppressUntilMs) return;
                if (result != null && (result.contains("link") || result.contains("selection"))) return;

                if ("page".equals(readingMode)) {
                    if (ratio < 0.34f) turnPageFromTap(-1, y);
                    else if (ratio > 0.66f) turnPageFromTap(1, y);
                    else toggleControls();
                } else {
                    if (ratio < 0.24f) navigateChapter(-1, true);
                    else if (ratio > 0.76f) navigateChapter(1, false);
                    else toggleControls();
                }
            });
        } catch (Exception ignored) {
            tapHitTestPending = false;
            toggleControls();
        }
    }

    private static final class SelectionData {
        String text;
        int start;
        int end;
    }

    private void keepNativeSelectionToolbarHidden() {
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

    private void captureCurrentSelection(int action, ActionMode mode) {
        if (webView == null || isPdf) {
            if (mode != null) mode.finish();
            return;
        }
        String js = "(function(){try{" +
                "var sel=window.getSelection&&window.getSelection();if(!sel||sel.rangeCount===0||sel.isCollapsed)return null;" +
                "var range=sel.getRangeAt(0),root=document.getElementById('wow-page-flow')||document.body;" +
                "if(!root||!root.contains(range.commonAncestorContainer))return null;" +
                "function nodes(){var out=[],w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,{acceptNode:function(n){var p=n.parentElement;if(!p)return NodeFilter.FILTER_REJECT;var tag=p.tagName;if(tag==='SCRIPT'||tag==='STYLE'||tag==='NOSCRIPT')return NodeFilter.FILTER_REJECT;return n.nodeValue&&n.nodeValue.length?NodeFilter.FILTER_ACCEPT:NodeFilter.FILTER_REJECT;}});var n;while(n=w.nextNode())out.push(n);return out;}" +
                "var ns=nodes(),pos=0,start=-1,end=-1;for(var i=0;i<ns.length;i++){var n=ns[i],len=n.nodeValue.length;if(n===range.startContainer)start=pos+Math.max(0,Math.min(len,range.startOffset));if(n===range.endContainer)end=pos+Math.max(0,Math.min(len,range.endOffset));pos+=len;}" +
                "var text=range.toString();if(start<0||end<start){var full='';for(var j=0;j<ns.length;j++)full+=ns[j].nodeValue||'';var raw=0;try{var pre=document.createRange();pre.selectNodeContents(root);pre.setEnd(range.startContainer,range.startOffset);raw=pre.toString().length;}catch(x){}var best=-1,dist=1e18,from=0,at;while(text&&(at=full.indexOf(text,from))>=0){var d=Math.abs(at-raw);if(d<dist){dist=d;best=at;}from=at+1;}if(best>=0){start=best;end=best+text.length;}}" +
                "var lm=text.match(/^\\s+/),rm=text.match(/\\s+$/),lead=lm?lm[0].length:0,trail=rm?rm[0].length:0;start+=lead;end-=trail;text=text.trim();" +
                "if(!text||start<0||end<=start)return null;return JSON.stringify({text:text,start:start,end:end});" +
                "}catch(e){return null;}})()";
        try {
            webView.evaluateJavascript(js, result -> {
                SelectionData data = parseSelectionResult(result);
                if (mode != null) mode.finish();
                clearWebSelection();
                if (data == null || data.text == null || data.text.trim().isEmpty() || data.end <= data.start) {
                    Toast.makeText(this, "Select some text first", Toast.LENGTH_SHORT).show();
                    return;
                }
                data.text = data.text.trim();
                if (action == SEL_HIGHLIGHT) showHighlightColorDialog(data);
                else if (action == SEL_NOTE) showNoteEditor(data);
                else if (action == SEL_TRANSLATE) showTranslateDialog(data.text);
                else if (action == SEL_COPY) copySelectedText(data.text);
            });
        } catch (Exception e) {
            if (mode != null) mode.finish();
        }
    }

    private SelectionData parseSelectionResult(String result) {
        if (result == null || "null".equals(result)) return null;
        try {
            Object decoded = new JSONTokener(result).nextValue();
            String raw = decoded instanceof String ? (String) decoded : String.valueOf(decoded);
            JSONObject o = new JSONObject(raw);
            SelectionData d = new SelectionData();
            d.text = o.optString("text", "");
            d.start = Math.max(0, o.optInt("start", 0));
            d.end = Math.max(d.start, o.optInt("end", d.start));
            return d;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void clearWebSelection() {
        if (webView == null) return;
        try {
            webView.evaluateJavascript("(function(){try{var s=window.getSelection();if(s)s.removeAllRanges();}catch(e){}})()", null);
        } catch (Exception ignored) {}
    }

    private void showHighlightColorDialog(SelectionData data) {
        final String[] colors = {
                "rgba(255,213,79,.46)",
                "rgba(128,203,196,.42)",
                "rgba(244,143,177,.42)",
                "rgba(149,117,205,.38)",
                "rgba(100,181,246,.40)"
        };
        final int[] swatches = {
                Color.rgb(255, 205, 70), Color.rgb(113, 201, 183), Color.rgb(239, 132, 172),
                Color.rgb(146, 112, 210), Color.rgb(108, 170, 232)
        };
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = readerSheetBase("Highlight", "Choose a color", dialog);
        LinearLayout palette = new LinearLayout(this);
        palette.setOrientation(LinearLayout.HORIZONTAL);
        palette.setGravity(Gravity.CENTER);
        palette.setPadding(dp(8), dp(8), dp(8), dp(10));
        for (int i = 0; i < colors.length; i++) {
            final int which = i;
            TextView swatch = new TextView(this);
            swatch.setText("●");
            swatch.setTextSize(31);
            swatch.setTextColor(swatches[i]);
            swatch.setGravity(Gravity.CENTER);
            swatch.setBackground(readerRoundRect(readerPanelBase(), dp(22), dp(1), readerPanelStroke()));
            swatch.setOnClickListener(v -> {
                dialog.dismiss();
                saveAnnotation(data, colors[which], "");
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(52));
            if (i > 0) lp.leftMargin = dp(7);
            palette.addView(swatch, lp);
        }
        sheet.addView(palette, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
        presentReaderSheet(dialog, sheet, false);
    }

    private void showNoteEditor(SelectionData data) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = readerSheetBase("Add note", shortQuote(data.text, 120), dialog);

        EditText input = new EditText(this);
        input.setHint("Write a note…");
        input.setTextSize(14.5f);
        input.setTextColor(readerPanelText());
        input.setHintTextColor(readerPanelSubText());
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(3);
        input.setMaxLines(7);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(readerRoundRect(readerPanelBase(), dp(16), dp(1), readerAccent()));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132));
        inputLp.topMargin = dp(6);
        sheet.addView(input, inputLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(10), 0, 0);
        TextView cancel = compactReaderButton("Cancel", false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        TextView save = compactReaderButton("Save note", true);
        save.setOnClickListener(v -> {
            String note = input.getText().toString();
            dialog.dismiss();
            saveAnnotation(data, "rgba(149,117,205,.36)", note);
        });
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(dp(96), dp(42));
        cancelLp.rightMargin = dp(8);
        actions.addView(cancel, cancelLp);
        actions.addView(save, new LinearLayout.LayoutParams(dp(124), dp(42)));
        sheet.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        presentReaderSheet(dialog, sheet, true);
        input.requestFocus();
    }

    private TextView compactReaderButton(String label, boolean primary) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(13f);
        button.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(primary ? Color.WHITE : readerAccent());
        button.setBackground(readerRoundRect(primary ? readerAccent() : readerPanelBase(), dp(16), dp(1), readerAccent()));
        return button;
    }

    private LinearLayout readerSheetBase(String title, String subtitle, Dialog dialog) {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(18), dp(9), dp(18), dp(18));
        sheet.setBackground(readerRoundRect(readerPanelBase(), dp(26), dp(1), readerPanelStroke()));
        sheet.setElevation(dp(14));

        TextView handle = new TextView(this);
        handle.setBackground(readerRoundRect(readerPanelSubText(), dp(2), 0, Color.TRANSPARENT));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(50), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(11);
        sheet.addView(handle, handleLp);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(20);
        heading.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        heading.setTextColor(readerPanelText());
        copy.addView(heading);
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(subtitle);
            sub.setTextSize(10.5f);
            sub.setTextColor(readerPanelSubText());
            sub.setMaxLines(2);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            sub.setPadding(0, dp(2), 0, 0);
            copy.addView(sub);
        }
        header.addView(copy, new LinearLayout.LayoutParams(0, dp(54), 1f));
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(21);
        close.setTextColor(readerPanelSubText());
        close.setGravity(Gravity.CENTER);
        close.setBackground(readerRoundRect(readerPanelBase(), dp(18), dp(1), readerPanelStroke()));
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        sheet.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        return sheet;
    }

    private void presentReaderSheet(Dialog dialog, View content, boolean keyboard) {
        dialog.setContentView(content);
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.34f);
        window.setGravity(Gravity.BOTTOM);
        int sw = getResources().getDisplayMetrics().widthPixels;
        window.setLayout(Math.min(sw, dp(720)), ViewGroup.LayoutParams.WRAP_CONTENT);
        if (keyboard) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            window.setBackgroundBlurRadius(dp(16));
        }
    }

    private GradientDrawable readerRoundRect(int fill, int radius, int strokeWidth, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private void saveAnnotation(SelectionData data, String color, String note) {
        ReaderAnnotationStore.add(prefs, bookFile.getName(), currentSpine,
                data.start, data.end, data.text, color, note);
        GoogleAutoSync.scheduleSoon(this);
        applySavedAnnotations();
        updateAnnotationButton();
        Toast.makeText(this, note == null || note.trim().isEmpty() ? "Highlighted" : "Note saved",
                Toast.LENGTH_SHORT).show();
    }

    private void applySavedAnnotations() {
        if (webView == null || isPdf || spine.isEmpty()) return;
        String json = ReaderAnnotationStore.chapterJson(prefs, bookFile.getName(), currentSpine);
        String pending = pendingAnnotationId;
        pendingAnnotationId = null;
        String js = "(function(){try{" +
                "var root=document.getElementById('wow-page-flow')||document.body;if(!root)return;" +
                "var old=root.querySelectorAll('span.wow-annotation');for(var oi=old.length-1;oi>=0;oi--){var q=old[oi];if(q.parentNode)q.parentNode.replaceChild(document.createTextNode(q.textContent||''),q);}" +
                "root.normalize();var anns=JSON.parse(" + jsQuote(json) + ");" +
                "function nodes(){var out=[],w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,{acceptNode:function(n){var p=n.parentElement;if(!p)return NodeFilter.FILTER_REJECT;var tag=p.tagName;" +
                "if(tag==='SCRIPT'||tag==='STYLE'||tag==='NOSCRIPT')return NodeFilter.FILTER_REJECT;return n.nodeValue&&n.nodeValue.length?NodeFilter.FILTER_ACCEPT:NodeFilter.FILTER_REJECT;}});var n;while(n=w.nextNode())out.push(n);return out;}" +
                "function resolved(a,ns){var full='';for(var z=0;z<ns.length;z++)full+=ns[z].nodeValue||'';var s=Math.max(0,Math.min(full.length,a.start||0)),e=Math.max(s,Math.min(full.length,a.end||s)),q=(a.quote||'').trim();if(q&&full.slice(s,e)!==q){var best=-1,dist=1e18,from=0,at;while((at=full.indexOf(q,from))>=0){var d=Math.abs(at-s);if(d<dist){dist=d;best=at;}from=at+1;}if(best>=0){s=best;e=best+q.length;}}return [s,e];}" +
                "function apply(a){var ns=nodes(),rr=resolved(a,ns),targetStart=rr[0],targetEnd=rr[1],pos=0,parts=[];for(var i=0;i<ns.length;i++){var n=ns[i],len=n.nodeValue.length,lo=Math.max(targetStart-pos,0),hi=Math.min(targetEnd-pos,len);if(hi>lo)parts.push({n:n,lo:lo,hi:hi});pos+=len;if(pos>=targetEnd)break;}" +
                "for(var j=parts.length-1;j>=0;j--){try{var p=parts[j],r=document.createRange();r.setStart(p.n,p.lo);r.setEnd(p.n,p.hi);var sp=document.createElement('span');sp.className='wow-annotation';sp.setAttribute('data-wow-ann-id',a.id);sp.style.background=a.color||'rgba(255,235,59,.48)';sp.style.borderRadius='3px';sp.style.boxDecorationBreak='clone';sp.style.webkitBoxDecorationBreak='clone';if(a.note)sp.style.borderBottom='2px solid rgba(251,188,4,.9)';r.surroundContents(sp);}catch(e){}}}" +
                "for(var ai=0;ai<anns.length;ai++)apply(anns[ai]);" +
                (pending == null ? "" : "setTimeout(function(){var el=root.querySelector('[data-wow-ann-id=\"'+" + jsQuote(pending) + "+'\"]');if(!el)return;var st=window.__wowPageEngine||{};if(st.mode==='page'&&st.step){var fr=(st.flow||root).getBoundingClientRect(),er=el.getBoundingClientRect();var pg=Math.max(0,Math.min((st.count||1)-1,Math.floor(Math.max(0,er.left-fr.left)/st.step)));st.page=pg;if(st.apply)st.apply(false);if(st.report)st.report();}else{el.scrollIntoView({block:'center',behavior:'smooth'});}},80);") +
                "}catch(e){}})();";
        try { webView.evaluateJavascript(js, null); } catch (Exception ignored) {}
        updateAnnotationButton();
    }

    private void updateAnnotationButton() {
        if (annotationButton == null || isPdf) return;
        int count = ReaderAnnotationStore.count(prefs, bookFile.getName());
        annotationButton.setContentDescription(count == 0 ? "Notes and highlights" : "Notes and highlights · " + count);
        annotationButton.setAlpha(count == 0 ? 0.82f : 1f);
    }

    private void showAnnotations() {
        if (isPdf) return;
        List<ReaderAnnotationStore.Annotation> items = ReaderAnnotationStore.load(prefs, bookFile.getName());
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = readerSheetBase("Notes & highlights", items.isEmpty() ? "Nothing saved in this book yet" : items.size() + " saved items", dialog);
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Select text while reading, then choose Highlight or Note.");
            empty.setTextSize(13);
            empty.setTextColor(readerPanelSubText());
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(24), dp(20), dp(24));
            sheet.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)));
        } else {
            ScrollView scroll = new ScrollView(this);
            scroll.setVerticalScrollBarEnabled(false);
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            for (ReaderAnnotationStore.Annotation a : items) {
                String chapter = a.chapter >= 0 && a.chapter < spine.size() ? chapterDisplayTitle(a.chapter) : "Chapter " + (a.chapter + 1);
                boolean isNote = a.note != null && !a.note.trim().isEmpty();
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(7), dp(9), dp(7));
                row.setBackground(readerRoundRect(readerPanelBase(), dp(15), dp(1), readerPanelStroke()));
                TextView icon = new TextView(this);
                icon.setText(isNote ? "▤" : "✎");
                icon.setTextSize(17);
                icon.setTextColor(readerAccent());
                icon.setGravity(Gravity.CENTER);
                row.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(48)));
                LinearLayout copy = new LinearLayout(this);
                copy.setOrientation(LinearLayout.VERTICAL);
                TextView quote = new TextView(this);
                quote.setText(shortQuote(a.quote, 92));
                quote.setTextSize(12.5f);
                quote.setTextColor(readerPanelText());
                quote.setMaxLines(2);
                quote.setEllipsize(android.text.TextUtils.TruncateAt.END);
                copy.addView(quote);
                TextView sub = new TextView(this);
                sub.setText((isNote ? "Note" : "Highlight") + "  ·  " + chapter);
                sub.setTextSize(9.5f);
                sub.setTextColor(readerPanelSubText());
                copy.addView(sub);
                row.addView(copy, new LinearLayout.LayoutParams(0, dp(50), 1f));
                TextView arrow = new TextView(this);
                arrow.setText("›");
                arrow.setTextSize(20);
                arrow.setTextColor(readerPanelSubText());
                arrow.setGravity(Gravity.CENTER);
                row.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(48)));
                row.setOnClickListener(v -> { dialog.dismiss(); showAnnotationDetail(a); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
                lp.topMargin = dp(6);
                list.addView(row, lp);
            }
            int h = Math.min(dp(430), Math.max(dp(96), items.size() * dp(70)));
            sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        }
        presentReaderSheet(dialog, sheet, false);
    }

    private void showAnnotationDetail(ReaderAnnotationStore.Annotation a) {
        String chapter = a.chapter >= 0 && a.chapter < spine.size() ? chapterDisplayTitle(a.chapter) : "Chapter " + (a.chapter + 1);
        boolean isNote = a.note != null && !a.note.trim().isEmpty();
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout sheet = readerSheetBase(isNote ? "Note" : "Highlight", chapter, dialog);
        TextView quote = new TextView(this);
        quote.setText("“" + a.quote + "”");
        quote.setTextSize(14);
        quote.setTextColor(readerPanelText());
        quote.setLineSpacing(dp(2), 1.12f);
        quote.setPadding(dp(13), dp(11), dp(13), dp(11));
        quote.setBackground(readerRoundRect(readerPanelBase(), dp(15), dp(1), readerPanelStroke()));
        sheet.addView(quote);
        if (isNote) {
            TextView note = new TextView(this);
            note.setText(a.note);
            note.setTextSize(13);
            note.setTextColor(readerPanelText());
            note.setPadding(dp(13), dp(11), dp(13), dp(11));
            note.setBackground(readerRoundRect(readerPanelBase(), dp(15), dp(1), readerAccent()));
            LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            noteLp.topMargin = dp(8);
            sheet.addView(note, noteLp);
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView remove = compactReaderButton("Delete", false);
        remove.setTextColor(Color.rgb(211, 65, 65));
        remove.setOnClickListener(v -> {
            dialog.dismiss();
            ReaderAnnotationStore.remove(prefs, bookFile.getName(), a.id);
            GoogleAutoSync.scheduleSoon(this);
            applySavedAnnotations();
            updateAnnotationButton();
            Toast.makeText(this, "Removed", Toast.LENGTH_SHORT).show();
        });
        TextView go = compactReaderButton("Go to text", true);
        go.setOnClickListener(v -> { dialog.dismiss(); goToAnnotation(a); });
        LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(dp(92), dp(42)); removeLp.rightMargin = dp(8);
        actions.addView(remove, removeLp);
        actions.addView(go, new LinearLayout.LayoutParams(dp(126), dp(42)));
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)); actionsLp.topMargin = dp(9);
        sheet.addView(actions, actionsLp);
        presentReaderSheet(dialog, sheet, false);
    }

    private void goToAnnotation(ReaderAnnotationStore.Annotation a) {
        if (a == null || a.chapter < 0 || a.chapter >= spine.size()) return;
        pendingAnnotationId = a.id;
        if (a.chapter == currentSpine) {
            applySavedAnnotations();
            return;
        }
        int direction = a.chapter > currentSpine ? 1 : -1;
        prepareChapterTransition(direction);
        currentSpine = a.chapter;
        currentProgressPermille = 0;
        saveEpubStateOnly();
        loadCurrentEpubChapter();
    }

    private String shortQuote(String text, int max) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").trim();
        if (clean.length() <= max) return clean;
        return clean.substring(0, Math.max(1, max - 1)) + "…";
    }

    private void copySelectedText(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("WoW Reader", text));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
    }

    private void showTranslateDialog(String text) {
        boolean hasMyanmar = text != null && text.matches("(?s).*[\\u1000-\\u109F\\uA9E0-\\uA9FF\\uAA60-\\uAA7F].*");
        String[] labels = hasMyanmar ? new String[]{"English", "မြန်မာ"} : new String[]{"မြန်မာ", "English"};
        String[] codes = hasMyanmar ? new String[]{"en", "my"} : new String[]{"my", "en"};
        new AlertDialog.Builder(this)
                .setTitle("Translate to")
                .setItems(labels, (dialog, which) -> openTranslation(text, codes[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openTranslation(String text, String targetLanguage) {
        try {
            String url = "https://translate.google.com/?sl=auto&tl=" + targetLanguage +
                    "&text=" + Uri.encode(text == null ? "" : text) + "&op=translate";
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open translation", Toast.LENGTH_SHORT).show();
        }
    }

    private View selectionActionButton(String label, int action) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setClickable(true);
        button.setContentDescription(label);

        int iconColor = readerAccent();
        String iconText = "✎";
        if (action == SEL_HIGHLIGHT) iconText = "✎";
        else if (action == SEL_NOTE) iconText = "▤";
        else if (action == SEL_TRANSLATE) iconText = "A";
        else if (action == SEL_COPY) iconText = "▣";

        TextView icon = new TextView(this);
        icon.setText(iconText);
        icon.setTextSize(action == SEL_TRANSLATE ? 14 : 16);
        icon.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        icon.setTextColor(iconColor);
        icon.setGravity(Gravity.CENTER);
        button.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(38)));

        TextView copy = new TextView(this);
        copy.setText(label);
        copy.setTextSize(10.5f);
        copy.setTextColor(readerPanelText());
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setSingleLine(true);
        button.addView(copy, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));

        button.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN)
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(45L).start();
            else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL)
                v.animate().scaleX(1f).scaleY(1f).setDuration(85L).start();
            return false;
        });
        button.setOnClickListener(v -> performSelectionAction(action));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(action == SEL_TRANSLATE ? 82 : 72), dp(42)));
        return button;
    }

    private void installSelectionWatcher() {
        if (webView == null || isPdf) return;
        String js = "(function(){try{" +
                "if(window.__wowSelectionWatcher)return;window.__wowSelectionWatcher=true;var timer=0;" +
                "document.addEventListener('selectionchange',function(){clearTimeout(timer);timer=setTimeout(function(){try{" +
                "var sel=window.getSelection&&window.getSelection();if(!sel||sel.rangeCount===0||sel.isCollapsed){WoW.onSelection('',0,0);return;}" +
                "var range=sel.getRangeAt(0),root=document.getElementById('wow-page-flow')||document.body;if(!root||!root.contains(range.commonAncestorContainer)){WoW.onSelection('',0,0);return;}" +
                "function nodes(){var out=[],w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,{acceptNode:function(n){var p=n.parentElement;if(!p)return NodeFilter.FILTER_REJECT;var tag=p.tagName;if(tag==='SCRIPT'||tag==='STYLE'||tag==='NOSCRIPT')return NodeFilter.FILTER_REJECT;return n.nodeValue&&n.nodeValue.length?NodeFilter.FILTER_ACCEPT:NodeFilter.FILTER_REJECT;}});var n;while(n=w.nextNode())out.push(n);return out;}" +
                "var ns=nodes(),pos=0,start=-1,end=-1;for(var i=0;i<ns.length;i++){var n=ns[i],len=n.nodeValue.length;if(n===range.startContainer)start=pos+Math.max(0,Math.min(len,range.startOffset));if(n===range.endContainer)end=pos+Math.max(0,Math.min(len,range.endOffset));pos+=len;}" +
                "var text=range.toString();if(start<0||end<start){var full='';for(var j=0;j<ns.length;j++)full+=ns[j].nodeValue||'';var at=full.indexOf(text);if(at>=0){start=at;end=at+text.length;}}" +
                "var lm=text.match(/^\\s+/),rm=text.match(/\\s+$/),lead=lm?lm[0].length:0,trail=rm?rm[0].length:0;start+=lead;end-=trail;text=text.trim();" +
                "if(!text||start<0||end<=start){WoW.onSelection('',0,0);return;}WoW.onSelection(text,start,end);" +
                "}catch(e){WoW.onSelection('',0,0);}},180);});" +
                "}catch(e){}})();";
        try { webView.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

    private void onWebSelection(String text, int start, int end) {
        if (paperGestureActive || suppressingSelectionForPaperGesture()) {
            currentSelection = null;
            hideSelectionBar();
            clearWebSelection();
            return;
        }
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty() || end <= start) {
            currentSelection = null;
            hideSelectionBar();
            return;
        }
        if (paperGestureCandidate) {
            paperGestureCandidate = false;
            recyclePageVelocityTracker();
        }
        if (currentSelection != null && currentSelection.start == start && currentSelection.end == end && clean.equals(currentSelection.text)) {
            if (selectionBar != null && selectionBar.getVisibility() != View.VISIBLE) showSelectionBar();
            return;
        }
        SelectionData data = new SelectionData();
        data.text = clean;
        data.start = Math.max(0, start);
        data.end = Math.max(data.start, end);
        currentSelection = data;
        keepNativeSelectionToolbarHidden();
        showSelectionBar();
    }

    private void showSelectionBar() {
        if (selectionBar == null || isPdf) return;
        selectionBar.animate().cancel();
        selectionBar.bringToFront();
        if (webView == null) {
            positionSelectionBarFallback();
            return;
        }
        String js = "(function(){try{var s=window.getSelection&&window.getSelection();" +
                "if(!s||s.rangeCount===0||s.isCollapsed)return null;var r=s.getRangeAt(0).getBoundingClientRect();" +
                "var d=window.devicePixelRatio||1;return JSON.stringify({x:((r.left+r.right)/2)*d,t:r.top*d,b:r.bottom*d});" +
                "}catch(e){return null;}})()";
        try { webView.evaluateJavascript(js, this::positionSelectionBarFromJs); }
        catch (Exception ignored) { positionSelectionBarFallback(); }
    }

    private void positionSelectionBarFromJs(String result) {
        if (selectionBar == null) return;
        try {
            if (result == null || "null".equals(result)) { positionSelectionBarFallback(); return; }
            Object decoded = new JSONTokener(result).nextValue();
            String raw = decoded instanceof String ? (String) decoded : String.valueOf(decoded);
            JSONObject o = new JSONObject(raw);
            float centerX = (float) o.optDouble("x", webView == null ? 0 : webView.getWidth() / 2f);
            float top = (float) o.optDouble("t", 0);
            float bottom = (float) o.optDouble("b", top);
            positionSelectionBar(centerX, top, bottom);
        } catch (Exception ignored) { positionSelectionBarFallback(); }
    }

    private void positionSelectionBar(float selectionCenterX, float selectionTop, float selectionBottom) {
        if (selectionBar == null || root == null) return;
        final boolean wasVisible = selectionBar.getVisibility() == View.VISIBLE;
        selectionBar.measure(
                View.MeasureSpec.makeMeasureSpec(Math.max(1, root.getWidth() - dp(20)), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(dp(50), View.MeasureSpec.EXACTLY));
        int barW = Math.max(dp(280), selectionBar.getMeasuredWidth());
        int barH = dp(50);
        int rootW = Math.max(1, root.getWidth());
        int rootH = Math.max(1, root.getHeight());
        int[] webLoc = new int[2];
        int[] rootLoc = new int[2];
        if (webView != null) webView.getLocationOnScreen(webLoc);
        root.getLocationOnScreen(rootLoc);
        int webOffsetX = webLoc[0] - rootLoc[0];
        int webOffsetY = webLoc[1] - rootLoc[1];
        int cx = webOffsetX + Math.round(selectionCenterX);
        int top = webOffsetY + Math.round(selectionTop);
        int bottom = webOffsetY + Math.round(selectionBottom);
        int x = Math.max(dp(10), Math.min(rootW - barW - dp(10), cx - barW / 2));
        int y = top - barH - dp(12);
        int safeTop = dp(68);
        int safeBottom = Math.max(safeTop, rootH - barH - dp(66));
        if (y < safeTop) y = bottom + dp(12);
        y = Math.max(safeTop, Math.min(safeBottom, y));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) selectionBar.getLayoutParams();
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.height = barH;
        lp.leftMargin = x;
        lp.topMargin = y;
        selectionBar.setLayoutParams(lp);
        selectionBar.setVisibility(View.VISIBLE);
        if (!wasVisible) {
            selectionBar.setAlpha(0f);
            selectionBar.setTranslationY(dp(3));
            selectionBar.animate().alpha(1f).translationY(0f).setDuration(105L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.35f)).start();
        } else {
            selectionBar.setAlpha(1f);
            selectionBar.setTranslationY(0f);
        }
    }

    private void positionSelectionBarFallback() {
        if (selectionBar == null || root == null) return;
        selectionBar.post(() -> {
            int rootW = Math.max(1, root.getWidth());
            selectionBar.measure(
                    View.MeasureSpec.makeMeasureSpec(Math.max(1, rootW - dp(20)), View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(dp(50), View.MeasureSpec.EXACTLY));
            int barW = Math.max(dp(280), selectionBar.getMeasuredWidth());
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) selectionBar.getLayoutParams();
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.height = dp(50);
            lp.leftMargin = Math.max(dp(10), (rootW - barW) / 2);
            lp.topMargin = Math.max(dp(74), root.getHeight() - dp(150));
            selectionBar.setLayoutParams(lp);
            selectionBar.setAlpha(1f);
            selectionBar.setTranslationY(0f);
            selectionBar.setVisibility(View.VISIBLE);
        });
    }

    private void hideSelectionBar() {
        if (root != null && hideNativeSelectionRunnable != null) root.removeCallbacks(hideNativeSelectionRunnable);
        hideNativeSelectionRunnable = null;
        if (selectionBar != null) {
            selectionBar.animate().cancel();
            selectionBar.setVisibility(View.GONE);
            selectionBar.setAlpha(1f);
            selectionBar.setTranslationY(0f);
        }
    }

    private void performSelectionAction(int action) {
        SelectionData data = currentSelection;
        if (data == null || data.text == null || data.text.trim().isEmpty()) {
            hideSelectionBar();
            return;
        }
        currentSelection = null;
        hideSelectionBar();
        clearWebSelection();
        if (action == SEL_HIGHLIGHT) showHighlightColorDialog(data);
        else if (action == SEL_NOTE) showNoteEditor(data);
        else if (action == SEL_TRANSLATE) showTranslateDialog(data.text);
        else if (action == SEL_COPY) copySelectedText(data.text);
    }

    private void setupPdfView(FrameLayout content) {
        pdfImage = new ImageView(this);
        pdfImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pdfImage.setBackgroundColor(Color.rgb(48, 49, 52));
        content.addView(pdfImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        pdfScaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        pdfScale *= detector.getScaleFactor();
                        pdfScale = Math.max(1f, Math.min(pdfScale, 4f));
                        pdfImage.setScaleX(pdfScale);
                        pdfImage.setScaleY(pdfScale);
                        return true;
                    }
                });

        pdfGestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDown(MotionEvent e) { return true; }

                    @Override public boolean onDoubleTap(MotionEvent e) {
                        if (pdfScale > 1.05f) {
                            resetPdfZoom();
                        } else {
                            pdfScale = 2f;
                            pdfImage.setPivotX(e.getX());
                            pdfImage.setPivotY(e.getY());
                            pdfImage.setScaleX(pdfScale);
                            pdfImage.setScaleY(pdfScale);
                        }
                        return true;
                    }

                    @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                        float r = e.getX() / Math.max(1f, pdfImage.getWidth());
                        if (pdfScale <= 1.05f && r < 0.24f) previous();
                        else if (pdfScale <= 1.05f && r > 0.76f) next();
                        else toggleControls();
                        return true;
                    }
                });

        pdfImage.setOnTouchListener((v, event) -> {
            pdfScaleDetector.onTouchEvent(event);
            pdfGestureDetector.onTouchEvent(event);

            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastTouchX = event.getX();
                lastTouchY = event.getY();
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE &&
                    pdfScale > 1.01f && !pdfScaleDetector.isInProgress()) {
                float dx = event.getX() - lastTouchX;
                float dy = event.getY() - lastTouchY;
                pdfImage.setTranslationX(pdfImage.getTranslationX() + dx);
                pdfImage.setTranslationY(pdfImage.getTranslationY() + dy);
                lastTouchX = event.getX();
                lastTouchY = event.getY();
            }
            return true;
        });
    }

    private void openEpub() {
        chapterLoading = true;
        new Thread(() -> {
            try {
                String id = Integer.toHexString((bookFile.getAbsolutePath() + ":" +
                        bookFile.lastModified() + ":" + bookFile.length()).hashCode());
                File extractDir = new File(getFilesDir(), "epub_cache/" + id);

                if (!new File(extractDir, ".ready").exists()) {
                    deleteRecursive(extractDir);
                    if (!extractDir.mkdirs() && !extractDir.exists())
                        throw new Exception("Cannot prepare EPUB folder");
                    unzipEpub(bookFile, extractDir);
                    new File(extractDir, ".ready").createNewFile();
                }

                EpubUtil.BookInfo info = EpubUtil.parseExtracted(extractDir);

                runOnUiThread(() -> {
                    spine.clear();
                    spine.addAll(info.spine);
                    chapterTitles.clear();
                    chapterTitles.addAll(info.chapterTitles);
                    tocSpineIndices.clear();
                    tocSpineIndices.addAll(info.tocSpineIndices);
                    tocTitles.clear();
                    tocTitles.addAll(info.tocTitles);
                    tocFragments.clear();
                    tocFragments.addAll(info.tocFragments);

                    if (info.title != null && !info.title.isEmpty() && !isGenericDisplayTitle(info.title))
                        titleView.setText(info.title);
                    else
                        titleView.setText(stripExtension(bookFile.getName()));

                    if (spine.isEmpty()) {
                        chapterLoading = false;
                        hideInitialReaderLoading();
                        Toast.makeText(this, "This EPUB has no readable chapters", Toast.LENGTH_LONG).show();
                        return;
                    }

                    currentSpine = Math.max(0, Math.min(
                            prefs.getInt("epub_chapter_" + bookFile.getName(), 0),
                            spine.size() - 1));
                    currentProgressPermille =
                            prefs.getInt("epub_scroll_" + bookFile.getName(), 0);
                    loadCurrentEpubChapter();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    chapterLoading = false;
                    hideInitialReaderLoading();
                    Toast.makeText(this, "EPUB error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    positionView.setText("Unable to open EPUB");
                });
            }
        }).start();
    }

    private void loadCurrentEpubChapter() {
        if (spine.isEmpty() || webView == null) return;
        if (chapterTransitionCapturePending) {
            chapterTransitionLoadDeferred = true;
            chapterLoading = true;
            pageTurnLocked = "page".equals(readingMode);
            return;
        }
        chapterTransitionLoadDeferred = false;

        if (activatePreloadedChapterIfReady()) return;
        if (preloadWebView != null && preloadedSpine == currentSpine && preloadLoading) {
            final int waitToken = preloadGeneration;
            chapterLoading = true;
            pageTurnLocked = "page".equals(readingMode);
            webView.postDelayed(() -> {
                if (waitToken != preloadGeneration) return;
                if (activatePreloadedChapterIfReady()) return;
                cancelChapterPreload();
                loadCurrentEpubChapter();
            }, 100L);
            return;
        }
        cancelChapterPreload();

        currentSelection = null;
        hideSelectionBar();
        final int loadGeneration = ++chapterLoadGeneration;
        chapterLoading = true;
        pageTurnLocked = "page".equals(readingMode);
        currentPageInChapter = 1;
        pageCountInChapter = 1;

        boolean firstOpen = readerLoadingOverlay != null &&
                readerLoadingOverlay.getVisibility() == View.VISIBLE &&
                (chapterTransitionOverlay == null || chapterTransitionOverlay.getVisibility() != View.VISIBLE);
        webView.animate().cancel();
        webView.setScaleX(1f);
        webView.setScaleY(1f);
        webView.setTranslationX(0f);
        // Never expose a newly loaded chapter until fonts + pagination settle.
        // The previous chapter snapshot (or the initial loading screen) stays visible.
        webView.setAlpha(0f);

        try {
            webView.loadUrl(Uri.fromFile(spine.get(currentSpine)).toString());
            updateEpubProgress(currentProgressPermille);
            updateBookmarkIcon();
        } catch (Exception e) {
            chapterLoading = false;
            pageTurnLocked = false;
            finishChapterFadeImmediate();
            Toast.makeText(this, "Cannot open chapter", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateChapter(int delta, boolean restoreEnd) {
        if (isPdf || spine.isEmpty() || chapterLoading) return;

        long now = System.currentTimeMillis();
        if (now - lastChapterNavMs < 420L) return;

        int target = currentSpine + delta;
        if (target < 0 || target >= spine.size()) {
            pageTurnLocked = false;
            return;
        }

        preferredPreloadDirection = delta < 0 ? -1 : 1;
        prepareChapterTransition(delta);
        lastChapterNavMs = now;
        currentSpine = target;
        currentProgressPermille = restoreEnd ? 1000 : 0;
        saveEpubStateOnly();
        loadCurrentEpubChapter();
    }

    private void showContents() {
        if (isPdf || spine.isEmpty()) return;

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        int panel = readerPanelBase();
        int text = readerPanelText();
        int sub = readerPanelSubText();
        int accent = readerAccent();
        int stroke = readerPanelStroke();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(12));
        card.setBackground(glassPanel(panel, dp(26), stroke));
        card.setElevation(dp(14));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView headerIcon = new TextView(this);
        headerIcon.setText("☷");
        headerIcon.setTextSize(22);
        headerIcon.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        headerIcon.setTextColor(accent);
        headerIcon.setGravity(Gravity.CENTER);
        headerIcon.setBackground(glassPanel(readerSelectedSurface(), dp(22), Color.TRANSPARENT));
        header.addView(headerIcon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        TextView title = new TextView(this);
        title.setText("Table of contents");
        title.setTextSize(23);
        title.setTextColor(text);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1f));

        View headerSpacer = new View(this);
        header.addView(headerSpacer, new LinearLayout.LayoutParams(dp(46), dp(46)));
        card.addView(header);

        LinearLayout divider = new LinearLayout(this);
        divider.setOrientation(LinearLayout.HORIZONTAL);
        divider.setGravity(Gravity.CENTER_VERTICAL);
        View leftLine = new View(this);
        leftLine.setBackgroundColor(stroke);
        divider.addView(leftLine, new LinearLayout.LayoutParams(0, dp(1), 1f));
        TextView sparkle = new TextView(this);
        sparkle.setText("✦");
        sparkle.setTextSize(13);
        sparkle.setTextColor(accent);
        sparkle.setGravity(Gravity.CENTER);
        divider.addView(sparkle, new LinearLayout.LayoutParams(dp(38), dp(24)));
        View rightLine = new View(this);
        rightLine.setBackgroundColor(stroke);
        divider.addView(rightLine, new LinearLayout.LayoutParams(0, dp(1), 1f));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28));
        dividerLp.topMargin = dp(2);
        dividerLp.bottomMargin = dp(4);
        card.addView(divider, dividerLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, dp(4));
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int entryCount = tocTitles.size();
        if (entryCount == 0) {
            TextView none = new TextView(this);
            none.setText("This EPUB does not contain a chapter table of contents.");
            none.setTextSize(15);
            none.setTextColor(sub);
            none.setGravity(Gravity.CENTER);
            none.setPadding(dp(16), dp(28), dp(16), dp(28));
            list.addView(none);
        }

        for (int i = 0; i < entryCount; i++) {
            final int entry = i;
            final int spineIndex = tocSpineAt(i);
            final String fragment = tocFragmentAt(i);
            boolean selected = spineIndex == currentSpine;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(5), dp(10), dp(5));
            row.setMinimumHeight(dp(62));
            if (selected) row.setBackground(glassPanel(readerSelectedSurface(), dp(17), accent));

            TextView marker = new TextView(this);
            marker.setText(selected ? "●" : "○");
            marker.setTextSize(selected ? 17 : 21);
            marker.setTextColor(selected ? accent : sub);
            marker.setGravity(Gravity.CENTER);
            row.addView(marker, new LinearLayout.LayoutParams(dp(42), dp(50)));

            TextView label = new TextView(this);
            label.setText(tocTitleAt(entry));
            label.setTextSize(16.5f);
            label.setTextColor(text);
            label.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            label.setLineSpacing(dp(1), 1.10f);
            label.setPadding(dp(6), dp(5), dp(4), dp(5));
            row.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            row.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN)
                    v.animate().scaleX(0.992f).scaleY(0.992f).setDuration(50L).start();
                else if (e.getActionMasked() == MotionEvent.ACTION_UP ||
                        e.getActionMasked() == MotionEvent.ACTION_CANCEL)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(95L).start();
                return false;
            });
            row.setOnClickListener(v -> {
                if (chapterLoading) return;
                if (spineIndex == currentSpine) {
                    pendingTocFragment = fragment;
                    jumpToPendingTocFragment(() -> {
                        saveEpubStateOnly();
                        updateBookmarkIcon();
                    });
                } else {
                    int direction = spineIndex > currentSpine ? 1 : -1;
                    preferredPreloadDirection = direction;
                    prepareChapterTransition(direction);
                    pendingTocFragment = fragment;
                    currentSpine = spineIndex;
                    currentProgressPermille = direction < 0 ? 1000 : 0;
                    saveEpubStateOnly();
                    loadCurrentEpubChapter();
                }
                dialog.dismiss();
            });

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = dp(3);
            list.addView(row, rowLp);

            if (i < entryCount - 1 && !selected) {
                View line = new View(this);
                line.setBackgroundColor(Color.argb(readerTheme == 2 ? 45 : 35,
                        Color.red(sub), Color.green(sub), Color.blue(sub)));
                LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                lineLp.leftMargin = dp(52);
                lineLp.rightMargin = dp(8);
                list.addView(line, lineLp);
            }
        }

        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView close = new TextView(this);
        close.setText("CLOSE");
        close.setTextSize(13.5f);
        close.setTextColor(accent);
        close.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        close.setGravity(Gravity.CENTER);
        close.setBackground(glassPanel(readerSoftSurface(), dp(18), stroke));
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(104), dp(44));
        closeLp.gravity = Gravity.END;
        closeLp.topMargin = dp(8);
        card.addView(close, closeLp);

        dialog.setContentView(card);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.46f);
            int sw = getResources().getDisplayMetrics().widthPixels;
            int sh = getResources().getDisplayMetrics().heightPixels;
            window.setLayout(Math.min(sw - dp(28), dp(560)),
                    Math.min(sh - dp(54), (int) (sh * 0.88f)));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                window.setBackgroundBlurRadius(dp(24));
            }
        }
    }

    private void applyReaderStyleSmooth(boolean restoreProgress) {
        if (webView == null || isPdf) {
            applyReaderStyle(restoreProgress);
            return;
        }
        if (chapterLoading || webView.getWidth() <= 0 || webView.getHeight() <= 0) {
            applyReaderStyle(restoreProgress);
            return;
        }

        final int token = ++readerStyleReflowToken;
        readerStyleReflowPending = true;
        if (readerStyleApplyRunnable != null) webView.removeCallbacks(readerStyleApplyRunnable);

        if (readerStyleOverlay != null && readerStyleOverlay.getVisibility() != View.VISIBLE) {
            Bitmap shot = captureWebViewBitmap();
            if (shot != null) {
                if (readerStyleBitmap != null && !readerStyleBitmap.isRecycled()) readerStyleBitmap.recycle();
                readerStyleBitmap = shot;
                readerStyleOverlay.animate().cancel();
                readerStyleOverlay.setImageBitmap(shot);
                readerStyleOverlay.setAlpha(1f);
                readerStyleOverlay.setVisibility(View.VISIBLE);
                readerStyleOverlay.bringToFront();
            }
        }

        if ("page".equals(readingMode)) pageTurnLocked = true;
        readerStyleApplyRunnable = () -> {
            if (token != readerStyleReflowToken || webView == null) return;
            readerStyleApplyRunnable = null;
            applyReaderStyle(restoreProgress, token);
        };
        webView.postDelayed(readerStyleApplyRunnable, 72L);
    }

    private void finishReaderStyleReflow(int token) {
        if (!readerStyleReflowPending || token != readerStyleReflowToken) return;
        readerStyleReflowPending = false;
        readerStyleApplyRunnable = null;
        if (!chapterLoading) pageTurnLocked = false;
        if (readerStyleOverlay == null || readerStyleOverlay.getVisibility() != View.VISIBLE) {
            if (readerStyleBitmap != null && !readerStyleBitmap.isRecycled()) readerStyleBitmap.recycle();
            readerStyleBitmap = null;
            return;
        }
        readerStyleOverlay.animate().cancel();
        readerStyleOverlay.animate().alpha(0f).setDuration(145L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f))
                .withEndAction(() -> {
                    if (readerStyleOverlay != null) {
                        readerStyleOverlay.setVisibility(View.GONE);
                        readerStyleOverlay.setImageDrawable(null);
                        readerStyleOverlay.setAlpha(1f);
                    }
                    if (readerStyleBitmap != null && !readerStyleBitmap.isRecycled()) readerStyleBitmap.recycle();
                    readerStyleBitmap = null;
                }).start();
    }

    private void applyReaderStyle(boolean restoreProgress) {
        applyReaderStyle(restoreProgress, 0);
    }

    private void applyReaderStyle(boolean restoreProgress, int styleToken) {
        if (webView == null) return;
        // WebView textZoom scales publisher px/pt/% sizes too. Body-only CSS scaling did
        // not affect many EPUBs in Scroll mode, so textZoom is the single font scale.
        try { webView.getSettings().setTextZoom(Math.max(80, Math.min(200, fontPercent))); }
        catch (Exception ignored) {}

        String bg = readerTheme == 2 ? "#121212" :
                readerTheme == 1 ? "#F4ECD8" : "#FFFFFF";
        String fg = readerTheme == 2 ? "#E8EAED" :
                readerTheme == 1 ? "#4A4033" : "#202124";
        String headingFg = readerTheme == 2 ? "#F1F3F4" :
                readerTheme == 1 ? "#3B3128" : fg;
        String link = readerTheme == 2 ? "#AECBFA" :
                readerTheme == 1 ? "#8A5A35" : "#1967D2";

        String familyCss = "";
        if ("pyidaungsu".equals(fontChoice))
            familyCss = "body,body *{font-family:'WoWPyidaungsu',sans-serif !important;}";
        else if ("yoeshin".equals(fontChoice))
            familyCss = "body,body *{font-family:'WoWYoeShin',sans-serif !important;}";
        else if ("burma2".equals(fontChoice))
            familyCss = "body,body *{font-family:'WoWBurma2',sans-serif !important;}";
        else if ("burma001".equals(fontChoice))
            familyCss = "body,body *{font-family:'WoWBurma001',sans-serif !important;}";
        else if ("pupu".equals(fontChoice))
            familyCss = "body,body *{font-family:'WoWPuPu',sans-serif !important;}";
        else if ("ayar".equals(fontChoice))
            familyCss = "body,body *{font-family:'WoWMyanmarAyar',sans-serif !important;}";
        else if ("phantee".equals(fontChoice))
            familyCss = "body,body *{font-family:'WoWPhantee',sans-serif !important;}";
        else if (fontChoice != null && fontChoice.startsWith("custom:")) {
            File customFont = ReaderFontStore.fileForChoice(this, fontChoice);
            if (customFont != null) {
                String customUrl = Uri.fromFile(customFont).toString().replace("'", "%27");
                familyCss = "@font-face{font-family:'WoWCustom';src:url('" + customUrl + "') format('" +
                        ReaderFontStore.cssFormat(customFont) + "');font-display:block;}" +
                        "body,body *{font-family:'WoWCustom',sans-serif !important;}";
            } else {
                fontChoice = "publisher";
            }
        }

        final int styleGeneration = chapterLoadGeneration;
        int restore = restoreProgress ? currentProgressPermille : -1;
        double ratio = restore >= 0 ? restore / 1000.0 : 0.0;
        double line = lineSpacing / 100.0;
        int safeMargin = Math.max(3, Math.min(14, marginPercent));

        String darkCss = readerTheme == 2
                ? "body,body p,body div,body span,body section,body article,body li,body dd,body dt,body blockquote,body td,body th,body figcaption{color:" + fg + " !important;}" +
                  "h1,h2,h3,h4,h5,h6,strong,b{color:" + headingFg + " !important;}"
                : readerTheme == 1
                ? "body,body p,body div,body span,body section,body article,body li,body dd,body dt,body blockquote,body td,body th,body figcaption{color:" + fg + " !important;}" +
                  "h1,h2,h3,h4,h5,h6,strong,b{color:" + headingFg + " !important;}" +
                  "a{color:" + link + " !important;}"
                : "";

        String commonCss =
                "@font-face{font-family:'WoWPyidaungsu';src:url('file:///android_asset/fonts/pyidaungsu.woff2') format('woff2');font-display:block;}" +
                "@font-face{font-family:'WoWYoeShin';src:url('file:///android_asset/fonts/yoeshin.woff2') format('woff2');font-display:block;}" +
                "@font-face{font-family:'WoWBurma2';src:url('file:///android_asset/fonts/burma2.woff2') format('woff2');font-display:block;}" +
                "@font-face{font-family:'WoWBurma001';src:url('file:///android_asset/fonts/burma001.ttf') format('truetype');font-display:block;}" +
                "@font-face{font-family:'WoWPuPu';src:url('file:///android_asset/fonts/m01_pupu_bold.ttf') format('truetype');font-display:block;}" +
                "@font-face{font-family:'WoWMyanmarAyar';src:url('file:///android_asset/fonts/myanmar_ayar_typewriter.ttf') format('truetype');font-display:block;}" +
                "@font-face{font-family:'WoWPhantee';src:url('file:///android_asset/fonts/phantee_hand_written.ttf') format('truetype');font-display:block;}" +
                "html,body{background:" + bg + " !important;color:" + fg + " !important;transform:none !important;zoom:1 !important;-webkit-text-size-adjust:100% !important;text-size-adjust:100% !important;}" +
                "a{color:" + link + " !important;}" +
                "pre{white-space:pre-wrap !important;overflow-wrap:anywhere !important;}" +
                ".wow-reader-block{line-height:" + line + " !important;letter-spacing:normal !important;}" +
                ".wow-reader-block *{line-height:inherit !important;}" +
                ".wow-align-justify{text-align:justify !important;text-align-last:start !important;}" +
                ".wow-align-left{text-align:left !important;text-align-last:auto !important;}" +
                ".wow-align-right{text-align:right !important;text-align-last:auto !important;}" +
                ".wow-mm-smart{text-justify:inter-character !important;word-spacing:0 !important;letter-spacing:normal !important;overflow-wrap:anywhere !important;word-break:normal !important;hyphens:none !important;}" +
                "h1,h2,h3,h4,h5,h6{break-after:avoid-column !important;page-break-after:avoid !important;}" +
                darkCss + familyCss;

        String typographyJs =
                "st.applyTypography=function(){try{" +
                "var align=" + jsQuote(textAlignment) + ",smart=" + (autoSpacingAdjustment ? "true" : "false") + ";" +
                "var rx=/[\\u1000-\\u109F\\uA9E0-\\uA9FF\\uAA60-\\uAA7F]/g;" +
                "var baseW=Math.max(1,(st.pageWidth||flow.clientWidth||window.innerWidth||1));" +
                "var wraps=flow.querySelectorAll('div,section,article,main,p,blockquote,dd,dt');" +
                "for(var wi=0;wi<wraps.length;wi++){var wn=wraps[wi],wt=(wn.textContent||'').replace(/\\s+/g,' ').trim();if(wt.length<120)continue;" +
                "var wcs=getComputedStyle(wn);if(wcs.display!=='block')continue;var wr=wn.getBoundingClientRect();" +
                "var par=wn.parentElement,pr=par?par.getBoundingClientRect():null,parWide=!pr||pr.width>=baseW*0.86;" +
                "if(parWide&&wr.width>0&&wr.width<baseW*0.90){wn.style.setProperty('width','auto','important');wn.style.setProperty('max-width','none','important');" +
                "wn.style.setProperty('min-width','0','important');wn.style.setProperty('box-sizing','border-box','important');wn.style.setProperty('margin-left','0','important');wn.style.setProperty('margin-right','0','important');}}" +
                "var blocks=flow.querySelectorAll('p,li,blockquote,dd,dt,div');" +
                "for(var i=0;i<blocks.length;i++){var n=blocks[i],txt=(n.textContent||'').trim();if(txt.length<8)continue;" +
                "if(n.tagName==='DIV'&&n.querySelector('p,div,li,blockquote,dd,dt'))continue;" +
                "var cs=getComputedStyle(n);if(cs.display==='none')continue;" +
                "var centered=(cs.textAlign==='center');if(centered&&txt.length<180)continue;" +
                "n.classList.add('wow-reader-block');n.classList.remove('wow-align-justify','wow-align-left','wow-align-right','wow-mm-smart');" +
                "n.classList.add(align==='right'?'wow-align-right':(align==='left'?'wow-align-left':'wow-align-justify'));" +
                "var mm=(txt.match(rx)||[]).length;var visible=txt.replace(/\\s/g,'').length;" +
                "if(align==='justify'&&smart&&visible>0&&mm/visible>0.18)n.classList.add('wow-mm-smart');" +
                "}" +
                "}catch(e){}};" +
                "st.preparePagination=function(){try{" +
                "var all=flow.querySelectorAll('*'),first=null,last=null;" +
                "var forced=function(v){v=(v||'').toLowerCase();return v==='always'||v==='page'||v==='left'||v==='right'||v==='column';};" +
                "var mediaSel='img,svg,video,audio,object,embed,table,math,canvas,hr';" +
                "for(var i=0;i<all.length;i++){var n=all[i],cs=getComputedStyle(n);if(cs.display==='none'||cs.visibility==='hidden')continue;" +
                "var txt=(n.textContent||'').replace(/\\s+/g,'');var hasMedia=(n.matches&&n.matches(mediaSel))||(n.querySelector&&n.querySelector(mediaSel));" +
                "var meaningful=txt.length>0||!!hasMedia;" +
                "var bb=cs.breakBefore||cs.pageBreakBefore,ba=cs.breakAfter||cs.pageBreakAfter;" +
                "if(!meaningful){if(forced(bb)){n.style.setProperty('break-before','auto','important');n.style.setProperty('page-break-before','auto','important');}" +
                "if(forced(ba)){n.style.setProperty('break-after','auto','important');n.style.setProperty('page-break-after','auto','important');}" +
                "if(!n.children.length){n.style.setProperty('min-height','0','important');n.style.setProperty('padding-top','0','important');n.style.setProperty('padding-bottom','0','important');}}" +
                "else{if(!first)first=n;last=n;if(forced(bb)){n.style.setProperty('break-before','column','important');n.style.setProperty('page-break-before','always','important');}" +
                "if(forced(ba)){n.style.setProperty('break-after','column','important');n.style.setProperty('page-break-after','always','important');}}}" +
                "if(first){first.style.setProperty('break-before','auto','important');first.style.setProperty('page-break-before','auto','important');}" +
                "if(last){last.style.setProperty('break-after','auto','important');last.style.setProperty('page-break-after','auto','important');}" +
                "}catch(e){}};";

        String css;
        String js;

        if ("page".equals(readingMode)) {
            css = commonCss +
                    "html,body{height:100% !important;width:100% !important;margin:0 !important;padding:0 !important;overflow:hidden !important;overscroll-behavior:none !important;}" +
                    "body{font-size:100% !important;line-height:" + line + " !important;max-width:none !important;}" +
                    "#wow-page-viewport{position:absolute !important;left:0 !important;top:0 !important;width:100vw !important;height:100vh !important;overflow:hidden !important;clip-path:inset(0) !important;contain:layout paint size !important;}" +
                    "#wow-page-flow{position:absolute !important;left:0 !important;top:0 !important;height:100vh !important;max-width:none !important;" +
                    "margin:0 !important;padding:4.2vh 0 5.2vh 0 !important;box-sizing:border-box !important;overflow:visible !important;" +
                    "column-fill:auto !important;will-change:transform !important;backface-visibility:hidden !important;transform-origin:0 0 !important;}" +
                    "#wow-page-flow p,#wow-page-flow li,#wow-page-flow blockquote,#wow-page-flow dd,#wow-page-flow dt{box-sizing:border-box !important;max-width:100% !important;}" +
                    "#wow-page-flow img,#wow-page-flow svg,#wow-page-flow video,#wow-page-flow table{max-width:100% !important;height:auto !important;}";

            js = "(function(){try{" +
                    "var style=document.getElementById('wow-reader-style');if(!style){style=document.createElement('style');style.id='wow-reader-style';document.head.appendChild(style);}style.innerHTML=" + jsQuote(css) + ";" +
                    "var viewport=document.getElementById('wow-page-viewport'),flow=document.getElementById('wow-page-flow');" +
                    "if(!viewport){viewport=document.createElement('div');viewport.id='wow-page-viewport';" +
                    "if(!flow){flow=document.createElement('div');flow.id='wow-page-flow';while(document.body.firstChild)flow.appendChild(document.body.firstChild);}" +
                    "viewport.appendChild(flow);document.body.appendChild(viewport);}else if(!flow){flow=document.createElement('div');flow.id='wow-page-flow';viewport.appendChild(flow);}" +
                    "var st=window.__wowPageEngine||{};window.__wowPageEngine=st;st.mode='page';st.locked=true;st.flow=flow;st.viewport=viewport;st.marginRatio=" + (safeMargin / 100.0) + ";" +
                    "st.clamp=function(v,a,b){return Math.max(a,Math.min(b,v));};" + typographyJs +
                    "st.layout=function(){var w=Math.max(1,viewport.clientWidth||window.innerWidth),m=Math.max(0,Math.round(w*st.marginRatio)),pw=Math.max(1,w-2*m),gap=Math.max(0,w-pw);st.step=w;st.marginPx=m;st.pageWidth=pw;st.gapPx=gap;flow.style.width=pw+'px';flow.style.minWidth=pw+'px';flow.style.columnWidth=pw+'px';flow.style.columnGap=gap+'px';};" +
                    "st.physical=function(){if(st.pageMap&&st.pageMap.length)return st.pageMap[st.clamp(st.page||0,0,st.pageMap.length-1)];return st.page||0;};" +
                    "st.apply=function(anim){st.layout();var physical=st.physical(),x=st.marginPx-physical*st.step;flow.style.transition=anim?'transform 155ms cubic-bezier(.2,.75,.25,1)':'none';flow.style.transform='translate3d('+x+'px,0,0)';};" +
                    "st.progress=function(){return (st.count||1)<=1?0:Math.round(((st.page||0)/((st.count||1)-1))*1000);};" +
                    "st.report=function(){WoW.onPage((st.page||0)+1,st.count||1,st.progress());};" +
                    "st.collectPageMap=function(){var used={},walker=document.createTreeWalker(flow,NodeFilter.SHOW_TEXT,null,false),n,range=document.createRange(),seen=0;" +
                    "var mark=function(r){if(!r||r.width<0.35||r.height<0.35)return;var a=Math.max(0,Math.floor((r.left-st.marginPx+1)/st.step));var b=Math.max(a,Math.floor((r.right-st.marginPx-1)/st.step));for(var k=a;k<=b;k++)used[k]=1;};" +
                    "while((n=walker.nextNode())&&seen<24000){var t=(n.nodeValue||'').replace(/\\s+/g,'');if(!t)continue;seen++;try{range.selectNodeContents(n);var rr=range.getClientRects();for(var j=0;j<rr.length;j++)mark(rr[j]);}catch(e){}}" +
                    "var media=flow.querySelectorAll('img,svg,video,audio,object,embed,table,math,canvas,hr');for(var i=0;i<media.length;i++){var r=media[i].getBoundingClientRect();mark(r);}" +
                    "var keys=Object.keys(used).map(function(x){return parseInt(x,10);}).filter(function(x){return isFinite(x)&&x>=0;}).sort(function(a,b){return a-b;});return keys;};" +
                    "st.nearestLogical=function(physical){if(!st.pageMap||!st.pageMap.length)return 0;var best=0,dist=1e9;for(var i=0;i<st.pageMap.length;i++){var d=Math.abs(st.pageMap[i]-physical);if(d<dist){dist=d;best=i;}}return best;};" +
                    "st.goToFragment=function(id){try{if(!id)return false;var el=document.getElementById(id);if(!el&&document.getElementsByName){var named=document.getElementsByName(id);if(named&&named.length)el=named[0];}if(!el)return false;" +
                    "var currentPhysical=st.physical(),r=el.getBoundingClientRect(),docX=(r.left-st.marginPx)+(currentPhysical*st.step),physical=Math.max(0,Math.floor((docX+2)/st.step));st.page=st.nearestLogical(physical);st.apply(false);st.report();return true;}catch(e){return false;}};" +
                    "st.paperTurn=function(d,done){st.apply(false);done();};" +
                    "st.measure=function(r){st.measureEpoch=(st.measureEpoch||0)+1;var epoch=st.measureEpoch,ratio=st.clamp(r,0,1),attempt=0;" +
                    "var run=function(){if(epoch!==st.measureEpoch)return;st.layout();st.page=0;st.pageMap=[0];flow.style.transition='none';flow.style.transform='translate3d('+st.marginPx+'px,0,0)';st.applyTypography();st.preparePagination();" +
                    "requestAnimationFrame(function(){requestAnimationFrame(function(){if(epoch!==st.measureEpoch)return;st.layout();var geom=(viewport.clientWidth||0)+'x'+(viewport.clientHeight||0)+'|'+Math.round(flow.scrollWidth||0);" +
                    "var map=st.collectPageMap();if(!map.length){st.count=0;st.locked=false;WoW.onEmptyChapter();return;}st.pageMap=map;st.count=map.length;st.page=st.clamp(Math.round((st.count-1)*ratio),0,st.count-1);st.apply(false);" +
                    "requestAnimationFrame(function(){if(epoch!==st.measureEpoch)return;st.layout();var geom2=(viewport.clientWidth||0)+'x'+(viewport.clientHeight||0)+'|'+Math.round(flow.scrollWidth||0);" +
                    "if(geom2!==geom&&attempt<1){attempt++;setTimeout(run,42);return;}st.locked=false;st.report();WoW.onPageReady(" + styleGeneration + ",st.page+1,st.count,st.progress());" +
                    (styleToken > 0 ? "WoW.onStyleReady(" + styleToken + ");" : "") +
                    "});});});};run();};" +
                    "st.turn=function(d){if(st.mode!=='page'||st.locked)return 'locked';if(d<0&&(st.page||0)<=0){st.locked=true;WoW.requestChapter(-1);return 'chapter';}if(d>0&&(st.page||0)>=(st.count||1)-1){st.locked=true;WoW.requestChapter(1);return 'chapter';}st.locked=true;st.page=st.clamp((st.page||0)+d,0,(st.count||1)-1);st.paperTurn(d,function(){st.report();st.locked=false;WoW.onPageTurnComplete(st.page+1,st.count,st.progress());});return 'page';};" +
                    "if(!st.resizeBound){st.resizeBound=true;window.addEventListener('resize',function(){if(st.mode!=='page')return;st.locked=true;clearTimeout(st.resizeTimer);st.resizeTimer=setTimeout(function(){var r=st.progress()/1000;st.measure(r);},220);});}" +
                    "var images=Array.prototype.slice.call(flow.querySelectorAll('img'));var waits=images.map(function(im){if(im.complete)return Promise.resolve();return new Promise(function(done){var f=function(){done();};im.addEventListener('load',f,{once:true});im.addEventListener('error',f,{once:true});});});" +
                    "var ready=function(){var all=Promise.all(waits);var timeout=new Promise(function(done){setTimeout(done,900);});Promise.race([all,timeout]).then(function(){st.measure(" + ratio + ");});};" +
                    "if(document.fonts&&document.fonts.ready)document.fonts.ready.then(ready);else ready();" +
                    "}catch(e){WoW.pageEngineFailed(String(e));}})();";
        } else {
            css = commonCss +
                    "html{overflow-x:hidden !important;overscroll-behavior:none !important;}" +
                    "body{font-size:100% !important;line-height:" + line + " !important;" +
                    "padding:5vh " + safeMargin + "vw 12vh " + safeMargin + "vw !important;" +
                    "height:auto !important;max-width:900px !important;margin:auto !important;box-sizing:border-box !important;" +
                    "column-width:auto !important;column-gap:normal !important;transform:none !important;transition:none !important;}" +
                    "body *{max-width:100%;}" +
                    "img,svg,video{max-width:100% !important;height:auto !important;}";

            js = "(function(){try{" +
                    "var viewport=document.getElementById('wow-page-viewport'),flow=document.getElementById('wow-page-flow');" +
                    "if(flow){var before=viewport||flow;while(flow.firstChild)document.body.insertBefore(flow.firstChild,before);if(viewport)viewport.remove();else flow.remove();}" +
                    "var style=document.getElementById('wow-reader-style');if(!style){style=document.createElement('style');style.id='wow-reader-style';document.head.appendChild(style);}style.innerHTML=" + jsQuote(css) + ";" +
                    "var flow=document.body;var st=window.__wowPageEngine||{};window.__wowPageEngine=st;st.mode='scroll';st.locked=false;" + typographyJs +
                    "st.applyTypography();" +
                    "st.goToFragment=function(id){try{if(!id)return false;var el=document.getElementById(id);if(!el&&document.getElementsByName){var named=document.getElementsByName(id);if(named&&named.length)el=named[0];}if(!el)return false;el.scrollIntoView({block:'start'});return true;}catch(e){return false;}};" +
                    "if(!window.__wowScrollBound){window.__wowScrollBound=true;var t=0;window.addEventListener('scroll',function(){if(window.__wowPageEngine&&window.__wowPageEngine.mode==='page')return;clearTimeout(t);t=setTimeout(function(){var h=Math.max(1,document.documentElement.scrollHeight-window.innerHeight);WoW.onScroll(Math.round((window.scrollY/h)*1000));},90);},{passive:true});}" +
                    "var finishWowStyle=function(){requestAnimationFrame(function(){requestAnimationFrame(function(){" +
                    (restore >= 0 ? "var h=Math.max(0,document.documentElement.scrollHeight-window.innerHeight);window.scrollTo(0,h*" + ratio + ");" : "") +
                    "WoW.onScrollReady(" + styleGeneration + ");" +
                    (styleToken > 0 ? "WoW.onStyleReady(" + styleToken + ");" : "") +
                    "});});};if(document.fonts&&document.fonts.ready)document.fonts.ready.then(finishWowStyle);else finishWowStyle();" +
                    "}catch(e){}})();";
        }

        try {
            webView.evaluateJavascript(js, null);
        } catch (Exception ignored) {
            if ("page".equals(readingMode)) {
                readingMode = "scroll";
                pageTurnLocked = false;
                chapterLoading = false;
                prefs.edit().putString("epub_reading_mode", "scroll").apply();
                Toast.makeText(this, "Page mode unavailable — switched to Scroll", Toast.LENGTH_SHORT).show();
            }
        }

        updateChromeTheme();
    }

    private int tocSpineAt(int entry) {
        if (entry >= 0 && entry < tocSpineIndices.size()) {
            return Math.max(0, Math.min(spine.size() - 1, tocSpineIndices.get(entry)));
        }
        return Math.max(0, Math.min(spine.size() - 1, entry));
    }

    private String tocTitleAt(int entry) {
        if (entry >= 0 && entry < tocTitles.size()) {
            String value = tocTitles.get(entry);
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return chapterDisplayTitle(tocSpineAt(entry));
    }

    private String tocFragmentAt(int entry) {
        if (entry >= 0 && entry < tocFragments.size()) {
            String value = tocFragments.get(entry);
            return value == null ? "" : value.trim();
        }
        return "";
    }

    private void jumpToPendingTocFragment(Runnable done) {
        String fragment = pendingTocFragment;
        pendingTocFragment = null;
        if (webView == null || fragment == null || fragment.isEmpty()) {
            if (done != null) done.run();
            return;
        }

        String script = "(window.__wowPageEngine&&window.__wowPageEngine.goToFragment)?window.__wowPageEngine.goToFragment(" +
                jsQuote(fragment) + "):false";
        try {
            webView.evaluateJavascript(script, result -> webView.postDelayed(() -> {
                if (done != null) done.run();
            }, 48L));
        } catch (Exception ignored) {
            if (done != null) done.run();
        }
    }

    private void completePageReady(int generation) {
        if (generation != chapterLoadGeneration || !chapterLoading) return;
        emptyChapterSkipCount = 0;
        jumpToPendingTocFragment(() -> {
            if (footnoteReturnPending) finishFootnoteReturnOnReady(footnoteReturnPage, footnoteReturnProgressPermille);
            if (searchNavigationActive && pendingSearchOccurrence >= 0) applyPendingSearchHit();
            if (paperGestureChapterBoundary && paperGestureReleased && paperGestureCommit) {
                finishInteractiveChapterBoundary();
                return;
            }
            if (finishPendingChapterCurl()) return;
            revealStableChapter();
        });
    }

    private void revealStableChapter() {
        // Never expose the new chapter's first WebView paint. Some EPUBs briefly
        // render a narrow/shifted column before the page engine finishes its final
        // viewport + typography pass. Keep the previous chapter snapshot visible
        // and the WebView hidden until two consecutive layout samples are stable.
        final int generation = chapterLoadGeneration;
        confirmStableChapterReveal(generation, 0, -1, -1);
    }

    private void confirmStableChapterReveal(int generation, int attempt, int previousWidth, int previousLeft) {
        if (webView == null || generation != chapterLoadGeneration || !chapterLoading) return;
        webView.setAlpha(0f);
        webView.setTranslationX(0f);
        webView.setScaleX(1f);
        webView.setScaleY(1f);

        final String probe = "(function(){try{" +
                "var root=document.getElementById('wow-page-flow')||document.body;" +
                "if(!root)return [-1,-1,-1];" +
                "var de=document.documentElement,b=document.body;" +
                "if(de){de.style.setProperty('zoom','1','important');de.style.setProperty('transform','none','important');}" +
                "if(b){b.style.setProperty('zoom','1','important');b.style.setProperty('transform','none','important');}" +
                "void root.offsetWidth;var r=root.getBoundingClientRect();" +
                "return [Math.round(r.width),Math.round(r.left),Math.round(window.innerWidth||0)];" +
                "}catch(e){return [-1,-1,-1];}})()";

        webView.evaluateJavascript(probe, value -> {
            if (generation != chapterLoadGeneration || !chapterLoading) return;
            int width = -1, left = -1, viewportCss = -1;
            try {
                String clean = value == null ? "" : value.replace("[", "").replace("]", "");
                String[] parts = clean.split(",");
                if (parts.length >= 3) {
                    width = Integer.parseInt(parts[0].trim());
                    left = Integer.parseInt(parts[1].trim());
                    viewportCss = Integer.parseInt(parts[2].trim());
                }
            } catch (Exception ignored) {}

            int tolerance = Math.max(2, Math.round(Math.max(1, viewportCss) * 0.01f));
            boolean saneWidth = width > 0 && viewportCss > 0 && width >= Math.round(viewportCss * 0.82f);
            boolean sameAsPrevious = previousWidth > 0 &&
                    Math.abs(width - previousWidth) <= tolerance &&
                    Math.abs(left - previousLeft) <= tolerance;

            // At least two matching, full-width CSS layout samples are required.
            // The previous chapter snapshot remains on top for the entire check,
            // so the transient narrow frame can never be exposed to the reader.
            if ((saneWidth && sameAsPrevious) || attempt >= 6) {
                webView.postOnAnimation(() -> webView.postOnAnimation(() -> {
                    if (generation != chapterLoadGeneration || !chapterLoading) return;
                    finishStableChapterReveal();
                }));
                return;
            }

            final int nextWidth = width;
            final int nextLeft = left;
            webView.postDelayed(() -> confirmStableChapterReveal(
                    generation, attempt + 1, nextWidth, nextLeft), 70L);
        });
    }

    private void finishStableChapterReveal() {
        if (webView != null) {
            webView.animate().cancel();
            webView.setScaleX(1f);
            webView.setScaleY(1f);
            webView.setTranslationX(0f);
            webView.setAlpha(1f);
        }
        hideInitialReaderLoading();
        pageTurnLocked = false;
        chapterLoading = false;
        pendingChapterCurlDirection = 0;
        if (pageCurlView != null && !pageCurlView.isBusy()) pageCurlView.release();
        finishChapterFadeImmediate();
        prewarmAdjacentChapters();
        scheduleAdjacentChapterPreload(preferredPreloadDirection);
    }

    private void prewarmAdjacentChapters() {
        if (spine.isEmpty()) return;
        final int here = currentSpine;
        new Thread(() -> {
            byte[] buffer = new byte[64 * 1024];
            int[] targets = {here - 1, here + 1};
            for (int idx : targets) {
                if (idx < 0 || idx >= spine.size()) continue;
                File f = spine.get(idx);
                try (InputStream in = new FileInputStream(f)) {
                    int left = 512 * 1024;
                    while (left > 0) {
                        int n = in.read(buffer, 0, Math.min(buffer.length, left));
                        if (n <= 0) break;
                        left -= n;
                    }
                } catch (Exception ignored) {}
            }
        }, "wow-chapter-prewarm").start();
    }

    private void forceChapterRepaginate(int generation) {
        if (webView == null || generation != chapterLoadGeneration || !chapterLoading || !"page".equals(readingMode)) return;
        try {
            webView.evaluateJavascript(
                    "(function(){var st=window.__wowPageEngine;if(!st||st.mode!=='page'||!st.measure)return false;st.locked=true;st.measure(st.progress()/1000);return true;})()",
                    null);
        } catch (Exception ignored) {}
    }


    private void skipEmptyEpubSpine() {
        if (spine.isEmpty()) return;
        emptyChapterSkipCount++;
        if (emptyChapterSkipCount > spine.size()) {
            chapterLoading = false;
            pageTurnLocked = false;
            pendingChapterCurlDirection = 0;
            if (pageCurlView != null) pageCurlView.release();
            finishChapterFade();
            return;
        }

        int direction = pendingChapterCurlDirection < 0 ? -1 : 1;
        int target = currentSpine + direction;
        if (target < 0 || target >= spine.size()) {
            direction = -direction;
            target = currentSpine + direction;
        }
        if (target < 0 || target >= spine.size()) {
            chapterLoading = false;
            pageTurnLocked = false;
            if (pageCurlView != null) pageCurlView.release();
            finishChapterFade();
            return;
        }

        currentSpine = target;
        currentProgressPermille = direction < 0 ? 1000 : 0;
        saveEpubStateOnly();
        loadCurrentEpubChapter();
    }


    private boolean handlePaperGesture(MotionEvent event) {
        if (event == null || webView == null || !"page".equals(readingMode) ||
                !"paper".equals(pageAnimation) || currentSelection != null) return false;

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            resetPaperGestureState();
            if (chapterLoading || pageTurnLocked || (pageCurlView != null && pageCurlView.isBusy())) return false;
            paperGestureCandidate = true;
            paperDownX = event.getX();
            paperDownY = event.getY();
            paperTouchY = webView.getHeight() <= 0 ? 0.5f :
                    Math.max(0f, Math.min(1f, event.getY() / (float) webView.getHeight()));
            pageVelocityTracker = VelocityTracker.obtain();
            pageVelocityTracker.addMovement(event);
            return false;
        }

        if (!paperGestureCandidate && !paperGestureActive) return false;
        if (pageVelocityTracker != null) pageVelocityTracker.addMovement(event);

        if (action == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - paperDownX;
            float dy = event.getY() - paperDownY;

            if (!paperGestureActive) {
                int earlySlop = Math.max(dp(4), Math.max(1, pageTouchSlop / 2));
                if (Math.abs(dx) < earlySlop) return false;
                if (Math.abs(dx) < Math.abs(dy) * 1.22f) {
                    resetPaperGestureState();
                    return false;
                }

                int direction = dx < 0f ? 1 : -1;
                int targetPage = currentPageInChapter + direction;
                cancelNativeSelectionForPaperGesture(event);

                if (targetPage < 1 || targetPage > pageCountInChapter) {
                    int targetSpine = currentSpine + direction;
                    if (targetSpine < 0 || targetSpine >= spine.size() ||
                            !beginInteractiveChapterBoundary(direction)) {
                        resetPaperGestureState();
                        return true;
                    }
                    paperGestureChapterBoundary = true;
                } else if (!beginInteractivePaperTurn(direction, targetPage - 1)) {
                    resetPaperGestureState();
                    return true;
                }
                paperGestureActive = true;
            }

            float width = Math.max(1f, webView.getWidth());
            paperProgress = Math.max(0f, Math.min(1f, Math.abs(dx) / (width * 0.90f)));
            paperTouchY = webView.getHeight() <= 0 ? 0.5f :
                    Math.max(0.07f, Math.min(0.93f, event.getY() / (float) webView.getHeight()));
            if (paperGestureReady && pageCurlView != null)
                pageCurlView.updateInteractive(paperProgress, paperTouchY);
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (!paperGestureActive) {
                resetPaperGestureState();
                return false;
            }

            float velocityX = 0f;
            if (pageVelocityTracker != null) {
                pageVelocityTracker.computeCurrentVelocity(1000);
                velocityX = pageVelocityTracker.getXVelocity();
            }
            paperReleaseVelocityX = velocityX;
            float width = Math.max(1f, webView.getWidth());
            float towardTurn = (-paperGestureDirection * velocityX) / width;
            float projected = paperProgress + towardTurn * 0.13f;
            boolean commit = action != MotionEvent.ACTION_CANCEL &&
                    (projected >= 0.23f || towardTurn > 0.32f);

            paperGestureCommit = commit;
            paperGestureReleased = true;
            recyclePageVelocityTracker();

            if (paperGestureChapterBoundary) {
                if (commit) commitInteractiveChapterBoundary();
                else cancelInteractiveChapterBoundary();
            } else if (paperGestureReady) {
                settlePaperGesture();
            }
            return true;
        }
        return paperGestureActive;
    }



    private boolean suppressingSelectionForPaperGesture() {
        return paperGestureActive || (paperGestureCandidate && pageTurnLocked && paperGestureDirection != 0);
    }

    private void cancelNativeSelectionForPaperGesture(MotionEvent source) {
        if (webView == null) return;
        currentSelection = null;
        hideSelectionBar();
        webView.cancelLongPress();
        webView.setLongClickable(false);
        clearWebSelection();
        try {
            MotionEvent cancel = MotionEvent.obtain(source);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            webView.onTouchEvent(cancel);
            cancel.recycle();
        } catch (Exception ignored) {}
    }

    private boolean beginInteractiveChapterBoundary(int direction) {
        if (pageCurlView == null || webView == null) return false;
        Bitmap current = captureWebViewBitmap();
        if (current == null) return false;
        Bitmap under;
        try {
            under = Bitmap.createBitmap(current.getWidth(), current.getHeight(), Bitmap.Config.ARGB_8888);
            under.eraseColor(readerTheme == 2 ? Color.rgb(18, 18, 18) :
                    (readerTheme == 1 ? Color.rgb(244, 236, 216) : Color.WHITE));
        } catch (Throwable e) {
            current.recycle();
            return false;
        }
        paperGestureDirection = direction < 0 ? -1 : 1;
        paperOriginalPageZero = Math.max(0, currentPageInChapter - 1);
        paperGestureReady = true;
        paperGestureReleased = false;
        paperGestureCommit = false;
        pageTurnLocked = true;
        lastPageTurnMs = System.currentTimeMillis();
        pageCurlView.hold(current);
        pageCurlView.beginInteractive(under, paperGestureDirection, paperProgress, paperTouchY);
        return true;
    }

    private void cancelInteractiveChapterBoundary() {
        if (pageCurlView == null) {
            pageTurnLocked = false;
            resetPaperGestureState();
            return;
        }
        pageCurlView.settleInteractive(false, paperReleaseVelocityX, () -> {
            pageCurlView.release();
            pageTurnLocked = false;
            resetPaperGestureState();
        });
    }

    private void commitInteractiveChapterBoundary() {
        int target = currentSpine + (paperGestureDirection < 0 ? -1 : 1);
        if (target < 0 || target >= spine.size()) {
            cancelInteractiveChapterBoundary();
            return;
        }
        pendingChapterCurlDirection = paperGestureDirection;
        chapterLoading = true;
        pageTurnLocked = true;
        currentSpine = target;
        currentProgressPermille = paperGestureDirection < 0 ? 1000 : 0;
        saveEpubStateOnly();
        loadCurrentEpubChapter();
    }

    private void finishInteractiveChapterBoundary() {
        if (pageCurlView == null) {
            pendingChapterCurlDirection = 0;
            chapterLoading = false;
            pageTurnLocked = false;
            resetPaperGestureState();
            return;
        }
        Bitmap target = captureWebViewBitmap();
        if (target == null) {
            pageCurlView.release();
            pendingChapterCurlDirection = 0;
            chapterLoading = false;
            pageTurnLocked = false;
            resetPaperGestureState();
            return;
        }
        pendingChapterCurlDirection = 0;
        pageCurlView.replaceTarget(target);
        pageCurlView.settleInteractive(true, paperReleaseVelocityX, () -> {
            chapterLoading = false;
            finishNativePageCurl();
        });
    }

    private void tintChromeChildren(ViewGroup group, int color) {
        if (group == null) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) ((TextView) child).setTextColor(color);
            else if (child instanceof ViewGroup) tintChromeChildren((ViewGroup) child, color);
        }
    }

    private boolean beginInteractivePaperTurn(int direction, int targetZeroBased) {
        if (pageCurlView == null || webView == null) return false;
        Bitmap current = captureWebViewBitmap();
        if (current == null) return false;

        paperGestureDirection = direction < 0 ? -1 : 1;
        paperOriginalPageZero = Math.max(0, currentPageInChapter - 1);
        paperTargetPageZero = Math.max(0, targetZeroBased);
        paperGestureReady = false;
        paperGestureReleased = false;
        paperGestureCommit = false;
        paperProgress = 0f;
        pageTurnLocked = true;
        lastPageTurnMs = System.currentTimeMillis();
        pageCurlView.hold(current);

        String jump = "(function(){var st=window.__wowPageEngine;if(!st||st.mode!=='page')return 'unavailable';" +
                "st.locked=true;st.page=st.clamp(" + paperTargetPageZero +
                ",0,(st.count||1)-1);st.apply(false);return 'ok';})()";
        try {
            webView.evaluateJavascript(jump, result -> {
                if (result == null || result.contains("unavailable")) {
                    if (pageCurlView != null) pageCurlView.release();
                    pageTurnLocked = false;
                    resetPaperGestureState();
                    return;
                }

                webView.postOnAnimation(() -> webView.postOnAnimation(() -> {
                    if (!paperGestureActive && !paperGestureReleased) {
                        restorePaperOriginalPage();
                        return;
                    }
                    Bitmap target = captureWebViewBitmap();
                    if (target == null || pageCurlView == null) {
                        restorePaperOriginalPage();
                        return;
                    }

                    pageCurlView.beginInteractive(target, paperGestureDirection,
                            paperProgress, paperTouchY);
                    paperGestureReady = true;
                    if (paperGestureReleased) settlePaperGesture();
                }));
            });
            return true;
        } catch (Exception e) {
            if (pageCurlView != null) pageCurlView.release();
            pageTurnLocked = false;
            return false;
        }
    }

    private void settlePaperGesture() {
        if (!paperGestureReady || pageCurlView == null) return;
        paperGestureReady = false;
        boolean commit = paperGestureCommit;
        float velocityX = paperReleaseVelocityX;

        pageCurlView.settleInteractive(commit, velocityX, () -> {
            if (commit) {
                finishNativePageCurl();
            } else {
                restorePaperOriginalPage();
            }
        });
    }

    private void restorePaperOriginalPage() {
        if (webView == null) {
            if (pageCurlView != null) pageCurlView.release();
            pageTurnLocked = false;
            resetPaperGestureState();
            return;
        }

        String restore = "(function(){var st=window.__wowPageEngine;if(!st)return;" +
                "st.page=st.clamp(" + paperOriginalPageZero +
                ",0,(st.count||1)-1);st.apply(false);st.locked=false;})()";
        try {
            webView.evaluateJavascript(restore, result -> webView.postOnAnimation(() -> {
                if (pageCurlView != null) pageCurlView.release();
                pageTurnLocked = false;
                resetPaperGestureState();
            }));
        } catch (Exception e) {
            if (pageCurlView != null) pageCurlView.release();
            pageTurnLocked = false;
            resetPaperGestureState();
        }
    }

    private void recyclePageVelocityTracker() {
        if (pageVelocityTracker != null) {
            pageVelocityTracker.recycle();
            pageVelocityTracker = null;
        }
    }

    private void resetPaperGestureState() {
        recyclePageVelocityTracker();
        paperGestureCandidate = false;
        paperGestureActive = false;
        paperGestureReady = false;
        paperGestureReleased = false;
        paperGestureCommit = false;
        paperGestureChapterBoundary = false;
        paperGestureDirection = 0;
        paperProgress = 0f;
        paperReleaseVelocityX = 0f;
        paperTouchY = 0.5f;
        if (webView != null) webView.setLongClickable(true);
    }


    private void turnPageFromTap(int delta, float tapY) {
        if (android.os.SystemClock.uptimeMillis() < footnoteTapSuppressUntilMs) return;
        if (webView == null || chapterLoading || !"page".equals(readingMode) || delta == 0) return;
        long now = System.currentTimeMillis();
        if (pageTurnLocked || now - lastPageTurnMs < 135L) return;

        lastPageTurnMs = now;
        int direction = delta < 0 ? -1 : 1;
        int targetPage = currentPageInChapter + direction;
        boolean insideChapter = targetPage >= 1 && targetPage <= pageCountInChapter;
        if (!insideChapter) {
            navigateChapter(direction, direction < 0);
            return;
        }
        if ("slide".equals(pageAnimation)) startNativeSlidePageTurn(direction, targetPage - 1);
        else performJsPageTurn(direction);
    }

    private void startNativeTapCurl(int direction, int targetZeroBased, float touchY) {
        Bitmap current = captureWebViewBitmap();
        if (current == null || pageCurlView == null) {
            performJsPageTurn(direction);
            return;
        }
        pageTurnLocked = true;
        pageCurlView.hold(current);
        String jump = "(function(){var st=window.__wowPageEngine;if(!st||st.mode!=='page')return 'unavailable';" +
                "st.locked=true;st.page=st.clamp(" + targetZeroBased + ",0,(st.count||1)-1);st.apply(false);return 'ok';})()";
        try {
            webView.evaluateJavascript(jump, result -> {
                if (result == null || result.contains("unavailable")) {
                    pageCurlView.release();
                    pageTurnLocked = false;
                    performJsPageTurn(direction);
                    return;
                }
                webView.postOnAnimation(() -> webView.postOnAnimation(() -> {
                    Bitmap target = captureWebViewBitmap();
                    if (target == null || pageCurlView == null) {
                        if (pageCurlView != null) pageCurlView.release();
                        finishNativePageCurl();
                        return;
                    }
                    pageCurlView.startTapCurl(target, direction, touchY, this::finishNativePageCurl);
                }));
            });
        } catch (Exception e) {
            if (pageCurlView != null) pageCurlView.release();
            pageTurnLocked = false;
            performJsPageTurn(direction);
        }
    }

    private void turnPage(int delta) {
        if (webView == null || chapterLoading || !"page".equals(readingMode) || delta == 0) return;
        long now = System.currentTimeMillis();
        if (pageTurnLocked || now - lastPageTurnMs < 220L) return;

        lastPageTurnMs = now;
        int direction = delta < 0 ? -1 : 1;
        int targetPage = currentPageInChapter + direction;
        boolean insideChapter = targetPage >= 1 && targetPage <= pageCountInChapter;

        if (!insideChapter) {
            navigateChapter(direction, direction < 0);
            return;
        }
        if ("slide".equals(pageAnimation)) startNativeSlidePageTurn(direction, targetPage - 1);
        else performJsPageTurn(direction);
    }


    private void startNativeSlidePageTurn(int direction, int targetZeroBased) {
        if (webView == null || pageSlideOverlay == null) { performJsPageTurn(direction); return; }
        Bitmap current = captureWebViewBitmap();
        if (current == null) { performJsPageTurn(direction); return; }
        if (pageSlideBitmap != null && !pageSlideBitmap.isRecycled()) pageSlideBitmap.recycle();
        pageSlideBitmap = current;
        pageTurnLocked = true;
        pageSlideOverlay.animate().cancel();
        pageSlideOverlay.setImageBitmap(current);
        pageSlideOverlay.setAlpha(1f);
        pageSlideOverlay.setTranslationX(0f);
        pageSlideOverlay.setVisibility(View.VISIBLE);
        pageSlideOverlay.bringToFront();

        String jump = "(function(){var st=window.__wowPageEngine;if(!st||st.mode!=='page')return 'unavailable';" +
                "st.locked=true;st.page=st.clamp(" + targetZeroBased + ",0,(st.count||1)-1);st.apply(false);return 'ok';})()";
        try {
            webView.evaluateJavascript(jump, result -> {
                if (result == null || result.contains("unavailable")) {
                    finishNativeSlidePageTurn(false);
                    performJsPageTurn(direction);
                    return;
                }
                webView.postOnAnimation(() -> {
                    float distance = Math.max(1f, webView.getWidth());
                    webView.animate().cancel();
                    webView.setTranslationX(direction > 0 ? distance * 0.055f : -distance * 0.055f);
                    webView.setAlpha(0.92f);
                    webView.animate().translationX(0f).alpha(1f).setDuration(205L)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator(1.45f)).start();
                    pageSlideOverlay.animate().translationX(direction > 0 ? -distance : distance).alpha(0.18f)
                            .setDuration(215L).setInterpolator(new android.view.animation.DecelerateInterpolator(1.28f))
                            .withEndAction(() -> finishNativeSlidePageTurn(true)).start();
                });
            });
        } catch (Exception e) {
            finishNativeSlidePageTurn(false);
            performJsPageTurn(direction);
        }
    }

    private void finishNativeSlidePageTurn(boolean report) {
        if (pageSlideOverlay != null) {
            pageSlideOverlay.animate().cancel();
            pageSlideOverlay.setVisibility(View.GONE);
            pageSlideOverlay.setImageDrawable(null);
            pageSlideOverlay.setAlpha(1f);
            pageSlideOverlay.setTranslationX(0f);
        }
        if (pageSlideBitmap != null && !pageSlideBitmap.isRecycled()) pageSlideBitmap.recycle();
        pageSlideBitmap = null;
        if (webView != null) {
            webView.animate().cancel();
            webView.setTranslationX(0f);
            webView.setAlpha(1f);
            try {
                webView.evaluateJavascript(report
                        ? "(function(){var st=window.__wowPageEngine;if(!st)return;st.locked=false;st.report();WoW.onPageTurnComplete((st.page||0)+1,st.count||1,st.progress());})()"
                        : "if(window.__wowPageEngine)window.__wowPageEngine.locked=false", null);
            } catch (Exception ignored) {}
        }
        pageTurnLocked = false;
    }

    private void startNativePageCurl(int direction, int targetZeroBased) {
        Bitmap current = captureWebViewBitmap();
        if (current == null || pageCurlView == null) {
            performJsPageTurn(direction);
            return;
        }

        pageTurnLocked = true;
        pageCurlView.hold(current);
        String jump = "(function(){var st=window.__wowPageEngine;if(!st||st.mode!=='page')return 'unavailable';" +
                "st.locked=true;st.page=st.clamp(" + targetZeroBased + ",0,(st.count||1)-1);st.apply(false);return 'ok';})()";
        try {
            webView.evaluateJavascript(jump, result -> {
                if (result == null || result.contains("unavailable")) {
                    if (pageCurlView != null) pageCurlView.release();
                    pageTurnLocked = false;
                    performJsPageTurn(direction);
                    return;
                }
                webView.postDelayed(() -> {
                    Bitmap target = captureWebViewBitmap();
                    if (target == null || pageCurlView == null) {
                        if (pageCurlView != null) pageCurlView.release();
                        finishNativePageCurl();
                        return;
                    }
                    pageCurlView.startCurl(target, direction, this::finishNativePageCurl);
                }, 48L);
            });
        } catch (Exception e) {
            if (pageCurlView != null) pageCurlView.release();
            pageTurnLocked = false;
            performJsPageTurn(direction);
        }
    }

    private void finishNativePageCurl() {
        try {
            webView.evaluateJavascript(
                    "(function(){var st=window.__wowPageEngine;if(!st)return;st.locked=false;st.report();WoW.onPageTurnComplete((st.page||0)+1,st.count||1,st.progress());})()",
                    null);
        } catch (Exception ignored) {}
        pageTurnLocked = false;
        resetPaperGestureState();
    }

    private void performJsPageTurn(int delta) {
        if (webView == null) return;
        pageTurnLocked = true;
        try {
            webView.evaluateJavascript(
                    "(window.__wowPageEngine&&window.__wowPageEngine.turn)?window.__wowPageEngine.turn(" + delta + "): 'unavailable'",
                    result -> {
                        if (result != null && result.contains("unavailable")) {
                            pageTurnLocked = false;
                            readingMode = "scroll";
                            prefs.edit().putString("epub_reading_mode", "scroll").apply();
                            applyReaderStyle(true);
                            Toast.makeText(this, "Page mode unavailable — switched to Scroll", Toast.LENGTH_SHORT).show();
                        }
                    });
        } catch (Exception e) {
            pageTurnLocked = false;
        }

        webView.postDelayed(() -> {
            if (pageTurnLocked && !chapterLoading && (pageCurlView == null || !pageCurlView.isBusy())) {
                pageTurnLocked = false;
                try { webView.evaluateJavascript("if(window.__wowPageEngine)window.__wowPageEngine.locked=false", null); }
                catch (Exception ignored) {}
            }
        }, 750L);
    }

    private Bitmap captureWebViewBitmap() {
        if (webView == null || webView.getWidth() <= 0 || webView.getHeight() <= 0) return null;
        try {
            Bitmap bitmap = Bitmap.createBitmap(webView.getWidth(), webView.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            webView.draw(canvas);
            return bitmap;
        } catch (OutOfMemoryError | RuntimeException e) {
            return null;
        }
    }

    private void prepareChapterTransition(int direction) {
        if (webView == null || webView.getUrl() == null || chapterTransitionOverlay == null) return;
        pendingChapterDirection = direction < 0 ? -1 : 1;
        pendingChapterCurlDirection = 0;
        chapterTransitionLoadDeferred = false;

        // WebView.draw(Canvas) is a software render and can use a different internal
        // page scale from the hardware-composited frame visible on screen. That was
        // the source of the outgoing chapter suddenly shrinking before navigation.
        // PixelCopy copies the already-composited window pixels instead, so the old
        // chapter is frozen at the exact size the reader was looking at.
        if (Build.VERSION.SDK_INT >= 26 && webView.getWidth() > 0 && webView.getHeight() > 0) {
            final int width = webView.getWidth();
            final int height = webView.getHeight();
            final Bitmap shot;
            try {
                shot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError | RuntimeException e) {
                return;
            }

            int[] location = new int[2];
            webView.getLocationInWindow(location);
            android.graphics.Rect src = new android.graphics.Rect(
                    location[0], location[1], location[0] + width, location[1] + height);
            final int token = ++chapterTransitionCaptureToken;
            chapterTransitionCapturePending = true;

            try {
                android.view.PixelCopy.request(getWindow(), src, shot, result -> {
                    if (token != chapterTransitionCaptureToken || isFinishing()) {
                        if (!shot.isRecycled()) shot.recycle();
                        return;
                    }
                    chapterTransitionCapturePending = false;
                    if (result != android.view.PixelCopy.SUCCESS) {
                        // A rare compositor miss is preferable to reintroducing the
                        // wrong-scale software WebView snapshot. Use a stable reader
                        // background for that transition instead of a shrunken page.
                        shot.eraseColor(readerTheme == 2 ? Color.rgb(18, 18, 18) :
                                (readerTheme == 1 ? Color.rgb(244, 236, 216) : Color.WHITE));
                    }
                    installChapterTransitionSnapshot(shot);
                    if (chapterTransitionLoadDeferred) {
                        chapterTransitionLoadDeferred = false;
                        loadCurrentEpubChapter();
                    }
                }, new android.os.Handler(android.os.Looper.getMainLooper()));
                return;
            } catch (RuntimeException e) {
                chapterTransitionCapturePending = false;
                if (!shot.isRecycled()) shot.recycle();
            }
        }

        // Android 6/7 fallback. Modern devices never use this software path.
        Bitmap fallback = captureWebViewBitmap();
        if (fallback != null) installChapterTransitionSnapshot(fallback);
    }

    private void installChapterTransitionSnapshot(Bitmap shot) {
        if (shot == null || chapterTransitionOverlay == null) return;
        if (chapterTransitionBitmap != null && chapterTransitionBitmap != shot &&
                !chapterTransitionBitmap.isRecycled()) chapterTransitionBitmap.recycle();
        chapterTransitionBitmap = shot;
        chapterTransitionOverlay.animate().cancel();
        chapterTransitionOverlay.setImageBitmap(shot);
        chapterTransitionOverlay.setAlpha(1f);
        chapterTransitionOverlay.setTranslationX(0f);
        chapterTransitionOverlay.setScaleX(1f);
        chapterTransitionOverlay.setScaleY(1f);
        chapterTransitionOverlay.setVisibility(View.VISIBLE);
        chapterTransitionOverlay.bringToFront();
        webView.animate().cancel();
        webView.setScaleX(1f);
        webView.setScaleY(1f);
        webView.setTranslationX(0f);
        webView.setAlpha(0f);
        pendingChapterFade = true;
    }

    private boolean finishPendingChapterCurl() {
        if (pendingChapterCurlDirection == 0 || pageCurlView == null) return false;
        int direction = pendingChapterCurlDirection;
        pendingChapterCurlDirection = 0;
        Bitmap target = captureWebViewBitmap();
        if (target == null) {
            pageCurlView.release();
            return false;
        }
        pageCurlView.startCurl(target, direction, () -> {
            chapterLoading = false;
            pageTurnLocked = false;
        });
        return true;
    }

    private void finishChapterFade() {
        // The V31 screenshot remains only while the new chapter stabilizes.
        // Once ready, remove it immediately: no fade, slide or translation.
        finishChapterFadeImmediate();
    }

    private void finishChapterFadeImmediate() {
        pendingChapterFade = false;
        pendingChapterDirection = 0;
        if (chapterTransitionOverlay != null) {
            chapterTransitionOverlay.animate().cancel();
            chapterTransitionOverlay.setVisibility(View.GONE);
            chapterTransitionOverlay.setImageDrawable(null);
            chapterTransitionOverlay.setAlpha(1f);
            chapterTransitionOverlay.setTranslationX(0f);
        }
        if (chapterTransitionBitmap != null && !chapterTransitionBitmap.isRecycled())
            chapterTransitionBitmap.recycle();
        chapterTransitionBitmap = null;
        if (webView != null) {
            webView.setAlpha(1f);
            webView.setTranslationX(0f);
        }
    }

    private String chapterDisplayTitle(int index) {
        String value = index >= 0 && index < chapterTitles.size() ? chapterTitles.get(index) : null;
        if (isGenericDisplayTitle(value)) return "Chapter " + (index + 1);
        return value.trim();
    }

    private boolean isGenericDisplayTitle(String value) {
        if (value == null) return true;
        String low = value.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ");
        if (low.isEmpty() || low.equals("unknown") || low.equals("untitled") || low.equals("undefined") ||
                low.equals("null") || low.equals("none") || low.equals("n/a") || low.equals("no title")) return true;
        return low.matches("^(chapter|section|part|page|text|content|item|file)\\s*$");
    }


    private int readerPanelBase() {
        if (readerTheme == 2) return Color.rgb(28, 29, 33);
        if (readerTheme == 1) return Color.rgb(249, 243, 226);
        return Color.rgb(253, 253, 255);
    }

    private int readerPanelText() {
        return readerTheme == 2 ? Color.rgb(240, 242, 247)
                : readerTheme == 1 ? Color.rgb(66, 54, 40) : Color.rgb(31, 33, 39);
    }

    private int readerPanelSubText() {
        return readerTheme == 2 ? Color.rgb(181, 186, 197)
                : readerTheme == 1 ? Color.rgb(126, 105, 78) : Color.rgb(101, 106, 118);
    }

    private int readerAccent() {
        return readerTheme == 2 ? Color.rgb(142, 163, 255)
                : readerTheme == 1 ? Color.rgb(164, 111, 67) : Color.rgb(103, 80, 190);
    }

    private int readerPanelStroke() {
        return readerTheme == 2 ? Color.rgb(68, 72, 82)
                : readerTheme == 1 ? Color.rgb(222, 205, 172) : Color.rgb(225, 225, 234);
    }

    private int readerSoftSurface() {
        return readerTheme == 2 ? Color.rgb(40, 42, 48)
                : readerTheme == 1 ? Color.rgb(245, 236, 216) : Color.rgb(250, 250, 253);
    }

    private int readerSelectedSurface() {
        return readerTheme == 2 ? Color.rgb(60, 57, 86)
                : readerTheme == 1 ? Color.rgb(243, 229, 206) : Color.rgb(244, 240, 255);
    }

    private void refreshSelectionBarTheme() {
        if (selectionBar == null) return;
        selectionBar.setBackground(glassPanel(readerPanelBase(), dp(20), readerPanelStroke()));
        selectionBar.removeAllViews();
        selectionBar.addView(selectionActionButton("Highlight", SEL_HIGHLIGHT));
        selectionBar.addView(selectionActionButton("Note", SEL_NOTE));
        selectionBar.addView(selectionActionButton("Translate", SEL_TRANSLATE));
        selectionBar.addView(selectionActionButton("Copy", SEL_COPY));
    }

    private String decoratedSheetChipLabel(String label) {
        if ("Light".equals(label)) return "☀  Light";
        if ("Sepia".equals(label)) return "☕  Sepia";
        if ("Dark".equals(label)) return "☾  Dark";
        if ("Off".equals(label)) return "⏻  Off";
        if ("Auto".equals(label)) return "Ⓐ  Auto";
        if ("On".equals(label)) return "◉  On";
        if ("Justify".equals(label)) return "≡  Justify";
        if ("Left".equals(label)) return "≡  Left";
        if ("Right".equals(label)) return "≡  Right";
        if ("Narrow".equals(label)) return "▯  Narrow";
        if ("Normal".equals(label)) return "▣  Normal";
        if ("Wide".equals(label)) return "▯  Wide";
        if ("Pages".equals(label)) return "▤  Pages";
        if ("Scroll".equals(label)) return "↕  Scroll";
        if ("None".equals(label)) return "○  None";
        if ("Slide".equals(label)) return "⇆  Slide";
        if (label != null && label.startsWith("Font · ")) return "Aa  " + label;
        if ("More reader settings".equals(label)) return "☰  More reader settings   ›";
        return label;
    }

    private String decoratedSheetSection(String label) {
        if ("Theme".equals(label)) return "◉   Theme";
        if ("Night Light".equals(label)) return "☾   Night Light";
        if ("Text".equals(label)) return "Tᵀ   Text";
        if ("Line height".equals(label)) return "↕   Line height";
        if ("Alignment".equals(label)) return "≡   Alignment";
        if ("Margins".equals(label)) return "▣   Margins";
        if ("Reading".equals(label)) return "▤   Reading";
        if ("Page animation".equals(label)) return "⇆   Page animation";
        if ("Reading brightness".equals(label)) return "☀   Reading brightness";
        return label;
    }

    private GradientDrawable glassPanel(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (Color.alpha(stroke) > 0) d.setStroke(Math.max(1, dp(1)), stroke);
        return d;
    }

    private String jsQuote(String value) {
        if (value == null) return "''";
        return "'" + value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", " ")
                .replace("\n", " ") + "'";
    }

    private void showReaderSettings() {
        if (isPdf) {
            showPdfSettings();
            return;
        }
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        int panel = readerTheme == 2 ? Color.rgb(28, 29, 32) :
                readerTheme == 1 ? Color.rgb(249, 243, 226) : Color.rgb(250, 250, 252);
        int text = readerTheme == 2 ? Color.rgb(241, 243, 247) : Color.rgb(35, 37, 43);
        int sub = readerTheme == 2 ? Color.rgb(184, 188, 196) : Color.rgb(103, 108, 119);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(12), dp(18), dp(20));
        card.setBackground(glassPanel(Color.argb(253, Color.red(panel), Color.green(panel), Color.blue(panel)),
                dp(28), readerPanelStroke()));
        card.setElevation(dp(14));
        scroll.addView(card, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View dragHandle = new View(this);
        dragHandle.setBackground(glassPanel(readerPanelStroke(), dp(3), Color.TRANSPARENT));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(38), dp(5));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(7);
        card.addView(dragHandle, handleLp);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView headerIcon = new TextView(this);
        headerIcon.setText("☷");
        headerIcon.setTextSize(21);
        headerIcon.setTextColor(readerAccent());
        headerIcon.setGravity(Gravity.CENTER);
        headerIcon.setBackground(glassPanel(readerSelectedSurface(), dp(20), Color.TRANSPARENT));
        header.addView(headerIcon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView title = new TextView(this);
        title.setText("Display options");
        title.setTextSize(22);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setTextColor(text);
        LinearLayout.LayoutParams titleHeaderLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        titleHeaderLp.leftMargin = dp(10);
        header.addView(title, titleHeaderLp);
        TextView close = sheetChip("×", false);
        close.setTextSize(24);
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(42)));
        card.addView(header);

        addSheetLabel(card, "Theme", sub);
        LinearLayout themeRow = sheetRow();
        TextView[] themeChips = {sheetChip("Light", readerTheme == 0), sheetChip("Sepia", readerTheme == 1), sheetChip("Dark", readerTheme == 2)};
        for (int i = 0; i < themeChips.length; i++) {
            final int value = i;
            themeChips[i].setOnClickListener(v -> {
                readerTheme = value;
                saveReaderPreferences();
                applyReaderStyle(true);
                updateChromeTheme();
                refreshSelectionBarTheme();
                dialog.dismiss();
                showReaderSettings();
            });
            themeRow.addView(themeChips[i], sheetChipLp(i > 0));
        }
        card.addView(themeRow);

        addSheetLabel(card, "Night Light", sub);
        LinearLayout nightRow = sheetRow();
        String[] nightLabels = {"Off", "Auto", "On"};
        String[] nightValues = {"off", "auto", "on"};
        TextView[] nightChips = new TextView[3];
        for (int i = 0; i < 3; i++) {
            nightChips[i] = sheetChip(nightLabels[i], nightValues[i].equals(nightLightMode));
            final int idx = i;
            nightChips[i].setOnClickListener(v -> {
                nightLightMode = nightValues[idx];
                saveReaderPreferences();
                updateNightLightOverlay();
                selectSheetChip(nightChips, idx);
            });
            nightRow.addView(nightChips[i], sheetChipLp(i > 0));
        }
        card.addView(nightRow);

        addSheetLabel(card, "Text", sub);
        LinearLayout fontSizeRow = sheetRow();
        TextView minusFont = sheetChip("A−", false);
        TextView fontValue = sheetChip(fontPercent + "%", true);
        TextView plusFont = sheetChip("A+", false);
        minusFont.setOnClickListener(v -> { fontPercent = Math.max(80, fontPercent - 10); fontValue.setText(fontPercent + "%"); saveReaderPreferences(); applyReaderStyleSmooth(true); });
        plusFont.setOnClickListener(v -> { fontPercent = Math.min(200, fontPercent + 10); fontValue.setText(fontPercent + "%"); saveReaderPreferences(); applyReaderStyleSmooth(true); });
        fontSizeRow.addView(minusFont, sheetChipLp(false));
        fontSizeRow.addView(fontValue, sheetChipLp(true));
        fontSizeRow.addView(plusFont, sheetChipLp(true));
        TextView fontPick = sheetChip("Font · " + fontDisplayName(), false);
        fontPick.setOnClickListener(v -> { dialog.dismiss(); showFontDialog(); });
        fontSizeRow.addView(fontPick, sheetChipLp(true));
        card.addView(fontSizeRow);

        addSheetLabel(card, "Line height", sub);
        LinearLayout lineRow = sheetRow();
        TextView lineMinus = sheetChip("−", false);
        TextView lineValue = sheetChip(lineSpacingDisplay(), true);
        TextView linePlus = sheetChip("+", false);
        lineMinus.setOnClickListener(v -> { lineSpacing = Math.max(120, lineSpacing - 10); lineValue.setText(lineSpacingDisplay()); saveReaderPreferences(); applyReaderStyleSmooth(true); });
        linePlus.setOnClickListener(v -> { lineSpacing = Math.min(220, lineSpacing + 10); lineValue.setText(lineSpacingDisplay()); saveReaderPreferences(); applyReaderStyleSmooth(true); });
        lineRow.addView(lineMinus, sheetChipLp(false));
        lineRow.addView(lineValue, sheetChipLp(true));
        lineRow.addView(linePlus, sheetChipLp(true));
        card.addView(lineRow);

        addSheetLabel(card, "Alignment", sub);
        LinearLayout alignRow = sheetRow();
        String[] alignLabels = {"Justify", "Left", "Right"};
        String[] alignValues = {"justify", "left", "right"};
        TextView[] alignChips = new TextView[3];
        for (int i = 0; i < 3; i++) {
            alignChips[i] = sheetChip(alignLabels[i], alignValues[i].equals(textAlignment));
            final int idx = i;
            alignChips[i].setOnClickListener(v -> { textAlignment = alignValues[idx]; saveReaderPreferences(); applyReaderStyleSmooth(true); selectSheetChip(alignChips, idx); });
            alignRow.addView(alignChips[i], sheetChipLp(i > 0));
        }
        card.addView(alignRow);

        addSheetLabel(card, "Margins", sub);
        LinearLayout marginRow = sheetRow();
        String[] marginLabels = {"Narrow", "Normal", "Wide"};
        int[] marginValues = {4, 7, 11};
        int marginSelected = marginPercent <= 5 ? 0 : (marginPercent >= 9 ? 2 : 1);
        TextView[] marginChips = new TextView[3];
        for (int i = 0; i < 3; i++) {
            marginChips[i] = sheetChip(marginLabels[i], i == marginSelected);
            final int idx = i;
            marginChips[i].setOnClickListener(v -> { marginPercent = marginValues[idx]; saveReaderPreferences(); applyReaderStyleSmooth(true); selectSheetChip(marginChips, idx); });
            marginRow.addView(marginChips[i], sheetChipLp(i > 0));
        }
        card.addView(marginRow);

        addSheetLabel(card, "Reading", sub);
        LinearLayout modeRow = sheetRow();
        TextView[] modeChips = {sheetChip("Pages", "page".equals(readingMode)), sheetChip("Scroll", "scroll".equals(readingMode))};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            modeChips[i].setOnClickListener(v -> { readingMode = idx == 0 ? "page" : "scroll"; pageTurnLocked = false; saveReaderPreferences(); applyReaderStyleSmooth(true); selectSheetChip(modeChips, idx); });
            modeRow.addView(modeChips[i], sheetChipLp(i > 0));
        }
        card.addView(modeRow);

        addSheetLabel(card, "Page animation", sub);
        LinearLayout animRow = sheetRow();
        String[] animLabels = {"None", "Slide"};
        String[] animValues = {"none", "slide"};
        TextView[] animChips = new TextView[2];
        for (int i = 0; i < 2; i++) {
            animChips[i] = sheetChip(animLabels[i], animValues[i].equals(pageAnimation));
            final int idx = i;
            animChips[i].setOnClickListener(v -> { pageAnimation = animValues[idx]; saveReaderPreferences(); selectSheetChip(animChips, idx); });
            animRow.addView(animChips[i], sheetChipLp(i > 0));
        }
        card.addView(animRow);

        addSheetLabel(card, "Reading brightness", sub);
        LinearLayout brightRow = sheetRow();
        TextView brightValue = new TextView(this);
        brightValue.setText(brightnessPercent < 0 ? "System" : brightnessPercent + "%");
        brightValue.setTextSize(13);
        brightValue.setTextColor(text);
        brightValue.setGravity(Gravity.CENTER_VERTICAL);
        SeekBar brightSeek = new SeekBar(this);
        brightSeek.setMax(101);
        brightSeek.setProgress(brightnessPercent < 0 ? 0 : Math.max(1, Math.min(101, brightnessPercent + 1)));
        brightSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                brightnessPercent = progress == 0 ? -1 : progress - 1;
                brightValue.setText(brightnessPercent < 0 ? "System" : brightnessPercent + "%");
                saveReaderPreferences();
                applyWindowPreferences();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        brightRow.addView(brightSeek, new LinearLayout.LayoutParams(0, dp(44), 1f));
        brightRow.addView(brightValue, new LinearLayout.LayoutParams(dp(66), dp(44)));
        card.addView(brightRow);

        TextView more = sheetChip("More reader settings", false);
        more.setGravity(Gravity.CENTER);
        more.setOnClickListener(v -> { dialog.dismiss(); showAdvancedReaderSettings(); });
        LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        moreLp.topMargin = dp(14);
        card.addView(more, moreLp);

        dialog.setContentView(scroll);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.42f);
            w.setGravity(Gravity.BOTTOM);
            int sw = getResources().getDisplayMetrics().widthPixels;
            int sh = getResources().getDisplayMetrics().heightPixels;
            w.setLayout(Math.min(sw - dp(16), dp(620)), Math.min((int) (sh * 0.86f), dp(780)));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                w.setBackgroundBlurRadius(dp(20));
            }
        }
    }

    private void addSheetLabel(LinearLayout parent, String label, int color) {
        TextView v = new TextView(this);
        v.setText(decoratedSheetSection(label));
        v.setTextSize(12.5f);
        v.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        v.setTextColor(color);
        v.setPadding(dp(3), dp(13), dp(3), dp(7));
        parent.addView(v, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout sheetRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout.LayoutParams sheetChipLp(boolean spaced) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        if (spaced) lp.leftMargin = dp(8);
        return lp;
    }

    private TextView sheetChip(String label, boolean selected) {
        TextView v = new TextView(this);
        v.setText(decoratedSheetChipLabel(label));
        v.setTextSize(12.5f);
        v.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setClickable(true);
        v.setPadding(dp(7), 0, dp(7), 0);
        styleSheetChip(v, selected);
        v.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
                view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(55L).start();
            else if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                    event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                view.animate().scaleX(1f).scaleY(1f).setDuration(110L).start();
            return false;
        });
        return v;
    }

    private void styleSheetChip(TextView v, boolean selected) {
        int bg = selected ? readerSelectedSurface() : readerSoftSurface();
        int fg = selected ? readerAccent() : readerPanelText();
        int stroke = selected ? readerAccent() : readerPanelStroke();
        v.setTextColor(fg);
        v.setBackground(glassPanel(bg, dp(16), stroke));
        v.setElevation(selected ? dp(2) : 0f);
    }

    private void selectSheetChip(TextView[] chips, int selected) {
        if (chips == null) return;
        for (int i = 0; i < chips.length; i++) if (chips[i] != null) styleSheetChip(chips[i], i == selected);
    }

    private void showAdvancedReaderSettings() {
        if (isPdf) {
            showPdfSettings();
            return;
        }

        String[] options = new String[]{
                "Reading mode · " + readingModeDisplayName(),
                "Page animation · " + pageAnimationDisplayName(),
                "Text alignment · " + alignmentDisplayName(),
                "Font size · " + fontPercent + "%",
                "Font · " + fontDisplayName(),
                "Line spacing · " + lineSpacingDisplay(),
                "Margins · " + marginPercent + "%",
                "Theme · " + themeDisplayName(),
                "Brightness · " + brightnessDisplayName(),
                "Keep screen on · " + onOff(keepScreenOn),
                "Lock orientation · " + onOff(lockOrientation),
                "Volume keys navigate · " + onOff(volumeChapterKeys),
                "Reset reader settings"
        };

        new AlertDialog.Builder(this)
                .setTitle("Reader settings")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: showReadingModeDialog(); break;
                        case 1: showPageAnimationDialog(); break;
                        case 2: showAlignmentDialog(); break;
                        case 3: showFontSizeDialog(); break;
                        case 4: showFontDialog(); break;
                        case 5: showLineSpacingDialog(); break;
                        case 6: showMarginDialog(); break;
                        case 7: showThemeDialog(); break;
                        case 8: showBrightnessDialog(); break;
                        case 9:
                            keepScreenOn = !keepScreenOn;
                            saveReaderPreferences();
                            applyWindowPreferences();
                            showReaderSettings();
                            break;
                        case 10:
                            lockOrientation = !lockOrientation;
                            saveReaderPreferences();
                            applyWindowPreferences();
                            showReaderSettings();
                            break;
                        case 11:
                            volumeChapterKeys = !volumeChapterKeys;
                            saveReaderPreferences();
                            showReaderSettings();
                            break;
                        case 12: resetReaderPreferences(); break;
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showReadingModeDialog() {
        String[] labels = {"Page by page", "Vertical scroll"};
        int selected = "page".equals(readingMode) ? 0 : 1;
        new AlertDialog.Builder(this)
                .setTitle("Reading mode")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    String mode = which == 0 ? "page" : "scroll";
                    if (!mode.equals(readingMode)) {
                        readingMode = mode;
                        pageTurnLocked = false;
                        saveReaderPreferences();
                        applyReaderStyle(true);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPageAnimationDialog() {
        String[] labels = {"None · default", "Smooth slide"};
        String[] values = {"none", "slide"};
        int selected = "slide".equals(pageAnimation) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle("Page animation")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    pageAnimation = values[which];
                    saveReaderPreferences();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAlignmentDialog() {
        String[] labels = {"Justify · Normal", "Justify · Auto spacing", "Left", "Right"};
        int selected;
        if ("left".equals(textAlignment)) selected = 2;
        else if ("right".equals(textAlignment)) selected = 3;
        else selected = autoSpacingAdjustment ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle("Text alignment")
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        textAlignment = "justify";
                        autoSpacingAdjustment = false;
                    } else if (which == 1) {
                        textAlignment = "justify";
                        autoSpacingAdjustment = true;
                    } else if (which == 2) {
                        textAlignment = "left";
                    } else {
                        textAlignment = "right";
                    }
                    saveReaderPreferences();
                    applyReaderStyleSmooth(true);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showPdfSettings() {
        String[] options = new String[]{
                "Brightness · " + brightnessDisplayName(),
                "Keep screen on · " + onOff(keepScreenOn),
                "Lock orientation · " + onOff(lockOrientation)
        };

        new AlertDialog.Builder(this)
                .setTitle("Reader settings")
                .setItems(options, (d, which) -> {
                    if (which == 0) showBrightnessDialog();
                    else if (which == 1) {
                        keepScreenOn = !keepScreenOn;
                        saveReaderPreferences();
                        applyWindowPreferences();
                        showPdfSettings();
                    } else if (which == 2) {
                        lockOrientation = !lockOrientation;
                        saveReaderPreferences();
                        applyWindowPreferences();
                        showPdfSettings();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showFontSizeDialog() {
        final int[] values = {80, 90, 100, 110, 115, 125, 140, 160, 180, 200};
        String[] labels = new String[values.length];
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i] + "%";
            if (values[i] == fontPercent) selected = i;
        }

        new AlertDialog.Builder(this)
                .setTitle("Font size")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    fontPercent = values[which];
                    saveReaderPreferences();
                    applyReaderStyleSmooth(true);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFontDialog() {
        List<ReaderFontStore.FontEntry> custom = ReaderFontStore.list(this);
        List<String> labels = new ArrayList<>();
        List<String> ids = new ArrayList<>();

        labels.add("Publisher font (EPUB original)"); ids.add("publisher");
        labels.add("Pyidaungsu"); ids.add("pyidaungsu");
        labels.add("A10 YoeShin"); ids.add("yoeshin");
        labels.add("Burma2"); ids.add("burma2");
        labels.add("Burma001"); ids.add("burma001");
        labels.add("M01 PuPu Bold"); ids.add("pupu");
        labels.add("Myanmar Ayar Typewriter"); ids.add("ayar");
        labels.add("Phantee Hand Written"); ids.add("phantee");

        for (ReaderFontStore.FontEntry f : custom) {
            labels.add("My font · " + f.label);
            ids.add(f.id);
        }
        labels.add("＋ Import custom font…"); ids.add("__import__");
        if (!custom.isEmpty()) {
            labels.add("Manage custom fonts…"); ids.add("__manage__");
        }

        int selected = -1;
        for (int i = 0; i < ids.size(); i++) if (ids.get(i).equals(fontChoice)) selected = i;

        new AlertDialog.Builder(this)
                .setTitle("Font")
                .setSingleChoiceItems(labels.toArray(new String[0]), selected, (dialog, which) -> {
                    String id = ids.get(which);
                    dialog.dismiss();
                    if ("__import__".equals(id)) pickCustomFont();
                    else if ("__manage__".equals(id)) showManageCustomFonts();
                    else {
                        fontChoice = id;
                        saveReaderPreferences();
                        applyReaderStyle(true);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickCustomFont() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        pick.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "font/ttf", "font/otf", "font/woff", "font/woff2",
                "application/x-font-ttf", "application/x-font-opentype",
                "application/font-woff", "application/octet-stream"
        });
        try {
            startActivityForResult(pick, REQ_IMPORT_FONT);
        } catch (Exception e) {
            Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show();
        }
    }

    private void showManageCustomFonts() {
        List<ReaderFontStore.FontEntry> fonts = ReaderFontStore.list(this);
        if (fonts.isEmpty()) {
            Toast.makeText(this, "No custom fonts imported", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[fonts.size()];
        for (int i = 0; i < fonts.size(); i++) labels[i] = fonts.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle("Custom fonts · tap to remove")
                .setItems(labels, (dialog, which) -> {
                    ReaderFontStore.FontEntry target = fonts.get(which);
                    new AlertDialog.Builder(this)
                            .setTitle("Remove font?")
                            .setMessage(target.label)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Remove", (d, w) -> {
                                boolean wasSelected = target.id.equals(fontChoice);
                                if (ReaderFontStore.delete(this, target.id)) {
                                    if (wasSelected) {
                                        fontChoice = "publisher";
                                        saveReaderPreferences();
                                        applyReaderStyleSmooth(true);
                                    }
                                    Toast.makeText(this, "Font removed", Toast.LENGTH_SHORT).show();
                                }
                            }).show();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT_FONT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            ReaderFontStore.FontEntry imported = ReaderFontStore.importFont(this, uri);
            fontChoice = imported.id;
            saveReaderPreferences();
            applyReaderStyleSmooth(true);
            Toast.makeText(this, "Font added · " + imported.label, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Font import failed · " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showLineSpacingDialog() {
        final int[] values = {135, 150, 160, 175, 190, 205};
        String[] labels = {"Compact · 1.35", "1.50", "Default · 1.60", "1.75", "1.90", "Relaxed · 2.05"};

        int selected = 2;
        for (int i = 0; i < values.length; i++)
            if (values[i] == lineSpacing) selected = i;

        new AlertDialog.Builder(this)
                .setTitle("Line spacing")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    lineSpacing = values[which];
                    saveReaderPreferences();
                    applyReaderStyleSmooth(true);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMarginDialog() {
        final int[] values = {3, 5, 7, 9, 12};
        String[] labels = {"Extra narrow", "Narrow · default", "Medium", "Wide", "Extra wide"};

        int selected = 1;
        for (int i = 0; i < values.length; i++)
            if (values[i] == marginPercent) selected = i;

        new AlertDialog.Builder(this)
                .setTitle("Page margins")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    marginPercent = values[which];
                    saveReaderPreferences();
                    applyReaderStyleSmooth(true);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showThemeDialog() {
        String[] labels = {"Light", "Sepia", "Dark"};

        new AlertDialog.Builder(this)
                .setTitle("Theme")
                .setSingleChoiceItems(labels, readerTheme, (dialog, which) -> {
                    readerTheme = which;
                    saveReaderPreferences();
                    applyReaderStyleSmooth(true);
                    updateChromeTheme();
                    refreshSelectionBarTheme();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showBrightnessDialog() {
        final int[] values = {-1, 40, 60, 80, 100};
        String[] labels = {"System", "40%", "60%", "80%", "100%"};

        int selected = 0;
        for (int i = 0; i < values.length; i++)
            if (values[i] == brightnessPercent) selected = i;

        new AlertDialog.Builder(this)
                .setTitle("Brightness")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    brightnessPercent = values[which];
                    saveReaderPreferences();
                    applyWindowPreferences();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void resetReaderPreferences() {
        fontPercent = 100;
        fontChoice = "publisher";
        lineSpacing = 160;
        marginPercent = 5;
        textAlignment = "justify";
        autoSpacingAdjustment = true;
        pageAnimation = "none";
        readerTheme = 0;
        brightnessPercent = -1;
        nightLightMode = "off";
        keepScreenOn = false;
        lockOrientation = false;
        volumeChapterKeys = false;
        readingMode = "page";
        pageTurnLocked = false;
        saveReaderPreferences();
        applyWindowPreferences();
        if (!isPdf) applyReaderStyle(true);
        Toast.makeText(this, "Reader settings reset", Toast.LENGTH_SHORT).show();
    }

    private void saveReaderPreferences() {
        if (!isPdf && bookFile != null) {
            BookTypographyStore.save(prefs, bookFile.getName(), fontPercent, fontChoice, lineSpacing,
                    marginPercent, textAlignment, autoSpacingAdjustment);
        }
        prefs.edit()
                .putInt("epub_font", fontPercent)
                .putString("epub_font_choice", fontChoice)
                .putInt("epub_line_spacing", lineSpacing)
                .putInt("epub_margin", marginPercent)
                .putString("epub_text_alignment", textAlignment)
                .putBoolean("epub_auto_spacing", autoSpacingAdjustment)
                .putString("epub_page_animation", pageAnimation)
                .putInt("reader_theme", readerTheme)
                .putInt("reader_brightness", brightnessPercent)
                .putString("reader_night_light", nightLightMode)
                .putBoolean("reader_keep_screen_on", keepScreenOn)
                .putBoolean("reader_lock_orientation", lockOrientation)
                .putBoolean("reader_volume_chapter", volumeChapterKeys)
                .putString("epub_reading_mode", readingMode)
                .putLong("sync_updated_ms", System.currentTimeMillis())
                .apply();
        GoogleAutoSync.scheduleSoon(this);
    }

    private String readingModeDisplayName() {
        return "page".equals(readingMode) ? "Pages" : "Scroll";
    }

    private String pageAnimationDisplayName() {
        return "slide".equals(pageAnimation) ? "Slide" : "None";
    }

    private String alignmentDisplayName() {
        if ("left".equals(textAlignment)) return "Left";
        if ("right".equals(textAlignment)) return "Right";
        return autoSpacingAdjustment ? "Justify · Auto spacing" : "Justify · Normal";
    }

    private String fontDisplayName() {
        if ("pyidaungsu".equals(fontChoice)) return "Pyidaungsu";
        if ("yoeshin".equals(fontChoice)) return "A10 YoeShin";
        if ("burma2".equals(fontChoice)) return "Burma2";
        if ("burma001".equals(fontChoice)) return "Burma001";
        if ("pupu".equals(fontChoice)) return "M01 PuPu Bold";
        if ("ayar".equals(fontChoice)) return "Myanmar Ayar Typewriter";
        if ("phantee".equals(fontChoice)) return "Phantee Hand Written";
        if (fontChoice != null && fontChoice.startsWith("custom:")) {
            String name = ReaderFontStore.displayNameForChoice(this, fontChoice);
            if (name != null) return name;
        }
        return "Publisher";
    }

    private String lineSpacingDisplay() {
        return String.format(Locale.US, "%.2f", lineSpacing / 100.0);
    }

    private String themeDisplayName() {
        if (readerTheme == 1) return "Sepia";
        if (readerTheme == 2) return "Dark";
        return "Light";
    }

    private String brightnessDisplayName() {
        return brightnessPercent < 0 ? "System" : brightnessPercent + "%";
    }

    private String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    private void applyWindowPreferences() {
        if (keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = brightnessPercent < 0 ? -1f : Math.max(0.05f, brightnessPercent / 100f);
        getWindow().setAttributes(lp);

        try {
            setRequestedOrientation(lockOrientation
                    ? ActivityInfo.SCREEN_ORIENTATION_LOCKED
                    : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        } catch (Exception ignored) {
        }

        enterImmersive();
    }

    private void previous() {
        if (isPdf) {
            if (currentPdfPage > 0) {
                currentPdfPage--;
                renderPdfPage();
            }
        } else {
            if ("page".equals(readingMode)) turnPage(-1);
            else navigateChapter(-1, true);
        }
    }

    private void next() {
        if (isPdf) {
            if (pdfRenderer != null && currentPdfPage < pdfRenderer.getPageCount() - 1) {
                currentPdfPage++;
                renderPdfPage();
            }
        } else {
            if ("page".equals(readingMode)) turnPage(1);
            else navigateChapter(1, false);
        }
    }

    private void updateEpubProgress(int p) {
        currentProgressPermille = Math.max(0, Math.min(1000, p));
        if (spine.isEmpty()) return;

        double overall = (currentSpine + currentProgressPermille / 1000.0) / spine.size();
        int percent = Math.max(0, Math.min(100, (int) Math.round(overall * 100.0)));

        String chapter = currentSpine < chapterTitles.size()
                ? chapterTitles.get(currentSpine)
                : "Chapter " + (currentSpine + 1);

        if ("page".equals(readingMode))
            positionView.setText("Page " + currentPageInChapter + " / " + pageCountInChapter + " · " + percent + "%");
        else
            positionView.setText(chapter + " · " + percent + "%");
        if (readingSeek != null && !readingSeekDragging)
            readingSeek.setProgress(Math.max(0, Math.min(1000, (int) Math.round(overall * 1000.0))));
        if (!footnoteNavigationActive && !footnoteReturnPending && !searchNavigationActive) ReadingProgressStore.set(prefs, bookFile.getName(), percent);
    }

    private void updateEpubPageProgress(int page, int count, int p) {
        currentPageInChapter = Math.max(1, page);
        pageCountInChapter = Math.max(1, count);
        updateEpubProgress(p);
        saveEpubStateOnly();
    }

    private void saveEpubStateOnly() {
        if (footnoteNavigationActive || footnoteReturnPending || searchNavigationActive) return;
        prefs.edit()
                .putInt("epub_chapter_" + bookFile.getName(), currentSpine)
                .putInt("epub_scroll_" + bookFile.getName(), currentProgressPermille)
                .putLong("sync_updated_ms", System.currentTimeMillis())
                .apply();
    }

    private void saveEpubState() {
        saveEpubStateOnly();
        updateEpubProgress(currentProgressPermille);
    }

    private void seekToOverallProgress(int permille) {
        int p = Math.max(0, Math.min(1000, permille));
        if (isPdf) {
            if (pdfRenderer == null || pdfRenderer.getPageCount() <= 0) return;
            int target = Math.max(0, Math.min(pdfRenderer.getPageCount() - 1,
                    (int) Math.round((p / 1000.0) * (pdfRenderer.getPageCount() - 1))));
            if (target != currentPdfPage) {
                currentPdfPage = target;
                renderPdfPage();
            }
            return;
        }
        if (spine.isEmpty() || webView == null) return;
        double absolute = (p / 1000.0) * spine.size();
        int targetSpine = Math.min(spine.size() - 1, Math.max(0, (int) Math.floor(absolute)));
        int targetChapterProgress = targetSpine == spine.size() - 1 && p >= 1000
                ? 1000 : Math.max(0, Math.min(1000, (int) Math.round((absolute - targetSpine) * 1000.0)));
        if (targetSpine != currentSpine) {
            int direction = targetSpine > currentSpine ? 1 : -1;
            prepareChapterTransition(direction);
            currentSpine = targetSpine;
            currentProgressPermille = targetChapterProgress;
            saveEpubStateOnly();
            loadCurrentEpubChapter();
            return;
        }
        currentProgressPermille = targetChapterProgress;
        if ("page".equals(readingMode)) {
            try {
                webView.evaluateJavascript(
                        "(function(){var st=window.__wowPageEngine;if(!st||st.mode!=='page')return;" +
                        "st.page=st.clamp(Math.round(((st.count||1)-1)*" + (targetChapterProgress / 1000.0) + "),0,(st.count||1)-1);st.apply(false);st.report();})()",
                        null);
            } catch (Exception ignored) {}
        } else {
            try {
                webView.evaluateJavascript(
                        "(function(){var h=Math.max(0,document.documentElement.scrollHeight-window.innerHeight);window.scrollTo(0,h*" +
                                (targetChapterProgress / 1000.0) + ");})()", null);
            } catch (Exception ignored) {}
        }
        updateEpubProgress(targetChapterProgress);
        saveEpubStateOnly();
    }

    private void searchInBook() {
        if (isPdf || webView == null || spine.isEmpty()) return;
        if (!searchNavigationActive) {
            searchReturnSpine = currentSpine;
            searchReturnProgressPermille = currentProgressPermille;
            searchReturnPage = currentPageInChapter;
        }
        showBookSearch(bookSearchQuery, !bookSearchResults.isEmpty());
    }

    private void showBookSearch(String initialQuery, boolean useCachedResults) {
        if (isFinishing()) return;
        if (bookSearchDialog != null) {
            try { bookSearchDialog.dismiss(); } catch (Exception ignored) {}
            bookSearchDialog = null;
        }
        hideSearchNavigationBar();
        final Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        bookSearchDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);

        int bg = readerTheme == 2 ? Color.rgb(18, 18, 18) :
                (readerTheme == 1 ? Color.rgb(244, 236, 216) : Color.WHITE);
        int surface = readerPanelBase();
        int text = readerPanelText();
        int sub = readerPanelSubText();
        int accent = readerAccent();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(bg);
        page.setPadding(dp(10), dp(12), dp(10), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(glassPanel(surface, dp(22), readerPanelStroke()));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(32);
        back.setTextColor(text);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> { dialog.dismiss(); restorePreSearchLocation(); });
        header.addView(back, new LinearLayout.LayoutParams(dp(50), dp(54)));

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Search in book");
        input.setHintTextColor(sub);
        input.setTextColor(text);
        input.setTextSize(17);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(4), 0, dp(4), 0);
        input.setText(initialQuery == null ? "" : initialQuery);
        input.setSelection(input.length());
        header.addView(input, new LinearLayout.LayoutParams(0, dp(54), 1f));

        TextView clear = new TextView(this);
        clear.setText("×");
        clear.setTextSize(24);
        clear.setTextColor(sub);
        clear.setGravity(Gravity.CENTER);
        clear.setOnClickListener(v -> input.setText(""));
        header.addView(clear, new LinearLayout.LayoutParams(dp(50), dp(54)));
        page.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        TextView status = new TextView(this);
        status.setTextSize(12.5f);
        status.setTextColor(sub);
        status.setPadding(dp(12), dp(12), dp(12), dp(8));
        page.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(dp(4), 0, dp(4), dp(20));
        scroll.addView(results, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dialog.setContentView(page);
        dialog.setOnDismissListener(d -> { if (bookSearchDialog == dialog) bookSearchDialog = null; });
        dialog.show();
        Window win = dialog.getWindow();
        if (win != null) {
            win.setStatusBarColor(bg);
            win.setNavigationBarColor(bg);
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }

        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable[] pending = new Runnable[1];
        final int[] localGeneration = {0};
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                if (pending[0] != null) handler.removeCallbacks(pending[0]);
                String q = value == null ? "" : value.toString().trim();
                bookSearchQuery = q;
                if (q.isEmpty()) {
                    bookSearchResults.clear();
                    results.removeAllViews();
                    status.setText("Type a word or phrase");
                    return;
                }
                int token = ++localGeneration[0];
                pending[0] = () -> performBookSearch(q, token, localGeneration, results, status, accent, text, sub, surface, dialog);
                handler.postDelayed(pending[0], 260L);
            }
            @Override public void afterTextChanged(android.text.Editable e) {}
        });

        if (useCachedResults && initialQuery != null && !initialQuery.trim().isEmpty() && !bookSearchResults.isEmpty()) {
            renderBookSearchResults(results, status, accent, text, sub, surface, dialog);
        } else if (initialQuery != null && !initialQuery.trim().isEmpty()) {
            int token = ++localGeneration[0];
            performBookSearch(initialQuery.trim(), token, localGeneration, results, status, accent, text, sub, surface, dialog);
        } else {
            status.setText("Type a word or phrase");
        }

        input.requestFocus();
        input.postDelayed(() -> {
            try {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            } catch (Exception ignored) {}
        }, 160L);
    }

    private void performBookSearch(String q, int token, int[] localGeneration, LinearLayout results, TextView status,
                                   int accent, int text, int sub, int surface, Dialog dialog) {
        status.setText("Searching the whole book…");
        results.removeAllViews();
        new Thread(() -> {
            List<ReaderSearchIndex.Hit> found = ReaderSearchIndex.search(spine, chapterTitles, q, 350);
            runOnUiThread(() -> {
                if (dialog != bookSearchDialog || !dialog.isShowing() || token != localGeneration[0]) return;
                bookSearchQuery = q;
                bookSearchResults.clear();
                bookSearchResults.addAll(found);
                renderBookSearchResults(results, status, accent, text, sub, surface, dialog);
            });
        }, "wow-book-search").start();
    }

    private void renderBookSearchResults(LinearLayout results, TextView status, int accent, int text, int sub, int surface, Dialog dialog) {
        results.removeAllViews();
        int count = bookSearchResults.size();
        status.setText(count == 0 ? "No matches" : count + (count == 1 ? " result" : " results") + " in this book");
        for (int i = 0; i < count; i++) {
            ReaderSearchIndex.Hit hit = bookSearchResults.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(13), dp(14), dp(13));
            row.setBackground(glassPanel(surface, dp(15), readerPanelStroke()));

            TextView snippet = new TextView(this);
            snippet.setTextSize(16f);
            snippet.setTextColor(text);
            snippet.setLineSpacing(dp(2), 1.05f);
            snippet.setText(highlightSearchText(hit.snippet, bookSearchQuery, accent));
            row.addView(snippet, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView where = new TextView(this);
            where.setText("⌕  " + hit.chapter);
            where.setTextSize(12.5f);
            where.setTextColor(sub);
            where.setPadding(0, dp(7), 0, 0);
            row.addView(where, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            final int index = i;
            row.setOnClickListener(v -> { dialog.dismiss(); navigateToSearchHit(index); });
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(8);
            results.addView(row, rowLp);
        }
    }

    private CharSequence highlightSearchText(String source, String query, int accent) {
        String value = source == null ? "" : source;
        android.text.SpannableString span = new android.text.SpannableString(value);
        if (query == null || query.trim().isEmpty()) return span;
        String low = value.toLowerCase(Locale.ROOT);
        String q = query.trim().toLowerCase(Locale.ROOT);
        int from = 0;
        while (from <= low.length() - q.length()) {
            int at = low.indexOf(q, from);
            if (at < 0) break;
            span.setSpan(new android.text.style.BackgroundColorSpan(Color.argb(105, Color.red(accent), Color.green(accent), Color.blue(accent))),
                    at, at + q.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            from = at + Math.max(1, q.length());
        }
        return span;
    }

    private void navigateToSearchHit(int index) {
        if (index < 0 || index >= bookSearchResults.size() || spine.isEmpty()) return;
        searchNavigationActive = true;
        searchCurrentIndex = index;
        ReaderSearchIndex.Hit hit = bookSearchResults.get(index);
        pendingSearchQuery = bookSearchQuery;
        pendingSearchOccurrence = hit.occurrence;
        hideControls();
        int target = Math.max(0, Math.min(spine.size() - 1, hit.spineIndex));
        if (target != currentSpine) {
            currentSpine = target;
            currentProgressPermille = 0;
            loadCurrentEpubChapter();
        } else {
            applyPendingSearchHit();
        }
        showSearchNavigationBar();
    }

    private void applyPendingSearchHit() {
        if (!searchNavigationActive || webView == null || pendingSearchOccurrence < 0 || pendingSearchQuery == null || pendingSearchQuery.isEmpty()) return;
        final String query = pendingSearchQuery;
        final int wanted = pendingSearchOccurrence;
        pendingSearchOccurrence = -1;
        String script = "(function(){try{" +
                "var root=document.getElementById('wow-page-flow')||document.body;if(!root)return 'no-root';" +
                "var old=root.querySelectorAll('span.wow-search-hit');for(var z=0;z<old.length;z++){var o=old[z],p=o.parentNode;while(o.firstChild)p.insertBefore(o.firstChild,o);p.removeChild(o);}" +
                "var q=" + jsQuote(query.toLowerCase(Locale.ROOT)) + ",target=" + wanted + ",seen=0,w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,null,false),n;" +
                "var pick=function(n,at){var r=document.createRange();r.setStart(n,at);r.setEnd(n,at+q.length);var sp=document.createElement('span');sp.className='wow-search-hit';sp.style.background='rgba(128,203,196,.50)';sp.style.borderRadius='3px';r.surroundContents(sp);" +
                "var st=window.__wowPageEngine;if(st&&st.mode==='page'){var cp=st.physical?st.physical():(st.page||0),bb=sp.getBoundingClientRect(),docX=(bb.left-(st.marginPx||0))+(cp*(st.step||window.innerWidth||1)),phys=Math.max(0,Math.floor((docX+2)/(st.step||window.innerWidth||1)));st.page=st.nearestLogical?st.nearestLogical(phys):phys;st.apply(false);st.report();}else sp.scrollIntoView({block:'center'});return true;};" +
                "while((n=w.nextNode())){var raw=n.nodeValue||'',low=raw.toLocaleLowerCase(),from=0,at;while((at=low.indexOf(q,from))>=0){if(seen===target)return pick(n,at)?'ok':'fail';seen++;from=at+Math.max(1,q.length);}}" +
                "return 'missing';}catch(e){return 'error';}})()";
        try { webView.evaluateJavascript(script, null); } catch (Exception ignored) {}
        updateSearchNavigationLabel();
    }

    private void showSearchNavigationBar() {
        if (root == null) return;
        if (searchNavigationBar == null) {
            LinearLayout bar = new LinearLayout(this);
            searchNavigationBar = bar;
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(dp(6), dp(4), dp(6), dp(4));
            bar.setBackground(glassPanel(readerPanelBase(), dp(20), readerPanelStroke()));
            bar.setElevation(dp(18));

            TextView close = new TextView(this);
            close.setText("×");
            close.setTextSize(25);
            close.setTextColor(readerPanelText());
            close.setGravity(Gravity.CENTER);
            close.setOnClickListener(v -> closeSearchNavigation(false));
            bar.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));

            searchNavigationLabel = new TextView(this);
            searchNavigationLabel.setTextSize(13.5f);
            searchNavigationLabel.setTextColor(readerPanelText());
            searchNavigationLabel.setMaxLines(2);
            searchNavigationLabel.setGravity(Gravity.CENTER_VERTICAL);
            bar.addView(searchNavigationLabel, new LinearLayout.LayoutParams(0, dp(48), 1f));

            TextView prev = new TextView(this);
            prev.setText("‹");
            prev.setTextSize(28);
            prev.setTextColor(readerPanelText());
            prev.setGravity(Gravity.CENTER);
            prev.setOnClickListener(v -> {
                if (!bookSearchResults.isEmpty()) navigateToSearchHit((searchCurrentIndex - 1 + bookSearchResults.size()) % bookSearchResults.size());
            });
            TextView next = new TextView(this);
            next.setText("›");
            next.setTextSize(28);
            next.setTextColor(readerPanelText());
            next.setGravity(Gravity.CENTER);
            next.setOnClickListener(v -> {
                if (!bookSearchResults.isEmpty()) navigateToSearchHit((searchCurrentIndex + 1) % bookSearchResults.size());
            });
            bar.addView(prev, new LinearLayout.LayoutParams(dp(48), dp(48)));
            bar.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48)));

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58), Gravity.BOTTOM);
            lp.leftMargin = dp(12);
            lp.rightMargin = dp(12);
            lp.bottomMargin = dp(18);
            root.addView(bar, lp);
        }
        searchNavigationBar.setBackground(glassPanel(readerPanelBase(), dp(20), readerPanelStroke()));
        tintChromeChildren(searchNavigationBar, readerPanelText());
        searchNavigationBar.setVisibility(View.VISIBLE);
        searchNavigationBar.bringToFront();
        updateSearchNavigationLabel();
    }

    private void updateSearchNavigationLabel() {
        if (searchNavigationLabel == null) return;
        int count = bookSearchResults.size();
        String where = currentSpine >= 0 && currentSpine < spine.size() ? chapterDisplayTitle(currentSpine) : "";
        searchNavigationLabel.setText(bookSearchQuery + "\n" + (searchCurrentIndex + 1) + " of " + Math.max(1, count) +
                (where.isEmpty() ? "" : " · " + where));
    }

    private void hideSearchNavigationBar() {
        if (searchNavigationBar != null) searchNavigationBar.setVisibility(View.GONE);
    }

    private void closeSearchNavigation(boolean restoreOriginal) {
        hideSearchNavigationBar();
        searchNavigationActive = false;
        pendingSearchOccurrence = -1;
        clearSearchHighlight();
        if (restoreOriginal) {
            restorePreSearchLocation();
        } else {
            updateEpubProgress(currentProgressPermille);
            saveEpubStateOnly();
            showControls();
        }
    }

    private void clearSearchHighlight() {
        if (webView == null) return;
        try {
            webView.evaluateJavascript("(function(){var a=document.querySelectorAll('span.wow-search-hit');for(var i=0;i<a.length;i++){var s=a[i],p=s.parentNode;while(s.firstChild)p.insertBefore(s.firstChild,s);p.removeChild(s);}return true;})()", null);
        } catch (Exception ignored) {}
    }

    private void restorePreSearchLocation() {
        hideSearchNavigationBar();
        searchNavigationActive = false;
        pendingSearchOccurrence = -1;
        clearSearchHighlight();
        if (searchReturnSpine < 0 || spine.isEmpty()) {
            showControls();
            return;
        }
        int target = Math.max(0, Math.min(spine.size() - 1, searchReturnSpine));
        currentProgressPermille = searchReturnProgressPermille;
        if (target != currentSpine) {
            currentSpine = target;
            loadCurrentEpubChapter();
        } else if ("page".equals(readingMode)) {
            int pageZero = Math.max(0, searchReturnPage - 1);
            try {
                webView.evaluateJavascript("(function(){var st=window.__wowPageEngine;if(!st)return;st.page=st.clamp(" + pageZero + ",0,(st.count||1)-1);st.apply(false);st.report();})()", null);
            } catch (Exception ignored) {}
        }
        showControls();
    }

    private void toggleBookmark() {
        String key = "marks_" + bookFile.getName();
        int pos = isPdf ? currentPdfPage : currentSpine;
        String token = "," + pos + ",";
        String value = prefs.getString(key, ",");

        boolean marked = value.contains(token);
        value = marked ? value.replace(token, ",") : value + pos + ",";
        prefs.edit().putString(key, value).putLong("sync_updated_ms", System.currentTimeMillis()).apply();

        updateBookmarkIcon();
        Toast.makeText(this,
                marked ? "Bookmark removed" : "Bookmarked",
                Toast.LENGTH_SHORT).show();
    }

    private void updateBookmarkIcon() {
        if (bookmarkButton == null) return;

        String value = prefs.getString("marks_" + bookFile.getName(), ",");
        int pos = isPdf ? currentPdfPage : currentSpine;
        bookmarkButton.setText(value.contains("," + pos + ",") ? "★" : "☆");
    }

    private void hideControls() {
        cancelChromeAutoHide();
        controlsVisible = false;
        if (topBar != null && topBar.getVisibility() == View.VISIBLE) {
            topBar.animate().cancel();
            topBar.animate().alpha(0f).translationY(-dp(14)).setDuration(145L)
                    .withEndAction(() -> { topBar.setVisibility(View.GONE); topBar.setAlpha(1f); topBar.setTranslationY(0f); }).start();
        }
        if (bottomBar != null && bottomBar.getVisibility() == View.VISIBLE) {
            bottomBar.animate().cancel();
            bottomBar.animate().alpha(0f).translationY(dp(14)).setDuration(145L)
                    .withEndAction(() -> { bottomBar.setVisibility(View.GONE); bottomBar.setAlpha(1f); bottomBar.setTranslationY(0f); }).start();
        }
        if (readingSeek != null && readingSeek.getVisibility() == View.VISIBLE) {
            readingSeek.animate().cancel();
            readingSeek.animate().alpha(0f).translationY(dp(8)).setDuration(130L)
                    .withEndAction(() -> { readingSeek.setVisibility(View.GONE); readingSeek.setAlpha(1f); readingSeek.setTranslationY(0f); }).start();
        }
    }


    private void showControls() {
        controlsVisible = true;
        if (topBar != null) {
            topBar.animate().cancel();
            topBar.setVisibility(View.VISIBLE);
            topBar.setAlpha(0f);
            topBar.setTranslationY(-dp(10));
            topBar.animate().alpha(1f).translationY(0f).setDuration(175L).start();
        }
        if (bottomBar != null) {
            bottomBar.animate().cancel();
            bottomBar.setVisibility(View.VISIBLE);
            bottomBar.setAlpha(0f);
            bottomBar.setTranslationY(dp(10));
            bottomBar.animate().alpha(1f).translationY(0f).setDuration(175L).start();
        }
        if (readingSeek != null) {
            readingSeek.animate().cancel();
            readingSeek.setVisibility(View.VISIBLE);
            readingSeek.setAlpha(0f);
            readingSeek.setTranslationY(dp(7));
            readingSeek.animate().alpha(1f).translationY(0f).setDuration(190L).start();
        }
        scheduleChromeAutoHide();
        enterImmersive();
    }


    private void cancelChromeAutoHide() {
        if (root != null && chromeAutoHideRunnable != null) root.removeCallbacks(chromeAutoHideRunnable);
        chromeAutoHideRunnable = null;
    }

    private void scheduleChromeAutoHide() {
        cancelChromeAutoHide();
        if (!controlsVisible || root == null || readingSeekDragging) return;
        chromeAutoHideRunnable = () -> {
            chromeAutoHideRunnable = null;
            if (controlsVisible && !readingSeekDragging && currentSelection == null) hideControls();
        };
        root.postDelayed(chromeAutoHideRunnable, 4200L);
    }

    private void updateNightLightOverlay() {
        if (nightLightOverlay == null) return;
        boolean active = "on".equals(nightLightMode);
        if ("auto".equals(nightLightMode)) {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            active = hour >= 19 || hour < 6;
        }
        if (readerTheme == 2) active = false;
        nightLightOverlay.animate().cancel();
        nightLightOverlay.animate().alpha(active ? 0.095f : 0f).setDuration(240L).start();
    }

    private void toggleControls() {
        if (controlsVisible) hideControls();
        else showControls();
        enterImmersive();
    }

    private void installReaderSafeAreaHandling() {
        if (root == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attrs = getWindow().getAttributes();
            attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attrs);
        }

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int safeTop = 0;
            int safeBottom = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsetsIgnoringVisibility(
                        android.view.WindowInsets.Type.systemBars() |
                        android.view.WindowInsets.Type.displayCutout());
                safeTop = bars.top;
                safeBottom = bars.bottom;
            } else {
                safeTop = Math.max(insets.getSystemWindowInsetTop(), insets.getStableInsetTop());
                safeBottom = Math.max(insets.getSystemWindowInsetBottom(), insets.getStableInsetBottom());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && insets.getDisplayCutout() != null) {
                    safeTop = Math.max(safeTop, insets.getDisplayCutout().getSafeInsetTop());
                    safeBottom = Math.max(safeBottom, insets.getDisplayCutout().getSafeInsetBottom());
                }
            }
            if (topBar != null) {
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) topBar.getLayoutParams();
                int wanted = safeTop + dp(8);
                if (p.topMargin != wanted) { p.topMargin = wanted; topBar.setLayoutParams(p); }
            }
            if (bottomBar != null) {
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) bottomBar.getLayoutParams();
                int wanted = safeBottom + dp(12);
                if (p.bottomMargin != wanted) { p.bottomMargin = wanted; bottomBar.setLayoutParams(p); }
            }
            if (readingSeek != null) {
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) readingSeek.getLayoutParams();
                int wanted = safeBottom + dp(64);
                if (p.bottomMargin != wanted) { p.bottomMargin = wanted; readingSeek.setLayoutParams(p); }
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    private void enterImmersive() {
        Window window = getWindow();
        View decor = window.getDecorView();

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        // Keep the content laid out edge-to-edge on every supported Android version.
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(android.view.WindowInsets.Type.statusBars() |
                        android.view.WindowInsets.Type.navigationBars());
            }
        }
    }

    private void updateChromeTheme() {
        int solid;
        int fg;
        int glass;
        int stroke;
        if (isPdf) {
            solid = Color.WHITE;
            fg = Color.rgb(32, 33, 36);
            glass = Color.argb(238, 255, 255, 255);
            stroke = Color.argb(82, 210, 214, 220);
        } else if (readerTheme == 2) {
            solid = Color.rgb(18, 18, 18);
            fg = Color.rgb(240, 242, 246);
            glass = Color.argb(232, 28, 29, 33);
            stroke = Color.argb(56, 255, 255, 255);
        } else if (readerTheme == 1) {
            solid = Color.rgb(244, 236, 216);
            fg = Color.rgb(74, 64, 51);
            glass = Color.argb(238, 250, 244, 228);
            stroke = Color.argb(92, 168, 153, 126);
        } else {
            solid = Color.WHITE;
            fg = Color.rgb(32, 33, 36);
            glass = Color.argb(238, 255, 255, 255);
            stroke = Color.argb(74, 175, 181, 193);
        }
        if (topBar != null) {
            topBar.setBackground(glassPanel(glass, dp(19), stroke));
            tintChromeChildren(topBar, fg);
        }
        if (bottomBar != null) {
            bottomBar.setBackground(glassPanel(glass, dp(19), stroke));
            tintChromeChildren(bottomBar, fg);
        }
        if (titleView != null) titleView.setTextColor(fg);
        if (positionView != null) positionView.setTextColor(fg);
        if (root != null) root.setBackgroundColor(solid);
        if (webView != null) webView.setBackgroundColor(solid);
        if (preloadWebView != null) preloadWebView.setBackgroundColor(solid);
        updateNightLightOverlay();
    }


    private void openPdf() {
        try {
            pdfDescriptor = ParcelFileDescriptor.open(bookFile, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(pdfDescriptor);

            if (pdfRenderer.getPageCount() == 0)
                throw new Exception("PDF has no pages");

            currentPdfPage = Math.max(0, Math.min(
                    prefs.getInt("pdf_page_" + bookFile.getName(), 0),
                    pdfRenderer.getPageCount() - 1));

            renderPdfPage();

        } catch (Exception e) {
            Toast.makeText(this, "PDF error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void renderPdfPage() {
        if (pdfRenderer == null) return;

        try {
            if (pdfPage != null) pdfPage.close();
            pdfPage = pdfRenderer.openPage(currentPdfPage);

            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int targetWidth = Math.min(Math.max(screenWidth, 720), 1600);
            float scale = targetWidth / (float) pdfPage.getWidth();
            int targetHeight = Math.max(1, Math.round(pdfPage.getHeight() * scale));

            Bitmap bitmap = Bitmap.createBitmap(
                    targetWidth,
                    targetHeight,
                    Bitmap.Config.ARGB_8888);

            bitmap.eraseColor(Color.WHITE);

            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);

            pdfPage.render(
                    bitmap,
                    null,
                    matrix,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            pdfImage.setImageBitmap(bitmap);
            resetPdfZoom();

            int percent = (int) Math.round(
                    ((currentPdfPage + 1.0) / pdfRenderer.getPageCount()) * 100.0);

            positionView.setText(
                    "Page " + (currentPdfPage + 1) +
                    " / " + pdfRenderer.getPageCount() +
                    " · " + percent + "%");
            if (readingSeek != null && !readingSeekDragging && pdfRenderer.getPageCount() > 1)
                readingSeek.setProgress((int) Math.round((currentPdfPage / (double) (pdfRenderer.getPageCount() - 1)) * 1000.0));

            prefs.edit()
                    .putInt("pdf_page_" + bookFile.getName(), currentPdfPage)
                    .putInt("percent_" + bookFile.getName(), percent)
                    .putLong("sync_updated_ms", System.currentTimeMillis())
                    .apply();

            updateBookmarkIcon();

        } catch (Exception e) {
            Toast.makeText(this,
                    "Unable to render PDF page",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void resetPdfZoom() {
        pdfScale = 1f;
        if (pdfImage != null) {
            pdfImage.setScaleX(1f);
            pdfImage.setScaleY(1f);
            pdfImage.setTranslationX(0f);
            pdfImage.setTranslationY(0f);
            pdfImage.setPivotX(pdfImage.getWidth() / 2f);
            pdfImage.setPivotY(pdfImage.getHeight() / 2f);
        }
    }

    private void unzipEpub(File epub, File dest) throws Exception {
        // Prefer the ZIP central directory. Some EPUB producers write a wrong
        // uncompressed size into a local file header while the central directory
        // is correct. ZipInputStream trusts that broken local value and throws
        // "invalid entry size"; ZipFile reads the correct central-directory metadata.
        try {
            unzipEpubWithCentralDirectory(epub, dest);
            return;
        } catch (SecurityException unsafe) {
            throw unsafe;
        } catch (Exception centralDirectoryFailure) {
            // Keep support for unusual streaming ZIPs whose central directory is
            // incomplete but whose local entries are still readable.
            resetEpubExtractionDirectory(dest);
            try {
                unzipEpubStreaming(epub, dest);
            } catch (Exception streamingFailure) {
                streamingFailure.addSuppressed(centralDirectoryFailure);
                throw streamingFailure;
            }
        }
    }

    private void unzipEpubWithCentralDirectory(File epub, File dest) throws Exception {
        String destPath = dest.getCanonicalPath() + File.separator;
        byte[] buffer = new byte[64 * 1024];

        try (ZipFile zip = new ZipFile(epub)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File out = safeEpubOutput(dest, destPath, entry.getName());

                if (entry.isDirectory()) {
                    if (!out.mkdirs() && !out.isDirectory())
                        throw new Exception("Cannot create EPUB folder");
                    continue;
                }

                File parent = out.getParentFile();
                if (parent != null && !parent.mkdirs() && !parent.isDirectory())
                    throw new Exception("Cannot create EPUB folder");

                try (InputStream in = zip.getInputStream(entry);
                     FileOutputStream fos = new FileOutputStream(out)) {
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (n > 0) fos.write(buffer, 0, n);
                    }
                }
            }
        }
    }

    private void unzipEpubStreaming(File epub, File dest) throws Exception {
        String destPath = dest.getCanonicalPath() + File.separator;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(epub))) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];

            while ((entry = zis.getNextEntry()) != null) {
                File out = safeEpubOutput(dest, destPath, entry.getName());

                if (entry.isDirectory()) {
                    if (!out.mkdirs() && !out.isDirectory())
                        throw new Exception("Cannot create EPUB folder");
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.mkdirs() && !parent.isDirectory())
                        throw new Exception("Cannot create EPUB folder");

                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        int n;
                        while ((n = zis.read(buffer)) != -1) {
                            if (n > 0) fos.write(buffer, 0, n);
                        }
                    }
                }

                zis.closeEntry();
            }
        }
    }

    private File safeEpubOutput(File dest, String destPath, String entryName) throws Exception {
        String normalized = entryName == null ? "" : entryName.replace('\\', '/');
        File out = new File(dest, normalized);
        String outPath = out.getCanonicalPath();
        if (!outPath.startsWith(destPath))
            throw new SecurityException("Unsafe EPUB path");
        return out;
    }

    private void resetEpubExtractionDirectory(File dest) throws Exception {
        File[] children = dest.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursive(child);
        }
        if (!dest.exists() && !dest.mkdirs())
            throw new Exception("Cannot prepare EPUB folder");
    }

    private void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;

        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null)
                for (File c : children)
                    deleteRecursive(c);
        }

        f.delete();
    }

    private class ReaderBridge {
        private final WebView owner;

        ReaderBridge(WebView owner) {
            this.owner = owner;
        }

        @JavascriptInterface
        public boolean onReaderLinkTap(String href, String epubType, String role, String rel, String cssClass, String sourceId, String label) {
            if (owner != webView) return false;
            if (footnoteNavigationActive && looksLikeFootnoteBacklink(href, epubType, role, rel, cssClass)) {
                runOnUiThread(BookReaderActivity.this::restoreFootnoteReturn);
                return true;
            }
            if (looksLikeFootnoteReference(href, epubType, role, rel, cssClass)) {
                final String targetHref = href == null ? "" : href;
                final String targetLabel = label == null ? "" : label;
                final String targetSourceId = sourceId == null ? "" : sourceId;
                footnoteTapSuppressUntilMs = android.os.SystemClock.uptimeMillis() + 1400L;
                runOnUiThread(() -> {
                    if (isFinishing() || owner != webView) return;
                    // Card-only footnotes: never navigate away from the reading page.
                    footnoteNavigationActive = false;
                    footnoteReturnPending = false;
                    footnoteReturnArmed = false;
                    requestFootnotePreview(targetHref, targetLabel, targetSourceId);
                });
                return true;
            }
            return false;
        }

        @JavascriptInterface
        public void onSelection(String text, int start, int end) {
            if (owner != webView) return;
            runOnUiThread(() -> onWebSelection(text, start, end));
        }

        @JavascriptInterface
        public void onScroll(int p) {
            if (owner != webView) return;
            runOnUiThread(() -> {
                if (!"scroll".equals(readingMode)) return;
                updateEpubProgress(p);
                saveEpubStateOnly();
            });
        }

        @JavascriptInterface
        public void onScrollReady(int generation) {
            if (owner != webView) return;
            runOnUiThread(() -> {
                if (!"scroll".equals(readingMode) || generation != chapterLoadGeneration) return;
                completePageReady(generation);
            });
        }

        @JavascriptInterface
        public void onPage(int page, int count, int p) {
            if (owner != webView) return;
            runOnUiThread(() -> {
                if (!"page".equals(readingMode)) return;
                updateEpubPageProgress(page, count, p);
            });
        }

        @JavascriptInterface
        public void onPageReady(int generation, int page, int count, int p) {
            if (owner != webView) return;
            runOnUiThread(() -> {
                if (!"page".equals(readingMode) || generation != chapterLoadGeneration) return;
                updateEpubPageProgress(page, count, p);
                completePageReady(generation);
            });
        }

        @JavascriptInterface
        public void onStyleReady(int token) {
            if (owner != webView) return;
            runOnUiThread(() -> finishReaderStyleReflow(token));
        }

        @JavascriptInterface
        public void onPageTurnComplete(int page, int count, int p) {
            if (owner != webView) return;
            runOnUiThread(() -> {
                if (!"page".equals(readingMode)) return;
                updateEpubPageProgress(page, count, p);
                pageTurnLocked = false;
            });
        }

        @JavascriptInterface
        public void onEmptyChapter() {
            if (owner != webView) return;
            runOnUiThread(() -> {
                if (!"page".equals(readingMode)) return;
                skipEmptyEpubSpine();
            });
        }

        @JavascriptInterface
        public void pageEngineFailed(String message) {
            if (owner != webView) return;
            runOnUiThread(() -> {
                if (!"page".equals(readingMode)) return;
                readingMode = "scroll";
                pageTurnLocked = false;
                chapterLoading = true;
                pendingChapterCurlDirection = 0;
                if (pageCurlView != null) pageCurlView.release();
                prefs.edit().putString("epub_reading_mode", "scroll").apply();
                applyReaderStyle(true);
                Toast.makeText(BookReaderActivity.this, "Page layout adjusted to Scroll for this book", Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public void requestChapter(int delta) {
            if (owner != webView) return;
            runOnUiThread(() -> {
                if (!"page".equals(readingMode) || delta == 0) return;
                int target = currentSpine + (delta < 0 ? -1 : 1);
                if (target < 0 || target >= spine.size()) {
                    pageTurnLocked = false;
                    try { webView.evaluateJavascript("if(window.__wowPageEngine)window.__wowPageEngine.locked=false", null); }
                    catch (Exception ignored) {}
                    return;
                }
                navigateChapter(delta < 0 ? -1 : 1, delta < 0);
            });
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isPdf && volumeChapterKeys) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if ("page".equals(readingMode)) turnPage(1); else navigateChapter(1, false);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                if ("page".equals(readingMode)) turnPage(-1); else navigateChapter(-1, true);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (footnotePreviewDialog != null && footnotePreviewDialog.isShowing()) { footnotePreviewDialog.dismiss(); return; }
        if (bookSearchDialog != null && bookSearchDialog.isShowing()) { bookSearchDialog.dismiss(); restorePreSearchLocation(); return; }
        if (!isPdf && searchNavigationActive) { showBookSearch(bookSearchQuery, true); return; }
        if (!isPdf && (footnoteNavigationActive || footnoteReturnPending)) { restoreFootnoteReturn(); return; }
        if (!isPdf) saveEpubState();
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) getWindow().getDecorView().postDelayed(this::enterImmersive, 55L);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (readingSessionStartedElapsedMs <= 0L)
            readingSessionStartedElapsedMs = ReadingStatsStore.beginSession();
        applyWindowPreferences();
        updateNightLightOverlay();
        GoogleAutoSync.schedule(this);
        getWindow().getDecorView().postDelayed(this::enterImmersive, 80L);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
            cancelChapterPreload();
    }

    @Override
    protected void onPause() {
        ReadingStatsStore.finishSession(prefs, bookFile == null ? null : bookFile.getName(), readingSessionStartedElapsedMs);
        readingSessionStartedElapsedMs = 0L;
        if (!isPdf) saveEpubState();
        GoogleAutoSync.flush(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        cancelChromeAutoHide();
        pendingChapterCurlDirection = 0;
        if (pageCurlView != null) pageCurlView.release();
        finishChapterFadeImmediate();
        if (webView != null) {
            try { webView.removeJavascriptInterface("WoW"); } catch (Exception ignored) {}
            try { webView.stopLoading(); } catch (Exception ignored) {}
            try { webView.destroy(); } catch (Exception ignored) {}
        }
        if (preloadWebView != null) {
            try { preloadWebView.removeJavascriptInterface("WoW"); } catch (Exception ignored) {}
            try { preloadWebView.stopLoading(); } catch (Exception ignored) {}
            try { preloadWebView.destroy(); } catch (Exception ignored) {}
            preloadWebView = null;
        }

        try { if (pdfPage != null) pdfPage.close(); } catch (Exception ignored) {}
        try { if (pdfRenderer != null) pdfRenderer.close(); } catch (Exception ignored) {}
        try { if (pdfDescriptor != null) pdfDescriptor.close(); } catch (Exception ignored) {}

        super.onDestroy();
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
