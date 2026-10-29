package com.rmstock.nativeapp;

import android.content.*;import android.database.*;import android.database.sqlite.*;import org.json.*;import java.io.*;import java.util.*;

public class StockDb extends SQLiteOpenHelper {
    private static final String DB="rmstock_native.db"; private static final int VER=14; private final Context ctx;
    private static StockDb INSTANCE;
    /** Single helper instance — faster cache, less memory (Android SQLite best practice). */
    public static synchronized StockDb get(Context c){
        if(INSTANCE==null) INSTANCE=new StockDb(c.getApplicationContext());
        return INSTANCE;
    }
    StockDb(Context c){super(c,DB,null,VER);ctx=c.getApplicationContext();}
    @Override public void onConfigure(SQLiteDatabase d){
        super.onConfigure(d);
        try{ d.setForeignKeyConstraintsEnabled(true); }catch(Exception ignored){}
    }
    @Override public void onOpen(SQLiteDatabase d){
        super.onOpen(d);
        if(!d.isReadOnly()){
            try{ d.enableWriteAheadLogging(); }catch(Exception ignored){}
            try{
                d.execSQL("PRAGMA synchronous=NORMAL");
                d.execSQL("PRAGMA temp_store=MEMORY");
            }catch(Exception ignored){}
            ensurePerfIndexes(d);
        }
    }
    private void ensurePerfIndexes(SQLiteDatabase d){
        try{
            d.execSQL("CREATE INDEX IF NOT EXISTS idx_receives_date ON receives(date)");
            d.execSQL("CREATE INDEX IF NOT EXISTS idx_receives_rm ON receives(rm_code)");
            d.execSQL("CREATE INDEX IF NOT EXISTS idx_batches_date ON batches(date)");
            d.execSQL("CREATE INDEX IF NOT EXISTS idx_batches_prod ON batches(product_code)");
            d.execSQL("CREATE INDEX IF NOT EXISTS idx_counts_date ON counts(date)");
            d.execSQL("CREATE INDEX IF NOT EXISTS idx_att_day ON attendance(day)");
            d.execSQL("CREATE INDEX IF NOT EXISTS idx_att_worker ON attendance(worker_id)");
            d.execSQL("CREATE INDEX IF NOT EXISTS idx_att_day_punch ON attendance(day,punch)");
            d.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_status ON sync_queue(status)");
            d.execSQL("CREATE INDEX IF NOT EXISTS idx_yield_hist_date ON yield_history(date)");
        }catch(Exception ignored){}
    }

    public void onCreate(SQLiteDatabase d){
        d.execSQL("CREATE TABLE rms(code TEXT PRIMARY KEY,name TEXT NOT NULL,uom TEXT NOT NULL,opening REAL NOT NULL DEFAULT 0,reorder REAL NOT NULL DEFAULT 0)");
        d.execSQL("CREATE TABLE products(code INTEGER PRIMARY KEY,name TEXT NOT NULL)");
        d.execSQL("CREATE TABLE recipes(rm_code TEXT NOT NULL,product_code INTEGER NOT NULL,qty REAL NOT NULL DEFAULT 0,PRIMARY KEY(rm_code,product_code))");
        d.execSQL("CREATE TABLE batches(id INTEGER PRIMARY KEY AUTOINCREMENT,date TEXT,product_code INTEGER,shift INTEGER,qty REAL,saved_at TEXT)");
        d.execSQL("CREATE TABLE receives(id INTEGER PRIMARY KEY AUTOINCREMENT,date TEXT,rm_code TEXT,qty REAL,note TEXT,voucher_no TEXT,source TEXT,lot TEXT,expiry TEXT,photo TEXT)");
        d.execSQL("CREATE TABLE IF NOT EXISTS counts(id INTEGER PRIMARY KEY AUTOINCREMENT,date TEXT,rm_code TEXT,counted REAL,system REAL,note TEXT,status TEXT DEFAULT 'pending',variance REAL DEFAULT 0,approved_by TEXT,approved_at TEXT)");
        d.execSQL("CREATE TABLE IF NOT EXISTS yield_defs(product_code INTEGER PRIMARY KEY,actual REAL NOT NULL DEFAULT 0,standard REAL NOT NULL DEFAULT 0,formula TEXT NOT NULL DEFAULT '',enabled INTEGER NOT NULL DEFAULT 1,stage TEXT DEFAULT 'mixing',moisture REAL DEFAULT 0)");
        d.execSQL("CREATE TABLE IF NOT EXISTS yield_history(id INTEGER PRIMARY KEY AUTOINCREMENT,date TEXT,product_code INTEGER,stage TEXT,dry_output REAL,input_kg REAL,moisture REAL,wastage REAL,batch_qty REAL,formula TEXT,result REAL,note TEXT,user TEXT)");
        d.execSQL("CREATE TABLE IF NOT EXISTS yield_stages(product_code INTEGER NOT NULL,stage TEXT NOT NULL,actual REAL DEFAULT 0,standard REAL DEFAULT 0,formula TEXT DEFAULT '',enabled INTEGER DEFAULT 1,PRIMARY KEY(product_code,stage))");
        d.execSQL("CREATE TABLE IF NOT EXISTS recipe_archive(id INTEGER PRIMARY KEY AUTOINCREMENT,product_code INTEGER,product_name TEXT,saved_at TEXT,user TEXT,payload TEXT)");
        d.execSQL("CREATE TABLE IF NOT EXISTS process_loss_defs(product_code INTEGER PRIMARY KEY,input_kg REAL DEFAULT 0,output_kg REAL DEFAULT 0,formula TEXT DEFAULT '',standard_pct REAL DEFAULT 0,enabled INTEGER DEFAULT 1)");
        d.execSQL("CREATE TABLE IF NOT EXISTS audit_log(id INTEGER PRIMARY KEY AUTOINCREMENT,ts TEXT,user TEXT,role TEXT,action TEXT,detail TEXT)");

        d.execSQL("CREATE TABLE approvals(id INTEGER PRIMARY KEY AUTOINCREMENT,type TEXT,old_value TEXT,new_value TEXT,meta TEXT,status TEXT,user TEXT,created_at TEXT)");
        d.execSQL("CREATE TABLE custom_sheets(id TEXT PRIMARY KEY,name TEXT,description TEXT,access TEXT)");
        d.execSQL("CREATE TABLE custom_cols(id INTEGER PRIMARY KEY AUTOINCREMENT,sheet_id TEXT,name TEXT,type TEXT,formula TEXT,position INTEGER)");
        d.execSQL("CREATE TABLE custom_rows(id INTEGER PRIMARY KEY AUTOINCREMENT,sheet_id TEXT,row_json TEXT)");
        d.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY,v TEXT)");
        d.execSQL("CREATE TABLE mixing2_batch_log(id INTEGER PRIMARY KEY AUTOINCREMENT,batch_id INTEGER NOT NULL,date TEXT,product_code INTEGER,shift INTEGER,rm_code TEXT,recipe_qty REAL,batch_count REAL,consumption REAL,created_at TEXT,UNIQUE(batch_id,rm_code))");
        d.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_batches_shift ON batches(date,product_code,shift)");
        seed(d);
    }
    private void seed(SQLiteDatabase d){try{
        JSONObject data=new JSONObject(readAsset("data.json")); String company=data.optString("company","RM Stock"); putSetting(d,"company",company); putSetting(d,"role","Admin");
        JSONArray rms=data.getJSONArray("rms"); for(int i=0;i<rms.length();i++){JSONObject x=rms.getJSONObject(i);ContentValues v=new ContentValues();v.put("code",String.valueOf(x.get("code")));v.put("name",x.getString("name"));v.put("uom",x.optString("uom","kg"));v.put("opening",x.optDouble("opening",0));d.insert("rms",null,v);}
        JSONArray ps=data.getJSONArray("products");for(int i=0;i<ps.length();i++){JSONObject x=ps.getJSONObject(i);ContentValues v=new ContentValues();v.put("code",x.getInt("code"));v.put("name",x.getString("name"));d.insert("products",null,v);}
        Set<String> known=new HashSet<>();for(int k=0;k<rms.length();k++)known.add(String.valueOf(rms.getJSONObject(k).get("code")));JSONObject rec=data.getJSONObject("recipe");Iterator<String> keys=rec.keys();while(keys.hasNext()){String rm=keys.next();if(!known.contains(rm))continue;JSONArray a=rec.getJSONArray(rm);for(int i=0;i<a.length();i++){if(i>=ps.length())break;ContentValues v=new ContentValues();v.put("rm_code",rm);v.put("product_code",ps.getJSONObject(i).getInt("code"));v.put("qty",a.optDouble(i,0));d.insert("recipes",null,v);}}
    }catch(Exception e){throw new RuntimeException(e);}}
    private String readAsset(String n)throws Exception{InputStream in=ctx.getAssets().open(n);ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int k;while((k=in.read(buf))>0)b.write(buf,0,k);return b.toString("UTF-8");}
    private void putSetting(SQLiteDatabase d,String k,String v){ContentValues x=new ContentValues();x.put("k",k);x.put("v",v);d.insertWithOnConflict("settings",null,x,SQLiteDatabase.CONFLICT_REPLACE);}
    String setting(String k,String def){Cursor c=getReadableDatabase().rawQuery("SELECT v FROM settings WHERE k=?",new String[]{k});try{return c.moveToFirst()?c.getString(0):def;}finally{c.close();}}
    void setSetting(String k,String v){putSetting(getWritableDatabase(),k,v);}
    public void onUpgrade(SQLiteDatabase d,int o,int n){
        if(o<14){
            try{ ensurePerfIndexes(d); }catch(Exception ignored){}
        }
        if(o<13){
            d.execSQL("CREATE TABLE IF NOT EXISTS sync_queue(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT,payload TEXT,created_at TEXT,tries INTEGER DEFAULT 0,status TEXT DEFAULT 'pending')");
        }

        if(o<12){
            d.execSQL("CREATE TABLE IF NOT EXISTS process_loss_defs(product_code INTEGER PRIMARY KEY,input_kg REAL DEFAULT 0,output_kg REAL DEFAULT 0,formula TEXT DEFAULT '',standard_pct REAL DEFAULT 0,enabled INTEGER DEFAULT 1)");
            try{ d.execSQL("ALTER TABLE process_loss_defs ADD COLUMN standard_pct REAL DEFAULT 0"); }catch(Exception ignored){}
        }

        if(o<11){
            d.execSQL("CREATE TABLE IF NOT EXISTS process_loss_defs(product_code INTEGER PRIMARY KEY,input_kg REAL DEFAULT 0,output_kg REAL DEFAULT 0,formula TEXT DEFAULT '',standard_pct REAL DEFAULT 0,enabled INTEGER DEFAULT 1)");
        }

        if(o<10){
            try{ d.execSQL("ALTER TABLE counts ADD COLUMN status TEXT DEFAULT 'pending'"); }catch(Exception ignored){}
            try{ d.execSQL("ALTER TABLE counts ADD COLUMN variance REAL DEFAULT 0"); }catch(Exception ignored){}
            try{ d.execSQL("ALTER TABLE counts ADD COLUMN approved_by TEXT"); }catch(Exception ignored){}
            try{ d.execSQL("ALTER TABLE counts ADD COLUMN approved_at TEXT"); }catch(Exception ignored){}
            d.execSQL("CREATE TABLE IF NOT EXISTS yield_stages(product_code INTEGER NOT NULL,stage TEXT NOT NULL,actual REAL DEFAULT 0,standard REAL DEFAULT 0,formula TEXT DEFAULT '',enabled INTEGER DEFAULT 1,PRIMARY KEY(product_code,stage))");
            d.execSQL("CREATE TABLE IF NOT EXISTS recipe_archive(id INTEGER PRIMARY KEY AUTOINCREMENT,product_code INTEGER,product_name TEXT,saved_at TEXT,user TEXT,payload TEXT)");
        }

        if(o<2){
            // Batch count is one editable value per date + product + shift.
            // Merge accidental duplicate rows before enforcing uniqueness.
            d.execSQL("CREATE TABLE batches_mig(id INTEGER PRIMARY KEY AUTOINCREMENT,date TEXT,product_code INTEGER,shift INTEGER,qty REAL,saved_at TEXT)");
            d.execSQL("INSERT INTO batches_mig(date,product_code,shift,qty,saved_at) SELECT date,product_code,shift,SUM(qty),MAX(saved_at) FROM batches GROUP BY date,product_code,shift");
            d.execSQL("DROP TABLE batches");
            d.execSQL("ALTER TABLE batches_mig RENAME TO batches");
            d.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_batches_shift ON batches(date,product_code,shift)");
        }
        if(o<3){ d.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_batches_shift ON batches(date,product_code,shift)"); }
        if(o<6){
            for(String col:new String[]{"lot","expiry","photo"}){ try{ d.execSQL("ALTER TABLE receives ADD COLUMN "+col+" TEXT"); }catch(Exception ignored){} }
            d.execSQL("CREATE TABLE IF NOT EXISTS counts(id INTEGER PRIMARY KEY AUTOINCREMENT,date TEXT,rm_code TEXT,counted REAL,system REAL,note TEXT)");
        d.execSQL("CREATE TABLE IF NOT EXISTS yield_defs(product_code INTEGER PRIMARY KEY,actual REAL NOT NULL DEFAULT 0,standard REAL NOT NULL DEFAULT 0,formula TEXT NOT NULL DEFAULT '',enabled INTEGER NOT NULL DEFAULT 1)");
        }
        if(o<5){ try{ d.execSQL("ALTER TABLE rms ADD COLUMN reorder REAL NOT NULL DEFAULT 0"); }catch(Exception ignored){} }
        if(o<7){ d.execSQL("CREATE TABLE IF NOT EXISTS yield_defs(product_code INTEGER PRIMARY KEY,actual REAL NOT NULL DEFAULT 0,standard REAL NOT NULL DEFAULT 0,formula TEXT NOT NULL DEFAULT '',enabled INTEGER NOT NULL DEFAULT 1)"); }
        if(o<8){
            d.execSQL("CREATE TABLE IF NOT EXISTS yield_history(id INTEGER PRIMARY KEY AUTOINCREMENT,date TEXT,product_code INTEGER,stage TEXT,dry_output REAL,input_kg REAL,moisture REAL,wastage REAL,batch_qty REAL,formula TEXT,result REAL,note TEXT,user TEXT)");
            d.execSQL("CREATE TABLE IF NOT EXISTS yield_stages(product_code INTEGER NOT NULL,stage TEXT NOT NULL,actual REAL DEFAULT 0,standard REAL DEFAULT 0,formula TEXT DEFAULT '',enabled INTEGER DEFAULT 1,PRIMARY KEY(product_code,stage))");
        d.execSQL("CREATE TABLE IF NOT EXISTS recipe_archive(id INTEGER PRIMARY KEY AUTOINCREMENT,product_code INTEGER,product_name TEXT,saved_at TEXT,user TEXT,payload TEXT)");
        d.execSQL("CREATE TABLE IF NOT EXISTS audit_log(id INTEGER PRIMARY KEY AUTOINCREMENT,ts TEXT,user TEXT,role TEXT,action TEXT,detail TEXT)");
            try{ d.execSQL("ALTER TABLE yield_defs ADD COLUMN stage TEXT DEFAULT 'mixing'"); }catch(Exception ignored){}
            try{ d.execSQL("ALTER TABLE yield_defs ADD COLUMN moisture REAL DEFAULT 0"); }catch(Exception ignored){}
        }
        if(o<4){
            d.execSQL("CREATE TABLE IF NOT EXISTS mixing2_batch_log(id INTEGER PRIMARY KEY AUTOINCREMENT,batch_id INTEGER NOT NULL,date TEXT,product_code INTEGER,shift INTEGER,rm_code TEXT,recipe_qty REAL,batch_count REAL,consumption REAL,created_at TEXT,UNIQUE(batch_id,rm_code))");
            d.execSQL("INSERT OR IGNORE INTO mixing2_batch_log(batch_id,date,product_code,shift,rm_code,recipe_qty,batch_count,consumption,created_at) SELECT b.id,b.date,b.product_code,b.shift,r.rm_code,r.qty,b.qty,b.qty*r.qty,b.saved_at FROM batches b JOIN recipes r ON r.product_code=b.product_code AND r.qty<>0");
        }
        if(o<9){
            try{ d.execSQL("ALTER TABLE counts ADD COLUMN status TEXT DEFAULT 'pending'"); }catch(Exception ignored){}
            try{ d.execSQL("ALTER TABLE counts ADD COLUMN variance REAL DEFAULT 0"); }catch(Exception ignored){}
            try{ d.execSQL("ALTER TABLE counts ADD COLUMN approved_by TEXT"); }catch(Exception ignored){}
            try{ d.execSQL("ALTER TABLE counts ADD COLUMN approved_at TEXT"); }catch(Exception ignored){}
            d.execSQL("CREATE TABLE IF NOT EXISTS yield_stages(product_code INTEGER NOT NULL,stage TEXT NOT NULL,actual REAL DEFAULT 0,standard REAL DEFAULT 0,formula TEXT DEFAULT '',enabled INTEGER DEFAULT 1,PRIMARY KEY(product_code,stage))");
            d.execSQL("CREATE TABLE IF NOT EXISTS recipe_archive(id INTEGER PRIMARY KEY AUTOINCREMENT,product_code INTEGER,product_name TEXT,saved_at TEXT,user TEXT,payload TEXT)");
            try{
                Cursor c=d.rawQuery("SELECT product_code,actual,standard,formula,enabled FROM yield_defs",null);
                while(c.moveToNext()){
                    ContentValues v=new ContentValues();
                    v.put("product_code",c.getInt(0)); v.put("stage","mixing");
                    v.put("actual",c.getDouble(1)); v.put("standard",c.getDouble(2));
                    v.put("formula",c.getString(3)); v.put("enabled",c.getInt(4));
                    d.insertWithOnConflict("yield_stages",null,v,SQLiteDatabase.CONFLICT_IGNORE);
                }
                c.close();
            }catch(Exception ignored){}
        }
    }
    List<JSONObject> rows(String sql,String[] args)throws Exception{Cursor c=getReadableDatabase().rawQuery(sql,args);List<JSONObject> out=new ArrayList<>();try{while(c.moveToNext()){JSONObject x=new JSONObject();for(int i=0;i<c.getColumnCount();i++)x.put(c.getColumnName(i),c.getString(i));out.add(x);}}finally{c.close();}return out;}
    JSONObject one(String sql,String[] args)throws Exception{List<JSONObject> a=rows(sql,args);return a.isEmpty()?null:a.get(0);}
    long insert(String table,ContentValues v){return getWritableDatabase().insertOrThrow(table,null,v);}
    int update(String table,ContentValues v,String where,String[] args){return getWritableDatabase().update(table,v,where,args);}
    void delete(String table,String where,String[] args){getWritableDatabase().delete(table,where,args);}
    void audit(String user,String role,String action,String detail){
        ContentValues v=new ContentValues();
        v.put("ts",new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.US).format(new java.util.Date()));
        v.put("user",user==null?"":user); v.put("role",role==null?"":role);
        v.put("action",action==null?"":action); v.put("detail",detail==null?"":detail);
        getWritableDatabase().insert("audit_log",null,v);
    }
    boolean monthLocked(String ymdOrYm){
        if(ymdOrYm==null||ymdOrYm.length()<7)return false;
        String ym=ymdOrYm.substring(0,7);
        return "1".equals(setting("finalized_"+ym,"0"));
    }
    void setMonthLocked(String ym,boolean on){setSetting("finalized_"+ym,on?"1":"0");}
    String currentUser(){return setting("current_user","Admin");}
    String currentRole(){return setting("role","Admin");}
    boolean canEdit(){String r=currentRole();return !"Operator".equalsIgnoreCase(r);}
    boolean canAdmin(){String r=currentRole();return "Admin".equalsIgnoreCase(r)||"Owner/Admin".equalsIgnoreCase(r)||"Owner".equalsIgnoreCase(r);}

    /** SHA-256 hex of admin PIN (never store plain PIN). */
    void ensureDevicesTable(){
        try{
            getWritableDatabase().execSQL(
                "CREATE TABLE IF NOT EXISTS team_devices(" +
                "device_id TEXT PRIMARY KEY," +
                "device_code TEXT," +
                "name TEXT," +
                "role TEXT," +
                "ip TEXT," +
                "last_seen TEXT," +
                "active INTEGER DEFAULT 1)");
        }catch(Exception ignored){}
    }
    void upsertDevice(String deviceId, String code, String name, String role, String ip, boolean active){
        ensureDevicesTable(); ensureAttendanceTables(); ensureAttendanceTables();
        try{
            android.content.ContentValues v=new android.content.ContentValues();
            v.put("device_id", deviceId);
            v.put("device_code", code);
            v.put("name", name);
            v.put("role", role);
            v.put("ip", ip==null?"":ip);
            v.put("last_seen", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date()));
            v.put("active", active?1:0);
            getWritableDatabase().insertWithOnConflict("team_devices", null, v, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);
        }catch(Exception ignored){}
    }
    java.util.List<org.json.JSONObject> listDevices(){
        ensureDevicesTable();
        try{
            return rows("SELECT * FROM team_devices ORDER BY last_seen DESC", null);
        }catch(Exception e){
            return new java.util.ArrayList<>();
        }
    }
    void setDeviceActive(String deviceId, boolean active){
        ensureDevicesTable();
        android.content.ContentValues v=new android.content.ContentValues();
        v.put("active", active?1:0);
        getWritableDatabase().update("team_devices", v, "device_id=?", new String[]{deviceId});
    }

    boolean hasAccessPin(){
        String h=setting("access_pin_hash","");
        return h!=null && h.length()>=32;
    }
    void setAccessPin(String plain) throws Exception {
        if(plain==null) plain="";
        plain=plain.trim();
        if(plain.length()<4) throw new Exception("Access PIN at least 4 digits");
        setSetting("access_pin_hash", sha256(plain));
    }
    boolean checkAccessPin(String plain){
        if(!hasAccessPin()) return false;
        try{ return sha256(plain==null?"":plain.trim()).equals(setting("access_pin_hash","")); }
        catch(Exception e){ return false; }
    }
    boolean setupDone(){ return "1".equals(setting("setup_done","0")); }
    void markSetupDone(){ setSetting("setup_done","1"); }

    boolean hasAdminPin(){
        String h=setting("admin_pin_hash","");
        return h!=null && h.length()>=32;
    }
    void setAdminPin(String plain) throws Exception {
        if(plain==null) plain="";
        plain=plain.trim();
        if(plain.length()<4) throw new Exception("Admin PIN must be at least 4 digits");
        setSetting("admin_pin_hash", sha256(plain));
        setSetting("admin_security_on","1");
    }
    boolean checkAdminPin(String plain){
        if(!hasAdminPin()) return false;
        try{
            return sha256(plain==null?"":plain.trim()).equals(setting("admin_pin_hash",""));
        }catch(Exception e){ return false; }
    }
    private static String sha256(String s) throws Exception {
        java.security.MessageDigest md=java.security.MessageDigest.getInstance("SHA-256");
        byte[] d=md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb=new StringBuilder();
        for(byte b:d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
    /** After first setup, prefer non-admin default only when explicitly reset — keep currentRole as stored. */
    boolean securityHardened(){
        return hasAdminPin() && !"0".equals(setting("admin_security_on","1"));
    }


    void ensureAttendanceTables(){
        try{
            getWritableDatabase().execSQL(
                "CREATE TABLE IF NOT EXISTS workers("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "code TEXT,"+
                "name TEXT NOT NULL,"+
                "shift TEXT,"+
                "photo_path TEXT,"+
                "gender TEXT,"+
                "active INTEGER DEFAULT 1)");
            try{ getWritableDatabase().execSQL("ALTER TABLE workers ADD COLUMN gender TEXT"); }catch(Exception ignored){}
            getWritableDatabase().execSQL(
                "CREATE TABLE IF NOT EXISTS attendance("+
                "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "worker_id INTEGER,"+
                "worker_name TEXT,"+
                "day TEXT,"+
                "time TEXT,"+
                "punch TEXT,"+
                "method TEXT,"+
                "photo_path TEXT,"+
                "by_user TEXT,"+
                "device_code TEXT,"+
                "note TEXT,"+ "lat REAL,"+ "lng REAL,"+ "late INTEGER DEFAULT 0)");
            try{getWritableDatabase().execSQL("ALTER TABLE attendance ADD COLUMN lat REAL");}catch(Exception ignored){}
            try{getWritableDatabase().execSQL("ALTER TABLE attendance ADD COLUMN lng REAL");}catch(Exception ignored){}
            try{getWritableDatabase().execSQL("ALTER TABLE attendance ADD COLUMN late INTEGER DEFAULT 0");}catch(Exception ignored){}
        }catch(Exception ignored){}
    }
    long addWorker(String code,String name,String shift,String photo){
        return addWorker(code,name,shift,photo,"");
    }
    long addWorker(String code,String name,String shift,String photo,String gender){
        ensureAttendanceTables();
        android.content.ContentValues v=new android.content.ContentValues();
        v.put("code",code); v.put("name",name); v.put("shift",shift);
        v.put("photo_path",photo==null?"":photo);
        v.put("gender", gender==null?"":gender);
        v.put("active",1);
        return getWritableDatabase().insert("workers",null,v);
    }
    java.util.List<org.json.JSONObject> listWorkers(){
        ensureAttendanceTables();
        try{ return rows("SELECT * FROM workers WHERE active=1 ORDER BY name",null); }
        catch(Exception e){ return new java.util.ArrayList<>(); }
    }
    long addAttendance(long workerId,String wname,String day,String time,String punch,
                       String method,String photo,String byUser,String device,String note){
        return addAttendance(workerId,wname,day,time,punch,method,photo,byUser,device,note,0,0,0);
    }
    long addAttendance(long workerId,String wname,String day,String time,String punch,
                       String method,String photo,String byUser,String device,String note,
                       double lat,double lng,int late){
        ensureAttendanceTables();
        android.content.ContentValues v=new android.content.ContentValues();
        v.put("worker_id",workerId); v.put("worker_name",wname);
        v.put("day",day); v.put("time",time); v.put("punch",punch);
        v.put("method",method); v.put("photo_path",photo==null?"":photo);
        v.put("by_user",byUser); v.put("device_code",device);
        v.put("note",note==null?"":note);
        v.put("lat",lat); v.put("lng",lng); v.put("late",late);
        return getWritableDatabase().insert("attendance",null,v);
    }

    /** Today's IN punches: total workers, male, female (by worker gender). */
    java.util.List<org.json.JSONObject> attendanceReportRows(String day){
        ensureAttendanceTables();
        java.util.List<org.json.JSONObject> out=new java.util.ArrayList<>();
        try{
            java.util.List<org.json.JSONObject> punches=rows(
                "SELECT a.*, IFNULL(w.code,'') AS wcode, IFNULL(w.shift,'') AS wshift, IFNULL(w.gender,'') AS wgender "+
                "FROM attendance a LEFT JOIN workers w ON w.id=a.worker_id WHERE a.day=? ORDER BY a.worker_name, a.time",
                new String[]{day});
            java.util.LinkedHashMap<Long,org.json.JSONObject> map=new java.util.LinkedHashMap<>();
            for(org.json.JSONObject r:punches){
                long wid=r.optLong("worker_id");
                org.json.JSONObject row=map.get(wid);
                if(row==null){
                    row=new org.json.JSONObject();
                    row.put("worker_id",wid);
                    row.put("name", r.optString("worker_name"));
                    row.put("code", r.optString("wcode"));
                    row.put("shift", r.optString("wshift"));
                    row.put("gender", r.optString("wgender"));
                    row.put("in_time","");
                    row.put("out_time","");
                    row.put("in_method","");
                    row.put("out_method","");
                    map.put(wid,row);
                }
                String punch=r.optString("punch");
                if("IN".equalsIgnoreCase(punch) && row.optString("in_time").isEmpty()){
                    row.put("in_time", r.optString("time"));
                    row.put("in_method", r.optString("method"));
                }
                if("OUT".equalsIgnoreCase(punch)){
                    row.put("out_time", r.optString("time"));
                    row.put("out_method", r.optString("method"));
                }
            }
            out.addAll(map.values());
        }catch(Exception ignored){}
        return out;
    }
    int[] attendanceGenderStats(String day){
        ensureAttendanceTables();
        // [0]=total unique IN, [1]=male, [2]=female
        int[] out=new int[]{0,0,0};
        try{
            java.util.List<org.json.JSONObject> rows=rows(
                "SELECT a.worker_id, IFNULL(w.gender,'') AS gender FROM attendance a "+
                "LEFT JOIN workers w ON w.id=a.worker_id "+
                "WHERE a.day=? AND a.punch='IN' GROUP BY a.worker_id",
                new String[]{day});
            out[0]=rows.size();
            for(org.json.JSONObject r:rows){
                String g=r.optString("gender","").trim().toLowerCase(java.util.Locale.US);
                if(g.startsWith("f")||g.contains("female")||g.contains("মহিলা")||g.contains("মেয়ে")||g.equals("nari")) out[2]++;
                else if(g.startsWith("m")||g.contains("male")||g.contains("পুরুষ")||g.contains("ছেলে")) out[1]++;
            }
        }catch(Exception ignored){}
        return out;
    }
    boolean hasRecentPunch(long workerId, String punch, int withinMinutes){
        ensureAttendanceTables();
        try{
            String day=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(new java.util.Date());
            java.util.List<org.json.JSONObject> rows=rows(
                "SELECT time,punch FROM attendance WHERE worker_id=? AND day=? AND punch=? ORDER BY id DESC LIMIT 5",
                new String[]{String.valueOf(workerId), day, punch});
            if(rows.isEmpty()) return false;
            java.text.SimpleDateFormat tf=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.US);
            long now=System.currentTimeMillis();
            for(org.json.JSONObject r:rows){
                try{
                    long t=tf.parse(day+" "+r.optString("time")).getTime();
                    if(now-t <= withinMinutes*60L*1000L) return true;
                }catch(Exception ignored){}
            }
            return false;
        }catch(Exception e){ return false; }
    }
    java.util.List<org.json.JSONObject> listAbsentWorkers(String day){
        ensureAttendanceTables();
        java.util.List<org.json.JSONObject> out=new java.util.ArrayList<>();
        try{
            java.util.HashSet<Long> present=new java.util.HashSet<>();
            for(org.json.JSONObject r:rows("SELECT DISTINCT worker_id FROM attendance WHERE day=? AND punch='IN'", new String[]{day}))
                present.add(r.optLong("worker_id"));
            for(org.json.JSONObject w:listWorkers()) if(!present.contains(w.optLong("id"))) out.add(w);
        }catch(Exception ignored){}
        return out;
    }
    int countLate(String day){
        try{
            java.util.List<org.json.JSONObject> r=rows("SELECT COUNT(*) AS c FROM attendance WHERE day=? AND punch='IN' AND IFNULL(late,0)=1", new String[]{day});
            return r.isEmpty()?0:r.get(0).optInt("c");
        }catch(Exception e){return 0;}
    }
    java.util.List<org.json.JSONObject> listAttendance(String day){
        ensureAttendanceTables();
        try{
            if(day==null||day.isEmpty())
                return rows("SELECT * FROM attendance ORDER BY id DESC LIMIT 100",null);
            return rows("SELECT * FROM attendance WHERE day=? ORDER BY id DESC",new String[]{day});
        }catch(Exception e){ return new java.util.ArrayList<>(); }
    }

}
