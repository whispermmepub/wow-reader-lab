package com.whisper.wowreader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Content-weighted EPUB progress model. Built once per extracted EPUB and cached. */
final class EpubProgressModel {
    private final long[] weights;
    private final long[] prefix;
    private final long total;

    private EpubProgressModel(long[] weights) {
        this.weights = weights == null ? new long[0] : weights;
        this.prefix = new long[this.weights.length + 1];
        long t = 0L;
        for (int i = 0; i < this.weights.length; i++) {
            long w = Math.max(0L, this.weights[i]);
            if (Long.MAX_VALUE - t < w) t = Long.MAX_VALUE;
            else t += w;
            this.prefix[i + 1] = t;
        }
        this.total = Math.max(1L, t);
    }

    static EpubProgressModel uniform(int count) {
        long[] w = new long[Math.max(0, count)];
        java.util.Arrays.fill(w, 1000L);
        return new EpubProgressModel(w);
    }

    static EpubProgressModel loadOrBuild(File extractDir, List<File> spine, List<String> titles) {
        if (spine == null || spine.isEmpty()) return uniform(0);
        File cache = new File(extractDir, ".wow_progress_weights_v2");
        EpubProgressModel cached = readCache(cache, spine.size());
        if (cached != null) return cached;
        long[] weights = new long[spine.size()];
        long positive = 0L;
        for (int i = 0; i < spine.size(); i++) {
            File f = spine.get(i);
            String title = titles != null && i < titles.size() ? titles.get(i) : "";
            long w = contentWeight(f, title);
            weights[i] = w;
            positive += Math.max(0L, w);
        }
        if (positive <= 0L) java.util.Arrays.fill(weights, 1000L);
        EpubProgressModel model = new EpubProgressModel(weights);
        writeCache(cache, weights);
        return model;
    }

    double overallFraction(int spineIndex, int chapterPermille) {
        if (weights.length == 0) return 0d;
        int index = Math.max(0, Math.min(weights.length - 1, spineIndex));
        int p = Math.max(0, Math.min(1000, chapterPermille));
        long before = prefix[index];
        long w = weights[index];
        double value = before + w * (p / 1000.0);
        return Math.max(0d, Math.min(1d, value / total));
    }

    Position positionForOverall(int overallPermille) {
        if (weights.length == 0) return new Position(0, 0);
        double target = Math.max(0, Math.min(1000, overallPermille)) / 1000.0 * total;
        if (overallPermille >= 1000) return new Position(weights.length - 1, 1000);
        for (int i = 0; i < weights.length; i++) {
            long start = prefix[i], end = prefix[i + 1];
            if (weights[i] <= 0L) continue;
            if (target < end || i == weights.length - 1) {
                int p = (int) Math.round(((target - start) / Math.max(1d, weights[i])) * 1000d);
                return new Position(i, Math.max(0, Math.min(1000, p)));
            }
        }
        return new Position(weights.length - 1, 1000);
    }

    static final class Position {
        final int spineIndex;
        final int chapterPermille;
        Position(int spineIndex, int chapterPermille) {
            this.spineIndex = spineIndex;
            this.chapterPermille = chapterPermille;
        }
    }

    private static long contentWeight(File file, String title) {
        try {
            byte[] buffer = new byte[64 * 1024];
            ByteArrayOutputStream bout = new ByteArrayOutputStream((int)Math.min(Math.max(1024L, file.length()), 4L * 1024L * 1024L));
            try (InputStream in = new FileInputStream(file)) {
                int n; while ((n = in.read(buffer)) > 0) bout.write(buffer, 0, n);
            }
            String raw = new String(bout.toByteArray(), StandardCharsets.UTF_8);
            String lower = raw.toLowerCase(Locale.ROOT);
            String hint = ((file == null ? "" : file.getName()) + " " + (title == null ? "" : title)).toLowerCase(Locale.ROOT);
            boolean nav = lower.contains("epub:type=\"toc\"") || lower.contains("epub:type='toc'") ||
                    lower.contains("role=\"doc-toc\"") || lower.contains("role='doc-toc'") ||
                    lower.contains("epub:type=\"cover\"") || lower.contains("epub:type='cover'") ||
                    lower.contains("epub:type=\"titlepage\"") || lower.contains("epub:type='titlepage'") ||
                    lower.contains("epub:type=\"copyright-page\"") || lower.contains("epub:type='copyright-page'");
            boolean obviousFront = hint.matches(".*(^|[^a-z])(toc|nav|cover|title[-_ ]?page|copyright|contents?)([^a-z]|$).*");
            String text = raw
                    .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                    .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                    .replaceAll("(?is)<[^>]+>", " ")
                    .replaceAll("&[a-zA-Z#0-9]+;", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            int chars = text.codePointCount(0, text.length());
            if ((nav || obviousFront) && chars < 30000) return 0L;
            if (chars < 40) return 250L; // image-only or very short readable page
            return Math.min(2_000_000L, Math.max(250L, chars));
        } catch (Exception ignored) {
            return 1000L;
        }
    }

    private static EpubProgressModel readCache(File file, int count) {
        if (file == null || !file.isFile()) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            List<Long> values = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) values.add(Long.parseLong(line));
            }
            if (values.size() != count) return null;
            long[] w = new long[count];
            for (int i = 0; i < count; i++) w[i] = Math.max(0L, values.get(i));
            return new EpubProgressModel(w);
        } catch (Exception ignored) { return null; }
    }

    private static void writeCache(File file, long[] weights) {
        if (file == null) return;
        try (FileWriter writer = new FileWriter(file, false)) {
            for (long w : weights) writer.write(Long.toString(w) + "\n");
        } catch (Exception ignored) {}
    }
}
