package com.rmstock.nativeapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Morning (or on-demand) offline reminders:
 * reorder level, expiry window, no batch entry today.
 */
public class AlertReceiver extends BroadcastReceiver {
    public static final String CH = "inv_daily_alerts";
    public static final String ACTION_DAILY = "com.rmstock.nativeapp.DAILY_ALERT";
    public static final String ACTION_BOOT = "com.rmstock.nativeapp.BOOT_RESCHEDULE";

    @Override
    public void onReceive(Context context, Intent intent) {
        String act = intent != null ? intent.getAction() : "";
        if (Intent.ACTION_BOOT_COMPLETED.equals(act) || ACTION_BOOT.equals(act)) {
            AlertScheduler.schedule(context);
            try { AutoBackup.schedule(context); } catch (Exception ignored) {}
            return;
        }
        if (AutoBackup.ACTION.equals(act)) {
            try {
                if (AutoBackup.isOn(context)) AutoBackup.runNow(context);
            } catch (Exception ignored) {}
            return;
        }
        // Daily check (also used for "Test notification")
        try {
            showDailyAlert(context);
        } catch (Exception ignored) {}
        // Reschedule next day in case exact alarm was one-shot
        AlertScheduler.schedule(context);
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CH, "Daily stock alerts", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Reorder, expiry and batch reminders");
        nm.createNotificationChannel(ch);
    }

    static void showDailyAlert(Context context) throws Exception {
        StockDb db = StockDb.get(context);
        if (!"1".equals(db.setting("alerts_on", "1"))) return;

        String today = today();
        int warnDays = 14;
        try {
            warnDays = Integer.parseInt(db.setting("expiry_days", "14"));
            if (warnDays < 1) warnDays = 14;
        } catch (Exception ignored) {}

        Map<String, Double> rec = sumMap(db,
                "SELECT rm_code k,SUM(qty) v FROM receives WHERE date<=? GROUP BY rm_code", new String[]{today});
        Map<String, Double> use = sumMap(db,
                "SELECT rm_code k,SUM(consumption) v FROM mixing2_batch_log WHERE date<=? GROUP BY rm_code", new String[]{today});

        int low = 0, shortN = 0;
        StringBuilder lowNames = new StringBuilder();
        for (JSONObject rm : db.rows("SELECT code,name,opening,reorder FROM rms", null)) {
            String code = rm.optString("code");
            double closing = rm.optDouble("opening", 0) + nz(rec.get(code)) - nz(use.get(code));
            double reorder = rm.optDouble("reorder", 0);
            if (closing < 0) {
                shortN++;
                if (lowNames.length() < 80) lowNames.append(rm.optString("name")).append(", ");
            } else if (reorder > 0 && closing <= reorder) {
                low++;
                if (lowNames.length() < 80) lowNames.append(rm.optString("name")).append(", ");
            }
        }

        String limit = PlanLogic.shiftDate(today, warnDays);
        int expN = 0;
        try {
            JSONObject eo = db.one(
                    "SELECT COUNT(*) n FROM receives WHERE expiry IS NOT NULL AND expiry<>'' AND expiry<=?",
                    new String[]{limit});
            if (eo != null) expN = eo.optInt("n", 0);
        } catch (Exception ignored) {}

        int batchToday = 0;
        try {
            JSONObject b = db.one("SELECT COUNT(*) n FROM batches WHERE date=?", new String[]{today});
            if (b != null) batchToday = b.optInt("n", 0);
        } catch (Exception ignored) {}

        StringBuilder body = new StringBuilder();
        int issues = 0;
        if (shortN > 0) {
            issues++;
            body.append("• ").append(shortN).append(" RM below zero\n");
        }
        if (low > 0) {
            issues++;
            body.append("• ").append(low).append(" at/under reorder\n");
        }
        if (expN > 0) {
            issues++;
            body.append("• ").append(expN).append(" lot(s) in expiry window (").append(warnDays).append("d)\n");
        }
        if (batchToday == 0) {
            issues++;
            body.append("• No batch entry today yet\n");
        }

        if (issues == 0) {
            body.append("All clear for ").append(today).append(" — stock & batches look OK.");
        } else if (lowNames.length() > 2) {
            body.append("e.g. ").append(lowNames.substring(0, Math.min(lowNames.length(), 60)));
        }

        String title = issues == 0
                ? "Inventory OK — " + today
                : "Inventory alerts (" + issues + ") — " + today;

        ensureChannel(context);
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CH)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body.toString().replace("\n", " "))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body.toString()))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        try {
            NotificationManagerCompat.from(context).notify(4401, b.build());
        } catch (SecurityException se) {
            // POST_NOTIFICATIONS not granted
        }
    }

    private static String today() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH));
    }

    private static double nz(Double d) { return d == null ? 0 : d; }

    private static Map<String, Double> sumMap(StockDb db, String sql, String[] args) throws Exception {
        Map<String, Double> m = new HashMap<>();
        for (JSONObject o : db.rows(sql, args)) {
            m.put(o.optString("k"), o.optDouble("v", 0));
        }
        return m;
    }
}
