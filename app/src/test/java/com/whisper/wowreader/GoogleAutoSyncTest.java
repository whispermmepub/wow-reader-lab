package com.whisper.wowreader;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={23,35,36},manifest=Config.NONE)
public class GoogleAutoSyncTest {
    @After public void cleanup(){GoogleAutoSync.setHomeVisible(false);}
    @Test public void readingDefersTransfersWithoutClaimingSuccess() {
        GoogleAutoSync.setHomeVisible(false);
        try {GoogleAutoSync.requireHomeVisible();fail("Hidden Home must defer sync work");}
        catch(Exception expected){assertTrue(expected.getMessage().startsWith("Sync paused"));}
    }
    @Test public void returningHomeAllowsSameQueuedTransferToResume() throws Exception {
        GoogleAutoSync.setHomeVisible(false);
        GoogleAutoSync.setHomeVisible(true);
        GoogleAutoSync.requireHomeVisible();
        GoogleAutoSync.setHomeVisible(false);
        try {GoogleAutoSync.requireHomeVisible();fail("Must pause again when reader opens");}
        catch(Exception expected){assertTrue(expected.getMessage().contains("Return to Home"));}
    }
}
