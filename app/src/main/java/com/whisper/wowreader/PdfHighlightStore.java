package com.whisper.wowreader;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.RectF;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Persistent normalized PDF highlight rectangles. */
final class PdfHighlightStore {
    private PdfHighlightStore() {}

    static final class Highlight {
        String id = "";
        int page = 0;
        String quote = "";
        int color = Color.argb(92, 255, 213, 79);
        long createdMs = 0L;
        final List<RectF> rects = new ArrayList<>();
    }

    private static String key(String bookName) {
        return "pdf_highlights_" + Integer.toHexString((bookName == null ? "book" : bookName).hashCode());
    }

    static List<Highlight> load(SharedPreferences prefs, String bookName) {
        List<Highlight> out = new ArrayList<>();
        if (prefs == null) return out;
        try {
            JSONArray a = new JSONArray(prefs.getString(key(bookName), "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                Highlight h = new Highlight();
                h.id = o.optString("id", "");
                h.page = Math.max(0, o.optInt("page", 0));
                h.quote = o.optString("quote", "");
                h.color = o.optInt("color", Color.argb(92, 255, 213, 79));
                h.createdMs = o.optLong("created_ms", 0L);
                JSONArray rs = o.optJSONArray("rects");
                if (rs != null) {
                    for (int r = 0; r < rs.length(); r++) {
                        JSONArray q = rs.optJSONArray(r);
                        if (q == null || q.length() < 4) continue;
                        float l = (float) q.optDouble(0, 0d);
                        float t = (float) q.optDouble(1, 0d);
                        float rr = (float) q.optDouble(2, 0d);
                        float b = (float) q.optDouble(3, 0d);
                        if (rr > l && b > t) h.rects.add(new RectF(clamp(l), clamp(t), clamp(rr), clamp(b)));
                    }
                }
                if (!h.id.isEmpty() && !h.rects.isEmpty()) out.add(h);
            }
        } catch (Exception ignored) {}
        return out;
    }

    static List<Highlight> forPage(SharedPreferences prefs, String bookName, int page) {
        List<Highlight> out = new ArrayList<>();
        for (Highlight h : load(prefs, bookName)) if (h.page == page) out.add(h);
        return out;
    }

    static Highlight add(SharedPreferences prefs, String bookName, int page, String quote,
                         int color, List<RectF> rects) {
        Highlight h = new Highlight();
        h.page = Math.max(0, page);
        h.quote = quote == null ? "" : quote.trim();
        h.color = color;
        h.createdMs = System.currentTimeMillis();
        h.id = Long.toHexString(h.createdMs) + "_" + Integer.toHexString((h.quote + ":" + h.page).hashCode());
        if (rects != null) {
            for (RectF r : rects) if (r != null && r.width() > 0f && r.height() > 0f)
                h.rects.add(new RectF(clamp(r.left), clamp(r.top), clamp(r.right), clamp(r.bottom)));
        }
        if (prefs != null && !h.rects.isEmpty()) {
            List<Highlight> all = load(prefs, bookName);
            all.add(h);
            save(prefs, bookName, all);
        }
        return h;
    }

    static void remove(SharedPreferences prefs, String bookName, String id) {
        List<Highlight> all = load(prefs, bookName);
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).id.equals(id)) all.remove(i);
        save(prefs, bookName, all);
    }

    private static void save(SharedPreferences prefs, String bookName, List<Highlight> all) {
        if (prefs == null) return;
        JSONArray a = new JSONArray();
        for (Highlight h : all) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", h.id);
                o.put("page", h.page);
                o.put("quote", h.quote);
                o.put("color", h.color);
                o.put("created_ms", h.createdMs);
                JSONArray rs = new JSONArray();
                for (RectF r : h.rects) {
                    JSONArray q = new JSONArray();
                    q.put(r.left); q.put(r.top); q.put(r.right); q.put(r.bottom);
                    rs.put(q);
                }
                o.put("rects", rs);
                a.put(o);
            } catch (Exception ignored) {}
        }
        prefs.edit().putString(key(bookName), a.toString())
                .putLong("sync_updated_ms", System.currentTimeMillis()).apply();
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
