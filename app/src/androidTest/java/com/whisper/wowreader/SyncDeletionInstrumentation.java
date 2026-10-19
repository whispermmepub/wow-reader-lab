package com.whisper.wowreader;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.lang.reflect.Constructor;
import java.util.concurrent.Callable;
/** Focused SQLite regression coverage for sync deletion/tombstone semantics. */
final class SyncDeletionInstrumentation {
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String HASH_OLD = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
    private final Context context;
    private SyncDeletionInstrumentation(Context context) {this.context=context;}
    static void run(Context context)throws Exception {
        new SyncDeletionInstrumentation(context).runRegression();
    }
    private Context getTargetContext() {return context;}
    private void runRegression() throws Exception {
        File root = new File(getTargetContext().getCacheDir(), "sync-deletion-instrumentation");
        deleteRecursively(root);
        if (!root.mkdirs() && !root.isDirectory()) throw new AssertionError("isolated cache unavailable");
        ReaderStateDb db = isolatedDb(new File(root, "local"));
        SQLiteDatabase sql = db.getWritableDatabase();
        File bookFile = new File(root, "book.epub");
        if (!bookFile.createNewFile()) throw new AssertionError("fixture file unavailable");
        insertBook(sql, "book.epub", bookFile, HASH, 42, 1, 1, "remote-book", "remote-cover");
        sql.execSQL("INSERT INTO reading_day(day,total_ms,daily_note) VALUES('2026-01-02',700,'keep')");
        sql.execSQL("INSERT INTO book_day(day,file_name,duration_ms,book_note) VALUES('2026-01-02','book.epub',500,'history')");

        db.removeBook("book.epub");
        await(() -> db.hasBookTombstone(HASH), "removeBook tombstone");
        check(count(sql, "SELECT COUNT(*) FROM books WHERE file_name='book.epub'") == 0, "removeBook deletes local row");
        check(count(sql, "SELECT COUNT(*) FROM book_day WHERE file_name='book.epub'") == 1, "removeBook retains history");

        insertBook(sql, "kept.epub", bookFile, HASH, 73, 1, 1, "remote-book", "remote-cover");
        db.rememberRemoteDeletion(HASH);
        check(db.hasBookTombstone(HASH), "remembered deletion has tombstone");
        check(count(sql, "SELECT COUNT(*) FROM books WHERE file_name='kept.epub' AND progress=73") == 1, "remote deletion preserves local book/progress");
        check(db.pendingBookUploadCount() == 0 && db.pendingCoverUploadCount() == 0, "remote deletion suppresses book and cover queues");
        check(count(sql, "SELECT COUNT(*) FROM books WHERE content_hash=? AND sync_dirty=0 AND cover_sync_dirty=0 AND upload_session_url='' AND cover_upload_session_url='' AND remote_file_id='' AND remote_cover_file_id=''", HASH) == 1, "remote deletion clears dirty state/checkpoints/remote IDs");
        db.markTombstoneSynced(HASH);
        check(count(sql, "SELECT COUNT(*) FROM book_tombstones WHERE content_hash=? AND sync_dirty=0", HASH) == 1, "markTombstoneSynced acknowledges tombstone");

        File restored = new File(root, "restored.epub");
        if (!restored.createNewFile()) throw new AssertionError("restore fixture unavailable");
        db.recordRestoredBook(restored, HASH, "restore-id", getTargetContext().getSharedPreferences("sync-deletion-regression", 0));
        db.recordRestoredCover(HASH, "/tmp/cover.jpg", "cover-id");
        check(count(sql, "SELECT COUNT(*) FROM books WHERE content_hash=?", HASH) == 1, "tombstone blocks restored book replacement");
        check(count(sql, "SELECT COUNT(*) FROM books WHERE content_hash=? AND custom_cover_path=''", HASH) == 1, "tombstone blocks restored cover replacement");

        File remoteSnapshot = snapshot(root, true, HASH, "remote-deleted.epub");
        ReaderStateDb merged = isolatedDb(new File(root, "merged"));
        merged.mergeSnapshot(remoteSnapshot, null);
        SQLiteDatabase mergedSql = merged.getWritableDatabase();
        check(merged.hasBookTombstone(HASH), "merge imports tombstones");
        check(count(mergedSql, "SELECT COUNT(*) FROM books WHERE content_hash=?", HASH) == 0, "merge imports tombstone before conflicting book");

        File oldSnapshot = snapshot(root, false, HASH_OLD, "old-compatible.epub");
        merged.mergeSnapshot(oldSnapshot, null);
        check(count(mergedSql, "SELECT COUNT(*) FROM books WHERE content_hash=?", HASH_OLD) == 1, "old snapshot without tombstones remains compatible");
        db.close(); merged.close(); deleteRecursively(root);
    }

    private File snapshot(File root, boolean withTombstone, String hash, String name) throws Exception {
        ReaderStateDb source = isolatedDb(new File(root, withTombstone ? "remote-new" : "remote-old"));
        SQLiteDatabase sql = source.getWritableDatabase();
        File file = new File(root, name); file.createNewFile();
        insertBook(sql, name, file, hash, 12, 0, 0, "", "");
        if (withTombstone) sql.execSQL("INSERT INTO book_tombstones(content_hash,file_name,remote_file_id,deleted_at,sync_dirty) VALUES(?,?,?,1,1)", new Object[]{hash, name, "remote-id"});
        else sql.execSQL("DROP TABLE book_tombstones");
        source.checkpoint();
        File dbFile = source.databaseFile(new IsolatedContext(getTargetContext(), new File(root, withTombstone ? "remote-new" : "remote-old")));
        File copy = new File(root, withTombstone ? "new-snapshot.db" : "old-snapshot.db");
        copyBytes(dbFile, copy);
        source.close();
        return copy;
    }

    private ReaderStateDb isolatedDb(File directory) throws Exception {
        if (!directory.exists() && !directory.mkdirs()) throw new AssertionError("cannot create isolated db directory");
        Constructor<ReaderStateDb> constructor = ReaderStateDb.class.getDeclaredConstructor(Context.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new IsolatedContext(getTargetContext(), directory));
    }

    private static void insertBook(SQLiteDatabase db, String name, File file, String hash, int progress, int dirty, int coverDirty, String remoteId, String coverId) {
        db.execSQL("INSERT INTO books(file_name,content_hash,file_path,format,title,author,file_size,modified_at,progress,remote_file_id,remote_cover_file_id,sync_dirty,cover_sync_dirty,upload_session_url,cover_upload_session_url) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                new Object[]{name, hash, file.getAbsolutePath(), "epub", "Fixture", "Author", file.length(), file.lastModified(), progress, remoteId, coverId, dirty, coverDirty, "book-session", "cover-session"});
    }

    private static long count(SQLiteDatabase db, String query, String... args) {
        try (Cursor c = db.rawQuery(query, args)) { return c.moveToFirst() ? c.getLong(0) : 0; }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void await(Callable<Boolean> condition, String label) throws Exception {
        long deadline = android.os.SystemClock.uptimeMillis() + 10000;
        while (android.os.SystemClock.uptimeMillis() < deadline) { if (condition.call()) return; android.os.SystemClock.sleep(40); }
        throw new AssertionError("timeout: " + label);
    }
    private static void copyBytes(File from, File to) throws Exception {
        try (java.io.InputStream in = new java.io.FileInputStream(from); java.io.OutputStream out = new java.io.FileOutputStream(to)) {
            byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) >= 0) { if (n > 0) out.write(buffer, 0, n); }
        }
    }
    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) { File[] children = file.listFiles(); if (children != null) for (File child : children) deleteRecursively(child); }
        file.delete();
    }

    private static final class IsolatedContext extends ContextWrapper {
        private final File directory;
        IsolatedContext(Context base, File directory) { super(base); this.directory = directory; }
        @Override public Context getApplicationContext() { return this; }
        @Override public File getDatabasePath(String name) { return new File(directory, name); }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) { return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory); }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler errorHandler) { return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getAbsolutePath(), factory, errorHandler); }
    }
}
