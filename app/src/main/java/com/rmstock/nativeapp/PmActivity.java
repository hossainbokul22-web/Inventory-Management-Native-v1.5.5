package com.rmstock.nativeapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.print.PrintManager;
import android.print.PrintDocumentAdapter;
import android.print.PrintAttributes;
import android.print.PrintDocumentInfo;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Fully native Daily PM Report — packaging materials, production, daily/monthly
 * reports, master data, settings and backup. No WebView.
 */
public class PmActivity extends Activity {
    private static final int REQ_IMPORT = 91, REQ_SAVE = 92;
    private static final String[] TABS = {"Home", "Entry", "Daily", "Monthly", "Production", "FG Daily", "Master", "Backup"};
    private static final String[] SHIFTS = {"A", "B", "C", "General"};

    private PmDb pm;
    private StockDb db;
    private LinearLayout root, content;
    private TextView titleTv, statusTv;
    private int tab;
    private boolean pmNested = false; // sub-screen inside a tab (e.g. FG month list)
    private int savedKindPosition=0, savedCode=-1;
    private String stockDate, monthYm;
    private File pendingFile;
    private String pendingMime = "application/octet-stream";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        pm = new PmDb(this);
        db = new StockDb(this);
        stockDate = PmDb.today();
        monthYm = PmDb.thisMonth();
        tab = 0;

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        // Match main app header: navy + same padding
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Ui.NAVY);
        bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        TextView back = Ui.text(this, "‹  Back", 16, 0xFFFFFFFF, true);
        back.setPadding(Ui.dp(this, 0), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        back.setOnClickListener(v -> handlePmBack());
        bar.addView(back);
        titleTv = Ui.text(this, pm.meta("appTitle", "Daily PM Report"), 17, 0xFFFFFFFF, true);
        bar.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        statusTv = Ui.text(this, "", 11, Color.WHITE, true);
        statusTv.setBackground(Ui.shape(this, Color.parseColor("#333333"), Color.parseColor("#333333"), 14));
        statusTv.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        bar.addView(statusTv);
        root.addView(bar);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setBackgroundColor(Color.WHITE);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView t = Ui.text(this, TABS[i], 13, Ui.MUTED, true);
            t.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
            tlp.setMargins(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8));
            t.setLayoutParams(tlp);
            t.setTag(idx);
            t.setOnClickListener(v -> {
                if (idx == 6) {
                    Guard.run(this, db, TABS[idx], false, () -> { pmNested = false; tab = idx; render(); });
                } else {
                    pmNested = false;
                    tab = idx;
                    render();
                }
            });
            tabs.addView(t);
        }
        hsv.addView(tabs);
        root.addView(hsv);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView sc = new ScrollView(this);
        sc.addView(content);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        render();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        Ui.init(this);
        root.setBackgroundColor(Ui.BG);
        render();
    }

    private void updateStatus() {
        statusTv.setText(pm.entryCount() + " entries");
        titleTv.setText(pm.meta("appTitle", "Daily PM Report"));
    }


    @Override
    public void onBackPressed() {
        handlePmBack();
    }

    /** Phone back / header Back: nested → tab; other tab → Entry; Entry → leave PM (dashboard). */
    private void handlePmBack() {
        if (pmNested) {
            pmNested = false;
            render();
            return;
        }
        if (tab != 0) {
            tab = 0;
            render();
            return;
        }
        finish();
    }

    private void render() {
        content.removeAllViews();
        updateStatus();
        HorizontalScrollView hsv = (HorizontalScrollView) root.getChildAt(1);
        LinearLayout tabs = (LinearLayout) hsv.getChildAt(0);
        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView t = (TextView) tabs.getChildAt(i);
            boolean on = i == tab;
            t.setTextColor(on ? Color.WHITE : Ui.MUTED);
            t.setBackground(Ui.shape(this, on ? Color.BLACK : Color.TRANSPARENT, on ? Color.BLACK : Ui.LINE, 20));
            t.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        }
        switch (tab) {
            case 0: showPmDashboard(); break;
            case 1: showEntry(); break;
            case 2: showDaily(); break;
            case 3: showMonthly(); break;
            case 4: showProduction(); break;
            case 5: showFgDaily(); break;
            case 6: showMaster(); break;
            case 7: showBackup(); break;
        }
    }

    private void add(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), 0);
        content.addView(v, lp);
    }


    private void showPmDashboard() {
        String date = stockDate == null || stockDate.isEmpty() ? PmDb.today() : stockDate;
        stockDate = date;

        // —— Header strip ——
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        head.setBackground(Ui.shape(this, Color.rgb(232, 242, 252), Color.rgb(20, 55, 95), 16));
        LinearLayout headRow = new LinearLayout(this);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView pmChip = Ui.text(this, "📦", 20, Ui.NAVY_TEXT, true);
        pmChip.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        pmChip.setBackground(Ui.shape(this, Color.WHITE, Ui.LINE, 12));
        headRow.addView(pmChip);
        LinearLayout headTxt = new LinearLayout(this);
        headTxt.setOrientation(LinearLayout.VERTICAL);
        headTxt.setPadding(Ui.dp(this, 12), 0, 0, 0);
        headTxt.addView(Ui.text(this, "PM Dashboard", 18, Ui.NAVY_TEXT, true));
        headTxt.addView(Ui.text(this, db.setting("company", "Inventory Management"), 12, Color.rgb(60, 90, 120), false));
        headRow.addView(headTxt, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(headRow);

        final EditText dateEt = field("Date", date);
        dateEt.setFocusable(false);
        dateEt.setOnClickListener(v -> pickDate(dateEt));
        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        dateRow.setPadding(0, Ui.dp(this, 10), 0, 0);
        dateRow.addView(Ui.text(this, "📅", 16, Ui.INK, false));
        dateRow.addView(dateEt, new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = Ui.primary(this, "Refresh");
        dateRow.addView(refresh, new LinearLayout.LayoutParams(-2, Ui.dp(this, 44)));
        head.addView(dateRow);
        add(head);

        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, Ui.dp(this, 8), 0, 0);
        add(body);

        Runnable fill = () -> {
            body.removeAllViews();
            String d = dateEt.getText().toString().trim();
            if (d.length() < 8) d = PmDb.today();
            stockDate = d;

            double totProd = 0, totDel = 0, totHold = 0, totPass = 0;
            try {
                for (JSONObject pr : pm.products()) {
                    int code = pr.optInt("code");
                    totProd += pm.sumKindOn("output", code, d);
                    totDel += pm.sumKindOn("delivery", code, d);
                    totHold += pm.sumKindOn("hold", code, d);
                    totPass += pm.sumKindOn("qa_pass", code, d);
                }
            } catch (Exception ignored) {}

            // —— KPI 2×2 grid ——
            body.addView(Ui.text(this, "Today at a glance", 13, Ui.MUTED, true));
            int[][] kpiColors = {
                {Color.rgb(232, 245, 233), Color.rgb(46, 125, 50)},   // prod green
                {Color.rgb(227, 242, 253), Color.rgb(21, 101, 192)},  // del blue
                {Color.rgb(255, 243, 224), Color.rgb(230, 126, 34)},  // hold orange
                {Color.rgb(243, 229, 245), Color.rgb(123, 31, 162)}   // pass purple
            };
            String[] kpiIcons = {"🏭", "🚚", "⏸", "✅"};
            String[] kpiLabels = {"Production", "Delivery", "QA Hold", "QA Pass"};
            double[] kpiVals = {totProd, totDel, totHold, totPass};
            LinearLayout kpiGrid = null;
            for (int i = 0; i < 4; i++) {
                if (i % 2 == 0) {
                    kpiGrid = new LinearLayout(this);
                    kpiGrid.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, -2);
                    gl.setMargins(0, 0, 0, Ui.dp(this, 8));
                    body.addView(kpiGrid, gl);
                }
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
                cell.setBackground(Ui.shape(this, kpiColors[i][0], kpiColors[i][0], 14));
                cell.addView(Ui.text(this, kpiIcons[i] + "  " + kpiLabels[i], 12, kpiColors[i][1], true));
                TextView val = Ui.text(this, PmDb.fmt(kpiVals[i]), 22, Ui.NAVY_TEXT, true);
                val.setPadding(0, Ui.dp(this, 6), 0, 0);
                cell.addView(val);
                cell.addView(Ui.text(this, "CTN / PCS", 11, Ui.MUTED, false));
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1);
                if (i % 2 == 0) cl.setMargins(0, 0, Ui.dp(this, 6), 0);
                else cl.setMargins(Ui.dp(this, 6), 0, 0, 0);
                kpiGrid.addView(cell, cl);
            }

            // —— Quick open pills ——
            body.addView(Ui.text(this, "Quick open", 13, Ui.MUTED, true));
            LinearLayout quick = new LinearLayout(this);
            quick.setOrientation(LinearLayout.HORIZONTAL);
            String[][] qs = {
                {"✏️ Entry", "1"}, {"📋 FG Daily", "5"}, {"🏭 Production", "4"}, {"📅 Monthly", "3"}
            };
            for (String[] q : qs) {
                final int ti = Integer.parseInt(q[1]);
                Button b = Ui.secondary(this, q[0]);
                b.setOnClickListener(v -> { pmNested = false; tab = ti; render(); });
                quick.addView(b, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
            }
            LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(-1, -2);
            ql.setMargins(0, Ui.dp(this, 4), 0, Ui.dp(this, 12));
            body.addView(quick, ql);

            // —— Product cards ——
            body.addView(Ui.text(this, "Products · Available / Closing", 13, Ui.MUTED, true));
            try {
                List<JSONObject> products = pm.products();
                if (products.isEmpty()) {
                    LinearLayout empty = Ui.card(this, Ui.CARD);
                    empty.addView(Ui.muted(this, "No products — add in Master"));
                    body.addView(empty);
                } else {
                    for (JSONObject pr : products) {
                        int code = pr.optInt("code");
                        String name = pr.optString("name");
                        double open = pm.openingProduct(code, d);
                        double prod = pm.sumKindOn("output", code, d);
                        double pass = pm.sumKindOn("qa_pass", code, d);
                        double holdT = pm.sumKindOn("hold", code, d);
                        double prevH = Math.max(0, pm.sumKind("hold", code, d, true) - pm.sumKind("qa_pass", code, d, true));
                        double del = pm.sumKindOn("delivery", code, d);
                        double avail = open + prod + pass - (holdT + prevH);
                        double close = open + prod - del;
                        if (Math.abs(open) + prod + del + holdT + pass < 1e-9) continue;

                        LinearLayout card = new LinearLayout(this);
                        card.setOrientation(LinearLayout.VERTICAL);
                        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
                        card.setBackground(Ui.shape(this, Color.WHITE, Ui.LINE, 14));
                        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
                        cardLp.setMargins(0, 0, 0, Ui.dp(this, 8));

                        LinearLayout titleRow = new LinearLayout(this);
                        titleRow.setOrientation(LinearLayout.HORIZONTAL);
                        titleRow.setGravity(Gravity.CENTER_VERTICAL);
                        titleRow.addView(Ui.text(this, code + "  " + name, 14, Ui.NAVY_TEXT, true),
                                new LinearLayout.LayoutParams(0, -2, 1));
                        String badge = avail < 0 ? "⚠ Low" : (holdT > 0 ? "Hold" : "OK");
                        int badgeBg = avail < 0 ? Color.rgb(255, 235, 238) : (holdT > 0 ? Color.rgb(255, 243, 224) : Color.rgb(232, 245, 233));
                        int badgeFg = avail < 0 ? Color.rgb(198, 40, 40) : (holdT > 0 ? Color.rgb(230, 126, 34) : Color.rgb(46, 125, 50));
                        TextView badgeTv = Ui.text(this, badge, 11, badgeFg, true);
                        badgeTv.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
                        badgeTv.setBackground(Ui.shape(this, badgeBg, badgeBg, 20));
                        titleRow.addView(badgeTv);
                        card.addView(titleRow);

                        // metric chips row
                        LinearLayout metrics = new LinearLayout(this);
                        metrics.setOrientation(LinearLayout.HORIZONTAL);
                        metrics.setPadding(0, Ui.dp(this, 10), 0, 0);
                        String[][] m = {
                            {"Open", PmDb.fmt(open)},
                            {"Avail", PmDb.fmt(avail)},
                            {"Close", PmDb.fmt(close)},
                            {"P/D", PmDb.fmt(prod) + "/" + PmDb.fmt(del)}
                        };
                        for (String[] mm : m) {
                            LinearLayout chip = new LinearLayout(this);
                            chip.setOrientation(LinearLayout.VERTICAL);
                            chip.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
                            chip.setBackground(Ui.shape(this, Color.rgb(248, 250, 252), Ui.LINE, 10));
                            chip.addView(Ui.text(this, mm[0], 10, Ui.MUTED, false));
                            chip.addView(Ui.text(this, mm[1], 13, Ui.NAVY_TEXT, true));
                            metrics.addView(chip, new LinearLayout.LayoutParams(0, -2, 1));
                        }
                        card.addView(metrics);
                        body.addView(card, cardLp);
                    }
                }
            } catch (Exception e) {
                body.addView(Ui.muted(this, "Error: " + e.getMessage()));
            }
            body.addView(Ui.muted(this, "Shift detail → FG Daily · Enter data → Entry"));
        };
        refresh.setOnClickListener(v -> fill.run());
        fill.run();
    }


    private void showEntry() {
        LinearLayout card = Ui.card(this, Ui.CARD);
        LinearLayout entryHead = new LinearLayout(this);
        entryHead.setOrientation(LinearLayout.HORIZONTAL);
        entryHead.setGravity(Gravity.CENTER_VERTICAL);
        entryHead.setPadding(0, 0, 0, Ui.dp(this, 8));
        TextView eIcon = Ui.text(this, "✏️", 18, Ui.NAVY_TEXT, true);
        eIcon.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        eIcon.setBackground(Ui.shape(this, Color.rgb(232, 242, 252), Ui.LINE, 12));
        entryHead.addView(eIcon);
        LinearLayout eTxt = new LinearLayout(this);
        eTxt.setOrientation(LinearLayout.VERTICAL);
        eTxt.setPadding(Ui.dp(this, 10), 0, 0, 0);
        eTxt.addView(Ui.text(this, "New entry", 18, Ui.NAVY_TEXT, true));
        eTxt.addView(Ui.text(this, "Packaging · Production · Wastage · QA", 12, Ui.MUTED, false));
        entryHead.addView(eTxt);
        card.addView(entryHead);

        final int[] kindIdx = {Math.min(savedKindPosition, PmDb.KIND_LABELS.length - 1)};
        HorizontalScrollView kindScroll = new HorizontalScrollView(this);
        kindScroll.setHorizontalScrollBarEnabled(false);
        final LinearLayout kindRow = new LinearLayout(this);
        kindRow.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] kindChips = new TextView[PmDb.KIND_LABELS.length];
        final int[] kindAccent = {
            Color.rgb(21, 101, 192), Color.rgb(84, 110, 122), Color.rgb(46, 125, 50),
            Color.rgb(2, 136, 209), Color.rgb(230, 126, 34), Color.rgb(198, 40, 40),
            Color.rgb(123, 31, 162), Color.rgb(255, 149, 0)
        };
        Runnable paintKinds = () -> {
            for (int i = 0; i < kindChips.length; i++) {
                boolean on = i == kindIdx[0];
                int acc = i < kindAccent.length ? kindAccent[i] : Color.BLACK;
                kindChips[i].setTextColor(on ? Color.WHITE : Ui.MUTED);
                kindChips[i].setBackground(Ui.shape(this, on ? acc : Ui.CARD, on ? acc : Ui.LINE, 20));
                kindChips[i].setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
            }
        };
        for (int i = 0; i < PmDb.KIND_LABELS.length; i++) {
            final int fi = i;
            TextView chip = Ui.text(this, PmDb.KIND_LABELS[i], 13, Ui.MUTED, true);
            chip.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.setMargins(0, 0, Ui.dp(this, 8), 0);
            chip.setLayoutParams(clp);
            kindChips[i] = chip;
            kindRow.addView(chip);
        }
        kindScroll.addView(kindRow);
        card.addView(kindScroll);
        paintKinds.run();

        // Date on its own row (top), Item/Product below
        card.addView(Ui.muted(this, "Date"));
        final EditText dateEt = field("Date", stockDate);
        dateEt.setFocusable(false);
        dateEt.setOnClickListener(v -> pickDate(dateEt));
        card.addView(dateEt);

        card.addView(Ui.muted(this, "Item / Product"));
        final Spinner codeSp = new Spinner(this);
        card.addView(codeSp);

        final List<JSONObject> selectedRecords = new ArrayList<>();
        final Runnable[] refreshHolder = new Runnable[1];
        refreshHolder[0] = () -> {
            selectedRecords.clear();
            String kk = PmDb.KINDS[kindIdx[0]];
            List<JSONObject> records = (kk.equals("receive") || kk.equals("ret") || kk.equals("wastage"))
                    ? pm.items() : pm.products();
            List<String> labels = new ArrayList<>();
            for (JSONObject o : records) {
                selectedRecords.add(o);
                labels.add(o.optInt("code") + " — " + o.optString("name"));
            }
            if (labels.isEmpty()) labels.add("No items available");
            codeSp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
            int restore = 0;
            for (int j = 0; j < selectedRecords.size(); j++) {
                if (selectedRecords.get(j).optInt("code") == savedCode) { restore = j; break; }
            }
            codeSp.setSelection(restore);
        };
        for (int i = 0; i < kindChips.length; i++) {
            final int fi = i;
            kindChips[i].setOnClickListener(v -> {
                kindIdx[0] = fi;
                paintKinds.run();
                refreshHolder[0].run();
            });
        }
        refreshHolder[0].run();

        card.addView(Ui.muted(this, "Quantity"));
        LinearLayout qtyRow = new LinearLayout(this);
        qtyRow.setOrientation(LinearLayout.HORIZONTAL);
        qtyRow.setGravity(Gravity.CENTER_VERTICAL);
        final EditText qtyEt = field("Qty", "");
        qtyEt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        qtyRow.addView(qtyEt, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.VERTICAL);
        for (int qv : new int[]{10, 50, 100}) {
            final int addv = qv;
            Button qb = Ui.secondary(this, "+" + addv);
            qb.setOnClickListener(v -> {
                try {
                    double cur = qtyEt.getText().toString().trim().isEmpty() ? 0
                            : Double.parseDouble(qtyEt.getText().toString().trim().replace(',', '.'));
                    qtyEt.setText(PmDb.fmt(cur + addv));
                } catch (Exception e) { qtyEt.setText(String.valueOf(addv)); }
            });
            LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(-2, -2);
            qlp.setMargins(Ui.dp(this, 6), 0, 0, Ui.dp(this, 4));
            quick.addView(qb, qlp);
        }
        qtyRow.addView(quick);
        card.addView(qtyRow);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout cShift = new LinearLayout(this);
        cShift.setOrientation(LinearLayout.VERTICAL);
        cShift.addView(Ui.muted(this, "Shift"));
        final Spinner shiftSp = new Spinner(this);
        List<String> shifts = new ArrayList<>();
        shifts.add("— none —");
        for (String s : SHIFTS) shifts.add(s);
        shiftSp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, shifts));
        cShift.addView(shiftSp);
        LinearLayout cVch = new LinearLayout(this);
        cVch.setOrientation(LinearLayout.VERTICAL);
        cVch.addView(Ui.muted(this, "Voucher / Ref"));
        final EditText voucherEt = field("Voucher", "");
        cVch.addView(voucherEt);
        row2.addView(cShift, new LinearLayout.LayoutParams(0, -2, 1));
        row2.addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));
        row2.addView(cVch, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(row2);

        card.addView(Ui.muted(this, "Note"));
        final EditText noteEt = field("Note", "");
        noteEt.setMinLines(2);
        noteEt.setSingleLine(false);
        card.addView(noteEt);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, Ui.dp(this, 12), 0, 0);
        Button clear = Ui.secondary(this, "Clear");
        clear.setOnClickListener(v -> {
            qtyEt.setText(""); voucherEt.setText(""); noteEt.setText(""); shiftSp.setSelection(0);
        });
        Button save = Ui.primary(this, "✓  Save entry");
        save.setOnClickListener(v -> {
            String date = dateEt.getText().toString().trim();
            stockDate = date;
            String kind = PmDb.KINDS[kindIdx[0]];
            int code; double qty;
            try {
                int selected = codeSp.getSelectedItemPosition();
                if (selectedRecords.isEmpty() || selected < 0 || selected >= selectedRecords.size())
                    throw new Exception("pick");
                code = selectedRecords.get(selected).optInt("code");
                qty = Double.parseDouble(qtyEt.getText().toString().trim().replace(',', '.'));
            } catch (Exception e) {
                toast("Select item/product and enter quantity");
                return;
            }
            if (date.length() < 8) { toast("Invalid date"); return; }
            String shift = shiftSp.getSelectedItemPosition() == 0 ? "" : SHIFTS[shiftSp.getSelectedItemPosition() - 1];
            savedKindPosition = kindIdx[0];
            savedCode = code;
            pm.addEntry(kind, code, date, qty, shift, voucherEt.getText().toString().trim(), noteEt.getText().toString().trim());
            toast("Saved");
            qtyEt.setText(""); voucherEt.setText(""); noteEt.setText("");
            render();
        });
        actions.addView(clear, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));
        actions.addView(save, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(actions);
        add(card);

        LinearLayout list = Ui.card(this, Ui.CARD);
        list.addView(Ui.text(this, "Recent entries", 16, Color.BLACK, true));
        List<JSONObject> recent = pm.recentEntries(40);
        if (recent.isEmpty()) list.addView(Ui.muted(this, "No entries yet"));
        for (JSONObject e : recent) {
            long id = e.optLong("id");
            String kind = e.optString("kind");
            int code = e.optInt("code");
            String name = kind.equals("receive") || kind.equals("ret") ? pm.itemName(code) : pm.productName(code);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            TextView badge = Ui.text(this, kindLabel(kind), 11, Color.WHITE, true);
            int bg = kind.equals("output") ? Color.parseColor("#4CAF50")
                    : kind.equals("receive") ? Color.parseColor("#2196F3")
                    : kind.equals("delivery") ? Color.parseColor("#FF9800")
                    : kind.equals("reject") || kind.equals("ret") ? Color.parseColor("#F44336")
                    : Ui.NAVY;
            badge.setBackground(Ui.shape(this, bg, bg, 16));
            badge.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
            row.addView(badge);
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 8), 0);
            mid.addView(Ui.text(this, code + "  " + name, 14, Color.BLACK, true));
            String sub = e.optString("date");
            if (!e.optString("shift").isEmpty()) sub += " · " + e.optString("shift");
            if (!e.optString("voucher").isEmpty()) sub += " · " + e.optString("voucher");
            mid.addView(Ui.muted(this, sub));
            row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(Ui.text(this, PmDb.fmt(e.optDouble("qty")), 15, Color.BLACK, true));
            row.setOnLongClickListener(v -> {
                Guard.run(this, db, "Delete entry", true, () -> {
                    pm.deleteEntry(id);
                    toast("Deleted");
                    render();
                });
                return true;
            });
            list.addView(row);
            View div = new View(this);
            div.setBackgroundColor(Ui.LINE);
            list.addView(div, new LinearLayout.LayoutParams(-1, 1));
        }
        list.addView(Ui.muted(this, "Long-press a row to delete"));
        add(list);
    }

    private String kindLabel(String k) {
        for (int i = 0; i < PmDb.KINDS.length; i++) if (PmDb.KINDS[i].equals(k)) return PmDb.KIND_LABELS[i];
        return k;
    }

    private void showDaily() {
        LinearLayout top = Ui.card(this, Ui.CARD);
        top.addView(Ui.text(this, "Daily stock report", 16, Ui.NAVY_TEXT, true));
        final EditText dateEt = field("Date", stockDate);
        dateEt.setFocusable(false);
        dateEt.setOnClickListener(v -> pickDate(dateEt));
        top.addView(Ui.muted(this, "Date"));
        top.addView(dateEt);
        Button go = Ui.primary(this, "Show report");
        go.setOnClickListener(v -> {
            stockDate = dateEt.getText().toString().trim();
            render();
        });
        top.addView(go);
        add(top);

        String date = stockDate;
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Code", "Name", "UoM", "Opening", "Receive", "Use", "Closing", "Status"});
        for (JSONObject it : pm.items()) {
            int code = it.optInt("code");
            double open = pm.openingItem(code, date);
            double recv = pm.sumKindOn("receive", code, date) - pm.sumKindOn("ret", code, date);
            double use = pm.itemUseOn(code, date);
            double close = open + recv - use;
            String status = close < 0 ? "SHORT" : (close < 1 ? "LOW" : "OK");
            rows.add(new String[]{
                    String.valueOf(code), it.optString("name"), it.optString("uom"),
                    PmDb.fmt(open), PmDb.fmt(recv), PmDb.fmt(use), PmDb.fmt(close), status
            });
        }
        addReportTable("Daily Stock — " + date, rows, date);
    }


    private void showReturnDayWise() {
        pmNested = true;
        content.removeAllViews();
        LinearLayout head = Ui.card(this, Ui.CARD);
        head.addView(Ui.text(this, "Return — day-wise", 17, Ui.NAVY_TEXT, true));
        head.addView(Ui.muted(this, "Packaging material returns from Entry → Return. Closing uses − Return."));
        final EditText ymEt = field("Month YYYY-MM", monthYm);
        head.addView(Ui.muted(this, "Month"));
        head.addView(ymEt);
        Button load = Ui.primary(this, "Show day-wise");
        head.addView(load);
        Button back = Ui.secondary(this, "Back to Monthly");
        back.setOnClickListener(v -> { pmNested = false; tab = 3; render(); });
        head.addView(back);
        add(head);
        final LinearLayout host = new LinearLayout(this);
        host.setOrientation(LinearLayout.VERTICAL);
        add(host);
        load.setOnClickListener(v -> {
            host.removeAllViews();
            String ym = ymEt.getText().toString().trim();
            if (ym.length() < 7) { toast("YYYY-MM"); return; }
            monthYm = ym;
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"Date", "Code", "Item", "Qty", "Shift", "Note"});
            double total = 0;
            try {
                for (JSONObject e : pm.recentEntries(5000)) {
                    if (!"ret".equals(e.optString("kind"))) continue;
                    String day = e.optString("date");
                    if (!day.startsWith(ym)) continue;
                    int code = e.optInt("code");
                    String name = "";
                    for (JSONObject it : pm.items()) {
                        if (it.optInt("code") == code) { name = it.optString("name"); break; }
                    }
                    double q = e.optDouble("qty", 0);
                    total += q;
                    rows.add(new String[]{
                        day, String.valueOf(code), name,
                        PmDb.fmt(q), e.optString("shift"), e.optString("note")
                    });
                }
            } catch (Exception ex) { toast(ex.getMessage()); return; }
            if (rows.size() == 1) {
                host.addView(Ui.muted(this, "No return entries this month"));
                return;
            }
            rows.add(new String[]{"TOTAL", "", "", PmDb.fmt(total), "", ""});
            String title = "Packaging Material Return — " + ym;
            LinearLayout card = Ui.card(this, Ui.CARD);
            card.addView(Ui.text(this, title, 15, Ui.NAVY_TEXT, true));
            card.addView(Ui.muted(this, db.setting("company", "")));
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            LinearLayout table = new LinearLayout(this);
            table.setOrientation(LinearLayout.VERTICAL);
            for (int r = 0; r < rows.size(); r++) {
                String[] row = rows.get(r);
                LinearLayout line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8));
                if (r == 0) line.setBackgroundColor(Ui.SOFT_BLUE);
                if ("TOTAL".equals(row[0])) line.setBackgroundColor(Color.parseColor("#EEEEEE"));
                for (int c = 0; c < row.length; c++) {
                    TextView tv = Ui.text(this, row[c] == null ? "" : row[c], r == 0 ? 11 : 12,
                            r == 0 || "TOTAL".equals(row[0]) ? Ui.NAVY_TEXT : Ui.INK, r == 0);
                    tv.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
                    tv.setMinWidth(Ui.dp(this, c == 2 ? 120 : 64));
                    line.addView(tv);
                }
                table.addView(line);
            }
            hsv.addView(table);
            card.addView(hsv);
            final List<String[]> rowsF = rows;
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button csvB = Ui.secondary(this, "CSV");
            csvB.setOnClickListener(vv -> exportCsv(title, rowsF, "return_" + ym));
            actions.addView(csvB, Ui.lp(this, 0, Ui.dp(this, 44), 1, 0, 0, 6, 0));
            Button pdfB = Ui.primary(this, "PDF / Print");
            pdfB.setOnClickListener(vv -> exportPdf(title, rowsF, "return_" + ym));
            actions.addView(pdfB, Ui.lp(this, 0, Ui.dp(this, 44), 1, 6, 0, 0, 0));
            card.addView(actions);
            host.addView(card);
        });
    }

    private void showMonthly() {
        LinearLayout top = Ui.card(this, Ui.CARD);
        top.addView(Ui.text(this, "Monthly reports", 16, Ui.NAVY_TEXT, true));
        final EditText ymEt = field("Month (YYYY-MM)", monthYm);
        top.addView(Ui.muted(this, "Month"));
        top.addView(ymEt);
        Button wastageSheet = Ui.secondary(this, "🗑️ Packaging Material Wastage (day-wise)");
        Button returnSheet = Ui.secondary(this, "↩️ Return (day-wise)");
        returnSheet.setOnClickListener(v -> showReturnDayWise());
        top.addView(returnSheet);
        wastageSheet.setOnClickListener(v -> showPackagingWastageDayWise());
        top.addView(wastageSheet);
        final Spinner repSp = new Spinner(this);
        String[] reps = {"Receive", "Material Use", "Output (Ctn)", "Delivery (Ctn)", "QA Hold", "Reject"};
        repSp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, reps));
        top.addView(Ui.muted(this, "Report type"));
        top.addView(repSp);
        Button go = Ui.primary(this, "Show report");
        go.setOnClickListener(v -> {
            monthYm = ymEt.getText().toString().trim();
            render();
        });
        top.addView(go);
        add(top);

        // Note: spinner selection resets on render; default first report after change is fine
        String ym = monthYm;
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Code", "Name", "UoM", "Total Receive"});
        for (JSONObject it : pm.items()) {
            int code = it.optInt("code");
            double r = pm.sumKindMonth("receive", code, ym) - pm.sumKindMonth("ret", code, ym);
            if (Math.abs(r) < 1e-9) continue;
            rows.add(new String[]{String.valueOf(code), it.optString("name"), it.optString("uom"), PmDb.fmt(r)});
        }
        addReportTable("Monthly Receive — " + ym, rows, ym);

        List<String[]> useRows = new ArrayList<>();
        useRows.add(new String[]{"Code", "Name", "UoM", "Total Use"});
        for (JSONObject it : pm.items()) {
            int code = it.optInt("code");
            double u = pm.itemUseMonth(code, ym);
            if (Math.abs(u) < 1e-9) continue;
            useRows.add(new String[]{String.valueOf(code), it.optString("name"), it.optString("uom"), PmDb.fmt(u)});
        }
        addReportTable("Monthly Material Use — " + ym, useRows, ym + "_use");

        for (int ri = 2; ri < PmDb.KINDS.length; ri++) {
            String kind = PmDb.KINDS[ri];
            List<String[]> pr = new ArrayList<>();
            pr.add(new String[]{"Code", "Name", "Total"});
            for (JSONObject p : pm.products()) {
                int code = p.optInt("code");
                double t = pm.sumKindMonth(kind, code, ym);
                if (Math.abs(t) < 1e-9) continue;
                pr.add(new String[]{String.valueOf(code), p.optString("name"), PmDb.fmt(t)});
            }
            addReportTable("Monthly " + PmDb.KIND_LABELS[ri] + " — " + ym, pr, ym + "_" + kind);
        }
    }

    private void showProduction() {
        LinearLayout top = Ui.card(this, Ui.CARD);
        top.addView(Ui.text(this, "Daily production summary", 16, Ui.NAVY_TEXT, true));
        final EditText dateEt = field("Date", stockDate);
        dateEt.setFocusable(false);
        dateEt.setOnClickListener(v -> pickDate(dateEt));
        top.addView(Ui.muted(this, "Date"));
        top.addView(dateEt);
        Button go = Ui.primary(this, "Show");
        go.setOnClickListener(v -> {
            stockDate = dateEt.getText().toString().trim();
            render();
        });
        top.addView(Ui.muted(this, "Opening = month Production Opening sheet (day 1) then previous day closing. Closing = Opening + Output − Delivery − Reject."));
        top.addView(go);
        Button monthlyRep = Ui.secondary(this, "Monthly Production Report");
        monthlyRep.setOnClickListener(v -> showMonthlyProductionReport());
        top.addView(monthlyRep);
        add(top);

        String date = stockDate;
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Code", "Product", "Opening", "Output", "Delivery", "Hold", "Reject", "Closing"});
        for (JSONObject p : pm.products()) {
            int code = p.optInt("code");
            double open = pm.openingProduct(code, date);
            double o = pm.sumKindOn("output", code, date);
            double d = pm.sumKindOn("delivery", code, date);
            double h = pm.sumKindOn("hold", code, date);
            double r = pm.sumKindOn("reject", code, date);
            double close = pm.closingProduct(code, date);
            if (Math.abs(open) + o + d + h + r < 1e-9) continue;
            rows.add(new String[]{String.valueOf(code), p.optString("name"),
                    PmDb.fmt(open), PmDb.fmt(o), PmDb.fmt(d), PmDb.fmt(h), PmDb.fmt(r), PmDb.fmt(close)});
        }
        if (rows.size() == 1) {
            LinearLayout empty = Ui.card(this, Ui.CARD);
            empty.addView(Ui.muted(this, "No production entries on " + date));
            add(empty);
        } else {
            addReportTable("Production — " + date, rows, date);
        }
    }



    /** Month sheet: Opening (Master opening that month) + Prod − Reject − Delivery = Closing.
        Next month Opening should be this Closing (carry button). Unit: CTN/PCS. */
    private void showMonthlyProductionReport() {
        pmNested = true;
        content.removeAllViews();
        LinearLayout head = Ui.card(this, Ui.CARD);
        head.addView(Ui.text(this, "Monthly Production Report", 17, Color.BLACK, true));
        head.addView(Ui.muted(this, "Unit: CTN/PCS. Opening = Master → Production Opening for this month. Closing = Opening + Production − Reject − Delivery. Next month Opening = this Closing."));
        final EditText ymEt = field("Month YYYY-MM", monthYm);
        head.addView(Ui.muted(this, "Month"));
        head.addView(ymEt);
        Button load = Ui.primary(this, "Show report");
        head.addView(load);
        Button back = Ui.secondary(this, "Back to Production");
        back.setOnClickListener(v -> { pmNested = false; tab = 4; render(); });
        head.addView(back);
        add(head);

        final LinearLayout host = new LinearLayout(this);
        host.setOrientation(LinearLayout.VERTICAL);
        add(host);

        load.setOnClickListener(v -> {
            host.removeAllViews();
            String ym = ymEt.getText().toString().trim();
            if (ym.length() < 7) { toast("Use YYYY-MM"); return; }
            monthYm = ym;

            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"Item (code + name)", "Opening", "Production", "Reject", "Delivery", "Closing"});

            double tO = 0, tP = 0, tR = 0, tD = 0, tC = 0;
            for (JSONObject p : pm.products()) {
                int code = p.optInt("code");
                String item = code + " — " + p.optString("name");
                double open = pm.getProductOpening(code, ym);
                double prod = pm.sumKindMonth("output", code, ym);
                double rej = pm.sumKindMonth("reject", code, ym);
                double del = pm.sumKindMonth("delivery", code, ym);
                double close = open + prod - rej - del;
                if (Math.abs(open) + prod + rej + del < 1e-9) continue;
                rows.add(new String[]{
                    item, PmDb.fmt(open), PmDb.fmt(prod), PmDb.fmt(rej), PmDb.fmt(del), PmDb.fmt(close)
                });
                tO += open; tP += prod; tR += rej; tD += del; tC += close;
            }
            if (rows.size() == 1) {
                host.addView(Ui.muted(this, "No movement this month. Set openings in Master → Production Opening."));
                return;
            }
            rows.add(new String[]{"TOTAL", PmDb.fmt(tO), PmDb.fmt(tP), PmDb.fmt(tR), PmDb.fmt(tD), PmDb.fmt(tC)});

            String title = "Monthly Production Report — " + ym + " (CTN/PCS)";
            // table + PDF/CSV via existing helper pattern
            LinearLayout card = Ui.card(this, Ui.CARD);
            card.addView(Ui.text(this, title, 15, Ui.NAVY_TEXT, true));
            card.addView(Ui.muted(this, db.setting("company", "")));
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            LinearLayout table = new LinearLayout(this);
            table.setOrientation(LinearLayout.VERTICAL);
            for (int r = 0; r < rows.size(); r++) {
                String[] row = rows.get(r);
                LinearLayout line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setPadding(Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6));
                boolean headRow = r == 0;
                boolean tot = "TOTAL".equals(row[0]);
                if (headRow) line.setBackgroundColor(Ui.SOFT_BLUE);
                else if (tot) line.setBackgroundColor(Color.parseColor("#EEEEEE"));
                for (int c = 0; c < row.length; c++) {
                    TextView tv = Ui.text(this, row[c] == null ? "" : row[c], headRow ? 11 : 12,
                            headRow || tot ? Ui.NAVY_TEXT : Ui.INK, headRow || tot);
                    tv.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
                    tv.setMinWidth(Ui.dp(this, c == 0 ? 160 : 72));
                    line.addView(tv);
                }
                table.addView(line);
            }
            hsv.addView(table);
            card.addView(hsv);

            final List<String[]> rowsF = rows;
            final String ymF = ym;
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button csvB = Ui.secondary(this, "CSV");
            csvB.setOnClickListener(vv -> exportCsv(title, rowsF, "mprod_" + ymF));
            actions.addView(csvB, Ui.lp(this, 0, Ui.dp(this, 44), 1, 0, 0, 6, 0));
            Button pdfB = Ui.secondary(this, "PDF / Print");
            pdfB.setOnClickListener(vv -> exportPdf(title, rowsF, "mprod_" + ymF));
            actions.addView(pdfB, Ui.lp(this, 0, Ui.dp(this, 44), 1, 6, 0, 6, 0));
            Button boss = Ui.primary(this, "Send to boss");
            boss.setOnClickListener(vv -> {
                StringBuilder sb = new StringBuilder();
                sb.append(title).append("\n").append(db.setting("company","")).append("\n\n");
                for (String[] row : rowsF) {
                    sb.append(TextUtils.join(" | ", row)).append("\n");
                }
                sb.append("\nUnit: CTN/PCS\n");
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                String email = db.setting("boss_email", "");
                if (email != null && email.contains("@"))
                    i.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
                i.putExtra(Intent.EXTRA_SUBJECT, title);
                i.putExtra(Intent.EXTRA_TEXT, sb.toString());
                startActivity(Intent.createChooser(i, "Send monthly production to boss"));
            });
            actions.addView(boss, Ui.lp(this, 0, Ui.dp(this, 44), 1, 6, 0, 0, 0));
            card.addView(actions);

            Button carry = Ui.secondary(this, "Carry closings → next month Opening");
            carry.setOnClickListener(vv -> Guard.run(this, db, "Carry month openings", true, () -> {
                try {
                    String[] p = ymF.split("-");
                    int y = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
                    m++; if (m > 12) { m = 1; y++; }
                    String nextYm = String.format(java.util.Locale.US, "%04d-%02d", y, m);
                    int n = 0;
                    for (JSONObject pr : pm.products()) {
                        int code = pr.optInt("code");
                        double open = pm.getProductOpening(code, ymF);
                        double prod = pm.sumKindMonth("output", code, ymF);
                        double rej = pm.sumKindMonth("reject", code, ymF);
                        double del = pm.sumKindMonth("delivery", code, ymF);
                        double close = open + prod - rej - del;
                        if (Math.abs(open) + prod + rej + del < 1e-9) continue;
                        pm.setProductOpening(code, nextYm, close);
                        n++;
                    }
                    toast("Set " + n + " openings for " + nextYm);
                } catch (Exception e) { toast(e.getMessage()); }
            }));
            card.addView(carry);
            card.addView(Ui.muted(this, "Stock count (physical): use main app More → Stock Count for RM. FG count can be cross-checked from Closing column."));
            host.addView(card);
        });
    }

    private void showFgDaily() {
        final String[] SH = {"A", "B", "C", "General"};
        LinearLayout head = Ui.card(this, Ui.CARD);
        head.addView(Ui.text(this, "FG Daily Sheet", 17, Color.BLACK, true));
        head.addView(Ui.muted(this, "Form layout for boss submit. PDF / CSV from table. Available = Opening+Prod+QA Pass−(Today Hold+Previous Hold). Closing = Open+Prod−Delivery."));
        final EditText dateEt = field("Date", stockDate);
        dateEt.setFocusable(false);
        dateEt.setOnClickListener(v -> pickDate(dateEt));
        head.addView(Ui.muted(this, "Date"));
        head.addView(dateEt);
        Button go = Ui.primary(this, "Show form");
        head.addView(go);
        Button monthBtn = Ui.secondary(this, "Month day-wise records");
        monthBtn.setOnClickListener(v -> showFgMonthly());
        head.addView(monthBtn);
        add(head);

        final LinearLayout sheetHost = new LinearLayout(this);
        sheetHost.setOrientation(LinearLayout.VERTICAL);
        add(sheetHost);

        Runnable fill = () -> {
            // rebuild only table area: clear children of root content after head — use host
            sheetHost.removeAllViews();
            // temporarily point add() target — we append into host manually
            String date = dateEt.getText().toString().trim();
            stockDate = date;
            if (date.length() < 8) { toast("Invalid date"); return; }

            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{
                "Item", "Shift", "Opening", "Previous Hold", "Today Hold",
                "Today Prod", "Today QA Pass", "Available", "Delivery", "Closing"
            });

            List<JSONObject> products = pm.products();
            if (products.isEmpty()) {
                sheetHost.addView(Ui.muted(this, "No products — add in Master"));
                return;
            }

            for (JSONObject pr : products) {
                int code = pr.optInt("code");
                String pname = code + " " + pr.optString("name");
                double open = pm.openingProduct(code, date);
                double sumProd = 0, sumHold = 0, sumPrev = 0, sumPass = 0, sumDel = 0, sumAvail = 0, sumClose = 0;
                boolean first = true;
                for (String sh : SH) {
                    double prod = pm.todayProduction(code, date, sh);
                    double hold = pm.todayHold(code, date, sh);
                    double prev = pm.previousHold(code, date, sh);
                    double pass = pm.todayQaPass(code, date, sh);
                    double del = pm.todayDelivery(code, date, sh);
                    double avail = open + prod + pass - (hold + prev);
                    double close = open + prod - del;
                    sumProd += prod; sumHold += hold; sumPrev += prev; sumPass += pass;
                    sumDel += del; sumAvail += avail; sumClose += close;
                    rows.add(new String[]{
                        first ? pname : "",
                        sh,
                        PmDb.fmt(open),
                        PmDb.fmt(prev),
                        PmDb.fmt(hold),
                        PmDb.fmt(prod),
                        PmDb.fmt(pass),
                        PmDb.fmt(avail),
                        PmDb.fmt(del),
                        PmDb.fmt(close)
                    });
                    first = false;
                }
                rows.add(new String[]{
                    "TOTAL", "", PmDb.fmt(open),
                    PmDb.fmt(sumPrev), PmDb.fmt(sumHold), PmDb.fmt(sumProd),
                    PmDb.fmt(sumPass), PmDb.fmt(sumAvail), PmDb.fmt(sumDel), PmDb.fmt(sumClose)
                });
            }

            // Build table into host with PDF/CSV (same as addReportTable but into host)
            String title = "FG Daily Sheet — " + date;
            LinearLayout card = Ui.card(this, Ui.CARD);
            card.addView(Ui.text(this, title, 15, Ui.NAVY_TEXT, true));
            card.addView(Ui.muted(this, db.setting("company", "")));
            // Fixed column widths — no left clip, numbers right-aligned
            final int[] colW = {
                Ui.dp(this, 150), // Item
                Ui.dp(this, 64),  // Shift
                Ui.dp(this, 72),  // Opening
                Ui.dp(this, 100), // Previous Hold
                Ui.dp(this, 88),  // Today Hold
                Ui.dp(this, 80),  // Today Prod
                Ui.dp(this, 96),  // Today QA Pass
                Ui.dp(this, 80),  // Available
                Ui.dp(this, 72),  // Delivery
                Ui.dp(this, 72)   // Closing
            };
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            hsv.setHorizontalScrollBarEnabled(true);
            hsv.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            hsv.setClipToPadding(false);
            LinearLayout table = new LinearLayout(this);
            table.setOrientation(LinearLayout.VERTICAL);
            table.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
            for (int r = 0; r < rows.size(); r++) {
                String[] row = rows.get(r);
                LinearLayout line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(android.view.Gravity.CENTER_VERTICAL);
                line.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
                boolean isHead = r == 0;
                boolean isTotal = row[0] != null && row[0].equals("TOTAL");
                if (isHead) line.setBackgroundColor(Ui.SOFT_BLUE);
                else if (isTotal) line.setBackgroundColor(Color.parseColor("#EEEEEE"));
                for (int c = 0; c < row.length; c++) {
                    TextView tv = Ui.text(this, row[c] == null ? "" : row[c],
                            isHead ? 11 : 12,
                            isHead || isTotal ? Ui.NAVY_TEXT : Ui.INK,
                            isHead || isTotal);
                    tv.setSingleLine(true);
                    tv.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
                    // text cols left, number cols right — zeros line up
                    if (c >= 2) tv.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
                    else tv.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
                    int w = c < colW.length ? colW[c] : Ui.dp(this, 72);
                    line.addView(tv, new LinearLayout.LayoutParams(w, -2));
                }
                table.addView(line);
            }
            hsv.addView(table);
            card.addView(hsv);
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button csvB = Ui.secondary(this, "CSV");
            final List<String[]> rowsF = rows;
            csvB.setOnClickListener(v -> exportCsv(title, rowsF, "fg_" + date));
            actions.addView(csvB, Ui.lp(this, 0, Ui.dp(this, 44), 1, 0, 0, 6, 0));
            Button pdfB = Ui.primary(this, "PDF / Print");
            pdfB.setOnClickListener(v -> exportPdf(title, rowsF, "fg_" + date));
            actions.addView(pdfB, Ui.lp(this, 0, Ui.dp(this, 44), 1, 6, 0, 0, 0));
            card.addView(actions);
            card.addView(Ui.muted(this, "Prepare By (Production) ________    AM (Production) ________"));
            sheetHost.addView(card);
        };
        go.setOnClickListener(v -> fill.run());
        fill.run();
    }

    private void showFgMonthly() {
        pmNested = true;
        content.removeAllViews();
        LinearLayout card = Ui.card(this, Ui.CARD);
        card.addView(Ui.text(this, "FG Daily — Month day-wise", 16, Ui.NAVY_TEXT, true));
        final EditText ymEt = field("Month YYYY-MM", monthYm);
        card.addView(Ui.muted(this, "Month"));
        card.addView(ymEt);
        Button load = Ui.primary(this, "Load days");
        card.addView(load);
        Button back = Ui.secondary(this, "Back to FG Daily");
        back.setOnClickListener(v -> { pmNested = false; tab = 5; render(); });
        card.addView(back);
        add(card);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        add(list);
        load.setOnClickListener(v -> {
            list.removeAllViews();
            String ym = ymEt.getText().toString().trim();
            if (ym.length() < 7) { toast("YYYY-MM"); return; }
            monthYm = ym;
            // days that have any FG movement
            try {
                java.util.LinkedHashSet<String> days = new java.util.LinkedHashSet<>();
                for (JSONObject e : pm.recentEntries(5000)) {
                    String d = e.optString("date");
                    if (d.startsWith(ym)) {
                        String k = e.optString("kind");
                        if (k.equals("output") || k.equals("hold") || k.equals("qa_pass") || k.equals("delivery") || k.equals("reject"))
                            days.add(d);
                    }
                }
                if (days.isEmpty()) list.addView(Ui.muted(this, "No FG entries this month"));
                java.util.ArrayList<String> sorted = new java.util.ArrayList<>(days);
                java.util.Collections.sort(sorted);
                java.util.Collections.reverse(sorted);
                for (String d : sorted) {
                    Button b = Ui.secondary(this, d + "  → open FG sheet");
                    b.setOnClickListener(vv -> {
                        stockDate = d;
                        tab = 5;
                        render();
                    });
                    list.addView(b);
                }
            } catch (Exception ex) { toast(ex.getMessage()); }
        });
    }

    private void showMaster() {
        LinearLayout itemsCard = Ui.card(this, Ui.CARD);
        itemsCard.addView(Ui.text(this, "Packaging items", 16, Ui.NAVY_TEXT, true));
        final EditText ic = field("Code", "");
        ic.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText in = field("Name", "");
        final EditText iu = field("UoM", "kg");
        final EditText io = field("Opening", "0");
        io.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        itemsCard.addView(Ui.muted(this, "Code")); itemsCard.addView(ic);
        itemsCard.addView(Ui.muted(this, "Name")); itemsCard.addView(in);
        itemsCard.addView(Ui.muted(this, "UoM")); itemsCard.addView(iu);
        itemsCard.addView(Ui.muted(this, "Opening stock")); itemsCard.addView(io);
        Button addItem = Ui.primary(this, "Add / update item");
        addItem.setOnClickListener(v -> {
            try {
                int code = Integer.parseInt(ic.getText().toString().trim());
                String name = in.getText().toString().trim();
                if (name.isEmpty()) { toast("Name required"); return; }
                double open = Double.parseDouble(io.getText().toString().trim().replace(',', '.'));
                pm.upsertItem(code, name, iu.getText().toString().trim(), open);
                toast("Item saved");
                render();
            } catch (Exception e) { toast("Invalid code/opening"); }
        });
        itemsCard.addView(addItem);
        for (JSONObject it : pm.items()) {
            int code = it.optInt("code");
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 4));
            row.addView(Ui.text(this, code + " · " + it.optString("name") + " · " + it.optString("uom")
                    + " · open " + PmDb.fmt(it.optDouble("open")), 13, Ui.INK, false));
            TextView editItem=Ui.text(this,"Edit",12,Ui.NAVY_TEXT,true);
            editItem.setOnClickListener(v->{
                ic.setText(String.valueOf(code));
                in.setText(it.optString("name"));
                iu.setText(it.optString("uom"));
                io.setText(PmDb.fmt(it.optDouble("open")));
            });
            row.addView(editItem);
            TextView del = Ui.text(this, "Delete", 12, Ui.BAD, true);
            del.setOnClickListener(v -> Guard.run(this, db, "Delete item", true, () -> {
                pm.deleteItem(code);
                render();
            }));
            row.addView(del);
            itemsCard.addView(row);
        }
        add(itemsCard);

        LinearLayout prodCard = Ui.card(this, Ui.CARD);
        prodCard.addView(Ui.text(this, "Products", 16, Ui.NAVY_TEXT, true));
        final EditText pc = field("Code", "");
        pc.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText pn = field("Name", "");
        prodCard.addView(Ui.muted(this, "Code")); prodCard.addView(pc);
        prodCard.addView(Ui.muted(this, "Name")); prodCard.addView(pn);
        Button addProd = Ui.primary(this, "Add / update product");
        addProd.setOnClickListener(v -> {
            try {
                int code = Integer.parseInt(pc.getText().toString().trim());
                String name = pn.getText().toString().trim();
                if (name.isEmpty()) { toast("Name required"); return; }
                pm.upsertProduct(code, name);
                toast("Product saved");
                render();
            } catch (Exception e) { toast("Invalid code"); }
        });
        prodCard.addView(addProd);
        for (JSONObject p : pm.products()) {
            int code = p.optInt("code");
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 4));
            row.addView(Ui.text(this, code + " · " + p.optString("name"), 13, Ui.INK, false));
            List<JSONObject> rec = pm.recipesFor(code);
            if (!rec.isEmpty()) {
                StringBuilder sb = new StringBuilder("Recipe: ");
                for (JSONObject r : rec) sb.append(r.optInt("i")).append("=").append(PmDb.fmt(r.optDouble("q"))).append("  ");
                row.addView(Ui.muted(this, sb.toString()));
            }
            TextView editRec = Ui.text(this, "Edit recipe", 12, Ui.NAVY_TEXT, true);
            editRec.setOnClickListener(v -> editRecipeDialog(code, p.optString("name")));
            row.addView(editRec);
            TextView del = Ui.text(this, "Delete", 12, Ui.BAD, true);
            del.setOnClickListener(v -> Guard.run(this, db, "Delete product", true, () -> {
                pm.deleteProduct(code);
                render();
            }));
            row.addView(del);
            prodCard.addView(row);
        }
        add(prodCard);

        // ---- Production Opening sheet (monthly baseline for Production tab) ----
        LinearLayout openCard = Ui.card(this, Ui.CARD);
        openCard.addView(Ui.text(this, "Production Opening (monthly)", 16, Ui.NAVY_TEXT, true));
        openCard.addView(Ui.muted(this, "Set opening stock for each product for a month. On the 1st, Production Opening = this value. Later days = previous day closing. New products appear here automatically."));
        final EditText openYm = field("Month (YYYY-MM)", monthYm);
        openCard.addView(Ui.muted(this, "Month"));
        openCard.addView(openYm);
        Button loadOpen = Ui.secondary(this, "Load month");
        openCard.addView(loadOpen);
        final LinearLayout openList = new LinearLayout(this);
        openList.setOrientation(LinearLayout.VERTICAL);
        openCard.addView(openList);
        final Runnable[] fillOpen = new Runnable[1];
        fillOpen[0] = () -> {
            openList.removeAllViews();
            String ym = openYm.getText().toString().trim();
            if (ym.length() < 7) { toast("Month YYYY-MM"); return; }
            monthYm = ym;
            for (JSONObject pr : pm.products()) {
                int code = pr.optInt("code");
                double q = pm.getProductOpening(code, ym);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
                row.addView(Ui.text(this, code + " · " + pr.optString("name") + " · open " + PmDb.fmt(q), 13, Ui.INK, false));
                final EditText qtyEt = field("Opening qty", PmDb.fmt(q));
                qtyEt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                row.addView(qtyEt);
                LinearLayout btns = new LinearLayout(this);
                btns.setOrientation(LinearLayout.HORIZONTAL);
                Button saveB = Ui.primary(this, "Save");
                Button delB = Ui.secondary(this, "Delete");
                final int fcode = code;
                final String fym = ym;
                saveB.setOnClickListener(v2 -> {
                    try {
                        double vq = Double.parseDouble(qtyEt.getText().toString().trim().replace(',', '.'));
                        pm.setProductOpening(fcode, fym, vq);
                        toast("Opening saved");
                        fillOpen[0].run();
                    } catch (Exception e) { toast("Invalid qty"); }
                });
                delB.setOnClickListener(v2 -> Guard.run(this, db, "Delete production opening", true, () -> {
                    pm.deleteProductOpening(fcode, fym);
                    toast("Opening cleared");
                    fillOpen[0].run();
                }));
                btns.addView(saveB);
                btns.addView(delB);
                row.addView(btns);
                openList.addView(row);
            }
            if (pm.products().isEmpty()) {
                openList.addView(Ui.muted(this, "No products yet — add products above first."));
            }
        };
        loadOpen.setOnClickListener(v -> fillOpen[0].run());
        fillOpen[0].run();
        add(openCard);

    
    }


    private void editRecipeDialog(int pCode, String pName) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), 0);
        final List<JSONObject> recipeItems=pm.items();
        final List<String> recipeLabels=new ArrayList<>();
        for(JSONObject item:recipeItems)
            recipeLabels.add(item.optInt("code")+" — "+item.optString("name"));
        if(recipeLabels.isEmpty()) recipeLabels.add("No items available");
        final Spinner iSp=new Spinner(this);
        iSp.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,recipeLabels));
        final EditText qEt = field("Qty per ctn", "");
        qEt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(Ui.muted(this, "Item")); box.addView(iSp);
        box.addView(Ui.muted(this, "Qty per carton")); box.addView(qEt);
        List<JSONObject> existing = pm.recipesFor(pCode);
        if (!existing.isEmpty()) {
            StringBuilder sb = new StringBuilder("Current:\n");
            for (JSONObject r : existing)
                sb.append(r.optInt("i")).append(" ").append(r.optString("name"))
                        .append(" = ").append(PmDb.fmt(r.optDouble("q"))).append("\n");
            box.addView(Ui.muted(this, sb.toString()));
        }
        new AlertDialog.Builder(this)
                .setTitle("Recipe · " + pCode + " " + pName)
                .setView(box)
                .setPositiveButton("Save line", (d, w) -> {
                    try {
                        if(recipeItems.isEmpty()){toast("Add an item first");return;}
                        int i=recipeItems.get(iSp.getSelectedItemPosition()).optInt("code");
                        double q = Double.parseDouble(qEt.getText().toString().trim().replace(',', '.'));
                        pm.setRecipe(pCode, i, q);
                        toast("Recipe updated");
                        render();
                    } catch (Exception e) { toast("Invalid"); }
                })
                .setNeutralButton("Clear item line", (d, w) -> {
                    try {
                        if(recipeItems.isEmpty()){toast("Add an item first");return;}
                        int i=recipeItems.get(iSp.getSelectedItemPosition()).optInt("code");
                        pm.setRecipe(pCode, i, 0);
                        render();
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showSettings() {
        LinearLayout card = Ui.card(this, Ui.CARD);
        card.addView(Ui.text(this, "Settings", 16, Ui.NAVY_TEXT, true));
        card.addView(Ui.muted(this, "Company name is edited once in the main app: More → Settings. Current: " + db.setting("company", "—")));
        final EditText prepared = field("Prepared by", pm.meta("preparedBy", ""));
        final EditText appTitle = field("App title", pm.meta("appTitle", "Daily PM Report"));
        card.addView(Ui.muted(this, "Prepared by (reports)")); card.addView(prepared);
        card.addView(Ui.muted(this, "PM screen title")); card.addView(appTitle);
        Button save = Ui.primary(this, "Save settings");
        save.setOnClickListener(v -> {
            pm.setMeta("preparedBy", prepared.getText().toString().trim());
            pm.setMeta("appTitle", appTitle.getText().toString().trim());
            toast("Saved");
            render();
        });
        card.addView(save);
        add(card);

        LinearLayout sec = Ui.card(this, Ui.CARD);
        sec.addView(Ui.text(this, "Security", 15, Ui.NAVY_TEXT, true));
        sec.addView(Ui.muted(this, "Same fingerprint / PIN lock as the main RM Stock app. Asked before Master and every delete. App Settings: main app More → Settings."));
        Button gb = Ui.secondary(this, "Protection: " + (Guard.enabled(db) ? "ON — tap to turn off" : "OFF — tap to turn on"));
        gb.setOnClickListener(v -> {
            if (Guard.enabled(db)) {
                Guard.run(this, db, "Turn protection off", true, () -> {
                    db.setSetting("guard", "0");
                    render();
                });
            } else {
                db.setSetting("guard", "1");
                render();
            }
        });
        sec.addView(gb);
        add(sec);
    }

    private void showBackup() {
        LinearLayout card = Ui.card(this, Ui.CARD);
        card.addView(Ui.text(this, "Unified backup", 16, Ui.NAVY_TEXT, true));
        card.addView(Ui.muted(this, "RM + PM + Yield are backed up together from the main app:\nMore → Settings → Unified backup (RM + PM + Yield).\n\nOne JSON file restores everything. Separate PM-only export is no longer used."));
        card.addView(Ui.muted(this, "To clear PM entries: main app More → Settings → Data clean-up."));
        add(card);
    }

    private void addReportTable(String title, List<String[]> rows, String tag) {
        LinearLayout card = Ui.card(this, Ui.CARD);
        card.addView(Ui.text(this, title, 15, Ui.NAVY_TEXT, true));
        card.addView(Ui.muted(this, db.setting("company","")));
        if (rows.size() <= 1) {
            card.addView(Ui.muted(this, "No data for this period"));
            add(card);
            return;
        }
        for (int r = 0; r < Math.min(rows.size(), 80); r++) {
            String[] row = rows.get(r);
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));
            if (r == 0) line.setBackgroundColor(Ui.SOFT_BLUE);
            for (int c = 0; c < row.length; c++) {
                TextView tv = Ui.text(this, row[c] == null ? "" : row[c], r == 0 ? 11 : 12,
                        r == 0 ? Ui.NAVY_TEXT : Ui.INK, r == 0);
                tv.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
                line.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, c == 1 ? 2f : 1f));
            }
            if (r > 0 && row.length > 7 && "SHORT".equals(row[7])) line.setBackgroundColor(Ui.SOFT_RED);
            else if (r > 0 && row.length > 7 && "LOW".equals(row[7])) line.setBackgroundColor(Ui.SOFT_AMBER);
            card.addView(line);
        }
        if (rows.size() > 80) card.addView(Ui.muted(this, "… " + (rows.size() - 80) + " more rows (full data in CSV/PDF)"));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button csvB = Ui.secondary(this, "CSV");
        csvB.setOnClickListener(v -> exportCsv(title, rows, tag));
        actions.addView(csvB, Ui.lp(this, 0, Ui.dp(this, 44), 1, 0, 0, 6, 0));
        Button pdfB = Ui.secondary(this, "PDF");
        pdfB.setOnClickListener(v -> exportPdf(title, rows, tag));
        actions.addView(pdfB, Ui.lp(this, 0, Ui.dp(this, 44), 1, 6, 0, 0, 0));
        card.addView(actions);
        add(card);
    }

    private void exportCsv(String title, List<String[]> rows, String tag) {
        try {
            StringBuilder sb = new StringBuilder("\uFEFF");
            sb.append(title).append("\r\n");
            sb.append(db.setting("company","")).append("\r\n");
            for (String[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) sb.append(',');
                    String cell = row[i] == null ? "" : row[i].replace("\"", "\"\"");
                    sb.append('"').append(cell).append('"');
                }
                sb.append("\r\n");
            }
            File dir = new File(getCacheDir(), "pm");
            dir.mkdirs();
            String safe = title.replaceAll("[^A-Za-z0-9._-]+", "_");
            File f = new File(dir, safe + "_" + tag + ".csv");
            try (FileOutputStream o = new FileOutputStream(f)) {
                o.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            offerFile(f, "text/csv");
        } catch (Exception e) {
            toast("CSV error: " + e.getMessage());
        }
    }


    /**
     * Fit a PDF table cell within the available width.
     * Long text is shortened with an ellipsis.
     */
    private String fitPdfCell(String text, float maxWidth, android.graphics.Paint paint) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        if (paint == null || maxWidth <= 0) {
            return "";
        }

        if (paint.measureText(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "\u2026";
        float availableWidth = maxWidth - paint.measureText(ellipsis);

        if (availableWidth <= 0) {
            return "";
        }

        int end = text.length();

        while (end > 0 && paint.measureText(text, 0, end) > availableWidth) {
            end--;
        }

        return text.substring(0, end) + ellipsis;
    }

    private void exportPdf(String title, List<String[]> rows, String tag) {
        try {
            PdfDocument doc = new PdfDocument();
            // A4 landscape always for wide tables (FG Daily)
            final int pageW = 842, pageH = 595;
            int y = 0, pageNo = 0;
            PdfDocument.Page page = null;
            android.graphics.Canvas canvas = null;
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setAntiAlias(true);
            paint.setTextSize(8);
            android.graphics.Paint head = new android.graphics.Paint(paint);
            head.setTypeface(Typeface.DEFAULT_BOLD);
            head.setTextSize(8);
            android.graphics.Paint titleP = new android.graphics.Paint(paint);
            titleP.setTypeface(Typeface.DEFAULT_BOLD);
            titleP.setTextSize(10);

            final int left = 18, top = 24, lineH = 12;
            int cols = rows.isEmpty() ? 1 : rows.get(0).length;
            float[] widths = new float[cols];
            float total = pageW - 36f;
            // Content-based widths for EVERY sheet:
            // measure longest cell per column, then scale to page (min/max so numbers stay readable)
            {
                float[] need = new float[cols];
                android.graphics.Paint measure = new android.graphics.Paint(head);
                for (int c = 0; c < cols; c++) {
                    float m = 0;
                    for (String[] row : rows) {
                        if (c >= row.length) continue;
                        String cell = row[c] == null ? "" : row[c];
                        m = Math.max(m, measure.measureText(cell));
                    }
                    // padding inside cell
                    need[c] = m + 10f;
                }
                float sumNeed = 0;
                for (float n : need) sumNeed += n;
                if (sumNeed < 1f) sumNeed = 1f;
                // min width so tiny cols don't vanish; max so one long name can't steal everything
                float minW = total * 0.05f;
                float maxW = total * 0.40f;
                float assigned = 0;
                for (int c = 0; c < cols; c++) {
                    float w = total * (need[c] / sumNeed);
                    if (w < minW) w = minW;
                    if (w > maxW) w = maxW;
                    widths[c] = w;
                    assigned += w;
                }
                // normalize to exact page width
                if (assigned > 0) {
                    float scale = total / assigned;
                    for (int c = 0; c < cols; c++) widths[c] *= scale;
                }
            }

            for (int r = 0; r < rows.size(); r++) {
                if (page == null || y > pageH - 36) {
                    if (page != null) doc.finishPage(page);
                    pageNo++;
                    PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create();
                    page = doc.startPage(info);
                    canvas = page.getCanvas();
                    y = top;
                    canvas.drawText(title + "  ·  " + db.setting("company",""), left, y, titleP);
                    y += 14;
                    canvas.drawText("Page " + pageNo + "  (landscape)", pageW - 120, top, paint);
                    // repeat header on every page
                    if (rows.size() > 0) {
                        String[] hdr = rows.get(0);
                        float x = left;
                        for (int c = 0; c < hdr.length && c < cols; c++) {
                            String cell = hdr[c] == null ? "" : hdr[c];
                            canvas.drawText(fitPdfCell(cell, widths[c], head), x, y, head);
                            x += widths[c];
                        }
                        y += lineH;
                    }
                }
                if (r == 0) continue; // already drawn as header
                String[] row = rows.get(r);
                float x = left;
                for (int c = 0; c < row.length && c < cols; c++) {
                    String cell = row[c] == null ? "" : row[c];
                    canvas.drawText(fitPdfCell(cell, widths[c], paint), x, y, paint);
                    x += widths[c];
                }
                y += lineH;
            }
            if (page != null) doc.finishPage(page);

            File dir = new File(getCacheDir(), "pm");
            dir.mkdirs();
            String safe = title.replaceAll("[^A-Za-z0-9._-]+", "_");
            File f = new File(dir, safe + "_" + tag + ".pdf");
            try (FileOutputStream o = new FileOutputStream(f)) {
                doc.writeTo(o);
            }
            doc.close();
            offerFile(f, "application/pdf");
        } catch (Exception e) {
            toast("PDF error: " + e.getMessage());
        }
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(Ui.INK);
        e.setHintTextColor(Ui.MUTED);
        e.setBackgroundColor(Ui.dark ? Color.rgb(30, 40, 52) : Color.rgb(248, 250, 252));
        e.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        e.setTextSize(15);
        e.setSingleLine(true);
        return e;
    }

    private void pickDate(EditText e) {
        Calendar c = Calendar.getInstance();
        String cur = e.getText().toString().trim();
        try {
            String[] p = cur.split("-");
            if (p.length == 3) c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
        } catch (Exception ignored) {}
        new DatePickerDialog(this, (view, y, m, d) ->
                e.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void offerFile(File f, String mime) {
        pendingFile = f;
        pendingMime = mime;
        new AlertDialog.Builder(this)
                .setTitle(f.getName())
                .setItems(new String[]{"Print", "Share", "Save…", "Close"}, (d, which) -> {
                    if (which == 0) {
                        if ("application/pdf".equals(mime)) printPdf(f);
                        else toast("Print is available for PDF files");
                    } else if (which == 1) {
                        shareFile(f, mime);
                    } else if (which == 2) {
                        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType(mime);
                        i.putExtra(Intent.EXTRA_TITLE, f.getName());
                        try { startActivityForResult(i, REQ_SAVE); }
                        catch (Exception e) { shareFile(f, mime); }
                    }
                })
                .show();
    }

    private void printPdf(File file) {
        try {
            PrintManager manager = (PrintManager) getSystemService(PRINT_SERVICE);
            if (manager == null) { toast("Printing is not available"); return; }
            manager.print(file.getName(), new PrintDocumentAdapter() {
                @Override
                public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                        CancellationSignal signal, LayoutResultCallback callback,
                        android.os.Bundle extras) {
                    if (signal.isCanceled()) { callback.onLayoutCancelled(); return; }
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder(file.getName())
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build();
                    callback.onLayoutFinished(info, true);
                }
                @Override
                public void onWrite(android.print.PageRange[] pages, ParcelFileDescriptor destination,
                        CancellationSignal signal, WriteResultCallback callback) {
                    try (InputStream in = new java.io.FileInputStream(file);
                         OutputStream out = new java.io.FileOutputStream(destination.getFileDescriptor())) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = in.read(buffer)) != -1) {
                            if (signal.isCanceled()) { callback.onWriteCancelled(); return; }
                            out.write(buffer, 0, count);
                        }
                        callback.onWriteFinished(new android.print.PageRange[]{
                                android.print.PageRange.ALL_PAGES});
                    } catch (Exception e) { callback.onWriteFailed(e.getMessage()); }
                }
            }, new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR).build());
        } catch (Exception e) { toast("Print failed: " + e.getMessage()); }
    }

    private void shareFile(File f, String mime) {
        try {
            Uri uri = FileProvider.getUriForFile(this, "com.rmstock.nativeapp.fileprovider", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mime);
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Share"));
        } catch (Exception e) {
            toast("Share failed: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_SAVE && pendingFile != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try (InputStream in = new java.io.FileInputStream(pendingFile);
                 OutputStream out = getContentResolver().openOutputStream(uri)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                toast("Saved");
            } catch (Exception e) {
                toast("Save failed: " + e.getMessage());
            }
        } else if (requestCode == REQ_IMPORT) {
            Uri uri = data.getData();
            if (uri == null) return;
            try (InputStream in = getContentResolver().openInputStream(uri);
                 java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
                byte[] more = new byte[8192];
                int m;
                while ((m = in.read(more)) > 0) bos.write(more, 0, m);
                final String body = bos.toString("UTF-8");
                Guard.run(this, db, "Import PM backup", true, () -> {
                    try {
                        pm.importJson(body);
                        toast("Import OK");
                        render();
                    } catch (Exception e) {
                        toast("Import failed: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                toast("Could not read file: " + e.getMessage());
            }
        }
    }
}
