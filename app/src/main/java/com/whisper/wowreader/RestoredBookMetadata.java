package com.whisper.wowreader;

import android.content.SharedPreferences;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation;

import java.io.File;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Safe, non-destructive metadata repair policy for books restored from Drive.
 *
 * <p>This class does not rename, delete, or rewrite a book.  Callers can persist
 * {@link Result} with their own compare-and-set policy after doing this work off
 * the UI thread.</p>
 */
public final class RestoredBookMetadata {
    private static final String UNTITLED = "Untitled book";
    private static final Pattern TRANSPORT_NAME = Pattern.compile(
            "^(?:wow_book_)?[0-9a-fA-F]{64}(?:-cloud(?:-[0-9]+)?)?(?:\\.(?:epub|pdf))?$",
            Pattern.CASE_INSENSITIVE);

    private RestoredBookMetadata() {
    }

    /** Immutable repaired values and whether either value differs from the input. */
    public static final class Result {
        public final String title;
        public final String author;
        public final boolean changed;

        private Result(String title, String author, boolean changed) {
            this.title = title;
            this.author = author;
            this.changed = changed;
        }

        public String getTitle() { return title; }
        public String getAuthor() { return author; }
        public boolean isChanged() { return changed; }
    }

    /**
     * Returns whether a title is a generated transport/default title rather than
     * a title a user should be asked to rename. Explicit custom titles always win.
     */
    public static boolean needsRepair(String title, String fileName, boolean explicitlyCustom) {
        if (explicitlyCustom) return false;
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) return true;
        if (isTransportName(t)) return true;
        // Some old rows stored the restored filename as their title. Do not
        // classify an ordinary filename as generated merely because it is a file.
        return fileName != null && isTransportName(fileName.trim()) && t.equalsIgnoreCase(fileName.trim());
    }

    /**
     * Reads embedded metadata and repairs only a blank/generated title. Existing
     * non-generated titles and existing authors are never overwritten.
     * Extraction errors intentionally return the original values unchanged.
     */
    public static Result read(File file, File coverDir, String currentTitle, String currentAuthor,
                              boolean explicitlyCustom) {
        final boolean repair = needsRepair(currentTitle, file == null ? null : file.getName(), explicitlyCustom);
        if (file == null || !file.isFile() || !repair) {
            return result(currentTitle, currentAuthor, currentTitle, currentAuthor);
        }

        String metadataTitle = null;
        String metadataAuthor = null;
        try {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".epub")) {
                File cache = coverDir != null ? coverDir : file.getParentFile();
                if (cache == null) cache = new File(".");
                EpubUtil.Summary summary = EpubUtil.extractSummary(file, cache);
                if (summary != null) {
                    metadataTitle = usable(summary.title);
                    metadataAuthor = usable(summary.author);
                }
            } else if (name.endsWith(".pdf")) {
                try (PDDocument document = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())) {
                    PDDocumentInformation info = document.getDocumentInformation();
                    if (info != null) {
                        metadataTitle = usable(info.getTitle());
                        metadataAuthor = usable(info.getAuthor());
                    }
                }
            } else {
                return result(currentTitle, currentAuthor, currentTitle, currentAuthor);
            }
        } catch (Exception ignored) {
            // A corrupt/unreadable file is not evidence that it should be removed
            // or that its metadata is valid. Leave both values exactly as supplied.
            return result(currentTitle, currentAuthor, currentTitle, currentAuthor);
        }

        String nextTitle = metadataTitle != null && !isTransportName(metadataTitle) ? metadataTitle : UNTITLED;
        String nextAuthor = usable(currentAuthor) != null ? currentAuthor : (metadataAuthor == null ? "" : metadataAuthor);
        return result(currentTitle, currentAuthor, nextTitle, nextAuthor);
    }

    /** Off-main-thread repair for either a just-restored book or an existing v71 library. */
    static boolean repairStored(ReaderStateDb db, File file, File covers, SharedPreferences prefs) {
        if (db == null || file == null || !file.isFile()) return false;
        String flag = "library_metadata_custom_" + file.getName();
        boolean custom = prefs != null && prefs.getBoolean(flag, false);
        String before = db.indexedTitle(file.getName(), "");
        if (!needsRepair(before, file.getName(), custom)) return false;
        Result metadata = read(file, covers, before, db.indexedAuthor(file.getName()), custom);
        if (!metadata.changed || (prefs != null && prefs.getBoolean(flag, false))) return false;
        // The DB is authoritative after indexing. Do not race a user edit by mirroring stale prefs.
        return db.repairRestoredMetadata(file.getName(), before, metadata.title, metadata.author);
    }

    private static Result result(String oldTitle, String oldAuthor, String newTitle, String newAuthor) {
        return new Result(newTitle, newAuthor,
                !Objects.equals(oldTitle, newTitle) || !Objects.equals(oldAuthor, newAuthor));
    }

    private static boolean isTransportName(String value) {
        return value != null && TRANSPORT_NAME.matcher(value).matches();
    }

    private static String usable(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
