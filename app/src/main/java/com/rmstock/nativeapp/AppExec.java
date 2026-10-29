
package com.rmstock.nativeapp;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared IO executor — keep SQLite / PDF / face off the UI thread. */
public final class AppExec {
    private AppExec() {}
    public static final ExecutorService IO = Executors.newFixedThreadPool(2);
    public static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void io(Runnable r) {
        try { IO.execute(r); } catch (Throwable t) {
            try { new Thread(r).start(); } catch (Throwable ignored) {}
        }
    }

    public static void main(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else MAIN.post(r);
    }
}
