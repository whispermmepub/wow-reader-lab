package com.whisper.wowreader;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24,28,35,36}, manifest = Config.NONE)
public class ReadingReminderIntegrationTest {
    private Application app;
    private SharedPreferences prefs;
    private AlarmManager alarms;
    private NotificationManager notifications;
    @Before public void setup() throws Exception {
        app=RuntimeEnvironment.getApplication();
        clearStore();
        prefs=app.getSharedPreferences("wow_reader",Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        alarms=(AlarmManager)app.getSystemService(Context.ALARM_SERVICE);
        notifications=(NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=33) Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
    }
    @After public void cleanup() throws Exception { clearStore(); }
    @Test public void defaultsAreOptInAndRepeatedRefreshHasOneAlarm() {
        ReadingReminderScheduler.refresh(app);
        assertEquals(0,Shadows.shadowOf(alarms).getScheduledAlarms().size());
        prefs.edit().putBoolean(ReadingReminderSettings.PREF_ENABLED,true).putInt(ReadingReminderSettings.PREF_HOUR,20).commit();
        ReadingReminderScheduler.refresh(app);
        ReadingReminderScheduler.refresh(app);
        assertEquals(1,Shadows.shadowOf(alarms).getScheduledAlarms().size());
        assertTrue(prefs.getLong(ReadingReminderScheduler.PREF_SCHEDULED_DUE,0)>System.currentTimeMillis());
    }
    @Test public void rebootRearmsEvenIfPersistedDueMatches() {
        prefs.edit().putBoolean(ReadingReminderSettings.PREF_ENABLED,true).commit();
        ReadingReminderScheduler.refresh(app);
        alarms.cancel(ReadingReminderScheduler.pendingIntent(app));
        assertEquals(0,Shadows.shadowOf(alarms).getScheduledAlarms().size());
        ReadingReminderReceiver.handle(app,new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertEquals(1,Shadows.shadowOf(alarms).getScheduledAlarms().size());
    }
    @Test public void coldStartUsesSqliteReadingInsteadOfStaleLegacyJson() throws Exception {
        prefs.edit().putBoolean(ReadingReminderSettings.PREF_ENABLED,true).commit();
        ReaderStateDb db=ReaderStateDb.initialize(app,prefs,new java.io.File(app.getFilesDir(),"library"));
        db.checkpoint();
        String today=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
        db.recordReading(today,"actual-reading.epub",60000L);
        // Simulate process state loss, preserving only on-disk SQLite and preferences.
        clearStore();
        assertEquals("{}",prefs.getString("reading_stats_days_json","{}"));
        ReadingReminderReceiver.handle(app,new Intent(ReadingReminderScheduler.ACTION_REMINDER));
        assertEquals(0,Shadows.shadowOf(notifications).size());
        assertFalse(prefs.contains("reading_reminder_last_delivered_day"));
    }
    @Test public void notificationIsDuplicateSafeAndOptOutCancelsIt() {
        prefs.edit().putBoolean(ReadingReminderSettings.PREF_ENABLED,true).commit();
        ReadingReminderReceiver.handle(app,new Intent(ReadingReminderScheduler.ACTION_REMINDER));
        assertEquals(1,Shadows.shadowOf(notifications).size());
        assertNotNull(Shadows.shadowOf(notifications).getNotification(ReadingReminderReceiver.NOTIFICATION_ID));
        String delivered=prefs.getString("reading_reminder_last_delivered_day","");
        assertFalse(delivered.isEmpty());
        ReadingReminderReceiver.handle(app,new Intent(ReadingReminderScheduler.ACTION_REMINDER));
        assertEquals(delivered,prefs.getString("reading_reminder_last_delivered_day",""));
        assertEquals(1,Shadows.shadowOf(notifications).size());
        prefs.edit().putBoolean(ReadingReminderSettings.PREF_ENABLED,false).commit();
        ReadingReminderScheduler.refresh(app);
        assertEquals(0,Shadows.shadowOf(notifications).size());
        assertEquals(0,Shadows.shadowOf(alarms).getScheduledAlarms().size());
    }
    @Test public void deniedNotificationPermissionDoesNotScheduleOnAndroid13Plus() {
        if(Build.VERSION.SDK_INT<33)return;
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        prefs.edit().putBoolean(ReadingReminderSettings.PREF_ENABLED,true).commit();
        ReadingReminderScheduler.refresh(app);
        assertEquals(0,Shadows.shadowOf(alarms).getScheduledAlarms().size());
        ReadingReminderReceiver.handle(app,new Intent(ReadingReminderScheduler.ACTION_REMINDER));
        assertEquals(0,Shadows.shadowOf(notifications).size());
    }
    private static void clearStore() throws Exception {
        ReaderStateDb db=ReaderStateDb.peek();
        if(db!=null){db.checkpoint();db.close();}
        Field singleton=ReaderStateDb.class.getDeclaredField("INSTANCE");singleton.setAccessible(true);singleton.set(null,null);
        Field stats=ReadingStatsStore.class.getDeclaredField("stateDb");stats.setAccessible(true);stats.set(null,null);
    }
}
