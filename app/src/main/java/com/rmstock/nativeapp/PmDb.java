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
final class PmDb extends SQLiteOpenHelper {
    private static final String NAME = "rm_pm.db";
    private static final int VER = 2;
    private final Context ctx;

    PmDb(Context c) {
        super(c, NAME, null, VER);
        this.ctx = c.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE items(code INTEGER PRIMARY KEY, name TEXT, uom TEXT, open REAL)");
        db.execSQL("CREATE TABLE products(code INTEGER PRIMARY KEY, name TEXT)");
        db.execSQL("CREATE TABLE recipes(p INTEGER, i INTEGER, q REAL)");
        db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT, code INTEGER, date TEXT, qty REAL, shift TEXT, voucher TEXT, note TEXT)");
        db.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)");
        db.execSQL("CREATE INDEX idx_entries ON entries(kind, code, date)");
        db.execSQL("CREATE TABLE IF NOT EXISTS product_openings(product_code INTEGER NOT NULL, ym TEXT NOT NULL, qty REAL NOT NULL DEFAULT 0, PRIMARY KEY(product_code, ym))");
        seed(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS product_openings(product_code INTEGER NOT NULL, ym TEXT NOT NULL, qty REAL NOT NULL DEFAULT 0, PRIMARY KEY(product_code, ym))");
        }
    }

    private void seed(SQLiteDatabase db) {
        try {
            InputStream in = ctx.getAssets().open("pm_seed.json");
            byte[] buf = new byte[in.available()];
            in.read(buf);
            in.close();
            JSONObject seed = new JSONObject(new String(buf, StandardCharsets.UTF_8));
            JSONArray items = seed.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("code", o.getInt("code"));
                cv.put("name", o.optString("name", ""));
                cv.put("uom", o.optString("uom", "kg"));
                cv.put("open", o.optDouble("open", 0));
                db.insert("items", null, cv);
            }
            JSONArray products = seed.getJSONArray("products");
            for (int i = 0; i < products.length(); i++) {
                JSONObject o = products.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("code", o.getInt("code"));
                cv.put("name", o.optString("name", ""));
                db.insert("products", null, cv);
            }
            JSONArray recipes = seed.getJSONArray("recipes");
            for (int i = 0; i < recipes.length(); i++) {
                JSONObject o = recipes.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("p", o.getInt("p"));
                cv.put("i", o.getInt("i"));
                cv.put("q", o.optDouble("q", 0));
                db.insert("recipes", null, cv);
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
        db.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    String meta(String k, String def) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT v FROM meta WHERE k=?", new String[]{k})) {
            if (c.moveToFirst()) return c.getString(0);
        }
        return def;
    }

    void setMeta(String k, String v) {
        ContentValues cv = new ContentValues();
        cv.put("k", k);
        cv.put("v", v == null ? "" : v);
        getWritableDatabase().insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    static final String[] KINDS = {"receive", "ret", "output", "delivery", "hold", "reject"};
    static final String[] KIND_LABELS = {"Receive", "Return", "Output", "Delivery", "QA Hold", "Reject"};

    List<JSONObject> items() {
        List<JSONObject> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT code,name,uom,open FROM items ORDER BY code", null)) {
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
        try (Cursor c = getReadableDatabase().rawQuery("SELECT code,name FROM products ORDER BY code", null)) {
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
        try (Cursor c = getReadableDatabase().rawQuery("SELECT i,q FROM recipes WHERE p=?", new String[]{String.valueOf(p)})) {
            while (c.moveToNext()) m.put(c.getInt(0), c.getDouble(1));
        }
        return m;
    }

    List<JSONObject> recipesFor(int p) {
        List<JSONObject> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT r.i,r.q,COALESCE(i.name,'') FROM recipes r LEFT JOIN items i ON i.code=r.i WHERE r.p=? ORDER BY r.i",
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
        ContentValues cv = new ContentValues();
        cv.put("kind", kind);
        cv.put("code", code);
        cv.put("date", date);
        cv.put("qty", qty);
        cv.put("shift", shift == null ? "" : shift);
        cv.put("voucher", voucher == null ? "" : voucher);
        cv.put("note", note == null ? "" : note);
        return getWritableDatabase().insert("entries", null, cv);
    }

    void deleteEntry(long id) {
        getWritableDatabase().delete("entries", "id=?", new String[]{String.valueOf(id)});
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
        getWritableDatabase().update("entries", cv, "id=?", new String[]{String.valueOf(id)});
    }

    List<JSONObject> entries(String kind, String dateOrNull, int limit) {
        List<JSONObject> out = new ArrayList<>();
        String sql;
        String[] args;
        if (dateOrNull != null && !dateOrNull.isEmpty()) {
            sql = "SELECT id,kind,code,date,qty,shift,voucher,note FROM entries WHERE kind=? AND date=? ORDER BY id DESC LIMIT ?";
            args = new String[]{kind, dateOrNull, String.valueOf(limit)};
        } else {
            sql = "SELECT id,kind,code,date,qty,shift,voucher,note FROM entries WHERE kind=? ORDER BY date DESC,id DESC LIMIT ?";
            args = new String[]{kind, String.valueOf(limit)};
        }
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) out.add(rowEntry(c));
        }
        return out;
    }

    List<JSONObject> recentEntries(int limit) {
        List<JSONObject> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,kind,code,date,qty,shift,voucher,note FROM entries ORDER BY id DESC LIMIT ?",
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
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(qty),0) FROM entries WHERE kind=? AND code=? AND date" + op + "?",
                new String[]{kind, String.valueOf(code), beforeOrOn})) {
            if (c.moveToFirst()) return c.getDouble(0);
        }
        return 0;
    }

    double sumKindOn(String kind, int code, String date) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(qty),0) FROM entries WHERE kind=? AND code=? AND date=?",
                new String[]{kind, String.valueOf(code), date})) {
            if (c.moveToFirst()) return c.getDouble(0);
        }
        return 0;
    }

    double sumKindMonth(String kind, int code, String ym) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(qty),0) FROM entries WHERE kind=? AND code=? AND date LIKE ?",
                new String[]{kind, String.valueOf(code), ym + "%"})) {
            if (c.moveToFirst()) return c.getDouble(0);
        }
        return 0;
    }

    /** Opening stock for packaging item: master open + all receives before date − uses before date.
     *  Use is computed from production output × recipe qty. */
    double openingItem(int itemCode, String date) {
        double open = 0;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT open FROM items WHERE code=?", new String[]{String.valueOf(itemCode)})) {
            if (c.moveToFirst()) open = c.getDouble(0);
        }
        open += sumKind("receive", itemCode, date, true);
        open -= sumKind("ret", itemCode, date, true);
        open -= itemUseBefore(itemCode, date, true);
        return open;
    }

    /** Material use from production: sum(output.qty * recipe.q) for this item on/before date */
    double itemUseBefore(int itemCode, String date, boolean exclusive) {
        String op = exclusive ? "<" : "<=";
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(e.qty * r.q),0) FROM entries e JOIN recipes r ON r.p=e.code AND r.i=? " +
                        "WHERE e.kind='output' AND e.date" + op + "?",
                new String[]{String.valueOf(itemCode), date})) {
            if (c.moveToFirst()) return c.getDouble(0);
        }
        return 0;
    }

    
    /** FG opening = previous day closing: output before date − delivery − reject (hold stays). */
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
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(qty),0) FROM entries WHERE kind=? AND code=? AND date>=? AND date<?",
                new String[]{kind, String.valueOf(code), fromInclusive, toExclusive})) {
            if (c.moveToFirst()) return c.getDouble(0);
        }
        return 0;
    }

    double getProductOpening(int productCode, String ym) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT qty FROM product_openings WHERE product_code=? AND ym=?",
                new String[]{String.valueOf(productCode), ym})) {
            if (c.moveToFirst()) return c.getDouble(0);
        }
        return 0;
    }

    void setProductOpening(int productCode, String ym, double qty) {
        ContentValues cv = new ContentValues();
        cv.put("product_code", productCode);
        cv.put("ym", ym);
        cv.put("qty", qty);
        getWritableDatabase().insertWithOnConflict("product_openings", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void deleteProductOpening(int productCode, String ym) {
        getWritableDatabase().delete("product_openings", "product_code=? AND ym=?",
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
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(e.qty * r.q),0) FROM entries e JOIN recipes r ON r.p=e.code AND r.i=? " +
                        "WHERE e.kind='output' AND e.date=?",
                new String[]{String.valueOf(itemCode), date})) {
            if (c.moveToFirst()) return c.getDouble(0);
        }
        return 0;
    }

    double itemUseMonth(int itemCode, String ym) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(e.qty * r.q),0) FROM entries e JOIN recipes r ON r.p=e.code AND r.i=? " +
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
        getWritableDatabase().insertWithOnConflict("items", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void deleteItem(int code) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("items", "code=?", new String[]{String.valueOf(code)});
        db.delete("recipes", "i=?", new String[]{String.valueOf(code)});
    }

    void upsertProduct(int code, String name) {
        ContentValues cv = new ContentValues();
        cv.put("code", code);
        cv.put("name", name);
        getWritableDatabase().insertWithOnConflict("products", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void deleteProduct(int code) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("products", "code=?", new String[]{String.valueOf(code)});
        db.delete("recipes", "p=?", new String[]{String.valueOf(code)});
    }

    void setRecipe(int p, int i, double q) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("recipes", "p=? AND i=?", new String[]{String.valueOf(p), String.valueOf(i)});
        if (q > 0) {
            ContentValues cv = new ContentValues();
            cv.put("p", p);
            cv.put("i", i);
            cv.put("q", q);
            db.insert("recipes", null, cv);
        }
    }

    void clearAllEntries() {
        getWritableDatabase().delete("entries", null, null);
    }

    int entryCount() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM entries", null)) {
            if (c.moveToFirst()) return c.getInt(0);
        }
        return 0;
    }

    String itemName(int code) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT name FROM items WHERE code=?", new String[]{String.valueOf(code)})) {
            if (c.moveToFirst()) return c.getString(0);
        }
        return String.valueOf(code);
    }

    String productName(int code) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT name FROM products WHERE code=?", new String[]{String.valueOf(code)})) {
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
        root.put("items", items);
        JSONArray products = new JSONArray();
        for (JSONObject o : products()) products.put(o);
        root.put("products", products);
        JSONArray recipes = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT p,i,q FROM recipes", null)) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("p", c.getInt(0));
                o.put("i", c.getInt(1));
                o.put("q", c.getDouble(2));
                recipes.put(o);
            }
        }
        root.put("recipes", recipes);
        for (String k : KINDS) {
            JSONArray arr = new JSONArray();
            try (Cursor c = getReadableDatabase().rawQuery(
                    "SELECT id,code,date,qty,shift,voucher,note FROM entries WHERE kind=? ORDER BY id", new String[]{k})) {
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
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("entries", null, null);
            db.delete("recipes", null, null);
            db.delete("products", null, null);
            db.delete("items", null, null);
            JSONArray items = root.optJSONArray("items");
            if (items != null) for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("code", o.getInt("code"));
                cv.put("name", o.optString("name", ""));
                cv.put("uom", o.optString("uom", "kg"));
                cv.put("open", o.optDouble("open", 0));
                db.insert("items", null, cv);
            }
            JSONArray products = root.optJSONArray("products");
            if (products != null) for (int i = 0; i < products.length(); i++) {
                JSONObject o = products.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("code", o.getInt("code"));
                cv.put("name", o.optString("name", ""));
                db.insert("products", null, cv);
            }
            JSONArray recipes = root.optJSONArray("recipes");
            if (recipes != null) for (int i = 0; i < recipes.length(); i++) {
                JSONObject o = recipes.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("p", o.getInt("p"));
                cv.put("i", o.getInt("i"));
                cv.put("q", o.optDouble("q", 0));
                db.insert("recipes", null, cv);
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
                    db.insert("entries", null, cv);
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
