package com.whisper.wowreader;

/** Pure decisions for the EPUB chapter handoff; no Android/WebView dependencies. */
public final class ChapterTransitionPolicy {
    private ChapterTransitionPolicy() {}

    public static final long PRELOAD_WAIT_MS = 180L;
    public static final long READY_TIMEOUT_MS = 900L;
    public static final int NORMAL_WARM_WEBVIEWS = 1;
    public static final int LOW_RAM_WARM_WEBVIEWS = 1;

    public static final class Inputs {
        public final boolean animationNone;
        public final boolean lowRam;
        public final boolean preloadReady;
        public final boolean chapterLoading;
        public final boolean userInteracting;
        public final boolean wholeBookCounterRunning;
        public final int requestGeneration;
        public final int currentGeneration;

        public Inputs(boolean animationNone, boolean lowRam, boolean preloadReady,
                boolean chapterLoading, boolean userInteracting,
                boolean wholeBookCounterRunning, int requestGeneration, int currentGeneration) {
            this.animationNone = animationNone;
            this.lowRam = lowRam;
            this.preloadReady = preloadReady;
            this.chapterLoading = chapterLoading;
            this.userInteracting = userInteracting;
            this.wholeBookCounterRunning = wholeBookCounterRunning;
            this.requestGeneration = requestGeneration;
            this.currentGeneration = currentGeneration;
        }
    }

    public static final class Decision {
        public final boolean stale;
        public final boolean captureOutgoing;
        public final boolean activateWarmChapter;
        public final boolean allowWholeBookCounter;
        public final int warmWebViewBudget;
        public final long readyTimeoutMs;

        private Decision(boolean stale, boolean captureOutgoing, boolean activateWarmChapter,
                boolean allowWholeBookCounter, int warmWebViewBudget, long readyTimeoutMs) {
            this.stale = stale;
            this.captureOutgoing = captureOutgoing;
            this.activateWarmChapter = activateWarmChapter;
            this.allowWholeBookCounter = allowWholeBookCounter;
            this.warmWebViewBudget = warmWebViewBudget;
            this.readyTimeoutMs = readyTimeoutMs;
        }
    }

    public static Decision decide(Inputs in) {
        boolean stale = in.requestGeneration != in.currentGeneration;
        int warm = in.lowRam ? LOW_RAM_WARM_WEBVIEWS : NORMAL_WARM_WEBVIEWS;
        boolean busy = in.chapterLoading || in.userInteracting;
        boolean activate = !stale && in.preloadReady;
        // None is a direct handoff: retain the live outgoing WebView until the warm
        // chapter is ready rather than paying for a screenshot/fade on every device.
        boolean capture = !stale && !in.animationNone;
        boolean count = !stale && !busy && !in.wholeBookCounterRunning;
        return new Decision(stale, capture, activate, count, warm, READY_TIMEOUT_MS);
    }

    public static boolean generationCurrent(int request, int current) {
        return request == current;
    }
}
