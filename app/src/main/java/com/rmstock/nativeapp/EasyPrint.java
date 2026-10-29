package com.rmstock.nativeapp;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * One-tap print to office Wi‑Fi / network printers via Android Print framework.
 * Phone + printer must be on same Wi‑Fi (or printer discovered by system).
 * Best practice: PrintManager + PDF (A4) — works with Mopria / HP / Epson / Canon plugins.
 */
public final class EasyPrint {
    private EasyPrint() {}

    public static void printPdf(Context ctx, File pdf, String jobName) {
        if (pdf == null || !pdf.exists()) {
            toast(ctx, "PDF not found");
            return;
        }
        try {
            PrintManager pm = (PrintManager) ctx.getSystemService(Context.PRINT_SERVICE);
            if (pm == null) {
                toast(ctx, "Print service missing — share PDF instead");
                share(ctx, pdf);
                return;
            }
            String name = jobName != null ? jobName : pdf.getName();
            PrintAttributes attrs = new PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(new PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                    .build();
            pm.print(name, new FilePrintAdapter(ctx, pdf, name), attrs);
            toast(ctx, "Select your Wi‑Fi printer…");
        } catch (Exception e) {
            toast(ctx, "Print: " + e.getMessage());
            share(ctx, pdf);
        }
    }

    public static void share(Context ctx, File pdf) {
        try {
            Uri uri = FileProvider.getUriForFile(ctx, "com.rmstock.nativeapp.fileprovider", pdf);
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
            i.setType("application/pdf");
            i.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(android.content.Intent.createChooser(i, "Share / Print PDF"));
        } catch (Exception e) {
            toast(ctx, e.getMessage());
        }
    }

    public static File reportsDir(Context ctx) {
        File dir = new File(ctx.getExternalFilesDir(null), "reports");
        if (!dir.exists()) dir.mkdirs();
        File alt = new File(ctx.getFilesDir(), "reports");
        if (!alt.exists()) alt.mkdirs();
        return dir.exists() ? dir : alt;
    }

    public static File[] listRecentPdfs(Context ctx) {
        java.util.ArrayList<File> all = new java.util.ArrayList<>();
        for (File dir : new File[]{reportsDir(ctx), new File(ctx.getFilesDir(), "reports")}) {
            File[] fs = dir.listFiles((d, n) -> n != null && n.toLowerCase().endsWith(".pdf"));
            if (fs != null) for (File f : fs) all.add(f);
        }
        File[] arr = all.toArray(new File[0]);
        java.util.Arrays.sort(arr, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return arr;
    }

    private static void toast(Context ctx, String m) {
        try {
            if (ctx instanceof Activity)
                ((Activity) ctx).runOnUiThread(() -> Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show());
            else Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
    }

    static final class FilePrintAdapter extends PrintDocumentAdapter {
        private final Context ctx;
        private final File file;
        private final String job;

        FilePrintAdapter(Context ctx, File file, String job) {
            this.ctx = ctx.getApplicationContext();
            this.file = file;
            this.job = job;
        }

        @Override
        public void onLayout(PrintAttributes oldAttr, PrintAttributes newAttr,
                             CancellationSignal signal, LayoutResultCallback cb, Bundle extras) {
            if (signal.isCanceled()) { cb.onLayoutCancelled(); return; }
            PrintDocumentInfo info = new PrintDocumentInfo.Builder(job)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build();
            cb.onLayoutFinished(info, true);
        }

        @Override
        public void onWrite(PageRange[] pages, ParcelFileDescriptor dest,
                            CancellationSignal signal, WriteResultCallback cb) {
            try {
                InputStream in = new FileInputStream(file);
                OutputStream out = new FileOutputStream(dest.getFileDescriptor());
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (signal.isCanceled()) { cb.onWriteCancelled(); in.close(); out.close(); return; }
                    out.write(buf, 0, n);
                }
                in.close();
                out.close();
                cb.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
            } catch (Exception e) {
                cb.onWriteFailed(e.getMessage());
            }
        }
    }
}
