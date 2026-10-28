package com.rmstock.nativeapp;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CallActivity extends Activity {
    public static final String EXTRA_VIDEO = "video";
    public static final String EXTRA_OUTGOING = "outgoing";
    public static final String EXTRA_PEER = "peer";

    private boolean videoCall;
    private boolean outgoing;
    private String me;
    private PeerConnectionFactory factory;
    private PeerConnection pc;
    private EglBase egl;
    private SurfaceViewRenderer localView, remoteView;
    private CameraVideoCapturer capturer;
    private AudioTrack localAudio;
    private VideoTrack localVideo;
    private boolean micOn = true;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView statusTv;
    private boolean ended;
    private boolean offerCreated;

    private final Runnable signalPoll = new Runnable() {
        @Override public void run() {
            if (ended) return;
            pollSignals();
            main.postDelayed(this, 600);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        videoCall = getIntent().getBooleanExtra(EXTRA_VIDEO, false);
        outgoing = getIntent().getBooleanExtra(EXTRA_OUTGOING, true);
        StockDb db = new StockDb(this);
        me = db.setting("duty_name", "");
        if (me.isEmpty()) me = db.setting("user_name", "User");
        if (me.isEmpty()) me = "User";

        if (!hasPerms()) {
            List<String> need = new ArrayList<>();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.RECORD_AUDIO);
            if (videoCall && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.CAMERA);
            if (!need.isEmpty())
                ActivityCompat.requestPermissions(this, need.toArray(new String[0]), 201);
        }

        buildUi();
        try {
            if (!hasPerms()) {
                setStatus("Allow Mic" + (videoCall ? " + Camera" : "") + " then retry");
                return;
            }
            initWebRtc();
            SoftSounds.startCallRing();
            if (outgoing) {
                setStatus("Calling…");
                publishSignal("invite", null);
                createOffer();
            } else setStatus("Incoming…");
            main.post(signalPoll);
        } catch (Throwable t) {
            setStatus("Error: " + t.getMessage());
            Toast.makeText(this, String.valueOf(t.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasPerms() {
        boolean ok = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        if (videoCall)
            ok = ok && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED;
        return ok;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(17, 27, 33));

        FrameLayout stage = new FrameLayout(this);
        remoteView = new SurfaceViewRenderer(this);
        localView = new SurfaceViewRenderer(this);
        stage.addView(remoteView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(110), dp(150));
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.setMargins(dp(12), dp(12), dp(12), dp(12));
        stage.addView(localView, lp);

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER_HORIZONTAL);
        overlay.setPadding(dp(16), dp(36), dp(16), dp(16));
        TextView title = new TextView(this);
        title.setText(videoCall ? "Video call" : "Voice call");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        overlay.addView(title);
        TextView peer = new TextView(this);
        peer.setText("Team");
        peer.setTextColor(Color.rgb(180, 200, 210));
        peer.setTextSize(15);
        peer.setGravity(Gravity.CENTER);
        peer.setPadding(0, dp(6), 0, dp(8));
        overlay.addView(peer);
        statusTv = new TextView(this);
        statusTv.setTextColor(Color.WHITE);
        statusTv.setTextSize(14);
        statusTv.setGravity(Gravity.CENTER);
        overlay.addView(statusTv);
        stage.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(20), dp(16), dp(20), dp(32));
        android.graphics.drawable.GradientDrawable dockBg = new android.graphics.drawable.GradientDrawable();
        dockBg.setColor(Color.argb(50, 255, 255, 255));
        dockBg.setCornerRadius(dp(28));
        dock.setBackground(dockBg);

        TextView mic = circleCtrl("MIC", Color.rgb(60, 80, 95));
        mic.setOnClickListener(v -> toggleMic(mic));
        TextView end = circleCtrl("END", Color.rgb(232, 72, 85));
        end.setOnClickListener(v -> hangup());
        dock.addView(mic);
        dock.addView(spacer(dp(28)));
        dock.addView(end);
        if (videoCall) {
            dock.addView(spacer(dp(28)));
            TextView cam = circleCtrl("CAM", Color.rgb(50, 120, 100));
            cam.setOnClickListener(v -> switchCamera());
            dock.addView(cam);
        }
        root.addView(dock);
        setContentView(root);
    }

    private View spacer(int w) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(w, 1));
        return v;
    }

    private TextView circleCtrl(String label, int bg) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        int sz = dp(68);
        tv.setMinWidth(sz);
        tv.setMinHeight(sz);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(bg);
        tv.setBackground(g);
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void setStatus(String s) {
        if (statusTv != null) main.post(() -> statusTv.setText(s));
    }

    private void toggleMic(TextView micBtn) {
        micOn = !micOn;
        if (localAudio != null) localAudio.setEnabled(micOn);
        micBtn.setText(micOn ? "MIC" : "MUTE");
        SoftSounds.messageSent();
    }

    private void switchCamera() {
        try { if (capturer != null) capturer.switchCamera(null); }
        catch (Exception e) { Toast.makeText(this, "Camera: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
    }

    private void initWebRtc() {
        egl = EglBase.create();
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(getApplicationContext())
                        .createInitializationOptions());
        factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(egl.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext()))
                .createPeerConnectionFactory();

        remoteView.init(egl.getEglBaseContext(), null);
        localView.init(egl.getEglBaseContext(), null);
        localView.setZOrderMediaOverlay(true);
        localView.setMirror(true);
        if (!videoCall) localView.setVisibility(View.GONE);

        List<PeerConnection.IceServer> ice = new ArrayList<>();
        ice.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        PeerConnection.RTCConfiguration cfg = new PeerConnection.RTCConfiguration(ice);
        cfg.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        pc = factory.createPeerConnection(cfg, new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState state) {}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                main.post(() -> {
                    setStatus(String.valueOf(state));
                    if (state == PeerConnection.IceConnectionState.CONNECTED
                            || state == PeerConnection.IceConnectionState.COMPLETED) {
                        SoftSounds.callConnected();
                        setStatus("Connected");
                    } else if (state == PeerConnection.IceConnectionState.FAILED) {
                        setStatus("Failed — same hotspot?");
                    }
                });
            }
            @Override public void onIceConnectionReceivingChange(boolean b) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) {}
            @Override public void onIceCandidate(IceCandidate c) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("sdpMid", c.sdpMid);
                    o.put("sdpMLineIndex", c.sdpMLineIndex);
                    o.put("candidate", c.sdp);
                    publishSignal("ice", o);
                } catch (Exception ignored) {}
            }
            @Override public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}
            @Override public void onAddStream(MediaStream stream) {
                if (stream.videoTracks.size() > 0)
                    main.post(() -> stream.videoTracks.get(0).addSink(remoteView));
            }
            @Override public void onRemoveStream(MediaStream mediaStream) {}
            @Override public void onDataChannel(DataChannel dataChannel) {}
            @Override public void onRenegotiationNeeded() {}
            @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
                if (receiver != null && receiver.track() instanceof VideoTrack)
                    main.post(() -> ((VideoTrack) receiver.track()).addSink(remoteView));
            }
        });

        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        localAudio = factory.createAudioTrack("AUDIO", audioSource);
        localAudio.setEnabled(true);
        pc.addTrack(localAudio, Collections.singletonList("ARDAMS"));
        if (videoCall) startCamera();
    }

    private void startCamera() {
        try {
            VideoSource videoSource = factory.createVideoSource(false);
            SurfaceTextureHelper helper = SurfaceTextureHelper.create("CaptureThread", egl.getEglBaseContext());
            Camera2Enumerator enumerator = new Camera2Enumerator(this);
            String camName = null;
            for (String name : enumerator.getDeviceNames()) {
                if (enumerator.isFrontFacing(name)) { camName = name; break; }
            }
            if (camName == null && enumerator.getDeviceNames().length > 0)
                camName = enumerator.getDeviceNames()[0];
            if (camName != null) {
                capturer = enumerator.createCapturer(camName, null);
            } else {
                Camera1Enumerator c1 = new Camera1Enumerator(false);
                for (String name : c1.getDeviceNames()) {
                    if (c1.isFrontFacing(name)) { camName = name; break; }
                }
                if (camName == null && c1.getDeviceNames().length > 0) camName = c1.getDeviceNames()[0];
                if (camName != null) capturer = c1.createCapturer(camName, null);
            }
            if (capturer == null) { setStatus("No camera"); return; }
            capturer.initialize(helper, getApplicationContext(), videoSource.getCapturerObserver());
            capturer.startCapture(640, 480, 24);
            localVideo = factory.createVideoTrack("VIDEO", videoSource);
            localVideo.setEnabled(true);
            localVideo.addSink(localView);
            localView.setVisibility(View.VISIBLE);
            pc.addTrack(localVideo, Collections.singletonList("ARDAMS"));
            setStatus("Camera on");
        } catch (Exception e) {
            setStatus("Camera: " + e.getMessage());
            Toast.makeText(this, "Camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void createOffer() {
        if (pc == null || offerCreated) return;
        offerCreated = true;
        MediaConstraints mc = new MediaConstraints();
        mc.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        mc.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", videoCall ? "true" : "false"));
        pc.createOffer(new SdpAdapter() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                pc.setLocalDescription(new SdpAdapter(), sdp);
                try {
                    JSONObject o = new JSONObject();
                    o.put("type", sdp.type.canonicalForm());
                    o.put("sdp", sdp.description);
                    publishSignal("offer", o);
                } catch (Exception ignored) {}
            }
            @Override public void onCreateFailure(String err) { setStatus("Offer fail: " + err); }
        }, mc);
    }

    private void createAnswer() {
        MediaConstraints mc = new MediaConstraints();
        mc.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        mc.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", videoCall ? "true" : "false"));
        pc.createAnswer(new SdpAdapter() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                pc.setLocalDescription(new SdpAdapter(), sdp);
                try {
                    JSONObject o = new JSONObject();
                    o.put("type", sdp.type.canonicalForm());
                    o.put("sdp", sdp.description);
                    publishSignal("answer", o);
                } catch (Exception ignored) {}
            }
        }, mc);
    }

    private void publishSignal(String kind, JSONObject payload) {
        try {
            JSONObject data = new JSONObject();
            data.put("kind", kind);
            data.put("from", me);
            data.put("video", videoCall);
            data.put("ts", System.currentTimeMillis());
            if (payload != null) data.put("payload", payload);
            SyncHub hub = SyncHub.get(this);
            hub.enqueue("call_signal", data);
            StockDb db = new StockDb(this);
            if ("server".equals(db.setting("sync_mode", "off"))) hub.storeCallSignal(data);
            else hub.flushQueue(null);
        } catch (Exception ignored) {}
    }

    private void pollSignals() {
        try {
            StockDb db = new StockDb(this);
            if ("client".equals(db.setting("sync_mode", "off")))
                SyncHub.get(this).pullCallSignals(this::applyPendingSignals);
            else applyPendingSignals();
        } catch (Exception ignored) {}
    }

    private void applyPendingSignals() {
        try {
            JSONArray arr = SyncHub.get(this).loadCallSignals(40);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject m = arr.getJSONObject(i);
                if (me.equals(m.optString("from"))) continue;
                String kind = m.optString("kind");
                JSONObject payload = m.optJSONObject("payload");
                if ("offer".equals(kind) && payload != null && !outgoing) {
                    SessionDescription sdp = new SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(payload.optString("type")),
                            payload.optString("sdp"));
                    pc.setRemoteDescription(new SdpAdapter() {
                        @Override public void onSetSuccess() { createAnswer(); }
                    }, sdp);
                    setStatus("Answering…");
                } else if ("answer".equals(kind) && payload != null && outgoing) {
                    SessionDescription sdp = new SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(payload.optString("type")),
                            payload.optString("sdp"));
                    pc.setRemoteDescription(new SdpAdapter(), sdp);
                    setStatus("Answer OK…");
                } else if ("ice".equals(kind) && payload != null && pc != null) {
                    pc.addIceCandidate(new IceCandidate(
                            payload.optString("sdpMid"),
                            payload.optInt("sdpMLineIndex"),
                            payload.optString("candidate")));
                } else if ("end".equals(kind)) hangup();
            }
        } catch (Exception ignored) {}
    }

    private void hangup() {
        if (ended) return;
        ended = true;
        SoftSounds.callEnded();
        publishSignal("end", null);
        main.removeCallbacks(signalPoll);
        try { if (capturer != null) { capturer.stopCapture(); capturer.dispose(); } } catch (Exception ignored) {}
        try { if (pc != null) pc.close(); } catch (Exception ignored) {}
        try { if (localView != null) localView.release(); } catch (Exception ignored) {}
        try { if (remoteView != null) remoteView.release(); } catch (Exception ignored) {}
        try { if (egl != null) egl.release(); } catch (Exception ignored) {}
        finish();
    }

    @Override public void onBackPressed() { hangup(); }

    @Override protected void onDestroy() {
        ended = true;
        SoftSounds.stopCallRing();
        main.removeCallbacks(signalPoll);
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 201 && hasPerms()) {
            try {
                initWebRtc();
                SoftSounds.startCallRing();
                if (outgoing) { setStatus("Calling…"); publishSignal("invite", null); createOffer(); }
                main.post(signalPoll);
            } catch (Throwable t) { setStatus("Error: " + t.getMessage()); }
        } else if (requestCode == 201) setStatus("Permission denied");
    }

    static class SdpAdapter implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription s) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String s) {}
        @Override public void onSetFailure(String s) {}
    }
}
