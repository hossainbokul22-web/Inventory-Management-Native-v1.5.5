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

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(20), dp(16), dp(20), dp(28));
        android.graphics.drawable.GradientDrawable dockBg = new android.graphics.drawable.GradientDrawable();
        dockBg.setColor(Color.argb(40, 255, 255, 255));
        dockBg.setCornerRadius(dp(28));
        dock.setBackground(dockBg);
        TextView muteHint = circleCtrl("🎙", Color.rgb(60, 80, 95), "Mic");
        muteHint.setOnClickListener(v -> SoftSounds.messageSent());
        TextView end = circleCtrl("⏻", Color.rgb(232, 72, 85), "End");
        end.setOnClickListener(v -> hangup());
        dock.addView(muteHint);
        dock.addView(new android.view.View(this), new LinearLayout.LayoutParams(dp(28), 1));
        dock.addView(end);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = dp(24);
        dlp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(dock, dlp);

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

    private TextView circleCtrl(String icon, int bg, String contentDesc) {
        TextView tv = new TextView(this);
        tv.setText(icon);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(22);
        tv.setGravity(Gravity.CENTER);
        int sz = dp(72);
        tv.setMinWidth(sz);
        tv.setMinHeight(sz);
        tv.setPadding(dp(16), dp(16), dp(16), dp(16));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(bg);
        tv.setBackground(g);
        tv.setContentDescription(contentDesc);
        return tv;
    }
    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
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
