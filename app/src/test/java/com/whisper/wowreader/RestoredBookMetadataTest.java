package com.whisper.wowreader;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 28, 35, 36})
public class RestoredBookMetadataTest {
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void transportPolicyDoesNotMistakeNumericOrExplicitCustomTitles() {
        assertTrue(RestoredBookMetadata.needsRepair(HASH, HASH + ".epub", false));
        assertTrue(RestoredBookMetadata.needsRepair("wow_book_" + HASH + "-cloud.pdf", "x.pdf", false));
        assertTrue(RestoredBookMetadata.needsRepair("", "wow_book_" + HASH + ".epub", false));
        assertFalse(RestoredBookMetadata.needsRepair("1984", HASH + ".epub", false));
        assertFalse(RestoredBookMetadata.needsRepair(HASH, HASH + ".epub", true));
        assertFalse(RestoredBookMetadata.needsRepair("My book", "wow_book_" + HASH + ".epub", false));
    }

    @Test public void minimalEpubRepairsFallbackWithBurmeseMetadata() throws Exception {
        File epub = minimalEpub("မြန်မာစာအုပ်", "စာရေးသူ");
        RestoredBookMetadata.Result result = RestoredBookMetadata.read(
                epub, temporary.newFolder("covers"), HASH, "", false);
        assertEquals("မြန်မာစာအုပ်", result.title);
        assertEquals("စာရေးသူ", result.author);
        assertTrue(result.changed);
    }

    @Test public void userRenameAndHealthyMetadataArePreserved() throws Exception {
        File epub = minimalEpub("Embedded title", "Embedded author");
        RestoredBookMetadata.Result renamed = RestoredBookMetadata.read(
                epub, temporary.newFolder("covers2"), "My chosen title", "My author", true);
        assertEquals("My chosen title", renamed.title);
        assertEquals("My author", renamed.author);
        assertFalse(renamed.changed);

        RestoredBookMetadata.Result healthy = RestoredBookMetadata.read(
                epub, temporary.newFolder("covers3"), "Already healthy", "Saved author", false);
        assertEquals("Already healthy", healthy.title);
        assertEquals("Saved author", healthy.author);
        assertFalse(healthy.changed);
    }

    @Test public void corruptedFileLeavesValuesAndFileUntouched() throws Exception {
        File bad = temporary.newFile("" + HASH + ".epub");
        try (FileOutputStream out = new FileOutputStream(bad)) {
            out.write("not an epub".getBytes(StandardCharsets.UTF_8));
        }
        long length = bad.length();
        RestoredBookMetadata.Result result = RestoredBookMetadata.read(
                bad, temporary.newFolder("covers4"), HASH, "Saved author", false);
        assertEquals(HASH, result.title);
        assertEquals("Saved author", result.author);
        assertFalse(result.changed);
        assertTrue(bad.isFile());
        assertEquals(length, bad.length());
    }

    @Test public void validEpubWithoutMetadataGetsFriendlyTitle() throws Exception {
        File epub = minimalEpub("", "");
        RestoredBookMetadata.Result result = RestoredBookMetadata.read(
                epub, temporary.newFolder("covers5"), HASH, "", false);
        assertEquals("Untitled book", result.title);
        assertTrue(result.changed);
    }

    private File minimalEpub(String title, String author) throws Exception {
        File file = temporary.newFile("book.epub");
        String container = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>";
        String opf = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>" + escape(title) + "</dc:title><dc:creator>" + escape(author) + "</dc:creator></metadata><manifest/><spine/></package>";
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            put(zip, "META-INF/container.xml", container);
            put(zip, "OEBPS/content.opf", opf);
        }
        assertNotNull(file);
        return file;
    }

    private static void put(ZipOutputStream zip, String path, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
