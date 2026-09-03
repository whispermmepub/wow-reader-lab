package com.whisper.wowreader;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class ReaderFontStore {
    private static final long MAX_FONT_BYTES = 8L * 1024L * 1024L;

    static final class FontEntry {
        final String id;
        final String label;
        final File file;

        FontEntry(String id, String label, File file) {
            this.id = id;
            this.label = label;
            this.file = file;
        }
    }

    private ReaderFontStore() {}

    private static File dir(Context context) {
        File d = new File(context.getFilesDir(), "reader_fonts");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static List<FontEntry> list(Context context) {
        List<FontEntry> out = new ArrayList<>();
        File[] files = dir(context).listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.isFile() || !isSupportedName(f.getName())) continue;
                out.add(new FontEntry("custom:" + f.getName(), labelFromStoredName(f.getName()), f));
            }
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    static FontEntry importFont(Context context, Uri uri) throws Exception {
        String original = queryDisplayName(context, uri);
        if (original == null || original.trim().isEmpty()) original = "custom-font.ttf";
        String ext = extension(original);
        if (!isSupportedExtension(ext)) throw new Exception("Choose a TTF, OTF, WOFF or WOFF2 font");

        String base = original.substring(0, Math.max(1, original.length() - ext.length()));
        base = base.replaceAll("[\\r\\n\\t/\\\\:]+", " ").trim().replaceAll("\\s+", " ");
        if (base.isEmpty()) base = "Custom Font";
        if (base.length() > 70) base = base.substring(0, 70).trim();

        String storedName = System.currentTimeMillis() + "_" + base.replace(' ', '_') + ext.toLowerCase(Locale.ROOT);
        File destination = new File(dir(context), storedName);
        File tmp = new File(dir(context), storedName + ".tmp");

        long total = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(tmp)) {
            if (in == null) throw new Exception("Unable to read the selected font");
            int n;
            while ((n = in.read(buffer)) > 0) {
                total += n;
                if (total > MAX_FONT_BYTES) throw new Exception("Font is larger than 8 MB");
                out.write(buffer, 0, n);
            }
        } catch (Exception e) {
            tmp.delete();
            throw e;
        }

        if (total < 12 || !hasFontSignature(tmp)) {
            tmp.delete();
            throw new Exception("The selected file is not a supported font");
        }
        if (!tmp.renameTo(destination)) {
            tmp.delete();
            throw new Exception("Unable to save font");
        }
        return new FontEntry("custom:" + destination.getName(), base, destination);
    }

    static File fileForChoice(Context context, String choice) {
        if (choice == null || !choice.startsWith("custom:")) return null;
        String name = choice.substring("custom:".length());
        if (name.contains("/") || name.contains("\\") || name.contains("..")) return null;
        File f = new File(dir(context), name);
        try {
            String parent = dir(context).getCanonicalPath() + File.separator;
            if (!f.getCanonicalPath().startsWith(parent)) return null;
        } catch (Exception e) {
            return null;
        }
        return f.isFile() && isSupportedName(f.getName()) ? f : null;
    }

    static String displayNameForChoice(Context context, String choice) {
        File f = fileForChoice(context, choice);
        return f == null ? null : labelFromStoredName(f.getName());
    }

    static boolean delete(Context context, String choice) {
        File f = fileForChoice(context, choice);
        return f != null && f.delete();
    }

    static String cssFormat(File file) {
        String ext = extension(file == null ? "" : file.getName()).toLowerCase(Locale.ROOT);
        if (".otf".equals(ext)) return "opentype";
        if (".woff".equals(ext)) return "woff";
        if (".woff2".equals(ext)) return "woff2";
        return "truetype";
    }

    private static String queryDisplayName(Context context, Uri uri) {
        Cursor c = null;
        try {
            c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) {
                int index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return c.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return uri.getLastPathSegment();
    }

    private static boolean hasFontSignature(File file) {
        byte[] h = new byte[4];
        try (FileInputStream in = new FileInputStream(file)) {
            if (in.read(h) != 4) return false;
        } catch (Exception e) {
            return false;
        }
        return (h[0] == 0x00 && h[1] == 0x01 && h[2] == 0x00 && h[3] == 0x00) ||
                (h[0] == 'O' && h[1] == 'T' && h[2] == 'T' && h[3] == 'O') ||
                (h[0] == 't' && h[1] == 'r' && h[2] == 'u' && h[3] == 'e') ||
                (h[0] == 'w' && h[1] == 'O' && h[2] == 'F' && h[3] == 'F') ||
                (h[0] == 'w' && h[1] == 'O' && h[2] == 'F' && h[3] == '2');
    }

    private static boolean isSupportedName(String name) {
        return isSupportedExtension(extension(name));
    }

    private static boolean isSupportedExtension(String ext) {
        String e = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        return ".ttf".equals(e) || ".otf".equals(e) || ".woff".equals(e) || ".woff2".equals(e);
    }

    private static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    private static String labelFromStoredName(String name) {
        String n = name == null ? "Custom Font" : name;
        int underscore = n.indexOf('_');
        if (underscore > 0 && n.substring(0, underscore).matches("\\d+")) n = n.substring(underscore + 1);
        String ext = extension(n);
        if (!ext.isEmpty()) n = n.substring(0, n.length() - ext.length());
        return n.replace('_', ' ').trim();
    }
}
