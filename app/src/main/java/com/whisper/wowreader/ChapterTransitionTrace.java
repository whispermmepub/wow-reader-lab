package com.whisper.wowreader;

import java.util.LinkedHashMap;
import java.util.Map;

/** One active transition only. No book name, URL, text, or persistent reader state. */
final class ChapterTransitionTrace {
    private int generation = -1;
    private long startedMs;
    private String route = "cold";
    private final Map<String, Long> stages = new LinkedHashMap<>();
    private final Map<String, Long> durations = new LinkedHashMap<>();

    void begin(int generation, long requestedMs, long nowMs, String route) {
        this.generation = generation;
        this.startedMs = requestedMs > 0 && requestedMs <= nowMs ? requestedMs : nowMs;
        this.route = "warm_ready".equals(route) || "warm_inflight".equals(route) ? route : "cold";
        stages.clear();
        durations.clear();
    }

    boolean mark(int generation, String stage, long nowMs) {
        if (generation != this.generation || !isStage(stage) || stages.containsKey(stage)) return false;
        stages.put(stage, Math.max(0L, nowMs - startedMs));
        return true;
    }

    boolean duration(int generation, String stage, double durationMs) {
        if (generation != this.generation || !isDuration(stage) || Double.isNaN(durationMs)
                || Double.isInfinite(durationMs) || durationMs < 0 || durationMs > 120000
                || durations.containsKey(stage)) return false;
        durations.put(stage, Math.round(durationMs));
        return true;
    }

    String summary(int generation) {
        if (generation != this.generation) return "";
        StringBuilder out = new StringBuilder("generation=").append(generation).append(" route=").append(route);
        for (Map.Entry<String, Long> entry : stages.entrySet())
            out.append(' ').append(entry.getKey()).append("_at_ms=").append(entry.getValue());
        for (Map.Entry<String, Long> entry : durations.entrySet())
            out.append(' ').append(entry.getKey()).append("_ms=").append(entry.getValue());
        return out.toString();
    }

    private static boolean isStage(String stage) {
        return "document_finished".equals(stage) || "style_submitted".equals(stage)
                || "engine_usable".equals(stage) || "map_complete".equals(stage)
                || "visual_commit".equals(stage) || "failed".equals(stage);
    }

    private static boolean isDuration(String stage) {
        return "fonts_wait".equals(stage) || "images_wait".equals(stage)
                || "image_decode".equals(stage) || "layout_prepare".equals(stage)
                || "page_scan".equals(stage);
    }
}
