package com.whisper.wowreader;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/** Regression coverage for the initialize/restore readiness barrier. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 28, 35, 36}, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ReaderInitializationTest {
    private File root;
    private final List<ReaderStateDb> helpers = new ArrayList<>();
    private Field instance;

    @Before public void setUp() throws Exception {
        root = new File(RuntimeEnvironment.getApplication().getCacheDir(), "reader-init-" + UUID.randomUUID());
        assertTrue(root.mkdirs());
        instance = ReaderStateDb.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @After public void tearDown() throws Exception {
        for (ReaderStateDb db : helpers) try { db.close(); } catch (Exception ignored) { }
        instance.set(null, null);
        delete(root);
    }

    @Test public void freshInitializeImmediatelyAcceptsRestoreAndKeepsPreferences() throws Exception {
        Context context = isolatedContext("fresh");
        SharedPreferences prefs = context.getSharedPreferences("restore-prefs", 0);
        prefs.edit().putBoolean("library_metadata_custom_book.epub", true).putString("library_title_book.epub", "Restored title").putInt("font_percent", 137).commit();
        ReaderStateDb db = ReaderStateDb.initialize(context, prefs, new File(root, "library"));
        helpers.add(db);
        File book = new File(root, "library/book.epub");
        assertTrue(book.getParentFile().mkdirs());
        assertTrue(book.createNewFile());
        String contentHash = FileIdentityUtil.sha256(book);
        db.recordRestoredBook(book, contentHash, "remote-1", prefs);
        assertEquals(contentHash, db.contentHash("book.epub"));
        assertEquals("Restored title", prefs.getString("library_title_book.epub", null));
        assertEquals(137, prefs.getInt("font_percent", 0));
    }

    @Test public void freshInitializeCanBind220RestoredBooks() throws Exception {
        Context context = isolatedContext("many");
        SharedPreferences prefs = context.getSharedPreferences("many-prefs", 0);
        File library = new File(root, "many-library");
        ReaderStateDb db = ReaderStateDb.initialize(context, prefs, library);
        helpers.add(db);
        db.awaitReady();
        assertTrue(library.mkdirs());
        for (int i = 0; i < 220; i++) {
            File book = new File(library, "book-" + i + ".epub");
            assertTrue(book.createNewFile());
            db.recordRestoredBook(book, hash(i + 10), "remote-" + i, prefs);
        }
        assertEquals(220, db.totalBookCount());
        assertEquals(hash(229), db.contentHash("book-219.epub"));
    }

    @Test public void schema6DatabaseSurvivesCloseReopenAndInitialize() throws Exception {
        Context context = isolatedContext("v64");
        SharedPreferences prefs = context.getSharedPreferences("v64-prefs", 0);
        File library = new File(root, "v64-library");
        assertTrue(library.mkdirs());
        ReaderStateDb first = newHelper("v64");
        SQLiteDatabase sql = first.getWritableDatabase();
        File book = new File(library, "legacy.epub");
        assertTrue(book.createNewFile());
        sql.execSQL("INSERT INTO books(file_name,content_hash,file_path,format,title,author,custom_cover_path,progress,remote_file_id,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                new Object[]{"legacy.epub", hash(64), book.getAbsolutePath(), "epub", "Legacy title", "Legacy author", "/covers/legacy.jpg", 73, "remote-64", 64L});
        sql.execSQL("INSERT INTO shelves(name,updated_at) VALUES('Favorites',64)");
        sql.execSQL("INSERT INTO shelf_books(shelf_name,file_name,added_at) VALUES('Favorites','legacy.epub',64)");
        sql.execSQL("INSERT INTO reading_day(day,total_ms,daily_note) VALUES('2026-10-19',1234,'kept note')");
        sql.execSQL("INSERT INTO book_day(day,file_name,duration_ms,book_note) VALUES('2026-10-19','legacy.epub',1234,'kept book note')");
        sql.execSQL("INSERT INTO meta(k,v) VALUES('legacy_migrated_v1','1')");
        sql.execSQL("INSERT INTO meta(k,v) VALUES('library_metadata_migrated_v2','1')");
        sql.execSQL("INSERT INTO meta(k,v) VALUES('home_indexed_v4','1')");
        prefs.edit().putString("legacy_setting", "retained").commit();
        first.close();
        ReaderStateDb reopened = newHelper("v64");
        instance.set(null, reopened);
        ReaderStateDb initialized = ReaderStateDb.initialize(context, prefs, library);
        initialized.awaitReady();
        SQLiteDatabase check = initialized.getReadableDatabase();
        assertEquals(6, check.getVersion());
        assertEquals("Legacy title", string(check, "SELECT title FROM books WHERE file_name='legacy.epub'"));
        assertEquals("Legacy author", string(check, "SELECT author FROM books WHERE file_name='legacy.epub'"));
        assertEquals(1, count(check, "SELECT COUNT(*) FROM books WHERE file_name='legacy.epub' AND progress=73 AND custom_cover_path='/covers/legacy.jpg'"));
        assertEquals(1, count(check, "SELECT COUNT(*) FROM shelves WHERE name='Favorites'"));
        assertEquals(1, count(check, "SELECT COUNT(*) FROM shelf_books WHERE shelf_name='Favorites' AND file_name='legacy.epub'"));
        assertEquals(1234, scalar(check, "SELECT total_ms FROM reading_day WHERE day='2026-10-19'"));
        assertEquals("kept book note", string(check, "SELECT book_note FROM book_day WHERE file_name='legacy.epub'"));
        assertEquals("retained", prefs.getString("legacy_setting", null));
    }

    @Test public void blockedWriterDeduplicatesInitialization() throws Exception {
        Context context = isolatedContext("blocked");
        SharedPreferences prefs = context.getSharedPreferences("blocked-prefs", 0);
        File library = new File(root, "blocked-library");
        ReaderStateDb db = newHelper("blocked");
        instance.set(null, db);
        Field writerField = ReaderStateDb.class.getDeclaredField("WRITER");
        writerField.setAccessible(true);
        ExecutorService writer = (ExecutorService) writerField.get(null);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> blocker = writer.submit(() -> { entered.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        ReaderStateDb first = ReaderStateDb.initialize(context, prefs, library);
        ReaderStateDb second = ReaderStateDb.initialize(context, prefs, library);
        Field init = ReaderStateDb.class.getDeclaredField("initialization");
        init.setAccessible(true);
        assertSame(init.get(first), init.get(second));
        release.countDown();
        blocker.get(5, TimeUnit.SECONDS);
        first.awaitReady();
        assertEquals(1, count(db.getReadableDatabase(), "SELECT COUNT(*) FROM meta WHERE k='library_reconciled_v72'"));
    }

    @Test public void failedPrerequisiteIsPropagatedAndDoesNotMarkReconciled() throws Exception {
        Context context = isolatedContext("failed");
        SharedPreferences prefs = context.getSharedPreferences("failed-prefs", 0);
        ReaderStateDb db = newHelper("failed");
        instance.set(null, db);
        // A malformed legacy preference causes migration to fail without deleting data.
        prefs.edit().putInt("reading_stats_days_json", 7).commit();
        ReaderStateDb.initialize(context, prefs, new File(root, "failed-library"));
        try {
            db.awaitReady();
            fail("a failed initialization prerequisite must not be reported ready");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("failed") || expected.getCause() != null);
        }
        assertEquals(0, count(db.getWritableDatabase(), "SELECT COUNT(*) FROM meta WHERE k='library_reconciled_v72'"));
        assertNotNull(prefs);
    }

    private ReaderStateDb newHelper(String name) throws Exception {
        Constructor<ReaderStateDb> c = ReaderStateDb.class.getDeclaredConstructor(Context.class);
        c.setAccessible(true);
        ReaderStateDb db = c.newInstance(isolatedContext(name));
        helpers.add(db);
        return db;
    }

    private Context isolatedContext(String name) {
        return new DbContext(RuntimeEnvironment.getApplication(), new File(root, name));
    }

    private static String hash(int n) { return String.format("%064x", n); }
    private static long scalar(SQLiteDatabase db, String sql) { try (android.database.Cursor c = db.rawQuery(sql, null)) { assertTrue(c.moveToFirst()); return c.getLong(0); } }
    private static String string(SQLiteDatabase db, String sql) { try (android.database.Cursor c = db.rawQuery(sql, null)) { assertTrue(c.moveToFirst()); return c.getString(0); } }
    private static long count(SQLiteDatabase db, String sql) { return scalar(db, sql); }
    private static void delete(File f) { if (f == null) return; File[] children = f.listFiles(); if (children != null) for (File c : children) delete(c); f.delete(); }

    private static final class DbContext extends ContextWrapper {
        private final File dir;
        DbContext(Context base, File dir) { super(base); this.dir = dir; dir.mkdirs(); }
        @Override public Context getApplicationContext() { return this; }
        @Override public File getDatabasePath(String name) { return new File(dir, name); }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) { return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory); }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler handler) { return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getAbsolutePath(), factory, handler); }
    }
}
