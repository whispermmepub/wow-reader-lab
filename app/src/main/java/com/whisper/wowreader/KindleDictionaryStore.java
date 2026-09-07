package com.whisper.wowreader;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.text.Html;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Imports an unencrypted PalmDOC/MOBI7 Kindle dictionary into a private local SQLite index. */
final class KindleDictionaryStore {
    private static final String DB_NAME = "kindle_dictionary_v1.db";
    private static final String PREF_SOURCE = "kindle_dictionary_source";
    private static final String PREF_COUNT = "kindle_dictionary_count";

    private KindleDictionaryStore() {}

    static final class Result {
        final String headword;
        final String meaning;
        Result(String headword, String meaning) {
            this.headword = headword == null ? "" : headword;
            this.meaning = meaning == null ? "" : meaning;
        }
    }

    static final class ImportResult {
        final String sourceName;
        final int entryCount;
        ImportResult(String sourceName, int entryCount) {
            this.sourceName = sourceName == null ? "Kindle dictionary" : sourceName;
            this.entryCount = Math.max(0, entryCount);
        }
    }

    static boolean isInstalled(Context context) {
        File f = dbFile(context);
        return f.isFile() && f.length() > 64 * 1024L && count(context) > 0;
    }

    static int count(Context context) {
        if (context == null) return 0;
        File f = dbFile(context);
        if (!f.isFile()) return 0;
        SQLiteDatabase db = null;
        Cursor c = null;
        try {
            db = SQLiteDatabase.openDatabase(f.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            c = db.rawQuery("SELECT count(*) FROM entries", null);
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Exception ignored) {
            return 0;
        } finally {
            if (c != null) try { c.close(); } catch (Exception ignored) {}
            if (db != null) try { db.close(); } catch (Exception ignored) {}
        }
    }

    static String sourceName(Context context) {
        if (context == null) return "";
        return context.getSharedPreferences("wow_reader", Context.MODE_PRIVATE).getString(PREF_SOURCE, "");
    }

    static void remove(Context context) {
        if (context == null) return;
        File f = dbFile(context);
        if (f.exists()) f.delete();
        context.getSharedPreferences("wow_reader", Context.MODE_PRIVATE).edit()
                .remove(PREF_SOURCE).remove(PREF_COUNT)
                .putLong("sync_updated_ms", System.currentTimeMillis()).apply();
    }

    static List<Result> lookup(Context context, String query, int limit) {
        List<Result> out = new ArrayList<>();
        if (context == null || query == null) return out;
        File f = dbFile(context);
        if (!f.isFile()) return out;
        String norm = normalize(query);
        if (norm.isEmpty()) return out;
        SQLiteDatabase db = null;
        Cursor c = null;
        try {
            db = SQLiteDatabase.openDatabase(f.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            c = db.rawQuery("SELECT headword,definition_html FROM entries WHERE headword_norm=? LIMIT ?",
                    new String[]{norm, String.valueOf(Math.max(1, limit))});
            while (c.moveToNext()) out.add(new Result(c.getString(0), htmlToText(c.getString(1))));
            c.close(); c = null;
            if (out.isEmpty() && norm.indexOf(' ') < 0) {
                c = db.rawQuery("SELECT headword,definition_html FROM entries WHERE headword_norm LIKE ? " +
                                "ORDER BY length(headword_norm),headword_norm LIMIT ?",
                        new String[]{norm + "%", String.valueOf(Math.max(1, limit))});
                while (c.moveToNext()) out.add(new Result(c.getString(0), htmlToText(c.getString(1))));
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) try { c.close(); } catch (Exception ignored) {}
            if (db != null) try { db.close(); } catch (Exception ignored) {}
        }
        return out;
    }

    static ImportResult importFromUri(Context context, Uri uri) throws Exception {
        if (context == null || uri == null) throw new IllegalArgumentException("Dictionary file is missing");
        File dir = new File(context.getFilesDir(), "dictionary");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create dictionary folder");
        File input = new File(dir, "kindle_import.tmp");
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(input, false)) {
            if (in == null) throw new Exception("Cannot open dictionary file");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.getFD().sync();
        }

        String displayName = displayName(context, uri);
        File target = dbFile(context);
        File staged = new File(dir, DB_NAME + ".new");
        if (staged.exists()) staged.delete();
        int imported;
        try {
            imported = buildIndex(input, staged);
            if (imported < 100) throw new Exception("This Kindle file does not contain a usable dictionary index");
            if (target.exists() && !target.delete()) throw new Exception("Cannot replace old dictionary");
            if (!staged.renameTo(target)) throw new Exception("Cannot install dictionary index");
            SharedPreferences prefs = context.getSharedPreferences("wow_reader", Context.MODE_PRIVATE);
            prefs.edit().putString(PREF_SOURCE, displayName)
                    .putInt(PREF_COUNT, imported)
                    .putLong("sync_updated_ms", System.currentTimeMillis()).apply();
            return new ImportResult(displayName, imported);
        } finally {
            input.delete();
            if (staged.exists()) staged.delete();
        }
    }

    private static int buildIndex(File input, File output) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(input, "r");
        SQLiteDatabase db = null;
        try {
            if (raf.length() < 100L) throw new Exception("Not a Kindle/MOBI dictionary");
            byte[] pdb = new byte[78];
            raf.seek(0L); raf.readFully(pdb);
            String magic = new String(pdb, 60, 8, StandardCharsets.ISO_8859_1);
            if (!"BOOKMOBI".equals(magic)) throw new Exception("Only Kindle PRC/MOBI dictionaries are supported");
            int records = u16(pdb, 76);
            if (records < 2 || records > 65535) throw new Exception("Invalid Kindle record table");
            long[] offsets = new long[records + 1];
            raf.seek(78L);
            byte[] row = new byte[8];
            for (int i = 0; i < records; i++) {
                raf.readFully(row);
                offsets[i] = u32(row, 0);
            }
            offsets[records] = raf.length();
            byte[] header = readRecord(raf, offsets, 0);
            int compression = u16(header, 0);
            int textRecords = u16(header, 8);
            int encryption = u16(header, 12);
            if (encryption != 0) throw new Exception("Encrypted/DRM Kindle dictionaries cannot be imported");
            if (compression != 1 && compression != 2)
                throw new Exception("This Kindle dictionary compression is not supported");
            if (textRecords <= 0 || textRecords >= records) throw new Exception("Invalid Kindle text records");
            int codepage = header.length >= 32 ? (int) u32(header, 28) : 65001;
            if (codepage != 65001 && codepage != 0) throw new Exception("Only Unicode Kindle dictionaries are supported");
            int extraFlags = header.length >= 0xF4 ? u16(header, 0xF2) : 0;

            db = SQLiteDatabase.openOrCreateDatabase(output, null);
            db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY,headword_norm TEXT NOT NULL,headword TEXT NOT NULL,definition_html TEXT NOT NULL)");
            db.execSQL("CREATE INDEX idx_kindle_headword ON entries(headword_norm)");
            db.beginTransaction();
            int count = 0;
            StringBuilder pending = new StringBuilder(32768);
            for (int i = 1; i <= textRecords; i++) {
                byte[] record = readRecord(raf, offsets, i);
                record = trimTrailing(record, extraFlags);
                byte[] plain = compression == 2 ? palmDocDecompress(record) : record;
                pending.append(new String(plain, StandardCharsets.UTF_8));
                count += drainEntries(db, pending, false);
            }
            count += drainEntries(db, pending, true);
            db.setTransactionSuccessful();
            db.endTransaction();
            db.execSQL("ANALYZE");
            return count;
        } finally {
            if (db != null) {
                try { if (db.inTransaction()) db.endTransaction(); } catch (Exception ignored) {}
                try { db.close(); } catch (Exception ignored) {}
            }
            try { raf.close(); } catch (Exception ignored) {}
        }
    }

    private static int drainEntries(SQLiteDatabase db, StringBuilder pending, boolean finish) {
        int added = 0;
        while (true) {
            int first = indexOfIgnoreCase(pending, "<h4>", 0);
            if (first < 0) {
                if (finish) pending.setLength(0);
                else if (pending.length() > 8192) pending.delete(0, pending.length() - 8192);
                break;
            }
            if (first > 0) pending.delete(0, first);
            int next = indexOfIgnoreCase(pending, "<h4>", 4);
            if (next < 0) {
                if (finish && pending.length() > 8) {
                    if (insertSegment(db, pending.toString())) added++;
                    pending.setLength(0);
                }
                break;
            }
            String segment = pending.substring(0, next);
            if (insertSegment(db, segment)) added++;
            pending.delete(0, next);
        }
        return added;
    }

    private static boolean insertSegment(SQLiteDatabase db, String segment) {
        int start = indexOfIgnoreCase(segment, "<h4>", 0);
        int close = indexOfIgnoreCase(segment, "</h4>", Math.max(0, start + 4));
        if (start < 0 || close < 0) return false;
        String head = htmlToText(segment.substring(start + 4, close))
                .replace("⌂", "").replace("»", "").trim();
        if (head.isEmpty() || head.length() > 180) return false;
        String definition = segment.substring(close + 5).trim();
        if (definition.isEmpty()) return false;
        ContentValues cv = new ContentValues();
        cv.put("headword_norm", normalize(head));
        cv.put("headword", head);
        cv.put("definition_html", definition);
        return db.insert("entries", null, cv) >= 0;
    }

    private static byte[] readRecord(RandomAccessFile raf, long[] offsets, int index) throws Exception {
        long start = offsets[index], end = offsets[index + 1];
        long len = end - start;
        if (start < 0 || len < 0 || len > 16L * 1024L * 1024L) throw new Exception("Invalid Kindle record");
        byte[] out = new byte[(int) len];
        raf.seek(start); raf.readFully(out);
        return out;
    }

    private static byte[] trimTrailing(byte[] data, int flags) throws Exception {
        int end = data.length;
        int f = flags >> 1;
        while (f != 0) {
            if ((f & 1) != 0) {
                int size = backwardVwi(data, end);
                if (size <= 0 || size > end) throw new Exception("Invalid Kindle trailing record");
                end -= size;
            }
            f >>= 1;
        }
        if ((flags & 1) != 0 && end > 0) {
            int size = (data[end - 1] & 0x03) + 1;
            if (size <= end) end -= size;
        }
        return end == data.length ? data : Arrays.copyOf(data, Math.max(0, end));
    }

    private static int backwardVwi(byte[] data, int end) throws Exception {
        int value = 0, shift = 0;
        for (int p = end - 1, seen = 0; p >= 0 && seen < 5; p--, seen++) {
            int b = data[p] & 0xFF;
            value |= (b & 0x7F) << shift;
            shift += 7;
            if ((b & 0x80) != 0) return value;
        }
        throw new Exception("Invalid Kindle variable-width integer");
    }

    private static byte[] palmDocDecompress(byte[] data) throws Exception {
        ByteBuf out = new ByteBuf(Math.max(4096, data.length * 3));
        int i = 0;
        while (i < data.length) {
            int c = data[i++] & 0xFF;
            if (c == 0 || (c >= 9 && c <= 0x7F)) {
                out.write(c);
            } else if (c >= 1 && c <= 8) {
                int n = Math.min(c, data.length - i);
                out.write(data, i, n); i += n;
            } else if (c >= 0xC0) {
                out.write(0x20); out.write(c ^ 0x80);
            } else {
                if (i >= data.length) break;
                int c2 = data[i++] & 0xFF;
                int v = (c << 8) | c2;
                int distance = (v >> 3) & 0x7FF;
                int length = (v & 7) + 3;
                if (distance <= 0 || distance > out.size()) throw new Exception("Invalid PalmDOC back-reference");
                for (int j = 0; j < length; j++) out.copyBack(distance);
            }
        }
        return out.toByteArray();
    }

    private static final class ByteBuf {
        private byte[] data; private int size;
        ByteBuf(int capacity) { data = new byte[Math.max(32, capacity)]; }
        int size() { return size; }
        void write(int value) { ensure(1); data[size++] = (byte) value; }
        void write(byte[] src, int off, int len) { ensure(len); System.arraycopy(src, off, data, size, len); size += len; }
        void copyBack(int distance) { ensure(1); data[size] = data[size - distance]; size++; }
        byte[] toByteArray() { return java.util.Arrays.copyOf(data, size); }
        private void ensure(int extra) {
            int need = size + extra;
            if (need <= data.length) return;
            int cap = Math.max(need, data.length + Math.max(1024, data.length / 2));
            data = java.util.Arrays.copyOf(data, cap);
        }
    }

    private static int indexOfIgnoreCase(CharSequence value, String needle, int from) {
        int n = needle.length();
        for (int i = Math.max(0, from); i + n <= value.length(); i++) {
            boolean ok = true;
            for (int j = 0; j < n; j++) {
                if (Character.toLowerCase(value.charAt(i + j)) != Character.toLowerCase(needle.charAt(j))) {
                    ok = false; break;
                }
            }
            if (ok) return i;
        }
        return -1;
    }

    private static String htmlToText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            CharSequence cs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY)
                    : Html.fromHtml(raw);
            return cs.toString().replace('\u00A0', ' ').replaceAll("[ \\t]+", " ")
                    .replaceAll("\\n[ \\t]+", "\\n").replaceAll("\\n{3,}", "\\n\\n").trim();
        } catch (Exception ignored) {
            return raw.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static int u16(byte[] b, int p) {
        return ((b[p] & 0xFF) << 8) | (b[p + 1] & 0xFF);
    }

    private static long u32(byte[] b, int p) {
        return ((long) (b[p] & 0xFF) << 24) | ((long) (b[p + 1] & 0xFF) << 16) |
                ((long) (b[p + 2] & 0xFF) << 8) | (long) (b[p + 3] & 0xFF);
    }

    private static File dbFile(Context context) {
        File dir = new File(context.getFilesDir(), "dictionary");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, DB_NAME);
    }

    private static String displayName(Context context, Uri uri) {
        Cursor c = null;
        try {
            c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) {
                String name = c.getString(0);
                if (name != null && !name.trim().isEmpty()) return name.trim();
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) try { c.close(); } catch (Exception ignored) {}
        }
        return "Kindle dictionary.prc";
    }
}
