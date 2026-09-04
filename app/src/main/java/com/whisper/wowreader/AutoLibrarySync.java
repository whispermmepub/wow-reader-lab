package com.whisper.wowreader;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lab v41: pulls EPUB posts indexed from @TheBookR and imports them into the
 * same private Library used by manual Add Book. The endpoint itself is a tiny
 * remote config so Lab can be provisioned without rebuilding the APK.
 */
final class AutoLibrarySync {
    interface Callback {
        void onFinished(Result result);
    }

    static final class Result {
        final int imported;
        final int skipped;
        final String error;

        Result(int imported, int skipped, String error) {
            this.imported = imported;
            this.skipped = skipped;
            this.error = error;
        }
    }

    private static final String PREFS = "wow_reader";
    private static final String CONFIG_URL =
            "https://raw.githubusercontent.com/whispermmepub/wow-reader-lab/main/cloudflare/telegram-auto-library/endpoint.txt";
    private static final String KEY_ENDPOINT = "auto_library_endpoint";
    private static final String KEY_LAST_ID = "auto_library_last_id";
    private static final long MAX_EPUB_BYTES = 20L * 1024L * 1024L;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private AutoLibrarySync() {}

    static void sync(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        if (!RUNNING.compareAndSet(false, true)) {
            if (callback != null) callback.onFinished(new Result(0, 0, null));
            return;
        }
        new Thread(() -> {
            Result result;
            try {
                result = run(app);
            } catch (Exception e) {
                result = new Result(0, 0, safeMessage(e));
            } finally {
                RUNNING.set(false);
            }
            if (callback != null) callback.onFinished(result);
        }, "wow-auto-library").start();
    }

    private static Result run(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String endpoint = resolveEndpoint(prefs);
        if (endpoint == null || endpoint.isEmpty()) return new Result(0, 0, null);

        long lastId = Math.max(0L, prefs.getLong(KEY_LAST_ID, 0L));
        JSONObject catalog = getJson(endpoint + "/api/books?after_id=" + lastId + "&limit=100");
        if (!catalog.optBoolean("ok", false)) throw new Exception("Catalog unavailable");
        JSONArray books = catalog.optJSONArray("books");
        if (books == null || books.length() == 0) return new Result(0, 0, null);

        File libraryDir = new File(context.getFilesDir(), "library");
        File coverCacheDir = new File(context.getFilesDir(), "cover_cache");
        File stagingDir = new File(context.getCacheDir(), "auto_library");
        if (!libraryDir.exists() && !libraryDir.mkdirs()) throw new Exception("Library folder unavailable");
        if (!coverCacheDir.exists()) coverCacheDir.mkdirs();
        if (!stagingDir.exists()) stagingDir.mkdirs();

        int imported = 0;
        int skipped = 0;
        long contiguousId = lastId;

        for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.optJSONObject(i);
            if (book == null) continue;
            long id = book.optLong("id", 0L);
            if (id <= contiguousId) continue;

            // Catalog is ordered ascending. Stop on the first transient failure so
            // the cursor never jumps over a book that still needs to be retried.
            try {
                if (prefs.getBoolean(doneKey(id), false)) {
                    contiguousId = id;
                    continue;
                }

                boolean downloadable = book.optBoolean("downloadable", false);
                long declaredSize = Math.max(0L, book.optLong("file_size", 0L));
                if (!downloadable || declaredSize > MAX_EPUB_BYTES) {
                    prefs.edit().putBoolean(doneKey(id), true).putLong(KEY_LAST_ID, id).apply();
                    contiguousId = id;
                    skipped++;
                    continue;
                }

                String rawName = book.optString("file_name", "book_" + id + ".epub");
                String fileName = safeFileName(rawName, id);
                if (!fileName.toLowerCase(Locale.ROOT).endsWith(".epub")) fileName += ".epub";

                File existing = new File(libraryDir, fileName);
                if (existing.isFile() && existing.length() > 0 &&
                        (declaredSize <= 0 || existing.length() == declaredSize)) {
                    markExistingAsOwned(prefs, existing, coverCacheDir);
                    prefs.edit().putBoolean(doneKey(id), true).putLong(KEY_LAST_ID, id).apply();
                    contiguousId = id;
                    skipped++;
                    continue;
                }

                File finalFile = existing.exists() ? conflictFile(libraryDir, fileName, id) : existing;
                File partial = new File(stagingDir, "book_" + id + ".part");
                if (partial.exists()) partial.delete();

                String downloadUrl = book.optString("download_url", "");
                if (downloadUrl.isEmpty()) throw new Exception("Missing book download URL");
                long written = download(downloadUrl, partial, MAX_EPUB_BYTES);
                if (declaredSize > 0 && written != declaredSize) {
                    partial.delete();
                    throw new Exception("Incomplete EPUB download");
                }
                validateEpub(partial);
                moveFile(partial, finalFile);
                importMetadata(prefs, finalFile, coverCacheDir);

                prefs.edit()
                        .putBoolean(doneKey(id), true)
                        .putLong(KEY_LAST_ID, id)
                        .putLong("auto_library_source_id_" + finalFile.getName(), id)
                        .apply();
                contiguousId = id;
                imported++;
            } catch (Exception e) {
                return new Result(imported, skipped, safeMessage(e));
            }
        }

        return new Result(imported, skipped, null);
    }

    private static String resolveEndpoint(SharedPreferences prefs) {
        String cached = normalizeEndpoint(prefs.getString(KEY_ENDPOINT, ""));
        try {
            String remote = getText(CONFIG_URL, 3500, 3500);
            if (remote != null) {
                for (String line : remote.split("\\r?\\n")) {
                    String candidate = normalizeEndpoint(line);
                    if (candidate != null && candidate.startsWith("https://")) {
                        prefs.edit().putString(KEY_ENDPOINT, candidate).apply();
                        return candidate;
                    }
                }
            }
        } catch (Exception ignored) {}
        return cached;
    }

    private static String normalizeEndpoint(String value) {
        if (value == null) return null;
        String s = value.trim();
        if (s.isEmpty() || s.startsWith("#")) return null;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s.startsWith("https://") ? s : null;
    }

    private static JSONObject getJson(String url) throws Exception {
        String text = getText(url, 5000, 8000);
        return new JSONObject(text);
    }

    private static String getText(String url, int connectTimeout, int readTimeout) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
            connection.setRequestProperty("User-Agent", "WoWReader/2.17.1 auto-library");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            try (InputStream in = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) out.append(line).append('\n');
                return out.toString().trim();
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static long download(String url, File output, long maxBytes) throws Exception {
        HttpURLConnection connection = null;
        long total = 0L;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(20000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/epub+zip,application/octet-stream,*/*");
            connection.setRequestProperty("User-Agent", "WoWReader/2.17.1 auto-library");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("Book download HTTP " + code);
            long contentLength = connection.getContentLengthLong();
            if (contentLength > maxBytes) throw new Exception("EPUB is too large for auto import");
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(output)) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    total += n;
                    if (total > maxBytes) throw new Exception("EPUB is too large for auto import");
                    out.write(buffer, 0, n);
                }
                out.getFD().sync();
            }
            return total;
        } catch (Exception e) {
            output.delete();
            throw e;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void validateEpub(File file) throws Exception {
        if (!file.isFile() || file.length() < 64) throw new Exception("Empty EPUB");
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry container = zip.getEntry("META-INF/container.xml");
            if (container == null || container.isDirectory()) throw new Exception("Invalid EPUB file");
        }
    }

    private static void moveFile(File from, File to) throws Exception {
        if (to.exists() && !to.delete()) throw new Exception("Cannot replace library file");
        if (from.renameTo(to)) return;
        try (FileInputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            out.getFD().sync();
        }
        if (!from.delete()) from.deleteOnExit();
    }

    private static void importMetadata(SharedPreferences prefs, File file, File coverCacheDir) {
        String title = stripExtension(file.getName());
        String author = "";
        try {
            EpubUtil.Summary summary = EpubUtil.extractSummary(file, coverCacheDir);
            if (summary != null) {
                if (summary.title != null && !summary.title.trim().isEmpty()) title = summary.title.trim();
                if (summary.author != null && !summary.author.trim().isEmpty()) author = summary.author.trim();
            }
        } catch (Exception ignored) {}
        prefs.edit()
                .putLong("added_at_" + file.getName(), System.currentTimeMillis())
                .putString("library_title_" + file.getName(), title)
                .putString("library_author_" + file.getName(), author)
                .putBoolean("library_owned_" + file.getName(), true)
                .putLong("sync_updated_ms", System.currentTimeMillis())
                .apply();
    }

    private static void markExistingAsOwned(SharedPreferences prefs, File file, File coverCacheDir) {
        if (!prefs.getBoolean("library_owned_" + file.getName(), false)) {
            importMetadata(prefs, file, coverCacheDir);
        }
    }

    private static String safeFileName(String original, long id) {
        String value = original == null ? "" : original.trim();
        value = value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (value.isEmpty()) value = "TheBookR_" + id + ".epub";
        return value;
    }

    private static File conflictFile(File libraryDir, String original, long id) {
        int dot = original.lastIndexOf('.');
        String base = dot > 0 ? original.substring(0, dot) : original;
        String ext = dot > 0 ? original.substring(dot) : ".epub";
        File candidate = new File(libraryDir, base + "_channel_" + id + ext);
        if (!candidate.exists()) return candidate;
        return new File(libraryDir, base + "_channel_" + id + "_" + System.currentTimeMillis() + ext);
    }

    private static String stripExtension(String name) {
        if (name == null) return "Book";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String doneKey(long id) {
        return "auto_library_done_" + id;
    }

    private static String safeMessage(Exception e) {
        if (e == null) return null;
        String value = e.getMessage();
        if (value == null || value.trim().isEmpty()) value = e.getClass().getSimpleName();
        return value.length() > 160 ? value.substring(0, 160) : value;
    }
}
