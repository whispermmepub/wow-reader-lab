package com.whisper.wowreader;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Vertical, continuous PDF reader that renders only RecyclerView-visible pages.
 * A single background renderer keeps memory/CPU predictable on older devices.
 * v56 adds a lightweight PDFBox text layer for Kindle-style long-press Dictionary/highlights.
 */
final class PdfContinuousView extends RecyclerView {
    interface Listener {
        void onPageChanged(int pageZeroBased, int pageCount);
        void onTap();
        void onUserInteraction();
        void onTextSelected(PdfTextRepository.Selection selection);
        void onTextUnavailable(int pageZeroBased);
    }

    private final LinearLayoutManager layout;
    private final PageAdapter adapter;
    private final ExecutorService rendererExecutor = Executors.newSingleThreadExecutor();
    private final Object rendererLock = new Object();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final GestureDetector tapDetector;

    private ParcelFileDescriptor descriptor;
    private PdfRenderer renderer;
    private volatile int pageCount = 0;
    private volatile int generation = 0;
    private Listener listener;
    private int lastReportedPage = -1;

    private PdfTextRepository textRepository;
    private SharedPreferences prefs;
    private String bookName = "";

    private boolean autoRunning = false;
    private int autoPixelsPerSecond = 0;
    private long autoLastMs = 0L;
    private float autoCarry = 0f;

    PdfContinuousView(Context context) {
        super(context);
        layout = new LinearLayoutManager(context, VERTICAL, false);
        setLayoutManager(layout);
        adapter = new PageAdapter();
        setAdapter(adapter);
        setItemAnimator(null);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(false);
        setPadding(dp(6), dp(8), dp(6), dp(16));
        setBackgroundColor(Color.rgb(48, 49, 52));
        setItemViewCacheSize(4);
        setNestedScrollingEnabled(false);

        tapDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (listener != null) listener.onTap();
                return true;
            }
            @Override public void onLongPress(MotionEvent e) {
                handleLongPress(e);
            }
        });

        addOnScrollListener(new OnScrollListener() {
            @Override public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                reportCurrentPage();
            }
            @Override public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == SCROLL_STATE_DRAGGING && listener != null) listener.onUserInteraction();
                if (newState == SCROLL_STATE_IDLE) reportCurrentPage();
            }
        });
    }

    void setListener(Listener value) { listener = value; }

    void setTextFeatures(PdfTextRepository repository, SharedPreferences preferences, String fileName) {
        textRepository = repository;
        prefs = preferences;
        bookName = fileName == null ? "" : fileName;
        adapter.notifyDataSetChanged();
    }

    void open(File file, int startPage) throws Exception {
        stopAutoScroll();
        generation++;
        lastReportedPage = -1;
        synchronized (rendererLock) {
            closeRendererLocked();
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(descriptor);
            pageCount = renderer.getPageCount();
            if (pageCount <= 0) throw new Exception("PDF has no pages");
        }
        adapter.notifyDataSetChanged();
        int target = clampPage(startPage);
        post(() -> {
            layout.scrollToPositionWithOffset(target, 0);
            post(this::reportCurrentPage);
        });
    }

    int currentPage() {
        int first = layout.findFirstVisibleItemPosition();
        int last = layout.findLastVisibleItemPosition();
        if (first == NO_POSITION) return Math.max(0, lastReportedPage);
        if (last == NO_POSITION) return first;
        View center = findChildViewUnder(getWidth() / 2f, getHeight() / 2f);
        int centered = center == null ? NO_POSITION : getChildAdapterPosition(center);
        return centered == NO_POSITION ? first : centered;
    }

    int pageCount() { return pageCount; }

    void scrollToPage(int pageZeroBased) {
        if (pageCount <= 0) return;
        int target = clampPage(pageZeroBased);
        stopScroll();
        layout.scrollToPositionWithOffset(target, 0);
        post(this::reportCurrentPage);
    }

    void startAutoScroll(int pixelsPerSecond) {
        int speed = Math.max(1, pixelsPerSecond);
        if (pageCount <= 0) {
            stopAutoScroll();
            return;
        }
        autoPixelsPerSecond = speed;
        autoCarry = 0f;
        autoLastMs = 0L;
        if (autoRunning) return;
        autoRunning = true;
        postOnAnimation(autoTick);
    }

    void stopAutoScroll() {
        autoRunning = false;
        autoLastMs = 0L;
        autoCarry = 0f;
        removeCallbacks(autoTick);
    }

    void refreshHighlights(int page) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            ViewHolder raw = getChildViewHolder(child);
            if (!(raw instanceof PageHolder)) continue;
            PageHolder h = (PageHolder) raw;
            if (h.boundPage == page) h.overlay.setHighlights(PdfHighlightStore.forPage(prefs, bookName, page));
        }
    }

    void clearTextSelection() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            ViewHolder raw = getChildViewHolder(child);
            if (raw instanceof PageHolder) ((PageHolder) raw).overlay.clearSelected();
        }
    }

    void close() {
        stopAutoScroll();
        generation++;
        adapter.releaseVisibleBitmaps();
        rendererExecutor.shutdownNow();
        synchronized (rendererLock) { closeRendererLocked(); }
        pageCount = 0;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        tapDetector.onTouchEvent(event);
        if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN && listener != null)
            listener.onUserInteraction();
        return super.onTouchEvent(event);
    }

    private void handleLongPress(MotionEvent e) {
        if (e == null || textRepository == null) return;
        if (listener != null) listener.onUserInteraction();
        View child = findChildViewUnder(e.getX(), e.getY());
        if (child == null) return;
        ViewHolder raw = getChildViewHolder(child);
        if (!(raw instanceof PageHolder)) return;
        PageHolder holder = (PageHolder) raw;
        int page = holder.boundPage;
        if (page < 0) return;
        float localX = e.getX() - child.getLeft();
        float localY = e.getY() - child.getTop();
        PointF normalized = holder.overlay.toNormalized(localX, localY);
        if (normalized == null) return;

        if (holder.pageText != null) {
            deliverSelection(holder, holder.pageText, normalized.x, normalized.y);
            return;
        }
        final int bound = page;
        textRepository.request(page, pageText -> {
            if (holder.boundPage != bound || holder.itemView.getParent() == null) return;
            holder.pageText = pageText;
            holder.overlay.setPageText(pageText);
            deliverSelection(holder, pageText, normalized.x, normalized.y);
        });
    }

    private void deliverSelection(PageHolder holder, PdfTextRepository.PageText pageText, float nx, float ny) {
        if (pageText == null || pageText.isEmpty()) {
            if (listener != null) listener.onTextUnavailable(holder.boundPage);
            return;
        }
        PdfTextRepository.Selection selection = pageText.select(nx, ny);
        if (selection == null) return;
        holder.overlay.setSelected(selection.wordRects);
        if (listener != null) listener.onTextSelected(selection);
    }

    private final Runnable autoTick = new Runnable() {
        @Override public void run() {
            if (!autoRunning) return;
            long now = android.os.SystemClock.uptimeMillis();
            if (autoLastMs == 0L) autoLastMs = now;
            long dt = Math.min(100L, Math.max(0L, now - autoLastMs));
            autoLastMs = now;
            autoCarry += (autoPixelsPerSecond * dt) / 1000f;
            int pixels = (int) autoCarry;
            if (pixels > 0 && canScrollVertically(1)) {
                autoCarry -= pixels;
                scrollBy(0, pixels);
            } else if (!canScrollVertically(1) && isAtDocumentEnd()) {
                stopAutoScroll();
                reportCurrentPage();
                return;
            }
            postOnAnimation(this);
        }
    };

    private boolean isAtDocumentEnd() {
        if (pageCount <= 0 || getChildCount() == 0) return false;
        int last = layout.findLastVisibleItemPosition();
        if (last < pageCount - 1) return false;
        View end = layout.findViewByPosition(pageCount - 1);
        if (end == null) return false;
        return end.getBottom() <= getHeight() - getPaddingBottom() + dp(2);
    }

    private void reportCurrentPage() {
        if (pageCount <= 0) return;
        int page = Math.max(0, Math.min(pageCount - 1, currentPage()));
        if (page == lastReportedPage) return;
        lastReportedPage = page;
        if (listener != null) listener.onPageChanged(page, pageCount);
    }

    private int clampPage(int page) { return Math.max(0, Math.min(Math.max(0, pageCount - 1), page)); }

    private int targetPageWidth() {
        int width = getWidth() > 0 ? getWidth() - getPaddingLeft() - getPaddingRight()
                : getResources().getDisplayMetrics().widthPixels - dp(12);
        return Math.max(320, Math.min(1200, width));
    }

    private int estimatedHeight() {
        return Math.max(dp(420), Math.round(targetPageWidth() * 1.4142f));
    }

    private void renderAsync(PageHolder holder, int position) {
        final int requestGeneration = generation;
        final int requestPage = position;
        final int width = targetPageWidth();
        rendererExecutor.execute(() -> {
            if (requestGeneration != generation || Thread.currentThread().isInterrupted()) return;
            Bitmap bitmap = null;
            int height = estimatedHeight();
            try {
                synchronized (rendererLock) {
                    if (requestGeneration != generation || renderer == null || requestPage < 0 || requestPage >= pageCount) return;
                    PdfRenderer.Page page = renderer.openPage(requestPage);
                    try {
                        float scale = width / (float) Math.max(1, page.getWidth());
                        height = Math.max(1, Math.round(page.getHeight() * scale));
                        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        Matrix matrix = new Matrix();
                        matrix.postScale(scale, scale);
                        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    } finally {
                        page.close();
                    }
                }
            } catch (Throwable ignored) {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                bitmap = null;
            }
            final Bitmap ready = bitmap;
            final int readyHeight = height;
            main.post(() -> {
                if (ready == null) return;
                if (requestGeneration != generation || holder.boundPage != requestPage || holder.itemView.getParent() == null) {
                    if (!ready.isRecycled()) ready.recycle();
                    return;
                }
                holder.replaceBitmap(ready);
                setPageHeight(holder, readyHeight);
            });
        });
    }

    private void setPageHeight(PageHolder holder, int height) {
        ViewGroup.LayoutParams imageLp = holder.image.getLayoutParams();
        if (imageLp.height != height) {
            imageLp.height = height;
            holder.image.setLayoutParams(imageLp);
        }
        ViewGroup.LayoutParams overlayLp = holder.overlay.getLayoutParams();
        if (overlayLp.height != height) {
            overlayLp.height = height;
            holder.overlay.setLayoutParams(overlayLp);
        }
    }

    private void requestText(PageHolder holder, int page) {
        holder.pageText = null;
        holder.overlay.clearSelected();
        holder.overlay.setHighlights(PdfHighlightStore.forPage(prefs, bookName, page));
        if (textRepository == null) return;
        PdfTextRepository.PageText cached = textRepository.cached(page);
        if (cached != null) {
            holder.pageText = cached;
            holder.overlay.setPageText(cached);
            return;
        }
        final int bound = page;
        textRepository.request(page, pageText -> {
            if (holder.boundPage != bound || holder.itemView.getParent() == null) return;
            holder.pageText = pageText;
            holder.overlay.setPageText(pageText);
        });
    }

    private void closeRendererLocked() {
        try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
        try { if (descriptor != null) descriptor.close(); } catch (Exception ignored) {}
        renderer = null;
        descriptor = null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class PageAdapter extends RecyclerView.Adapter<PageHolder> {
        @Override public PageHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout shell = new FrameLayout(getContext());
            shell.setForegroundGravity(Gravity.CENTER);
            shell.setPadding(0, 0, 0, dp(8));
            shell.setBackgroundColor(Color.rgb(48, 49, 52));
            ImageView image = new ImageView(getContext());
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackgroundColor(Color.WHITE);
            FrameLayout.LayoutParams pageLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, estimatedHeight(), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            shell.addView(image, pageLp);
            PdfHighlightOverlayView overlay = new PdfHighlightOverlayView(getContext());
            shell.addView(overlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, estimatedHeight(), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
            shell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new PageHolder(shell, image, overlay);
        }

        @Override public void onBindViewHolder(PageHolder holder, int position) {
            holder.clearBitmap();
            int page = holder.getBindingAdapterPosition();
            if (page == RecyclerView.NO_POSITION) return;
            holder.boundPage = page;
            setPageHeight(holder, estimatedHeight());
            holder.image.setImageDrawable(null);
            requestText(holder, page);
            renderAsync(holder, page);
        }

        @Override public void onViewRecycled(PageHolder holder) {
            holder.boundPage = -1;
            holder.pageText = null;
            holder.overlay.clearSelected();
            holder.clearBitmap();
            super.onViewRecycled(holder);
        }

        @Override public int getItemCount() { return pageCount; }

        void releaseVisibleBitmaps() {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                ViewHolder raw = getChildViewHolder(child);
                if (raw instanceof PageHolder) ((PageHolder) raw).clearBitmap();
            }
        }
    }

    private static final class PageHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final PdfHighlightOverlayView overlay;
        int boundPage = -1;
        Bitmap bitmap;
        PdfTextRepository.PageText pageText;

        PageHolder(View itemView, ImageView image, PdfHighlightOverlayView overlay) {
            super(itemView);
            this.image = image;
            this.overlay = overlay;
        }

        void replaceBitmap(Bitmap next) {
            if (bitmap != null && bitmap != next && !bitmap.isRecycled()) bitmap.recycle();
            bitmap = next;
            image.setImageBitmap(next);
        }

        void clearBitmap() {
            image.setImageDrawable(null);
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            bitmap = null;
        }
    }
}
