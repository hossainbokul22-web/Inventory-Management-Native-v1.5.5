from pathlib import Path

p = Path("app/src/main/java/com/rmstock/nativeapp/PmActivity.java")
s = p.read_text()

old = '''        final EditText codeEt = field("Code", "");
        codeEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        card.addView(Ui.muted(this, "Item / Product code"));
        card.addView(codeEt);
'''
new = '''        final Spinner codeSp = new Spinner(this);
        final List<JSONObject> selectedRecords = new ArrayList<>();
        Runnable refreshChoices = () -> {
            selectedRecords.clear();
            List<JSONObject> records = kindSp.getSelectedItemPosition() <= 1
                    ? pm.items() : pm.products();
            List<String> labels = new ArrayList<>();
            for (JSONObject o : records) {
                selectedRecords.add(o);
                labels.add(o.optInt("code") + " — " + o.optString("name"));
            }
            if (labels.isEmpty()) labels.add("No items available");
            codeSp.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item, labels));
        };
        card.addView(Ui.muted(this, "Item / Product"));
        card.addView(codeSp);
        refreshChoices.run();
        kindSp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                    int position, long id) { refreshChoices.run(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
'''
assert old in s, "Code field block mismatch"
s = s.replace(old, new, 1)

old = '''            int code;
            double qty;
            try {
                code = Integer.parseInt(codeEt.getText().toString().trim());
                qty = Double.parseDouble(qtyEt.getText().toString().trim().replace(',', '.'));
            } catch (Exception e) {
                toast("Code and quantity required");
                return;
            }
            if (date.length() < 8) { toast("Invalid date"); return; }
            boolean isItem = ki <= 1;
            if (isItem) {
                boolean found = false;
                for (JSONObject o : pm.items()) if (o.optInt("code") == code) { found = true; break; }
                if (!found) { toast("Unknown item code " + code); return; }
            } else {
                boolean found = false;
                for (JSONObject o : pm.products()) if (o.optInt("code") == code) { found = true; break; }
                if (!found) { toast("Unknown product code " + code); return; }
            }
'''
new = '''            int code;
            double qty;
            try {
                int selected = codeSp.getSelectedItemPosition();
                if (selectedRecords.isEmpty() || selected < 0 || selected >= selectedRecords.size()) {
                    toast("Select an item or product first");
                    return;
                }
                code = selectedRecords.get(selected).optInt("code");
                qty = Double.parseDouble(qtyEt.getText().toString().trim().replace(',', '.'));
            } catch (Exception e) {
                toast("Select item/product and enter quantity");
                return;
            }
            if (date.length() < 8) { toast("Invalid date"); return; }
'''
assert old in s, "Save validation block mismatch"
s = s.replace(old, new, 1)
s = s.replace('''            codeEt.setText("");
            qtyEt.setText("");
''', '''            codeSp.setSelection(0);
            qtyEt.setText("");
''', 1)

if 'import android.print.PrintManager;' not in s:
    s = s.replace('import android.net.Uri;\n',
'''import android.net.Uri;
import android.print.PrintManager;
import android.print.PrintDocumentAdapter;
import android.print.PrintAttributes;
import android.print.PrintDocumentInfo;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
''', 1)

old = '''        new AlertDialog.Builder(this)
                .setTitle(f.getName())
                .setMessage("Save or share this file?")
                .setPositiveButton("Share", (d, w) -> shareFile(f, mime))
                .setNeutralButton("Save…", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType(mime);
                    i.putExtra(Intent.EXTRA_TITLE, f.getName());
                    try {
                        startActivityForResult(i, REQ_SAVE);
                    } catch (Exception e) {
                        shareFile(f, mime);
                    }
                })
                .setNegativeButton("Close", null)
                .show();
'''
new = '''        new AlertDialog.Builder(this)
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
'''
assert old in s, "File dialog block mismatch"
s = s.replace(old, new, 1)

marker = '    private void shareFile(File f, String mime) {'
method = '''    private void printPdf(File file) {
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

'''
assert marker in s, "shareFile method not found"
s = s.replace(marker, method + marker, 1)

p.write_text(s)
print("Changes applied.")
