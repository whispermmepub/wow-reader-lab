package com.whisper.wowreader;

/** In-memory, account-scoped credentials. Never persists bearer tokens or replays non-401 failures. */
final class DriveAuthRetry {
    interface Refresh { String acquire(String rejectedToken) throws Exception; }
    interface Request<T> { T run() throws Exception; }
    static final class Unauthorized extends Exception {
        final String rejectedToken;
        Unauthorized(String rejectedToken) {
            super("Google Drive authorization expired; reconnect Google Drive and retry. Local data is preserved.");
            this.rejectedToken = rejectedToken;
        }
    }
    private volatile String token;
    private final Refresh refresh;
    DriveAuthRetry(String token, Refresh refresh) { this.token = token; this.refresh = refresh; }
    String token() { return token; }
    private synchronized void refresh(String rejected) throws Exception {
        // Parallel restore workers rejected on the old token share one refresh.
        if (!token.equals(rejected)) return;
        String next = refresh.acquire(rejected);
        if (next == null || next.trim().isEmpty() || next.equals(rejected))
            throw new IllegalStateException("Google Drive needs reconnect; local data is preserved. Retry after reconnecting.");
        token = next;
    }
    <T> T execute(Request<T> request) throws Exception {
        try { return request.run(); }
        catch (Unauthorized rejected) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            refresh(rejected.rejectedToken);
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            return request.run(); // At most one replay per request, even if the new token is rejected.
        }
    }
}
