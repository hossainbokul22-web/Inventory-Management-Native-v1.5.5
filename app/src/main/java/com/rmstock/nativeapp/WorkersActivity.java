package com.rmstock.nativeapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/** Worker list + gallery photo upload. No camera — survives gallery pick. */
public class WorkersActivity extends Activity {
    private static final int REQ_PHOTO = 4201;
    private StockDb db;
    private LinearLayout listBox;
    private long pendingId = -1;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = StockDb.get(this);
        ScrollView sc = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.setBackgroundColor(Color.rgb(245, 247, 250));

        TextView title = new TextView(this);
        title.setText("Workers");
        title.setTextSize(20);
        title.setTextColor(Color.rgb(20, 40, 70));
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView tip = new TextView(this);
        tip.setText("Admin & Supervisor can add workers and upload photo from Gallery.\nAttendance camera is separate — open Attendance only to punch.");
        tip.setTextSize(12);
        tip.setTextColor(Color.rgb(90, 100, 110));
        tip.setPadding(0, 0, 0, dp(10));
        root.addView(tip);

        Button add = new Button(this);
        add.setText("＋ Add worker");
        add.setOnClickListener(v -> showAddDialog());
        root.addView(add);

        Button refresh = new Button(this);
        refresh.setText("Refresh list");
        refresh.setOnClickListener(v -> reload());
        root.addView(refresh);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(0, dp(8), 0, 0);
        root.addView(listBox);

        Button back = new Button(this);
        back.setText("← Back");
        back.setOnClickListener(v -> finish());
        root.addView(back);

        sc.addView(root);
        setContentView(sc);
        reload();
    }

    private void reload() {
        listBox.removeAllViews();
        List<JSONObject> list;
        try { list = db.listWorkers(); } catch (Exception e) { list = null; }
        if (list == null || list.isEmpty()) {
            TextView e = new TextView(this);
            e.setText("No workers yet. Tap Add worker.");
            e.setTextColor(Color.GRAY);
            listBox.addView(e);
            return;
        }
        for (JSONObject w : list) {
            final long id = w.optLong("id");
            final String name = w.optString("name");
            final String code = w.optString("code");
            boolean has = FaceMatch.fileOk(w.optString("photo_path"));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(10), dp(10), dp(10), dp(10));
            row.setBackgroundColor(Color.WHITE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);

            TextView t = new TextView(this);
            t.setText((has ? "📷 " : "⚠ NO PHOTO · ") + name + "  ·  " + code
                    + "\nShift: " + w.optString("shift") + "  ·  " + w.optString("gender"));
            t.setTextSize(14);
            t.setTextColor(Color.BLACK);
            row.addView(t);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button up = new Button(this);
            up.setText(has ? "Change photo" : "Upload photo");
            up.setOnClickListener(v -> {
                pendingId = id;
                openGallery();
            });
            actions.addView(up);
            row.addView(actions);
            listBox.addView(row);
        }
    }

    private void showAddDialog() {
        final EditText name = new EditText(this);
        name.setHint("Full name *");
        final EditText code = new EditText(this);
        code.setHint("ID / Code *");
        final EditText shift = new EditText(this);
        shift.setHint("Shift A/B/C/General");
        final EditText gender = new EditText(this);
        gender.setHint("Male / Female");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        box.addView(name); box.addView(code); box.addView(shift); box.addView(gender);
        new AlertDialog.Builder(this)
                .setTitle("Add worker")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) { toast("Name required"); return; }
                    long id = db.addWorker(
                            code.getText().toString().trim(), n,
                            shift.getText().toString().trim(),
                            "",
                            gender.getText().toString().trim());
                    try {
                        org.json.JSONObject payload = new org.json.JSONObject();
                        payload.put("worker_id", id);
                        payload.put("code", code.getText().toString().trim());
                        payload.put("name", n);
                        payload.put("shift", shift.getText().toString().trim());
                        payload.put("gender", gender.getText().toString().trim());
                        payload.put("photo_path", "");
                        SyncHub.get(this).enqueueAndFlush("worker_upsert", payload);
                        
                    } catch (Exception ignored) {}
                    reload();
                    toast("Saved. Now Upload photo for this worker.");
                    pendingId = id;
                    openGallery();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openGallery() {
        try {
            Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            i.setType("image/*");
            startActivityForResult(i, REQ_PHOTO);
        } catch (Exception e) {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i, "Select photo"), REQ_PHOTO);
        }
    }

    private String copyUri(Uri uri) {
        if (uri == null) return null;
        try {
            File dir = new File(getFilesDir(), "attendance");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "worker_" + System.currentTimeMillis() + ".jpg");
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            OutputStream out = new FileOutputStream(f);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();
            return f.getAbsolutePath();
        } catch (Exception e) { return null; }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PHOTO) return;
        if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingId > 0) {
            String path = copyUri(data.getData());
            if (path != null) {
                db.updateWorkerPhoto(pendingId, path);
                try {
                    org.json.JSONObject payload = new org.json.JSONObject();
                    payload.put("worker_id", pendingId);
                    payload.put("photo_path", path);
                    for (JSONObject w : db.listWorkers()) {
                        if (w.optLong("id") == pendingId) {
                            payload.put("code", w.optString("code"));
                            payload.put("name", w.optString("name"));
                            payload.put("shift", w.optString("shift"));
                            payload.put("gender", w.optString("gender"));
                            break;
                        }
                    }
                    SyncHub.get(this).enqueueAndFlush("worker_upsert", payload);
                    
                } catch (Exception ignored) {}
                toast("Photo saved");
                reload();
            } else toast("Photo copy failed");
        }
        pendingId = -1;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
