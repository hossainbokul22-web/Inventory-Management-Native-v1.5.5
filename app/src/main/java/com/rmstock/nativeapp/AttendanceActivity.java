package com.rmstock.nativeapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
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
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Dual attendance:
 * 1) FACE path — ML Kit must see a face, then pick worker (photo proof saved)
 * 2) PHOTO fallback — if face fails or user chooses, worker + photo only
 */
public class AttendanceActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private StockDb db;
    private Camera camera;
    private SurfaceView preview;
    private TextView statusTv, modeTv;
    private FaceDetector detector;
    private volatile boolean faceOk;
    private volatile long lastFaceMs;
    private boolean punching;
    private String punchType = "IN";
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout workerBox;
    private byte[] lastFrame;
    private int previewW, previewH;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        db = new StockDb(this);
        db.ensureAttendanceTables();
        FaceDetectorOptions opts = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();
        detector = FaceDetection.getClient(opts);

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
        title.setText("Attendance · Face + Photo fallback");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setPadding(dp(16), dp(14), dp(16), dp(14));
        title.setBackgroundColor(Color.rgb(15, 76, 129));
        root.addView(title);

        modeTv = new TextView(this);
        modeTv.setText("Mode: FACE (if face not found → use Photo)");
        modeTv.setPadding(dp(12), dp(8), dp(12), dp(4));
        modeTv.setTextColor(Color.rgb(20, 40, 60));
        root.addView(modeTv);

        FrameLayout stage = new FrameLayout(this);
        preview = new SurfaceView(this);
        preview.getHolder().addCallback(this);
        stage.addView(preview, new FrameLayout.LayoutParams(-1, dp(280)));
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
        row.setPadding(dp(8), dp(8), dp(8), dp(4));
        row.addView(btn("IN", v -> { punchType = "IN"; toast("Punch IN"); }));
        row.addView(btn("OUT", v -> { punchType = "OUT"; toast("Punch OUT"); }));
        row.addView(btn("Photo fallback", v -> openPhotoFallback()));
        row.addView(btn("Workers", v -> manageWorkers()));
        root.addView(row);

        TextView hint = new TextView(this);
        hint.setText("Face path: face detected → tap worker. Photo path: if face fails.");
        hint.setPadding(dp(12), dp(4), dp(12), dp(4));
        hint.setTextSize(12);
        hint.setTextColor(Color.GRAY);
        root.addView(hint);

        ScrollView sc = new ScrollView(this);
        workerBox = new LinearLayout(this);
        workerBox.setOrientation(LinearLayout.VERTICAL);
        workerBox.setPadding(dp(8), dp(4), dp(8), dp(8));
        sc.addView(workerBox);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        reloadWorkers();
        main.post(faceTicker);
    }

    private final Runnable faceTicker = new Runnable() {
        @Override public void run() {
            boolean ok = faceOk && (System.currentTimeMillis() - lastFaceMs) < 1200;
            statusTv.setText(ok
                    ? "FACE OK · select worker for " + punchType
                    : "No face · try again or Photo fallback");
            statusTv.setBackgroundColor(ok ? Color.argb(160, 20, 140, 80) : Color.argb(140, 0, 0, 0));
            main.postDelayed(this, 400);
        }
    };

    private TextView btn(String t, View.OnClickListener l) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(Color.rgb(15, 76, 129));
        tv.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        tv.setLayoutParams(lp);
        tv.setGravity(Gravity.CENTER);
        tv.setOnClickListener(l);
        return tv;
    }

    private void reloadWorkers() {
        workerBox.removeAllViews();
        List<JSONObject> list = db.listWorkers();
        if (list.isEmpty()) {
            TextView e = new TextView(this);
            e.setText("No workers — tap Workers to add (name + optional photo).");
            e.setPadding(dp(8), dp(12), dp(8), dp(8));
            workerBox.addView(e);
            return;
        }
        for (JSONObject w : list) {
            final long id = w.optLong("id");
            final String name = w.optString("name");
            TextView row = new TextView(this);
            row.setText("👤 " + name + " · " + w.optString("code") + " · " + w.optString("shift"));
            row.setTextSize(15);
            row.setPadding(dp(12), dp(14), dp(12), dp(14));
            row.setBackgroundColor(Color.WHITE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(6);
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> tryPunch(id, name));
            workerBox.addView(row);
        }
    }

    private void tryPunch(long workerId, String name) {
        if (punching) return;
        boolean ok = faceOk && (System.currentTimeMillis() - lastFaceMs) < 1500;
        if (ok) {
            completePunch(workerId, name, "face", null);
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Face not detected")
                    .setMessage("Use PHOTO fallback for " + name + "?")
                    .setPositiveButton("Photo punch", (d, w) -> captureAndPunch(workerId, name))
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void openPhotoFallback() {
        List<JSONObject> list = db.listWorkers();
        if (list.isEmpty()) { toast("Add workers first"); return; }
        String[] names = new String[list.size()];
        long[] ids = new long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            names[i] = list.get(i).optString("name");
            ids[i] = list.get(i).optLong("id");
        }
        new AlertDialog.Builder(this)
                .setTitle("Photo fallback · " + punchType)
                .setItems(names, (d, which) -> captureAndPunch(ids[which], names[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void captureAndPunch(long workerId, String name) {
        // Save last preview frame as proof
        String path = saveLastFrameFile();
        if (path == null) {
            toast("Could not capture — try again");
            return;
        }
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
            db.addAttendance(workerId, name, day, time, punchType, method,
                    photoPath == null ? "" : photoPath, user, device, "");
            try {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("worker_id", workerId);
                o.put("worker_name", name);
                o.put("day", day);
                o.put("time", time);
                o.put("punch", punchType);
                o.put("method", method);
                if ("client".equals(db.setting("sync_mode", "off"))) {
                    SyncHub.get(this).enqueue("attendance", o);
                    SyncHub.get(this).flushQueue(null);
                }
            } catch (Exception ignored) {}
            toast(punchType + " · " + name + " · via " + method);
            statusTv.setText("Saved " + punchType + " for " + name);
        } finally {
            punching = false;
        }
    }

    private String saveLastFrameFile() {
        try {
            byte[] data = lastFrame;
            if (data == null || previewW <= 0) return null;
            YuvImage yuv = new YuvImage(data, ImageFormat.NV21, previewW, previewH, null);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, previewW, previewH), 85, bos);
            File dir = new File(getFilesDir(), "attendance");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "att_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(bos.toByteArray());
            fos.close();
            return f.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private void manageWorkers() {
        final EditText name = new EditText(this);
        name.setHint("Worker name");
        final EditText code = new EditText(this);
        code.setHint("Code / ID");
        final EditText shift = new EditText(this);
        shift.setHint("Shift A/B/C/General");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        box.addView(name);
        box.addView(code);
        box.addView(shift);
        new AlertDialog.Builder(this)
                .setTitle("Add worker")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) { toast("Name required"); return; }
                    db.addWorker(code.getText().toString().trim(), n,
                            shift.getText().toString().trim(), "");
                    reloadWorkers();
                    toast("Worker added");
                })
                .setNeutralButton("Today list", (d, w) -> showToday())
                .setNegativeButton("Close", null)
                .show();
    }

    private void showToday() {
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        List<JSONObject> rows = db.listAttendance(day);
        StringBuilder sb = new StringBuilder();
        for (JSONObject r : rows) {
            sb.append(r.optString("time")).append(" ")
                    .append(r.optString("punch")).append(" ")
                    .append(r.optString("worker_name")).append(" [")
                    .append(r.optString("method")).append("]\n");
        }
        if (sb.length() == 0) sb.append("No punches today");
        new AlertDialog.Builder(this).setTitle("Today").setMessage(sb.toString())
                .setPositiveButton("OK", null).show();
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera = Camera.open();
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(holder);
            Camera.Parameters params = camera.getParameters();
            Camera.Size sz = params.getPreviewSize();
            previewW = sz.width;
            previewH = sz.height;
            camera.setPreviewCallback(this);
            camera.startPreview();
        } catch (Exception e) {
            statusTv.setText("Camera: " + e.getMessage());
        }
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        if (camera != null) {
            try {
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.release();
            } catch (Exception ignored) {}
            camera = null;
        }
    }

    @Override public void onPreviewFrame(byte[] data, Camera cam) {
        lastFrame = data;
        if (detector == null || data == null) return;
        try {
            InputImage image = InputImage.fromByteArray(
                    data, previewW, previewH, 90, InputImage.IMAGE_FORMAT_NV21);
            detector.process(image)
                    .addOnSuccessListener(faces -> {
                        if (faces != null && !faces.isEmpty()) {
                            faceOk = true;
                            lastFaceMs = System.currentTimeMillis();
                        } else {
                            faceOk = false;
                        }
                    })
                    .addOnFailureListener(e -> faceOk = false);
        } catch (Exception ignored) {}
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        main.removeCallbacks(faceTicker);
        if (detector != null) detector.close();
        surfaceDestroyed(null);
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] g) {
        if (code == 301 && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) {
            recreate();
        }
    }
}
