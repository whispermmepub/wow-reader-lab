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

import java.io.File;
import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35, 36}, manifest = Config.NONE)
public class TitleIdentityTest {
    private File root, library;
    private ReaderStateDb db;
    private SQLiteDatabase sql;
    private SharedPreferences prefs;
    private ReaderStateDb previousInstance;

    @Before public void setup() throws Exception {
        root = new File(RuntimeEnvironment.getApplication().getCacheDir(), "title-identity-" + UUID.randomUUID());
        assertTrue(root.mkdirs()); library = new File(root, "library"); assertTrue(library.mkdirs());
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences(root.getName(), 0);
        db = isolated(); sql = db.getWritableDatabase();
        java.lang.reflect.Field f = ReaderStateDb.class.getDeclaredField("INSTANCE");
        f.setAccessible(true); previousInstance = (ReaderStateDb) f.get(null); f.set(null, db);
    }

    @After public void tearDown() throws Exception {
        java.lang.reflect.Field f = ReaderStateDb.class.getDeclaredField("INSTANCE");
        f.setAccessible(true); f.set(null, previousInstance);
        try { db.close(); } catch (Exception ignored) {}
        prefs.edit().clear().commit(); delete(root);
    }

    @Test public void transientNumericAndHashTitlesAreNotMeaningful() {
        assertFalse(ReaderStateDb.meaningfulDisplayTitle("121cd11ee866cddfc11b7a2ad3067216321c2dc79a8311fd8ff7243eadb2bf52"));
        assertFalse(ReaderStateDb.meaningfulDisplayTitle("42"));
        assertFalse(ReaderStateDb.meaningfulDisplayTitle("f7151f02fb78f4aa08d0c555c93ff78655f7317aeb75f4e65fc0b414bdd5592d"));
        assertTrue(ReaderStateDb.meaningfulDisplayTitle("The preserved title"));
    }

    @Test public void staleHashPreferenceFallsBackToStableFilenameIdentity() {
        SharedPreferences p = RuntimeEnvironment.getApplication().getSharedPreferences("title-stale", 0);
        String file = "121cd11ee866cddfc11b7a2ad3067216321c2dc79a8311fd8ff7243eadb2bf52.epub";
        p.edit().putString("library_title_" + file, "121cd11ee866cddfc11b7a2ad3067216321c2dc79a8311fd8ff7243eadb2bf52").commit();
        assertEquals("Untitled book", ReaderStateDb.displayTitle(p, file));
    }

    @Test public void explicitCustomRenameWinsEvenWhenOldMetadataWasTransient() {
        SharedPreferences p = RuntimeEnvironment.getApplication().getSharedPreferences("title-custom", 0);
        String file = "old-alias.epub";
        p.edit().putString("library_title_" + file, "My renamed book")
                .putBoolean("library_metadata_custom_" + file, true).commit();
        assertEquals("My renamed book", ReaderStateDb.displayTitle(p, file));
    }

    @Test public void indexedMetadataWinsOverStaleHashPreferenceAndAuthorAlias() throws Exception {
        File f = file("canonical.epub");
        sql.execSQL("INSERT INTO books(file_name,file_path,title,author) VALUES(?,?,?,?)",
                new Object[]{f.getName(), f.getAbsolutePath(), "Canonical title", "Canonical author"});
        prefs.edit().putString("library_title_canonical.epub", "deadbeefdeadbeefdeadbeefdeadbeef")
                .putString("library_author_canonical.epub", "Stale author").commit();
        assertEquals("Canonical title", ReaderStateDb.displayTitle(prefs, f.getName()));
        assertEquals("Canonical author", ReaderStateDb.displayAuthor(prefs, f.getName()));
    }

    @Test public void importedRefreshPreservesEstablishedTitleAuthorAndNumericRename() throws Exception {
        File f = file("refresh.epub");
        sql.execSQL("INSERT INTO books(file_name,file_path,title,author) VALUES(?,?,?,?)",
                new Object[]{f.getName(), f.getAbsolutePath(), "Established title", "Established author"});
        db.upsertImportedBook(f, "new-hash", "1234567890", "", 1L);
        assertEquals("Established title", scalar("SELECT title FROM books WHERE file_name='refresh.epub'"));
        assertEquals("Established author", scalar("SELECT author FROM books WHERE file_name='refresh.epub'"));

        File numeric = file("numeric.epub");
        db.upsertImportedBook(numeric, "numeric-hash", "1984", "Author", 1L);
        db.upsertImportedBook(numeric, "numeric-hash-2", "42", "", 2L);
        assertEquals("1984", scalar("SELECT title FROM books WHERE file_name='numeric.epub'"));
        assertEquals("Author", scalar("SELECT author FROM books WHERE file_name='numeric.epub'"));
        assertEquals("1984", ReaderStateDb.displayTitle(prefs, numeric.getName()));
    }

    @Test public void libraryRefreshCannotReplaceCanonicalMetadataWithTransportHash() throws Exception {
        File f = file("refresh-title.epub");
        db.upsertImportedBook(f, "hash", "1984", "George Orwell", 1L);
        prefs.edit().putString("library_title_" + f.getName(), "deadbeefdeadbeefdeadbeefdeadbeef")
                .putString("library_author_" + f.getName(), "").commit();
        java.lang.reflect.Method update = ReaderStateDb.class.getDeclaredMethod("insertOrUpdateBook", SQLiteDatabase.class, File.class, String.class, SharedPreferences.class);
        update.setAccessible(true); update.invoke(db, sql, f, "hash", prefs);
        assertEquals("1984", ReaderStateDb.displayTitle(prefs, f.getName()));
        assertEquals("George Orwell", ReaderStateDb.displayAuthor(prefs, f.getName()));
    }

    @Test public void aliasRebindPreservesMetadataAndDeletionHidesHistory() throws Exception {
        File alias = file("old-alias.epub");
        sql.execSQL("INSERT INTO books(file_name,file_path,content_hash,title,author) VALUES(?,?,?,?,?)",
                new Object[]{alias.getName(), alias.getAbsolutePath(), "alias-hash", "Alias title", "Alias author"});
        db.recordReading("2026-01-01", alias.getName(), 77L);
        File canonical = file("alias-hash.epub");
        db.recordRestoredBook(canonical, "alias-hash", "remote", prefs);
        assertEquals("Alias title", scalar("SELECT title FROM books WHERE file_name='alias-hash.epub'"));
        assertEquals("Alias author", scalar("SELECT author FROM books WHERE file_name='alias-hash.epub'"));
        assertEquals(1, db.booksForDay("2026-01-01").size());
        assertTrue(db.removeBookNow(canonical.getName()));
        assertEquals(0, db.booksForDay("2026-01-01").size());
    }

    private File file(String name) throws Exception { File f = new File(library, name); assertTrue(f.createNewFile()); return f; }
    private ReaderStateDb isolated() throws Exception {
        java.lang.reflect.Constructor<ReaderStateDb> c = ReaderStateDb.class.getDeclaredConstructor(Context.class);
        c.setAccessible(true); return c.newInstance(new DbContext(RuntimeEnvironment.getApplication(), new File(root, "db")));
    }
    private String scalar(String q) { try (Cursor c = sql.rawQuery(q, null)) { assertTrue(c.moveToFirst()); return c.getString(0); } }
    private static void delete(File f) { if (f == null) return; File[] cs = f.listFiles(); if (cs != null) for (File c : cs) delete(c); f.delete(); }
    private static final class DbContext extends ContextWrapper {
        final File dir; DbContext(Context base, File d) { super(base); dir = d; dir.mkdirs(); }
        @Override public Context getApplicationContext() { return this; }
        @Override public File getDatabasePath(String n) { return new File(dir, n); }
        @Override public SQLiteDatabase openOrCreateDatabase(String n, int m, SQLiteDatabase.CursorFactory f) { return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(n), f); }
        @Override public SQLiteDatabase openOrCreateDatabase(String n, int m, SQLiteDatabase.CursorFactory f, DatabaseErrorHandler h) { return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(n).getAbsolutePath(), f, h); }
    }
}
