package com.rmstock.nativeapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.os.ParcelFileDescriptor;
import android.os.CancellationSignal;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
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
    private final java.util.HashMap<Long,float[]> embedCache = new java.util.HashMap<>();
    private long lastAutoMatchId = -1;
    private float lastAutoScore = 0f; private volatile boolean eyeLiveness;
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
        db = StockDb.get(this);
        db.ensureAttendanceTables();
        try{ FaceNetEngine.get(this); }catch(Throwable ignored){}
        detector = FaceDetection.getClient(new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).build());
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
        row.addView(action("Print", v -> printAttendanceSheet()));
        root.addView(row);
        LinearLayout shifts = new LinearLayout(this);
        shifts.setPadding(dp(6), dp(4), dp(6), dp(4));
        for (String sh : new String[]{"ALL", "A", "B", "C", "General"}) {
            final String f = sh;
            TextView t = chip(sh);
            t.setOnClickListener(v -> { shiftFilter = f; reloadWorkers();
        try {
            if (FaceNetEngine.get(this).isReady()) toast("MobileFaceNet ON (offline)");
            else toast("FaceNet model missing — basic match");
        } catch (Throwable ignored) {} });
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
        try {
            int miss = 0;
            for (JSONObject w : db.listWorkers()) {
                if (!FaceMatch.fileOk(w.optString("photo_path"))) miss++;
            }
            boolean net = false;
            try { net = FaceNetEngine.get(this).isReady();
            if (!net && !FaceNetEngine.modelPresent(this)) { /* no tflite in APK */ } } catch (Throwable ignored) {}
            if (miss > 0) toast(miss + " workers need face photo (Workers)");
            statusTv.setText((net ? "MobileFaceNet ON · " : "Basic match · ") + "missing faces: " + miss);
        } catch (Exception ignored) {}
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
            statusTv.setText(ok && eyeLiveness ? "FACE+LIVE · "+punchType : (ok ? "Eyes? retry/Photo" : "No face · Photo"));
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
        boolean ok = faceOk && eyeLiveness && (System.currentTimeMillis() - lastFaceMs) < 1500;
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
            double[] loc = lastKnownLatLng();
            int late = 0;
            if ("IN".equals(punchType)) {
                String cutoff = db.setting("att_late_after", "09:15");
                try {
                    if (time.length() >= 5 && time.substring(0, 5).compareTo(cutoff) > 0) late = 1;
                } catch (Exception ignored) {}
            }
            long id = db.addAttendance(workerId, name, day, time, punchType, method,
                    photoPath == null ? "" : photoPath, user, device,
                    late == 1 ? "LATE" : "", loc[0], loc[1], late);
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

    
    private double[] lastKnownLatLng() {
        double[] out = new double[]{0, 0};
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) return out;
            android.location.LocationManager lm =
                    (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
            android.location.Location loc = lm != null
                    ? lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER) : null;
            if (loc == null && lm != null)
                loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
            if (loc != null) { out[0] = loc.getLatitude(); out[1] = loc.getLongitude(); }
        } catch (Exception ignored) {}
        return out;
    }
    private void showAbsent() {
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        java.util.List<JSONObject> abs = db.listAbsentWorkers(day);
        StringBuilder sb = new StringBuilder("Absent (" + abs.size() + ")\n");
        for (JSONObject w : abs)
            sb.append("• ").append(w.optString("name")).append("\n");
        if (abs.isEmpty()) sb.append("All present (IN).");
        new AlertDialog.Builder(this).setTitle("Absent today").setMessage(sb.toString())
                .setPositiveButton("OK", null).show();
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
        name.setHint("Worker full name *");
        final EditText code = new EditText(this);
        code.setHint("ID / Code *");
        final EditText shift = new EditText(this);
        shift.setHint("Shift A/B/C/General");
        final EditText gender = new EditText(this);
        gender.setHint("Gender: Male / Female");
        TextView tip = new TextView(this);
        tip.setText("Face the camera, then tap Save face. Reference photo is required for match.");
        tip.setTextColor(Color.rgb(80, 90, 100));
        tip.setPadding(0, dp(6), 0, dp(6));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        box.addView(tip);
        box.addView(name); box.addView(code); box.addView(shift); box.addView(gender);
        new AlertDialog.Builder(this).setTitle("Enroll worker + face")
                .setView(box)
                .setPositiveButton("Save face & worker", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) { toast("Name required"); return; }
                    if (!(faceOk && (System.currentTimeMillis() - lastFaceMs) < 2000)) {
                        toast("No face — stand in front of camera");
                        return;
                    }
                    String path = saveLastFrameFile();
                    if (path == null || path.isEmpty()) {
                        toast("Could not save face photo");
                        return;
                    }
                    long id = db.addWorker(code.getText().toString().trim(), n,
                            shift.getText().toString().trim(), path, gender.getText().toString().trim());
                    embedCache.put(id, FaceMatch.embedFile(path));
                    reloadWorkers();
                    toast("Enrolled: " + n + " (face saved)");
                })
                .setNeutralButton("List / re-photo", (d, w) -> showWorkerFaceList())
                .setNegativeButton("Close", null).show();
    }

    private void showWorkerFaceList() {
        java.util.List<JSONObject> list = db.listWorkers();
        StringBuilder sb = new StringBuilder();
        for (JSONObject w : list) {
            boolean has = FaceMatch.fileOk(w.optString("photo_path"));
            sb.append(has ? "✓ " : "✗ ")
              .append(w.optString("name")).append(" (")
              .append(w.optString("code")).append(")\n");
        }
        new AlertDialog.Builder(this).setTitle("Face enrolled?")
                .setMessage(sb.length() == 0 ? "No workers" : sb.toString())
                .setPositiveButton("Re-photo selected…", (d, w) -> rePhotoWorker())
                .setNegativeButton("OK", null).show();
    }

    private void rePhotoWorker() {
        java.util.List<JSONObject> list = db.listWorkers();
        if (list.isEmpty()) { toast("No workers"); return; }
        String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++)
            names[i] = list.get(i).optString("name") + " · " + list.get(i).optString("code");
        new AlertDialog.Builder(this).setTitle("Update face photo")
                .setItems(names, (d, which) -> {
                    if (!(faceOk && (System.currentTimeMillis() - lastFaceMs) < 2000)) {
                        toast("Show face to camera first");
                        return;
                    }
                    JSONObject w = list.get(which);
                    String path = saveLastFrameFile();
                    if (path == null) { toast("Save failed"); return; }
                    db.updateWorkerPhoto(w.optLong("id"), path);
                    embedCache.put(w.optLong("id"), FaceMatch.embedFile(path));
                    reloadWorkers();
                    toast("Face updated: " + w.optString("name"));
                }).setNegativeButton("Cancel", null).show();
    }

    /** Rank enrolled workers by live frame similarity; auto-punch if very high. */
    private long lastMatchAt;
    private void runFaceMatch() {
        if (lastFrame == null || previewW <= 0 || !faceOk) return;
        if (System.currentTimeMillis() - lastMatchAt < 1200) return;
        lastMatchAt = System.currentTimeMillis();
        final byte[] frame = lastFrame;
        final int w = previewW, h = previewH;
        AppExec.io(() -> runFaceMatchBg(frame, w, h));
    }
    private void runFaceMatchBg(byte[] frame, int previewW, int previewH) {
        if (frame == null || previewW <= 0) return;
        android.graphics.Bitmap live = FaceMatch.nv21ToBitmap(frame, previewW, previewH);
        if (live == null) return;
        boolean useNet = FaceNetEngine.get(this).isReady();
        float[] liveE = useNet ? null : FaceMatch.embedBitmap(live);
        if (liveE == null) return;
        long bestId = -1;
        String bestName = null;
        float best = 0f;
        for (JSONObject w : db.listWorkers()) {
            long id = w.optLong("id");
            String path = w.optString("photo_path");
            if (!FaceMatch.fileOk(path)) continue;
            float[] ref = embedCache.get(id);
            if (ref == null) {
                ref = FaceMatch.embedFile(path);
                if (ref != null) embedCache.put(id, ref);
            }
            if (ref == null) continue;
            float sc;
            if (useNet) {
                android.graphics.Bitmap refBmp = android.graphics.BitmapFactory.decodeFile(path);
                sc = FaceNetEngine.get(this).compare(live, refBmp);
            } else {
                sc = FaceMatch.cosine(liveE, ref);
            }
            if (sc > best) {
                best = sc;
                bestId = id;
                bestName = w.optString("name");
            }
        }
        lastAutoMatchId = bestId;
        lastAutoScore = best;
        if (bestId > 0 && best >= FaceNetEngine.SUGGEST && bestName != null) {
            final long fid = bestId;
            final String fn = bestName;
            final float fs = best;
            main.post(() -> {
                statusTv.setText(String.format(Locale.US,
                        "MATCH %s %.0f%% · %s", fn, fs * 100f, punchType));
                if (fs >= FaceNetEngine.THRESHOLD && !punching) {
                    if (db.hasRecentPunch(fid, punchType, DUP_MINUTES)) return;
                    new AlertDialog.Builder(this)
                            .setTitle("Face match")
                            .setMessage(fn + "\nScore " + Math.round(fs * 100) + "%\nConfirm " + punchType + "?")
                            .setPositiveButton(punchType, (d, w) -> completePunch(fid, fn, "face-match", null))
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });
        }
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
        int[] st = db.attendanceGenderStats(day);
        sb.append("\n────────────\n");
        sb.append("Total (IN): ").append(st[0]);
        sb.append("   ছেলে: ").append(st[1]);
        sb.append("   মেয়ে: ").append(st[2]);
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
    
    private void printAttendanceSheet() {
        final String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        File pdf = buildAttendancePdf(day);
        if (pdf == null || !pdf.exists()) { toast("PDF failed"); return; }
        new AlertDialog.Builder(this)
                .setTitle("Attendance sheet")
                .setMessage(day + "\nPrint or share for boss.")
                .setPositiveButton("Print", (d, w) -> doPrint(pdf, day))
                .setNeutralButton("Share", (d, w) -> sharePdf(pdf, day))
                .setNegativeButton("Close", null)
                .show();
    }

    private File buildAttendancePdf(String day) {
        try {
            String company = db.setting("company", "Company");
            java.util.List<JSONObject> rows;
            try { rows = db.attendanceReportRows(day); }
            catch (Throwable t) {
                rows = new java.util.ArrayList<>();
                for (JSONObject r : db.listAttendance(day)) {
                    JSONObject o = new JSONObject();
                    o.put("name", r.optString("worker_name"));
                    o.put("code", "");
                    o.put("shift", "");
                    o.put("gender", "");
                    o.put("in_time", "IN".equalsIgnoreCase(r.optString("punch")) ? r.optString("time") : "");
                    o.put("out_time", "OUT".equalsIgnoreCase(r.optString("punch")) ? r.optString("time") : "");
                    rows.add(o);
                }
            }
            int[] st;
            try { st = db.attendanceGenderStats(day); }
            catch (Throwable t) { st = new int[]{rows.size(), 0, 0}; }
            int lateN = 0, absN = 0;
            try { lateN = db.countLate(day); } catch (Throwable ignored) {}
            try { absN = db.listAbsentWorkers(day).size(); } catch (Throwable ignored) {}

            PdfDocument doc = new PdfDocument();
            Paint title = new Paint();
            title.setTextSize(14);
            title.setFakeBoldText(true);
            title.setColor(Color.rgb(15, 40, 70));
            Paint head = new Paint();
            head.setTextSize(9);
            head.setFakeBoldText(true);
            head.setColor(Color.rgb(20, 40, 60));
            Paint cell = new Paint();
            cell.setTextSize(8);
            cell.setColor(Color.rgb(30, 40, 50));
            Paint line = new Paint();
            line.setColor(Color.rgb(180, 190, 200));
            line.setStrokeWidth(1f);

            int pageW = 595, pageH = 842, margin = 36, pageNo = 1;
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create();
            PdfDocument.Page page = doc.startPage(info);
            android.graphics.Canvas c = page.getCanvas();
            int y = margin;
            c.drawText(company, margin, y, title); y += 18;
            c.drawText("Attendance Report (Hajira Sheet)", margin, y, title); y += 14;
            c.drawText("Date: " + day + "  Prepared: " +
                    new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()), margin, y, cell);
            y += 16;
            c.drawLine(margin, y, pageW - margin, y, line); y += 14;

            String[] headers = {"#", "Code", "Name", "Shift", "Gender", "IN", "OUT"};
            float[] xs = {margin, margin + 28, margin + 70, margin + 220, margin + 268, margin + 320, margin + 390};
            for (int i = 0; i < headers.length; i++) c.drawText(headers[i], xs[i], y, head);
            y += 6;
            c.drawLine(margin, y, pageW - margin, y, line); y += 12;

            int n = 0;
            for (JSONObject r : rows) {
                if (y > pageH - 90) {
                    c.drawText("Continued...", margin, pageH - 28, cell);
                    doc.finishPage(page);
                    pageNo++;
                    info = new PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create();
                    page = doc.startPage(info);
                    c = page.getCanvas();
                    y = margin;
                    for (int i = 0; i < headers.length; i++) c.drawText(headers[i], xs[i], y, head);
                    y += 12;
                }
                n++;
                String name = r.optString("name");
                if (name.length() > 22) name = name.substring(0, 21) + ".";
                String g = r.optString("gender");
                if (g.length() > 8) g = g.substring(0, 8);
                c.drawText(String.valueOf(n), xs[0], y, cell);
                c.drawText(r.optString("code"), xs[1], y, cell);
                c.drawText(name, xs[2], y, cell);
                c.drawText(r.optString("shift"), xs[3], y, cell);
                c.drawText(g, xs[4], y, cell);
                c.drawText(r.optString("in_time"), xs[5], y, cell);
                c.drawText(r.optString("out_time"), xs[6], y, cell);
                y += 13;
            }
            y += 10;
            c.drawLine(margin, y, pageW - margin, y, line); y += 16;
            Paint foot = new Paint();
            foot.setTextSize(10);
            foot.setFakeBoldText(true);
            foot.setColor(Color.rgb(15, 40, 70));
            c.drawText("Total(IN): " + st[0] + "  Male: " + st[1] + "  Female: " + st[2]
                    + "  Late: " + lateN + "  Absent: " + absN, margin, y, foot);
            y += 20;
            c.drawText("Supervisor: _______________     Admin/Boss: _______________", margin, y, cell);
            y += 24;
            c.drawText("Inventory Management App", margin, y, cell);
            doc.finishPage(page);

            File dir = new File(getExternalFilesDir(null), "reports");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "Attendance_" + day + ".pdf");
            FileOutputStream fos = new FileOutputStream(out);
            doc.writeTo(fos);
            fos.close();
            doc.close();
            return out;
        } catch (Exception e) {
            toast("PDF: " + e.getMessage());
            return null;
        }
    }

    private void doPrint(final File pdf, final String day) {
        EasyPrint.printPdf(this, pdf, "Attendance-" + day);
        if (true) return;
        // legacy below
        try {
            PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
            if (pm == null) { sharePdf(pdf, day); return; }
            pm.print("Attendance-" + day, new PrintDocumentAdapter() {
                @Override public void onLayout(PrintAttributes oldAttr, PrintAttributes newAttr,
                        CancellationSignal signal, LayoutResultCallback cb, android.os.Bundle extras) {
                    if (signal.isCanceled()) { cb.onLayoutCancelled(); return; }
                    cb.onLayoutFinished(new PrintDocumentInfo.Builder("Attendance_" + day + ".pdf")
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), true);
                }
                @Override public void onWrite(android.print.PageRange[] pages, ParcelFileDescriptor dest,
                        CancellationSignal signal, WriteResultCallback cb) {
                    try {
                        InputStream in = new FileInputStream(pdf);
                        OutputStream out = new FileOutputStream(dest.getFileDescriptor());
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        in.close(); out.close();
                        cb.onWriteFinished(new android.print.PageRange[]{android.print.PageRange.ALL_PAGES});
                    } catch (Exception e) { cb.onWriteFailed(e.getMessage()); }
                }
            }, null);
        } catch (Exception e) {
            toast(e.getMessage());
            sharePdf(pdf, day);
        }
    }

    private void sharePdf(File pdf, String day) {
        try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, "com.rmstock.nativeapp.fileprovider", pdf);
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
            i.setType("application/pdf");
            i.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            i.putExtra(android.content.Intent.EXTRA_SUBJECT, "Attendance " + day);
            i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(i, "Share attendance PDF"));
        } catch (Exception e) { toast("Share: " + e.getMessage()); }
    }

    


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
