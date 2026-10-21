package com.rmstock.nativeapp;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.widget.Toast;

/**
 * Asks for the fingerprint (or the phone's PIN / pattern) before add, edit and delete work.
 * One successful unlock stays valid for 3 minutes; deletes always ask again.
 */
final class Guard {
    private Guard() {}

    static final int REQ = 7001;
    private static long unlockedUntil = 0;
    private static Runnable pendingOk;

    static boolean enabled(StockDb db) {
        return !"0".equals(db.setting("guard", "1"));
    }

    static boolean valid() {
        return System.currentTimeMillis() < unlockedUntil;
    }

    static void run(final Activity a, StockDb db, final String why, boolean fresh, final Runnable ok) {
        if (!enabled(db)) { ok.run(); return; }
        if (!fresh && valid()) { ok.run(); return; }
        KeyguardManager km = (KeyguardManager) a.getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) {
            Toast.makeText(a, "No screen lock on this phone, so protection is off. Set a fingerprint or PIN in the phone settings.", Toast.LENGTH_LONG).show();
            ok.run();
            return;
        }
        final Runnable done = () -> { unlockedUntil = System.currentTimeMillis() + 3 * 60 * 1000L; ok.run(); };
        if (Build.VERSION.SDK_INT >= 28) {
            BiometricPrompt.Builder b = new BiometricPrompt.Builder(a).setTitle("Unlock").setSubtitle(why);
            if (Build.VERSION.SDK_INT >= 30) {
                b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            } else if (Build.VERSION.SDK_INT == 29) {
                b.setDeviceCredentialAllowed(true);
            } else {
                b.setNegativeButton("Cancel", a.getMainExecutor(), (d, w) -> { });
            }
            final BiometricPrompt p = b.build();
            p.authenticate(new CancellationSignal(), a.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    done.run();
                }

                @Override
                public void onAuthenticationError(int code, CharSequence msg) {
                    if (code == BiometricPrompt.BIOMETRIC_ERROR_CANCELED || code == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) return;
                    if (code == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS || code == BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT || code == BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
                        keyguard(a, km, why, done);
                        return;
                    }
                    Toast.makeText(a, String.valueOf(msg), Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }
        keyguard(a, km, why, done);
    }

    private static void keyguard(Activity a, KeyguardManager km, String why, Runnable done) {
        Intent i = km.createConfirmDeviceCredentialIntent("Unlock", why);
        if (i == null) { done.run(); return; }
        pendingOk = done;
        a.startActivityForResult(i, REQ);
    }

    /** Call from onActivityResult; returns true when the result belonged to the lock screen. */
    static boolean onResult(int req, int res) {
        if (req != REQ) return false;
        Runnable r = pendingOk;
        pendingOk = null;
        if (res == Activity.RESULT_OK && r != null) r.run();
        return true;
    }
}
