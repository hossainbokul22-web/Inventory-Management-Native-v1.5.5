package com.rmstock.nativeapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline SQLite store for the Daily PM (packaging materials) module.
 * Fully native — no WebView / localStorage.
 */
final class PmDb {
    private final Context ctx;
    private static PmDb INSTANCE;

    private StockDb db(){ return StockDb.get(ctx); }

    public android.database.sqlite.SQLiteDatabase getWritableDatabase() {
        return StockDb.get(ctx).getWritableDatabase();
    }
    public android.database.sqlite.SQLiteDatabase getReadableDatabase() {
        return StockDb.get(ctx).getReadableDatabase();
    }


    static synchronized PmDb get(Context c) {
        if (INSTANCE == null) INSTANCE = new PmDb(c.getApplicationContext());
        return INSTANCE;
    }

    
    /** Uses RM StockDb month lock (same finalized_YYYY-MM). */
    private void monthLockedGuard(String date) {
        if (date == null || date.length() < 7) return;
        try {
            if (StockDb.get(ctx).monthLocked(date)) {
                throw new RuntimeException("Month " + date.substring(0, 7) + " is LOCKED — PM entry blocked");
            }
        } catch (RuntimeException re) { throw re; }
        catch (Exception ignored) {}
    }

    PmDb(Context c) {
        this.ctx = c.getApplicationContext();
        try { StockDb.get(ctx).getWritableDatabase(); } catch (Exception ignored) {}
    }

    
    
    

    

    

    private void seed(SQLiteDatabase db) {
        try {
            InputStream in = ctx.getAssets().open("pm_seed.json");
            byte[] buf = new byte[in.available()];
            in.read(buf);
            in.close();
            JSONObject seed = new JSONObject(new String(buf, StandardCharsets.UTF_8));
            JSONArray items = seed.getJSONArray("pm_items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("code", o.getInt("code"));
                cv.put("name", o.optString("name", ""));
                cv.put("uom", o.optString("uom", "kg"));
                cv.put("open", o.optDouble("open", 0));
                db.insert("pm_items", null, cv);
            }
            JSONArray products = seed.getJSONArray("pm_products");
            for (int i = 0; i < products.length(); i++) {
                JSONObject o = products.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("code", o.getInt("code"));
                cv.put("name", o.optString("name", ""));
                db.insert("pm_products", null, cv);
            }
            JSONArray recipes = seed.getJSONArray("pm_recipes");
            for (int i = 0; i < recipes.length(); i++) {
                JSONObject o = recipes.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("p", o.getInt("p"));
                cv.put("i", o.getInt("i"));
                cv.put("q", o.optDouble("q", 0));
                db.insert("pm_recipes", null, cv);
            }
            putMeta(db, "company", "Ovijat Food & Beverage industries Ltd.");
            putMeta(db, "preparedBy", "MD. Bokul Hossain");
            putMeta(db, "appTitle", "Daily PM Report");
        } catch (Exception e) {
            // empty DB if seed missing
        }
    }

    private void putMeta(SQLiteDatabase db, String k, String v) {
        ContentValues cv = new ContentValues();
        cv.put("k", k);
        cv.put("v", v);
        db.insertWithOnConflict("pm_meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    String meta(String k, String def) {
        try (Cursor c = db().getReadableDatabase().rawQuery("SELECT v FROM pm_meta WHERE k=?", new String[]{k})) {
            if (c.moveToFirst()) return c.getString(0);
        }
        return def;
    }

    void setMeta(String k, String v) {
        ContentValues cv = new ContentValues();
        cv.put("k", k);
        cv.put("v", v == null ? "" : v);
        db().getWritableDatabase().insertWithOnConflict("pm_meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    static final String[] KINDS = {"receive", "ret", "output", "delivery", "hold", "reject", "qa_pass", "wastage"};
    static final String[] KIND_LABELS = {"Receive", "Return", "Output", "Delivery", "QA Hold", "Reject", "QA Pass", "Wastage"};

    List<JSONObject> items() {
        List<JSONObject> out = new ArrayList<>();
        try (Cursor c = db().getReadableDatabase().rawQuery("SELECT code,name,uom,open FROM pm_items ORDER BY code", null)) {
            while (c.moveToNext()) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("code", c.getInt(0));
                    o.put("name", c.getString(1));
                    o.put("uom", c.getString(2));
                    o.put("open", c.getDouble(3));
                    out.add(o);
                } catch (Exception ignored) {}
            }
        }
        return out;
    }

    List<JSONObject> products() {
        List<JSONObject> out = new ArrayList<>();
        try (Cursor c = db().getReadableDatabase().rawQuery("SELECT code,name FROM pm_products ORDER BY code", null)) {
            while (c.moveToNext()) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("code", c.getInt(0));
                    o.put("name", c.getString(1));
                    out.add(o);
                } catch (Exception ignored) {}
            }
        }
        return out;
    }

    /** recipe qty of item i per product p (per ctn) */
    Map<Integer, Double> recipeForProduct(int p) {
        Map<Integer, Double> m = new HashMap<>();
        try (Cursor c = db().getReadableDatabase().rawQuery("SELECT i,q FROM pm_recipes WHERE p=?", new String[]{String.valueOf(p)})) {
            while (c.moveToNext()) m.put(c.getInt(0), c.getDouble(1));
        }
        return m;
    }

    List<JSONObject> recipesFor(int p) {
        List<JSONObject> out = new ArrayList<>();
        try (Cursor c = db().getReadableDatabase().rawQuery(
                "SELECT r.i,r.q,COALESCE(i.name,'') FROM pm_recipes r LEFT JOIN items i ON i.code=r.i WHERE r.p=? ORDER BY r.i",
                new String[]{String.valueOf(p)})) {
            while (c.moveToNext()) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("i", c.getInt(0));
                    o.put("q", c.getDouble(1));
                    o.put("name", c.getString(2));
                    out.add(o);
                } catch (Exception ignored) {}
            }
        }
        return out;
    }

    long addEntry(String kind, int code, String date, double qty, String shift, String voucher, String note) {
        monthLockedGuard(date);
        ContentValues cv = new ContentValues();
        cv.put("kind", kind);
        cv.put("code", code);
        cv.put("date", date);
        cv.put("qty", qty);
        cv.put("shift", shift == null ? "" : shift);
        cv.put("voucher", voucher == null ? "" : voucher);
        cv.put("note", note == null ? "" : note);
        return db().getWritableDatabase().insert("pm_entries", null, cv);
    }

    void deleteEntry(long id) {
        db().getWritableDatabase().delete("pm_entries", "id=?", new String[]{String.valueOf(id)});
    }

    void updateEntry(long id, String kind, int code, String date, double qty, String shift, String voucher, String note) {
        ContentValues cv = new ContentValues();
        cv.put("kind", kind);
        cv.put("code", code);
        cv.put("date", date);
        cv.put("qty", qty);
        cv.put("shift", shift == null ? "" : shift);
        cv.put("voucher", voucher == null ? "" : voucher);
        cv.put("note", note == null ? "" : note);
        db().getWritableDatabase().update("pm_entries", cv, "id=?", new String[]{String.valueOf(id)});
    }

    List<JSONObject> entries(String kind, String dateOrNull, int limit) {
        List<JSONObject> out = new ArrayList<>();
        String sql;
        String[] args;
        if (dateOrNull != null && !dateOrNull.isEmpty()) {
            sql = "SELECT id,kind,code,date,qty,shift,voucher,note FROM pm_entries WHERE kind=? AND date=? ORDER BY id DESC LIMIT ?";
            args = new String[]{kind, dateOrNull, String.valueOf(limit)};
        } else {
            sql = "SELECT id,kind,code,date,qty,shift,voucher,note FROM pm_entries WHERE kind=? ORDER BY date DESC,id DESC LIMIT ?";
            args = new String[]{kind, String.valueOf(limit)};
        }
        try (Cursor c = db().getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) out.add(rowEntry(c));
        }
        return out;
    }

    List<JSONObject> recentEntries(int limit) {
        List<JSONObject> out = new ArrayList<>();
        try (Cursor c = db().getReadableDatabase().rawQuery(
                "SELECT id,kind,code,date,qty,shift,voucher,note FROM pm_entries ORDER BY id DESC LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) out.add(rowEntry(c));
        }
        return out;
    }

    private JSONObject rowEntry(Cursor c) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", c.getLong(0));
            o.put("kind", c.getString(1));
            o.put("code", c.getInt(2));
            o.put("date", c.getString(3));
            o.put("qty", c.getDouble(4));
            o.put("shift", c.getString(5));
            o.put("voucher", c.getString(6));
            o.put("note", c.getString(7));
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    double sumKind(String kind, int code, String beforeOrOn, boolean exclusive) {
        String op = exclusive ? "<" : "<=";
        try (Cursor c = db().getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(qty),0) FROM pm_entries WHERE kind=? AND code=? AND date" + op + "?",
                new String[]{kind, String.valueOf(code), beforeOrOn})) {
            if (c.moveToFirst()) return c.getDouble(0);
        }
        return 0;
    }

    double sumKindOn(String kind, int code, String date) {
        try (Cursor c = db().getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(qty),0) FROM pm_entries WHERE kind=? AND code=? AND date=?",
                new String[]{kind, String.valueOf(code), date})) {
            if (c.moveToFirst()) return c.getDouble(0);
        }
        return 0;
    }

    double sumKindMonth(String kind, int code, String ym) {
        try (Cursor c = db().getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(qty),0) FROM pm_entries WHERE kind=? AND code=? AND date LIKE ?",
                new String[]{kind, String.valueOf(code), ym + "%"})) {
            if (c.moveToFirst()) return c.getDouble(0);
        }
        return 0;
    }

    /** Opening stock for packaging item: master open + all receives before date − uses before date.
     *  Use is computed from production output × recipe qty. */
    double openingItem(int itemCode, String date) {
        double open = 0;
        try (Cursor c = db().getReadableDatabase().rawQuery("SELECT open FROM pm_items WHERE code=?", new String[]{String.valueOf(itemCode)})) {
            if (c.moveToFirst()) open = c.getDouble(0);
        }
        open += sumKind("receive", itemCode, date, true);
        open -= sumKind("ret", itemCode, date, true);
        open -= sumKind("wastage", itemCode, date, true);
        open -= itemUseBefore(itemCode, date, true);
        return open;
    }

    /** Material use from production: sum(output.qty * recipe.q) for this item on/before date */
    double itemUseBefore(int itemCode, String date, boolean exclusive) {
        String op = exclusive ? "<" : "<=";
        try (Cursor c = db().getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(e.qty * r.q),0) FROM pm_entries e JOIN recipes r ON r.p=e.code AND r.i=? " +
                        "WHERE e.kind='output' AND e.date" + op + "?",
                new String[]{String.valueOf(itemCode), date})) {
            if (c.moveToFirst()) return c.getDouble(0);
        }
        return 0;
    }

    
    /** FG opening = previous day closing: output before date − delivery − reject (hold stays). */
    
    /** Qty for kind+code+date, optional shift ("" / General = blank or General). */
    double sumKindOnShift(String kind, int code, String date, String shift) {
        String sh = shift == null ? "" : shift.trim();
        boolean gen = sh.isEmpty() || sh.equalsIgnoreCase("General");
        String sql;
        String[] args;
        if (gen) {
            sql = "SELECT COALESCE(SUM(qty),0) FROM pm_entries WHERE kind=? AND code=? AND date=? AND (shift='' OR shift='General' OR shift IS NULL)";
            args = new String[]{kind, String.valueOf(code), date};
        } else {
            sql = "SELECT COALESCE(SUM(qty),0) FROM pm_entries WHERE kind=? AND code=? AND date=? AND shift=?";
            args = new String[]{kind, String.valueOf(code), date, sh};
        }
        try (Cursor c = db().getReadableDatabase().rawQuery(sql, args)) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    double sumKindBeforeShift(String kind, int code, String beforeExclusive, String shift) {
        String sh = shift == null ? "" : shift.trim();
        boolean gen = sh.isEmpty() || sh.equalsIgnoreCase("General");
        String sql;
        String[] args;
        if (gen) {
            sql = "SELECT COALESCE(SUM(qty),0) FROM pm_entries WHERE kind=? AND code=? AND date<? AND (shift='' OR shift='General' OR shift IS NULL)";
            args = new String[]{kind, String.valueOf(code), beforeExclusive};
        } else {
            sql = "SELECT COALESCE(SUM(qty),0) FROM pm_entries WHERE kind=? AND code=? AND date<? AND shift=?";
            args = new String[]{kind, String.valueOf(code), beforeExclusive, sh};
        }
        try (Cursor c = db().getReadableDatabase().rawQuery(sql, args)) {
            return c.moveToFirst() ? c.getDouble(0) : 0;
        }
    }

    /** Outstanding hold before date: sum(hold) - sum(qa_pass) before date (not below 0). Product-level if shift null. */
    double previousHold(int code, String date, String shift) {
        double h = sumKindBeforeShift("hold", code, date, shift);
        double p = sumKindBeforeShift("qa_pass", code, date, shift);
        return Math.max(0, h - p);
    }

    double todayHold(int code, String date, String shift) {
        return sumKindOnShift("hold", code, date, shift);
    }

    double todayQaPass(int code, String date, String shift) {
        return sumKindOnShift("qa_pass", code, date, shift);
    }

    double todayProduction(int code, String date, String shift) {
        return sumKindOnShift("output", code, date, shift);
    }

    double todayDelivery(int code, String date, String shift) {
        return sumKindOnShift("delivery", code, date, shift);
    }

    /**
     * Available = Opening + Production + QA Pass - (Today Hold + Previous Hold)
     * Closing (floor) = Opening + Production - Delivery
     */
    double fgAvailable(int code, String date, String shift) {
        double open = openingProduct(code, date);
        return open + todayProduction(code, date, shift) + todayQaPass(code, date, shift)
                - (todayHold(code, date, shift) + previousHold(code, date, shift));
    }

    double fgClosingFloor(int code, String date, String shift) {
        // Floor stock: opening + production - delivery (hold/pass stay on floor)
        // Shift-wise production/delivery; opening is product-level baseline for the day
        double open = openingProduct(code, date);
        return open + todayProduction(code, date, shift) - todayDelivery(code, date, shift);
    }

double openingProduct(int productCode, String date) {
        /* Month baseline from Master "Production Opening" sheet + movements from month-start to day-before.
           On 1st of month Opening = sheet value only. Later days = previous day closing chain. */
        if (date == null || date.length() < 10) return 0;
        String ym = date.substring(0, 7);
        String monthStart = ym + "-01";
        double base = getProductOpening(productCode, ym);
        base += sumKindRange("output", productCode, monthStart, date);
        base -= sumKindRange("delivery", productCode, monthStart, date);
        base -= sumKindRange("reject", productCode, monthStart, date);
        return base;
    }

    /** Sum kind where date >= fromInclusive AND date < toExclusive */
    double sumKindRange(String kind, int code, String fromInclusive, String toExclusive) {
        try (Cursor c = db().getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(qty),0) FROM pm_entries WHERE kind=? AND code=? AND date>=? AND date<?",
                new String[]{kind, String.valueOf(code), fromInclusive, toExclusive})) {
            if (c.moveToFirst()) return c.getDouble(0);
        }
        return 0;
    }

    double getProductOpening(int productCode, String ym) {
        try (Cursor c = db().getReadableDatabase().rawQuery(
                "SELECT qty FROM pm_product_openings WHERE product_code=? AND ym=?",
                new String[]{String.valueOf(productCode), ym})) {
            if (c.moveToFirst()) return c.getDouble(0);
        }
        return 0;
    }

    void setProductOpening(int productCode, String ym, double qty) {
        try { monthLockedGuard(ym + "-01"); } catch (RuntimeException re) { throw re; } catch (Exception ignored) {}

        ContentValues cv = new ContentValues();
        cv.put("product_code", productCode);
        cv.put("ym", ym);
        cv.put("qty", qty);
        db().getWritableDatabase().insertWithOnConflict("pm_product_openings", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void deleteProductOpening(int productCode, String ym) {
        db().getWritableDatabase().delete("pm_product_openings", "product_code=? AND ym=?",
                new String[]{String.valueOf(productCode), ym});
    }


    /** FG closing = Opening + Output − Delivery − Reject. */
    double closingProduct(int productCode, String date) {
        return openingProduct(productCode, date)
                + sumKindOn("output", productCode, date)
                - sumKindOn("delivery", productCode, date)
                - sumKindOn("reject", productCode, date);
    }

double itemUseOn(int itemCode, String date) {
        try (Cursor c = db().getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(e.qty * r.q),0) FROM pm_entries e JOIN recipes r ON r.p=e.code AND r.i=? " +
                        "WHERE e.kind='output' AND e.date=?",
                new String[]{String.valueOf(itemCode), date})) {
            if (c.moveToFirst()) return c.getDouble(0);
        }
        return 0;
    }

    double itemUseMonth(int itemCode, String ym) {
        try (Cursor c = db().getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(e.qty * r.q),0) FROM pm_entries e JOIN recipes r ON r.p=e.code AND r.i=? " +
                        "WHERE e.kind='output' AND e.date LIKE ?",
                new String[]{String.valueOf(itemCode), ym + "%"})) {
            if (c.moveToFirst()) return c.getDouble(0);
        }
        return 0;
    }

    void upsertItem(int code, String name, String uom, double open) {
        ContentValues cv = new ContentValues();
        cv.put("code", code);
        cv.put("name", name);
        cv.put("uom", uom);
        cv.put("open", open);
        db().getWritableDatabase().insertWithOnConflict("pm_items", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void deleteItem(int code) {
        SQLiteDatabase db = db().getWritableDatabase();
        db.delete("pm_items", "code=?", new String[]{String.valueOf(code)});
        db.delete("pm_recipes", "i=?", new String[]{String.valueOf(code)});
    }

    void upsertProduct(int code, String name) {
        ContentValues cv = new ContentValues();
        cv.put("code", code);
        cv.put("name", name);
        db().getWritableDatabase().insertWithOnConflict("pm_products", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void deleteProduct(int code) {
        SQLiteDatabase db = db().getWritableDatabase();
        db.delete("pm_products", "code=?", new String[]{String.valueOf(code)});
        db.delete("pm_recipes", "p=?", new String[]{String.valueOf(code)});
    }

    void setRecipe(int p, int i, double q) {
        SQLiteDatabase db = db().getWritableDatabase();
        db.delete("pm_recipes", "p=? AND i=?", new String[]{String.valueOf(p), String.valueOf(i)});
        if (q > 0) {
            ContentValues cv = new ContentValues();
            cv.put("p", p);
            cv.put("i", i);
            cv.put("q", q);
            db.insert("pm_recipes", null, cv);
        }
    }

    void clearAllEntries() {
        db().getWritableDatabase().delete("pm_entries", null, null);
    }

    int entryCount() {
        try (Cursor c = db().getReadableDatabase().rawQuery("SELECT COUNT(*) FROM pm_entries", null)) {
            if (c.moveToFirst()) return c.getInt(0);
        }
        return 0;
    }

    String itemName(int code) {
        try (Cursor c = db().getReadableDatabase().rawQuery("SELECT name FROM pm_items WHERE code=?", new String[]{String.valueOf(code)})) {
            if (c.moveToFirst()) return c.getString(0);
        }
        return String.valueOf(code);
    }

    String productName(int code) {
        try (Cursor c = db().getReadableDatabase().rawQuery("SELECT name FROM pm_products WHERE code=?", new String[]{String.valueOf(code)})) {
            if (c.moveToFirst()) return c.getString(0);
        }
        return String.valueOf(code);
    }

    /** Full backup as JSON string */
    String exportJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("v", 3);
        JSONArray items = new JSONArray();
        for (JSONObject o : items()) items.put(o);
        root.put("pm_items", items);
        JSONArray products = new JSONArray();
        for (JSONObject o : products()) products.put(o);
        root.put("pm_products", products);
        JSONArray recipes = new JSONArray();
        try (Cursor c = db().getReadableDatabase().rawQuery("SELECT p,i,q FROM pm_recipes", null)) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("p", c.getInt(0));
                o.put("i", c.getInt(1));
                o.put("q", c.getDouble(2));
                recipes.put(o);
            }
        }
        root.put("pm_recipes", recipes);
        for (String k : KINDS) {
            JSONArray arr = new JSONArray();
            try (Cursor c = db().getReadableDatabase().rawQuery(
                    "SELECT id,code,date,qty,shift,voucher,note FROM pm_entries WHERE kind=? ORDER BY id", new String[]{k})) {
                while (c.moveToNext()) {
                    JSONObject o = new JSONObject();
                    o.put("id", c.getLong(0));
                    o.put("code", c.getInt(1));
                    o.put("date", c.getString(2));
                    o.put("qty", c.getDouble(3));
                    o.put("shift", c.getString(4));
                    o.put("voucher", c.getString(5));
                    o.put("note", c.getString(6));
                    arr.put(o);
                }
            }
            root.put(k, arr);
        }
        JSONObject settings = new JSONObject();
        settings.put("company", meta("company", ""));
        settings.put("preparedBy", meta("preparedBy", ""));
        root.put("settings", settings);
        root.put("appTitle", meta("appTitle", "Daily PM Report"));
        return root.toString(2);
    }

    void importJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        SQLiteDatabase db = db().getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("pm_entries", null, null);
            db.delete("pm_recipes", null, null);
            db.delete("pm_products", null, null);
            db.delete("pm_items", null, null);
            JSONArray items = root.optJSONArray("pm_items");
            if (items != null) for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("code", o.getInt("code"));
                cv.put("name", o.optString("name", ""));
                cv.put("uom", o.optString("uom", "kg"));
                cv.put("open", o.optDouble("open", 0));
                db.insert("pm_items", null, cv);
            }
            JSONArray products = root.optJSONArray("pm_products");
            if (products != null) for (int i = 0; i < products.length(); i++) {
                JSONObject o = products.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("code", o.getInt("code"));
                cv.put("name", o.optString("name", ""));
                db.insert("pm_products", null, cv);
            }
            JSONArray recipes = root.optJSONArray("pm_recipes");
            if (recipes != null) for (int i = 0; i < recipes.length(); i++) {
                JSONObject o = recipes.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("p", o.getInt("p"));
                cv.put("i", o.getInt("i"));
                cv.put("q", o.optDouble("q", 0));
                db.insert("pm_recipes", null, cv);
            }
            for (String k : KINDS) {
                JSONArray arr = root.optJSONArray(k);
                if (arr == null) continue;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    ContentValues cv = new ContentValues();
                    cv.put("kind", k);
                    cv.put("code", o.getInt("code"));
                    cv.put("date", o.optString("date", ""));
                    cv.put("qty", o.optDouble("qty", 0));
                    cv.put("shift", o.optString("shift", ""));
                    cv.put("voucher", o.optString("voucher", ""));
                    cv.put("note", o.optString("note", ""));
                    db.insert("pm_entries", null, cv);
                }
            }
            JSONObject settings = root.optJSONObject("settings");
            if (settings != null) {
                putMeta(db, "company", settings.optString("company", ""));
                putMeta(db, "preparedBy", settings.optString("preparedBy", ""));
            }
            if (root.has("appTitle")) putMeta(db, "appTitle", root.optString("appTitle", "Daily PM Report"));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    static String today() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(java.util.Calendar.YEAR),
                c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH));
    }

    static String thisMonth() {
        return today().substring(0, 7);
    }

    static String fmt(double n) {
        n = Math.round(n * 1000.0) / 1000.0;
        if (n == (long) n) return String.valueOf((long) n);
        return String.valueOf(n);
    }
}
