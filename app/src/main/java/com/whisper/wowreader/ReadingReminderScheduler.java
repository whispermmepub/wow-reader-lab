package com.whisper.wowreader;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationManagerCompat;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Owns one inexact daily alarm; never needs exact-alarm special access. */
public final class ReadingReminderScheduler {
    static final String ACTION_REMINDER = "com.whisper.wowreader.action.READING_REMINDER";
    static final int ALARM_REQUEST_CODE = 7861;
    static final String PREF_SCHEDULED_DUE = "reading_reminder_scheduled_due_ms";
    private ReadingReminderScheduler() {}

    public static void refresh(Context context) { refresh(context, false); }

    /** System boot/clock events force rearming because a persisted due time is not an OS alarm. */
    static void refresh(Context context, boolean force) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences("wow_reader", Context.MODE_PRIVATE);
        AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = pendingIntent(app);
        boolean permission = Build.VERSION.SDK_INT < 33
                || app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        if (alarms == null || !prefs.getBoolean(ReadingReminderSettings.PREF_ENABLED, false) || !permission) {
            if (alarms != null) alarms.cancel(pending);
            NotificationManagerCompat.from(app).cancel(ReadingReminderReceiver.NOTIFICATION_ID);
            prefs.edit().remove(PREF_SCHEDULED_DUE).apply();
            return;
        }
        if (hasReadToday(prefs)) NotificationManagerCompat.from(app).cancel(ReadingReminderReceiver.NOTIFICATION_ID);
        long now = System.currentTimeMillis();
        long due = ReadingReminderPolicy.nextLocalDateTime(now,
                prefs.getInt(ReadingReminderSettings.PREF_HOUR, ReadingReminderPolicy.DEFAULT_HOUR),
                prefs.getInt(ReadingReminderSettings.PREF_MINUTE, ReadingReminderPolicy.DEFAULT_MINUTE));
        // Replacing the same PendingIntent is idempotent and also repairs force-stop/reboot loss.
        // Never trust the persisted due stamp as proof that Android still holds the alarm.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pending);
        prefs.edit().putLong(PREF_SCHEDULED_DUE, due).apply();
    }

    static boolean hasReadToday(SharedPreferences prefs) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        ReaderStateDb db = ReaderStateDb.peek();
        if (db != null && db.isLegacyMigrated()) return db.dayTime(today) > 0L;
        try { return new JSONObject(prefs.getString("reading_stats_days_json", "{}")).optLong(today, 0L) > 0L; }
        catch (Exception ignored) { return false; }
    }

    static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, ReadingReminderReceiver.class).setAction(ACTION_REMINDER);
        return PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
