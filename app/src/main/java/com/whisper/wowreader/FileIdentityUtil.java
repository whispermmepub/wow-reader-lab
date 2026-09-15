package com.whisper.wowreader;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

final class FileIdentityUtil {
    private FileIdentityUtil() {}

    static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[128 * 1024];
        try (InputStream in = new FileInputStream(file)) {
            int n;
            while ((n = in.read(buffer)) > 0) digest.update(buffer, 0, n);
        }
        return hex(digest.digest());
    }

    static String copyAndSha256(InputStream in, java.io.OutputStream out) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[128 * 1024];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
            digest.update(buffer, 0, n);
        }
        out.flush();
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
        return sb.toString();
    }
}
