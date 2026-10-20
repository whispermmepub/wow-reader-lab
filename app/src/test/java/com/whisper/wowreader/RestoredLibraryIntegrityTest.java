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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.Assert.*;

/** Focused v72 regressions for local-library binding and restored-state integrity. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 28, 35, 36}, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class RestoredLibraryIntegrityTest {
    private File root, library;
    private ReaderStateDb db;
    private SQLiteDatabase sql;
    private SharedPreferences prefs;

    @Before public void setup() throws Exception {
        root = new File(RuntimeEnvironment.getApplication().getCacheDir(), "v72-integrity-" + UUID.randomUUID());
        assertTrue(root.mkdirs());
        library = new File(root, "library");
        assertTrue(library.mkdirs());
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences(root.getName(), 0);
        db = isolated("local");
        sql = db.getWritableDatabase();
    }

    @After public void tearDown() {
        try { db.checkpoint(); } catch (Exception ignored) { }
        try { db.close(); } catch (Exception ignored) { }
        prefs.edit().clear().commit();
        delete(root);
    }

    @Test public void reconciliationRetainsHealthyFileClearsStaleAndRejectsExternalPath() throws Exception {
        File healthy = file("healthy.epub");
        File outside = new File(root, "outside.epub"); assertTrue(outside.createNewFile());
        sql.execSQL("INSERT INTO books(file_name,content_hash,file_path,title) VALUES('healthy.epub','h','" + healthy.getAbsolutePath() + "','Healthy')");
        sql.execSQL("INSERT INTO books(file_name,content_hash,file_path,title) VALUES('stale.epub','s','/missing/stale.epub','Stale')");
        sql.execSQL("INSERT INTO books(file_name,content_hash,file_path,title) VALUES('external.epub','e',?, 'External')", new Object[]{outside.getAbsolutePath()});
        db.reconcileLocalLibrary(library, prefs);
        assertEquals(healthy.getCanonicalPath(), scalar("SELECT file_path FROM books WHERE file_name='healthy.epub'"));
        assertEquals("", scalar("SELECT file_path FROM books WHERE file_name='stale.epub'"));
        assertEquals("", scalar("SELECT file_path FROM books WHERE file_name='external.epub'"));
        assertTrue(healthy.isFile());
        assertTrue(outside.isFile());
        assertEquals(0, count("SELECT COUNT(*) FROM book_tombstones"));
    }

    @Test public void remoteOnlyGhostIsAbsentFromEveryLocalVisibilitySurface() throws Exception {
        File local = file("local.epub");
        sql.execSQL("INSERT INTO books(file_name,content_hash,file_path,title,author,annotation_count,progress) VALUES('local.epub','local-h',?,?,?,2,40)", new Object[]{local.getAbsolutePath(), "Local title", "Author"});
        sql.execSQL("INSERT INTO books(file_name,content_hash,file_path,title,author,annotation_count,progress) VALUES('ghost.epub','ghost-h','', 'Ghost title','Ghost author',9,40)");
        db.setShelfMembershipRow("Shelf", "ghost.epub", true);
        db.reconcileLocalLibrary(library, prefs);
        assertEquals(1, db.totalBookCount());
        assertEquals(1, db.countLibraryBooks(new LibraryQuerySpec("", "", "all", "", "added")));
        assertEquals(0, db.countLibraryBooks(new LibraryQuerySpec("Ghost", "", "all", "", "added")));
        assertEquals(1, db.pageLibraryBooks(new LibraryQuerySpec("", "", "all", "", "added"), 0, 20).size());
        assertEquals(0, db.pageLibraryBooks(new LibraryQuerySpec("Ghost", "", "all", "", "added"), 0, 20).size());
        assertEquals(1, db.recentBooksPage(20, 0, false).size());
        assertEquals(1, db.annotatedBooks(20).size());
        assertEquals(2, db.totalAnnotationCount());
        assertEquals(1, db.totalAuthorCount());
        assertEquals(0, db.shelfBookCount("Shelf"));
    }

    @Test public void metadataOnlyGhostWithNoHashCanBeRemovedWithoutFabricatedTombstone() {
        sql.execSQL("INSERT INTO books(file_name,content_hash,file_path,title) VALUES('ghost.epub','','','Ghost')");
        assertTrue(db.removeBookNow("ghost.epub"));
        assertEquals(0, count("SELECT COUNT(*) FROM books WHERE file_name='ghost.epub'"));
        assertEquals(0, count("SELECT COUNT(*) FROM book_tombstones"));
    }

    @Test public void existingRemoteTombstoneDoesNotHideHealthyLocalFile() throws Exception {
        File local = file("kept.epub");
        sql.execSQL("INSERT INTO books(file_name,content_hash,file_path,title,progress) VALUES('kept.epub','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',?,?,73)", new Object[]{local.getAbsolutePath(), "Kept",});
        db.rememberRemoteDeletion("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertTrue(local.isFile());
        assertEquals(1, db.totalBookCount());
        assertEquals(73, scalarInt("SELECT progress FROM books WHERE file_name='kept.epub'"));
        assertEquals(1, db.recentBooksPage(20, 0, false).size());
    }

    @Test public void ignoredTombstoneTriggerPreventsRemovalAndPreservesShelvesAndHistory() throws Exception {
        File local = file("protected.epub");
        sql.execSQL("INSERT INTO books(file_name,content_hash,file_path) VALUES('protected.epub','protected-h',?)", new Object[]{local.getAbsolutePath()});
        db.setShelfMembershipRow("Keep", "protected.epub", true);
        sql.execSQL("INSERT INTO book_day(day,file_name,duration_ms,book_note) VALUES('2026-01-01','protected.epub',9,'history')");
        sql.execSQL("CREATE TRIGGER ignore_tombstone BEFORE INSERT ON book_tombstones BEGIN SELECT RAISE(IGNORE); END");
        boolean failed = false;
        try { db.removeBookNow("protected.epub"); } catch (IllegalStateException expected) { failed = true; }
        assertTrue(failed);
        assertEquals(1, count("SELECT COUNT(*) FROM books WHERE file_name='protected.epub'"));
        assertEquals(1, db.shelfBookCount("Keep"));
        assertEquals(1, count("SELECT COUNT(*) FROM book_day WHERE file_name='protected.epub'"));
        assertEquals(0, count("SELECT COUNT(*) FROM book_tombstones"));
    }

    @Test public void repairMetadataIsCompareAndSetAndNeverOverwritesUserRenameOrAuthor() throws Exception {
        File local = file("cas.epub");
        sql.execSQL("INSERT INTO books(file_name,content_hash,file_path,title,author) VALUES('cas.epub','cas-h',?,?,?)", new Object[]{local.getAbsolutePath(), "Fallback", ""});
        assertTrue(db.repairRestoredMetadata("cas.epub", "Fallback", "Embedded", "Embedded author"));
        assertEquals("Embedded", scalar("SELECT title FROM books WHERE file_name='cas.epub'"));
        assertEquals("Embedded author", scalar("SELECT author FROM books WHERE file_name='cas.epub'"));
        assertFalse(db.repairRestoredMetadata("cas.epub", "Fallback", "Wrong", "Other"));
        sql.execSQL("UPDATE books SET title='User title',author='User author' WHERE file_name='cas.epub'");
        assertFalse(db.repairRestoredMetadata("cas.epub", "Embedded", "Remote title", "Remote author"));
        assertTrue(db.repairRestoredMetadata("cas.epub", "User title", "User title", "Remote author"));
        assertEquals("User title", scalar("SELECT title FROM books WHERE file_name='cas.epub'"));
        assertEquals("User author", scalar("SELECT author FROM books WHERE file_name='cas.epub'"));
    }

    @Test public void canonicalRestoreRebindMigratesAnnotationsCustomFlagProgressAndShelves() throws Exception {
        File old = file("alias.epub");
        sql.execSQL("INSERT INTO books(file_name,content_hash,file_path,title,progress) VALUES('alias.epub','canon-h',?,?,66)", new Object[]{old.getAbsolutePath(), "Alias"});
        db.setShelfMembershipRow("Shelf", "alias.epub", true);
        String annotation = "[{\"id\":\"a\",\"chapter\":1,\"start\":1,\"end\":3,\"quote\":\"q\"}]";
        prefs.edit().putString("annotations_" + Integer.toHexString("alias.epub".hashCode()), annotation)
                .putBoolean("library_metadata_custom_alias.epub", true)
                .putString("library_title_alias.epub", "User title").apply();
        File actual = file("canon-h.epub");
        assertTrue(old.renameTo(actual));
        db.recordRestoredBook(actual, "canon-h", "remote", prefs);
        assertEquals(actual.getCanonicalPath(), scalar("SELECT file_path FROM books WHERE file_name='canon-h.epub'"));
        assertEquals(66, scalarInt("SELECT progress FROM books WHERE file_name='canon-h.epub'"));
        assertEquals(1, db.shelfBookCount("Shelf"));
        assertEquals(annotation, prefs.getString("annotations_" + Integer.toHexString("canon-h.epub".hashCode()), ""));
        assertTrue(prefs.getBoolean("library_metadata_custom_canon-h.epub", false));
        assertEquals("User title", prefs.getString("library_title_canon-h.epub", ""));
    }

    @Test public void legacySnapshotSameHashDifferentNameCannotReplaceLiveBindingTitleOrNotes() throws Exception {
        File live = file("live.epub");
        sql.execSQL("INSERT INTO books(file_name,content_hash,file_path,title,progress) VALUES('live.epub','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',?,?,55)", new Object[]{live.getAbsolutePath(), "Live title"});
        sql.execSQL("INSERT INTO book_day(day,file_name,book_note) VALUES('2026-01-01','live.epub','live note')");
        ReaderStateDb source = isolated("legacy-source");
        SQLiteDatabase incoming = source.getWritableDatabase();
        incoming.execSQL("INSERT INTO books(file_name,content_hash,file_path,title,progress) VALUES('legacy.epub','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa','/remote/legacy.epub','Legacy title',99)");
        incoming.execSQL("INSERT INTO book_day(day,file_name,book_note) VALUES('2026-01-01','legacy.epub','remote note')");
        File snapshot = new File(root, "legacy.db");
        Files.copy(source.databaseFile(new DbContext(RuntimeEnvironment.getApplication(), new File(root, "legacy-source"))).toPath(), snapshot.toPath());
        source.close();
        db.mergeSnapshot(snapshot, null);
        assertEquals(1, count("SELECT COUNT(*) FROM books WHERE file_name='live.epub'"));
        assertEquals(0, count("SELECT COUNT(*) FROM books WHERE file_name='legacy.epub'"));
        assertEquals(live.getCanonicalPath(), scalar("SELECT file_path FROM books WHERE file_name='live.epub'"));
        assertEquals("Live title", scalar("SELECT title FROM books WHERE file_name='live.epub'"));
        assertEquals("live note", scalar("SELECT book_note FROM book_day WHERE file_name='live.epub'"));
    }

    @Test public void restoredAliasRenamePreservesHistoryWhenTargetNameWasAbsent() throws Exception {
        File alias = file("original.epub");
        sql.execSQL("INSERT INTO books(file_name,content_hash,file_path,title,progress) VALUES('original.epub','rename-h',?,?,44)", new Object[]{alias.getAbsolutePath(), "Saved"});
        sql.execSQL("INSERT INTO book_day(day,file_name,duration_ms,book_note) VALUES('2026-02-02','original.epub',7,'keep')");
        db.setShelfMembershipRow("Keep", "original.epub", true);
        File actual = file("rename-h.epub");
        assertTrue(alias.renameTo(actual));
        db.recordRestoredBook(actual, "rename-h", "remote", prefs);
        assertEquals(0, count("SELECT COUNT(*) FROM books WHERE file_name='original.epub'"));
        assertEquals(1, count("SELECT COUNT(*) FROM books WHERE file_name='rename-h.epub'"));
        assertEquals("keep", scalar("SELECT book_note FROM book_day WHERE file_name='rename-h.epub'"));
        assertEquals(1, db.shelfBookCount("Keep"));
        assertEquals(44, scalarInt("SELECT progress FROM books WHERE file_name='rename-h.epub'"));
    }

    @Test public void largeAliasHistoryMergeKeepsMaximumDurationAndTargetNotes() throws Exception {
        File actual = file("history-target.epub");
        sql.execSQL("INSERT INTO books(file_name,content_hash,title) VALUES('history-alias.epub','history-h','Saved title')");
        sql.beginTransaction();
        try {
            for (int i = 0; i < 1200; i++) {
                String day = String.format(java.util.Locale.ROOT, "%04d", i);
                sql.execSQL("INSERT INTO book_day(day,file_name,duration_ms,book_note) VALUES(?,'history-alias.epub',?,?)",
                        new Object[]{day, i + 10L, "source-" + i});
                if (i % 2 == 0) sql.execSQL("INSERT INTO book_day(day,file_name,duration_ms,book_note) VALUES(?,'history-target.epub',?,?)",
                        new Object[]{day, 2000L - i, i % 4 == 0 ? "target-" + i : ""});
            }
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
        db.recordRestoredBook(actual, "history-h", "remote", prefs);
        assertEquals(0, count("SELECT COUNT(*) FROM book_day WHERE file_name='history-alias.epub'"));
        assertEquals(1200, count("SELECT COUNT(*) FROM book_day WHERE file_name='history-target.epub'"));
        try (Cursor history = sql.rawQuery("SELECT day,duration_ms,book_note FROM book_day WHERE file_name='history-target.epub'", null)) {
            while (history.moveToNext()) {
                int i = Integer.parseInt(history.getString(0));
                assertEquals(i % 2 == 0 ? Math.max(i + 10L, 2000L - i) : i + 10L, history.getLong(1));
                assertEquals(i % 4 == 0 ? "target-" + i : "source-" + i, history.getString(2));
            }
        }
    }

    @Test public void restoredBindingCannotResurrectTombstonedIdentity() throws Exception {
        File actual = file("deleted.epub");
        String hash = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        db.rememberRemoteDeletion(hash);
        db.recordRestoredBook(actual, hash, "remote", prefs);
        assertEquals(0, count("SELECT COUNT(*) FROM books WHERE file_name='deleted.epub'"));
        assertTrue(actual.isFile());
    }

    @Test public void finalRebindSkipsTransferredBooksButStillBindsPreexistingFiles() throws Exception {
        File transferred = file("transferred.epub"), preexisting = file("preexisting.epub");
        sql.execSQL("INSERT INTO books(file_name,content_hash,file_path,title) VALUES('transferred.epub','transferred-h',?,'Transferred title')",
                new Object[]{transferred.getCanonicalPath()});
        sql.execSQL("INSERT INTO books(file_name,content_hash,file_path,title) VALUES('preexisting.epub','preexisting-h','/old/device/preexisting.epub','Existing title')");
        sql.execSQL("CREATE TABLE restore_bind_updates(file_name TEXT)");
        sql.execSQL("CREATE TRIGGER count_restore_bind AFTER UPDATE OF file_path ON books BEGIN INSERT INTO restore_bind_updates(file_name) VALUES(NEW.file_name); END");
        GoogleDriveSync.rebindUnprocessedBooks(db, library, prefs, java.util.Collections.singleton(transferred.getName()));
        assertEquals(0, count("SELECT COUNT(*) FROM restore_bind_updates WHERE file_name='transferred.epub'"));
        assertTrue(count("SELECT COUNT(*) FROM restore_bind_updates WHERE file_name='preexisting.epub'") > 0);
        assertEquals(preexisting.getCanonicalPath(), scalar("SELECT file_path FROM books WHERE file_name='preexisting.epub'"));
        assertEquals("Existing title", scalar("SELECT title FROM books WHERE file_name='preexisting.epub'"));
    }

    private File file(String name) throws Exception { File f = new File(library, name); assertTrue(f.createNewFile()); return f; }
    private ReaderStateDb isolated(String name) throws Exception { java.lang.reflect.Constructor<ReaderStateDb> c = ReaderStateDb.class.getDeclaredConstructor(Context.class); c.setAccessible(true); return c.newInstance(new DbContext(RuntimeEnvironment.getApplication(), new File(root, name))); }
    private long count(String q) { try (Cursor c = sql.rawQuery(q, null)) { assertTrue(c.moveToFirst()); return c.getLong(0); } }
    private int scalarInt(String q) { return (int) scalarLong(q); }
    private long scalarLong(String q) { try (Cursor c = sql.rawQuery(q, null)) { assertTrue(c.moveToFirst()); return c.getLong(0); } }
    private String scalar(String q) { try (Cursor c = sql.rawQuery(q, null)) { assertTrue(c.moveToFirst()); return c.getString(0); } }
    private static void delete(File f) { if (f == null) return; File[] cs = f.listFiles(); if (cs != null) for (File c : cs) delete(c); f.delete(); }

    private static final class DbContext extends ContextWrapper {
        final File dir;
        DbContext(Context base, File d) { super(base); dir = d; dir.mkdirs(); }
        @Override public Context getApplicationContext() { return this; }
        @Override public File getDatabasePath(String n) { return new File(dir, n); }
        @Override public SQLiteDatabase openOrCreateDatabase(String n, int m, SQLiteDatabase.CursorFactory f) { return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(n), f); }
        @Override public SQLiteDatabase openOrCreateDatabase(String n, int m, SQLiteDatabase.CursorFactory f, DatabaseErrorHandler h) { return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(n).getAbsolutePath(), f, h); }
    }
}
