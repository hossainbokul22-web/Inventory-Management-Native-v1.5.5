package com.rmstock.nativeapp;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.*;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;

/**
 * Foreground "on duty" location every \~75s.
 * No in-app stop button — user can still revoke via system Settings.
 */
public class DutyLocationService extends Service implements LocationListener {
    public static final String CH = "duty_location";
    public static final int NID = 4401;
    private static final long INTERVAL_MS = 75_000L;
    private LocationManager lm;
    private Handler handler;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            requestOneFix();
            if (handler != null) handler.postDelayed(this, INTERVAL_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        handler = new Handler(Looper.getMainLooper());
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = new NotificationCompat.Builder(this, CH)
                .setContentTitle("on duty")
                .setContentText("Location sharing for team")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NID, n);
            }
        } catch (Exception e) {
            startForeground(NID, n);
        }
        handler.removeCallbacks(tick);
        handler.post(tick);
        return START_STICKY;
    }

    private void requestOneFix() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            boolean gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean net = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if (gps) lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, Looper.getMainLooper());
            else if (net) lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, this, Looper.getMainLooper());
            Location last = null;
            if (gps) last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null && net) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (last != null) publish(last);
        } catch (Exception e) {
            Log.w("DutyLoc", "fix fail", e);
        }
    }

    private void publish(Location loc) {
        try {
            StockDb db = new StockDb(this);
            String name = db.setting("duty_name", db.setting("company", "User"));
            if (db.setting("user_name", "").length() > 0) name = db.setting("user_name", name);
            JSONObject o = new JSONObject();
            o.put("user", name);
            o.put("lat", loc.getLatitude());
            o.put("lng", loc.getLongitude());
            o.put("acc", loc.hasAccuracy() ? loc.getAccuracy() : -1);
            o.put("ts", System.currentTimeMillis());
            o.put("when", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date()));
            // always keep last local
            db.setSetting("last_duty_lat", String.valueOf(loc.getLatitude()));
            db.setSetting("last_duty_lng", String.valueOf(loc.getLongitude()));
            db.setSetting("last_duty_ts", o.optString("when"));
            String mode = db.setting("sync_mode", "off");
            if ("client".equals(mode)) {
                SyncHub.get(this).enqueue("location", o);
                // try immediate push of just this point
                SyncHub.get(this).flushQueue(null);
            } else if ("server".equals(mode) || "off".equals(mode)) {
                // store on this device if admin/server
                storeLocal(db, o);
            }
        } catch (Exception e) {
            Log.w("DutyLoc", "publish", e);
        }
    }

    static void storeLocal(StockDb db, JSONObject o) {
        try {
            db.getWritableDatabase().execSQL(
                "CREATE TABLE IF NOT EXISTS duty_locations(id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user TEXT, lat REAL, lng REAL, acc REAL, ts TEXT, received_at TEXT)");
            android.content.ContentValues v = new android.content.ContentValues();
            v.put("user", o.optString("user"));
            v.put("lat", o.optDouble("lat"));
            v.put("lng", o.optDouble("lng"));
            v.put("acc", o.optDouble("acc"));
            v.put("ts", o.optString("when"));
            v.put("received_at", String.valueOf(System.currentTimeMillis()));
            // keep one row per user name (latest)
            db.getWritableDatabase().delete("duty_locations", "user=?", new String[]{o.optString("user")});
            db.getWritableDatabase().insert("duty_locations", null, v);
        } catch (Exception ignored) {}
    }

    @Override public void onLocationChanged(Location location) {
        if (location != null) publish(location);
    }

    @Override public void onDestroy() {
        if (handler != null) handler.removeCallbacks(tick);
        try { if (lm != null) lm.removeUpdates(this); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "on duty", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Team location while on duty");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    public static void start(Context c) {
        Intent i = new Intent(c, DutyLocationService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }
}
