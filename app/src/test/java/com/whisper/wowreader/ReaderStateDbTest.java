package com.whisper.wowreader;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.Assert.*;

/** Host Android/SQLite regression tests; these do not replace physical-device tests. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 28, 35, 36}, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ReaderStateDbTest {
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private File root;
    private ReaderStateDb db;
    private SQLiteDatabase sql;
    private final List<ReaderStateDb> helpers = new ArrayList<>();

    @Before public void setup() throws Exception {
        root = new File(RuntimeEnvironment.getApplication().getCacheDir(), "db-regression-" + UUID.randomUUID());
        assertTrue(root.mkdirs());
        db = isolated("local");
        sql = db.getWritableDatabase();
    }
    @After public void cleanup() {
        for (ReaderStateDb helper : helpers) try { helper.close(); } catch (Exception ignored) {}
        delete(root);
    }
    @Test public void syncedDeletionRetainsAuthorityAndHistory() throws Exception {
        fixture(sql, "deleted.epub", HASH, 62);
        sql.execSQL("INSERT INTO reading_day(day,total_ms,daily_note) VALUES('2026-10-19',9000,'Keep my day note')");
        sql.execSQL("INSERT INTO book_day(day,file_name,duration_ms,book_note) VALUES('2026-10-19','deleted.epub',9000,'Keep my book note')");
        db.removeBook("deleted.epub");
        await(() -> db.hasBookTombstone(HASH));
        assertEquals(0, count(sql, "SELECT COUNT(*) FROM books"));
        assertEquals(1, db.pendingTombstoneCount());
        db.markTombstoneSynced(HASH);
        assertTrue(db.hasBookTombstone(HASH));
        assertEquals(0, db.pendingTombstoneCount());
        assertEquals(9000, db.dayTime("2026-10-19"));
        assertEquals("Keep my day note", db.dailyNote("2026-10-19"));
        assertEquals(1, count(sql, "SELECT COUNT(*) FROM book_day"));
        assertTrue("Deleted book hidden from calendar list", db.booksForDay("2026-10-19").isEmpty());
    }
    @Test public void remoteDeletionPreservesLocalBytesAndProgressButStopsUploads() throws Exception {
        File file = fixture(sql, "local.epub", HASH, 73);
        db.rememberRemoteDeletion(HASH);
        assertTrue(file.isFile());
        assertTrue(db.hasBookTombstone(HASH));
        assertEquals(73, count(sql, "SELECT progress FROM books WHERE file_name='local.epub'"));
        assertEquals(0, db.pendingBookUploadCount());
        assertEquals(0, db.pendingCoverUploadCount());
        assertEquals(1, count(sql, "SELECT COUNT(*) FROM books WHERE remote_file_id='' AND remote_cover_file_id='' AND upload_session_url='' AND cover_upload_session_url=''"));
        db.recordRestoredCover(HASH, "/tmp/should-not-attach.jpg", "stale-cover");
        assertEquals(1, count(sql, "SELECT COUNT(*) FROM books WHERE custom_cover_path=''"));
    }
    @Test public void ignoredTombstoneInsertionCannotLosePendingUploads() throws Exception {
        fixture(sql, "keep-queued.epub", HASH, 38);
        sql.execSQL("CREATE TRIGGER ignore_tombstone BEFORE INSERT ON book_tombstones BEGIN SELECT RAISE(IGNORE); END");
        boolean threw = false;
        try { db.rememberRemoteDeletion(HASH); } catch (IllegalStateException expected) { threw = true; }
        assertTrue("Failed durable marker must propagate", threw);
        assertFalse(db.hasBookTombstone(HASH));
        assertEquals(1, db.pendingBookUploadCount());
        assertEquals(1, db.pendingCoverUploadCount());
    }
    @Test public void databaseFailureCannotMasqueradeAsEmptyQueues() {
        sql.execSQL("DROP TABLE books");
        assertThrows(RuntimeException.class, () -> db.pendingBookUploadCount());
        assertThrows(RuntimeException.class, () -> db.pendingBookUploads(5));
        assertThrows(RuntimeException.class, () -> db.pendingCoverUploadCount());
        assertThrows(RuntimeException.class, () -> db.pendingCoverUploads(5));
        sql.execSQL("DROP TABLE book_tombstones");
        assertThrows(RuntimeException.class, () -> db.pendingTombstoneCount());
        assertThrows(RuntimeException.class, () -> db.pendingTombstones(5));
    }
    @Test public void snapshotDeletionWinsBeforeConflictingBookMetadata() throws Exception {
        ReaderStateDb source = isolated("snapshot-source");
        SQLiteDatabase incoming = source.getWritableDatabase();
        fixture(incoming, "stale.epub", HASH, 10);
        incoming.execSQL("INSERT INTO book_tombstones(content_hash,file_name,deleted_at,sync_dirty) VALUES(?,?,1000,1)", new Object[]{HASH, "stale.epub"});
        File sourcePath = source.databaseFile(new DbContext(RuntimeEnvironment.getApplication(), new File(root, "snapshot-source")));
        source.close();
        File snapshot = new File(root, "remote-snapshot.db");
        Files.copy(sourcePath.toPath(), snapshot.toPath());
        db.mergeSnapshot(snapshot, null);
        assertTrue(db.hasBookTombstone(HASH));
        assertEquals(0, count(sql, "SELECT COUNT(*) FROM books WHERE content_hash=?", HASH));
    }
    @Test public void legacySnapshotWithoutTombstoneTableRemainsReadable() throws Exception {
        ReaderStateDb source = isolated("legacy-source");
        SQLiteDatabase incoming = source.getWritableDatabase();
        fixture(incoming, "legacy.epub", HASH, 21);
        incoming.execSQL("DROP TABLE book_tombstones");
        File sourcePath = source.databaseFile(new DbContext(RuntimeEnvironment.getApplication(), new File(root, "legacy-source")));
        source.close();
        File snapshot = new File(root, "legacy-snapshot.db");
        Files.copy(sourcePath.toPath(), snapshot.toPath());
        db.mergeSnapshot(snapshot, null);
        assertEquals(1, count(sql, "SELECT COUNT(*) FROM books WHERE content_hash=?", HASH));
    }
    @Test public void mergePreservesLocalPathCoverAndUploadCheckpoints() throws Exception {
        File localFile = fixture(sql, "preserve.epub", HASH, 17);
        sql.execSQL("UPDATE books SET sync_dirty=0,title='Local title',custom_cover_path='/local/cover.jpg',cover_sync_dirty=1,cover_upload_session_url='cover-local',cover_upload_offset=23,upload_session_url='book-local',upload_offset=41,remote_file_id='local-id',remote_cover_file_id='local-cover',updated_at=1 WHERE file_name='preserve.epub'");
        ReaderStateDb source = isolated("preserve-source");
        SQLiteDatabase incoming = source.getWritableDatabase();
        fixture(incoming, "preserve.epub", HASH, 92);
        incoming.execSQL("UPDATE books SET title='Remote title',updated_at=100,progress=92,remote_file_id='remote-id',remote_cover_file_id='remote-cover',upload_session_url='remote-session',upload_offset=99,custom_cover_path='/remote/cover.jpg' WHERE file_name='preserve.epub'");
        File sourcePath = source.databaseFile(new DbContext(RuntimeEnvironment.getApplication(), new File(root, "preserve-source")));
        source.close();
        File snapshot = new File(root, "preserve-snapshot.db");
        Files.copy(sourcePath.toPath(), snapshot.toPath());
        db.mergeSnapshot(snapshot, null);
        try (Cursor c = sql.rawQuery("SELECT file_path,title,progress,custom_cover_path,cover_sync_dirty,cover_upload_session_url,cover_upload_offset,upload_session_url,upload_offset,remote_file_id FROM books WHERE file_name='preserve.epub'", null)) {
            assertTrue(c.moveToFirst());
            assertEquals(localFile.getAbsolutePath(), c.getString(0));
            assertEquals("Remote title", c.getString(1));
            assertEquals(92, c.getInt(2));
            assertEquals("/local/cover.jpg", c.getString(3));
            assertEquals(1, c.getInt(4));
            assertEquals("cover-local", c.getString(5));
            assertEquals(23, c.getLong(6));
            assertEquals("book-local", c.getString(7));
            assertEquals(41, c.getLong(8));
            assertEquals("local-id", c.getString(9));
        }
    }
    @Test public void dirtyMetadataWinsOverNewerRemoteExtras() throws Exception {
        fixture(sql, "dirty.epub", HASH, 17);
        sql.execSQL("UPDATE books SET title='Local title',author='Local author',sync_dirty=1,cover_sync_dirty=1 WHERE file_name='dirty.epub'");
        ReaderStateDb source = isolated("dirty-source");
        SQLiteDatabase incoming = source.getWritableDatabase();
        fixture(incoming, "dirty.epub", HASH, 92);
        incoming.execSQL("UPDATE books SET title='Remote title',author='Remote author',updated_at=100,cover_updated_at=100 WHERE file_name='dirty.epub'");
        File sourcePath = source.databaseFile(new DbContext(RuntimeEnvironment.getApplication(), new File(root, "dirty-source")));
        source.close();
        File snapshot = new File(root, "dirty-snapshot.db");
        Files.copy(sourcePath.toPath(), snapshot.toPath());
        db.mergeSnapshot(snapshot, null);
        assertEquals("Local title", scalar(sql, "SELECT title FROM books WHERE file_name='dirty.epub'"));
        assertEquals("Local author", scalar(sql, "SELECT author FROM books WHERE file_name='dirty.epub'"));
    }
    @Test public void olderCleanSnapshotCannotOverwriteNewerMetadataExtras() throws Exception {
        fixture(sql, "newer.epub", HASH, 60);
        sql.execSQL("UPDATE books SET sync_dirty=0,title='New local title',author='New local author',updated_at=200 WHERE file_name='newer.epub'");
        ReaderStateDb source = isolated("older-source");
        SQLiteDatabase incoming = source.getWritableDatabase();
        fixture(incoming, "newer.epub", HASH, 10);
        incoming.execSQL("UPDATE books SET title='Old remote title',author='Old remote author',updated_at=100 WHERE file_name='newer.epub'");
        File sourcePath = source.databaseFile(new DbContext(RuntimeEnvironment.getApplication(), new File(root, "older-source")));
        source.close();
        File snapshot = new File(root, "older-snapshot.db");
        Files.copy(sourcePath.toPath(), snapshot.toPath());
        db.mergeSnapshot(snapshot, null);
        assertEquals("New local title", scalar(sql, "SELECT title FROM books WHERE file_name='newer.epub'"));
        assertEquals("New local author", scalar(sql, "SELECT author FROM books WHERE file_name='newer.epub'"));
        assertEquals(60, count(sql, "SELECT progress FROM books WHERE file_name='newer.epub'"));
    }
    @Test public void existingBooksOnlyDoesNotImportCloudOnlyRows() throws Exception {
        ReaderStateDb source = isolated("only-source");
        SQLiteDatabase incoming = source.getWritableDatabase();
        fixture(incoming, "cloud-only.epub", HASH, 44);
        File sourcePath = source.databaseFile(new DbContext(RuntimeEnvironment.getApplication(), new File(root, "only-source")));
        source.close();
        File snapshot = new File(root, "only-snapshot.db");
        Files.copy(sourcePath.toPath(), snapshot.toPath());
        db.mergeSnapshot(snapshot, null, true);
        assertEquals(0, count(sql, "SELECT COUNT(*) FROM books WHERE file_name='cloud-only.epub'"));
    }
    @Test public void checkpointWaitsForQueuedWriter() throws Exception {
        db.updateProgress("queued.epub", 0, 0);
        fixture(sql, "queued.epub", HASH, 1);
        db.updateProgress("queued.epub", 77, 0);
        db.checkpoint();
        assertEquals(77, count(sql, "SELECT progress FROM books WHERE file_name='queued.epub'"));
    }
    @Test public void frozenSnapshotIsReadableAndIndependentOfLaterWrites() throws Exception {
        fixture(sql, "snapshot.epub", HASH, 35);
        File snapshot = db.createSnapshot(new DbContext(RuntimeEnvironment.getApplication(), new File(root, "local")));
        try {
            sql.execSQL("UPDATE books SET progress=91 WHERE file_name='snapshot.epub'");
            db.checkpoint();
            try (SQLiteDatabase frozen = SQLiteDatabase.openDatabase(snapshot.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
                assertEquals("ok", scalar(frozen, "PRAGMA integrity_check"));
                assertEquals(35, count(frozen, "SELECT progress FROM books WHERE file_name='snapshot.epub'"));
            }
            assertEquals(91, count(sql, "SELECT progress FROM books WHERE file_name='snapshot.epub'"));
        } finally { snapshot.delete(); }
    }
    @Test public void missingBooksSnapshotIsRejectedBeforeLocalOrPreferenceMutation() throws Exception {
        fixture(sql, "guard.epub", HASH, 31);
        SharedPreferences prefs = RuntimeEnvironment.getApplication().getSharedPreferences("reader-guard-" + root.getName(), 0);
        prefs.edit().putInt("percent_guard.epub", 31).putString("sync_seen", "before").commit();
        File bad = new File(root, "missing-books.db");
        try (SQLiteDatabase incoming = SQLiteDatabase.openOrCreateDatabase(bad, null)) {
            incoming.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY,v TEXT NOT NULL)"); incoming.setVersion(6);
        }
        assertThrows(ReaderStateDb.InvalidSnapshotException.class, () -> db.mergeSnapshot(bad, prefs));
        assertEquals(31, count(sql, "SELECT progress FROM books WHERE file_name='guard.epub'"));
        assertEquals("before", prefs.getString("sync_seen", "")); assertEquals(31, prefs.getInt("percent_guard.epub", -1));
    }
    @Test public void invalidSnapshotValidationPreservesEveryOriginalByte() throws Exception {
        byte[][] samples = {new byte[0], "random bytes".getBytes(StandardCharsets.UTF_8), "SQLite format 3\000truncated".getBytes(StandardCharsets.UTF_8)};
        for (int i = 0; i < samples.length; i++) {
            File candidate = new File(root, "invalid-" + i + ".db"); Files.write(candidate.toPath(), samples[i]);
            assertThrows(ReaderStateDb.InvalidSnapshotException.class, () -> ReaderStateDb.validateSnapshot(candidate));
            assertArrayEquals(samples[i], Files.readAllBytes(candidate.toPath()));
        }
    }
    @Test public void legacySnapshotWithoutOptionalTablesStillMergesCoreBooks() throws Exception {
        ReaderStateDb source = isolated("optional-source"); SQLiteDatabase incoming = source.getWritableDatabase();
        fixture(incoming, "optional.epub", HASH, 47);
        for (String table : new String[]{"shelves", "shelf_books", "reading_day", "book_day", "book_tombstones"}) incoming.execSQL("DROP TABLE " + table);
        File sourcePath = source.databaseFile(new DbContext(RuntimeEnvironment.getApplication(), new File(root, "optional-source")));
        source.close(); File snapshot = new File(root, "optional-snapshot.db"); Files.copy(sourcePath.toPath(), snapshot.toPath());
        db.mergeSnapshot(snapshot, null);
        assertEquals(1, count(sql, "SELECT COUNT(*) FROM books WHERE content_hash=?", HASH));
        assertEquals(47, count(sql, "SELECT progress FROM books WHERE file_name='optional.epub'"));
    }
    @Test public void reopeningVersion6MissingBooksRepairsSchemaWithoutLosingHistoryOrMarkers() throws Exception {
        sql.execSQL("INSERT INTO reading_day(day,total_ms,daily_note) VALUES('2026-10-19',9000,'retain note')");
        sql.execSQL("INSERT INTO book_day(day,file_name,duration_ms,book_note) VALUES('2026-10-19','history.epub',9000,'retain history')");
        sql.execSQL("INSERT INTO meta(k,v) VALUES('legacy_migrated_v1','1'),('library_metadata_migrated_v2','1'),('home_indexed_v4','1')"); sql.execSQL("DROP TABLE books");
        db.close(); db = isolated("local"); sql = db.getWritableDatabase();
        assertEquals(1, count(sql, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='books'"));
        assertEquals(9000, db.dayTime("2026-10-19")); assertEquals("retain note", db.dailyNote("2026-10-19"));
        assertEquals("retain history", db.bookDayNote("2026-10-19", "history.epub"));
        assertEquals(0, count(sql, "SELECT COUNT(*) FROM meta WHERE k IN ('legacy_migrated_v1','library_metadata_migrated_v2','home_indexed_v4')"));
    }
    @Test public void partialMissingStatsRepairDoesNotOverwriteExistingStatsWhenLegacyPrefsReapplied() throws Exception {
        sql.execSQL("INSERT INTO reading_day(day,total_ms,daily_note) VALUES('2026-10-19',9000,'local note')"); sql.execSQL("DROP TABLE book_day");
        db.close(); db = isolated("local"); sql = db.getWritableDatabase();
        SharedPreferences prefs = RuntimeEnvironment.getApplication().getSharedPreferences("legacy-stats-" + root.getName(), 0);
        prefs.edit().putString("reading_stats_days_json", "{\"2026-10-19\":1000,\"2026-10-20\":2000}").putString("reading_stats_day_books_json", "{}").putString("reading_stats_day_notes_json", "{\"2026-10-19\":\"legacy note\"}").putString("reading_stats_book_day_notes_json", "{}").commit();
        Method migrate = ReaderStateDb.class.getDeclaredMethod("migrateLegacyAsync", SharedPreferences.class, File.class); migrate.setAccessible(true); migrate.invoke(db, prefs, root);
        await(() -> "1".equals(metaValue("legacy_migrated_v1")));
        assertEquals(9000, db.dayTime("2026-10-19")); assertEquals("local note", db.dailyNote("2026-10-19")); assertEquals(2000, db.dayTime("2026-10-20"));
    }
    @Test public void lateMergeFailureRollsBackBooksProgressAndPreferences() throws Exception {
        fixture(sql, "rollback.epub", HASH, 11); sql.execSQL("UPDATE books SET sync_dirty=0,updated_at=1 WHERE file_name='rollback.epub'");
        SharedPreferences prefs = RuntimeEnvironment.getApplication().getSharedPreferences("rollback-" + root.getName(), 0); prefs.edit().putInt("percent_rollback.epub", 11).putString("merge_marker", "before").commit();
        ReaderStateDb source = isolated("malformed-source"); SQLiteDatabase incoming = source.getWritableDatabase(); fixture(incoming, "rollback.epub", HASH, 88); incoming.execSQL("UPDATE books SET sync_dirty=0,updated_at=99 WHERE file_name='rollback.epub'");
        incoming.execSQL("INSERT INTO shelves(name,updated_at) VALUES('Remote shelf',99)");
        sql.execSQL("CREATE TRIGGER ignore_shelf BEFORE INSERT ON shelves BEGIN SELECT RAISE(IGNORE); END");
        File sourcePath = source.databaseFile(new DbContext(RuntimeEnvironment.getApplication(), new File(root, "malformed-source"))); source.close(); File snapshot = new File(root, "malformed-snapshot.db"); Files.copy(sourcePath.toPath(), snapshot.toPath());
        assertThrows(RuntimeException.class, () -> db.mergeSnapshot(snapshot, prefs));
        assertEquals(11, count(sql, "SELECT progress FROM books WHERE file_name='rollback.epub'")); assertEquals("before", prefs.getString("merge_marker", "")); assertEquals(11, prefs.getInt("percent_rollback.epub", -1));
    }
    @Test public void walSnapshotIncludesQueuedWritesAndRestoresWalMode() throws Exception {
        fixture(sql, "wal.epub", HASH, 12); db.updateProgress("wal.epub", 88, 0);
        File snapshot = db.createSnapshot(new DbContext(RuntimeEnvironment.getApplication(), new File(root, "local")));
        try (SQLiteDatabase frozen = SQLiteDatabase.openDatabase(snapshot.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) { assertEquals("ok", scalar(frozen, "PRAGMA integrity_check")); assertEquals(88, count(frozen, "SELECT progress FROM books WHERE file_name='wal.epub'")); }
        snapshot.delete(); assertTrue("WAL must be restored after snapshot", sql.isWriteAheadLoggingEnabled());
    }
    @Test public void additiveSchemaUpgradePreservesProgressAndResetsExpiredUploadSessions() throws Exception {
        fixture(sql, "upgrade.epub", HASH, 88);
        sql.setVersion(5);
        db.close();
        db = isolated("local");
        sql = db.getWritableDatabase();
        assertEquals(88, count(sql, "SELECT progress FROM books WHERE file_name='upgrade.epub'"));
        assertEquals(1, count(sql, "SELECT COUNT(*) FROM books WHERE upload_session_url='' AND cover_upload_session_url=''"));
    }
    @Test public void restoreBindsAtLeast220BooksWithoutDroppingRows() throws Exception {
        for (int i = 0; i < 220; i++) {
            File file = new File(root, "restore-" + i + ".epub");
            Files.write(file.toPath(), ("restore-content-" + i).getBytes(StandardCharsets.UTF_8));
            db.recordRestoredBook(file, FileIdentityUtil.sha256(file), "remote-" + i, null);
        }
        assertEquals(220, count(sql, "SELECT COUNT(*) FROM books WHERE file_path<>''"));
        assertEquals(220, count(sql, "SELECT COUNT(*) FROM books WHERE sync_dirty=0"));
    }
    @Test public void bindingDiagnosticClassifiesDatabaseFailureWithoutLeakingDetails() {
        String diagnostic = ReaderStateDb.bindingFailureDiagnostic(
                new IllegalStateException("database locked at /private/user/token=secret"));
        assertEquals("database busy", diagnostic);
        assertFalse(diagnostic.contains("private"));
        assertFalse(diagnostic.contains("secret"));
        assertEquals("database constraint", ReaderStateDb.bindingFailureDiagnostic(
                new IllegalStateException("UNIQUE constraint failed: books.content_hash")));
    }
    private ReaderStateDb isolated(String name) throws Exception {
        File dir = new File(root, name);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        Constructor<ReaderStateDb> c = ReaderStateDb.class.getDeclaredConstructor(Context.class);
        c.setAccessible(true);
        ReaderStateDb helper = c.newInstance(new DbContext(RuntimeEnvironment.getApplication(), dir));
        helpers.add(helper);
        return helper;
    }
    private File fixture(SQLiteDatabase target, String name, String hash, int progress) throws Exception {
        File file = new File(root, name);
        if (!file.exists()) assertTrue(file.createNewFile());
        target.execSQL("INSERT INTO books(file_name,content_hash,file_path,format,title,progress,sync_dirty,cover_sync_dirty,upload_session_url,cover_upload_session_url,remote_file_id,remote_cover_file_id) VALUES(?,?,?,'epub','Fixture',?,1,1,'book-session','cover-session','remote-book','remote-cover')", new Object[]{name, hash, file.getAbsolutePath(), progress});
        return file;
    }
    private static long count(SQLiteDatabase target, String query, String...args) {
        try (Cursor c = target.rawQuery(query, args)) { assertTrue(c.moveToFirst()); return c.getLong(0); }
    }
    private static String scalar(SQLiteDatabase target, String query, String...args) {
        try (Cursor c = target.rawQuery(query, args)) { assertTrue(c.moveToFirst()); return c.getString(0); }
    }
    private String metaValue(String key) {
        try { return scalar(sql, "SELECT v FROM meta WHERE k=?", key); } catch (AssertionError e) { return ""; }
    }
    private interface Condition { boolean ready() throws Exception; }
    private static void await(Condition c) throws Exception {
        long end = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < end) { if (c.ready()) return; Thread.sleep(10); }
        fail("Database writer timed out");
    }
    private static void delete(File file) {
        if (file == null) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
    private static final class DbContext extends ContextWrapper {
        private final File dir;
        DbContext(Context base, File dir) { super(base); this.dir=dir; }
        @Override public Context getApplicationContext() { return this; }
        @Override public File getDatabasePath(String name) { return new File(dir,name); }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) { return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory); }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler handler) { return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getAbsolutePath(),factory,handler); }
    }
}
