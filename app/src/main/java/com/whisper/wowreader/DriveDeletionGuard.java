package com.whisper.wowreader;

import java.util.Locale;

/** Delete-wins protocol. Markers are immutable; clocks and filenames are not identities. */
final class DriveDeletionGuard {
    static final String PREFIX = "wow_deleted_v1_";

    interface Remote {
        boolean hasMarker(String hash) throws Exception;
        void publishMarker(String hash) throws Exception;
        void deleteAssets(String hash) throws Exception;
    }
    interface Local {
        boolean hasTombstone(String hash) throws Exception;
        void rememberDeletion(String hash) throws Exception;
    }
    interface Action { void run() throws Exception; }

    static String markerName(String hash) {
        if (hash == null || !hash.matches("(?i)[0-9a-f]{64}"))
            throw new IllegalArgumentException("A SHA-256 book identity is required");
        return PREFIX + hash.toLowerCase(Locale.ROOT);
    }

    static String parseMarker(String name) {
        if (name == null || !name.startsWith(PREFIX)) return "";
        String hash = name.substring(PREFIX.length());
        return hash.matches("(?i)[0-9a-f]{64}") ? hash.toLowerCase(Locale.ROOT) : "";
    }

    static boolean isDeleted(String hash, Remote remote, Local local) throws Exception {
        markerName(hash); // Fail closed on missing/corrupt identity.
        return local.hasTombstone(hash) || remote.hasMarker(hash);
    }

    static void publishDeletion(String hash, Remote remote, Action acknowledge) throws Exception {
        markerName(hash);
        // A crash or failed cleanup must never erase the durable intent or acknowledge it early.
        remote.publishMarker(hash);
        remote.deleteAssets(hash);
        acknowledge.run();
    }

    private static void suppress(String hash, Remote remote, Local local) throws Exception {
        remote.publishMarker(hash);
        remote.deleteAssets(hash);
        local.rememberDeletion(hash);
    }

    static boolean uploadIfLive(String hash, Remote remote, Local local, Action upload) throws Exception {
        if (isDeleted(hash, remote, local)) {
            suppress(hash, remote, local);
            return false;
        }
        try {
            upload.run();
        } catch (Exception error) {
            // A concurrent delete can invalidate an existing file ID or resumable session.
            if (!isDeleted(hash, remote, local)) throw error;
            suppress(hash, remote, local);
            return false;
        }
        // Cover uploads and resumed book uploads follow exactly the same protocol.
        if (isDeleted(hash, remote, local)) {
            suppress(hash, remote, local);
            return false;
        }
        return true;
    }

    static boolean restoreIfLive(String hash, Remote remote, Local local, Action install) throws Exception {
        if (isDeleted(hash, remote, local)) {
            local.rememberDeletion(hash);
            return false;
        }
        install.run();
        if (isDeleted(hash, remote, local)) {
            // Do not erase an existing/local file as race recovery. Retain it local-only.
            local.rememberDeletion(hash);
            return false;
        }
        return true;
    }

    private DriveDeletionGuard() {}
}
