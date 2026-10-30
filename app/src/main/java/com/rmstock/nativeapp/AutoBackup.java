package com.rmstock.nativeapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Daily auto backup of RM + PM. Only runs when setting auto_backup_on=1.
 * Start schedules alarm; Stop cancels and keeps off until user starts again.
 */
public final class AutoBackup {
    public static final String ACTION = "com.rmstock.nativeapp.AUTO_BACKUP";
    public static final String KEY_ON = "auto_backup_on";
    public static final int REQ = 4410;
    private AutoBackup() {}

    public static boolean isOn(Context c) {
        return "1".equals(StockDb.get(c).setting(KEY_ON, "0"));
    }

    public static void start(Context c) {
        StockDb.get(c).setSetting(KEY_ON, "1");
        schedule(c);
        try { runNow(c); } catch (Exception ignored) {}
    }

    public static void stop(Context c) {
        StockDb.get(c).setSetting(KEY_ON, "0");
        cancel(c);
    }

    public static void schedule(Context context) {
        Context c = context.getApplicationContext();
        if (!isOn(c)) {
            cancel(c);
            return;
        }
        int hour = 21;
        int minute = 0;
        try {
            hour = Integer.parseInt(StockDb.get(c).setting("auto_backup_hour", "21"));
            minute = Integer.parseInt(StockDb.get(c).setting("auto_backup_minute", "0"));
        } catch (Exception ignored) {}
        if (hour < 0 || hour > 23) hour = 21;
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
        i.setAction(ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(c, REQ, i, flags);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            }
        } catch (SecurityException se) {
            am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        }
    }

    public static void cancel(Context context) {
        Context c = context.getApplicationContext();
        Intent i = new Intent(c, AlertReceiver.class);
        i.setAction(ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(c, REQ, i, flags);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pi);
    }

    /** Write unified RM+PM JSON under files/backups/. Keeps last \~14 files. */
    public static File runNow(Context context) throws Exception {
        Context c = context.getApplicationContext();
        StockDb db = StockDb.get(c);
        JSONObject all = new JSONObject();
        all.put("version", 5);
        all.put("app", "Inventory Management");
        all.put("auto", true);
        all.put("when", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        all.put("company", db.setting("company", ""));
        JSONArray tables = new JSONArray();
        String[] ts = {
            "rms", "products", "recipes", "batches", "receives", "approvals",
            "custom_sheets", "custom_cols", "custom_rows", "mixing2_batch_log", "counts",
            "settings", "yield_defs", "yield_stages", "yield_history", "audit_log",
            "recipe_archive", "workers", "attendance", "sync_queue", "team_devices"
        };
        for (String t : ts) {
            JSONObject z = new JSONObject();
            z.put("table", t);
            z.put("rows", new JSONArray());
            try {
                for (JSONObject r : db.rows("SELECT * FROM " + t, null)) {
                    z.getJSONArray("rows").put(r);
                }
            } catch (Exception ignored) {}
            tables.put(z);
        }
        all.put("tables", tables);
        try {
            PmDb pm = PmDb.get(c);
            all.put("pm", new JSONObject(pm.exportJson()));
        } catch (Exception pe) {
            all.put("pm", JSONObject.NULL);
        }

        File dir = new File(c.getExternalFilesDir(null), "backups");
        if (!dir.exists()) dir.mkdirs();
        String day = new SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(new Date());
        File f = new File(dir, "auto-backup-" + day + ".json");
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(all.toString().getBytes("UTF-8"));
        }
        db.setSetting("auto_backup_last", f.getAbsolutePath());
        db.setSetting("auto_backup_last_at", all.optString("when"));
        try { db.audit("system", "system", "auto_backup", f.getName()); } catch (Exception ignored) {}
        prune(dir, 14);
        // reschedule next day only if still on
        schedule(c);
        return f;
    }

    private static void prune(File dir, int keep) {
        File[] fs = dir.listFiles((d, n) -> n != null && n.startsWith("auto-backup-") && n.endsWith(".json"));
        if (fs == null || fs.length <= keep) return;
        java.util.Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = keep; i < fs.length; i++) {
            try { fs[i].delete(); } catch (Exception ignored) {}
        }
    }
}
