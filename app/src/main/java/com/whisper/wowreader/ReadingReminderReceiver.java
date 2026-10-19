package com.whisper.wowreader;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Delivers one reminder, records delivery by local day, and schedules the next occurrence. */
public final class ReadingReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL_ID = "reading_reminders";
    static final int NOTIFICATION_ID = 7862;
    private static final String PREF_LAST_DELIVERED_DAY = "reading_reminder_last_delivered_day";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread worker = new Thread(r, "wow-reading-reminder");
        worker.setDaemon(true);
        return worker;
    });

    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            try { handle(app, intent); }
            catch (RuntimeException error) {
                app.getSharedPreferences("wow_reader", Context.MODE_PRIVATE).edit()
                        .putString("reading_reminder_last_error", error.getClass().getSimpleName()).apply();
                try { ReadingReminderScheduler.refresh(app, true); } catch (RuntimeException ignored) {}
            }
            finally { pending.finish(); }
        });
    }

    static void handle(Context app, Intent intent) {
        SharedPreferences prefs = app.getSharedPreferences("wow_reader", Context.MODE_PRIVATE);
        if (ReadingReminderScheduler.ACTION_REMINDER.equals(intent.getAction())
                && prefs.getBoolean(ReadingReminderSettings.PREF_ENABLED, false)) {
            // An alarm can cold-start this process. The legacy JSON is no longer authoritative
            // after SQLite migration, so initialize and await migration off the broadcast thread.
            ReadingStatsStore.init(app, prefs);
            ReaderStateDb db = ReaderStateDb.peek();
            if (db != null) db.checkpoint();
            deliverIfNeeded(app, prefs);
        }
        ReadingReminderScheduler.refresh(app, true);
    }

    private static void deliverIfNeeded(Context context, SharedPreferences prefs) {
        if (!prefs.getBoolean(ReadingReminderSettings.PREF_ENABLED, false)) return;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return;
        // Date key uses the device timezone, matching ReadingStatsStore's day-based semantics.
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        if (ReadingReminderPolicy.alreadyDeliveredForDay(prefs.getString(PREF_LAST_DELIVERED_DAY, ""), today)) return;
        ReadingStatsStore.Snapshot snapshot = ReadingStatsStore.snapshot(prefs);
        ReadingReminderPolicy.Decision decision = ReadingReminderPolicy.decide(snapshot.todayMs, snapshot.currentStreak,
                prefs.getBoolean(ReadingReminderSettings.PREF_ONLY_IF_UNREAD, true),
                prefs.getBoolean(ReadingReminderSettings.PREF_STREAK_NUDGE, true));
        if (!decision.deliver) return;
        ensureChannel(context);
        Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, NOTIFICATION_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("WoW Reader")
                .setContentText(decision.message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(decision.message))
                .setContentIntent(tap).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT);
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification.build());
        } catch (SecurityException ignored) {
            return;
        }
        prefs.edit().putString(PREF_LAST_DELIVERED_DAY, today).commit();
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                "Reading reminders", NotificationManager.IMPORTANCE_DEFAULT));
    }
}
