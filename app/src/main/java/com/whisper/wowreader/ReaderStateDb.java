package com.whisper.wowreader;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Scalable structured state for v63. SharedPreferences stays as a compatibility mirror for
 * small settings and the transition release; growing book/statistical data is indexed here.
 */
final class ReaderStateDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "wow_reader_state_v2.db";
    private static final int DB_VERSION = 1;
    private static final String META_LEGACY_MIGRATED = "legacy_migrated_v1";
    private static volatile ReaderStateDb INSTANCE;
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wow-reader-state-db");
        t.setDaemon(true);
        return t;
    });
    private static final Object MIGRATION_LOCK = new Object();

    private ReaderStateDb(Context context) { super(context.getApplicationContext(), DB_NAME, null, DB_VERSION); }

    static ReaderStateDb initialize(Context context, SharedPreferences prefs, File libraryDir) {
        ReaderStateDb db = get(context);
        if (!db.isLegacyMigrated()) db.migrateLegacyAsync(prefs, libraryDir);
        return db;
    }

    static ReaderStateDb get(Context context) {
        if (INSTANCE == null) {
            synchronized (ReaderStateDb.class) {
                if (INSTANCE == null) INSTANCE = new ReaderStateDb(context);
            }
        }
        return INSTANCE;
    }

    static ReaderStateDb peek() { return INSTANCE; }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
        try { db.enableWriteAheadLogging(); } catch (Exception ignored) {}
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS books (" +
                "file_name TEXT PRIMARY KEY, content_hash TEXT UNIQUE, file_path TEXT, format TEXT," +
                "file_size INTEGER NOT NULL DEFAULT 0, modified_at INTEGER NOT NULL DEFAULT 0," +
                "added_at INTEGER NOT NULL DEFAULT 0, last_opened_at INTEGER NOT NULL DEFAULT 0," +
                "progress INTEGER NOT NULL DEFAULT 0, finished_at INTEGER NOT NULL DEFAULT 0," +
                "epub_spine INTEGER NOT NULL DEFAULT 0, epub_offset INTEGER NOT NULL DEFAULT 0," +
                "pdf_page INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_hash ON books(content_hash)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_last_opened ON books(last_opened_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_finished ON books(finished_at DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS reading_day (day TEXT PRIMARY KEY, total_ms INTEGER NOT NULL DEFAULT 0, daily_note TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE IF NOT EXISTS book_day (day TEXT NOT NULL, file_name TEXT NOT NULL, duration_ms INTEGER NOT NULL DEFAULT 0, book_note TEXT NOT NULL DEFAULT '', PRIMARY KEY(day,file_name))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_book_day_file ON book_day(file_name)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_book_day_day ON book_day(day)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    boolean isLegacyMigrated() {
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery("SELECT v FROM meta WHERE k=?", new String[]{META_LEGACY_MIGRATED});
            return c.moveToFirst() && "1".equals(c.getString(0));
        } catch (Exception ignored) { return false; }
        finally { if (c != null) c.close(); }
    }

    private void migrateLegacyAsync(SharedPreferences prefs, File libraryDir) {
        WRITER.execute(() -> {
            synchronized (MIGRATION_LOCK) {
                if (isLegacyMigrated()) return;
                SQLiteDatabase db = getWritableDatabase();
                db.beginTransaction();
                try {
                    if (libraryDir != null) {
                        File[] files = libraryDir.listFiles();
                        if (files != null) for (File file : files) {
                            if (file == null || !file.isFile() || !isBook(file.getName())) continue;
                            insertOrUpdateBook(db, file, null, prefs);
                        }
                    }
                    migrateReadingStats(db, prefs);
                    ContentValues meta = new ContentValues();
                    meta.put("k", META_LEGACY_MIGRATED); meta.put("v", "1");
                    db.insertWithOnConflict("meta", null, meta, SQLiteDatabase.CONFLICT_REPLACE);
                    db.setTransactionSuccessful();
                } catch (Exception ignored) {
                    // Leave marker unset; next launch can retry without destroying legacy data.
                } finally { db.endTransaction(); }
                // Existing files are hashed lazily only when a same-size import needs identity checking.
                // This avoids reading gigabytes of a large library at startup.
            }
        });
    }

    private void migrateReadingStats(SQLiteDatabase db, SharedPreferences prefs) throws Exception {
        JSONObject days = object(prefs.getString("reading_stats_days_json", "{}"));
        JSONObject dayBooks = object(prefs.getString("reading_stats_day_books_json", "{}"));
        JSONObject dailyNotes = object(prefs.getString("reading_stats_day_notes_json", "{}"));
        JSONObject bookNotes = object(prefs.getString("reading_stats_book_day_notes_json", "{}"));
        Set<String> allDays = new HashSet<>();
        addKeys(allDays, days); addKeys(allDays, dayBooks); addKeys(allDays, dailyNotes); addKeys(allDays, bookNotes);
        for (String day : allDays) {
            ContentValues d = new ContentValues();
            d.put("day", day);
            d.put("total_ms", Math.max(0L, days.optLong(day, 0L)));
            d.put("daily_note", dailyNotes.optString(day, ""));
            db.insertWithOnConflict("reading_day", null, d, SQLiteDatabase.CONFLICT_REPLACE);
            JSONObject dbks = dayBooks.optJSONObject(day);
            JSONObject notes = bookNotes.optJSONObject(day);
            Set<String> names = new HashSet<>();
            addKeys(names, dbks); addKeys(names, notes);
            for (String fileName : names) {
                ContentValues b = new ContentValues();
                b.put("day", day); b.put("file_name", fileName);
                b.put("duration_ms", dbks == null ? 0L : Math.max(0L, dbks.optLong(fileName, 0L)));
                b.put("book_note", notes == null ? "" : notes.optString(fileName, ""));
                db.insertWithOnConflict("book_day", null, b, SQLiteDatabase.CONFLICT_REPLACE);
            }
        }
    }

    private static void addKeys(Set<String> out, JSONObject o) {
        if (o == null) return;
        Iterator<String> it = o.keys(); while (it.hasNext()) out.add(it.next());
    }

    File findExistingByHash(String hash, long incomingSize, File libraryDir, SharedPreferences prefs) {
        if (hash == null || hash.isEmpty()) return null;
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery("SELECT file_path,file_name FROM books WHERE content_hash=? LIMIT 1", new String[]{hash});
            if (c.moveToFirst()) {
                File f = new File(c.getString(0));
                if (f.isFile()) return f;
                if (libraryDir != null) {
                    f = new File(libraryDir, c.getString(1));
                    if (f.isFile()) return f;
                }
            }
        } catch (Exception ignored) {} finally { if (c != null) c.close(); }
        // Migration may still be hashing. Do an import-thread fallback scan once and cache it.
        if (libraryDir != null) {
            File[] files = libraryDir.listFiles();
            if (files != null) for (File f : files) {
                if (f == null || !f.isFile() || !isBook(f.getName())) continue;
                if (incomingSize >= 0L && f.length() != incomingSize) continue;
                try {
                    String existing = ensureHash(f, prefs);
                    if (hash.equals(existing)) return f;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    String ensureHash(File file, SharedPreferences prefs) throws Exception {
        if (file == null || !file.isFile()) return "";
        String sig = file.length() + ":" + file.lastModified();
        String key = "content_hash_" + file.getName();
        String sigKey = "content_hash_sig_" + file.getName();
        String cached = prefs == null ? "" : prefs.getString(key, "");
        String oldSig = prefs == null ? "" : prefs.getString(sigKey, "");
        if (cached != null && !cached.isEmpty() && sig.equals(oldSig)) {
            updateBookHash(file, cached);
            return cached;
        }
        String hash = FileIdentityUtil.sha256(file);
        if (prefs != null) prefs.edit().putString(key, hash).putString(sigKey, sig).apply();
        updateBookHash(file, hash);
        return hash;
    }

    void upsertBook(File file, String hash, SharedPreferences prefs) {
        if (file == null) return;
        WRITER.execute(() -> {
            SQLiteDatabase db = getWritableDatabase();
            insertOrUpdateBook(db, file, hash, prefs);
        });
    }

    private void insertOrUpdateBook(SQLiteDatabase db, File file, String hash, SharedPreferences prefs) {
        if (file == null || !file.isFile()) return;
        String name = file.getName();
        ContentValues v = new ContentValues();
        v.put("file_name", name); v.put("file_path", file.getAbsolutePath());
        v.put("format", name.toLowerCase(Locale.ROOT).endsWith(".pdf") ? "pdf" : "epub");
        v.put("file_size", file.length()); v.put("modified_at", file.lastModified());
        if (hash != null && !hash.isEmpty()) v.put("content_hash", hash);
        if (prefs != null) {
            v.put("added_at", prefs.getLong("added_at_" + name, 0L));
            v.put("last_opened_at", prefs.getLong("last_opened_" + name, 0L));
            v.put("progress", ReadingProgressStore.get(prefs, name));
            v.put("finished_at", ReadingProgressStore.finishedAt(prefs, name));
            v.put("epub_spine", prefs.getInt("epub_chapter_" + name, 0));
            v.put("epub_offset", prefs.getInt("epub_scroll_" + name, 0));
            v.put("pdf_page", prefs.getInt("pdf_page_" + name, 0));
        }
        v.put("updated_at", System.currentTimeMillis());
        db.insertWithOnConflict("books", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void updateBookHash(File file, String hash) {
        if (file == null || hash == null || hash.isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("content_hash", hash); v.put("file_path", file.getAbsolutePath());
        v.put("file_size", file.length()); v.put("modified_at", file.lastModified());
        try { getWritableDatabase().update("books", v, "file_name=?", new String[]{file.getName()}); }
        catch (Exception ignored) {}
    }

    void updateLastOpened(String fileName, long when) {
        if (fileName == null) return;
        WRITER.execute(() -> {
            ContentValues v = new ContentValues(); v.put("last_opened_at", when); v.put("updated_at", System.currentTimeMillis());
            getWritableDatabase().update("books", v, "file_name=?", new String[]{fileName});
        });
    }

    void updateProgress(String fileName, int percent, long finishedAt) {
        if (fileName == null) return;
        WRITER.execute(() -> {
            ContentValues v = new ContentValues(); v.put("progress", Math.max(0, Math.min(100, percent)));
            if (finishedAt > 0L) v.put("finished_at", finishedAt);
            v.put("updated_at", System.currentTimeMillis());
            getWritableDatabase().update("books", v, "file_name=?", new String[]{fileName});
        });
    }

    void updateEpubLocator(String fileName, int spine, int offset) {
        if (fileName == null) return;
        WRITER.execute(() -> {
            ContentValues v = new ContentValues(); v.put("epub_spine", Math.max(0, spine)); v.put("epub_offset", Math.max(0, Math.min(1000, offset)));
            v.put("updated_at", System.currentTimeMillis()); getWritableDatabase().update("books", v, "file_name=?", new String[]{fileName});
        });
    }

    void updatePdfPage(String fileName, int page) {
        if (fileName == null) return;
        WRITER.execute(() -> {
            ContentValues v = new ContentValues(); v.put("pdf_page", Math.max(0, page)); v.put("updated_at", System.currentTimeMillis());
            getWritableDatabase().update("books", v, "file_name=?", new String[]{fileName});
        });
    }

    void removeBook(String fileName) {
        if (fileName == null) return;
        WRITER.execute(() -> {
            SQLiteDatabase db = getWritableDatabase();
            db.delete("books", "file_name=?", new String[]{fileName});
            // Keep book_day history so Reading Calendar remains historical after a local book is removed.
        });
    }


    RangeSummary summarizeRange(long startMs, long endMs) {
        String start = dayKey(startMs), end = dayKey(endMs);
        long reading = scalarLong("SELECT COALESCE(SUM(total_ms),0) FROM reading_day WHERE day>=? AND day<=?", new String[]{start,end});
        int active = (int) scalarLong("SELECT COUNT(*) FROM reading_day WHERE day>=? AND day<=? AND total_ms>0", new String[]{start,end});
        int touched = (int) scalarLong("SELECT COUNT(DISTINCT file_name) FROM book_day WHERE day>=? AND day<=? AND duration_ms>0", new String[]{start,end});
        return new RangeSummary(reading, active, touched);
    }

    static final class RangeSummary {
        final long readingMs; final int activeDays; final int touchedBooks;
        RangeSummary(long readingMs, int activeDays, int touchedBooks) { this.readingMs=readingMs; this.activeDays=activeDays; this.touchedBooks=touchedBooks; }
    }

    List<FinishedRow> finishedBetween(long startMs, long endMs) {
        List<FinishedRow> out = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery("SELECT file_name,finished_at FROM books WHERE finished_at>=? AND finished_at<=? ORDER BY finished_at DESC",
                    new String[]{Long.toString(startMs), Long.toString(endMs)});
            while (c.moveToNext()) out.add(new FinishedRow(c.getString(0), c.getLong(1)));
        } catch (Exception ignored) {} finally { if (c != null) c.close(); }
        return out;
    }

    static final class FinishedRow {
        final String fileName; final long finishedAt;
        FinishedRow(String fileName, long finishedAt) { this.fileName = fileName; this.finishedAt = finishedAt; }
    }

    void recordReading(String day, String fileName, long durationMs) {
        if (day == null || durationMs <= 0L) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // API 23-safe upsert: SQLite bundled with Android 6 predates UPSERT ... DO UPDATE.
            db.execSQL("INSERT OR IGNORE INTO reading_day(day,total_ms,daily_note) VALUES(?,0,'')",
                    new Object[]{day});
            db.execSQL("UPDATE reading_day SET total_ms=total_ms+? WHERE day=?",
                    new Object[]{durationMs, day});
            if (fileName != null && !fileName.trim().isEmpty()) {
                db.execSQL("INSERT OR IGNORE INTO book_day(day,file_name,duration_ms,book_note) VALUES(?,?,0,'')",
                        new Object[]{day, fileName});
                db.execSQL("UPDATE book_day SET duration_ms=duration_ms+? WHERE day=? AND file_name=?",
                        new Object[]{durationMs, day, fileName});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    long dayTime(String day) { return scalarLong("SELECT total_ms FROM reading_day WHERE day=?", new String[]{day}); }
    long totalBookTime(String fileName) { return scalarLong("SELECT COALESCE(SUM(duration_ms),0) FROM book_day WHERE file_name=?", new String[]{fileName}); }
    long bookTimeForDay(String day, String fileName) { return scalarLong("SELECT duration_ms FROM book_day WHERE day=? AND file_name=?", new String[]{day,fileName}); }

    List<ReadingStatsStore.DayBook> booksForDay(String day) {
        List<ReadingStatsStore.DayBook> out = new ArrayList<>(); Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery("SELECT file_name,duration_ms FROM book_day WHERE day=? AND duration_ms>0 ORDER BY duration_ms DESC", new String[]{day});
            while (c.moveToNext()) out.add(new ReadingStatsStore.DayBook(c.getString(0), c.getLong(1)));
        } catch (Exception ignored) {} finally { if (c != null) c.close(); }
        return out;
    }

    long readingTimeForMonth(int year, int month) {
        String prefix = String.format(Locale.US, "%04d-%02d-%%", year, month);
        return scalarLong("SELECT COALESCE(SUM(total_ms),0) FROM reading_day WHERE day LIKE ?", new String[]{prefix});
    }
    int activeDaysForMonth(int year, int month) {
        String prefix = String.format(Locale.US, "%04d-%02d-%%", year, month);
        return (int) scalarLong("SELECT COUNT(*) FROM reading_day WHERE day LIKE ? AND total_ms>0", new String[]{prefix});
    }
    int uniqueBooksForMonth(int year, int month) {
        String prefix = String.format(Locale.US, "%04d-%02d-%%", year, month);
        return (int) scalarLong("SELECT COUNT(DISTINCT file_name) FROM book_day WHERE day LIKE ? AND duration_ms>0", new String[]{prefix});
    }

    String dailyNote(String day) { return scalarString("SELECT daily_note FROM reading_day WHERE day=?", new String[]{day}); }
    String bookDayNote(String day, String fileName) { return scalarString("SELECT book_note FROM book_day WHERE day=? AND file_name=?", new String[]{day,fileName}); }
    void setDailyNote(String day, String note) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("INSERT OR IGNORE INTO reading_day(day,total_ms,daily_note) VALUES(?,0,'')", new Object[]{day});
            db.execSQL("UPDATE reading_day SET daily_note=? WHERE day=?", new Object[]{note == null ? "" : note, day});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    void setBookDayNote(String day, String fileName, String note) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("INSERT OR IGNORE INTO book_day(day,file_name,duration_ms,book_note) VALUES(?,?,0,'')", new Object[]{day,fileName});
            db.execSQL("UPDATE book_day SET book_note=? WHERE day=? AND file_name=?", new Object[]{note == null ? "" : note, day, fileName});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    ReadingStatsStore.Snapshot snapshot(String bookName) {
        ReadingStatsStore.Snapshot s = new ReadingStatsStore.Snapshot();
        s.todayMs = dayTime(dayKey(System.currentTimeMillis()));
        s.totalMs = scalarLong("SELECT COALESCE(SUM(total_ms),0) FROM reading_day", null);
        s.bookMs = bookName == null ? 0L : totalBookTime(bookName);
        s.activeDays = (int) scalarLong("SELECT COUNT(*) FROM reading_day WHERE total_ms>0", null);
        List<String> days = positiveDays();
        s.currentStreak = calculateCurrentStreak(days);
        s.longestStreak = calculateLongestStreak(days);
        return s;
    }

    private List<String> positiveDays() {
        List<String> out = new ArrayList<>(); Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery("SELECT day FROM reading_day WHERE total_ms>0 ORDER BY day ASC", null);
            while (c.moveToNext()) out.add(c.getString(0));
        } catch (Exception ignored) {} finally { if (c != null) c.close(); }
        return out;
    }

    private static int calculateCurrentStreak(List<String> positive) {
        if (positive.isEmpty()) return 0;
        Set<String> set = new HashSet<>(positive); Calendar c = Calendar.getInstance();
        if (!set.contains(dayKey(c.getTimeInMillis()))) {
            c.add(Calendar.DAY_OF_YEAR, -1);
            if (!set.contains(dayKey(c.getTimeInMillis()))) return 0;
        }
        int n = 0; while (n < 36600 && set.contains(dayKey(c.getTimeInMillis()))) { n++; c.add(Calendar.DAY_OF_YEAR, -1); }
        return n;
    }
    private static int calculateLongestStreak(List<String> positive) {
        if (positive.isEmpty()) return 0;
        Collections.sort(positive); int best = 1, run = 1;
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US); fmt.setLenient(false);
            Date prev = fmt.parse(positive.get(0));
            for (int i=1;i<positive.size();i++) {
                Date cur = fmt.parse(positive.get(i));
                long delta = cur == null || prev == null ? Long.MAX_VALUE : (cur.getTime()-prev.getTime())/86400000L;
                run = delta == 1L ? run + 1 : 1; best = Math.max(best, run); prev = cur;
            }
        } catch (Exception ignored) {}
        return best;
    }

    void mergeSnapshot(File snapshot, SharedPreferences prefs) {
        if (snapshot == null || !snapshot.isFile()) return;
        SQLiteDatabase remote = null;
        SQLiteDatabase local = getWritableDatabase();
        local.beginTransaction();
        try {
            remote = SQLiteDatabase.openDatabase(snapshot.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            Cursor c = remote.rawQuery("SELECT file_name,content_hash,file_path,format,file_size,modified_at,added_at,last_opened_at,progress,finished_at,epub_spine,epub_offset,pdf_page,updated_at FROM books", null);
            while (c.moveToNext()) {
                String name = c.getString(0); long remoteUpdated = c.getLong(13);
                long localUpdated = 0L; Cursor lc = local.rawQuery("SELECT updated_at FROM books WHERE file_name=?", new String[]{name});
                try { if (lc.moveToFirst()) localUpdated = lc.getLong(0); } finally { lc.close(); }
                if (remoteUpdated < localUpdated) continue;
                ContentValues v = new ContentValues();
                v.put("file_name",name); if(!c.isNull(1))v.put("content_hash",c.getString(1));
                v.put("file_path",c.getString(2)); v.put("format",c.getString(3)); v.put("file_size",c.getLong(4)); v.put("modified_at",c.getLong(5));
                v.put("added_at",c.getLong(6)); v.put("last_opened_at",c.getLong(7)); v.put("progress",c.getInt(8)); v.put("finished_at",c.getLong(9));
                v.put("epub_spine",c.getInt(10)); v.put("epub_offset",c.getInt(11)); v.put("pdf_page",c.getInt(12)); v.put("updated_at",remoteUpdated);
                try { local.insertWithOnConflict("books",null,v,SQLiteDatabase.CONFLICT_REPLACE); } catch(Exception ignored) {}
                if (prefs != null) {
                    SharedPreferences.Editor e=prefs.edit().putInt("percent_"+name,c.getInt(8))
                            .putInt("epub_chapter_"+name,c.getInt(10)).putInt("epub_scroll_"+name,c.getInt(11)).putInt("pdf_page_"+name,c.getInt(12));
                    if(c.getLong(9)>0)e.putLong("finished_at_"+name,c.getLong(9));
                    if(c.getLong(7)>0)e.putLong("last_opened_"+name,c.getLong(7));
                    e.apply();
                }
            }
            c.close();
            c = remote.rawQuery("SELECT day,total_ms,daily_note FROM reading_day", null);
            while(c.moveToNext()){
                String day=c.getString(0); long ms=c.getLong(1); String note=c.getString(2);
                long existing=scalarLong("SELECT total_ms FROM reading_day WHERE day=?",new String[]{day});
                String existingNote=scalarString("SELECT daily_note FROM reading_day WHERE day=?",new String[]{day});
                ContentValues v=new ContentValues();v.put("day",day);v.put("total_ms",Math.max(existing,ms));v.put("daily_note",(note!=null&&!note.isEmpty())?note:existingNote);
                local.insertWithOnConflict("reading_day",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            }
            c.close();
            c = remote.rawQuery("SELECT day,file_name,duration_ms,book_note FROM book_day", null);
            while(c.moveToNext()){
                String day=c.getString(0),name=c.getString(1);long ms=c.getLong(2);String note=c.getString(3);
                long existing=scalarLong("SELECT duration_ms FROM book_day WHERE day=? AND file_name=?",new String[]{day,name});
                String existingNote=scalarString("SELECT book_note FROM book_day WHERE day=? AND file_name=?",new String[]{day,name});
                ContentValues v=new ContentValues();v.put("day",day);v.put("file_name",name);v.put("duration_ms",Math.max(existing,ms));v.put("book_note",(note!=null&&!note.isEmpty())?note:existingNote);
                local.insertWithOnConflict("book_day",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            }
            c.close();
            ContentValues meta = new ContentValues(); meta.put("k",META_LEGACY_MIGRATED); meta.put("v","1");
            local.insertWithOnConflict("meta",null,meta,SQLiteDatabase.CONFLICT_REPLACE);
            local.setTransactionSuccessful();
        } catch(Exception ignored) {
        } finally {
            local.endTransaction();
            if(remote!=null)remote.close();
        }
    }

    void checkpoint() {
        try { getWritableDatabase().rawQuery("PRAGMA wal_checkpoint(FULL)", null).close(); } catch (Exception ignored) {}
    }

    File databaseFile(Context context) { checkpoint(); return context.getDatabasePath(DB_NAME); }

    private long scalarLong(String sql, String[] args) {
        Cursor c = null; try { c = getReadableDatabase().rawQuery(sql, args); return c.moveToFirst() ? c.getLong(0) : 0L; }
        catch (Exception ignored) { return 0L; } finally { if (c != null) c.close(); }
    }
    private String scalarString(String sql, String[] args) {
        Cursor c = null; try { c = getReadableDatabase().rawQuery(sql, args); return c.moveToFirst() && c.getString(0) != null ? c.getString(0) : ""; }
        catch (Exception ignored) { return ""; } finally { if (c != null) c.close(); }
    }

    private static boolean isBook(String name) {
        String s = name == null ? "" : name.toLowerCase(Locale.ROOT); return s.endsWith(".epub") || s.endsWith(".pdf");
    }
    private static JSONObject object(String raw) { try { return new JSONObject(raw == null ? "{}" : raw); } catch (Exception ignored) { return new JSONObject(); } }
    private static String dayKey(long ms) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(ms)); }
}
