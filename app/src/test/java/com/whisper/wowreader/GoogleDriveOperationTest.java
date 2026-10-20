package com.whisper.wowreader;

import android.app.Activity;
import android.os.Looper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 35, 36}, manifest = Config.NONE)
public class GoogleDriveOperationTest {
    private Activity activity;
    private Class<?> operationType;
    private int successes, errors, cancellations;
    private final GoogleDriveSync.SyncCallback callback = new GoogleDriveSync.SyncCallback() {
        public void onSuccess(String text) { successes++; }
        public void onError(String text) { errors++; }
        public void onCancelled() { cancellations++; }
    };
    @Before public void setup() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        operationType = Class.forName("com.whisper.wowreader.GoogleDriveSync$Operation");
        setActive(null);
    }
    @After public void cleanup() throws Exception {
        setActive(null); Thread.interrupted(); activity.finish();
    }
    private void setActive(Object operation) throws Exception {
        Field f = GoogleDriveSync.class.getDeclaredField("activeOperation"); f.setAccessible(true); f.set(null, operation);
    }
    private Object begin() throws Exception {
        Method m = GoogleDriveSync.class.getDeclaredMethod("beginOperation", GoogleDriveSync.SyncCallback.class);
        m.setAccessible(true); return m.invoke(null, callback);
    }
    private void deliver(String name, Object operation) throws Exception {
        Method m;
        if (name.equals("deliverCancelled")) {
            m = GoogleDriveSync.class.getDeclaredMethod(name, Activity.class, operationType);
            m.setAccessible(true); m.invoke(null, activity, operation);
        } else {
            m = GoogleDriveSync.class.getDeclaredMethod(name, Activity.class, operationType, String.class);
            m.setAccessible(true); m.invoke(null, activity, operation, "result");
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    @Test public void activeOperationRejectsDuplicateUntilTerminalCallback() throws Exception {
        Object first = begin(); assertNotNull(first); assertNull(begin());
        deliver("deliverError", first); assertNotNull(begin()); assertEquals(1, errors);
    }
    @Test public void cancellationCannotBecomeSuccessAndDeliversOnlyOnce() throws Exception {
        Object operation = begin(); assertTrue(GoogleDriveSync.cancelActive());
        deliver("deliverSuccess", operation); deliver("deliverError", operation); deliver("deliverCancelled", operation);
        assertEquals(1, cancellations); assertEquals(0, successes); assertEquals(0, errors);
    }
    @Test public void finishingPreviousOperationCannotClearNewOperation() throws Exception {
        Object old = begin(); deliver("deliverSuccess", old); Object next = begin();
        Method finish = GoogleDriveSync.class.getDeclaredMethod("finishOperation", operationType);
        finish.setAccessible(true); finish.invoke(null, old);
        assertNull(begin()); assertTrue(GoogleDriveSync.cancelActive());
        deliver("deliverCancelled", next); assertEquals(1, successes); assertEquals(1, cancellations);
    }
}
