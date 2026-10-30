package com.rmstock.nativeapp;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * On-device MobileFaceNet (TFLite). No internet at runtime.
 * Model: assets/MobileFaceNet.tflite (syaringan357 / InsightFace MobileFaceNet).
 * Input 112x112 RGB; compare two faces → similarity score.
 */
public final class FaceNetEngine {
    public static final int SIZE = 112;
    public static final float THRESHOLD = 0.80f;
    public static final float SUGGEST = 0.70f;

    private static FaceNetEngine INSTANCE;
    private Interpreter interpreter;
    private boolean ready;
    private int embDim = 192;

    public static synchronized FaceNetEngine get(Context c) {
        if (INSTANCE == null) INSTANCE = new FaceNetEngine(c.getApplicationContext());
        return INSTANCE;
    }

    private FaceNetEngine(Context c) {
        try {
            MappedByteBuffer model = loadModel(c, "MobileFaceNet.tflite");
            Interpreter.Options opt = new Interpreter.Options();
            opt.setNumThreads(4);
            interpreter = new Interpreter(model, opt);
            try {
                int[] out = interpreter.getOutputTensor(0).shape();
                if (out != null && out.length >= 2) embDim = out[out.length - 1];
            } catch (Exception ignored) {}
            ready = true;
        } catch (Throwable t) {
            ready = false;
            interpreter = null;
        }
    }

    public boolean isReady() { return ready && interpreter != null; }
    public static boolean modelPresent(Context c) {
        try {
            String[] list = c.getAssets().list("");
            if (list != null) for (String n : list) if ("MobileFaceNet.tflite".equals(n)) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private static MappedByteBuffer loadModel(Context c, String name) throws Exception {
        AssetFileDescriptor fd = c.getAssets().openFd(name);
        FileInputStream in = new FileInputStream(fd.getFileDescriptor());
        FileChannel ch = in.getChannel();
        return ch.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
    }

    /** Similarity 0..1 (same person if >= THRESHOLD). */
    public synchronized float compare(Bitmap face1, Bitmap face2) {
        if (!isReady() || face1 == null || face2 == null) return 0f;
        try {
            Bitmap a = Bitmap.createScaledBitmap(face1, SIZE, SIZE, true);
            Bitmap b = Bitmap.createScaledBitmap(face2, SIZE, SIZE, true);
            // Dual-input models expect [2,112,112,3] → [2,192]
            float[][][][] input = new float[2][SIZE][SIZE][3];
            fill(input[0], a);
            fill(input[1], b);
            float[][] emb = new float[2][embDim];
            interpreter.run(input, emb);
            return cosine(emb[0], emb[1]);
        } catch (Throwable t) {
            // Some exports are single-image embedding only
            try {
                float[] e1 = embed(face1);
                float[] e2 = embed(face2);
                return cosine(e1, e2);
            } catch (Throwable t2) {
                return 0f;
            }
        }
    }

    public synchronized float[] embed(Bitmap face) {
        if (!isReady() || face == null) return null;
        Bitmap a = Bitmap.createScaledBitmap(face, SIZE, SIZE, true);
        try {
            float[][][][] input = new float[1][SIZE][SIZE][3];
            fill(input[0], a);
            float[][] emb = new float[1][embDim];
            interpreter.run(input, emb);
            return emb[0];
        } catch (Throwable t) {
            return null;
        }
    }

    private static void fill(float[][][] dst, Bitmap bmp) {
        int[] px = new int[SIZE * SIZE];
        bmp.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE);
        int i = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int c = px[i++];
                // normalize to [-1,1] common for MobileFaceNet
                dst[y][x][0] = (((c >> 16) & 255) - 127.5f) / 128f;
                dst[y][x][1] = (((c >> 8) & 255) - 127.5f) / 128f;
                dst[y][x][2] = ((c & 255) - 127.5f) / 128f;
            }
        }
    }

    public static float cosine(float[] a, float[] b) {
        if (a == null || b == null) return 0f;
        int n = Math.min(a.length, b.length);
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        double d = Math.sqrt(na) * Math.sqrt(nb);
        if (d < 1e-9) return 0f;
        float s = (float) (dot / d);
        return Math.max(0f, Math.min(1f, s));
    }
}
