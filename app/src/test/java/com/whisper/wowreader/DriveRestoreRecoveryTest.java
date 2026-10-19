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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.Assert.*;

/** Exercises the production restore/sync boundary, not Google OAuth or a live Drive account. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 28, 35, 36}, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class DriveRestoreRecoveryTest {
    private File root;
    private ReaderStateDb local;
    private SharedPreferences prefs;
    private final List<ReaderStateDb> helpers = new ArrayList<>();

    @Before public void setup() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        root = new File(context.getCacheDir(), "restore-recovery-" + UUID.randomUUID());
        assertTrue(root.mkdirs());
        prefs = context.getSharedPreferences(root.getName(), 0);
        prefs.edit().putString("google_account_uid", "broken-account").putString("theme", "local").commit();
        local = isolated("local");
        local.getWritableDatabase().execSQL("INSERT INTO reading_day(day,total_ms,daily_note) VALUES('2026-10-19',9000,'Keep history')");
    }

    @After public void cleanup() {
        for (ReaderStateDb helper : helpers) try { helper.close(); } catch (Exception ignored) { }
        prefs.edit().clear().commit();
        delete(root);
    }

    @Test public void missingBooksRecoversIndependentPreferencesAndDoesNotEraseHistory() throws Exception {
        File broken = noBooks();
        assertFalse(GoogleDriveSync.restoreMetadata(local, broken, preferences(), prefs));
        assertEquals("cloud", prefs.getString("theme", ""));
        assertEquals("Recovered note", prefs.getString("book_notes_example.epub", ""));
        assertEquals("Keep history", local.dailyNote("2026-10-19"));
        assertEquals(9000, local.dayTime("2026-10-19"));
        assertTrue(prefs.getStringSet("google_restore_incomplete_accounts", java.util.Collections.emptySet()).contains("broken-account"));
        assertThrows(IllegalStateException.class, () -> GoogleDriveSync.requireCompleteRestore(prefs));
        // Neither the backup file nor the local library is reset by this recovery path.
        assertTrue(broken.isFile());
        assertEquals(0, count(local.getReadableDatabase(), "SELECT COUNT(*) FROM books"));
    }

    @Test public void missingOrTruncatedCurrentDatabaseCanStillReachBookRecovery() throws Exception {
        assertFalse(GoogleDriveSync.restoreMetadata(local, new File(root, "missing.db"), preferences(), prefs));
        File broken = new File(root, "truncated.db");
        byte[] bytes = "SQLite format 3\000broken".getBytes(StandardCharsets.UTF_8);
        Files.write(broken.toPath(), bytes);
        assertFalse(GoogleDriveSync.restoreMetadata(local, broken, preferences(), prefs));
        assertArrayEquals("Recovery must not delete or rewrite the original", bytes, Files.readAllBytes(broken.toPath()));
        assertEquals(9000, local.dayTime("2026-10-19"));
    }

    @Test public void automaticSyncRejectsBadDatabaseBeforeChangingPreferencesOrSeenState() throws Exception {
        prefs.edit().putString("google_last_seen_state_modified", "previous").commit();
        assertThrows(ReaderStateDb.InvalidSnapshotException.class, () ->
                GoogleDriveSync.mergeCloudMetadata(local, noBooks(), preferences(), prefs, true));
        assertEquals("local", prefs.getString("theme", ""));
        assertFalse(prefs.contains("book_notes_example.epub"));
        assertEquals("previous", prefs.getString("google_last_seen_state_modified", ""));
        assertEquals(9000, local.dayTime("2026-10-19"));
    }

    @Test public void protectionIsScopedToTheFailingGoogleAccount() {
        GoogleDriveSync.rememberIncompleteRestore(prefs, true);
        assertThrows(IllegalStateException.class, () -> GoogleDriveSync.requireCompleteRestore(prefs));
        prefs.edit().putString("google_account_uid", "other-account").commit();
        GoogleDriveSync.requireCompleteRestore(prefs);
        GoogleDriveSync.rememberIncompleteRestore(prefs, false);
        prefs.edit().putString("google_account_uid", "broken-account").commit();
        assertThrows(IllegalStateException.class, () -> GoogleDriveSync.requireCompleteRestore(prefs));
        GoogleDriveSync.rememberIncompleteRestore(prefs, false);
        GoogleDriveSync.requireCompleteRestore(prefs);
    }

    @Test public void partialRecoveryCannotClaimAllReadingDataWasRestored() {
        String message = GoogleDriveSync.restoreMessage(3, 1, true);
        assertTrue(message.contains("Recovered 3 cloud books"));
        assertTrue(message.contains("incomplete"));
        assertTrue(message.contains("may be missing"));
        assertTrue(message.contains("paused"));
        assertFalse(message.contains("reading data restored"));
        assertEquals("Books, notes and reading data restored", GoogleDriveSync.restoreMessage(0, 0, false));
    }

    @Test public void healthyMetadataAndPreferencesStillRestore() throws Exception {
        File snapshot = validSnapshot();
        assertTrue(GoogleDriveSync.restoreMetadata(local, snapshot, preferences(), prefs));
        assertEquals(62, count(local.getReadableDatabase(), "SELECT progress FROM books WHERE file_name='remote.epub'"));
        assertEquals("cloud", prefs.getString("theme", ""));
        assertEquals("Keep history", local.dailyNote("2026-10-19"));
    }

    @Test public void localMergeFailureIsNotMisclassifiedAsRecoverableCloudCorruption() throws Exception {
        File snapshot = validSnapshot();
        local.getWritableDatabase().execSQL("CREATE TRIGGER block_book BEFORE INSERT ON books BEGIN SELECT RAISE(ABORT, 'local write failed'); END");
        RuntimeException error = assertThrows(RuntimeException.class, () ->
                GoogleDriveSync.restoreMetadata(local, snapshot, preferences(), prefs));
        assertFalse(error instanceof ReaderStateDb.InvalidSnapshotException);
        assertEquals(0, count(local.getReadableDatabase(), "SELECT COUNT(*) FROM books"));
        assertEquals(9000, local.dayTime("2026-10-19"));
    }

    private File noBooks() {
        File file = new File(root, "no-books-" + UUID.randomUUID() + ".db");
        try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null)) {
            db.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY,v TEXT)");
            db.setVersion(6);
        }
        return file;
    }

    private File preferences() throws Exception {
        File file = new File(root, "state.json");
        Files.write(file.toPath(), ("{\"format\":1,\"prefs\":{\"theme\":{\"t\":\"s\",\"v\":\"cloud\"},"
                + "\"book_notes_example.epub\":{\"t\":\"s\",\"v\":\"Recovered note\"}}}").getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private File validSnapshot() throws Exception {
        ReaderStateDb source = isolated("source-" + UUID.randomUUID());
        source.getWritableDatabase().execSQL("INSERT INTO books(file_name,format,progress,sync_dirty,updated_at) VALUES('remote.epub','epub',62,0,100)");
        return source.createSnapshot(sourceContext);
    }

    private DbContext sourceContext;
    private ReaderStateDb isolated(String name) throws Exception {
        File dir = new File(root, name);
        assertTrue(dir.mkdirs());
        sourceContext = new DbContext(RuntimeEnvironment.getApplication(), dir);
        Constructor<ReaderStateDb> c = ReaderStateDb.class.getDeclaredConstructor(Context.class);
        c.setAccessible(true);
        ReaderStateDb helper = c.newInstance(sourceContext);
        helpers.add(helper);
        return helper;
    }

    private static long count(SQLiteDatabase db, String query) {
        try (Cursor c = db.rawQuery(query, null)) { assertTrue(c.moveToFirst()); return c.getLong(0); }
    }
    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
    private static class DbContext extends ContextWrapper {
        private final File dir;
        DbContext(Context base, File dir) { super(base); this.dir = dir; }
        @Override public Context getApplicationContext() { return this; }
        @Override public File getDatabasePath(String name) { return new File(dir, name); }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
        }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler handler) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(), factory, handler);
        }
    }
}
