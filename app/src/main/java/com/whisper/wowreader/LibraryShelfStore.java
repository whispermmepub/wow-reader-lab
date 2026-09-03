package com.whisper.wowreader;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Local-first shelf/collection storage for WoW Reader Lab. */
public final class LibraryShelfStore {
    private LibraryShelfStore() {}

    private static final String KEY = "library_shelves_json";

    public static List<String> shelves(SharedPreferences prefs) {
        List<String> result = new ArrayList<>();
        if (prefs == null) return result;
        JSONObject root = object(prefs.getString(KEY, "{}"));
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String name = cleanName(keys.next());
            if (!name.isEmpty()) result.add(name);
        }
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public static boolean createShelf(SharedPreferences prefs, String shelfName) {
        if (prefs == null) return false;
        String name = cleanName(shelfName);
        if (name.isEmpty()) return false;
        try {
            JSONObject root = object(prefs.getString(KEY, "{}"));
            if (!root.has(name)) root.put(name, new JSONArray());
            save(prefs, root);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean contains(SharedPreferences prefs, String shelfName, String bookName) {
        if (prefs == null || bookName == null) return false;
        String name = cleanName(shelfName);
        if (name.isEmpty()) return false;
        JSONObject root = object(prefs.getString(KEY, "{}"));
        JSONArray books = root.optJSONArray(name);
        if (books == null) return false;
        for (int i = 0; i < books.length(); i++) if (bookName.equals(books.optString(i, ""))) return true;
        return false;
    }

    public static void setMembership(SharedPreferences prefs, String shelfName, String bookName, boolean included) {
        if (prefs == null || bookName == null || bookName.trim().isEmpty()) return;
        String name = cleanName(shelfName);
        if (name.isEmpty()) return;
        try {
            JSONObject root = object(prefs.getString(KEY, "{}"));
            JSONArray old = root.optJSONArray(name);
            if (old == null) old = new JSONArray();
            JSONArray next = new JSONArray();
            boolean found = false;
            for (int i = 0; i < old.length(); i++) {
                String value = old.optString(i, "");
                if (bookName.equals(value)) {
                    found = true;
                    if (!included) continue;
                }
                if (!value.isEmpty()) next.put(value);
            }
            if (included && !found) next.put(bookName);
            root.put(name, next);
            save(prefs, root);
        } catch (Exception ignored) {
        }
    }

    public static int count(SharedPreferences prefs, String shelfName) {
        if (prefs == null) return 0;
        JSONObject root = object(prefs.getString(KEY, "{}"));
        JSONArray books = root.optJSONArray(cleanName(shelfName));
        return books == null ? 0 : books.length();
    }

    public static void removeBookFromAll(SharedPreferences prefs, String bookName) {
        if (prefs == null || bookName == null) return;
        try {
            JSONObject root = object(prefs.getString(KEY, "{}"));
            Iterator<String> keys = root.keys();
            List<String> names = new ArrayList<>();
            while (keys.hasNext()) names.add(keys.next());
            boolean changed = false;
            for (String shelf : names) {
                JSONArray old = root.optJSONArray(shelf);
                if (old == null) continue;
                JSONArray next = new JSONArray();
                for (int i = 0; i < old.length(); i++) {
                    String value = old.optString(i, "");
                    if (bookName.equals(value)) { changed = true; continue; }
                    if (!value.isEmpty()) next.put(value);
                }
                root.put(shelf, next);
            }
            if (changed) save(prefs, root);
        } catch (Exception ignored) {
        }
    }

    private static void save(SharedPreferences prefs, JSONObject root) {
        prefs.edit()
                .putString(KEY, root.toString())
                .putLong("sync_updated_ms", System.currentTimeMillis())
                .apply();
    }

    private static JSONObject object(String raw) {
        try { return new JSONObject(raw == null ? "{}" : raw); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    private static String cleanName(String value) {
        if (value == null) return "";
        String clean = value.trim().replaceAll("\\s+", " ");
        return clean.length() > 40 ? clean.substring(0, 40).trim() : clean;
    }
}
