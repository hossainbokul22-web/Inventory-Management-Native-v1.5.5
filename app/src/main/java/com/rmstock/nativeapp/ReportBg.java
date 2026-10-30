
package com.rmstock.nativeapp;

import android.app.Activity;
import android.widget.Toast;
import java.io.File;
import java.util.concurrent.Callable;

/** Run heavy report work off UI thread; toast result on main. */
public final class ReportBg {
    private ReportBg() {}
    public static void run(Activity a, String busyMsg, Callable<File> work, boolean printAfter) {
        if (a == null || work == null) return;
        try { Toast.makeText(a, busyMsg != null ? busyMsg : "Building report…", Toast.LENGTH_SHORT).show(); } catch (Exception ignored) {}
        AppExec.io(() -> {
            try {
                File f = work.call();
                AppExec.main(() -> {
                    if (f == null || !f.exists()) {
                        Toast.makeText(a, "Report failed", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(a, "Ready: " + f.getName(), Toast.LENGTH_SHORT).show();
                    if (printAfter) {
                        try { EasyPrint.printPdf(a, f, f.getName()); }
                        catch (Exception e) { EasyPrint.share(a, f); }
                    } else {
                        try { EasyPrint.share(a, f); } catch (Exception ignored) {}
                    }
                });
            } catch (Exception e) {
                AppExec.main(() -> Toast.makeText(a, "Report: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }
}
