package com.whisper.wowreader;

import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class CoverDecodeMemoryTest {
    @Test
    public void sampledCoverDecodeBoundsBitmapAndPreservesCacheFile() throws Exception {
        File source = new File(RuntimeEnvironment.getApplication().getCacheDir(), "cover-memory-test.jpg");
        Bitmap original = Bitmap.createBitmap(2400, 3600, Bitmap.Config.RGB_565);
        try (FileOutputStream out = new FileOutputStream(source)) {
            assertTrue(original.compress(Bitmap.CompressFormat.JPEG, 90, out));
        } finally {
            original.recycle();
        }

        Bitmap sampled = CustomCoverStore.decodeSampled(source, 360, 520);
        try {
            assertNotNull(sampled);
            assertTrue(sampled.getWidth() <= 720);
            assertTrue(sampled.getHeight() <= 1040);
            assertTrue(source.isFile());
        } finally {
            if (sampled != null) sampled.recycle();
            //noinspection ResultOfMethodCallIgnored
            source.delete();
        }
    }
}
