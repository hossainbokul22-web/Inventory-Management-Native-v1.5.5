package com.rmstock.nativeapp;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Call UI + soft ring. WebRTC media path can be expanded when dependency is stable. */
public class CallActivity extends Activity {
    public static final String EXTRA_VIDEO = "video";
    public static final String EXTRA_OUTGOING = "outgoing";
    public static final String EXTRA_PEER = "peer";

    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean ended;
    private TextView status;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean video = getIntent().getBooleanExtra(EXTRA_VIDEO, false);
        boolean outgoing = getIntent().getBooleanExtra(EXTRA_OUTGOING, true);
        String peer = getIntent().getStringExtra(EXTRA_PEER);
        if (peer == null) peer = "Team";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(17, 27, 33));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(48, 80, 48, 48);

        TextView title = new TextView(this);
        title.setText(video ? "📹 Video call" : "📞 Voice call");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView peerTv = new TextView(this);
        peerTv.setText(peer);
        peerTv.setTextColor(Color.rgb(180, 200, 210));
        peerTv.setTextSize(16);
        peerTv.setGravity(Gravity.CENTER);
        peerTv.setPadding(0, 24, 0, 24);
        root.addView(peerTv);

        status = new TextView(this);
        status.setText(outgoing ? "Calling… (hotspot)" : "Incoming…");
        status.setTextColor(Color.WHITE);
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        TextView end = new TextView(this);
        end.setText("✕ End");
        end.setTextColor(Color.WHITE);
        end.setTextSize(16);
        end.setPadding(64, 28, 64, 28);
        end.setBackgroundColor(Color.rgb(220, 50, 50));
        end.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = 80;
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        end.setOnClickListener(v -> hangup());
        root.addView(end, lp);

        setContentView(root);
        SoftSounds.startCallRing();
        // Signal invite over sync
        try {
            org.json.JSONObject data = new org.json.JSONObject();
            data.put("kind", outgoing ? "invite" : "ringing");
            data.put("video", video);
            data.put("from", new StockDb(this).setting("duty_name", "User"));
            data.put("ts", System.currentTimeMillis());
            SyncHub.get(this).enqueue("call_signal", data);
            if ("server".equals(new StockDb(this).setting("sync_mode", "off")))
                SyncHub.get(this).storeCallSignal(data);
            else
                SyncHub.get(this).flushQueue(null);
        } catch (Exception ignored) {}

        main.postDelayed(() -> {
            if (!ended) {
                SoftSounds.callConnected();
                status.setText("Connected (signaling) — full WebRTC media next build");
            }
        }, 2500);
    }

    private void hangup() {
        if (ended) return;
        ended = true;
        SoftSounds.callEnded();
        try {
            org.json.JSONObject data = new org.json.JSONObject();
            data.put("kind", "end");
            data.put("ts", System.currentTimeMillis());
            SyncHub.get(this).enqueue("call_signal", data);
            SyncHub.get(this).storeCallSignal(data);
        } catch (Exception ignored) {}
        finish();
    }

    @Override public void onBackPressed() { hangup(); }

    @Override protected void onDestroy() {
        ended = true;
        SoftSounds.stopCallRing();
        super.onDestroy();
    }
}
