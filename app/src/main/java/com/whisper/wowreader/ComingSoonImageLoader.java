package com.whisper.wowreader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ComingSoonImageLoader {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    private ComingSoonImageLoader() {}

    static void load(Context context, String url, ImageView target) {
        if (context == null || target == null || url == null || url.trim().isEmpty()) return;
        final String normalized = url.trim();
        target.setTag(normalized);
        EXECUTOR.execute(() -> {
            Bitmap bitmap = null;
            try {
                File cacheDir = new File(context.getCacheDir(), "coming_soon_images");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File cached = new File(cacheDir, sha256(normalized) + ".img");
                if (cached.isFile() && cached.length() > 0) {
                    try (FileInputStream in = new FileInputStream(cached)) {
                        bitmap = BitmapFactory.decodeStream(in);
                    }
                }
                if (bitmap == null) {
                    File tmp = new File(cacheDir, cached.getName() + ".tmp");
                    HttpURLConnection c = (HttpURLConnection) new URL(normalized).openConnection();
                    c.setConnectTimeout(7000);
                    c.setReadTimeout(10000);
                    c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("User-Agent", "WoWReader/2.16 Android");
                    try {
                        int code = c.getResponseCode();
                        if (code >= 200 && code < 300) {
                            try (BufferedInputStream in = new BufferedInputStream(c.getInputStream());
                                 FileOutputStream out = new FileOutputStream(tmp)) {
                                byte[] buffer = new byte[16 * 1024];
                                int n;
                                while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
                            }
                            if (tmp.length() > 0) {
                                if (cached.exists()) cached.delete();
                                if (!tmp.renameTo(cached)) {
                                    try (FileInputStream in = new FileInputStream(tmp);
                                         FileOutputStream out = new FileOutputStream(cached)) {
                                        byte[] buffer = new byte[16 * 1024];
                                        int n;
                                        while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
                                    }
                                    tmp.delete();
                                }
                                bitmap = BitmapFactory.decodeFile(cached.getAbsolutePath());
                            }
                        }
                    } finally {
                        c.disconnect();
                        if (tmp.exists()) tmp.delete();
                    }
                }
            } catch (Exception ignored) {}

            final Bitmap ready = bitmap;
            if (ready != null) {
                target.post(() -> {
                    Object tag = target.getTag();
                    if (normalized.equals(tag)) target.setImageBitmap(ready);
                });
            }
        });
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b));
        return out.toString();
    }
}
