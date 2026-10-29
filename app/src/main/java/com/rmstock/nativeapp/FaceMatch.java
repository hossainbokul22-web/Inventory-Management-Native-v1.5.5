package com.rmstock.nativeapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * Lightweight offline face template match (no cloud, no TFLite model file).
 * Pattern used by many FOSS demos: enroll reference photo → cosine on normalized face crop.
 * Not as strong as MobileFaceNet; good practical gate + ranking for factory floor.
 */
public final class FaceMatch {
    private FaceMatch() {}
    public static final float AUTO_THRESHOLD = 0.82f;
    public static final float SUGGEST_THRESHOLD = 0.72f;

    public static float[] embedFile(String path) {
        if (path == null || path.isEmpty()) return null;
        try {
            Bitmap b = BitmapFactory.decodeFile(path);
            if (b == null) return null;
            return embedBitmap(b);
        } catch (Exception e) { return null; }
    }

    public static float[] embedBitmap(Bitmap src) {
        if (src == null) return null;
        Bitmap b = Bitmap.createScaledBitmap(src, 64, 64, true);
        float[] v = new float[64 * 64];
        long sum = 0;
        int[] px = new int[64 * 64];
        b.getPixels(px, 0, 64, 0, 0, 64, 64);
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int g = ((c >> 16) & 255) * 30 + ((c >> 8) & 255) * 59 + (c & 255) * 11;
            g /= 100;
            v[i] = g;
            sum += g;
        }
        float mean = sum / (float) v.length;
        double norm = 0;
        for (int i = 0; i < v.length; i++) {
            v[i] -= mean;
            norm += v[i] * v[i];
        }
        norm = Math.sqrt(norm) + 1e-6;
        for (int i = 0; i < v.length; i++) v[i] /= norm;
        return v;
    }

    public static float cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0f;
        double dot = 0;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return (float) Math.max(0, Math.min(1, dot));
    }

    /** NV21 preview frame → JPEG Bitmap (full frame; face already gated by ML Kit). */
    public static Bitmap nv21ToBitmap(byte[] nv21, int w, int h) {
        if (nv21 == null || w <= 0 || h <= 0) return null;
        try {
            YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, w, h, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, w, h), 70, baos);
            byte[] jpeg = baos.toByteArray();
            return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
        } catch (Exception e) { return null; }
    }

    public static boolean fileOk(String path) {
        return path != null && path.length() > 0 && new File(path).exists();
    }
}
