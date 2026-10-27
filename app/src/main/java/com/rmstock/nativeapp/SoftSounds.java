package com.rmstock.nativeapp;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.ToneGenerator;
import android.os.Build;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Soft unique tones for chat + calls (procedural, no raw files). */
public final class SoftSounds {
    private static final ExecutorService EX = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean ringOn = new AtomicBoolean(false);
    private static volatile int lastChatCount = -1;
    private static volatile long suppressReceiveUntil = 0;

    private SoftSounds() {}

    public static void messageSent() {
        suppressReceiveUntil = System.currentTimeMillis() + 1200;
        EX.execute(() -> playMelody(new double[]{720, 960}, new int[]{55, 70}, 0.22f));
    }

    public static void messageReceived() {
        if (System.currentTimeMillis() < suppressReceiveUntil) return;
        EX.execute(() -> playMelody(new double[]{523.25, 659.25, 783.99}, new int[]{90, 90, 120}, 0.28f));
    }

    public static void startCallRing() {
        if (!ringOn.compareAndSet(false, true)) return;
        EX.execute(() -> {
            while (ringOn.get()) {
                playMelody(new double[]{440, 554.37, 659.25}, new int[]{160, 160, 220}, 0.32f);
                if (!ringOn.get()) break;
                try { Thread.sleep(450); } catch (InterruptedException e) { break; }
                playMelody(new double[]{659.25, 554.37, 440}, new int[]{140, 140, 180}, 0.28f);
                if (!ringOn.get()) break;
                try { Thread.sleep(700); } catch (InterruptedException e) { break; }
            }
        });
    }

    public static void stopCallRing() { ringOn.set(false); }

    public static void callConnected() {
        stopCallRing();
        EX.execute(() -> playMelody(new double[]{392, 523.25, 659.25, 784}, new int[]{80, 80, 80, 140}, 0.30f));
    }

    public static void callEnded() {
        stopCallRing();
        EX.execute(() -> playMelody(new double[]{587.33, 440, 349.23}, new int[]{100, 100, 160}, 0.26f));
    }

    public static void maybeNotifyNewChat(int count) {
        if (lastChatCount < 0) { lastChatCount = count; return; }
        if (count > lastChatCount) messageReceived();
        lastChatCount = count;
    }

    public static void resetChatBaseline(int count) { lastChatCount = count; }

    private static void playMelody(double[] hz, int[] ms, float volume) {
        try {
            for (int i = 0; i < hz.length; i++) {
                playTone(hz[i], ms[i], volume);
                if (i < hz.length - 1) Thread.sleep(18);
            }
        } catch (Exception ignored) {
            try {
                ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60);
                tg.startTone(ToneGenerator.TONE_PROP_ACK, 120);
                Thread.sleep(140);
                tg.release();
            } catch (Exception ignored2) {}
        }
    }

    private static void playTone(double freqHz, int durationMs, float volume) {
        final int sampleRate = 22050;
        int samples = Math.max(1, (int) (sampleRate * (durationMs / 1000.0)));
        short[] buf = new short[samples];
        for (int i = 0; i < samples; i++) {
            double t = i / (double) sampleRate;
            int fade = Math.min(samples / 6, sampleRate / 30);
            double env = 1.0;
            if (i < fade) env = i / (double) fade;
            if (i > samples - fade) env = (samples - i) / (double) fade;
            double wave = Math.sin(2 * Math.PI * freqHz * t);
            double tri = 2 * Math.abs(2 * (t * freqHz - Math.floor(t * freqHz + 0.5))) - 1;
            buf[i] = (short) ((0.85 * wave + 0.15 * tri) * env * volume * Short.MAX_VALUE * 0.45);
        }
        int channel = AudioFormat.CHANNEL_OUT_MONO;
        int format = AudioFormat.ENCODING_PCM_16BIT;
        int min = AudioTrack.getMinBufferSize(sampleRate, channel, format);
        int size = Math.max(min, buf.length * 2);
        AudioTrack track;
        if (Build.VERSION.SDK_INT >= 23) {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(sampleRate).setEncoding(format).setChannelMask(channel).build())
                    .setBufferSizeInBytes(size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
        } else {
            track = new AudioTrack(AudioManager.STREAM_NOTIFICATION, sampleRate, channel, format, size, AudioTrack.MODE_STATIC);
        }
        track.write(buf, 0, buf.length);
        track.play();
        try { Thread.sleep(durationMs + 30); } catch (InterruptedException ignored) {}
        try { track.stop(); track.release(); } catch (Exception ignored) {}
    }
}
