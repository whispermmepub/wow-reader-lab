package com.whisper.wowreader;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.text.TextPosition;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Extracts a lightweight word/line text layer without replacing PdfRenderer. */
final class PdfTextRepository {
    interface Callback { void onPage(PageText pageText); }

    static final class Word {
        final String text;
        final RectF rect; // normalized to crop box, top-left origin
        Word(String text, RectF rect) { this.text = text; this.rect = rect; }
    }

    static final class Selection {
        final int page;
        final String word;
        final String line;
        final List<RectF> wordRects;
        final List<RectF> lineRects;
        Selection(int page, String word, String line, List<RectF> wordRects, List<RectF> lineRects) {
            this.page = page;
            this.word = word == null ? "" : word;
            this.line = line == null ? this.word : line;
            this.wordRects = copy(wordRects);
            this.lineRects = copy(lineRects);
        }
        private static List<RectF> copy(List<RectF> source) {
            List<RectF> out = new ArrayList<>();
            if (source != null) for (RectF r : source) if (r != null) out.add(new RectF(r));
            return out;
        }
    }

    static final class PageText {
        final int page;
        final float width;
        final float height;
        final List<Word> words;
        PageText(int page, float width, float height, List<Word> words) {
            this.page = page;
            this.width = Math.max(1f, width);
            this.height = Math.max(1f, height);
            this.words = words == null ? new ArrayList<>() : words;
        }

        boolean isEmpty() { return words.isEmpty(); }

        Selection select(float nx, float ny) {
            if (words.isEmpty()) return null;
            Word nearest = null;
            float best = Float.MAX_VALUE;
            for (Word w : words) {
                float dx = nx < w.rect.left ? w.rect.left - nx : nx > w.rect.right ? nx - w.rect.right : 0f;
                float dy = ny < w.rect.top ? w.rect.top - ny : ny > w.rect.bottom ? ny - w.rect.bottom : 0f;
                float d = dx * dx + dy * dy;
                if (d < best) { best = d; nearest = w; }
            }
            if (nearest == null || best > 0.03f * 0.03f) return null;

            List<Word> lineWords = new ArrayList<>();
            float cy = nearest.rect.centerY();
            float tolerance = Math.max(0.010f, nearest.rect.height() * 0.70f);
            for (Word w : words) {
                if (Math.abs(w.rect.centerY() - cy) <= tolerance) lineWords.add(w);
            }
            Collections.sort(lineWords, new Comparator<Word>() {
                @Override public int compare(Word a, Word b) {
                    int y = Float.compare(a.rect.top, b.rect.top);
                    return Math.abs(a.rect.centerY() - b.rect.centerY()) < tolerance ? Float.compare(a.rect.left, b.rect.left) : y;
                }
            });

            StringBuilder line = new StringBuilder();
            List<RectF> lineRects = new ArrayList<>();
            for (Word w : lineWords) {
                if (line.length() > 0) line.append(' ');
                line.append(w.text);
                lineRects.add(new RectF(w.rect));
            }
            List<RectF> wordRects = new ArrayList<>();
            wordRects.add(new RectF(nearest.rect));
            return new Selection(page, nearest.text, line.toString().trim(), wordRects, lineRects);
        }
    }

    private final Context appContext;
    private final File file;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object documentLock = new Object();
    private PDDocument document;
    private volatile boolean closed = false;

    private final LinkedHashMap<Integer, PageText> cache = new LinkedHashMap<Integer, PageText>(24, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, PageText> eldest) { return size() > 24; }
    };

    PdfTextRepository(Context context, File file) {
        appContext = context.getApplicationContext();
        this.file = file;
        PDFBoxResourceLoader.init(appContext);
    }

    PageText cached(int page) {
        synchronized (cache) { return cache.get(page); }
    }

    void request(int page, Callback callback) {
        if (closed || callback == null || page < 0) return;
        PageText hit = cached(page);
        if (hit != null) { main.post(() -> callback.onPage(hit)); return; }
        worker.execute(() -> {
            PageText result = null;
            try {
                result = extract(page);
                if (result != null) synchronized (cache) { cache.put(page, result); }
            } catch (Throwable ignored) {}
            final PageText ready = result;
            if (!closed) main.post(() -> callback.onPage(ready));
        });
    }

    private PageText extract(int pageZero) throws Exception {
        if (closed || file == null || !file.isFile()) return null;
        synchronized (documentLock) {
            if (closed) return null;
            if (document == null) document = PDDocument.load(file);
            if (pageZero < 0 || pageZero >= document.getNumberOfPages()) return null;
            PDRectangle box = document.getPage(pageZero).getCropBox();
            final float pageWidth = Math.max(1f, box.getWidth());
            final float pageHeight = Math.max(1f, box.getHeight());
            final List<RawWord> raw = new ArrayList<>();

            PDFTextStripper stripper = new PDFTextStripper() {
                @Override protected void writeString(String text, List<TextPosition> positions) throws IOException {
                    collectWords(positions, raw);
                }
            };
            stripper.setSortByPosition(true);
            stripper.setStartPage(pageZero + 1);
            stripper.setEndPage(pageZero + 1);
            stripper.getText(document);

            List<Word> words = new ArrayList<>();
            for (RawWord r : raw) {
                String clean = cleanWord(r.text.toString());
                if (clean.isEmpty()) continue;
                float left = clamp01(r.left / pageWidth);
                float right = clamp01(r.right / pageWidth);
                float top = clamp01(r.top / pageHeight);
                float bottom = clamp01(r.bottom / pageHeight);
                if (right <= left || bottom <= top) continue;
                words.add(new Word(clean, new RectF(left, top, right, bottom)));
            }
            return new PageText(pageZero, pageWidth, pageHeight, words);
        }
    }

    private static void collectWords(List<TextPosition> positions, List<RawWord> out) {
        if (positions == null || positions.isEmpty()) return;
        RawWord current = null;
        TextPosition previous = null;
        for (TextPosition p : positions) {
            if (p == null) continue;
            String unicode = p.getUnicode();
            if (unicode == null) unicode = "";
            float x = p.getXDirAdj();
            float y = p.getYDirAdj();
            float w = Math.max(0.1f, p.getWidthDirAdj());
            float h = Math.max(1f, p.getHeightDir());
            boolean blank = unicode.trim().isEmpty();
            boolean breakWord = blank;
            if (!blank && previous != null && current != null) {
                float prevXEnd = previous.getXDirAdj() + Math.max(0.1f, previous.getWidthDirAdj());
                float prevH = Math.max(1f, previous.getHeightDir());
                float yGap = Math.abs(y - previous.getYDirAdj());
                float xGap = x - prevXEnd;
                if (yGap > Math.max(h, prevH) * 0.65f || xGap > Math.max(h, prevH) * 0.55f || xGap < -Math.max(h, prevH))
                    breakWord = true;
            }
            if (breakWord && current != null && current.text.length() > 0) {
                out.add(current);
                current = null;
            }
            if (!blank) {
                if (current == null) current = new RawWord();
                current.text.append(unicode);
                float top = Math.max(0f, y - h);
                float bottom = y + Math.max(1f, h * 0.18f);
                current.left = Math.min(current.left, x);
                current.right = Math.max(current.right, x + w);
                current.top = Math.min(current.top, top);
                current.bottom = Math.max(current.bottom, bottom);
            }
            previous = blank ? null : p;
        }
        if (current != null && current.text.length() > 0) out.add(current);
    }

    private static String cleanWord(String text) {
        if (text == null) return "";
        String s = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        s = s.replaceAll("\\s+", " ");
        s = s.replaceAll("^[\\p{Punct}“”‘’]+|[\\p{Punct}“”‘’]+$", "").trim();
        return s;
    }

    void close() {
        closed = true;
        worker.shutdownNow();
        synchronized (documentLock) {
            try { if (document != null) document.close(); } catch (Exception ignored) {}
            document = null;
        }
        synchronized (cache) { cache.clear(); }
    }

    static PointF clampPoint(float nx, float ny) { return new PointF(clamp01(nx), clamp01(ny)); }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static final class RawWord {
        final StringBuilder text = new StringBuilder();
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
    }
}
