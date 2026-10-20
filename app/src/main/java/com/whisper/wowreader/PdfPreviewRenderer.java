package com.whisper.wowreader;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.IOException;

/**
 * Reusable PDF thumbnail renderer. Instances are intended to be confined to one worker thread.
 * A renderer and its descriptor remain open while the PDF identity is unchanged.
 */
final class PdfPreviewRenderer {
    private static final int MAX_WIDTH = 400;
    private static final int MAX_HEIGHT = 560;

    private PdfRenderer renderer;
    private ParcelFileDescriptor descriptor;
    private String path;
    private long length;
    private long modified;

    /**
     * Renders one zero-based PDF page into a thumbnail no larger than 400x560.
     * The caller should invoke this object from a single worker thread.
     */
    public Bitmap render(File pdf, int page, int backgroundColor) throws IOException {
        if (pdf == null) {
            throw new IOException("PDF file is null");
        }

        final String requestedPath = pdf.getAbsolutePath();
        final long requestedLength = pdf.length();
        final long requestedModified = pdf.lastModified();
        if (!sameIdentity(requestedPath, requestedLength, requestedModified)) {
            close();
            open(pdf, requestedPath, requestedLength, requestedModified);
        }

        PdfRenderer.Page pdfPage = null;
        Bitmap bitmap = null;
        boolean failed = false;
        try {
            if (page < 0 || page >= renderer.getPageCount()) {
                throw new IOException("PDF page out of range: " + page);
            }
            pdfPage = renderer.openPage(page);
            int sourceWidth = Math.max(1, pdfPage.getWidth());
            int sourceHeight = Math.max(1, pdfPage.getHeight());
            float scale = Math.min(MAX_WIDTH / (float) sourceWidth,
                    MAX_HEIGHT / (float) sourceHeight);
            int width = boundedDimension(Math.round(sourceWidth * scale), MAX_WIDTH);
            int height = boundedDimension(Math.round(sourceHeight * scale), MAX_HEIGHT);
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(backgroundColor);
            pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bitmap;
        } catch (IOException e) {
            failed = true;
            recycle(bitmap);
            throw e;
        } catch (RuntimeException e) {
            failed = true;
            recycle(bitmap);
            IOException failure = new IOException("Unable to render PDF page", e);
            throw failure;
        } finally {
            if (pdfPage != null) {
                try {
                    pdfPage.close();
                } catch (RuntimeException e) {
                    // A successfully rendered bitmap remains usable; cleanup is best effort here.
                }
            }
            if (failed) close();
        }
    }

    /** Closes the cached renderer and descriptor; safe to call more than once. */
    public void close() {
        PdfRenderer rendererToClose = renderer;
        ParcelFileDescriptor descriptorToClose = descriptor;
        renderer = null;
        descriptor = null;
        path = null;
        length = 0L;
        modified = 0L;

        if (rendererToClose != null) {
            try {
                rendererToClose.close();
            } catch (RuntimeException ignored) {
                // Continue closing the descriptor even if renderer cleanup fails.
            }
        }
        if (descriptorToClose != null) {
            try {
                descriptorToClose.close();
            } catch (IOException | RuntimeException ignored) {
                // Cleanup is best effort and the state has already been cleared.
            }
        }
    }

    private void open(File pdf, String requestedPath, long requestedLength, long requestedModified)
            throws IOException {
        ParcelFileDescriptor openedDescriptor = null;
        PdfRenderer openedRenderer = null;
        try {
            openedDescriptor = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
            openedRenderer = new PdfRenderer(openedDescriptor);
            descriptor = openedDescriptor;
            renderer = openedRenderer;
            path = requestedPath;
            length = requestedLength;
            modified = requestedModified;
        } catch (IOException e) {
            closeQuietly(openedRenderer, openedDescriptor);
            throw e;
        } catch (RuntimeException e) {
            closeQuietly(openedRenderer, openedDescriptor);
            throw new IOException("Unable to open PDF", e);
        }
    }

    private boolean sameIdentity(String requestedPath, long requestedLength, long requestedModified) {
        return renderer != null && descriptor != null && requestedPath.equals(path)
                && requestedLength == length && requestedModified == modified;
    }

    private static int boundedDimension(int value, int maximum) {
        return Math.max(1, Math.min(maximum, value));
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    private static void closeQuietly(PdfRenderer renderer, ParcelFileDescriptor descriptor) {
        if (renderer != null) {
            try {
                renderer.close();
            } catch (RuntimeException ignored) {
                // Preserve the opening failure.
            }
        }
        if (descriptor != null) {
            try {
                descriptor.close();
            } catch (IOException ignored) {
                // Preserve the opening failure.
            }
        }
    }
}
