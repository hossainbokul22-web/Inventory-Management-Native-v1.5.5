package com.rmstock.nativeapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
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
    private static final String[] TABS = {"Entry", "Daily", "Monthly", "Production", "Master", "Settings", "Backup"};
    private static final String[] SHIFTS = {"A", "B", "C", "General"};

    private PmDb pm;
    private StockDb db;
    private LinearLayout root, content;
    private TextView titleTv, statusTv;
    private int tab;
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

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.BLACK);
        bar.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        TextView back = Ui.text(this, "‹  Back", 16, Color.WHITE, true);
        back.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        titleTv = Ui.text(this, pm.meta("appTitle", "Daily PM Report"), 16, Color.WHITE, true);
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
                if (idx == 4 || idx == 5) {
                    Guard.run(this, db, TABS[idx], false, () -> { tab = idx; render(); });
                } else {
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
            case 0: showEntry(); break;
            case 1: showDaily(); break;
            case 2: showMonthly(); break;
            case 3: showProduction(); break;
            case 4: showMaster(); break;
            case 5: showSettings(); break;
            case 6: showBackup(); break;
        }
    }

    private void add(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), 0);
        content.addView(v, lp);
    }

    private void showEntry() {
        LinearLayout card = Ui.card(this, Ui.CARD);
        card.addView(Ui.text(this, "New entry", 17, Color.BLACK, true));

        final int[] kindIdx = {Math.min(savedKindPosition, PmDb.KIND_LABELS.length - 1)};
        HorizontalScrollView kindScroll = new HorizontalScrollView(this);
        kindScroll.setHorizontalScrollBarEnabled(false);
        final LinearLayout kindRow = new LinearLayout(this);
        kindRow.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] kindChips = new TextView[PmDb.KIND_LABELS.length];
        Runnable paintKinds = () -> {
            for (int i = 0; i < kindChips.length; i++) {
                boolean on = i == kindIdx[0];
                kindChips[i].setTextColor(on ? Color.BLACK : Ui.MUTED);
                kindChips[i].setBackground(Ui.shape(this, Ui.CARD, on ? Color.BLACK : Ui.LINE, 20));
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

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout cDate = new LinearLayout(this);
        cDate.setOrientation(LinearLayout.VERTICAL);
        cDate.addView(Ui.muted(this, "Date"));
        final EditText dateEt = field("Date", stockDate);
        dateEt.setFocusable(false);
        dateEt.setOnClickListener(v -> pickDate(dateEt));
        cDate.addView(dateEt);
        LinearLayout cItem = new LinearLayout(this);
        cItem.setOrientation(LinearLayout.VERTICAL);
        cItem.addView(Ui.muted(this, "Item / Product"));
        final Spinner codeSp = new Spinner(this);
        cItem.addView(codeSp);
        row1.addView(cDate, new LinearLayout.LayoutParams(0, -2, 1));
        row1.addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));
        row1.addView(cItem, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(row1);

        final List<JSONObject> selectedRecords = new ArrayList<>();
        final Runnable[] refreshHolder = new Runnable[1];
        refreshHolder[0] = () -> {
            selectedRecords.clear();
            List<JSONObject> records = kindIdx[0] <= 1 ? pm.items() : pm.products();
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

    private void showMonthly() {
        LinearLayout top = Ui.card(this, Ui.CARD);
        top.addView(Ui.text(this, "Monthly reports", 16, Ui.NAVY_TEXT, true));
        final EditText ymEt = field("Month (YYYY-MM)", monthYm);
        top.addView(Ui.muted(this, "Month"));
        top.addView(ymEt);
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
        sec.addView(Ui.muted(this, "Same fingerprint / PIN lock as the main RM Stock app. Asked before Master, Settings and every delete."));
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
        Button wipe = Ui.secondary(this, "Clear all PM entries only");
        wipe.setOnClickListener(v -> Guard.run(this, db, "Clear all PM entries", true, () -> {
            pm.clearAllEntries();
            toast("Entries cleared");
            render();
        }));
        card.addView(wipe);
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

    private void exportPdf(String title, List<String[]> rows, String tag) {
        try {
            PdfDocument doc = new PdfDocument();
            int pageW = 842, pageH = 595;
            int y = 0, pageNo = 0;
            PdfDocument.Page page = null;
            android.graphics.Canvas canvas = null;
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setAntiAlias(true);
            paint.setTextSize(9);
            android.graphics.Paint head = new android.graphics.Paint(paint);
            head.setTypeface(Typeface.DEFAULT_BOLD);
            head.setTextSize(11);

            final int left = 24, top = 28, lineH = 14;
            int cols = rows.isEmpty() ? 1 : rows.get(0).length;
            float[] widths = new float[cols];
            float total = pageW - 48f;
            for (int c = 0; c < cols; c++) widths[c] = total / cols;
            if (cols > 1) {
                widths[1] = total * 0.28f;
                float rest = (total - widths[1]) / (cols - 1);
                for (int c = 0; c < cols; c++) if (c != 1) widths[c] = rest;
            }

            for (int r = 0; r < rows.size(); r++) {
                if (page == null || y > pageH - 40) {
                    if (page != null) doc.finishPage(page);
                    pageNo++;
                    PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create();
                    page = doc.startPage(info);
                    canvas = page.getCanvas();
                    y = top;
                    canvas.drawText(title + "  ·  " + db.setting("company",""), left, y, head);
                    y += 16;
                    canvas.drawText("Page " + pageNo, pageW - 80, top, paint);
                    if (r > 0) {
                        String[] hdr = rows.get(0);
                        float x = left;
                        for (int c = 0; c < hdr.length; c++) {
                            canvas.drawText(hdr[c] == null ? "" : hdr[c], x, y, head);
                            x += widths[c];
                        }
                        y += lineH;
                    }
                }
                String[] row = rows.get(r);
                float x = left;
                android.graphics.Paint p = (r == 0) ? head : paint;
                for (int c = 0; c < row.length; c++) {
                    String cell = row[c] == null ? "" : row[c];
                    if (cell.length() > 28) cell = cell.substring(0, 27) + "…";
                    canvas.drawText(cell, x, y, p);
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
