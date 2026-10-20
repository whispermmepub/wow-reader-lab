package com.whisper.wowreader;

import java.io.*;
import java.util.Locale;

/** Content-addressed installation for restored books; never replaces different local bytes. */
final class BookRestoreFiles {
    private static final Object INSTALL_LOCK = new Object();
    private BookRestoreFiles() { }

    static File destination(File library, String preferred, String hash, String extension) throws Exception {
        if (hash == null || !hash.matches("(?i)[0-9a-f]{64}")) throw new IOException("Invalid restored book identity");
        if (!"epub".equals(extension) && !"pdf".equals(extension)) throw new IOException("Unsupported restored book format");
        File root = library.getCanonicalFile();
        DriveRestorePlanner.BookObject object = new DriveRestorePlanner.BookObject(hash.toLowerCase(Locale.ROOT), extension);
        File first = new File(root, DriveRestorePlanner.safeLocalName(preferred, object)).getCanonicalFile();
        if (!root.equals(first.getParentFile())) first = new File(root, object.hash + "." + extension);
        if (available(first, hash)) return first;
        for (int i = 0; i < 10000; i++) {
            String suffix = i == 0 ? "" : "-cloud-" + i;
            File candidate = new File(root, object.hash + suffix + "." + extension);
            if (available(candidate, hash)) return candidate;
        }
        throw new IOException("Unable to find a safe restored book name");
    }

    private static boolean available(File target, String hash) throws Exception {
        if (!target.getAbsoluteFile().equals(target.getCanonicalFile())) return false;
        return !target.exists() || (target.isFile() && hash.equalsIgnoreCase(FileIdentityUtil.sha256(target)));
    }

    static void install(File source, File target, String hash) throws Exception {
        synchronized (INSTALL_LOCK) {
            if (!source.isFile())
                throw new IOException("Cloud book integrity check failed");
            if (target.exists()) {
                // Repeated/legacy restores need validation, not an extra full pending-file write.
                if (!hash.equalsIgnoreCase(FileIdentityUtil.sha256(source)))
                    throw new IOException("Cloud book integrity check failed");
                if (target.isFile() && hash.equalsIgnoreCase(FileIdentityUtil.sha256(target))) return;
                throw new IOException("A different local book occupies the restore destination; it was not changed");
            }
            File part = File.createTempFile(".wow-restore-", ".pending", target.getParentFile());
            boolean claimed = false;
            try {
                try (InputStream in = new BufferedInputStream(new FileInputStream(source));
                     FileOutputStream fileOut = new FileOutputStream(part);
                     OutputStream out = new BufferedOutputStream(fileOut)) {
                    // The bytes copied to the pending file are the bytes validated. This avoids
                    // reading a large Drive file once to hash it and again to install it.
                    String copiedHash = FileIdentityUtil.copyAndSha256(in, out);
                    out.flush();
                    fileOut.getFD().sync();
                    if (!hash.equalsIgnoreCase(copiedHash))
                        throw new IOException("Cloud book integrity check failed");
                }
                // Source validation must precede this check: a valid existing target does not
                // make an unvalidated or changed source acceptable.
                if (target.exists()) {
                    if (target.isFile() && hash.equalsIgnoreCase(FileIdentityUtil.sha256(target))) return;
                    throw new IOException("A different local book occupies the restore destination; it was not changed");
                }
                // Atomically reserve a previously absent name. Imports also respect existing names.
                if (!target.createNewFile()) throw new IOException("Restore destination changed; no local book was replaced");
                claimed = true;
                if (!part.renameTo(target)) throw new IOException("Unable to install restored book");
                claimed = false;
            } finally {
                if (claimed && target.length() == 0) target.delete();
                part.delete();
            }
        }
    }
}
