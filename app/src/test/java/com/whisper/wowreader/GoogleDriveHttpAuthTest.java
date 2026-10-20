package com.whisper.wowreader;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Integration-level checks for GoogleDriveSync's private HTTP/auth seam, without Google traffic. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 35, 36}, manifest = Config.NONE)
public class GoogleDriveHttpAuthTest {
    private static final String OLD = "old-token";
    private static final String FRESH = "fresh-token";
    private LocalHttpServer server;
    private ExecutorService serverExecutor;
    private String endpoint;
    private Map<String, DriveAuthRetry> credentials;

    @Before public void setUp() throws Exception {
        serverExecutor = Executors.newCachedThreadPool();
        server = new LocalHttpServer(serverExecutor);
        server.start();
        endpoint = "http://127.0.0.1:" + server.port() + "/drive";
        Field field = GoogleDriveSync.class.getDeclaredField("driveCredentials");
        field.setAccessible(true);
        credentials = (Map<String, DriveAuthRetry>) field.get(null);
        synchronized (GoogleDriveSync.class) { credentials.clear(); }
    }

    @After public void tearDown() {
        synchronized (GoogleDriveSync.class) { credentials.clear(); }
        if (server != null) server.stop(0);
        if (serverExecutor != null) serverExecutor.shutdownNow();
    }

    @Test public void authorizedJson401RefreshesAndReplaysWithNewBearer() throws Exception {
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/drive", exchange -> {
            seen.add(exchange.getRequestHeaders().getFirst("Authorization"));
            if (requests.getAndIncrement() == 0) respond(exchange, 401, "expired");
            else respond(exchange, 200, "{\"ok\":true}");
        });
        AtomicInteger refreshes = new AtomicInteger();
        credentials.put(OLD, new DriveAuthRetry(OLD, rejected -> {
            assertEquals(OLD, rejected); refreshes.incrementAndGet(); return FRESH;
        }));

        JSONObject result = invokeAuthorizedJson(endpoint, OLD);

        assertTrue(result.getBoolean("ok"));
        assertEquals(1, refreshes.get());
        assertEquals(List.of("Bearer " + OLD, "Bearer " + FRESH), seen);
    }

    @Test public void second401IsPropagatedAndDoesNotRefreshAgain() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/drive", exchange -> {
            requests.incrementAndGet(); respond(exchange, 401, "still expired");
        });
        AtomicInteger refreshes = new AtomicInteger();
        credentials.put(OLD, new DriveAuthRetry(OLD, rejected -> {
            refreshes.incrementAndGet(); return FRESH;
        }));

        try {
            invokeAuthorizedJson(endpoint, OLD);
            fail("expected second unauthorized response");
        } catch (DriveAuthRetry.Unauthorized expected) {
            assertEquals(FRESH, expected.rejectedToken);
        }
        assertEquals(2, requests.get());
        assertEquals(1, refreshes.get());
    }

    @Test public void forbiddenResponseIsNotRetried() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/drive", exchange -> {
            requests.incrementAndGet(); respond(exchange, 403, "forbidden");
        });
        AtomicInteger refreshes = new AtomicInteger();
        credentials.put(OLD, new DriveAuthRetry(OLD, rejected -> {
            refreshes.incrementAndGet(); return FRESH;
        }));

        try {
            invokeAuthorizedJson(endpoint, OLD);
            fail("expected forbidden response");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("403"));
        }
        assertEquals(1, requests.get());
        assertEquals(0, refreshes.get());
    }

    @Test public void concurrent401RequestsShareOneRefreshAndReplayWithFreshBearer() throws Exception {
        final int workers = 6;
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch firstWave = new CountDownLatch(workers);
        CountDownLatch release = new CountDownLatch(1);
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        server.createContext("/drive", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            seen.add(auth);
            if (auth.equals("Bearer " + OLD)) {
                firstWave.countDown();
                try { if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IOException("timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                respond(exchange, 401, "expired");
            } else { requests.incrementAndGet(); respond(exchange, 200, "{\"ok\":true}"); }
        });
        AtomicInteger refreshes = new AtomicInteger();
        credentials.put(OLD, new DriveAuthRetry(OLD, rejected -> {
            refreshes.incrementAndGet();
            try { Thread.sleep(30); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return FRESH;
        }));

        var pool = Executors.newFixedThreadPool(workers);
        try {
            List<java.util.concurrent.Future<JSONObject>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) futures.add(pool.submit(() -> invokeAuthorizedJson(endpoint, OLD)));
            assertTrue(firstWave.await(5, java.util.concurrent.TimeUnit.SECONDS));
            release.countDown();
            for (var future : futures) assertTrue(future.get(5, java.util.concurrent.TimeUnit.SECONDS).getBoolean("ok"));
        } finally { release.countDown(); pool.shutdownNow(); }
        assertEquals(1, refreshes.get());
        assertEquals(workers, requests.get());
        assertEquals(workers, seen.stream().filter(("Bearer " + OLD)::equals).count());
    }

    @Test public void responseCodeRejectsTheBearerRecordedFor401() throws Exception {
        HttpURLConnection connection = new UnauthorizedConnection();
        Field tokens = GoogleDriveSync.class.getDeclaredField("requestTokens");
        tokens.setAccessible(true);
        Map<HttpURLConnection, String> requestTokens = (Map<HttpURLConnection, String>) tokens.get(null);
        requestTokens.put(connection, OLD);
        Method responseCode = GoogleDriveSync.class.getDeclaredMethod("responseCode", HttpURLConnection.class);
        responseCode.setAccessible(true);
        try {
            responseCode.invoke(null, connection);
            fail("expected unauthorized");
        } catch (InvocationTargetException wrapped) {
            assertTrue(wrapped.getCause() instanceof DriveAuthRetry.Unauthorized);
            assertEquals(OLD, ((DriveAuthRetry.Unauthorized) wrapped.getCause()).rejectedToken);
        } finally { requestTokens.remove(connection); connection.disconnect(); }
    }

    @Test public void sendUploadChunkReplaysChunkAfter401() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        List<byte[]> bodies = Collections.synchronizedList(new ArrayList<>());
        server.createContext("/drive", exchange -> {
            bodies.add(exchange.getRequestBody().readAllBytes());
            if (requests.getAndIncrement() == 0) respond(exchange, 401, "expired");
            else respond(exchange, 200, "{\"id\":\"uploaded\"}");
        });
        AtomicInteger refreshes = new AtomicInteger();
        credentials.put(OLD, new DriveAuthRetry(OLD, rejected -> { refreshes.incrementAndGet(); return FRESH; }));
        File file = Files.createTempFile("drive-auth", ".bin").toFile();
        try (FileOutputStream out = new FileOutputStream(file)) { out.write("chunk-data".getBytes(StandardCharsets.UTF_8)); }
        RandomAccessFileHolder source = new RandomAccessFileHolder(file);
        try {
            DriveAuthRetry.Request<Object> request = () -> invokeSendUploadChunk(OLD, file, endpoint, source.raf, file.length(), file.length() - 1, (int) file.length());
            invokeWithDriveAuth(OLD, request);
        } finally { source.close(); file.delete(); }
        assertEquals(1, refreshes.get());
        assertEquals(2, requests.get());
        assertArrayEquals(bodies.get(0), bodies.get(1));
        assertEquals("chunk-data", new String(bodies.get(1), StandardCharsets.UTF_8));
    }

    private JSONObject invokeAuthorizedJson(String url, String token) throws Exception {
        Method method = GoogleDriveSync.class.getDeclaredMethod("authorizedJson", String.class, String.class);
        method.setAccessible(true);
        try { return (JSONObject) method.invoke(null, url, token); }
        catch (InvocationTargetException e) { throw rethrow(e.getCause()); }
    }

    private Object invokeSendUploadChunk(String token, File file, String session, java.io.RandomAccessFile source, long total, long end, int length) throws Exception {
        Method m = GoogleDriveSync.class.getDeclaredMethod("sendUploadChunk", String.class, File.class, String.class, java.io.RandomAccessFile.class, long.class, long.class, int.class);
        m.setAccessible(true);
        try { return m.invoke(null, token, file, session, source, total, end, length); }
        catch (InvocationTargetException e) { throw rethrow(e.getCause()); }
    }

    private Object invokeWithDriveAuth(String token, DriveAuthRetry.Request<?> request) throws Exception {
        Method m = GoogleDriveSync.class.getDeclaredMethod("withDriveAuth", String.class, DriveAuthRetry.Request.class);
        m.setAccessible(true);
        try { return m.invoke(null, token, request); }
        catch (InvocationTargetException e) { throw rethrow(e.getCause()); }
    }

    private static Exception rethrow(Throwable cause) throws Exception {
        if (cause instanceof Exception) return (Exception) cause;
        if (cause instanceof Error) throw (Error) cause;
        throw new Exception(cause);
    }

    private static void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }

    /** Minimal concurrent HTTP fixture using only java.net, available to Android test javac. */
    private static final class LocalHttpServer {
        interface Handler { void handle(HttpExchange exchange) throws IOException; }
        private final ExecutorService executor;
        private final ServerSocket socket;
        private volatile Handler handler;
        private volatile boolean running;
        LocalHttpServer(ExecutorService executor) throws IOException {
            this.executor = executor;
            this.socket = new ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"));
        }
        void createContext(String path, Handler handler) { this.handler = handler; }
        int port() { return socket.getLocalPort(); }
        void start() {
            running = true;
            executor.execute(() -> {
                while (running) {
                    try { Socket client = socket.accept(); executor.execute(() -> serve(client)); }
                    catch (IOException ignored) { if (running) throw new AssertionError(ignored); }
                }
            });
        }
        void stop(int ignoredDelay) { running = false; try { socket.close(); } catch (IOException ignored) { } }
        private void serve(Socket client) {
            try (Socket c = client) {
                c.setSoTimeout(10000);
                java.io.InputStream in = c.getInputStream();
                java.io.ByteArrayOutputStream headerBytes = new java.io.ByteArrayOutputStream();
                int previous = -1, current;
                while ((current = in.read()) != -1) {
                    headerBytes.write(current);
                    if (previous == '\r' && current == '\n') {
                        byte[] h = headerBytes.toByteArray(); int n = h.length;
                        if (n >= 4 && h[n - 4] == '\r' && h[n - 3] == '\n') break;
                    }
                    previous = current;
                }
                String[] lines = headerBytes.toString(StandardCharsets.ISO_8859_1.name()).split("\r\n");
                java.util.Map<String, String> requestHeaders = new java.util.HashMap<>();
                int contentLength = 0;
                for (int i = 1; i < lines.length; i++) {
                    int colon = lines[i].indexOf(':');
                    if (colon > 0) {
                        String key = lines[i].substring(0, colon).trim();
                        requestHeaders.put(key, lines[i].substring(colon + 1).trim());
                        if (key.equalsIgnoreCase("Content-Length")) contentLength = Integer.parseInt(requestHeaders.get(key));
                    }
                }
                byte[] body = in.readNBytes(contentLength);
                HttpExchange exchange = new HttpExchange(c, requestHeaders, body);
                Handler currentHandler = handler;
                if (currentHandler == null) throw new IOException("No HTTP handler registered");
                currentHandler.handle(exchange);
            } catch (IOException ignored) { }
        }
    }

    private static final class HttpExchange {
        private final Socket socket;
        private final Map<String, String> headers;
        private final byte[] body;
        HttpExchange(Socket socket, Map<String, String> headers, byte[] body) { this.socket = socket; this.headers = headers; this.body = body; }
        RequestHeaders getRequestHeaders() { return new RequestHeaders(headers); }
        java.io.InputStream getRequestBody() { return new java.io.ByteArrayInputStream(body); }
        void sendResponseHeaders(int code, long length) throws IOException {
            String reason = code == 200 ? "OK" : code == 401 ? "Unauthorized" : code == 403 ? "Forbidden" : "Response";
            socket.getOutputStream().write(("HTTP/1.1 " + code + " " + reason + "\r\nContent-Length: " + length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        }
        java.io.OutputStream getResponseBody() throws IOException { return socket.getOutputStream(); }
    }

    private static final class RequestHeaders {
        private final Map<String, String> values;
        RequestHeaders(Map<String, String> values) { this.values = values; }
        String getFirst(String key) {
            for (Map.Entry<String, String> e : values.entrySet()) if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
            return null;
        }
    }

    private static final class RandomAccessFileHolder {
        final java.io.RandomAccessFile raf;
        RandomAccessFileHolder(File file) throws IOException { raf = new java.io.RandomAccessFile(file, "r"); }
        void close() throws IOException { raf.close(); }
    }

    private static final class UnauthorizedConnection extends HttpURLConnection {
        UnauthorizedConnection() throws Exception { super(new URL("http://stub.invalid/")); }
        @Override public void connect() { connected = true; }
        @Override public void disconnect() { connected = false; }
        @Override public boolean usingProxy() { return false; }
        @Override public int getResponseCode() { return 401; }
    }
}
