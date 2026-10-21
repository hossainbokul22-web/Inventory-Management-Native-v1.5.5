package com.rmstock.nativeapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** Schedules the daily morning inventory alert. */
public final class AlertScheduler {
    private AlertScheduler() {}

    public static void schedule(Context context) {
        Context c = context.getApplicationContext();
        StockDb db = new StockDb(c);
        if (!"1".equals(db.setting("alerts_on", "1"))) {
            cancel(c);
            return;
        }
        int hour = 8;
        int minute = 0;
        try {
            hour = Integer.parseInt(db.setting("alert_hour", "8"));
            minute = Integer.parseInt(db.setting("alert_minute", "0"));
        } catch (Exception ignored) {}
        if (hour < 0 || hour > 23) hour = 8;
        if (minute < 0 || minute > 59) minute = 0;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        Intent i = new Intent(c, AlertReceiver.class);
        i.setAction(AlertReceiver.ACTION_DAILY);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(c, 4402, i, flags);

        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            }
        } catch (SecurityException se) {
            // SCHEDULE_EXACT_ALARM may be denied — fall back
            am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        }
    }

    public static void cancel(Context context) {
        Context c = context.getApplicationContext();
        Intent i = new Intent(c, AlertReceiver.class);
        i.setAction(AlertReceiver.ACTION_DAILY);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(c, 4402, i, flags);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pi);
    }

    /** Fire once now (test) without changing schedule. */
    public static void testNow(Context context) {
        try {
            AlertReceiver.ensureChannel(context);
            AlertReceiver.showDailyAlert(context);
        } catch (Exception ignored) {}
        schedule(context);
    }
}
