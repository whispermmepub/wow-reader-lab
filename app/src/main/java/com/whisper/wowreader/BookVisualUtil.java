package com.whisper.wowreader;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.widget.ImageView;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class BookVisualUtil {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private BookVisualUtil() {}

    static String title(SharedPreferences prefs, String fileName) {
        if (fileName == null) return "Book";
        String fallback = stripExtension(fileName);
        String value = prefs == null ? fallback : prefs.getString("library_title_" + fileName, fallback);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    static String author(SharedPreferences prefs, String fileName) {
        if (prefs == null || fileName == null) return "";
        String value = prefs.getString("library_author_" + fileName, "");
        return value == null ? "" : value.trim();
    }

    static void loadCover(Activity activity, File file, ImageView target, int widthPx, int heightPx) {
        if (activity == null || target == null) return;
        String name = file == null ? "Book" : file.getName();
        target.setImageBitmap(placeholder(activity, name, widthPx, heightPx));
        if (file == null || !file.isFile()) return;
        final int tag = System.identityHashCode(file) ^ file.getName().hashCode();
        target.setTag(tag);
        EXECUTOR.execute(() -> {
            Bitmap bitmap = null;
            try {
                if (file.getName().toLowerCase(Locale.ROOT).endsWith(".epub")) {
                    File cache = new File(activity.getFilesDir(), "cover_cache");
                    if (!cache.exists()) cache.mkdirs();
                    EpubUtil.Summary summary = EpubUtil.extractSummary(file, cache);
                    if (summary.cover != null && summary.cover.isFile()) bitmap = BitmapFactory.decodeFile(summary.cover.getAbsolutePath());
                } else if (file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    bitmap = pdfCover(file, Math.max(120, widthPx));
                }
            } catch (Exception ignored) {}
            if (bitmap == null) return;
            final Bitmap ready = bitmap;
            activity.runOnUiThread(() -> {
                Object current = target.getTag();
                if (!activity.isFinishing() && current instanceof Integer && ((Integer) current) == tag)
                    target.setImageBitmap(ready);
            });
        });
    }

    private static Bitmap pdfCover(File file, int width) {
        ParcelFileDescriptor pfd = null;
        PdfRenderer renderer = null;
        PdfRenderer.Page page = null;
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);
            if (renderer.getPageCount() == 0) return null;
            page = renderer.openPage(0);
            int height = Math.max(1, Math.round(width * page.getHeight() / (float) page.getWidth()));
            Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            b.eraseColor(Color.WHITE);
            page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return b;
        } catch (Exception ignored) { return null; }
        finally {
            try { if (page != null) page.close(); } catch (Exception ignored) {}
            try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
            try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}
        }
    }

    private static Bitmap placeholder(Activity activity, String title, int width, int height) {
        int w = Math.max(40, width), h = Math.max(60, height);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] colors = {Color.rgb(92, 76, 150), Color.rgb(52, 100, 138), Color.rgb(146, 78, 75), Color.rgb(69, 111, 83), Color.rgb(137, 91, 57)};
        p.setColor(colors[Math.abs(title == null ? 0 : title.hashCode()) % colors.length]);
        c.drawRect(0, 0, w, h, p);
        p.setColor(Color.WHITE);
        Typeface tf;
        try { tf = Typeface.createFromAsset(activity.getAssets(), "fonts/pyidaungsu_native.ttf"); }
        catch (Exception ignored) { tf = Typeface.DEFAULT; }
        p.setTypeface(Typeface.create(tf, Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(Math.min(w, h) * .28f);
        String letter = title == null || title.trim().isEmpty() ? "W" : title.trim().substring(0, 1);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(letter, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, p);
        return b;
    }

    private static String stripExtension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : (name == null ? "Book" : name);
    }
}
