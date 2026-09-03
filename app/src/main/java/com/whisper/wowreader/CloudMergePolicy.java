package com.whisper.wowreader;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Conservative merge rules for combining a remote reader snapshot with local state.
 * Local scalar preferences win during a true conflict, while additive/library data
 * is merged so another device's books, annotations, shelves and progress are not
 * silently discarded by the next automatic upload. Google operational keys and
 * sync timestamps are intentionally never imported from the other device.
 */
final class CloudMergePolicy {
    private CloudMergePolicy() {}

    static boolean mergePreferences(JSONObject remoteValues, SharedPreferences prefs) {
        if (remoteValues == null || prefs == null) return false;
        Map<String, ?> local = prefs.getAll();
        SharedPreferences.Editor edit = prefs.edit();
        boolean changed = false;
        Iterator<String> keys = remoteValues.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key == null || key.startsWith("google_") || "sync_updated_ms".equals(key)) continue;
            JSONObject item = remoteValues.optJSONObject(key);
            if (item == null) continue;
            String type = item.optString("t", "");
            Object localValue = local.get(key);

            if (localValue == null) {
                changed |= putRemoteValue(edit, key, item, type);
                continue;
            }

            if ("s".equals(type) && localValue instanceof String) {
                String localString = (String) localValue;
                String remoteString = item.optString("v", "");
                String merged = mergeStringValue(key, localString, remoteString);
                if (!merged.equals(localString)) {
                    edit.putString(key, merged);
                    changed = true;
                }
            } else if ("ss".equals(type) && localValue instanceof Set) {
                HashSet<String> merged = new HashSet<>();
                for (Object value : (Set<?>) localValue) if (value != null) merged.add(String.valueOf(value));
                JSONArray arr = item.optJSONArray("v");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        String value = arr.optString(i, "");
                        if (!value.isEmpty()) merged.add(value);
                    }
                }
                if (merged.size() != ((Set<?>) localValue).size()) {
                    edit.putStringSet(key, merged);
                    changed = true;
                }
            } else if ("i".equals(type) && localValue instanceof Integer) {
                int current = (Integer) localValue;
                int remote = item.optInt("v", current);
                int merged = mergeIntValue(key, current, remote);
                if (merged != current) {
                    edit.putInt(key, merged);
                    changed = true;
                }
            } else if ("l".equals(type) && localValue instanceof Long) {
                long current = (Long) localValue;
                long remote = item.optLong("v", current);
                long merged = mergeLongValue(key, current, remote);
                if (merged != current) {
                    edit.putLong(key, merged);
                    changed = true;
                }
            }
        }
        if (changed) edit.apply();
        return changed;
    }

    private static boolean putRemoteValue(SharedPreferences.Editor edit, String key,
                                          JSONObject item, String type) {
        if ("s".equals(type)) edit.putString(key, item.optString("v", ""));
        else if ("i".equals(type)) edit.putInt(key, item.optInt("v", 0));
        else if ("l".equals(type)) edit.putLong(key, item.optLong("v", 0L));
        else if ("f".equals(type)) edit.putFloat(key, (float) item.optDouble("v", 0));
        else if ("b".equals(type)) edit.putBoolean(key, item.optBoolean("v", false));
        else if ("ss".equals(type)) {
            HashSet<String> set = new HashSet<>();
            JSONArray arr = item.optJSONArray("v");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                String value = arr.optString(i, "");
                if (!value.isEmpty()) set.add(value);
            }
            edit.putStringSet(key, set);
        } else return false;
        return true;
    }

    private static String mergeStringValue(String key, String local, String remote) {
        if (key.startsWith("annotations_")) return mergeAnnotationArrays(local, remote);
        if ("library_shelves_json".equals(key)) return mergeShelves(local, remote);
        if ("reading_stats_days_json".equals(key) || "reading_stats_books_json".equals(key))
            return mergeNumericObjects(local, remote);
        if ("reading_stats_last_day".equals(key)) return local.compareTo(remote) >= 0 ? local : remote;
        // Typography, titles, authors and other scalar choices remain local on conflict.
        return local;
    }

    private static int mergeIntValue(String key, int local, int remote) {
        if (key.startsWith("percent_") || key.startsWith("reading_stats_"))
            return Math.max(local, remote);
        return local;
    }

    private static long mergeLongValue(String key, long local, long remote) {
        if (key.startsWith("last_opened_") || key.startsWith("reading_stats_"))
            return Math.max(local, remote);
        if (key.startsWith("added_at_")) {
            if (local <= 0L) return remote;
            if (remote <= 0L) return local;
            return Math.min(local, remote);
        }
        return local;
    }

    private static String mergeAnnotationArrays(String localRaw, String remoteRaw) {
        try {
            JSONArray local = new JSONArray(localRaw == null ? "[]" : localRaw);
            JSONArray remote = new JSONArray(remoteRaw == null ? "[]" : remoteRaw);
            JSONObject byId = new JSONObject();
            JSONArray merged = new JSONArray();
            for (int i = 0; i < local.length(); i++) {
                JSONObject item = local.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                if (id.isEmpty() || byId.has(id)) continue;
                byId.put(id, true);
                merged.put(item);
            }
            for (int i = 0; i < remote.length(); i++) {
                JSONObject item = remote.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                if (id.isEmpty() || byId.has(id)) continue;
                byId.put(id, true);
                merged.put(item);
            }
            return merged.toString();
        } catch (Exception ignored) {
            return localRaw == null ? "[]" : localRaw;
        }
    }

    private static String mergeShelves(String localRaw, String remoteRaw) {
        try {
            JSONObject local = new JSONObject(localRaw == null ? "{}" : localRaw);
            JSONObject remote = new JSONObject(remoteRaw == null ? "{}" : remoteRaw);
            Iterator<String> shelfNames = remote.keys();
            while (shelfNames.hasNext()) {
                String shelf = shelfNames.next();
                JSONArray localBooks = local.optJSONArray(shelf);
                JSONArray remoteBooks = remote.optJSONArray(shelf);
                if (localBooks == null) localBooks = new JSONArray();
                if (remoteBooks == null) continue;
                HashSet<String> seen = new HashSet<>();
                JSONArray merged = new JSONArray();
                for (int i = 0; i < localBooks.length(); i++) {
                    String name = localBooks.optString(i, "");
                    if (!name.isEmpty() && seen.add(name)) merged.put(name);
                }
                for (int i = 0; i < remoteBooks.length(); i++) {
                    String name = remoteBooks.optString(i, "");
                    if (!name.isEmpty() && seen.add(name)) merged.put(name);
                }
                local.put(shelf, merged);
            }
            return local.toString();
        } catch (Exception ignored) {
            return localRaw == null ? "{}" : localRaw;
        }
    }

    private static String mergeNumericObjects(String localRaw, String remoteRaw) {
        try {
            JSONObject local = new JSONObject(localRaw == null ? "{}" : localRaw);
            JSONObject remote = new JSONObject(remoteRaw == null ? "{}" : remoteRaw);
            Iterator<String> keys = remote.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                long current = local.optLong(key, 0L);
                long incoming = remote.optLong(key, 0L);
                if (incoming > current) local.put(key, incoming);
            }
            return local.toString();
        } catch (Exception ignored) {
            return localRaw == null ? "{}" : localRaw;
        }
    }
}
