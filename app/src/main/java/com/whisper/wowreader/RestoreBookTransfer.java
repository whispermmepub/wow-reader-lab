package com.whisper.wowreader;

import java.io.File;

/** One content-addressed restore. Download may run on a worker; install/bind runs on the caller. */
final class RestoreBookTransfer implements RestoreTransfers.Task {
    interface Destination { File resolve() throws Exception; }
    interface Download { void run(File scratch) throws Exception; }
    interface Bind { void run(File target) throws Exception; }

    private final String hash;
    private final DriveDeletionGuard.Remote remote;
    private final DriveDeletionGuard.Local local;
    private final Destination destination;
    private final Download download;
    private final Bind bind;
    private boolean downloaded;

    RestoreBookTransfer(String hash, DriveDeletionGuard.Remote remote, DriveDeletionGuard.Local local,
                        Destination destination, Download download, Bind bind) {
        this.hash = hash;
        this.remote = remote;
        this.local = local;
        this.destination = destination;
        this.download = download;
        this.bind = bind;
    }

    public boolean download(File scratch) throws Exception {
        if (DriveDeletionGuard.isDeleted(hash, remote, local)) {
            local.rememberDeletion(hash);
            return false;
        }
        // Destination resolution verifies the hash of any existing match. Never trust a DB path alone.
        if (!destination.resolve().isFile()) {
            download.run(scratch);
            downloaded = true;
        }
        return true;
    }

    public boolean install(File scratch) throws Exception {
        File target = destination.resolve();
        if (!downloaded && !target.isFile()) {
            // Local match vanished/changed since prefetch. Recover bytes rather than bind a stale path.
            download.run(scratch);
            downloaded = true;
        }
        boolean live = DriveDeletionGuard.restoreIfLive(hash, remote, local, () -> {
            if (downloaded) BookRestoreFiles.install(scratch, target, hash);
            else if (!target.isFile() || !hash.equalsIgnoreCase(FileIdentityUtil.sha256(target)))
                throw new java.io.IOException("Local book changed during restore; retry safely");
            bind.run(target);
        });
        // Already-local verified objects are bound, but are not newly restored media.
        return live && downloaded;
    }
}
