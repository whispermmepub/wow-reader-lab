package com.whisper.wowreader;

public final class ChapterTransitionPolicyTest {
    public static void main(String[] args) {
        ChapterTransitionPolicy.Decision fast = ChapterTransitionPolicy.decide(
                new ChapterTransitionPolicy.Inputs(true, false, true, false, false, false, 4, 4));
        assertTrue(!fast.captureOutgoing, "none animation does not capture bitmap");
        assertTrue(fast.activateWarmChapter, "ready warm chapter activates");
        assertEquals(ChapterTransitionPolicy.READY_TIMEOUT_MS, fast.readyTimeoutMs, "bounded timeout");
        assertEquals(ChapterTransitionPolicy.NORMAL_WARM_WEBVIEWS, fast.warmWebViewBudget, "one warm webview");

        ChapterTransitionPolicy.Decision low = ChapterTransitionPolicy.decide(
                new ChapterTransitionPolicy.Inputs(false, true, false, false, false, false, 5, 5));
        assertTrue(low.captureOutgoing, "animated mode retains capture behavior");
        assertEquals(ChapterTransitionPolicy.LOW_RAM_WARM_WEBVIEWS, low.warmWebViewBudget, "low RAM one warm webview");

        ChapterTransitionPolicy.Decision stale = ChapterTransitionPolicy.decide(
                new ChapterTransitionPolicy.Inputs(true, false, true, false, false, false, 6, 7));
        assertTrue(stale.stale, "stale callback rejected");
        assertTrue(!stale.activateWarmChapter && !stale.captureOutgoing, "stale callback cannot overwrite transition");

        ChapterTransitionPolicy.Decision busy = ChapterTransitionPolicy.decide(
                new ChapterTransitionPolicy.Inputs(true, true, false, true, false, false, 8, 8));
        assertTrue(!busy.allowWholeBookCounter, "counter deferred while chapter loads");
        ChapterTransitionPolicy.Decision waitingReady = ChapterTransitionPolicy.decide(
                new ChapterTransitionPolicy.Inputs(true, true, true, true, false, false, 8, 8));
        assertTrue(waitingReady.activateWarmChapter, "ready preload must activate while navigation waits");
        assertTrue(!waitingReady.allowWholeBookCounter, "handoff still has priority over counting");
        ChapterTransitionPolicy.Decision touch = ChapterTransitionPolicy.decide(
                new ChapterTransitionPolicy.Inputs(true, false, false, false, true, false, 9, 9));
        assertTrue(!touch.allowWholeBookCounter, "counter deferred during interaction");
        assertTrue(ChapterTransitionPolicy.generationCurrent(3, 3), "current generation accepted");
        assertTrue(!ChapterTransitionPolicy.generationCurrent(3, 4), "stale generation rejected");
        System.out.println("CHAPTER_TRANSITION_POLICY_TEST_PASS");
    }
    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
}
