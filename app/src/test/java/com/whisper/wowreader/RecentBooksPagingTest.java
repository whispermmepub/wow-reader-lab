package com.whisper.wowreader;

import android.content.Context;
import android.content.ContextWrapper;
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
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35, 36}, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class RecentBooksPagingTest {
    private File root;
    private ReaderStateDb db;
    private SQLiteDatabase sql;

    @Before public void setup() throws Exception {
        root = new File(RuntimeEnvironment.getApplication().getCacheDir(), "recent-page-" + UUID.randomUUID());
        assertTrue(root.mkdirs());
        Constructor<ReaderStateDb> c = ReaderStateDb.class.getDeclaredConstructor(Context.class);
        c.setAccessible(true);
        db = c.newInstance(new DbContext(RuntimeEnvironment.getApplication(), new File(root, "db")));
        sql = db.getWritableDatabase();
    }

    @After public void cleanup() {
        try { db.close(); } catch (Exception ignored) {}
        delete(root);
    }

    @Test public void homePagesEligibleRowsBeforeCheckingFilePaths() {
        for (int i = 0; i < 20; i++) insert("missing-" + i + ".epub", 20 - i, 1000L + i);
        File older = new File(root, "older-valid.epub");
        try { assertTrue(older.createNewFile()); } catch (java.io.IOException e) { fail(e.getMessage()); }
        insert(older.getName(), 42, 0L);
        insert("finished.epub", 100, 2000L);

        assertEquals(20, db.recentBooksPage(20, 0, false).size());
        assertEquals(1, db.recentBooksPage(20, 20, false).size());
        ReaderStateDb.LibraryBookRow row = db.recentBooksPage(20, 20, false).get(0);
        assertEquals(older.getName(), row.fileName);
        assertEquals(42, row.progress);
        assertTrue("Finished books must not consume home pages", db.recentBooksPage(20, 0, false).stream().noneMatch(r -> r.progress >= 100));
    }

    @Test public void readingOnlyStillExcludesJustOpenedZeroProgressBooks() {
        insert("zero.epub", 0, 2L);
        insert("reading.epub", 1, 1L);
        assertEquals(1, db.recentBooksPage(20, 0, true).size());
        assertEquals("reading.epub", db.recentBooksPage(20, 0, true).get(0).fileName);
    }

    private void insert(String name, int progress, long opened) {
        sql.execSQL("INSERT INTO books(file_name,file_path,format,title,progress,added_at,last_opened_at) VALUES(?,?, 'epub', ?,?,?,?)",
                new Object[]{name, new File(root, name).getAbsolutePath(), name, progress, opened, opened});
    }

    private static final class DbContext extends ContextWrapper {
        private final File dir;
        DbContext(Context base, File dir) { super(base); this.dir = dir; dir.mkdirs(); }
        @Override public File getDatabasePath(String name) { return new File(dir, name); }
    }
    private static void delete(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) { File[] children = f.listFiles(); if (children != null) for (File c : children) delete(c); }
        f.delete();
    }
}
