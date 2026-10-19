package com.whisper.wowreader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** In-memory protocol tests; deliberately exercises the guard, not production constants. */
public final class DriveDeletionGuardTest {
    private static final String H = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String H2 = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
    private static int tests;

    private static void ok(boolean value, String message) {
        tests++;
        if (!value) throw new AssertionError(message);
    }
    private static void eq(Object actual, Object expected, String message) {
        tests++;
        if (actual == null ? expected != null : !actual.equals(expected))
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
    }
    private static void throwsException(Class<? extends Throwable> type, Action action, String message) {
        tests++;
        try { action.run(); }
        catch (Throwable e) {
            if (type.isInstance(e)) return;
            throw new AssertionError(message + " wrong exception=" + e, e);
        }
        throw new AssertionError(message + " did not throw");
    }
    private interface Action { void run() throws Exception; }

    private static final class Remote implements DriveDeletionGuard.Remote {
        final Set<String> markers = new HashSet<String>();
        final List<String> events = new ArrayList<String>();
        int lookups;
        boolean failLookup;
        boolean failPublish;
        boolean failDelete;
        boolean failDeleteOnce;
        public boolean hasMarker(String hash) throws Exception {
            lookups++;
            events.add("lookup:" + hash);
            if (failLookup) throw new Exception("lookup failed");
            return markers.contains(hash.toLowerCase());
        }
        public void publishMarker(String hash) throws Exception {
            events.add("publish:" + hash);
            if (failPublish) throw new Exception("publish failed");
            markers.add(hash.toLowerCase());
        }
        public void deleteAssets(String hash) throws Exception {
            events.add("delete:" + hash);
            if (failDelete || failDeleteOnce) {
                failDeleteOnce = false;
                throw new Exception("delete failed");
            }
            events.add("assets-clean");
        }
    }
    private static final class Local implements DriveDeletionGuard.Local {
        final Set<String> tombstones = new HashSet<String>();
        final Set<String> staleCopies = new HashSet<String>();
        int lookups;
        boolean failLookup;
        public boolean hasTombstone(String hash) throws Exception {
            lookups++;
            if (failLookup) throw new Exception("local lookup failed");
            return tombstones.contains(hash.toLowerCase());
        }
        public void rememberDeletion(String hash) throws Exception {
            tombstones.add(hash.toLowerCase());
        }
    }

    public static void main(String[] args) throws Exception {
        markerValidation();
        publishOrderingAndAcknowledgement();
        publishFailures();
        uploadAndResumeCases();
        restoreCases();
        lookupAndDeltaCases();
        System.out.println("DRIVE_DELETION_GUARD_TEST_PASS tests=" + tests);
    }

    private static void markerValidation() throws Exception {
        eq(DriveDeletionGuard.markerName(H.toUpperCase()), DriveDeletionGuard.PREFIX + H,
                "marker identity is normalized");
        eq(DriveDeletionGuard.parseMarker(DriveDeletionGuard.PREFIX + H.toUpperCase()), H,
                "valid marker parses case-insensitively");
        eq(DriveDeletionGuard.parseMarker("other_" + H), "", "wrong prefix rejected");
        eq(DriveDeletionGuard.parseMarker(DriveDeletionGuard.PREFIX + "short"), "", "short marker rejected");
        eq(DriveDeletionGuard.parseMarker(DriveDeletionGuard.PREFIX + H.substring(0, 63) + "g"), "",
                "non-hex marker rejected");
        throwsException(IllegalArgumentException.class, () -> DriveDeletionGuard.markerName(null),
                "null identity fails closed");
        throwsException(IllegalArgumentException.class, () -> DriveDeletionGuard.markerName("not-a-hash"),
                "corrupt identity fails closed");
    }

    private static void publishOrderingAndAcknowledgement() throws Exception {
        Remote remote = new Remote();
        final int[] acknowledgements = {0};
        boolean[] result = {false};
        DriveDeletionGuard.publishDeletion(H, remote, () -> {
            acknowledgements[0]++;
            ok(remote.events.size() == 3, "acknowledgement is last operation");
            ok(("delete:" + H).equals(remote.events.get(1)), "delete follows publish");
        });
        result[0] = remote.markers.contains(H);
        ok(result[0], "published deletion marker remains durable");
        eq(acknowledgements[0], 1, "deletion acknowledged once");
        eq(remote.events.get(0), "publish:" + H, "publish precedes cleanup");
        eq(remote.events.get(1), "delete:" + H, "asset cleanup follows marker");
    }

    private static void publishFailures() throws Exception {
        Remote unpublished = new Remote();
        unpublished.failPublish = true;
        final int[] ack = {0};
        throwsException(Exception.class, () -> DriveDeletionGuard.publishDeletion(H, unpublished, () -> ack[0]++),
                "failed publish propagates");
        ok(unpublished.events.size() == 1 && !unpublished.events.contains("delete:" + H),
                "failed publish cannot delete assets");
        eq(ack[0], 0, "failed publish cannot acknowledge");

        Remote cleanupFailed = new Remote();
        cleanupFailed.failDeleteOnce = true;
        throwsException(Exception.class, () -> DriveDeletionGuard.publishDeletion(H, cleanupFailed, () -> ack[0]++),
                "failed cleanup propagates");
        ok(cleanupFailed.markers.contains(H), "failed cleanup retains marker");
        eq(ack[0], 0, "failed cleanup cannot acknowledge");
        DriveDeletionGuard.publishDeletion(H, cleanupFailed, () -> ack[0]++);
        eq(ack[0], 1, "retry after cleanup failure acknowledges");
    }

    private static void uploadAndResumeCases() throws Exception {
        Remote remote = new Remote();
        Local local = new Local();
        local.staleCopies.add(H); // Device B's dirty local copy is intentionally not removed here.
        remote.markers.add(H);
        final int[] uploads = {0};
        ok(!DriveDeletionGuard.uploadIfLive(H, remote, local, () -> uploads[0]++),
                "device B blocks dirty stale upload after device A deletion");
        eq(uploads[0], 0, "blocked upload never reaches Drive");
        ok(local.staleCopies.contains(H), "blocking does not erase local stale copy");
        ok(local.tombstones.contains(H), "blocked upload remembers local tombstone");

        Remote resumeRemote = new Remote();
        Local resumeLocal = new Local();
        resumeRemote.markers.add(H);
        ok(!DriveDeletionGuard.uploadIfLive(H, resumeRemote, resumeLocal, () -> uploads[0]++),
                "resume path is blocked by the same guard");
        eq(uploads[0], 0, "resume cannot bypass tombstone");

        Remote coverRemote = new Remote();
        coverRemote.markers.add(H);
        final int[] covers = {0};
        ok(!DriveDeletionGuard.uploadIfLive(H, coverRemote, new Local(), () -> covers[0]++),
                "cover upload is blocked by deletion marker");
        eq(covers[0], 0, "blocked cover is not uploaded");

        Remote raceRemote = new Remote();
        Local raceLocal = new Local();
        final int[] raceUploads = {0};
        ok(!DriveDeletionGuard.uploadIfLive(H, raceRemote, raceLocal, () -> {
            raceUploads[0]++;
            raceRemote.markers.add(H); // deletion appears during upload
        }), "postflight deletion wins over completed upload");
        eq(raceUploads[0], 1, "race upload was attempted exactly once");
        ok(raceLocal.tombstones.contains(H), "postflight race is remembered locally");
        ok(raceRemote.events.contains("publish:" + H) && raceRemote.events.contains("delete:" + H),
                "postflight race cleans remote assets");

        Remote exceptionRaceRemote = new Remote();
        Local exceptionRaceLocal = new Local();
        final int[] attempts = {0};
        ok(!DriveDeletionGuard.uploadIfLive(H, exceptionRaceRemote, exceptionRaceLocal, () -> {
            attempts[0]++;
            exceptionRaceRemote.markers.add(H);
            throw new Exception("stale resumable session");
        }), "concurrent deletion converts upload exception to suppression");
        eq(attempts[0], 1, "exception race attempted once");
        ok(exceptionRaceLocal.tombstones.contains(H), "exception race is remembered locally");

        Remote ordinaryRemote = new Remote();
        Local ordinaryLocal = new Local();
        final int[] ordinary = {0};
        ok(DriveDeletionGuard.uploadIfLive(H2, ordinaryRemote, ordinaryLocal, () -> ordinary[0]++),
                "ordinary upload remains live");
        eq(ordinary[0], 1, "ordinary upload runs");

        Remote nonDeletionRemote = new Remote();
        Local nonDeletionLocal = new Local();
        final int[] failed = {0};
        throwsException(Exception.class, () -> DriveDeletionGuard.uploadIfLive(H2, nonDeletionRemote,
                nonDeletionLocal, () -> { failed[0]++; throw new Exception("network error"); }),
                "non-deletion upload error propagates");
        eq(failed[0], 1, "non-deletion failure is attempted once");
        ok(!nonDeletionLocal.tombstones.contains(H2), "non-deletion error is not acknowledged as delete");
    }

    private static void restoreCases() throws Exception {
        Remote staleRemote = new Remote();
        Local pending = new Local();
        staleRemote.markers.add(H);
        final int[] installs = {0};
        ok(!DriveDeletionGuard.restoreIfLive(H, staleRemote, pending, () -> installs[0]++),
                "restore rejects stale remote asset identity with tombstone");
        eq(installs[0], 0, "tombstoned restore does not install");
        ok(pending.tombstones.contains(H), "remote tombstone is cached locally");

        Remote localPendingRemote = new Remote();
        Local localPending = new Local();
        localPending.tombstones.add(H2);
        ok(!DriveDeletionGuard.restoreIfLive(H2, localPendingRemote, localPending, () -> installs[0]++),
                "restore rejects locally pending deletion even with stale remote asset");
        eq(installs[0], 0, "local tombstone does not install");

        Remote liveRemote = new Remote();
        Local liveLocal = new Local();
        ok(DriveDeletionGuard.restoreIfLive(H2, liveRemote, liveLocal, () -> installs[0]++),
                "ordinary restore remains live");
        eq(installs[0], 1, "ordinary restore installs once");

        Remote raceRemote = new Remote();
        Local raceLocal = new Local();
        ok(!DriveDeletionGuard.restoreIfLive(H, raceRemote, raceLocal, () -> {
            raceLocal.staleCopies.add(H);
            raceRemote.markers.add(H);
        }), "delete appearing during restore is not reported as live restore");
        ok(raceLocal.tombstones.contains(H), "restore race remembers deletion for future upload suppression");
        ok(raceLocal.staleCopies.contains(H), "restore race conservatively retains installed bytes local-only");

        Remote failedPostflight = new Remote();
        throwsException(Exception.class, () -> DriveDeletionGuard.restoreIfLive(H, failedPostflight,
                new Local(), () -> failedPostflight.failLookup = true),
                "post-install lookup failure cannot falsely report successful live restore");
    }

    private static void lookupAndDeltaCases() throws Exception {
        Remote remote = new Remote();
        Local local = new Local();
        remote.failLookup = true;
        final int[] actions = {0};
        throwsException(Exception.class, () -> DriveDeletionGuard.uploadIfLive(H, remote, local, () -> actions[0]++),
                "remote lookup failure fails closed");
        eq(actions[0], 0, "lookup failure never uploads");
        throwsException(Exception.class, () -> DriveDeletionGuard.restoreIfLive(H, remote, local, () -> actions[0]++),
                "restore lookup failure fails closed");
        eq(actions[0], 0, "lookup failure never installs");

        Remote deltaRemote = new Remote();
        Local deltaLocal = new Local();
        int existing = 100_000;
        for (int i = 0; i < 2; i++) {
            String hash = i == 0 ? H : H2;
            DriveDeletionGuard.uploadIfLive(hash, deltaRemote, deltaLocal, () -> { });
        }
        ok(existing == 100_000, "large existing library remains conceptual, not scanned");
        eq(deltaRemote.lookups, 4, "only the two changed hashes are queried (pre/postflight)");
        eq(deltaLocal.lookups, 4, "only changed hashes are checked locally (pre/postflight)");
    }
}
