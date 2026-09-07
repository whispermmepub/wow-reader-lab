package com.whisper.wowreader;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Small offline Myanmar spelling word list bundled as an app asset. */
final class MyanmarSpellingStore {
    private static final String ASSET = "dictionary/myanmar_spelling.txt";
    private static volatile Data cache;

    private MyanmarSpellingStore() {}

    static final class Match {
        final boolean found;
        final List<String> suggestions;
        Match(boolean found, List<String> suggestions) {
            this.found = found;
            this.suggestions = suggestions == null ? Collections.emptyList() : suggestions;
        }
    }

    static Match lookup(Context context, String query) {
        String q = normalize(query);
        if (q.isEmpty()) return new Match(false, Collections.emptyList());
        Data d = data(context);
        if (d == null) return new Match(false, Collections.emptyList());
        if (d.words.contains(q)) return new Match(true, Collections.emptyList());

        List<Scored> scored = new ArrayList<>();
        for (String word : d.ordered) {
            if (word.isEmpty()) continue;
            boolean prefix = word.startsWith(q) || q.startsWith(word);
            int delta = Math.abs(word.codePointCount(0, word.length()) - q.codePointCount(0, q.length()));
            if (!prefix && delta > 2) continue;
            int dist = prefix ? Math.min(2, delta) : distance(q, word, 3);
            if (dist <= 2) scored.add(new Scored(word, dist, prefix));
        }
        Collections.sort(scored, (a, b) -> {
            if (a.prefix != b.prefix) return a.prefix ? -1 : 1;
            int c = Integer.compare(a.distance, b.distance);
            if (c != 0) return c;
            return Integer.compare(a.word.length(), b.word.length());
        });
        List<String> out = new ArrayList<>();
        for (Scored s : scored) {
            if (!out.contains(s.word)) out.add(s.word);
            if (out.size() >= 6) break;
        }
        return new Match(false, out);
    }

    static int count(Context context) {
        Data d = data(context);
        return d == null ? 0 : d.ordered.size();
    }

    private static Data data(Context context) {
        Data existing = cache;
        if (existing != null) return existing;
        synchronized (MyanmarSpellingStore.class) {
            if (cache != null) return cache;
            try {
                Set<String> set = new HashSet<>();
                List<String> list = new ArrayList<>();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(
                        context.getAssets().open(ASSET), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String w = normalize(line.replace("\uFEFF", ""));
                        if (w.isEmpty() || w.startsWith("#")) continue;
                        if (set.add(w)) list.add(w);
                    }
                }
                cache = new Data(set, list);
            } catch (Exception ignored) {
                cache = new Data(new HashSet<>(), new ArrayList<>());
            }
            return cache;
        }
    }

    private static int distance(String a, String b, int cap) {
        int[] x = toCodePoints(a);
        int[] y = toCodePoints(b);
        if (Math.abs(x.length - y.length) > cap) return cap + 1;
        int[] prev = new int[y.length + 1];
        int[] cur = new int[y.length + 1];
        for (int j = 0; j <= y.length; j++) prev[j] = j;
        for (int i = 1; i <= x.length; i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= y.length; j++) {
                int cost = x[i - 1] == y[j - 1] ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, cur[j]);
            }
            if (rowMin > cap) return cap + 1;
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[y.length];
    }

    private static int[] toCodePoints(String value) {
        if (value == null || value.isEmpty()) return new int[0];
        int count = value.codePointCount(0, value.length());
        int[] out = new int[count];
        int at = 0;
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            out[at++] = cp;
            i += Character.charCount(cp);
        }
        return out;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static final class Data {
        final Set<String> words;
        final List<String> ordered;
        Data(Set<String> words, List<String> ordered) { this.words = words; this.ordered = ordered; }
    }

    private static final class Scored {
        final String word; final int distance; final boolean prefix;
        Scored(String word, int distance, boolean prefix) { this.word = word; this.distance = distance; this.prefix = prefix; }
    }
}
