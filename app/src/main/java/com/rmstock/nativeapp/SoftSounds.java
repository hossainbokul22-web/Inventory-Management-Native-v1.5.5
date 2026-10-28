package com.rmstock.nativeapp;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Unique soft "liquid glass" tones for chat + calls. */
public final class SoftSounds {
    private static final ExecutorService EX = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean ringOn = new AtomicBoolean(false);
    private static volatile int lastChatCount = -1;
    private static volatile long suppressReceiveUntil = 0;
    private static final int SR = 24000;

    private SoftSounds() {}

    public static void messageSent() {
        suppressReceiveUntil = System.currentTimeMillis() + 1100;
        EX.execute(() -> playPhrase(new float[][]{
                {880, 0.04f, 0.18f}, {1320, 0.05f, 0.14f}, {1760, 0.06f, 0.10f}
        }, 0.20f, 0.012f));
    }

    public static void messageReceived() {
        if (System.currentTimeMillis() < suppressReceiveUntil) return;
        EX.execute(() -> playPhrase(new float[][]{
                {392f, 0.07f, 0.22f}, {588f, 0.09f, 0.20f}, {784f, 0.11f, 0.16f}, {1176f, 0.08f, 0.10f}
        }, 0.24f, 0.018f));
    }

    public static void startCallRing() {
        if (!ringOn.compareAndSet(false, true)) return;
        EX.execute(() -> {
            while (ringOn.get()) {
                playPhrase(new float[][]{
                        {311.13f, 0.12f, 0.28f}, {369.99f, 0.12f, 0.26f}, {466.16f, 0.18f, 0.24f}
                }, 0.30f, 0.02f);
                if (!ringOn.get()) break;
                sleep(220);
                playPhrase(new float[][]{
                        {466.16f, 0.10f, 0.22f}, {415.30f, 0.10f, 0.20f}, {349.23f, 0.16f, 0.18f}
                }, 0.26f, 0.02f);
                if (!ringOn.get()) break;
                sleep(650);
            }
        });
    }

    public static void stopCallRing() { ringOn.set(false); }

    public static void callConnected() {
        stopCallRing();
        EX.execute(() -> playPhrase(new float[][]{
                {261.63f, 0.10f, 0.28f}, {329.63f, 0.10f, 0.26f},
                {392.00f, 0.10f, 0.24f}, {523.25f, 0.16f, 0.22f}
        }, 0.28f, 0.015f));
    }

    public static void callEnded() {
        stopCallRing();
        EX.execute(() -> playPhrase(new float[][]{
                {523.25f, 0.09f, 0.24f}, {415.30f, 0.10f, 0.22f},
                {311.13f, 0.12f, 0.20f}, {233.08f, 0.18f, 0.14f}
        }, 0.26f, 0.025f));
    }

    public static void maybeNotifyNewChat(int count) {
        if (lastChatCount < 0) { lastChatCount = count; return; }
        if (count > lastChatCount) messageReceived();
        lastChatCount = count;
    }

    public static void resetChatBaseline(int count) { lastChatCount = count; }

    private static void playPhrase(float[][] notes, float masterVol, float gapSec) {
        try {
            for (int i = 0; i < notes.length; i++) {
                playTone(notes[i][0], notes[i][1], notes[i][2] * masterVol);
                if (i < notes.length - 1) sleep((long) (gapSec * 1000));
            }
        } catch (Exception ignored) {}
    }

    private static void playTone(float freqHz, float durationSec, float volume) {
        int samples = Math.max(1, (int) (SR * durationSec));
        short[] buf = new short[samples];
        int fadeIn = Math.min(samples / 5, SR / 40);
        int fadeOut = Math.min(samples / 3, SR / 12);
        for (int i = 0; i < samples; i++) {
            double t = i / (double) SR;
            double env = 1.0;
            if (i < fadeIn) env = i / (double) fadeIn;
            if (i > samples - fadeOut) env = (samples - i) / (double) fadeOut;
            double w = Math.sin(2 * Math.PI * freqHz * t);
            double h2 = 0.22 * Math.sin(2 * Math.PI * freqHz * 2 * t);
            double h3 = 0.08 * Math.sin(2 * Math.PI * freqHz * 3.01 * t);
            double shimmer = 0.04 * Math.sin(2 * Math.PI * (freqHz * 1.5) * t + Math.sin(2 * Math.PI * 6 * t));
            double sample = (w + h2 + h3 + shimmer) * env * volume;
            if (sample > 1) sample = 1;
            if (sample < -1) sample = -1;
            buf[i] = (short) (sample * Short.MAX_VALUE * 0.42);
        }
        writePlay(buf, (int) (durationSec * 1000) + 20);
    }

    private static void writePlay(short[] buf, int sleepMs) {
        int channel = AudioFormat.CHANNEL_OUT_MONO;
        int format = AudioFormat.ENCODING_PCM_16BIT;
        int min = AudioTrack.getMinBufferSize(SR, channel, format);
        int size = Math.max(min, buf.length * 2);
        AudioTrack track;
        if (Build.VERSION.SDK_INT >= 23) {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(SR).setEncoding(format).setChannelMask(channel).build())
                    .setBufferSizeInBytes(size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
        } else {
            track = new AudioTrack(AudioManager.STREAM_NOTIFICATION, SR, channel, format, size, AudioTrack.MODE_STATIC);
        }
        track.write(buf, 0, buf.length);
        track.play();
        sleep(sleepMs);
        try { track.stop(); track.release(); } catch (Exception ignored) {}
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
