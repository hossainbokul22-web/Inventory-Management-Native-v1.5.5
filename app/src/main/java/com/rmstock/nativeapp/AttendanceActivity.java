package com.rmstock.nativeapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AttendanceActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private StockDb db;
    private Camera camera;
    private SurfaceView preview;
    private TextView statusTv, punchTv, todayTv;
    private FaceDetector detector;
    private volatile boolean faceOk;
    private volatile long lastFaceMs;
    private boolean punching;
    private String punchType = "IN";
    private String shiftFilter = "ALL";
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout workerBox;
    private byte[] lastFrame;
    private int previewW, previewH;
    private static final int DUP_MINUTES = 25;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        db = new StockDb(this);
        db.ensureAttendanceTables();
        detector = FaceDetection.getClient(new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build());
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 301);
        }
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 248, 250));
        TextView title = new TextView(this);
        title.setText("Attendance · Face + Photo backup");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setPadding(dp(16), dp(14), dp(16), dp(14));
        title.setBackgroundColor(Color.rgb(15, 76, 129));
        root.addView(title);
        punchTv = new TextView(this);
        punchTv.setText("Punch: IN");
        punchTv.setTextColor(Color.rgb(15, 76, 129));
        punchTv.setPadding(dp(12), dp(8), dp(12), dp(4));
        root.addView(punchTv);
        FrameLayout stage = new FrameLayout(this);
        preview = new SurfaceView(this);
        preview.getHolder().addCallback(this);
        stage.addView(preview, new FrameLayout.LayoutParams(-1, dp(240)));
        statusTv = new TextView(this);
        statusTv.setText("Looking for face…");
        statusTv.setTextColor(Color.WHITE);
        statusTv.setBackgroundColor(Color.argb(140, 0, 0, 0));
        statusTv.setPadding(dp(12), dp(8), dp(12), dp(8));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1, -2);
        sp.gravity = Gravity.BOTTOM;
        stage.addView(statusTv, sp);
        root.addView(stage);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(6), dp(8), dp(6), dp(4));
        row.addView(action("IN", v -> { punchType = "IN"; punchTv.setText("Punch: IN"); }));
        row.addView(action("OUT", v -> { punchType = "OUT"; punchTv.setText("Punch: OUT"); }));
        row.addView(action("Photo", v -> openPhotoFallback()));
        row.addView(action("Workers", v -> manageWorkers()));
        row.addView(action("Today", v -> refreshToday()));
        root.addView(row);
        LinearLayout shifts = new LinearLayout(this);
        shifts.setPadding(dp(6), dp(4), dp(6), dp(4));
        for (String sh : new String[]{"ALL", "A", "B", "C", "General"}) {
            final String f = sh;
            TextView t = chip(sh);
            t.setOnClickListener(v -> { shiftFilter = f; reloadWorkers(); });
            shifts.addView(t);
        }
        root.addView(shifts);
        ScrollView sc = new ScrollView(this);
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        workerBox = new LinearLayout(this);
        workerBox.setOrientation(LinearLayout.VERTICAL);
        workerBox.setPadding(dp(8), dp(4), dp(8), dp(4));
        mid.addView(workerBox);
        todayTv = new TextView(this);
        todayTv.setTextSize(12);
        todayTv.setTextColor(Color.rgb(40, 60, 80));
        todayTv.setPadding(dp(12), dp(8), dp(12), dp(16));
        mid.addView(todayTv);
        sc.addView(mid);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        reloadWorkers();
        refreshToday();
        main.post(faceTicker);
    }

    private TextView action(String t, View.OnClickListener l) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(Color.rgb(15, 76, 129));
        tv.setPadding(dp(8), dp(10), dp(8), dp(10));
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(2), 0, dp(2), 0);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(l);
        return tv;
    }

    private TextView chip(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(Color.rgb(15, 76, 129));
        tv.setBackgroundColor(Color.rgb(220, 235, 250));
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(3), 0, dp(3), 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    private final Runnable faceTicker = new Runnable() {
        @Override public void run() {
            boolean ok = faceOk && (System.currentTimeMillis() - lastFaceMs) < 1000;
            statusTv.setText(ok ? "FACE OK · tap worker · " + punchType : "No face · use Photo backup");
            statusTv.setBackgroundColor(ok ? Color.argb(170, 20, 140, 80) : Color.argb(140, 0, 0, 0));
            main.postDelayed(this, 350);
        }
    };

    private void reloadWorkers() {
        workerBox.removeAllViews();
        List<JSONObject> list = db.listWorkers();
        int n = 0;
        for (JSONObject w : list) {
            String sh = w.optString("shift", "");
            if (!"ALL".equals(shiftFilter) && sh.length() > 0
                    && !sh.equalsIgnoreCase(shiftFilter)
                    && !sh.toUpperCase(Locale.US).contains(shiftFilter.toUpperCase(Locale.US))) continue;
            n++;
            final long id = w.optLong("id");
            final String name = w.optString("name");
            TextView row = new TextView(this);
            row.setText((w.optString("photo_path", "").length() > 4 ? "📷 " : "👤 ") + name + " · " + w.optString("code") + " · " + sh);
            row.setTextSize(15);
            row.setPadding(dp(12), dp(14), dp(12), dp(14));
            row.setBackgroundColor(Color.WHITE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(6);
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> onWorkerTap(id, name));
            workerBox.addView(row);
        }
        if (n == 0) {
            TextView e = new TextView(this);
            e.setText("No workers. Tap Workers to add.");
            e.setPadding(dp(8), dp(12), dp(8), dp(8));
            workerBox.addView(e);
        }
    }

    private void onWorkerTap(long workerId, String name) {
        if (punching) return;
        if (db.hasRecentPunch(workerId, punchType, DUP_MINUTES)) {
            toast("Already " + punchType + " in last " + DUP_MINUTES + " min");
            return;
        }
        boolean ok = faceOk && (System.currentTimeMillis() - lastFaceMs) < 1500;
        if (ok) {
            new AlertDialog.Builder(this)
                    .setTitle(punchType + " · Face")
                    .setMessage(name + "\nConfirm face punch?")
                    .setPositiveButton("Confirm", (d, w) -> completePunch(workerId, name, "face", null))
                    .setNegativeButton("Cancel", null).show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Face not detected")
                    .setMessage(name + "\nUse PHOTO backup?")
                    .setPositiveButton("Photo " + punchType, (d, w) -> captureAndPunch(workerId, name))
                    .setNegativeButton("Cancel", null).show();
        }
    }

    private void openPhotoFallback() {
        List<JSONObject> list = db.listWorkers();
        List<String> names = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (JSONObject w : list) {
            String sh = w.optString("shift", "");
            if (!"ALL".equals(shiftFilter) && sh.length() > 0
                    && !sh.equalsIgnoreCase(shiftFilter)
                    && !sh.toUpperCase(Locale.US).contains(shiftFilter.toUpperCase(Locale.US))) continue;
            names.add(w.optString("name"));
            ids.add(w.optLong("id"));
        }
        if (names.isEmpty()) { toast("No workers"); return; }
        String[] arr = names.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Photo backup · " + punchType)
                .setItems(arr, (d, which) -> {
                    if (db.hasRecentPunch(ids.get(which), punchType, DUP_MINUTES)) {
                        toast("Already punched recently");
                        return;
                    }
                    captureAndPunch(ids.get(which), arr[which]);
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void captureAndPunch(long workerId, String name) {
        String path = saveLastFrameFile();
        if (path == null) { toast("Capture failed"); return; }
        completePunch(workerId, name, "photo", path);
    }

    private void completePunch(long workerId, String name, String method, String photoPath) {
        punching = true;
        try {
            if (photoPath == null) photoPath = saveLastFrameFile();
            String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            String user = db.setting("current_user", db.setting("duty_name", "User"));
            String device = db.setting("device_code", "-");
            long id = db.addAttendance(workerId, name, day, time, punchType, method,
                    photoPath == null ? "" : photoPath, user, device, "");
            if (id <= 0) { toast("Save failed"); return; }
            try {
                JSONObject o = new JSONObject();
                o.put("worker_id", workerId);
                o.put("worker_name", name);
                o.put("day", day);
                o.put("time", time);
                o.put("punch", punchType);
                o.put("method", method);
                o.put("by_user", user);
                o.put("device_code", device);
                if ("client".equals(db.setting("sync_mode", "off"))) {
                    SyncHub.get(this).enqueue("attendance", o);
                    SyncHub.get(this).flushQueue(null);
                }
            } catch (Exception ignored) {}
            toast(punchType + " OK · " + name + " · " + method);
            refreshToday();
        } finally {
            punching = false;
        }
    }

    private String saveLastFrameFile() {
        try {
            if (lastFrame == null || previewW <= 0) return null;
            YuvImage yuv = new YuvImage(lastFrame, ImageFormat.NV21, previewW, previewH, null);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, previewW, previewH), 88, bos);
            File dir = new File(getFilesDir(), "attendance");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "att_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(bos.toByteArray());
            fos.close();
            return f.getAbsolutePath();
        } catch (Exception e) { return null; }
    }

    private void manageWorkers() {
        final EditText name = new EditText(this);
        name.setHint("Worker name *");
        final EditText code = new EditText(this);
        code.setHint("Code / ID");
        final EditText shift = new EditText(this);
        shift.setHint("Shift A/B/C/General");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        box.addView(name); box.addView(code); box.addView(shift);
        new AlertDialog.Builder(this).setTitle("Add worker").setView(box)
                .setPositiveButton("Add", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) { toast("Name required"); return; }
                    String path = saveLastFrameFile();
                    db.addWorker(code.getText().toString().trim(), n, shift.getText().toString().trim(), path == null ? "" : path);
                    reloadWorkers();
                    toast("Worker added");
                })
                .setNegativeButton("Close", null).show();
    }

    private void refreshToday() {
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        List<JSONObject> rows = db.listAttendance(day);
        StringBuilder sb = new StringBuilder("Today " + day + " (" + rows.size() + ")\n");
        for (int i = 0; i < Math.min(rows.size(), 40); i++) {
            JSONObject r = rows.get(i);
            sb.append(r.optString("time")).append(" ")
                    .append(r.optString("punch")).append(" ")
                    .append(r.optString("worker_name")).append(" [")
                    .append(r.optString("method")).append("]\n");
        }
        if (rows.isEmpty()) sb.append("No punches yet.");
        todayTv.setText(sb.toString());
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera = Camera.open();
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(holder);
            Camera.Size sz = camera.getParameters().getPreviewSize();
            previewW = sz.width; previewH = sz.height;
            camera.setPreviewCallback(this);
            camera.startPreview();
        } catch (Exception e) { statusTv.setText("Camera: " + e.getMessage()); }
    }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int ht) {}
    @Override public void surfaceDestroyed(SurfaceHolder h) {
        if (camera != null) {
            try { camera.setPreviewCallback(null); camera.stopPreview(); camera.release(); } catch (Exception ignored) {}
            camera = null;
        }
    }
    @Override public void onPreviewFrame(byte[] data, Camera cam) {
        lastFrame = data;
        if (detector == null || data == null || previewW <= 0) return;
        try {
            InputImage image = InputImage.fromByteArray(data, previewW, previewH, 90, InputImage.IMAGE_FORMAT_NV21);
            detector.process(image)
                    .addOnSuccessListener(faces -> {
                        if (faces != null && !faces.isEmpty()) { faceOk = true; lastFaceMs = System.currentTimeMillis(); }
                        else faceOk = false;
                    })
                    .addOnFailureListener(e -> faceOk = false);
        } catch (Exception ignored) {}
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    @Override protected void onDestroy() {
        main.removeCallbacks(faceTicker);
        if (detector != null) try { detector.close(); } catch (Exception ignored) {}
        surfaceDestroyed(null);
        super.onDestroy();
    }
    @Override public void onRequestPermissionsResult(int code, String[] p, int[] g) {
        if (code == 301 && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) recreate();
    }
}
