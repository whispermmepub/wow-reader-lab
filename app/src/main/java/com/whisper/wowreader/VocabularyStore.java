package com.whisper.wowreader;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Local-first Vocabulary Builder populated by dictionary lookups. */
public final class VocabularyStore {
    private VocabularyStore() {}

    private static final String KEY = "vocabulary_builder_json";

    public static final class Entry {
        public String word = "";
        public int direction = 0; // 0 English→Myanmar, 1 Myanmar→English
        public String book = "";
        public int lookups = 0;
        public long firstMs = 0L;
        public long lastMs = 0L;
        public boolean learned = false;

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("word", word);
            o.put("direction", direction);
            o.put("book", book);
            o.put("lookups", lookups);
            o.put("first_ms", firstMs);
            o.put("last_ms", lastMs);
            o.put("learned", learned);
            return o;
        }

        static Entry fromJson(JSONObject o) {
            Entry e = new Entry();
            if (o == null) return e;
            e.word = o.optString("word", "").trim();
            e.direction = o.optInt("direction", 0) == 1 ? 1 : 0;
            e.book = o.optString("book", "").trim();
            e.lookups = Math.max(1, o.optInt("lookups", 1));
            e.firstMs = Math.max(0L, o.optLong("first_ms", 0L));
            e.lastMs = Math.max(e.firstMs, o.optLong("last_ms", e.firstMs));
            e.learned = o.optBoolean("learned", false);
            return e;
        }
    }

    public static void record(SharedPreferences prefs, String word, int direction, String bookName) {
        if (prefs == null) return;
        String clean = clean(word);
        if (clean.isEmpty()) return;
        List<Entry> entries = load(prefs);
        String key = normalize(clean, direction);
        Entry found = null;
        for (Entry e : entries) {
            if (normalize(e.word, e.direction).equals(key)) { found = e; break; }
        }
        long now = System.currentTimeMillis();
        if (found == null) {
            found = new Entry();
            found.word = clean;
            found.direction = direction == 1 ? 1 : 0;
            found.book = bookName == null ? "" : bookName.trim();
            found.lookups = 1;
            found.firstMs = now;
            found.lastMs = now;
            entries.add(found);
        } else {
            found.lookups = Math.max(1, found.lookups + 1);
            found.lastMs = now;
            if (found.book.isEmpty() && bookName != null) found.book = bookName.trim();
        }
        save(prefs, entries);
    }

    public static List<Entry> load(SharedPreferences prefs) {
        List<Entry> out = new ArrayList<>();
        if (prefs == null) return out;
        try {
            JSONArray a = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                Entry e = Entry.fromJson(a.optJSONObject(i));
                if (!e.word.isEmpty()) out.add(e);
            }
        } catch (Exception ignored) {}
        Collections.sort(out, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) { return Long.compare(b.lastMs, a.lastMs); }
        });
        return out;
    }

    public static List<Entry> learned(SharedPreferences prefs, boolean learned) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : load(prefs)) if (e.learned == learned) out.add(e);
        return out;
    }

    public static void setLearned(SharedPreferences prefs, String word, int direction, boolean learned) {
        if (prefs == null) return;
        List<Entry> entries = load(prefs);
        String key = normalize(word, direction);
        for (Entry e : entries) {
            if (normalize(e.word, e.direction).equals(key)) {
                e.learned = learned;
                e.lastMs = System.currentTimeMillis();
                break;
            }
        }
        save(prefs, entries);
    }

    public static void remove(SharedPreferences prefs, String word, int direction) {
        if (prefs == null) return;
        List<Entry> entries = load(prefs);
        String key = normalize(word, direction);
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry e = entries.get(i);
            if (normalize(e.word, e.direction).equals(key)) entries.remove(i);
        }
        save(prefs, entries);
    }

    public static int count(SharedPreferences prefs) { return load(prefs).size(); }

    private static void save(SharedPreferences prefs, List<Entry> entries) {
        JSONArray a = new JSONArray();
        for (Entry e : entries) {
            try { a.put(e.toJson()); } catch (Exception ignored) {}
        }
        prefs.edit().putString(KEY, a.toString())
                .putLong("sync_updated_ms", System.currentTimeMillis()).apply();
    }

    private static String clean(String text) {
        if (text == null) return "";
        String s = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        s = s.replaceAll("\\s+", " ");
        if (s.length() > 120) s = s.substring(0, 120).trim();
        return s;
    }

    private static String normalize(String text, int direction) {
        return (direction == 1 ? "my:" : "en:") + clean(text).toLowerCase(Locale.ROOT);
    }
}
