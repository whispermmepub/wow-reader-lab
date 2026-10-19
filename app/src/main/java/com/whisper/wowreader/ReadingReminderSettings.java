package com.whisper.wowreader;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

/** Small native opt-in editor for the daily reading reminder. */
public final class ReadingReminderSettings {
    public static final int REQUEST_POST_NOTIFICATIONS = 4207;
    static final String PREF_ENABLED = "reading_reminder_enabled";
    static final String PREF_ONLY_IF_UNREAD = "reading_reminder_only_if_unread";
    static final String PREF_STREAK_NUDGE = "reading_reminder_streak_nudge";
    static final String PREF_HOUR = "reading_reminder_hour";
    static final String PREF_MINUTE = "reading_reminder_minute";

    private ReadingReminderSettings() {}

    public static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        final android.content.SharedPreferences prefs = activity.getSharedPreferences("wow_reader", 0);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        box.setPadding(pad, 0, pad, 0);

        final CheckBox enabled = new CheckBox(activity);
        enabled.setText("Daily reading reminder");
        enabled.setChecked(prefs.getBoolean(PREF_ENABLED, false));
        box.addView(enabled);
        final TimePicker time = new TimePicker(activity);
        time.setIs24HourView(android.text.format.DateFormat.is24HourFormat(activity));
        time.setHour(prefs.getInt(PREF_HOUR, ReadingReminderPolicy.DEFAULT_HOUR));
        time.setMinute(prefs.getInt(PREF_MINUTE, ReadingReminderPolicy.DEFAULT_MINUTE));
        box.addView(time);
        final CheckBox onlyUnread = new CheckBox(activity);
        onlyUnread.setText("Only remind me if I haven't read today");
        onlyUnread.setChecked(prefs.getBoolean(PREF_ONLY_IF_UNREAD, true));
        box.addView(onlyUnread);
        final CheckBox streak = new CheckBox(activity);
        streak.setText("Nudge me when my streak is at risk");
        streak.setChecked(prefs.getBoolean(PREF_STREAK_NUDGE, true));
        box.addView(streak);
        TextView timing = new TextView(activity);
        timing.setText("Reminders use your phone's local time. Android battery restrictions may delay delivery.");
        timing.setPadding(0, pad / 2, 0, 0);
        box.addView(timing);
        View.OnClickListener state = v -> {
            boolean on = enabled.isChecked();
            time.setEnabled(on); onlyUnread.setEnabled(on); streak.setEnabled(on);
        };
        enabled.setOnClickListener(state); state.onClick(enabled);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Reading reminder")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            int hour = time.getHour(), minute = time.getMinute();
            prefs.edit().putBoolean(PREF_ENABLED, enabled.isChecked())
                    .putBoolean(PREF_ONLY_IF_UNREAD, onlyUnread.isChecked())
                    .putBoolean(PREF_STREAK_NUDGE, streak.isChecked())
                    .putInt(PREF_HOUR, hour).putInt(PREF_MINUTE, minute).apply();
            if (enabled.isChecked() && Build.VERSION.SDK_INT >= 33
                    && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
            } else {
                ReadingReminderScheduler.refresh(activity);
            }
            dialog.dismiss();
        }));
        dialog.show();
    }

    /** Returns true when this class consumed the notification permission callback. */
    public static boolean onPermissionResult(Activity activity, int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQUEST_POST_NOTIFICATIONS) return false;
        boolean granted = grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (activity != null) {
            if (!granted) {
                activity.getSharedPreferences("wow_reader", 0).edit().putBoolean(PREF_ENABLED, false).apply();
                Toast.makeText(activity, "Notifications are off, so the reading reminder wasn't enabled.", Toast.LENGTH_LONG).show();
            }
            ReadingReminderScheduler.refresh(activity);
        }
        return true;
    }
}
