package com.whisper.wowreader;

import java.io.File;
import java.io.IOException;

/** Removes an app-private copy without losing it if its database removal fails. */
final class LocalBookRemoval {
    interface RemoveRow { boolean remove() throws Exception; }
    private LocalBookRemoval() { }

    static boolean remove(File file, File libraryDir, RemoveRow row) throws Exception {
        if (file == null || libraryDir == null || row == null || file.getName().isEmpty())
            throw new IOException("Unable to identify this library entry");
        File root = libraryDir.getCanonicalFile();
        File target = file.getCanonicalFile();
        if (!root.equals(target.getParentFile()))
            throw new IOException("Only WoW Reader's private library copy can be removed");
        if (target.exists() && !target.isFile())
            throw new IOException("This library entry is not a book file");

        // A missing local copy must not make its indexed entry impossible to remove.
        if (!target.exists()) return row.remove();

        // Stage on the same filesystem. A .pending file is not imported or shown as a book.
        File staged = File.createTempFile(".wow-remove-", ".pending", root);
        if (!target.renameTo(staged)) {
            staged.delete();
            throw new IOException("Unable to remove the local copy; your book has not been changed");
        }
        boolean committed = false;
        try {
            if (!row.remove()) return false;
            committed = true;
            // If cleanup is denied, retain the private staged bytes instead of deleting other data.
            staged.delete();
            return true;
        } finally {
            if (!committed && !target.exists() && !staged.renameTo(target))
                throw new IOException("Removal was not completed. The saved copy is preserved at " + staged.getAbsolutePath());
        }
    }
}
