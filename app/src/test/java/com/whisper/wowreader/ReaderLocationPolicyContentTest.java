package com.whisper.wowreader;

import static org.junit.Assert.*;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class ReaderLocationPolicyContentTest {
    @Test public void contentHashIsStableAndChangesWithBytes() throws Exception {
        File a = File.createTempFile("reader-a", ".epub");
        File b = File.createTempFile("reader-b", ".epub");
        try {
            write(a, "same bytes"); write(b, "same bytes");
            assertEquals(ReaderLocationPolicy.contentSha256(a), ReaderLocationPolicy.contentSha256(b));
            write(b, "other byte");
            assertEquals(a.length(), b.length());
            assertNotEquals(ReaderLocationPolicy.contentSha256(a), ReaderLocationPolicy.contentSha256(b));
        } finally { a.delete(); b.delete(); }
    }
    @Test public void contentKeysDoNotUseLegacyNameSizeIdentity() {
        assertNotEquals(ReaderLocationPolicy.key("epub_location_", new File("/x/book.epub")),
                ReaderLocationPolicy.keyForId("epub_location_", "0123456789abcdef"));
    }
    @Test public void newerDatabaseRestoreLocationIsNotHiddenByContentMigration() {
        assertTrue(ReaderLocationPolicy.preferLegacyLocation(false, 0, 0));
        assertTrue(ReaderLocationPolicy.preferLegacyLocation(true, 100, 200));
        assertFalse(ReaderLocationPolicy.preferLegacyLocation(true, 200, 100));
        assertFalse(ReaderLocationPolicy.preferLegacyLocation(true, 200, 200));
    }
    private static void write(File f, String s) throws Exception {
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(s.getBytes(StandardCharsets.UTF_8)); }
    }
}
