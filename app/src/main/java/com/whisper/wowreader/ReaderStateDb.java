package com.whisper.wowreader;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Scalable structured state for v63. SharedPreferences stays as a compatibility mirror for
 * small settings and the transition release; growing book/statistical data is indexed here.
 */
final class ReaderStateDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "wow_reader_state_v2.db";
    private static final int DB_VERSION = 6;
    private static final String META_LEGACY_MIGRATED = "legacy_migrated_v1";
    private static final String META_V2_MIGRATED = "library_metadata_migrated_v2";
    private static final String META_HOME_INDEXED = "home_indexed_v4";
    private static final String META_V72_RECONCILED = "library_reconciled_v72";
    private static volatile ReaderStateDb INSTANCE;
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wow-reader-state-db");
        t.setDaemon(true);
        return t;
    });
    private static final Object MIGRATION_LOCK = new Object();
    private static final Object SNAPSHOT_LOCK = new Object();
    /** The last initialization chain; restore callers wait for it before binding rows. */
    private volatile Future<?> initialization;
    private final Object initializationLock = new Object();

    // The platform's default corruption handler deletes files. Preserve recovery data instead.
    private static final DatabaseErrorHandler PRESERVE_DATABASE = ignored -> { };

    private ReaderStateDb(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION, PRESERVE_DATABASE);
    }

    static ReaderStateDb initialize(Context context, SharedPreferences prefs, File libraryDir) {
        ReaderStateDb db = get(context);
        // Do not share the legacy worker's long-running migration lock with UI callers.
        synchronized (db.initializationLock) {
            // Do not enqueue a second, independent migration/reconciliation chain on
            // every Activity recreation.  The final barrier is also what immediate
            // restore callers can await; the individual migrations remain retryable.
            if (db.initialization == null || db.initialization.isDone()) {
                if (!db.isLegacyMigrated()) db.migrateLegacyAsync(prefs, libraryDir);
                db.migrateV2Async(prefs);
                db.migrateHomeIndexAsync(prefs);
                db.initialization = WRITER.submit(() -> {
                    if (!db.isLegacyMigrated() || !db.isLibraryIndexReady())
                        throw new IllegalStateException("Reader database migration incomplete; local data is preserved");
                    if (!db.metaIs(META_V72_RECONCILED)) {
                        db.reconcileLocalLibrary(libraryDir, prefs);
                        db.putMeta(META_V72_RECONCILED, "1");
                    }
                });
            }
        }
        return db;
    }

    private void awaitInitialization() {
        Future<?> pending = initialization;
        if (pending == null || Thread.currentThread().getName().equals("wow-reader-state-db")) return;
        try { pending.get(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Reader database initialization interrupted", e); }
        catch (java.util.concurrent.ExecutionException e) { throw new IllegalStateException("Reader database initialization failed", e.getCause()); }
    }

    /** Wait for the initialization chain when a background restore/sync needs a ready index. */
    void awaitReady() { awaitInitialization(); }

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
                "title TEXT NOT NULL DEFAULT '', author TEXT NOT NULL DEFAULT '', annotation_count INTEGER NOT NULL DEFAULT 0," +
                "custom_cover_path TEXT NOT NULL DEFAULT '', cover_scope TEXT NOT NULL DEFAULT 'library_only', cover_updated_at INTEGER NOT NULL DEFAULT 0," +
                "remote_cover_file_id TEXT NOT NULL DEFAULT '', cover_sync_dirty INTEGER NOT NULL DEFAULT 0, cover_upload_session_url TEXT NOT NULL DEFAULT '', cover_upload_offset INTEGER NOT NULL DEFAULT 0," +
                "file_size INTEGER NOT NULL DEFAULT 0, modified_at INTEGER NOT NULL DEFAULT 0," +
                "added_at INTEGER NOT NULL DEFAULT 0, last_opened_at INTEGER NOT NULL DEFAULT 0," +
                "progress INTEGER NOT NULL DEFAULT 0, finished_at INTEGER NOT NULL DEFAULT 0," +
                "epub_spine INTEGER NOT NULL DEFAULT 0, epub_offset INTEGER NOT NULL DEFAULT 0," +
                "pdf_page INTEGER NOT NULL DEFAULT 0," +
                "remote_file_id TEXT NOT NULL DEFAULT '', remote_modified_time TEXT NOT NULL DEFAULT ''," +
                "sync_dirty INTEGER NOT NULL DEFAULT 1, upload_session_url TEXT NOT NULL DEFAULT ''," +
                "upload_offset INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_hash ON books(content_hash)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_last_opened ON books(last_opened_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_added ON books(added_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_title ON books(title COLLATE NOCASE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_author ON books(author COLLATE NOCASE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_progress ON books(progress)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_annotations ON books(annotation_count,last_opened_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_sync_dirty ON books(sync_dirty,updated_at)");
        db.execSQL("CREATE TABLE IF NOT EXISTS book_tombstones (content_hash TEXT PRIMARY KEY, file_name TEXT NOT NULL DEFAULT '', remote_file_id TEXT NOT NULL DEFAULT '', deleted_at INTEGER NOT NULL DEFAULT 0, sync_dirty INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tombstones_dirty ON book_tombstones(sync_dirty,deleted_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_finished ON books(finished_at DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS shelves (name TEXT PRIMARY KEY, updated_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS shelf_books (shelf_name TEXT NOT NULL, file_name TEXT NOT NULL, added_at INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(shelf_name,file_name))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_shelf_books_file ON shelf_books(file_name)");
        db.execSQL("CREATE TABLE IF NOT EXISTS reading_day (day TEXT PRIMARY KEY, total_ms INTEGER NOT NULL DEFAULT 0, daily_note TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE IF NOT EXISTS book_day (day TEXT NOT NULL, file_name TEXT NOT NULL, duration_ms INTEGER NOT NULL DEFAULT 0, book_note TEXT NOT NULL DEFAULT '', PRIMARY KEY(day,file_name))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_book_day_file ON book_day(file_name)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_book_day_day ON book_day(day)");
    }

    @Override public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // Repair only at open time: a table dropped after open must still surface as a real DB failure.
        String[] required = {"meta", "books", "book_tombstones", "shelves", "shelf_books", "reading_day", "book_day"};
        boolean missingBooks = !tableExists(db, "books");
        boolean missingStats = !tableExists(db, "reading_day") || !tableExists(db, "book_day");
        boolean missing = false;
        for (String table : required) missing |= !tableExists(db, table);
        if (!missing) return;
        db.beginTransaction();
        try {
            onCreate(db); // CREATE IF NOT EXISTS; never deletes or alters an existing table.
            if (missingBooks) {
                db.delete("meta", "k IN (?, ?, ?)", new String[]{META_LEGACY_MIGRATED, META_V2_MIGRATED, META_HOME_INDEXED});
            } else if (missingStats) {
                db.delete("meta", "k=?", new String[]{META_LEGACY_MIGRATED});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE books ADD COLUMN title TEXT NOT NULL DEFAULT ''"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE books ADD COLUMN author TEXT NOT NULL DEFAULT ''"); } catch (Exception ignored) {}
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_added ON books(added_at DESC)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_title ON books(title COLLATE NOCASE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_author ON books(author COLLATE NOCASE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_progress ON books(progress)");
            db.execSQL("CREATE TABLE IF NOT EXISTS shelves (name TEXT PRIMARY KEY, updated_at INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("CREATE TABLE IF NOT EXISTS shelf_books (shelf_name TEXT NOT NULL, file_name TEXT NOT NULL, added_at INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(shelf_name,file_name))");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_shelf_books_file ON shelf_books(file_name)");
        }
        if (oldVersion < 3) {
            try { db.execSQL("ALTER TABLE books ADD COLUMN remote_file_id TEXT NOT NULL DEFAULT ''"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE books ADD COLUMN remote_modified_time TEXT NOT NULL DEFAULT ''"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE books ADD COLUMN sync_dirty INTEGER NOT NULL DEFAULT 1"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE books ADD COLUMN upload_session_url TEXT NOT NULL DEFAULT ''"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE books ADD COLUMN upload_offset INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_sync_dirty ON books(sync_dirty,updated_at)");
            db.execSQL("CREATE TABLE IF NOT EXISTS book_tombstones (content_hash TEXT PRIMARY KEY, file_name TEXT NOT NULL DEFAULT '', remote_file_id TEXT NOT NULL DEFAULT '', deleted_at INTEGER NOT NULL DEFAULT 0, sync_dirty INTEGER NOT NULL DEFAULT 1)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_tombstones_dirty ON book_tombstones(sync_dirty,deleted_at)");
        }
        if (oldVersion < 4) {
            try { db.execSQL("ALTER TABLE books ADD COLUMN annotation_count INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_books_annotations ON books(annotation_count,last_opened_at DESC)");
        }
        if (oldVersion < 5) {
            try { db.execSQL("ALTER TABLE books ADD COLUMN custom_cover_path TEXT NOT NULL DEFAULT ''"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE books ADD COLUMN cover_scope TEXT NOT NULL DEFAULT 'library_only'"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE books ADD COLUMN cover_updated_at INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE books ADD COLUMN remote_cover_file_id TEXT NOT NULL DEFAULT ''"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE books ADD COLUMN cover_sync_dirty INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE books ADD COLUMN cover_upload_session_url TEXT NOT NULL DEFAULT ''"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE books ADD COLUMN cover_upload_offset INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) {}
        }
        if (oldVersion < 6) {
            db.execSQL("UPDATE books SET upload_session_url='', upload_offset=0 WHERE sync_dirty=1");
            db.execSQL("UPDATE books SET cover_upload_session_url='', cover_upload_offset=0 WHERE cover_sync_dirty=1");
        }
    }

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
                            // A repair retry must not replace a surviving book's newer DB progress.
                            try (Cursor existing = db.rawQuery("SELECT 1 FROM books WHERE file_name=?", new String[]{file.getName()})) {
                                if (existing.moveToFirst()) continue;
                            }
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
            d.put("total_ms", Math.max(dayTime(day), Math.max(0L, days.optLong(day, 0L))));
            String existingDayNote = dailyNote(day);
            d.put("daily_note", existingDayNote.isEmpty() ? dailyNotes.optString(day, "") : existingDayNote);
            db.insertWithOnConflict("reading_day", null, d, SQLiteDatabase.CONFLICT_REPLACE);
            JSONObject dbks = dayBooks.optJSONObject(day);
            JSONObject notes = bookNotes.optJSONObject(day);
            Set<String> names = new HashSet<>();
            addKeys(names, dbks); addKeys(names, notes);
            for (String fileName : names) {
                ContentValues b = new ContentValues();
                b.put("day", day); b.put("file_name", fileName);
                b.put("duration_ms", Math.max(bookTimeForDay(day, fileName), dbks == null ? 0L : Math.max(0L, dbks.optLong(fileName, 0L))));
                String existingBookNote = bookDayNote(day, fileName);
                b.put("book_note", existingBookNote.isEmpty() ? (notes == null ? "" : notes.optString(fileName, "")) : existingBookNote);
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
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery("SELECT content_hash,file_size,modified_at FROM books WHERE file_name=? LIMIT 1", new String[]{file.getName()});
            if (c.moveToFirst()) {
                String cached = c.getString(0);
                if (cached != null && !cached.isEmpty() && c.getLong(1) == file.length() && c.getLong(2) == file.lastModified()) return cached;
            }
        } catch (Exception ignored) {} finally { if (c != null) c.close(); }
        String sig = file.length() + ":" + file.lastModified();
        String legacy = prefs == null ? "" : prefs.getString("content_hash_" + file.getName(), "");
        String legacySig = prefs == null ? "" : prefs.getString("content_hash_sig_" + file.getName(), "");
        if (legacy != null && !legacy.isEmpty() && sig.equals(legacySig)) {
            updateBookHash(file, legacy); return legacy;
        }
        String hash = FileIdentityUtil.sha256(file);
        if (!isLibraryIndexReady() && prefs != null)
            prefs.edit().putString("content_hash_" + file.getName(), hash).putString("content_hash_sig_" + file.getName(), sig).apply();
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
        String existingTitle = scalarString(db, "SELECT title FROM books WHERE file_name=?", new String[]{name});
        String existingAuthor = scalarString(db, "SELECT author FROM books WHERE file_name=?", new String[]{name});
        ContentValues v = new ContentValues();
        v.put("file_name", name); v.put("file_path", file.getAbsolutePath());
        v.put("format", name.toLowerCase(Locale.ROOT).endsWith(".pdf") ? "pdf" : "epub");
        v.put("file_size", file.length()); v.put("modified_at", file.lastModified());
        if (hash != null && !hash.isEmpty()) v.put("content_hash", hash);
        if (prefs != null) {
            String fallbackTitle = stripExtension(name);
            String title = prefs.getString("library_title_" + name, fallbackTitle);
            String author = prefs.getString("library_author_" + name, "");
            if (!prefs.getBoolean("library_metadata_custom_" + name, false) && !meaningfulDisplayTitle(title) && establishedStoredTitle(existingTitle)) title = existingTitle;
            if (author.trim().isEmpty() && !existingAuthor.trim().isEmpty()) author = existingAuthor;
            v.put("title", title == null || title.trim().isEmpty() ? fallbackTitle : title.trim());
            v.put("author", author == null ? "" : author.trim());
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

    boolean isLibraryIndexReady() {
        if (!isLegacyMigrated()) return false;
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery("SELECT v FROM meta WHERE k=?", new String[]{META_V2_MIGRATED});
            return c.moveToFirst() && "1".equals(c.getString(0));
        } catch (Exception ignored) { return false; }
        finally { if (c != null) c.close(); }
    }

    private void migrateV2Async(SharedPreferences prefs) {
        if (prefs == null || isLibraryIndexReady()) return;
        WRITER.execute(() -> {
            if (!isLegacyMigrated() || isLibraryIndexReady()) return;
            SQLiteDatabase sql = getWritableDatabase();
            sql.beginTransaction();
            Cursor c = null;
            try {
                c = sql.rawQuery("SELECT file_name FROM books", null);
                while (c.moveToNext()) {
                    String name = c.getString(0);
                    String fallback = stripExtension(name);
                    String title = prefs.getString("library_title_" + name, fallback);
                    String author = prefs.getString("library_author_" + name, "");
                    ContentValues v = new ContentValues();
                    v.put("title", title == null || title.trim().isEmpty() ? fallback : title.trim());
                    v.put("author", author == null ? "" : author.trim());
                    sql.update("books", v, "file_name=?", new String[]{name});
                }
                if (c != null) { c.close(); c = null; }
                JSONObject shelves = object(prefs.getString("library_shelves_json", "{}"));
                Iterator<String> keys = shelves.keys();
                while (keys.hasNext()) {
                    String shelf = keys.next();
                    ContentValues sv = new ContentValues(); sv.put("name", shelf); sv.put("updated_at", System.currentTimeMillis());
                    sql.insertWithOnConflict("shelves", null, sv, SQLiteDatabase.CONFLICT_IGNORE);
                    org.json.JSONArray books = shelves.optJSONArray(shelf);
                    if (books == null) continue;
                    for (int i = 0; i < books.length(); i++) {
                        String fileName = books.optString(i, ""); if (fileName.isEmpty()) continue;
                        ContentValues bv = new ContentValues(); bv.put("shelf_name", shelf); bv.put("file_name", fileName); bv.put("added_at", 0L);
                        sql.insertWithOnConflict("shelf_books", null, bv, SQLiteDatabase.CONFLICT_IGNORE);
                    }
                }
                ContentValues marker = new ContentValues(); marker.put("k", META_V2_MIGRATED); marker.put("v", "1");
                sql.insertWithOnConflict("meta", null, marker, SQLiteDatabase.CONFLICT_REPLACE);
                sql.setTransactionSuccessful();
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.close();
                sql.endTransaction();
            }
        });
    }

    private void migrateHomeIndexAsync(SharedPreferences prefs) {
        if (prefs == null) return;
        WRITER.execute(() -> {
            Cursor marker=null;
            try { marker=getReadableDatabase().rawQuery("SELECT v FROM meta WHERE k=?",new String[]{META_HOME_INDEXED}); if(marker.moveToFirst()&&"1".equals(marker.getString(0)))return; }
            catch(Exception ignored) {} finally { if(marker!=null)marker.close(); }
            if(!isLibraryIndexReady())return;
            SQLiteDatabase sql=getWritableDatabase(); sql.beginTransaction(); Cursor c=null;
            try {
                c=sql.rawQuery("SELECT file_name FROM books",null);
                while(c.moveToNext()) {
                    String name=c.getString(0); ContentValues v=new ContentValues();
                    v.put("annotation_count",ReaderAnnotationStore.count(prefs,name));
                    sql.update("books",v,"file_name=?",new String[]{name});
                }
                ContentValues m=new ContentValues();m.put("k",META_HOME_INDEXED);m.put("v","1");
                sql.insertWithOnConflict("meta",null,m,SQLiteDatabase.CONFLICT_REPLACE);sql.setTransactionSuccessful();
            } catch(Exception ignored) {} finally { if(c!=null)c.close();sql.endTransaction(); }
        });
    }

    static final class LibraryBookRow {
        final String fileName, filePath, title, author;
        final int progress;
        final long addedAt, lastOpenedAt;
        LibraryBookRow(Cursor c) {
            fileName = c.getString(0); filePath = c.getString(1); title = c.getString(2); author = c.getString(3);
            progress = c.getInt(4); addedAt = c.getLong(5); lastOpenedAt = c.getLong(6);
        }
        File asFile() { return new File(filePath == null ? "" : filePath); }
    }

    int countLibraryBooks(LibraryQuerySpec spec) {
        if (spec == null) spec = new LibraryQuerySpec("", "", "all", "", "added");
        return (int)Math.min(Integer.MAX_VALUE, scalarLong("SELECT COUNT(*) FROM books WHERE file_path IS NOT NULL AND TRIM(file_path)<>'' AND " + spec.whereSql(), spec.args()));
    }

    List<LibraryBookRow> pageLibraryBooks(LibraryQuerySpec spec, int offset, int limit) {
        if (spec == null) spec = new LibraryQuerySpec("", "", "all", "", "added");
        int o = Math.max(0, offset), n = Math.max(1, Math.min(250, limit));
        List<LibraryBookRow> out = new ArrayList<>(); Cursor c = null;
        try {
            String sql = "SELECT file_name,file_path,title,author,progress,added_at,last_opened_at FROM books WHERE file_path IS NOT NULL AND TRIM(file_path)<>'' AND " +
                    spec.whereSql() + " ORDER BY " + spec.orderSql() + " LIMIT " + n + " OFFSET " + o;
            c = getReadableDatabase().rawQuery(sql, spec.args());
            while (c.moveToNext()) out.add(new LibraryBookRow(c));
        } catch (Exception ignored) {} finally { if (c != null) c.close(); }
        return out;
    }

    String indexedTitle(String fileName, String fallback) {
        String v = scalarString("SELECT title FROM books WHERE file_name=?", new String[]{fileName});
        return v.isEmpty() ? fallback : v;
    }
    String indexedAuthor(String fileName) { return scalarString("SELECT author FROM books WHERE file_name=?", new String[]{fileName}); }

    /** Canonical UI identity. Hashes and numeric import placeholders are never user-facing titles. */
    static String displayTitle(SharedPreferences prefs, String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return "Book";
        String fallback = stripExtension(fileName);
        String pref = prefs == null ? "" : prefs.getString("library_title_" + fileName, "");
        boolean custom = prefs != null && prefs.getBoolean("library_metadata_custom_" + fileName, false);
        if (custom && !pref.trim().isEmpty()) return pref.trim();
        ReaderStateDb db = peek();
        String indexed = db == null ? "" : db.indexedTitle(fileName, "");
        if (establishedStoredTitle(indexed)) return indexed.trim();
        if (meaningfulDisplayTitle(pref)) return pref.trim();
        return meaningfulDisplayTitle(fallback) ? fallback : "Untitled book";
    }

    static String displayAuthor(SharedPreferences prefs, String fileName) {
        if (fileName == null) return "";
        ReaderStateDb db = peek();
        String value = db == null ? "" : db.indexedAuthor(fileName);
        if (value == null || value.trim().isEmpty()) value = prefs == null ? "" : prefs.getString("library_author_" + fileName, "");
        return value == null ? "" : value.trim();
    }

    static boolean meaningfulDisplayTitle(String value) {
        if (value == null) return false;
        String s = value.trim();
        if (s.isEmpty() || s.matches("[0-9]+")) return false;
        return !s.matches("(?i)[0-9a-f]{32}|[0-9a-f]{40}|[0-9a-f]{64}");
    }

    static final class PendingSyncRow {
        final String fileName, filePath, format, hash, remoteId, sessionUrl;
        final long offset;
        PendingSyncRow(Cursor c) {
            fileName=c.getString(0); filePath=c.getString(1); format=c.getString(2); hash=c.getString(3);
            remoteId=c.getString(4); sessionUrl=c.getString(5); offset=c.getLong(6);
        }
        File file() { return new File(filePath); }
    }
    static final class TombstoneRow {
        final String hash, fileName, remoteId;
        TombstoneRow(Cursor c) { hash=c.getString(0); fileName=c.getString(1); remoteId=c.getString(2); }
    }

    List<PendingSyncRow> pendingBookUploads(int limit) {
        int n=Math.max(1,Math.min(100,limit)); List<PendingSyncRow> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT file_name,file_path,format,content_hash,remote_file_id,upload_session_url,upload_offset FROM books WHERE file_path IS NOT NULL AND TRIM(file_path)<>'' AND sync_dirty=1 ORDER BY updated_at ASC LIMIT "+n,null)) {
            while(c.moveToNext()) out.add(new PendingSyncRow(c));
        }
        return out;
    }
    int pendingBookUploadCount() { return syncCount("SELECT COUNT(*) FROM books WHERE file_path IS NOT NULL AND TRIM(file_path)<>'' AND sync_dirty=1"); }
    List<TombstoneRow> pendingTombstones(int limit) {
        int n=Math.max(1,Math.min(100,limit)); List<TombstoneRow> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT content_hash,file_name,remote_file_id FROM book_tombstones WHERE sync_dirty=1 ORDER BY deleted_at ASC LIMIT "+n,null)) {
            while(c.moveToNext())out.add(new TombstoneRow(c));
        }
        return out;
    }
    int pendingTombstoneCount() { return syncCount("SELECT COUNT(*) FROM book_tombstones WHERE sync_dirty=1"); }
    private int syncCount(String query) {
        try(Cursor c=getReadableDatabase().rawQuery(query,null)) {
            if(!c.moveToFirst())throw new IllegalStateException("Sync queue count unavailable");
            return (int)Math.min(Integer.MAX_VALUE,c.getLong(0));
        }
    }
    void saveUploadCheckpoint(String fileName,String session,long offset) {
        ContentValues v=new ContentValues(); v.put("upload_session_url",session==null?"":session); v.put("upload_offset",Math.max(0L,offset));
        getWritableDatabase().update("books",v,"file_name=?",new String[]{fileName});
    }
    void markBookSynced(String fileName,String remoteId,String modifiedTime) {
        ContentValues v=new ContentValues(); v.put("remote_file_id",remoteId==null?"":remoteId); v.put("remote_modified_time",modifiedTime==null?"":modifiedTime);
        v.put("sync_dirty",0); v.put("upload_session_url",""); v.put("upload_offset",0L);
        getWritableDatabase().update("books",v,"file_name=?",new String[]{fileName});
    }
    void markTombstoneSynced(String hash) {
        if(hash==null||hash.isEmpty())return;
        ContentValues v=new ContentValues();v.put("sync_dirty",0);
        getWritableDatabase().update("book_tombstones",v,"content_hash=?",new String[]{hash});
    }

    boolean hasBookTombstone(String hash) {
        // Do not use scalarLong: a database error must stop sync, not grant upload permission.
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM book_tombstones WHERE content_hash=? LIMIT 1",new String[]{hash})) {
            return c.moveToFirst();
        }
    }

    void rememberRemoteDeletion(String hash) {
        DriveDeletionGuard.markerName(hash);
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();
        try {
            ContentValues t=new ContentValues();t.put("content_hash",hash);t.put("deleted_at",System.currentTimeMillis());t.put("sync_dirty",0);
            // Never acknowledge an unsent local deletion merely because a marker was observed.
            sql.insertWithOnConflict("book_tombstones",null,t,SQLiteDatabase.CONFLICT_IGNORE);
            // CONFLICT_IGNORE returns -1 both for an existing row and some insertion failures.
            if(!hasBookTombstone(hash))throw new IllegalStateException("Unable to retain cloud deletion");
            ContentValues v=new ContentValues();v.put("sync_dirty",0);v.put("upload_session_url","");v.put("upload_offset",0L);
            v.put("cover_sync_dirty",0);v.put("cover_upload_session_url","");v.put("cover_upload_offset",0L);
            v.put("remote_file_id","");v.put("remote_cover_file_id","");
            sql.update("books",v,"content_hash=?",new String[]{hash});
            // Preserve this device's books, progress and history; only cloud uploads are suppressed.
            sql.setTransactionSuccessful();
        } finally {sql.endTransaction();}
    }

    List<LibraryBookRow> recentBooks(int limit, boolean readingOnly) {
        int n=Math.max(1,Math.min(20,limit));List<LibraryBookRow> out=new ArrayList<>();Cursor c=null;
        try {String where="file_path IS NOT NULL AND TRIM(file_path)<>'' AND "+(readingOnly?"progress>0 AND progress<100":"1=1");c=getReadableDatabase().rawQuery(
                "SELECT file_name,file_path,title,author,progress,added_at,last_opened_at FROM books WHERE "+where+" ORDER BY last_opened_at DESC,added_at DESC LIMIT "+n,null);
            while(c.moveToNext())out.add(new LibraryBookRow(c));}catch(Exception ignored){}finally{if(c!=null)c.close();}return out;
    }
    /** Returns one bounded home/recent page; eligibility is filtered before paging in SQLite. */
    List<LibraryBookRow> recentBooksPage(int limit, int offset, boolean readingOnly) {
        int n=Math.max(1,Math.min(20,limit));int o=Math.max(0,offset);List<LibraryBookRow> out=new ArrayList<>();Cursor c=null;
        try {String where="file_path IS NOT NULL AND TRIM(file_path)<>'' AND "+(readingOnly?"progress>0 AND progress<100":"progress>=0 AND progress<100");c=getReadableDatabase().rawQuery(
                "SELECT file_name,file_path,title,author,progress,added_at,last_opened_at FROM books WHERE "+where+" ORDER BY last_opened_at DESC,added_at DESC LIMIT "+n+" OFFSET "+o,null);
            while(c.moveToNext())out.add(new LibraryBookRow(c));}catch(Exception ignored){}finally{if(c!=null)c.close();}return out;
    }
    int totalAnnotationCount(){return (int)Math.min(Integer.MAX_VALUE,scalarLong("SELECT COALESCE(SUM(annotation_count),0) FROM books WHERE file_path IS NOT NULL AND TRIM(file_path)<>''",null));}
    void updateAnnotationCount(String fileName,int count){if(fileName==null)return;ContentValues v=new ContentValues();v.put("annotation_count",Math.max(0,count));getWritableDatabase().update("books",v,"file_name=?",new String[]{fileName});}
    List<LibraryBookRow> annotatedBooks(int limit){int n=Math.max(1,Math.min(500,limit));List<LibraryBookRow> out=new ArrayList<>();Cursor c=null;try{c=getReadableDatabase().rawQuery(
            "SELECT file_name,file_path,title,author,progress,added_at,last_opened_at FROM books WHERE file_path IS NOT NULL AND TRIM(file_path)<>'' AND annotation_count>0 ORDER BY last_opened_at DESC,updated_at DESC LIMIT "+n,null);while(c.moveToNext())out.add(new LibraryBookRow(c));}catch(Exception ignored){}finally{if(c!=null)c.close();}return out;}
    static final class AuthorCount{final String author;final int count;AuthorCount(String a,int c){author=a;count=c;}}
    List<AuthorCount> authors(int limit){int n=Math.max(1,Math.min(1000,limit));List<AuthorCount> out=new ArrayList<>();Cursor c=null;try{c=getReadableDatabase().rawQuery(
            "SELECT author,COUNT(*) FROM books WHERE file_path IS NOT NULL AND TRIM(file_path)<>'' AND author<>'' GROUP BY author ORDER BY author COLLATE NOCASE ASC LIMIT "+n,null);while(c.moveToNext())out.add(new AuthorCount(c.getString(0),c.getInt(1)));}catch(Exception ignored){}finally{if(c!=null)c.close();}return out;}
    int totalAuthorCount(){return (int)Math.min(Integer.MAX_VALUE,scalarLong("SELECT COUNT(DISTINCT author) FROM books WHERE file_path IS NOT NULL AND TRIM(file_path)<>'' AND author<>''",null));}
    int totalBookCount(){return (int)Math.min(Integer.MAX_VALUE,scalarLong("SELECT COUNT(*) FROM books WHERE file_path IS NOT NULL AND TRIM(file_path)<>''",null));}

    List<String> shelfNames(){List<String> out=new ArrayList<>();Cursor c=null;try{c=getReadableDatabase().rawQuery("SELECT name FROM shelves ORDER BY name COLLATE NOCASE ASC",null);while(c.moveToNext())out.add(c.getString(0));}catch(Exception ignored){}finally{if(c!=null)c.close();}return out;}
    boolean createShelfRow(String name){if(name==null||name.trim().isEmpty())return false;ContentValues v=new ContentValues();v.put("name",name.trim());v.put("updated_at",System.currentTimeMillis());return getWritableDatabase().insertWithOnConflict("shelves",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1;}
    boolean renameShelfRow(String oldName,String newName){if(oldName==null||newName==null||newName.trim().isEmpty())return false;SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try{ContentValues nv=new ContentValues();nv.put("name",newName.trim());nv.put("updated_at",System.currentTimeMillis());if(sql.insertWithOnConflict("shelves",null,nv,SQLiteDatabase.CONFLICT_IGNORE)==-1)return false;sql.execSQL("INSERT OR IGNORE INTO shelf_books(shelf_name,file_name,added_at) SELECT ?,file_name,added_at FROM shelf_books WHERE shelf_name=?",new Object[]{newName.trim(),oldName});sql.delete("shelf_books","shelf_name=?",new String[]{oldName});sql.delete("shelves","name=?",new String[]{oldName});sql.setTransactionSuccessful();return true;}catch(Exception e){return false;}finally{sql.endTransaction();}}
    boolean deleteShelfRow(String name){if(name==null)return false;SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try{sql.delete("shelf_books","shelf_name=?",new String[]{name});int n=sql.delete("shelves","name=?",new String[]{name});sql.setTransactionSuccessful();return n>0;}finally{sql.endTransaction();}}
    boolean shelfContains(String shelf,String fileName){return shelf!=null&&fileName!=null&&scalarLong("SELECT COUNT(*) FROM shelf_books sb JOIN books b ON b.file_name=sb.file_name AND b.file_path IS NOT NULL AND TRIM(b.file_path)<>'' WHERE sb.shelf_name=? AND sb.file_name=?",new String[]{shelf,fileName})>0;}
    int shelfBookCount(String shelf){return shelf==null?0:(int)Math.min(Integer.MAX_VALUE,scalarLong("SELECT COUNT(*) FROM shelf_books sb JOIN books b ON b.file_name=sb.file_name AND b.file_path IS NOT NULL AND TRIM(b.file_path)<>'' WHERE sb.shelf_name=?",new String[]{shelf}));}
    void setShelfMembershipRow(String shelf,String fileName,boolean included){if(shelf==null||fileName==null)return;createShelfRow(shelf);SQLiteDatabase sql=getWritableDatabase();if(included){ContentValues v=new ContentValues();v.put("shelf_name",shelf);v.put("file_name",fileName);v.put("added_at",System.currentTimeMillis());sql.insertWithOnConflict("shelf_books",null,v,SQLiteDatabase.CONFLICT_IGNORE);}else sql.delete("shelf_books","shelf_name=? AND file_name=?",new String[]{shelf,fileName});}
    void removeBookFromShelves(String fileName){if(fileName!=null)getWritableDatabase().delete("shelf_books","file_name=?",new String[]{fileName});}

    String contentHash(String fileName){return scalarString("SELECT content_hash FROM books WHERE file_name=?",new String[]{fileName});}
    String fileNameForHash(String hash){return hash==null?"":scalarString("SELECT file_name FROM books WHERE content_hash=? LIMIT 1",new String[]{hash});}
    void recordRestoredBook(File file, String hash, String remoteId, SharedPreferences prefs) {
        if (file == null || !file.isFile() || hash == null || hash.isEmpty()) return;
        // A fresh install can call restore immediately after initialize().  Do not
        // bind against a partially migrated index; this is a bounded wait for the
        // already queued chain, not a new migration or a retry loop.
        awaitInitialization();
        SQLiteDatabase sql = getWritableDatabase();
        SharedPreferences.Editor aliases = null;
        sql.beginTransaction();
        try {
            // Keep the deletion guard inside the same write transaction as the bind. A
            // pre-transaction check can race a remote-deletion import and resurrect a book.
            if (hasBookTombstone(hash)) return;
            String existing = fileNameForHash(hash), actual = file.getName();
            if (existing.isEmpty()) {
                // Never replace a different indexed identity just because names happen to match.
                String other = contentHash(actual);
                if (!other.isEmpty() && !hash.equals(other)) throw new IllegalStateException("Conflicting restored book identity");
                insertOrUpdateBook(sql, file, hash, prefs);
                existing = actual;
            }
            if (!existing.equals(actual)) {
                if (scalarLong("SELECT COUNT(*) FROM books WHERE file_name=?", new String[]{actual}) != 0)
                    throw new IllegalStateException("Conflicting restored book name; original data is preserved");
                ContentValues name = new ContentValues(); name.put("file_name", actual);
                if (sql.update("books", name, "file_name=?", new String[]{existing}) != 1)
                    throw new IllegalStateException("Restored book identity is missing");
                sql.execSQL("INSERT OR IGNORE INTO shelf_books(shelf_name,file_name,added_at) SELECT shelf_name,?,added_at FROM shelf_books WHERE file_name=?", new Object[]{actual, existing});
                sql.delete("shelf_books", "file_name=?", new String[]{existing});
                Map<String, Long> targetTimes = new HashMap<>();
                Map<String, String> targetNotes = new HashMap<>();
                try (Cursor target = sql.rawQuery("SELECT day,duration_ms,book_note FROM book_day WHERE file_name=?", new String[]{actual})) {
                    while (target.moveToNext()) {
                        targetTimes.put(target.getString(0), target.getLong(1));
                        targetNotes.put(target.getString(0), target.getString(2) == null ? "" : target.getString(2));
                    }
                }
                try (Cursor history = sql.rawQuery("SELECT day,duration_ms,book_note FROM book_day WHERE file_name=?", new String[]{existing})) {
                    while (history.moveToNext()) {
                        String day = history.getString(0), currentNote = targetNotes.get(day);
                        if (currentNote == null) currentNote = "";
                        ContentValues row = new ContentValues(); row.put("day", day); row.put("file_name", actual);
                        Long targetTime = targetTimes.get(day);
                        row.put("duration_ms", Math.max(targetTime == null ? 0L : targetTime, history.getLong(1)));
                        row.put("book_note", currentNote.isEmpty() ? history.getString(2) : currentNote);
                        mergeRow(sql, "book_day", row);
                    }
                }
                sql.delete("book_day", "file_name=?", new String[]{existing});
                aliases = stagedPrefAliases(prefs, existing, actual);
                existing = actual;
            }
            ContentValues v = new ContentValues();
            v.put("file_path", file.getCanonicalPath()); v.put("file_size", file.length()); v.put("modified_at", file.lastModified());
            v.put("format", actual.toLowerCase(Locale.ROOT).endsWith(".pdf") ? "pdf" : "epub");
            String knownRemote = scalarString("SELECT remote_file_id FROM books WHERE file_name=?", new String[]{existing});
            v.put("remote_file_id", DriveRestorePlanner.chooseRemoteId(knownRemote, remoteId));
            v.put("sync_dirty", 0); v.put("upload_session_url", ""); v.put("upload_offset", 0L);
            if (sql.update("books", v, "file_name=?", new String[]{existing}) != 1)
                throw new IllegalStateException("Restored book row missing");
            sql.setTransactionSuccessful();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to bind restored book (" + bindingFailureDiagnostic(e) + ")", e);
        } finally { sql.endTransaction(); }
        if (aliases != null) aliases.apply();
    }

    /** Keep useful SQLite failure classes while excluding paths, IDs, and provider tokens. */
    static String bindingFailureDiagnostic(Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.startsWith("conflicting restored book")) return "book identity conflict; original data preserved";
                if (lower.contains("locked") || lower.contains("busy")) return "database busy";
                if (lower.contains("constraint") || lower.contains("unique")) return "database constraint";
                if (lower.contains("no such table") || lower.contains("no such column")) return "database schema";
            }
            cause = cause.getCause();
        }
        return error == null ? "unknown" : error.getClass().getSimpleName();
    }

    private static SharedPreferences.Editor stagedPrefAliases(SharedPreferences prefs, String oldName, String newName) throws Exception {
        if (prefs == null || oldName.equals(newName)) return null;
        SharedPreferences.Editor edit = prefs.edit();
        java.util.Map<String, ?> values = prefs.getAll();
        String[] prefixes = {"percent_", "finished_at_", "epub_chapter_", "epub_scroll_", "pdf_page_", "library_title_", "library_author_",
                "library_metadata_custom_", "library_owned_", "added_at_", "last_opened_", "content_hash_", "content_hash_sig_", "book_notes_"};
        for (String prefix : prefixes) {
            String from = prefix + oldName, to = prefix + newName;
            if (!values.containsKey(from) || values.containsKey(to)) continue;
            Object value = values.get(from);
            if (value instanceof String) edit.putString(to, (String) value);
            else if (value instanceof Integer) edit.putInt(to, (Integer) value);
            else if (value instanceof Long) edit.putLong(to, (Long) value);
            else if (value instanceof Boolean) edit.putBoolean(to, (Boolean) value);
        }
        String from = "annotations_" + Integer.toHexString(oldName.hashCode());
        String to = "annotations_" + Integer.toHexString(newName.hashCode());
        if (!from.equals(to) && values.containsKey(from)) {
            org.json.JSONArray combined = new org.json.JSONArray(prefs.getString(to, "[]"));
            org.json.JSONArray source = new org.json.JSONArray(prefs.getString(from, "[]"));
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < combined.length(); i++) { JSONObject item = combined.optJSONObject(i); if (item != null) ids.add(item.optString("id", "")); }
            for (int i = 0; i < source.length(); i++) { JSONObject item = source.optJSONObject(i); if (item != null && ids.add(item.optString("id", ""))) combined.put(item); }
            edit.putString(to, combined.toString());
        }
        // Original keys are intentionally retained as a recovery source.
        return edit;
    }

    void recordRestoredCover(String hash,String path,String remoteId){
        if(hash==null||hash.isEmpty())return;
        if(hasBookTombstone(hash))return;
        ContentValues v=new ContentValues();v.put("custom_cover_path",path==null?"":path);v.put("remote_cover_file_id",remoteId==null?"":remoteId);
        v.put("cover_sync_dirty",0);v.put("cover_upload_session_url","");v.put("cover_upload_offset",0L);
        getWritableDatabase().update("books",v,"content_hash=?",new String[]{hash});
    }
    String customCoverPath(String fileName){return scalarString("SELECT custom_cover_path FROM books WHERE file_name=?",new String[]{fileName});}
    String coverScope(String fileName){String s=scalarString("SELECT cover_scope FROM books WHERE file_name=?",new String[]{fileName});return "everywhere".equals(s)?"everywhere":"library_only";}
    void updateBookMetadata(String fileName,String title,String author){if(fileName==null)return;ContentValues v=new ContentValues();v.put("title",title==null||title.trim().isEmpty()?stripExtension(fileName):title.trim());v.put("author",author==null?"":author.trim());v.put("updated_at",System.currentTimeMillis());getWritableDatabase().update("books",v,"file_name=?",new String[]{fileName});}
    void updateCustomCover(String fileName,String path,String scope){if(fileName==null)return;ContentValues v=new ContentValues();v.put("custom_cover_path",path==null?"":path);v.put("cover_scope","everywhere".equals(scope)?"everywhere":"library_only");v.put("cover_updated_at",System.currentTimeMillis());v.put("cover_sync_dirty",1);v.put("cover_upload_session_url","");v.put("cover_upload_offset",0L);getWritableDatabase().update("books",v,"file_name=?",new String[]{fileName});}
    void setCoverScope(String fileName,String scope){if(fileName==null)return;ContentValues v=new ContentValues();v.put("cover_scope","everywhere".equals(scope)?"everywhere":"library_only");v.put("cover_updated_at",System.currentTimeMillis());if(!customCoverPath(fileName).isEmpty())v.put("cover_sync_dirty",1);getWritableDatabase().update("books",v,"file_name=?",new String[]{fileName});}
    void clearCustomCover(String fileName){updateCustomCover(fileName,"","library_only");}
    static final class PendingCoverRow{final String fileName,filePath,hash,coverPath,remoteId,session;final long offset;PendingCoverRow(Cursor c){fileName=c.getString(0);filePath=c.getString(1);hash=c.getString(2);coverPath=c.getString(3);remoteId=c.getString(4);session=c.getString(5);offset=c.getLong(6);}}
    List<PendingCoverRow> pendingCoverUploads(int limit){int n=Math.max(1,Math.min(100,limit));List<PendingCoverRow> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT file_name,file_path,content_hash,custom_cover_path,remote_cover_file_id,cover_upload_session_url,cover_upload_offset FROM books WHERE file_path IS NOT NULL AND TRIM(file_path)<>'' AND cover_sync_dirty=1 ORDER BY cover_updated_at ASC LIMIT "+n,null)){while(c.moveToNext())out.add(new PendingCoverRow(c));}return out;}
    int pendingCoverUploadCount(){return syncCount("SELECT COUNT(*) FROM books WHERE file_path IS NOT NULL AND TRIM(file_path)<>'' AND cover_sync_dirty=1");}
    void saveCoverUploadCheckpoint(String fileName,String session,long offset){ContentValues v=new ContentValues();v.put("cover_upload_session_url",session==null?"":session);v.put("cover_upload_offset",Math.max(0L,offset));getWritableDatabase().update("books",v,"file_name=?",new String[]{fileName});}
    void markCoverSynced(String fileName,String remoteId){ContentValues v=new ContentValues();v.put("remote_cover_file_id",remoteId==null?"":remoteId);v.put("cover_sync_dirty",0);v.put("cover_upload_session_url","");v.put("cover_upload_offset",0L);getWritableDatabase().update("books",v,"file_name=?",new String[]{fileName});}

    void upsertImportedBook(File file,String hash,String title,String author,long addedAt) {
        if(file==null||!file.isFile())return;
        SQLiteDatabase sql=getWritableDatabase();ContentValues v=new ContentValues();String name=file.getName();
        String oldTitle=scalarString(sql,"SELECT title FROM books WHERE file_name=?",new String[]{name});
        String oldAuthor=scalarString(sql,"SELECT author FROM books WHERE file_name=?",new String[]{name});
        // A refresh may supply a hash/numeric transport placeholder. Never erase
        // established indexed metadata; numeric titles remain valid deliberate
        // renames (for example, "1984"), so do not reject them as old values.
        if(!meaningfulDisplayTitle(title)&&establishedStoredTitle(oldTitle))title=oldTitle;
        if((author==null||author.trim().isEmpty())&&!oldAuthor.trim().isEmpty())author=oldAuthor;
        v.put("file_name",name);v.put("file_path",file.getAbsolutePath());v.put("format",name.toLowerCase(Locale.ROOT).endsWith(".pdf")?"pdf":"epub");
        v.put("title",title==null||title.trim().isEmpty()?stripExtension(name):title.trim());v.put("author",author==null?"":author.trim());
        if(hash!=null&&!hash.isEmpty())v.put("content_hash",hash);v.put("file_size",file.length());v.put("modified_at",file.lastModified());v.put("added_at",Math.max(0L,addedAt));
        v.put("sync_dirty",1);v.put("updated_at",System.currentTimeMillis());
        sql.insertWithOnConflict("books",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static boolean establishedStoredTitle(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        String s = value.trim();
        return !s.matches("(?i)[0-9a-f]{32}|[0-9a-f]{40}|[0-9a-f]{64}");
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

    boolean removeBookNow(String fileName) {
        if (fileName == null || fileName.isEmpty()) return false;
        SQLiteDatabase sql = getWritableDatabase(); sql.beginTransaction();
        try {
            String hash = "", remote = "";
            try (Cursor c = sql.rawQuery("SELECT content_hash,remote_file_id FROM books WHERE file_name=? LIMIT 1", new String[]{fileName})) {
                if (!c.moveToFirst()) { sql.setTransactionSuccessful(); return false; }
                hash = c.getString(0) == null ? "" : c.getString(0); remote = c.getString(1) == null ? "" : c.getString(1);
            }
            if (!hash.isEmpty()) {
            ContentValues t = new ContentValues(); t.put("content_hash", hash); t.put("file_name", fileName); t.put("remote_file_id", remote); t.put("deleted_at", System.currentTimeMillis()); t.put("sync_dirty", 1);
            if (sql.insertWithOnConflict("book_tombstones", null, t, SQLiteDatabase.CONFLICT_REPLACE) == -1 || !hasBookTombstone(hash)) throw new IllegalStateException("Unable to retain book deletion");
            }
            sql.delete("shelf_books", "file_name=?", new String[]{fileName});
            sql.delete("books", "file_name=?", new String[]{fileName});
            sql.setTransactionSuccessful(); return true;
        } finally { sql.endTransaction(); }
    }
    void removeBook(String fileName) { if (fileName != null) WRITER.execute(() -> removeBookNow(fileName)); }



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
            // Keep book_day for historical totals and notes, but do not display a
            // row after its library book has been deleted. A later re-import creates
            // a books row again and intentionally makes the name visible again.
            c = getReadableDatabase().rawQuery("SELECT d.file_name,d.duration_ms FROM book_day d JOIN books b ON b.file_name=d.file_name WHERE d.day=? AND d.duration_ms>0 ORDER BY d.duration_ms DESC", new String[]{day});
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
        return (int) scalarLong("SELECT COUNT(DISTINCT d.file_name) FROM book_day d JOIN books b ON b.file_name=d.file_name WHERE d.day LIKE ? AND d.duration_ms>0", new String[]{prefix});
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

    static final class InvalidSnapshotException extends IllegalStateException {
        InvalidSnapshotException(String message) { super(message); }
        InvalidSnapshotException(String message, Throwable cause) { super(message, cause); }
    }

    /** Read-only, cheap preflight used before any local database or preference mutation. */
    static void validateSnapshot(File snapshot) {
        if (snapshot == null || !snapshot.isFile() || snapshot.length() == 0L)
            throw new InvalidSnapshotException("Reader snapshot is missing or empty");
        SQLiteDatabase db = null;
        try {
            db = openSnapshotReadOnly(snapshot);
            try (Cursor check = db.rawQuery("PRAGMA quick_check", null)) {
                if (!check.moveToFirst() || !"ok".equalsIgnoreCase(check.getString(0)))
                    throw new InvalidSnapshotException("Reader snapshot failed SQLite integrity check");
            }
            // Keep this list identical to the core merge query below. This rejects a remote
            // database that happens to be valid SQLite but cannot represent reader state.
            try (Cursor core = db.rawQuery("SELECT file_name,content_hash,format,file_size,modified_at,added_at,last_opened_at,progress,finished_at,epub_spine,epub_offset,pdf_page,updated_at FROM books", null)) {
                // Executing the query is the schema check; no rows need to be read.
            }
            String[][] optionalTables = {
                    {"book_tombstones", "content_hash", "file_name", "remote_file_id", "deleted_at"},
                    {"shelves", "name", "updated_at"},
                    {"shelf_books", "shelf_name", "file_name", "added_at"},
                    {"reading_day", "day", "total_ms", "daily_note"},
                    {"book_day", "day", "file_name", "duration_ms", "book_note"}
            };
            for (String[] table : optionalTables) {
                if (tableExists(db, table[0]) && !hasColumns(db, table[0], java.util.Arrays.copyOfRange(table, 1, table.length)))
                    throw new InvalidSnapshotException("Reader snapshot contains incomplete " + table[0] + " metadata");
            }
        } catch (InvalidSnapshotException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidSnapshotException("Reader snapshot is corrupt or missing required books columns", e);
        } finally { if (db != null) db.close(); }
    }

    private static SQLiteDatabase openSnapshotReadOnly(File snapshot) {
        return SQLiteDatabase.openDatabase(snapshot.getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READONLY, PRESERVE_DATABASE);
    }

    void mergeSnapshot(File snapshot, SharedPreferences prefs) {
        mergeSnapshot(snapshot, prefs, false);
    }

    /**
     * Merge the cloud state database without allowing it to manufacture local file entries.
     * The two-argument form remains the legacy/explicit-restore behavior; automatic metadata
     * pulls should use existingBooksOnly=true.
     */
    void mergeSnapshot(File snapshot, SharedPreferences prefs, boolean existingBooksOnly) {
        validateSnapshot(snapshot);
        SQLiteDatabase remote = null;
        SQLiteDatabase local = getWritableDatabase();
        SharedPreferences.Editor stagedPrefs = prefs == null ? null : prefs.edit();
        local.beginTransaction();
        try {
            remote = openSnapshotReadOnly(snapshot);
            // Import delete intent first. A tombstone suppresses cloud resurrection, but never
            // deletes this device's bytes or reading history.
            try (Cursor exists = remote.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='book_tombstones'", null)) {
                if (exists.moveToFirst()) {
                    try (Cursor tombstones = remote.rawQuery("SELECT content_hash,file_name,remote_file_id,deleted_at FROM book_tombstones", null)) {
                        while (tombstones.moveToNext()) {
                            String hash = tombstones.getString(0);
                            try { DriveDeletionGuard.markerName(hash); } catch (IllegalArgumentException invalid) { continue; }
                            ContentValues t = new ContentValues();
                            t.put("content_hash", hash);
                            t.put("file_name", tombstones.getString(1));
                            t.put("remote_file_id", tombstones.getString(2));
                            t.put("deleted_at", tombstones.getLong(3));
                            t.put("sync_dirty", 0);
                            if (local.insertWithOnConflict("book_tombstones", null, t, SQLiteDatabase.CONFLICT_IGNORE) == -1
                                    && !hasBookTombstone(hash)) {
                                throw new IllegalStateException("Unable to retain cloud deletion");
                            }
                            ContentValues clear = new ContentValues();
                            clear.put("sync_dirty", 0); clear.put("upload_session_url", ""); clear.put("upload_offset", 0L);
                            clear.put("cover_sync_dirty", 0); clear.put("cover_upload_session_url", ""); clear.put("cover_upload_offset", 0L);
                            clear.put("remote_file_id", ""); clear.put("remote_cover_file_id", "");
                            local.update("books", clear, "content_hash=?", new String[]{hash});
                        }
                    }
                }
            }

            Set<String> acceptedBooks = new HashSet<>();
            java.util.Map<String,String> restoredNames = new java.util.HashMap<>();
            try (Cursor c = remote.rawQuery("SELECT file_name,content_hash,format,file_size,modified_at,added_at,last_opened_at,progress,finished_at,epub_spine,epub_offset,pdf_page,updated_at FROM books", null)) {
                while (c.moveToNext()) {
                    String name = c.getString(0);
                    String hash = c.isNull(1) ? "" : c.getString(1);
                    long remoteUpdated = c.getLong(12);
                    if (!hash.isEmpty() && hasBookTombstone(hash)) continue;
                    if (!hash.isEmpty()) {
                        String sameBook = fileNameForHash(hash);
                        if (!sameBook.isEmpty() && !sameBook.equals(name)) {
                            restoredNames.put(name, sameBook);
                            name = sameBook;
                        }
                    }
                    boolean found;
                    long localUpdated = 0L;
                    boolean localDirty = false;
                    String localPath = "";
                    try (Cursor lc = local.rawQuery("SELECT updated_at,sync_dirty,file_path,content_hash FROM books WHERE file_name=?", new String[]{name})) {
                        found = lc.moveToFirst();
                        if (found) {
                            localUpdated = lc.getLong(0); localDirty = lc.getInt(1) != 0; localPath = lc.getString(2) == null ? "" : lc.getString(2);
                            String localHash = lc.isNull(3) ? "" : lc.getString(3);
                            // A filename collision is not permission to reassign an installed book.
                            if (!localPath.isEmpty() && !localHash.isEmpty() && !hash.isEmpty() && !hash.equals(localHash)) continue;
                        }
                    }
                    if (found && (localDirty || remoteUpdated < localUpdated)) continue;
                    if (!found && existingBooksOnly) continue;
                    ContentValues v = new ContentValues();
                    v.put("file_name", name);
                    if (!hash.isEmpty()) v.put("content_hash", hash);
                    // Never import a cloud machine's path. Explicit restore records a verified
                    // local path separately; an imported placeholder is intentionally empty.
                    if (!found) v.put("file_path", "");
                    v.put("format", c.getString(2)); v.put("file_size", c.getLong(3)); v.put("modified_at", c.getLong(4));
                    v.put("added_at", c.getLong(5)); v.put("last_opened_at", c.getLong(6)); v.put("progress", c.getInt(7));
                    v.put("finished_at", c.getLong(8)); v.put("epub_spine", c.getInt(9)); v.put("epub_offset", c.getInt(10));
                    v.put("pdf_page", c.getInt(11)); v.put("updated_at", remoteUpdated); v.put("sync_dirty", 0);
                    int changed = found
                            ? local.update("books", v, "file_name=?", new String[]{name})
                            : (int) local.insertWithOnConflict("books", null, v, SQLiteDatabase.CONFLICT_REPLACE);
                    if (changed < 1) throw new IllegalStateException("Unable to merge book metadata");
                    acceptedBooks.add(name);
                    if (stagedPrefs != null) {
                        SharedPreferences.Editor e = stagedPrefs.putInt("percent_" + name, c.getInt(7))
                                .putInt("epub_chapter_" + name, c.getInt(9)).putInt("epub_scroll_" + name, c.getInt(10)).putInt("pdf_page_" + name, c.getInt(11));
                        if (c.getLong(8) > 0) e.putLong("finished_at_" + name, c.getLong(8));
                        if (c.getLong(6) > 0) e.putLong("last_opened_" + name, c.getLong(6));
                        // The canonical location key is preferred by the reader. Do not create
                        // it for a cloud-only row: only a verified local file may receive it.
                        if (found && "epub".equalsIgnoreCase(c.getString(2)) && !localPath.isEmpty()) {
                            File matchingFile = new File(localPath);
                            if (matchingFile.isFile() && matchingFile.length() == c.getLong(3)) {
                                String key = ReaderLocationPolicy.key("epub_location_", matchingFile);
                                long canonicalAt = prefs.getLong(key + "updated_ms", 0L);
                                if (canonicalAt <= remoteUpdated) {
                                    e.putInt(key + "chapter", c.getInt(9)).putInt(key + "permille", c.getInt(10))
                                            .putString(key + "anchor", ReaderLocationPolicy.anchor(c.getInt(9), c.getInt(10)))
                                            .putLong(key + "updated_ms", remoteUpdated);
                                }
                            }
                        }
                    }
                }
            }
            // v64+ metadata is optional so older snapshots remain restorable. Only accepted
            // rows are touched, and local dirty state wins (cover dirty is independent).
            if (hasColumns(remote, "books", new String[]{"title", "author", "annotation_count",
                    "cover_scope", "cover_updated_at", "remote_cover_file_id"})) {
            try (Cursor extra = remote.rawQuery("SELECT file_name,title,author,annotation_count,cover_scope,cover_updated_at,remote_cover_file_id FROM books", null)) {
                while (extra.moveToNext()) {
                    String remoteName = extra.getString(0);
                    String name = restoredNames.containsKey(remoteName) ? restoredNames.get(remoteName) : remoteName;
                    if (!acceptedBooks.contains(name)) continue;
                    long remoteCoverAt = extra.getLong(5);
                    try (Cursor lc = local.rawQuery("SELECT sync_dirty,cover_sync_dirty,cover_updated_at FROM books WHERE file_name=?", new String[]{name})) {
                        if (!lc.moveToFirst() || lc.getInt(0) != 0) continue;
                        boolean coverDirty = lc.getInt(1) != 0;
                        long localCoverAt = lc.getLong(2);
                        ContentValues v = new ContentValues();
                        v.put("title", extra.getString(1) == null ? "" : extra.getString(1));
                        v.put("author", extra.getString(2) == null ? "" : extra.getString(2));
                        v.put("annotation_count", extra.getInt(3));
                        if (!coverDirty && remoteCoverAt >= localCoverAt) {
                            v.put("cover_scope", "everywhere".equals(extra.getString(4)) ? "everywhere" : "library_only");
                            v.put("cover_updated_at", remoteCoverAt);
                            v.put("remote_cover_file_id", extra.getString(6) == null ? "" : extra.getString(6));
                        }
                        local.update("books", v, "file_name=?", new String[]{name});
                    }
                }
            }
            }
            if (tableExists(remote, "shelves")) {
            try (Cursor sh = remote.rawQuery("SELECT name,updated_at FROM shelves", null)) {
                while (sh.moveToNext()) { ContentValues v = new ContentValues(); v.put("name", sh.getString(0)); v.put("updated_at", sh.getLong(1)); mergeRow(local, "shelves", v); }
            }
            }
            if (tableExists(remote, "shelf_books")) {
            try (Cursor sh = remote.rawQuery("SELECT shelf_name,file_name,added_at FROM shelf_books", null)) {
                while (sh.moveToNext()) { ContentValues v = new ContentValues(); v.put("shelf_name", sh.getString(0)); v.put("file_name", restoredNames.containsKey(sh.getString(1)) ? restoredNames.get(sh.getString(1)) : sh.getString(1)); v.put("added_at", sh.getLong(2)); mergeRow(local, "shelf_books", v); }
            }
            }
            if (tableExists(remote, "reading_day")) {
            try (Cursor c = remote.rawQuery("SELECT day,total_ms,daily_note FROM reading_day", null)) {
                while (c.moveToNext()) { String day=c.getString(0); long ms=c.getLong(1); String note=c.getString(2); ContentValues v=new ContentValues(); v.put("day",day); v.put("total_ms",Math.max(scalarLong("SELECT total_ms FROM reading_day WHERE day=?",new String[]{day}),ms)); v.put("daily_note",(note!=null&&!note.isEmpty())?note:scalarString("SELECT daily_note FROM reading_day WHERE day=?",new String[]{day})); mergeRow(local, "reading_day", v); }
            }
            }
            if (tableExists(remote, "book_day")) {
            try (Cursor c = remote.rawQuery("SELECT day,file_name,duration_ms,book_note FROM book_day", null)) {
                while (c.moveToNext()) { String day=c.getString(0), remoteName=c.getString(1), name=restoredNames.containsKey(remoteName)?restoredNames.get(remoteName):remoteName; ContentValues v=new ContentValues(); v.put("day",day); v.put("file_name",name); v.put("duration_ms",Math.max(scalarLong("SELECT duration_ms FROM book_day WHERE day=? AND file_name=?",new String[]{day,name}),c.getLong(2))); String existingNote=bookDayNote(day,name); v.put("book_note",restoredNames.containsKey(remoteName)&&!existingNote.isEmpty()?existingNote:((c.getString(3)!=null&&!c.getString(3).isEmpty())?c.getString(3):existingNote)); mergeRow(local, "book_day", v); }
            }
            }
            ContentValues meta = new ContentValues(); meta.put("k", META_LEGACY_MIGRATED); meta.put("v", "1");
            mergeRow(local, "meta", meta);
            local.setTransactionSuccessful();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to merge reader state snapshot", e);
        } finally {
            local.endTransaction();
            if (remote != null) remote.close();
        }
        // Publish compatibility preferences only after the SQLite transaction committed.
        if (stagedPrefs != null) stagedPrefs.apply();
    }

    private static void mergeRow(SQLiteDatabase db, String table, ContentValues values) {
        if (db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE) < 0)
            throw new IllegalStateException("Unable to merge " + table + " metadata");
    }

    boolean repairRestoredMetadata(String fileName, String expectedTitle, String title, String author) {
        if (fileName == null || expectedTitle == null || title == null) return false;
        // One statement prevents a late metadata read from replacing a concurrent user rename.
        SQLiteDatabase sql = getWritableDatabase();
        try (android.database.sqlite.SQLiteStatement statement = sql.compileStatement(
                "UPDATE books SET title=?,author=CASE WHEN COALESCE(TRIM(author),'')='' THEN ? ELSE author END " +
                "WHERE file_name=? AND COALESCE(title,'')=? AND file_path IS NOT NULL AND TRIM(file_path)<>''")) {
            statement.bindString(1, title); statement.bindString(2, author == null ? "" : author.trim());
            statement.bindString(3, fileName); statement.bindString(4, expectedTitle);
            return statement.executeUpdateDelete() == 1;
        }
    }

    private boolean metaIs(String key) { return "1".equals(scalarString("SELECT v FROM meta WHERE k=?", new String[]{key})); }
    private void putMeta(String key, String value) { ContentValues v = new ContentValues(); v.put("k", key); v.put("v", value); mergeRow(getWritableDatabase(), "meta", v); }

    void reconcileLocalLibrary(File libraryDir, SharedPreferences prefs) {
        if (libraryDir == null) return;
        try {
            File dir = libraryDir.getCanonicalFile();
            if (!dir.isDirectory()) return;
            SQLiteDatabase sql = getWritableDatabase();
            // Keyset batches avoid materializing a large library, and filesystem checks are
            // never performed inside a long write transaction or on the UI thread.
            String after = "";
            while (true) {
                List<String[]> rows = new ArrayList<>();
                try (Cursor c = sql.rawQuery("SELECT file_name,file_path,content_hash FROM books WHERE file_name>? ORDER BY file_name LIMIT 200", new String[]{after})) {
                    while (c.moveToNext()) rows.add(new String[]{c.getString(0), c.getString(1), c.getString(2)});
                }
                if (rows.isEmpty()) break;
                for (String[] row : rows) {
                    String name = row[0], oldPath = row[1], hash = row[2] == null ? "" : row[2];
                    after = name;
                    File bound = null;
                    if (oldPath != null && !oldPath.isEmpty()) {
                        File f = new File(oldPath).getCanonicalFile();
                        if (dir.equals(f.getParentFile()) && f.isFile() && isBook(f.getName())) bound = f;
                    }
                    if (bound == null && new File(name).getName().equals(name)) {
                        File candidate = new File(dir, name).getCanonicalFile();
                        if (dir.equals(candidate.getParentFile()) && candidate.isFile() && isBook(name)) {
                            // A name match alone must not attach cloud metadata to a different book.
                            if (hash.isEmpty() || (hash.matches("(?i)[0-9a-f]{64}") && hash.equalsIgnoreCase(FileIdentityUtil.sha256(candidate)))) bound = candidate;
                        }
                    }
                    if (bound != null && !name.equals(bound.getName()) && !hash.isEmpty()) {
                        recordRestoredBook(bound, hash, "", prefs);
                    } else {
                        String newPath = bound == null ? "" : bound.getCanonicalPath();
                        if (!newPath.equals(oldPath == null ? "" : oldPath)) {
                            ContentValues v = new ContentValues(); v.put("file_path", newPath);
                            // Guard against a concurrent restore binding the row after it was read.
                            sql.update("books", v, "file_name=? AND COALESCE(file_path,'')=?", new String[]{name, oldPath == null ? "" : oldPath});
                        }
                    }
                }
            }
        } catch (Exception e) { throw new IllegalStateException("Unable to reconcile local library", e); }
    }

    private static boolean tableExists(SQLiteDatabase db,String name){try(Cursor c=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{name})){return c.moveToFirst();}}
    private static boolean hasColumns(SQLiteDatabase db,String table,String[] columns){try(Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null)){Set<String> found=new HashSet<>();while(c.moveToNext())found.add(c.getString(1));for(String col:columns)if(!found.contains(col))return false;return true;}}

    void checkpoint() {
        try { if(!Thread.currentThread().getName().equals("wow-reader-state-db")) WRITER.submit(() -> {}).get();
            try(Cursor c=getWritableDatabase().rawQuery("PRAGMA wal_checkpoint(FULL)",null)){if(!c.moveToFirst()||c.getInt(0)!=0)throw new IllegalStateException("Reader database is busy; retry snapshot");}
        } catch(Exception e){throw new IllegalStateException("Unable to checkpoint reader state",e);}
    }
    File databaseFile(Context context){checkpoint();return context.getDatabasePath(DB_NAME);}

    /** Freeze a consistent main database image without copying a live WAL database. */
    File createSnapshot(Context context) throws java.io.IOException {
        // Never use the SQLiteOpenHelper monitor while waiting for its queued writer.
        synchronized (SNAPSHOT_LOCK) { return createSnapshotLocked(context); }
    }

    private File createSnapshotLocked(Context context) throws java.io.IOException {
        checkpoint();
        SQLiteDatabase sql = getWritableDatabase();
        if (sql.inTransaction()) throw new java.io.IOException("Cannot snapshot an active reader transaction");
        boolean wal = sql.isWriteAheadLoggingEnabled();
        File snapshot = File.createTempFile("wow-reader-db-", ".db", context.getCacheDir());
        boolean complete = false;
        try {
            // In rollback-journal mode an exclusive transaction freezes the main database.
            // Disabling WAL flushes its remaining commits, closing the checkpoint/copy race.
            // API 23 does not provide SQLite VACUUM INTO or a public SQLite backup API.
            if (wal) sql.disableWriteAheadLogging();
            sql.beginTransaction();
            try {
                try (java.io.InputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(context.getDatabasePath(DB_NAME)));
                     java.io.OutputStream out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(snapshot))) {
                    byte[] buffer = new byte[64 * 1024]; int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                }
                sql.setTransactionSuccessful();
            } finally { sql.endTransaction(); }
            validateSnapshot(snapshot);
            complete = true;
            return snapshot;
        } catch (RuntimeException e) {
            throw new java.io.IOException("Unable to freeze reader database", e);
        } finally {
            if (wal) try { sql.enableWriteAheadLogging(); } catch (Exception ignored) { }
            if (!complete) snapshot.delete();
        }
    }

    private long scalarLong(String sql, String[] args) {
        Cursor c = null; try { c = getReadableDatabase().rawQuery(sql, args); return c.moveToFirst() ? c.getLong(0) : 0L; }
        catch (Exception ignored) { return 0L; } finally { if (c != null) c.close(); }
    }
    private String scalarString(String sql, String[] args) {
        Cursor c = null; try { c = getReadableDatabase().rawQuery(sql, args); return c.moveToFirst() && c.getString(0) != null ? c.getString(0) : ""; }
        catch (Exception ignored) { return ""; } finally { if (c != null) c.close(); }
    }
    private static String scalarString(SQLiteDatabase db, String sql, String[] args) {
        Cursor c = null; try { c = db.rawQuery(sql, args); return c.moveToFirst() && c.getString(0) != null ? c.getString(0) : ""; }
        catch (Exception ignored) { return ""; } finally { if (c != null) c.close(); }
    }

    private static String stripExtension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : (name == null ? "Book" : name);
    }
    private static boolean isBook(String name) {
        String s = name == null ? "" : name.toLowerCase(Locale.ROOT); return s.endsWith(".epub") || s.endsWith(".pdf");
    }
    private static JSONObject object(String raw) { try { return new JSONObject(raw == null ? "{}" : raw); } catch (Exception ignored) { return new JSONObject(); } }
    private static String dayKey(long ms) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(ms)); }
}
