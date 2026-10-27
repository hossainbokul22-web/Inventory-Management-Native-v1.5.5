package com.rmstock.nativeapp;
import android.database.sqlite.SQLiteDatabase;
import com.googlecode.tesseract.android.TessBaseAPI;

import android.Manifest;import android.app.*;import android.content.*;import android.content.pm.PackageManager;import android.graphics.*;import android.graphics.pdf.PdfDocument;import android.net.Uri;import android.os.*;import android.print.*;import android.provider.MediaStore;import android.speech.RecognizerIntent;import android.view.*;import android.widget.*;import androidx.annotation.Nullable;import android.app.Activity;import androidx.core.app.ActivityCompat;import androidx.core.content.ContextCompat;import androidx.core.content.FileProvider;import com.googlecode.tesseract.android.TessBaseAPI;import org.json.*;import java.io.*;import java.text.*;import java.util.*;import java.util.concurrent.*;

public class MainActivity extends Activity {
 private StockDb db; private File photoFile;private LinearLayout root,content,navRow; private ScrollView mainScroll; private LinearLayout chatOverlay;private TextView brandView;private final List<ImageView> navIcons=new ArrayList<>();private final List<TextView> navViews=new ArrayList<>();private final List<String> navKeys=new ArrayList<>(); private Uri photoUri; private static final int CAM=77,PHOTO=78,CHAT_IMG=94,CHAT_FILE=95; private final Handler main=new Handler(Looper.getMainLooper()); private String role(){return db.currentRole();} private String user(){return db.currentUser();}
 private File pendingPdf; private String currentScreen="Dashboard"; private final List<Runnable> dashboardRunnables=new ArrayList<>(); private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);} private TextView title(String s){TextView t=Ui.text(this,s,21,Ui.INK,true);t.setPadding(dp(2),dp(10),dp(2),dp(8));return t;} private Button btn(String s){return Ui.primary(this,s);} private EditText edit(String hint){return Ui.field(this,hint);} private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);l.setPadding(0,dp(3),0,dp(3));return l;} private void add(View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);if(v instanceof Button||v instanceof EditText)p.setMargins(0,dp(4),0,dp(4));else if(v.getLayoutParams() instanceof LinearLayout.LayoutParams)p=(LinearLayout.LayoutParams)v.getLayoutParams();content.addView(v,p);}
 @Override public void onCreate(Bundle b){super.onCreate(b);Ui.init(this);db=new StockDb(this);if(db.setting("role","").isEmpty())db.setSetting("role","Admin");
    if(db.setting("current_user","").isEmpty())db.setSetting("current_user","Admin");
    build();AlertScheduler.schedule(this);requestNotifPermission();requestDutyLocation();showDashboard();}

 private void build(){
    root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Ui.BG);

    LinearLayout header=new LinearLayout(this);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(dp(16),dp(10),dp(12),dp(10));
    header.setBackgroundColor(Ui.NAVY);
    LinearLayout names=new LinearLayout(this);
    names.setOrientation(LinearLayout.VERTICAL);
    brandView=Ui.text(this,db.setting("company","Company Name"),17,0xFFFFFFFF,true);
    brandView.setMaxLines(2);
    brandView.setEllipsize(android.text.TextUtils.TruncateAt.END);
    names.addView(brandView);
    names.addView(Ui.text(this,"Inventory Management",12,0xFFBFD3E8,false));
    header.addView(names,new LinearLayout.LayoutParams(0,-2,1));
    TextView gear=Ui.text(this,"\u2699",22,0xFFFFFFFF,false);
    gear.setPadding(dp(12),dp(4),dp(4),dp(4));
    gear.setOnClickListener(v->gateOpen("Settings",()->showSettings()));
    TextView msgIcon=Ui.text(this,"💬",20,0xFFFFFFFF,false);
    msgIcon.setPadding(dp(10),dp(4),dp(8),dp(4));
    msgIcon.setOnClickListener(v->showMessenger());
    header.addView(msgIcon);
    header.addView(gear);
    root.addView(header,new LinearLayout.LayoutParams(-1,-2));

    ScrollView sv=new ScrollView(this); mainScroll=sv;
    content=new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(14),dp(12),dp(14),dp(24));
    sv.addView(content);
    root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

    View line=new View(this);
    line.setBackgroundColor(Ui.LINE);
    root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    navRow=new LinearLayout(this);
    navRow.setOrientation(LinearLayout.HORIZONTAL);
    navRow.setBackgroundColor(Ui.CARD);
    navRow.setPadding(dp(4),dp(4),dp(4),dp(4));
    navViews.clear();navIcons.clear();navKeys.clear();
    String[] keys={"Home","Entry","Batch","Reports","More"};
    int[] icons={R.drawable.ic_nav_home,R.drawable.ic_nav_entry,R.drawable.ic_nav_batch,R.drawable.ic_nav_reports,R.drawable.ic_nav_more};
    for(int i=0;i<keys.length;i++){
        final String key=keys[i];
        LinearLayout cell=new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(0,dp(5),0,dp(5));
        ImageView iv=new ImageView(this);
        iv.setImageResource(icons[i]);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setPadding(dp(16),dp(4),dp(16),dp(4));
        iv.setColorFilter(Ui.MUTED);
        cell.addView(iv,new LinearLayout.LayoutParams(dp(60),dp(30)));
        TextView lb=Ui.text(this,tabName(key),11,Ui.MUTED,false);
        lb.setGravity(Gravity.CENTER);
        lb.setMaxLines(1);
        cell.addView(lb,new LinearLayout.LayoutParams(-2,-2));
        cell.setOnClickListener(v->go(key));
        navRow.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
        navIcons.add(iv);navViews.add(lb);navKeys.add(key);
    }
    root.addView(navRow,new LinearLayout.LayoutParams(-1,-2));
    setContentView(root);
 }
 private void go(String key){
    if(key.equals("Home"))showDashboard();else if(key.equals("Entry"))showDaily();else if(key.equals("Batch"))showBatch();else if(key.equals("Reports"))showReports();else showMore();
 }
 private String navKeyFor(String s){
    if(s==null||s.startsWith("Dashboard"))return "Home";
    if(s.startsWith("RM Daily Entry"))return "Entry";
    if(s.startsWith("Input Batch"))return "Batch";
    if(s.contains("Report")||s.contains("Monthly")||s.contains("Usage")||s.contains("Batch Log")||s.contains("Saved snapshot")||s.startsWith("Mixing-2 — Batch"))return "Reports";
    return "More";
 }
 private void highlightNav(){
    String k=navKeyFor(currentScreen);
    for(int i=0;i<navViews.size();i++){
        boolean on=navKeys.get(i).equals(k);int col=Ui.TAB[i];
        TextView t=navViews.get(i);ImageView iv=navIcons.get(i);
        t.setTextColor(on?col:Ui.MUTED);t.setTypeface(null,on?Typeface.BOLD:Typeface.NORMAL);
        iv.setColorFilter(on?col:Ui.MUTED);iv.setBackground(on?Ui.shape(this,Ui.TAB_SOFT[i],0,15):null);
    }
 }
 private String tabName(String key){String v=db.setting("tab_"+key,key);return v==null||v.trim().isEmpty()?key:v.trim();}
 private void gateOpen(String why,Runnable r){Guard.run(this,db,why,false,r);}
 
 private void requestDutyLocation(){
    try{
        if(androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.ACCESS_FINE_LOCATION)
                ==android.content.pm.PackageManager.PERMISSION_GRANTED){
            startDutyServiceSafe();
            // Android 10+ background optional
            if(android.os.Build.VERSION.SDK_INT>=29
                && androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    !=android.content.pm.PackageManager.PERMISSION_GRANTED){
                try{ androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_BACKGROUND_LOCATION}, LOC_PERM); }catch(Exception ignored){}
            }
            return;
        }
        androidx.core.app.ActivityCompat.requestPermissions(this,
            new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION},
            LOC_PERM);
    }catch(Exception e){ /* ignore */ }
 }
 private void startDutyServiceSafe(){
    try{ DutyLocationService.start(this); }catch(Exception e){ toast("Location service: "+e.getMessage()); }
 }

 private void showTeamLiveLocations(){
    clear("Team live locations");
    add(Ui.muted(this,"Latest on-duty points received on this admin phone (via hotspot sync)."));
    try{
        db.getWritableDatabase().execSQL(
            "CREATE TABLE IF NOT EXISTS duty_locations(id INTEGER PRIMARY KEY AUTOINCREMENT,user TEXT, lat REAL, lng REAL, acc REAL, ts TEXT, received_at TEXT)");
        java.util.List<org.json.JSONObject> rows=db.rows("SELECT * FROM duty_locations ORDER BY received_at DESC LIMIT 30",null);
        if(rows.isEmpty()){
            add(Ui.muted(this,"No locations yet. Supervisors need Client mode + hotspot + location Allow."));
        }else{
            for(final org.json.JSONObject r:rows){
                LinearLayout c=Ui.card(this,Ui.CARD);
                c.addView(Ui.text(this,r.optString("user","?"),15,Ui.NAVY_TEXT,true));
                c.addView(Ui.text(this,"Lat "+r.optDouble("lat")+"  Lng "+r.optDouble("lng"),13,Ui.INK,false));
                c.addView(Ui.muted(this,r.optString("ts")+" · acc "+r.optDouble("acc")));
                Button mapB=Ui.secondary(this,"Open in Maps");
                mapB.setOnClickListener(vv->{
                    try{
                        android.net.Uri uri=android.net.Uri.parse("geo:"+r.optDouble("lat")+","+r.optDouble("lng")+"?q="+r.optDouble("lat")+","+r.optDouble("lng")+"("+android.net.Uri.encode(r.optString("user"))+")");
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    }catch(Exception ex){ toast("No maps app"); }
                });
                c.addView(mapB);
                add(c);
            }
        }
    }catch(Exception e){ add(Ui.muted(this,e.getMessage())); }
    Button back=Ui.secondary(this,"Back to Settings");
    back.setOnClickListener(v->showSettings());
    add(back);
 }
private void requestNotifPermission(){
    try{
        AlertReceiver.ensureChannel(this);
        if(android.os.Build.VERSION.SDK_INT>=33){
            if(ContextCompat.checkSelfPermission(this,"android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this,new String[]{"android.permission.POST_NOTIFICATIONS"},91);
            }
        }
    }catch(Exception ignored){}
 }
 private boolean open(){if(!Guard.enabled(db)||Guard.valid())return true;Guard.run(this,db,"Unlock to continue",false,()->toast("Unlocked ✔ — tap again"));return false;}
 private void addCard(View c){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(10));c.setLayoutParams(p);add(c);}
 private double liveGap(){double g=num(db.setting("live_gap","3"));if(g<2.5)g=2.5;if(g>5)g=5;return g;}
 private void openDailyPm(){startActivity(new Intent(this,PmActivity.class));}
 private EditText dateField(String hint){
    final EditText e=Ui.field(this,hint);
    e.setFocusable(false);e.setClickable(true);e.setCursorVisible(false);
    e.setOnClickListener(v->{Calendar c=Calendar.getInstance();try{String[] p=e.getText().toString().trim().split("-");if(p.length==3)c.set(Integer.parseInt(p[0]),Integer.parseInt(p[1])-1,Integer.parseInt(p[2]));}catch(Exception ignored){}
        new DatePickerDialog(this,(view,y,m,dd)->e.setText(String.format(Locale.US,"%04d-%02d-%02d",y,m+1,dd)),c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();});
    return e;
 }
 private String ymd(EditText e)throws Exception{String t=e.getText().toString().trim();if(!PlanLogic.validDate(t))throw new Exception("Please pick a valid date");return t;}

    
 /** Jump ScrollView to top so edit form is visible without dragging */
 private void scrollFormTop(){
    try{
        if(content==null)return;
        android.view.ViewParent par=content.getParent();
        while(par!=null && !(par instanceof ScrollView)) par=par.getParent();
        if(par instanceof ScrollView){
            final ScrollView sv=(ScrollView)par;
            sv.post(()->{ sv.fullScroll(View.FOCUS_UP); sv.smoothScrollTo(0,0); });
        }
        content.post(()->{ if(content.getChildCount()>0) content.getChildAt(0).requestFocus(); });
    }catch(Exception ignored){}
 }

private void clear(String head){
    currentScreen=head;
    for(Runnable task:dashboardRunnables)main.removeCallbacks(task);
    dashboardRunnables.clear();
    dismissChatOverlay();
    if(mainScroll!=null) mainScroll.setVisibility(View.VISIBLE);
    if(navRow!=null) navRow.setVisibility(View.VISIBLE);
    content.removeAllViews();
    if(!"Team Messenger".equals(head)) add(title(lbl(head)));
    highlightNav();
}

 @Override public void onBackPressed(){
    String h=currentScreen==null?"":currentScreen;
    // Settings sub-pages → Settings
    if(h.startsWith("Settings — ")||h.equals("Team live locations")){showSettings();return;}
    if(h.equals("Team Messenger")){showDashboard();return;}
    // Anything opened from More → back to More (not Dashboard)
    if(h.equals("Settings")||h.equals("Wastage")||h.equals("Stock Count")||h.equals("Daily Summary")
        ||h.startsWith("Batch Planner")||h.startsWith("RM Master")||h.startsWith("Master Data")
        ||h.startsWith("Product Master")||h.startsWith("Mixing-2 Recipe")||h.startsWith("Mixing-2 RM Recipe")
        ||h.startsWith("Custom Sheets")||h.startsWith("Yield")||h.startsWith("Process Loss")
        ||h.startsWith("Owner pack")||h.startsWith("Audit")||h.startsWith("Offline AI")||h.startsWith("AI Draft")
        ||h.startsWith("Voice")||h.startsWith("Fix entry")||h.startsWith("Assistant")){
        showMore();return;
    }
    // Report detail screens → Reports list
    if(h.contains("Daily Date Report")||h.contains("Final Monthly Usage")||h.contains("RM Mixing-2 Batch Log")
        ||h.contains("RM Receive Monthly")||h.contains("RM Mixing-2 Monthly")||h.contains("Saved snapshot")
        ||h.equals("Reports — Sheets")){
        if(h.equals("Reports — Sheets")){showDashboard();return;}
        showReports();return;
    }
    // Entry sub-screens → Entry tab
    if(h.contains("Daily Entry")||h.startsWith("RM Daily")){showDaily();return;}
    // Batch tab sub-screens → Batch tab
    if(h.contains("Input Batch")||h.contains("Mixing-2 — Batch")||h.contains("Batch Log")){showBatch();return;}
    // Main tabs / unknown non-dashboard → Dashboard
    if(!h.equals("Dashboard")&&!h.equals("More")){showDashboard();return;}
    if(h.equals("More")){showDashboard();return;}
    super.onBackPressed();
}
 private boolean admin(){return db.canAdmin()||db.canEdit();}
 private void showDashboard(){
    clear("Dashboard");
    try{
        TextView live=new TextView(this); live.setText("LIVE DATA  •  Today’s RM Receive, Mixing-2 Use, Batch and Closing Stock");
        live.setTextSize(12); live.setTextColor(Ui.MUTED); live.setPadding(dp(2),0,dp(2),dp(10)); add(live);

        LinearLayout grid=new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r1=row(),r2=row();
        TextView receiveValue=new TextView(this),useValue=new TextView(this),batchValue=new TextView(this),closingValue=new TextView(this);
        addLiveCard(r1,lbl("Today RM Receive"),receiveValue,"#EAF7EF");
        addLiveCard(r1,lbl("Today Mixing-2 Use"),useValue,"#EEF4FC");
        addLiveCard(r2,lbl("Today Batch"),batchValue,"#FFF5E8");
        addLiveCard(r2,lbl("Today Closing Stock"),closingValue,"#F3EEFC");
        grid.addView(r1); grid.addView(r2); add(grid);

        // Yield % rotating card (product-wise)
        addYieldLiveCard();
        addProcessLossLiveCard();
        try{Button cs=Ui.secondary(this,"Custom sheets");cs.setOnClickListener(v->showCustom());add(cs);}catch(Exception ignored){}
        addShiftWiseSummary();
        addSmartSuggestions();
        // Quick access: Boss report + AI voucher (dashboard)
        try{
            LinearLayout quick=Ui.card(this,Ui.CARD);
            quick.addView(dashIconTitle("⚡", "Quick actions", Color.rgb(20, 55, 95)));
            quick.addView(Ui.muted(this,"Boss report needs internet when you share. Voucher scan works offline."));
            LinearLayout rowQ=new LinearLayout(this);
            rowQ.setOrientation(LinearLayout.HORIZONTAL);
            Button bossM=Ui.primary(this,"Send report → Boss");
            bossM.setOnClickListener(v->{
                // opens Assistant with monthly send (email/WhatsApp chooser)
                showAssistant();
                toast("Tap «This month → boss» or type: send monthly to boss");
            });
            Button bossD=Ui.secondary(this,"Today → Boss");
            bossD.setOnClickListener(v->runAssistantCommand("send daily to boss"));
            rowQ.addView(bossM,Ui.lp(this,0,dp(48),1,0,0,6,0));
            rowQ.addView(bossD,Ui.lp(this,0,dp(48),1,6,0,0,0));
            quick.addView(rowQ);
            LinearLayout rowQ2=new LinearLayout(this);
            rowQ2.setOrientation(LinearLayout.HORIZONTAL);
            rowQ2.setPadding(0,dp(8),0,0);
            Button scan=Ui.primary(this,"Scan voucher (AI)");
            scan.setOnClickListener(v->showAI());
            Button asst=Ui.secondary(this,"Assistant");
            asst.setOnClickListener(v->showAssistant());
            rowQ2.addView(scan,Ui.lp(this,0,dp(48),1,0,0,6,0));
            rowQ2.addView(asst,Ui.lp(this,0,dp(48),1,6,0,0,0));
            quick.addView(rowQ2);
            add(quick);
        }catch(Exception ignored){}

        String d=today();
        List<String> receives=new ArrayList<>(), uses=new ArrayList<>(), batches=new ArrayList<>(), closings=new ArrayList<>();
        for(JSONObject x:db.rows("SELECT r.rm_code, m.name, m.uom, SUM(r.qty) qty FROM receives r LEFT JOIN rms m ON m.code=r.rm_code WHERE r.date=? GROUP BY r.rm_code,m.name,m.uom HAVING ABS(SUM(r.qty))>0.0000001 ORDER BY CAST(r.rm_code AS INTEGER)",new String[]{d}))
            receives.add(x.optString("rm_code")+" • "+x.optString("name","RM")+"  "+fmt(x.optDouble("qty"))+" "+x.optString("uom","kg"));
        for(JSONObject x:db.rows("SELECT l.rm_code,m.name,m.uom,SUM(l.consumption) qty FROM mixing2_batch_log l LEFT JOIN rms m ON m.code=l.rm_code WHERE l.date=? GROUP BY l.rm_code,m.name,m.uom HAVING ABS(SUM(l.consumption))>0.0000001 ORDER BY CAST(l.rm_code AS INTEGER)",new String[]{d}))
            uses.add(x.optString("rm_code")+" • "+x.optString("name","RM")+"  "+fmt(x.optDouble("qty"))+" "+x.optString("uom","kg"));
        for(JSONObject x:db.rows("SELECT b.product_code,p.name,b.shift,SUM(b.qty) qty FROM batches b LEFT JOIN products p ON p.code=b.product_code WHERE b.date=? GROUP BY b.product_code,p.name,b.shift ORDER BY b.product_code,b.shift",new String[]{d}))
            batches.add(x.optString("name", "Product "+x.optString("product_code"))+" • Shift "+x.optString("shift")+"  "+fmt(x.optDouble("qty"))+" batch");
        double totalReceive=0,totalUse=0;
        JSONObject tr=db.one("SELECT COALESCE(SUM(qty),0) qty FROM receives WHERE date=?",new String[]{d}); if(tr!=null)totalReceive=tr.optDouble("qty");
        JSONObject tu=db.one("SELECT COALESCE(SUM(consumption),0) qty FROM mixing2_batch_log WHERE date=?",new String[]{d}); if(tu!=null)totalUse=tu.optDouble("qty");
        int batchCount=0; JSONObject bc=db.one("SELECT COUNT(*) n FROM batches WHERE date=?",new String[]{d}); if(bc!=null)batchCount=bc.optInt("n");
        final Map<String,Double> recMap=sumMap("SELECT rm_code k,SUM(qty) v FROM receives WHERE date<=? GROUP BY rm_code",new String[]{d});
        final Map<String,Double> useMap=sumMap("SELECT rm_code k,SUM(consumption) v FROM mixing2_batch_log WHERE date<=? GROUP BY rm_code",new String[]{d});
        final Map<String,Double> use7=sumMap("SELECT rm_code k,SUM(consumption) v FROM mixing2_batch_log WHERE date>=? AND date<? GROUP BY rm_code",new String[]{PlanLogic.shiftDate(d,-7),d});
        final List<JSONObject> rmList=db.rows("SELECT code,name,uom,opening,reorder FROM rms ORDER BY CAST(code AS INTEGER)",null);
        final Map<String,Double> closeMap=new HashMap<>();
        for(JSONObject rm:rmList){String code=rm.getString("code");double closing=rm.optDouble("opening",0)+nz(recMap.get(code))-nz(useMap.get(code));if(Double.isNaN(closing)||Double.isInfinite(closing))closing=0d;closeMap.put(code,closing);closings.add(code+" • "+rm.getString("name")+"  "+fmt(closing)+" "+rm.getString("uom"));}
        double sumClose=0;for(double cv2:closeMap.values())sumClose+=cv2;
        List<List<String>> liveLists=new ArrayList<>();liveLists.add(receives);liveLists.add(uses);liveLists.add(batches);liveLists.add(closings);
        startLive(new TextView[]{receiveValue,useValue,batchValue,closingValue},liveLists,
            new String[]{"No RM receive today","No Mixing-2 use today","No batch entered today","No raw materials"},
            new String[]{totalReceive==0?"0 KG":fmt(totalReceive)+" KG",totalUse==0?"0 KG":fmt(totalUse)+" KG",batchCount+" Batch",fmt(sumClose)+" KG"},
            new String[]{"Total received","Total used","Entries today","Total closing stock"});

        addSectionTitle("Today Batch",Ui.SOFT_BLUE);
        addSimpleTable(new String[]{"Product","Batch / Shift","Qty"},batches);
        addSectionTitle("Today Receive",Ui.SOFT_GREEN);
        addSimpleTable(new String[]{"RM","Receive","UoM"},receives);
        addSectionTitle("Today Mixing-2 Use",Ui.SOFT_PURPLE);
        addSimpleTable(new String[]{"RM","Use","UoM"},uses);
        LinearLayout quick=row();
        Button planB=Ui.secondary(this,"Batch Planner");planB.setOnClickListener(v->showRmPlanner());quick.addView(planB,Ui.lp(this,0,dp(48),1,0,0,6,0));
        Button pmB=Ui.secondary(this,"Daily PM Report");pmB.setOnClickListener(v->openDailyPm());quick.addView(pmB,Ui.lp(this,0,dp(48),1,6,0,0,0));
        add(quick);
        final List<JSONObject> byClose=new ArrayList<>(rmList);
        Collections.sort(byClose,(p1,p2)->Double.compare(nz(closeMap.get(p1.optString("code"))),nz(closeMap.get(p2.optString("code")))));
        {
            int warnDays=(int)Math.round(num(db.setting("expiry_days","14")));if(warnDays<1)warnDays=14;
            int exN=0;
            for(JSONObject x:db.rows("SELECT r.rm_code,r.lot,r.expiry,r.qty,m.name,m.uom FROM receives r LEFT JOIN rms m ON m.code=r.rm_code WHERE r.expiry<>'' AND r.expiry<=? ORDER BY r.expiry LIMIT 40",new String[]{PlanLogic.shiftDate(d,warnDays)})){
                if(nz(closeMap.get(x.optString("rm_code")))<=0)continue;
                if(exN==0)addSectionTitle("Expiry Watch",Ui.SOFT_AMBER);
                if(exN>=8)break;
                int left=PlanLogic.daysBetween(d,x.optString("expiry"));
                boolean gone=left<0;
                LinearLayout ec=Ui.card(this,gone?Ui.SOFT_RED:Ui.SOFT_AMBER);
                LinearLayout et=new LinearLayout(this);et.setGravity(Gravity.CENTER_VERTICAL);
                et.addView(Ui.text(this,x.optString("name"),15,Ui.INK,true),new LinearLayout.LayoutParams(0,-2,1));
                et.addView(Ui.text(this,gone?"EXPIRED":(left==0?"EXPIRES TODAY":left+" DAYS LEFT"),11,gone?Ui.BAD:Ui.WARN,true));
                ec.addView(et);
                ec.addView(Ui.muted(this,"Lot "+(x.optString("lot").isEmpty()?"—":x.optString("lot"))+"  \u2022  expires "+x.optString("expiry")+"  \u2022  received "+fmt(x.optDouble("qty"))+" "+x.optString("uom")+"  \u2022  use this lot first"));
                LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.setMargins(0,0,0,dp(8));ec.setLayoutParams(ep);add(ec);exN++;
            }
        }
        addSectionTitle("Stock Watch",Ui.SOFT_RED);
        int watchN=0;
        for(JSONObject rm:byClose){String code=rm.optString("code");double cl=nz(closeMap.get(code)),avg=nz(use7.get(code))/7.0,re=rm.optDouble("reorder",0);int st=PlanLogic.status(cl,re,avg);if(st!=PlanLogic.OK&&watchN<12){add(rmStockCard(rm,cl,avg,re,st));watchN++;}}
        if(watchN==0){LinearLayout okc=Ui.card(this,Ui.SOFT_GREEN);okc.addView(Ui.text(this,"All raw materials look fine",15,Ui.INK,true));okc.addView(Ui.muted(this,"Nothing is below zero, under its reorder level, or running out within 7 days. Set a Reorder level in RM Master to get early warnings."));add(okc);}
        addSectionTitle("Mixing-2 use — last 7 days",Ui.SOFT_PURPLE);
        final Map<String,Double> byDay=sumMap("SELECT date k,SUM(consumption) v FROM mixing2_batch_log WHERE date>=? AND date<=? GROUP BY date",new String[]{PlanLogic.shiftDate(d,-6),d});
        double maxDay=0;for(int k=6;k>=0;k--)maxDay=Math.max(maxDay,nz(byDay.get(PlanLogic.shiftDate(d,-k))));
        LinearLayout chart=Ui.card(this,Ui.CARD);
        for(int k=6;k>=0;k--){String day=PlanLogic.shiftDate(d,-k);double val=nz(byDay.get(day));chart.addView(Ui.barRow(this,day.substring(5)+(k==0?"  (today)":""),fmt(val)+" kg",maxDay<=0?0:val/maxDay,Ui.NAVY_TEXT));}
        add(chart);
        addSectionTitle("Stock Overview",Ui.SOFT_BLUE);
        final List<JSONObject> desc=new ArrayList<>(byClose);Collections.reverse(desc);
        double maxClose=1;for(JSONObject rm:desc)maxClose=Math.max(maxClose,nz(closeMap.get(rm.optString("code"))));
        LinearLayout ov=Ui.card(this,Ui.CARD);
        for(JSONObject rm:desc){double cv=nz(closeMap.get(rm.optString("code")));ov.addView(Ui.barRow(this,rm.optString("code")+"  "+rm.optString("name"),fmt(cv)+" "+rm.optString("uom"),Math.max(0,cv)/maxClose,cv<0?Ui.BAD:Ui.ACCENT));}
        add(ov);
        LinearLayout foot=new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setPadding(0,dp(14),0,dp(10));
        TextView ownerL=new TextView(this);
        ownerL.setText("Developed by MD. Bokul Hossain,\nDiploma in Food Technology  • Inventory Management");
        ownerL.setTextSize(11);
        ownerL.setTextColor(Ui.MUTED);
        ownerL.setGravity(Gravity.START|Gravity.TOP);
        TextView ownerR=new TextView(this);
        ownerR.setText("Edinburgh  দুর্গের মতো  হয়তো রহস্যময় জীবন— সুন্দর, তবে কাছে গেলে আরও গভীর, পরিবর্তনশীল  আপনি হাসলে একটা জীবন নীলকান্তমণির মতো অমূল্য\nIntrovert Mimusops");
        ownerR.setTextSize(10);
        ownerR.setTextColor(Ui.MUTED);
        ownerR.setGravity(Gravity.END|Gravity.TOP);
        foot.addView(ownerL,new LinearLayout.LayoutParams(0,-2,1));
        foot.addView(ownerR,new LinearLayout.LayoutParams(0,-2,1));
        add(foot);
    }catch(Exception e){error(e);}
 }
 private Map<String,Double> sumMap(String sql,String[] args)throws Exception{Map<String,Double> m=new HashMap<>();for(JSONObject r:db.rows(sql,args))m.put(r.optString("k"),r.optDouble("v",0));return m;}
 private static double nz(Double x){return x==null?0:x;}
 private Map<String,Double> closingMap(String d)throws Exception{
    Map<String,Double> rec=sumMap("SELECT rm_code k,SUM(qty) v FROM receives WHERE date<=? GROUP BY rm_code",new String[]{d});
    Map<String,Double> use=sumMap("SELECT rm_code k,SUM(consumption) v FROM mixing2_batch_log WHERE date<=? GROUP BY rm_code",new String[]{d});
    Map<String,Double> out=new HashMap<>();
    for(JSONObject rm:db.rows("SELECT code,opening FROM rms",null)){String c=rm.optString("code");out.put(c,rm.optDouble("opening",0)+nz(rec.get(c))-nz(use.get(c)));}
    return out;
 }
 private View rmStockCard(JSONObject rm,double closing,double avg,double reorder,int st){
    int tone=st==PlanLogic.SOON?Ui.WARN:Ui.BAD;
    String tag=st==PlanLogic.NEG?"BELOW ZERO":(st==PlanLogic.LOW?"REORDER":"RUNNING OUT");
    LinearLayout c=Ui.card(this,st==PlanLogic.SOON?Ui.SOFT_AMBER:Ui.SOFT_RED);
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
    top.addView(Ui.text(this,rm.optString("name"),15,Ui.INK,true),new LinearLayout.LayoutParams(0,-2,1));
    top.addView(Ui.text(this,tag,11,tone,true));
    c.addView(top);
    double dl=PlanLogic.daysLeft(closing,avg);
    String line="Stock "+fmt(closing)+" "+rm.optString("uom");
    if(dl>=0)line+="  •  about "+fmt(Math.round(dl*10)/10.0)+" days left (avg "+fmt(avg)+"/day)";
    if(reorder>0)line+="  •  reorder at "+fmt(reorder);
    c.addView(Ui.muted(this,line));
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(8));c.setLayoutParams(p);
    return c;
 }
 private String lbl(String key){String v=db.setting("lbl::"+key,"");return v==null||v.trim().isEmpty()?key:v.trim();}
 private static final String[][] LABEL_GROUPS={
   {"Screen titles","Dashboard","RM Daily Entry — Add / Edit","Input Batch — Editable Batch Count","Reports — Sheets","More","Settings","RM Master","Product Master — Dynamic Add / Edit","Mixing-2 Recipe — Dynamic RM × Product","Batch Planner — What do I need?","Wastage","Stock Count","Daily Summary","Custom Sheets — Native Dynamic Designer","Offline AI Agent — Voucher Draft"},
   {"Dashboard cards and sections","Today RM Receive","Today Mixing-2 Use","Today Batch","Today Closing Stock","Stock Watch","Expiry Watch","Mixing-2 use — last 7 days","Stock Overview","Today Receive"},
   {"PDF report headings","RM STOCK - DAILY DATE REPORT","RM STOCK - DAILY DATE REPORT - FULL MONTH","RM STOCK - FINAL MONTHLY USAGE","DAILY SUMMARY","STOCK COUNT REPORT"}};
 private String expiryOf(EditText e)throws Exception{String t=e.getText().toString().trim();if(t.isEmpty())return "";if(!PlanLogic.validDate(t))throw new Exception("Expiry date is not valid");return t;}
 private String saveVoucherPhoto(){try{if(photoFile==null||!photoFile.exists())return "";File dir=new File(getFilesDir(),"vouchers");dir.mkdirs();File out=new File(dir,"v_"+System.currentTimeMillis()+".jpg");try(InputStream in=new FileInputStream(photoFile);OutputStream o=new FileOutputStream(out)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);}return out.getAbsolutePath();}catch(Exception e){return "";}}
 private void viewPhoto(String path){try{File f=new File(path);if(!f.exists())throw new Exception("Photo file not found");Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(FileProvider.getUriForFile(this,"com.rmstock.nativeapp.fileprovider",f),"image/jpeg");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Exception e){error(e);}}
 private void confirmDo(String title,String msg,Runnable action){Guard.run(this,db,title,true,()->new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setNegativeButton("Cancel",null).setPositiveButton("Delete",(dd,ww)->action.run()).show());}

 private void showMore(){
    clear("More");
    add(Ui.muted(this,"Modules"));

    // —— Daily PM featured (full width, special colors) ——
    LinearLayout pmHero=new LinearLayout(this);
    pmHero.setOrientation(LinearLayout.HORIZONTAL);
    pmHero.setGravity(Gravity.CENTER_VERTICAL);
    pmHero.setPadding(dp(16),dp(16),dp(16),dp(16));
    // teal / navy authentic highlight (light, not dark)
    pmHero.setBackground(Ui.shape(this,Color.rgb(232,245,253),Color.rgb(20,55,95),16));
    LinearLayout pmIcon=new LinearLayout(this);
    pmIcon.setGravity(Gravity.CENTER);
    pmIcon.setPadding(dp(12),dp(12),dp(12),dp(12));
    pmIcon.setBackground(Ui.shape(this,Color.rgb(20,55,95),Color.rgb(20,55,95),14));
    TextView pmEmoji=Ui.text(this,"📦",18,Color.WHITE,true);
    pmIcon.addView(pmEmoji);
    pmHero.addView(pmIcon);
    LinearLayout pmTxt=new LinearLayout(this);
    pmTxt.setOrientation(LinearLayout.VERTICAL);
    pmTxt.setPadding(dp(12),0,dp(8),0);
    TextView pmTitle=Ui.text(this,"Daily PM Report",17,Color.rgb(20,55,95),true);
    TextView pmSub=Ui.text(this,"Packaging · Production · FG Daily · Special module",13,Color.rgb(60,90,120),false);
    pmTxt.addView(pmTitle);pmTxt.addView(pmSub);
    pmHero.addView(pmTxt,new LinearLayout.LayoutParams(0,-2,1));
    TextView pmArrow=Ui.text(this,"›",22,Color.rgb(20,55,95),true);
    pmHero.addView(pmArrow);
    pmHero.setOnClickListener(v->openDailyPm());
    LinearLayout.LayoutParams heroLp=new LinearLayout.LayoutParams(-1,-2);
    heroLp.setMargins(0,dp(4),0,dp(12));
    content.addView(pmHero,heroLp);

    // —— Other modules 2-column light grid ——
    String[][] items={
        {"Wastage","Spoilage and loss","waste",String.valueOf(Color.rgb(255,149,0)),"🗑️"},
        {"Stock Count","Physical vs book","count",String.valueOf(Color.rgb(52,199,89)),"📋"},
        {"Daily Summary","PDF for owner","summary",String.valueOf(Color.rgb(175,82,222)),"📄"},
        {"Batch Planner","What do I need?","planner",String.valueOf(Color.rgb(90,200,250)),"🥣"},
        {"Master — RM","Raw materials","rm",String.valueOf(Color.rgb(0,122,255)),"🌾"},
        {"Master — Products","Add / edit products","prod",String.valueOf(Color.rgb(88,86,214)),"🍞"},
        {"Recipe","RM per batch","recipe",String.valueOf(Color.rgb(255,45,85)),"👨‍🍳"},
        {"Custom sheets","Your formulas","custom",String.valueOf(Color.rgb(100,210,180)),"📑"},
        {"Yield %","Formulas + history","yield",String.valueOf(Color.rgb(255,179,64)),"📈"},
        {"Process Loss %","Loss vs standard","ploss",String.valueOf(Color.rgb(255,59,48)),"📉"},
        {"Owner pack","Stock + yield page","owner",String.valueOf(Color.rgb(50,173,230)),"📊"},
        {"Audit log","Who changed what","audit",String.valueOf(Color.rgb(142,142,147)),"🧾"},
        {"Assistant","Commands / boss","fix",String.valueOf(Color.rgb(64,156,255)),"🤖"},
        {"Scan voucher","AI OCR draft","ai",String.valueOf(Color.rgb(191,90,242)),"📷"},
        {"Settings","Roles, lock, backup","settings",String.valueOf(Color.rgb(84,110,122)),"⚙️"}
    };

    LinearLayout grid=null;
    for(int i=0;i<items.length;i++){
        if(i%2==0){
            grid=new LinearLayout(this);
            grid.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams glp=new LinearLayout.LayoutParams(-1,-2);
            glp.setMargins(0,0,0,dp(10));
            content.addView(grid,glp);
        }
        final String[] it=items[i];
        final String k=it[2];
        int accent=Integer.parseInt(it[3]);
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(14),dp(14),dp(14));
        card.setBackground(Ui.shape(this,Ui.CARD,Ui.LINE,16));
        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout ic=new LinearLayout(this);
        ic.setGravity(Gravity.CENTER);
        ic.setPadding(dp(10),dp(10),dp(10),dp(10));
        ic.setBackground(Ui.shape(this,accent,accent,12));
        String icon=it.length>4?it[4]:it[0].substring(0,1);
        TextView iconTv=Ui.text(this,icon,18,Color.WHITE,true);
        iconTv.setGravity(Gravity.CENTER);
        ic.addView(iconTv);
        top.addView(ic);
        TextView arrow=Ui.text(this,"›",18,Ui.MUTED,true);
        LinearLayout.LayoutParams aLp=new LinearLayout.LayoutParams(-2,-2);
        aLp.weight=1;
        top.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        top.addView(arrow);
        card.addView(top);
        card.addView(Ui.text(this,it[0],15,Ui.NAVY_TEXT,true));
        TextView sub=Ui.text(this,it[1],12,Ui.MUTED,false);
        sub.setPadding(0,dp(4),0,0);
        card.addView(sub);
        card.setOnClickListener(v->{
            if(k.equals("rm"))gateOpen("Raw material master",()->showRmMaster());
            else if(k.equals("prod"))gateOpen("Product master",()->showProductMaster());
            else if(k.equals("recipe"))gateOpen("Recipe",()->showRecipe());
            else if(k.equals("settings"))gateOpen("Settings",()->showSettings());
            else if(k.equals("waste"))showWastage();
            else if(k.equals("count"))showCount();
            else if(k.equals("summary"))showSummary();
            else if(k.equals("planner"))showRmPlanner();
            else if(k.equals("custom"))showCustom();
            else if(k.equals("yield"))showYieldSheet();
            else if(k.equals("ploss"))showProcessLossSheet();
            else if(k.equals("owner"))showOwnerPack();
            else if(k.equals("audit"))showAuditLog();
            else if(k.equals("ai"))showAI();
            else if(k.equals("fix"))showAssistant();
        });
        LinearLayout.LayoutParams cLp=new LinearLayout.LayoutParams(0,-2,1);
        if(i%2==0) cLp.setMargins(0,0,dp(5),0);
        else cLp.setMargins(dp(5),0,0,0);
        grid.addView(card,cLp);
    }
 }


 private void showWastage(){
    clear("Wastage");
    try{
        add(Ui.muted(this,"Spoilage, spills and loss. Wastage is taken out of stock exactly like normal use, so Closing Stock and every report include it."));
        final EditText d=dateField("Date");d.setText(today());
        final Spinner rm=new Spinner(this);final ArrayList<String> a=new ArrayList<>();
        for(JSONObject x:db.rows("SELECT code||' — '||name AS label FROM rms ORDER BY CAST(code AS INTEGER)",null))a.add(x.getString("label"));
        rm.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,a));
        final EditText q=edit("Quantity lost");q.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText note=edit("Reason (optional)");
        add(d);add(rm);add(q);add(note);
        Button save=btn("Save wastage");
        save.setOnClickListener(v->{try{
            String label=String.valueOf(rm.getSelectedItem());if(label.equals("null"))throw new Exception("RM required");
            double qty=num(q.getText().toString());if(qty<=0)throw new Exception("Quantity must be more than zero");
            String code=label.split(" — ",2)[0].trim();
            JSONObject m=db.one("SELECT COALESCE(MIN(batch_id),0) v FROM mixing2_batch_log WHERE batch_id<0",null);long nid=(m==null?0:m.optLong("v"))-1;
            ContentValues z=new ContentValues();z.put("batch_id",nid);z.put("date",ymd(d));z.put("product_code",0);z.put("shift",0);z.put("rm_code",code);z.put("recipe_qty",0);z.put("batch_count",0);z.put("consumption",qty);z.put("created_at","WASTE|"+note.getText().toString().trim());
            db.insert("mixing2_batch_log",z);
            try{
                if("client".equals(db.setting("sync_mode","off"))){
                    org.json.JSONObject payload=new org.json.JSONObject();
                    payload.put("date",ymd(d));
                    payload.put("rm_code",code);
                    payload.put("qty",qty);
                    payload.put("note",note.getText().toString().trim());
                    payload.put("kind_waste",true);
                    SyncHub.get(this).enqueue("wastage", payload);
                }
            }catch(Exception ignored){}
            toast("Wastage saved ✔");showWastage();
        }catch(Exception e){error(e);}});
        add(save);
        String month=today().substring(0,7);
        Map<String,Double> tot=sumMap("SELECT rm_code k,SUM(consumption) v FROM mixing2_batch_log WHERE batch_id<0 AND date LIKE ? GROUP BY rm_code",new String[]{month+"-%"});
        add(title("This month ("+month+")"));
        if(tot.isEmpty())add(Ui.muted(this,"No wastage recorded this month."));
        else{LinearLayout c=Ui.card(this,Ui.CARD);double mx=0;for(double vv:tot.values())mx=Math.max(mx,vv);
            for(JSONObject rr:db.rows("SELECT code,name,uom FROM rms ORDER BY CAST(code AS INTEGER)",null)){Double t=tot.get(rr.optString("code"));if(t!=null)c.addView(Ui.barRow(this,rr.optString("code")+"  "+rr.optString("name"),fmt(t)+" "+rr.optString("uom"),mx<=0?0:t/mx,Ui.BAD));}
            addCard(c);}
        add(title("Recent wastage"));
        for(final JSONObject x:db.rows("SELECT l.id,l.date,l.rm_code,l.consumption,l.created_at,m.name,m.uom FROM mixing2_batch_log l LEFT JOIN rms m ON m.code=l.rm_code WHERE l.batch_id<0 ORDER BY l.date DESC,l.id DESC LIMIT 60",null)){
            LinearLayout row=Ui.card(this,Ui.CARD);
            String why=x.optString("created_at");why=why.startsWith("WASTE|")?why.substring(6):"";
            row.addView(Ui.text(this,x.optString("date")+"  •  "+x.optString("name")+"  •  "+fmt(x.optDouble("consumption"))+" "+x.optString("uom"),14,Ui.INK,true));
            if(!why.isEmpty())row.addView(Ui.muted(this,why));
            Button dl=Ui.secondary(this,"Delete");dl.setTextColor(Ui.BAD);
            dl.setOnClickListener(v->confirmDo("Delete wastage?",x.optString("date")+"  "+x.optString("name")+"  "+fmt(x.optDouble("consumption")),()->{db.delete("mixing2_batch_log","id=?",new String[]{x.optString("id")});toast("Deleted");showWastage();}));
            row.addView(dl);addCard(row);
        }
    }catch(Exception e){error(e);}
 }

 private void showCount(){
    clear("Stock Count");
    try{
        add(Ui.muted(this,"Flow: Count → Variance → Approve → Stock adjust. Pending rows need Supervisor/Admin approve."));
        final EditText d=dateField("Count date");d.setText(today());add(d);
        final LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        final List<EditText> ins=new ArrayList<>();final List<String> codes=new ArrayList<>();
        final Map<String,Double> sys=closingMap(today());
        for(JSONObject r:db.rows("SELECT code,name,uom FROM rms ORDER BY CAST(code AS INTEGER)",null)){
            LinearLayout row=row();
            LinearLayout nm=new LinearLayout(this);nm.setOrientation(LinearLayout.VERTICAL);
            nm.addView(Ui.text(this,r.optString("code")+"  "+r.optString("name"),14,Ui.INK,false));
            nm.addView(Ui.muted(this,"Book "+fmt(nz(sys.get(r.optString("code"))))+" "+r.optString("uom")));
            row.addView(nm,new LinearLayout.LayoutParams(0,-2,1));
            EditText e=Ui.field(this,"counted");e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);e.setGravity(Gravity.CENTER);
            row.addView(e,new LinearLayout.LayoutParams(dp(110),dp(48)));ins.add(e);codes.add(r.optString("code"));list.addView(row);
        }
        add(list);
        Button save=btn("1. Save as PENDING count");
        save.setOnClickListener(v->{try{
            if(!db.canEdit()){toast("No permission");return;}
            String day=ymd(d);if(db.monthLocked(day)){toast("Month locked");return;}
            Map<String,Double> book=closingMap(day);int n=0;
            for(int i=0;i<ins.size();i++){String t=ins.get(i).getText().toString().trim();if(t.isEmpty())continue;
                double counted=num(t);String code=codes.get(i);double system=nz(book.get(code));double var=counted-system;
                db.delete("counts","date=? AND rm_code=? AND (status IS NULL OR status!='approved')",new String[]{day,code});
                ContentValues z=new ContentValues();z.put("date",day);z.put("rm_code",code);z.put("counted",counted);z.put("system",system);z.put("variance",var);z.put("status","pending");z.put("note","");
                db.insert("counts",z);n++;}
            if(n==0)throw new Exception("Type at least one counted quantity");
            db.audit(user(),role(),"count_pending",day+" x"+n);
            toast(n+" pending — review variance below");showCount();
        }catch(Exception e){error(e);}});
        add(save);

        add(title("Pending (variance)"));
        List<JSONObject> pending=db.rows("SELECT c.id,c.date,c.rm_code,c.counted,c.system,IFNULL(c.variance,c.counted-c.system) variance,IFNULL(c.status,'pending') status,m.name,m.uom FROM counts c LEFT JOIN rms m ON m.code=c.rm_code WHERE IFNULL(c.status,'pending')='pending' ORDER BY c.date DESC,CAST(c.rm_code AS INTEGER) LIMIT 60",null);
        if(pending.isEmpty())add(Ui.muted(this,"No pending counts"));
        for(final JSONObject c:pending){
            long id=c.optLong("id");
            double var=c.optDouble("variance");
            LinearLayout card=Ui.card(this,Math.abs(var)<0.0005?Ui.CARD:(var>0?Ui.SOFT_GREEN:Ui.SOFT_RED));
            card.addView(Ui.text(this,c.optString("date")+" · "+c.optString("rm_code")+" "+c.optString("name"),14,Ui.NAVY_TEXT,true));
            card.addView(Ui.muted(this,"Book "+fmt(c.optDouble("system"))+" → Counted "+fmt(c.optDouble("counted"))+" "+c.optString("uom")+" · Variance "+fmt(var)));
            LinearLayout actions=row();
            Button ap=Ui.primary(this,"Approve & adjust stock");
            ap.setOnClickListener(v->{
                if(!db.canAdmin()&&!"Supervisor".equalsIgnoreCase(role())){toast("Supervisor/Admin only");return;}
                Guard.run(this,db,"Approve stock count",true,()->{
                    try{
                        approveCountRow(c);
                        showCount();
                    }catch(Exception e){error(e);}
                });
            });
            Button rej=Ui.secondary(this,"Reject");
            rej.setOnClickListener(v->{
                try{
                    ContentValues z=new ContentValues();z.put("status","rejected");
                    db.update("counts",z,"id=?",new String[]{String.valueOf(id)});
                    db.audit(user(),role(),"count_reject",c.optString("rm_code"));
                    showCount();
                }catch(Exception e){error(e);}
            });
            actions.addView(ap,Ui.lp(this,0,dp(44),2,0,0,6,0));
            actions.addView(rej,Ui.lp(this,0,dp(44),1,6,0,0,0));
            card.addView(actions);
            add(card);
        }

        add(title("Approved / history"));
        List<JSONObject> hist=db.rows("SELECT c.id,c.date,c.rm_code,c.counted,c.system,IFNULL(c.variance,0) variance,IFNULL(c.status,'pending') status,c.approved_by,m.name,m.uom FROM counts c LEFT JOIN rms m ON m.code=c.rm_code WHERE IFNULL(c.status,'pending')!='pending' ORDER BY c.id DESC LIMIT 40",null);
        if(hist.isEmpty())add(Ui.muted(this,"None yet"));
        for(JSONObject c:hist){
            LinearLayout card=Ui.card(this,Ui.CARD);
            card.addView(Ui.text(this,c.optString("status").toUpperCase(Locale.US)+" · "+c.optString("date")+" · "+c.optString("rm_code")+" "+c.optString("name"),13,Ui.INK,true));
            card.addView(Ui.muted(this,"Var "+fmt(c.optDouble("variance"))+" · by "+c.optString("approved_by","—")));
            add(card);
        }
    }catch(Exception e){error(e);}
}
 private void approveCountRow(JSONObject c)throws Exception{
    String day=c.optString("date");String code=c.optString("rm_code");
    if(db.monthLocked(day))throw new Exception("Month locked");
    double counted=c.optDouble("counted");double system=c.optDouble("system");
    double var=c.has("variance")?c.optDouble("variance"):(counted-system);
    // Adjust book stock via receive line (source=count_adjust)
    if(Math.abs(var)>0.0000001){
        ContentValues r=new ContentValues();
        r.put("date",day);r.put("rm_code",code);r.put("qty",var);
        r.put("note","Stock count adjust");r.put("voucher_no","COUNT");r.put("source","count_adjust");
        r.put("lot","");r.put("expiry","");r.put("photo","");
        db.insert("receives",r);
    }
    ContentValues z=new ContentValues();
    z.put("status","approved");z.put("variance",var);
    z.put("approved_by",user());
    z.put("approved_at",new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date()));
    db.update("counts",z,"id=?",new String[]{String.valueOf(c.optLong("id"))});
    db.audit(user(),role(),"count_approve",code+" var="+fmt(var));
    toast("Approved · stock adjusted "+fmt(var));
}
private void showSummary(){
    clear("Daily Summary");
    add(Ui.muted(this,"One page for the owner: what came in, what was used, today’s batches and the alerts. Use Share to send it, for example on WhatsApp."));
    final EditText d=dateField("Date");d.setText(today());add(d);
    Button go=btn("Create summary PDF");go.setOnClickListener(v->{try{reportSummary(ymd(d));}catch(Exception e){error(e);}});add(go);
 }
 private void reportSummary(String day){
    try{
        ArrayList<String[]> rows=new ArrayList<>();
        for(JSONObject r:db.rows("SELECT r.rm_code,r.qty,r.voucher_no,r.lot,m.name,m.uom FROM receives r LEFT JOIN rms m ON m.code=r.rm_code WHERE r.date=? ORDER BY CAST(r.rm_code AS INTEGER),r.id",new String[]{day}))
            rows.add(new String[]{"RECEIVE",r.optString("rm_code")+"  "+r.optString("name"),fmt(r.optDouble("qty"))+" "+r.optString("uom"),(r.optString("voucher_no").isEmpty()?"":"Voucher "+r.optString("voucher_no")+"  ")+(r.optString("lot").isEmpty()?"":"Lot "+r.optString("lot"))});
        for(JSONObject r:db.rows("SELECT l.rm_code,SUM(l.consumption) q,MIN(l.batch_id) b,m.name,m.uom FROM mixing2_batch_log l LEFT JOIN rms m ON m.code=l.rm_code WHERE l.date=? GROUP BY l.rm_code,m.name,m.uom ORDER BY CAST(l.rm_code AS INTEGER)",new String[]{day}))
            rows.add(new String[]{"USE",r.optString("rm_code")+"  "+r.optString("name"),fmt(r.optDouble("q"))+" "+r.optString("uom"),r.optInt("b")<0?"includes wastage":""});
        for(JSONObject r:db.rows("SELECT b.shift,b.qty,p.name FROM batches b LEFT JOIN products p ON p.code=b.product_code WHERE b.date=? ORDER BY b.product_code,b.shift",new String[]{day}))
            rows.add(new String[]{"BATCH",r.optString("name"),batchCountText(r.optDouble("qty"))+" batch","Shift "+r.optString("shift")});
        Map<String,Double> close=closingMap(day);
        Map<String,Double> use7=sumMap("SELECT rm_code k,SUM(consumption) v FROM mixing2_batch_log WHERE date>=? AND date<? GROUP BY rm_code",new String[]{PlanLogic.shiftDate(day,-7),day});
        for(JSONObject r:db.rows("SELECT code,name,uom,reorder FROM rms ORDER BY CAST(code AS INTEGER)",null)){
            String code=r.optString("code");double cl=nz(close.get(code)),avg=nz(use7.get(code))/7.0;int st=PlanLogic.status(cl,r.optDouble("reorder",0),avg);
            if(st==PlanLogic.OK)continue;
            double dl=PlanLogic.daysLeft(cl,avg);
            rows.add(new String[]{"ALERT",code+"  "+r.optString("name"),fmt(cl)+" "+r.optString("uom"),(st==PlanLogic.NEG?"Below zero":(st==PlanLogic.LOW?"At reorder level":"Running out"))+(dl>=0?" ("+fmt(Math.round(dl*10)/10.0)+" days left)":"")});
        }
        if(rows.isEmpty())rows.add(new String[]{"","Nothing recorded for this date","",""});
        createLandscapeTablePdf("DAILY SUMMARY","Date: "+day,new String[]{"Section","Item","Quantity","Note"},rows,"Daily-Summary-"+day);
    }catch(Exception e){error(e);}
 }

 
 
 private void dismissChatOverlay(){
    if(chatOverlay!=null && root!=null){
        try{ root.removeView(chatOverlay); }catch(Exception ignored){}
        chatOverlay=null;
    }
 }

private void showMessenger(){
    clear("Team Messenger");
    if(mainScroll!=null) mainScroll.setVisibility(View.GONE);
    if(navRow!=null) navRow.setVisibility(View.GONE);

    final String me;
    {
        String who=db.setting("duty_name","");
        if(who.isEmpty()) who=db.setting("user_name", role());
        if(who==null||who.isEmpty()) who="User";
        me=who;
    }

    chatOverlay=new LinearLayout(this);
    chatOverlay.setOrientation(LinearLayout.VERTICAL);
    chatOverlay.setBackgroundColor(Color.rgb(236,229,221));
    chatOverlay.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1));

    // Header
    LinearLayout top=new LinearLayout(this);
    top.setOrientation(LinearLayout.HORIZONTAL);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(dp(10),dp(12),dp(10),dp(12));
    top.setBackgroundColor(Color.rgb(7,94,84));
    TextView back=Ui.text(this,"←",22,Color.WHITE,true);
    back.setPadding(dp(6),0,dp(12),0);
    back.setOnClickListener(v->showDashboard());
    top.addView(back);
    TextView av=Ui.text(this,"👥",18,Color.WHITE,true);
    top.addView(av);
    LinearLayout titles=new LinearLayout(this);
    titles.setOrientation(LinearLayout.VERTICAL);
    titles.setPadding(dp(10),0,0,0);
    titles.addView(Ui.text(this,"Team Chat",17,Color.WHITE,true));
    titles.addView(Ui.text(this,"Live · "+me,12,Color.rgb(209,237,232),false));
    top.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
    // Call buttons
    TextView voice=Ui.text(this,"📞",20,Color.WHITE,true);
    voice.setPadding(dp(10),dp(4),dp(8),dp(4));
    voice.setOnClickListener(v->startTeamCall(false));
    top.addView(voice);
    TextView video=Ui.text(this,"📹",20,Color.WHITE,true);
    video.setPadding(dp(8),dp(4),dp(10),dp(4));
    video.setOnClickListener(v->startTeamCall(true));
    top.addView(video);
    TextView live=Ui.text(this,"●",14,Color.rgb(180,255,200),true);
    live.setPadding(dp(4),0,dp(6),0);
    top.addView(live);
    chatOverlay.addView(top,new LinearLayout.LayoutParams(-1,-2));

    final ScrollView chatScroll=new ScrollView(this);
    chatScroll.setFillViewport(true);
    final LinearLayout list=new LinearLayout(this);
    list.setOrientation(LinearLayout.VERTICAL);
    list.setPadding(dp(10),dp(12),dp(10),dp(12));
    chatScroll.addView(list);
    chatOverlay.addView(chatScroll,new LinearLayout.LayoutParams(-1,0,1));

    final Runnable paint=()->{
        list.removeAllViews();
        try{
            org.json.JSONArray arr=SyncHub.get(this).loadChatJson(200);
            if(arr.length()==0){
                TextView empty=Ui.text(this,"No messages yet — say hello 👋",14,Color.rgb(90,90,90),false);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0,dp(40),0,dp(40));
                list.addView(empty);
            }
            for(int i=0;i<arr.length();i++){
                org.json.JSONObject m=arr.getJSONObject(i);
                String sender=m.optString("sender","?");
                String body=m.optString("body","");
                String ts=m.optString("ts","");
                if(ts.length()>=16) ts=ts.substring(11,16);
                boolean mine=sender.equalsIgnoreCase(me);

                LinearLayout row=new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dp(2),dp(4),dp(2),dp(4));
                row.setGravity(mine?Gravity.END:Gravity.START);

                LinearLayout bubble=new LinearLayout(this);
                bubble.setOrientation(LinearLayout.VERTICAL);
                bubble.setPadding(dp(12),dp(8),dp(12),dp(6));
                int fill=mine?Color.rgb(217,253,211):Color.WHITE;
                bubble.setBackground(Ui.shape(this,fill,Color.rgb(220,220,220),16));
                LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-2,-2);
                blp.setMargins(mine?dp(56):0,0,mine?0:dp(56),0);
                if(!mine) bubble.addView(Ui.text(this,sender,12,Color.rgb(6,128,105),true));

                if(body.startsWith("[IMG]")){
                    String path=body.substring(5);
                    try{
                        android.graphics.Bitmap bm=android.graphics.BitmapFactory.decodeFile(path);
                        if(bm!=null){
                            ImageView iv=new ImageView(this);
                            iv.setAdjustViewBounds(true);
                            iv.setMaxWidth(dp(220));
                            iv.setMaxHeight(dp(280));
                            iv.setImageBitmap(bm);
                            iv.setOnClickListener(vv->{
                                try{
                                    Intent it=new Intent(Intent.ACTION_VIEW);
                                    it.setDataAndType(FileProvider.getUriForFile(this,"com.rmstock.nativeapp.fileprovider",new File(path)),"image/*");
                                    it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    startActivity(it);
                                }catch(Exception ex){toast("Cannot open image");}
                            });
                            bubble.addView(iv);
                        }else bubble.addView(Ui.text(this,"🖼 Image",14,Color.rgb(30,30,30),false));
                    }catch(Exception ex){ bubble.addView(Ui.text(this,"🖼 Image",14,Color.rgb(30,30,30),false)); }
                }else if(body.startsWith("[FILE]")){
                    String path=body.substring(6);
                    String name=new File(path).getName();
                    TextView ft=Ui.text(this,"📎 "+name,14,Color.rgb(20,100,180),true);
                    ft.setOnClickListener(vv->{
                        try{
                            Intent it=new Intent(Intent.ACTION_VIEW);
                            Uri u=FileProvider.getUriForFile(this,"com.rmstock.nativeapp.fileprovider",new File(path));
                            it.setDataAndType(u,"*/*");
                            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(Intent.createChooser(it,"Open file"));
                        }catch(Exception ex){toast("Cannot open file");}
                    });
                    bubble.addView(ft);
                }else if(body.startsWith("[CALL]")){
                    bubble.addView(Ui.text(this,body.substring(6),14,Color.rgb(30,30,30),true));
                }else{
                    TextView bodyTv=Ui.text(this,body,15,Color.rgb(30,30,30),false);
                    bodyTv.setMaxWidth(dp(260));
                    bubble.addView(bodyTv);
                }
                TextView timeTv=Ui.text(this,ts+(mine?" ✓":""),11,Color.rgb(120,120,120),false);
                timeTv.setGravity(Gravity.END);
                bubble.addView(timeTv);
                row.addView(bubble,blp);
                list.addView(row);
            }
            chatScroll.post(()->chatScroll.fullScroll(View.FOCUS_DOWN));
        }catch(Exception e){ list.addView(Ui.muted(this,String.valueOf(e.getMessage()))); }
    };
    paint.run();
    SyncHub.get(this).pullChat(paint);

    final Runnable chatPoll=new Runnable(){
        @Override public void run(){
            if(!"Team Messenger".equals(currentScreen)) return;
            final Runnable self=this;
            if("client".equals(db.setting("sync_mode","off"))){
                SyncHub.get(MainActivity.this).pullChat(()->{
                    paint.run();
                    if("Team Messenger".equals(currentScreen)) main.postDelayed(self, 800);
                });
            }else{
                paint.run();
                if("Team Messenger".equals(currentScreen)) main.postDelayed(self, 800);
            }
        }
    };
    dashboardRunnables.add(chatPoll);
    main.postDelayed(chatPoll, 800);

    // Composer
    LinearLayout composer=new LinearLayout(this);
    composer.setOrientation(LinearLayout.HORIZONTAL);
    composer.setGravity(Gravity.CENTER_VERTICAL);
    composer.setPadding(dp(8),dp(8),dp(8),dp(10));
    composer.setBackgroundColor(Color.rgb(240,242,245));
    TextView attach=Ui.text(this,"➕",22,Color.rgb(7,94,84),true);
    attach.setPadding(dp(6),dp(6),dp(8),dp(6));
    attach.setOnClickListener(v->{
        String[] opts={"Photo / Gallery","File","Camera"};
        new AlertDialog.Builder(this).setTitle("Attach")
            .setItems(opts,(d,w)->{
                try{
                    if(w==0){
                        Intent i=new Intent(Intent.ACTION_GET_CONTENT);
                        i.setType("image/*");
                        startActivityForResult(Intent.createChooser(i,"Photo"),CHAT_IMG);
                    }else if(w==1){
                        Intent i=new Intent(Intent.ACTION_GET_CONTENT);
                        i.setType("*/*");
                        startActivityForResult(Intent.createChooser(i,"File"),CHAT_FILE);
                    }else{
                        startCameraForChat();
                    }
                }catch(Exception e){toast(e.getMessage());}
            }).show();
    });
    composer.addView(attach);
    final EditText box=new EditText(this);
    box.setHint("Message");
    box.setMinHeight(dp(46));
    box.setPadding(dp(14),dp(10),dp(14),dp(10));
    box.setBackground(Ui.shape(this,Color.WHITE,Color.rgb(210,210,210),24));
    box.setTextSize(15);
    Button send=new Button(this);
    send.setText("➤");
    send.setTextColor(Color.WHITE);
    send.setAllCaps(false);
    send.setBackground(Ui.shape(this,Color.rgb(7,94,84),0,24));
    send.setMinWidth(dp(48));
    send.setMinHeight(dp(48));
    send.setOnClickListener(v->{
        String t=box.getText().toString().trim();
        if(t.isEmpty()) return;
        SyncHub.get(this).sendChat(me, t, ()->{ box.setText(""); paint.run(); });
    });
    composer.addView(box,new LinearLayout.LayoutParams(0,dp(48),1));
    LinearLayout.LayoutParams sLp=new LinearLayout.LayoutParams(dp(52),dp(52));
    sLp.setMargins(dp(8),0,0,0);
    composer.addView(send,sLp);
    chatOverlay.addView(composer,new LinearLayout.LayoutParams(-1,-2));

    // Insert overlay after header (index 1) if possible
    try{
        if(root.getChildCount()>=1) root.addView(chatOverlay, 1);
        else root.addView(chatOverlay);
    }catch(Exception e){ root.addView(chatOverlay); }
 }

 private void startTeamCall(boolean video){
    String me=db.setting("duty_name","");
    if(me.isEmpty()) me=db.setting("user_name", role());
    if(me==null||me.isEmpty()) me="User";
    String kind=video?"📹 Video call":"📞 Voice call";
    SyncHub.get(this).sendChat(me, "[CALL]"+kind+" from "+me, null);
    try{
        Intent i=new Intent(this, CallActivity.class);
        i.putExtra(CallActivity.EXTRA_VIDEO, video);
        i.putExtra(CallActivity.EXTRA_OUTGOING, true);
        i.putExtra(CallActivity.EXTRA_PEER, "Team");
        startActivity(i);
    }catch(Exception e){
        toast("Call open failed: "+e.getMessage());
    }
}


 private void startCameraForChat(){
    try{
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},CAM);
            toast("Allow camera then try again");
            return;
        }
        File dir=new File(getCacheDir(),"chat"); dir.mkdirs();
        File f=File.createTempFile("chat_",".jpg",dir);
        photoFile=f;
        photoUri=FileProvider.getUriForFile(this,"com.rmstock.nativeapp.fileprovider",f);
        Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT,photoUri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(i,CHAT_IMG);
    }catch(Exception e){error(e);}
 }

 private void sendChatAttachment(Uri uri, boolean image){
    try{
        String me=db.setting("duty_name","");
        if(me.isEmpty()) me=db.setting("user_name", role());
        if(me==null||me.isEmpty()) me="User";
        File dir=new File(getFilesDir(),"chat_media"); dir.mkdirs();
        String name=image?("img_"+System.currentTimeMillis()+".jpg"):("file_"+System.currentTimeMillis());
        try{
            String path=uri.getLastPathSegment();
            if(path!=null && path.contains(".")) name=System.currentTimeMillis()+"_"+new File(path).getName();
        }catch(Exception ignored){}
        File out=new File(dir, name);
        InputStream in=getContentResolver().openInputStream(uri);
        if(in==null && photoFile!=null && photoFile.exists()){
            // camera file
            out=photoFile;
        }else if(in!=null){
            OutputStream os=new FileOutputStream(out);
            byte[] buf=new byte[8192]; int n;
            while((n=in.read(buf))>0) os.write(buf,0,n);
            in.close(); os.close();
        }else{ toast("Cannot read file"); return; }
        String body=(image?"[IMG]":"[FILE]")+out.getAbsolutePath();
        SyncHub.get(this).sendChat(me, body, ()->{ if("Team Messenger".equals(currentScreen)) showMessenger(); });
        toast(image?"Photo sent":"File sent");
    }catch(Exception e){error(e);}
 }




private void showSettings(){
    clear("Settings");
    try{
        LinearLayout c0=Ui.card(this,Ui.CARD);
        c0.addView(Ui.text(this,"Role & user (this device)",15,Ui.NAVY_TEXT,true));
        c0.addView(Ui.muted(this,"Admin = full · Supervisor = edit · Operator = view mostly. Offline only."));
        final EditText uEt=edit("User name");uEt.setText(db.currentUser());
        final Spinner roleSp=new Spinner(this);
        String[] roles=new String[]{"Admin","Supervisor","Operator"};
        roleSp.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,roles));
        String cr=db.currentRole();
        for(int i=0;i<roles.length;i++)if(roles[i].equalsIgnoreCase(cr)||(cr.contains("Admin")&&i==0))roleSp.setSelection(i);
        c0.addView(Ui.muted(this,"User"));c0.addView(uEt);
        c0.addView(Ui.muted(this,"Role"));c0.addView(roleSp);
        Button saveRole=btn("Save user / role");
        saveRole.setOnClickListener(v->{
            if(!db.canAdmin()){toast("Admin only — current role cannot change roles");return;}
            Runnable save=()->{
                db.setSetting("current_user",uEt.getText().toString().trim());
                db.setSetting("role",roles[roleSp.getSelectedItemPosition()]);
                db.audit(user(),role(),"set_role",roles[roleSp.getSelectedItemPosition()]);
                toast("Saved ✔");
            };
            if(Guard.enabled(db)) Guard.run(this,db,"Change role",true,save);
            else save.run();
        });
        c0.addView(saveRole);addCard(c0);

        LinearLayout cLock=Ui.card(this,Ui.CARD);
        cLock.addView(Ui.text(this,"Month close lock",15,Ui.NAVY_TEXT,true));
        cLock.addView(Ui.muted(this,"Finalized months block edits (Supervisor/Admin can unlock)."));
        final EditText ymEt=edit("YYYY-MM");ymEt.setText(today().substring(0,7));
        cLock.addView(ymEt);
        Button lockB=Ui.secondary(this,"Finalize / lock month");
        lockB.setOnClickListener(v->{
            if(!db.canAdmin()){toast("Admin only");return;}
            String ym=ymEt.getText().toString().trim();
            if(ym.length()<7){toast("Use YYYY-MM");return;}
            Guard.run(this,db,"Finalize "+ym,true,()->{
                db.setMonthLocked(ym,true);
                db.audit(user(),role(),"month_lock",ym);
                toast(ym+" locked");
            });
        });
        Button unlockB=Ui.secondary(this,"Unlock month");
        unlockB.setOnClickListener(v->{
            if(!db.canAdmin()){toast("Admin only");return;}
            String ym=ymEt.getText().toString().trim();
            Guard.run(this,db,"Unlock "+ym,true,()->{
                db.setMonthLocked(ym,false);
                db.audit(user(),role(),"month_unlock",ym);
                toast(ym+" unlocked");
            });
        });
        cLock.addView(lockB);cLock.addView(unlockB);addCard(cLock);

        LinearLayout c1=Ui.card(this,Ui.CARD);
        c1.addView(Ui.text(this,"Company name",15,Ui.NAVY_TEXT,true));
        final EditText co=edit("Company name");co.setText(db.setting("company","Company Name"));c1.addView(co);
        Button saveCo=btn("Save company name");
        saveCo.setOnClickListener(v->{if(!open())return;String x=co.getText().toString().trim();if(x.isEmpty()){toast("Name required");return;}db.setSetting("company",x);if(brandView!=null)brandView.setText(x);toast("Saved ✔");});
        c1.addView(saveCo);addCard(c1);

        LinearLayout cBoss=Ui.card(this,Ui.CARD);
        cBoss.addView(Ui.text(this,"Boss contact (monthly report)",15,Ui.NAVY_TEXT,true));
        cBoss.addView(Ui.muted(this,"Use once a month when internet is on. App builds the report offline, then opens Email / WhatsApp / Share for your boss."));
        final EditText bossName=edit("Boss name"); bossName.setText(db.setting("boss_name",""));
        final EditText bossEmail=edit("Boss email"); bossEmail.setText(db.setting("boss_email",""));
        final EditText bossWa=edit("Boss WhatsApp (e.g. 8801XXXXXXXXX)"); bossWa.setText(db.setting("boss_whatsapp",""));
        cBoss.addView(Ui.muted(this,"Name"));cBoss.addView(bossName);
        cBoss.addView(Ui.muted(this,"Email"));cBoss.addView(bossEmail);
        cBoss.addView(Ui.muted(this,"WhatsApp number"));cBoss.addView(bossWa);
        Button saveBoss=btn("Save boss contact");
        saveBoss.setOnClickListener(v->{
            db.setSetting("boss_name",bossName.getText().toString().trim());
            db.setSetting("boss_email",bossEmail.getText().toString().trim());
            db.setSetting("boss_whatsapp",bossWa.getText().toString().trim().replace(" ",""));
            toast("Boss contact saved ✔");
        });
        cBoss.addView(saveBoss);addCard(cBoss);

        LinearLayout c2=Ui.card(this,Ui.CARD);
        
        LinearLayout cPm=Ui.card(this,Ui.CARD);
        cPm.addView(Ui.text(this,"Daily PM labels",15,Ui.NAVY_TEXT,true));
        cPm.addView(Ui.muted(this,"Used on Daily PM reports / PDF. Same Settings for RM + PM — no separate PM Settings tab."));
        final EditText prepBy=edit("Prepared by (PM reports)");
        final EditText pmTitle=edit("PM screen title");
        try{
            PmDb pmDb=new PmDb(this);
            prepBy.setText(pmDb.meta("preparedBy",""));
            pmTitle.setText(pmDb.meta("appTitle","Daily PM Report"));
        }catch(Exception ignored){}
        cPm.addView(Ui.muted(this,"Prepared by"));cPm.addView(prepBy);
        cPm.addView(Ui.muted(this,"PM title"));cPm.addView(pmTitle);
        Button savePm=btn("Save PM labels");
        savePm.setOnClickListener(v->{
            try{
                PmDb pmDb=new PmDb(this);
                pmDb.setMeta("preparedBy",prepBy.getText().toString().trim());
                pmDb.setMeta("appTitle",pmTitle.getText().toString().trim());
                toast("PM labels saved ✔");
            }catch(Exception e){error(e);}
        });
        cPm.addView(savePm);addCard(cPm);

c2.addView(Ui.text(this,"Tab names",15,Ui.NAVY_TEXT,true));
        c2.addView(Ui.muted(this,"Rename the five tabs at the bottom."));
        final String[] keys={"Home","Entry","Batch","Reports","More"};final EditText[] tf=new EditText[keys.length];
        for(int i=0;i<keys.length;i++){tf[i]=edit(keys[i]);tf[i].setText(tabName(keys[i]));c2.addView(tf[i]);}
        Button saveTabs=btn("Save tab names");
        saveTabs.setOnClickListener(v->{if(!open())return;for(int j=0;j<keys.length;j++){String x=tf[j].getText().toString().trim();db.setSetting("tab_"+keys[j],x.isEmpty()?keys[j]:x);}build();showSettings();toast("Tab names saved ✔");});
        Button resetTabs=Ui.secondary(this,"Reset tab names");
        resetTabs.setOnClickListener(v->{if(!open())return;for(String kk:keys)db.setSetting("tab_"+kk,kk);build();showSettings();});
        c2.addView(saveTabs);c2.addView(resetTabs);addCard(c2);

        LinearLayout c3=Ui.card(this,Ui.CARD);
        c3.addView(Ui.text(this,"Names and titles",15,Ui.NAVY_TEXT,true));
        c3.addView(Ui.muted(this,"Screen titles, dashboard card names, section names and PDF report headings. Change any of them, or reset to the original."));
        Button nb=btn("Edit names and titles");nb.setOnClickListener(v->showLabels());c3.addView(nb);addCard(c3);

        LinearLayout c4=Ui.card(this,Ui.CARD);
        c4.addView(Ui.text(this,"Lists you can add to, edit and delete",15,Ui.NAVY_TEXT,true));
        String[][] lists={{"Raw materials (code, name, unit, opening, reorder)","rm"},{"Products","prod"},{"Recipes (raw material per batch)","recipe"},{"Custom sheets","custom"}};
        for(String[] ls:lists){final String k=ls[1];Button b=Ui.secondary(this,ls[0]);b.setOnClickListener(v->{if(!open())return;if(k.equals("rm"))showRmMaster();else if(k.equals("prod"))showProductMaster();else if(k.equals("recipe"))showRecipe();else showCustom();});c4.addView(b,Ui.lp(this,-1,-2,0,0,3,0,3));}
        addCard(c4);

        LinearLayout c5=Ui.card(this,Ui.CARD);
        c5.addView(Ui.text(this,"Dashboard and alerts",15,Ui.NAVY_TEXT,true));
        c5.addView(Ui.muted(this,"Seconds between two live-card changes (2.5 to 5):"));
        final EditText gap=edit("3");gap.setText(fmt(liveGap()));gap.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);c5.addView(gap);
        c5.addView(Ui.muted(this,"Warn about lots that expire within this many days:"));
        final EditText ed=edit("14");ed.setText(db.setting("expiry_days","14"));ed.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);c5.addView(ed);
        Button saveGap=btn("Save");
        saveGap.setOnClickListener(v->{if(!open())return;double g=num(gap.getText().toString());if(g<2.5)g=2.5;if(g>5)g=5;db.setSetting("live_gap",fmt(g));gap.setText(fmt(g));int dd=(int)Math.round(num(ed.getText().toString()));if(dd<1)dd=14;db.setSetting("expiry_days",String.valueOf(dd));ed.setText(String.valueOf(dd));toast("Saved ✔");});
        c5.addView(saveGap);addCard(c5);

        LinearLayout cAlert=Ui.card(this,Ui.CARD);
        cAlert.addView(Ui.text(this,"Morning alerts / reminders",15,Ui.NAVY_TEXT,true));
        cAlert.addView(Ui.muted(this,"Daily notification: reorder level, expiry window, and if no batch was entered today. Works offline on this phone."));
        final boolean on="1".equals(db.setting("alerts_on","1"));
        Button toggle=Ui.secondary(this,"Alerts: "+(on?"ON — tap to turn off":"OFF — tap to turn on"));
        toggle.setOnClickListener(v->{
            boolean now="1".equals(db.setting("alerts_on","1"));
            db.setSetting("alerts_on",now?"0":"1");
            if(now) AlertScheduler.cancel(this); else { requestNotifPermission(); AlertScheduler.schedule(this); }
            toast(now?"Alerts off":"Alerts on");
            showSettings();
        });
        cAlert.addView(toggle);
        final EditText hourEt=edit("Hour 0-23"); hourEt.setText(db.setting("alert_hour","8"));
        hourEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        final EditText minEt=edit("Minute 0-59"); minEt.setText(db.setting("alert_minute","0"));
        minEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        cAlert.addView(Ui.muted(this,"Time (24h)"));
        LinearLayout timeRow=row();
        timeRow.addView(hourEt,new LinearLayout.LayoutParams(0,-2,1));
        timeRow.addView(minEt,new LinearLayout.LayoutParams(0,-2,1));
        cAlert.addView(timeRow);
        Button saveT=btn("Save alert time & reschedule");
        saveT.setOnClickListener(v->{
            int h=(int)Math.round(num(hourEt.getText().toString()));
            int m=(int)Math.round(num(minEt.getText().toString()));
            if(h<0||h>23)h=8; if(m<0||m>59)m=0;
            db.setSetting("alert_hour",String.valueOf(h));
            db.setSetting("alert_minute",String.valueOf(m));
            db.setSetting("alerts_on","1");
            requestNotifPermission();
            AlertScheduler.schedule(this);
            toast("Scheduled daily at "+String.format(java.util.Locale.US,"%02d:%02d",h,m));
        });
        cAlert.addView(saveT);
        Button test=Ui.secondary(this,"Test notification now");
        test.setOnClickListener(v->{
            requestNotifPermission();
            AlertScheduler.testNow(this);
            toast("Notification sent (if permission allowed)");
        });
        cAlert.addView(test);
        addCard(cAlert);

        LinearLayout c6=Ui.card(this,Ui.CARD);
        c6.addView(Ui.text(this,"Data clean-up",15,Ui.NAVY_TEXT,true));
        c6.addView(Ui.muted(this,"RM + PM clear in one place (Admin). Masters/recipes never touched. Guard + confirm. Backup first."));
        final EditText mo=dateField("Pick any date in the month");mo.setText(today());c6.addView(mo);
        String[][] dels={{"Delete receives of this month","receives"},{"Delete batch counts of this month","batches"},{"Delete wastage of this month","waste"},{"Delete stock counts of this month","counts"},{"Delete PM entries of this month","pm_month"},{"Delete ALL PM entries (all months)","pm_all"},{"Delete ALL RM entries (all months)","all"}};
        for(String[] dx:dels){final String k=dx[1],label=dx[0];Button b=Ui.secondary(this,label);b.setTextColor(Ui.BAD);b.setOnClickListener(v->{String m=mo.getText().toString().trim();if(!k.equals("all")&&!PlanLogic.validDate(m)){toast("Pick a date first");return;}final String month=m.length()>=7?m.substring(0,7):"";confirmDo(label+"?",k.equals("all")?"Every receive, batch count, wastage and stock count will be deleted.":"Month "+month+". This cannot be undone.",()->wipe(k,month));});c6.addView(b,Ui.lp(this,-1,-2,0,0,3,0,3));}
        addCard(c6);

        LinearLayout c7=Ui.card(this,Ui.CARD);
        
        LinearLayout cSync=Ui.card(this,Ui.CARD);
        cSync.addView(Ui.text(this,"Team sync (hotspot)",15,Ui.NAVY_TEXT,true));
        cSync.addView(Ui.muted(this,"Admin: Start server on hotspot. Supervisors: mode Client, same PIN, server IP (often 192.168.43.1), Sync now."));
        final TextView syncSt=Ui.text(this,"Status: "+SyncHub.get(this).status()+" · pending "+SyncHub.get(this).pendingCount(),13,Ui.INK,false);
        cSync.addView(syncSt);
        final EditText modeEt=edit("Mode: off / server / client");
        modeEt.setText(db.setting("sync_mode","off"));
        final EditText dutyName=edit("Your name (on duty)");
        dutyName.setText(db.setting("duty_name", db.setting("user_name","")));
        cSync.addView(Ui.muted(this,"Display name on map/list"));
        cSync.addView(dutyName);
        cSync.addView(Ui.muted(this,"Mode"));
        cSync.addView(modeEt);
        final EditText pinEt=edit("Shared PIN");
        pinEt.setText(db.setting("sync_pin","1234"));
        cSync.addView(Ui.muted(this,"PIN"));
        cSync.addView(pinEt);
        final EditText ipEt=edit("Server IP (client)");
        ipEt.setText(db.setting("sync_server_ip","192.168.43.1"));
        cSync.addView(Ui.muted(this,"Server IP (hotspot LAN)"));
        cSync.addView(ipEt);
        final EditText httpEt=edit("Internet webhook URL (optional)");
        httpEt.setText(db.setting("sync_http_url",""));
        cSync.addView(Ui.muted(this,"Optional HTTPS endpoint to receive JSON pushes when not on hotspot"));
        cSync.addView(httpEt);
        Button saveSync=Ui.secondary(this,"Save sync settings");
        Runnable refreshSt=()->syncSt.setText("Status: "+SyncHub.get(this).status()+" · pending "+SyncHub.get(this).pendingCount());
        saveSync.setOnClickListener(v->{
            if(!open())return;
            db.setSetting("duty_name",dutyName.getText().toString().trim());
            db.setSetting("sync_mode",modeEt.getText().toString().trim().toLowerCase(Locale.US));
            db.setSetting("sync_pin",pinEt.getText().toString().trim().isEmpty()?"1234":pinEt.getText().toString().trim());
            db.setSetting("sync_server_ip",ipEt.getText().toString().trim().isEmpty()?"192.168.43.1":ipEt.getText().toString().trim());
            db.setSetting("sync_http_url",httpEt.getText().toString().trim());
            toast("Sync settings saved");
            refreshSt.run();
        });
        cSync.addView(saveSync,Ui.lp(this,-1,-2,0,0,3,0,3));
        Button startSrv=Ui.primary(this,"Start admin server");
        startSrv.setOnClickListener(v->{
            db.setSetting("sync_mode","server");
            modeEt.setText("server");
            SyncHub.get(this).startServer(refreshSt);
            toast("Server starting… keep app open");
        });
        cSync.addView(startSrv,Ui.lp(this,-1,-2,0,0,3,0,3));
        Button stopSrv=Ui.secondary(this,"Stop server");
        stopSrv.setOnClickListener(v->SyncHub.get(this).stopServer(refreshSt));
        cSync.addView(stopSrv,Ui.lp(this,-1,-2,0,0,3,0,3));
        Button ping=Ui.secondary(this,"Ping server");
        ping.setOnClickListener(v->SyncHub.get(this).pingServer(refreshSt));
        cSync.addView(ping,Ui.lp(this,-1,-2,0,0,3,0,3));
        Button teamLive=Ui.secondary(this,"Team live locations");
        teamLive.setOnClickListener(v->showTeamLiveLocations());
        cSync.addView(teamLive,Ui.lp(this,-1,-2,0,0,3,0,3));
        Button flush=Ui.primary(this,"Sync now (flush queue)");
        flush.setOnClickListener(v->{
            db.setSetting("sync_mode","client");
            modeEt.setText("client");
            SyncHub.get(this).flushQueue(refreshSt);
            toast("Flushing…");
        });
        cSync.addView(flush,Ui.lp(this,-1,-2,0,0,3,0,3));
        add(cSync);

c7.addView(Ui.text(this,"Unified backup (RM + PM + Yield)",15,Ui.NAVY_TEXT,true));
        c7.addView(Ui.muted(this,"One file includes raw materials, products, batches, receives, PM packaging data and yield definitions."));
        Button b1=Ui.secondary(this,"Backup to JSON file");b1.setOnClickListener(v->backupJson());c7.addView(b1,Ui.lp(this,-1,-2,0,0,3,0,3));
        Button bShare=Ui.secondary(this,"Share backup (Email / Drive / WhatsApp)");bShare.setOnClickListener(v->shareUnifiedBackup());c7.addView(bShare,Ui.lp(this,-1,-2,0,0,3,0,3));
        Button b2=Ui.secondary(this,"Restore from JSON file");b2.setOnClickListener(v->Guard.run(this,db,"Restore backup",true,()->restoreJson()));c7.addView(b2,Ui.lp(this,-1,-2,0,0,3,0,3));
        Button b3=Ui.secondary(this,"Export everything as CSV");b3.setOnClickListener(v->exportCsv());c7.addView(b3,Ui.lp(this,-1,-2,0,0,3,0,3));
        addCard(c7);

        LinearLayout c8=Ui.card(this,Ui.CARD);
        c8.addView(Ui.text(this,"Security",15,Ui.NAVY_TEXT,true));
        c8.addView(Ui.muted(this,"Fingerprint (or phone PIN) is asked before Settings, Master, Recipe and every delete. One unlock lasts 3 minutes; deleting always asks again. The same lock protects Daily PM."));
        Button gb=Ui.secondary(this,"Protection: "+(Guard.enabled(db)?"ON  — tap to turn off":"OFF — tap to turn on"));
        gb.setOnClickListener(v->{if(Guard.enabled(db))Guard.run(this,db,"Turn protection off",true,()->{db.setSetting("guard","0");showSettings();});else{db.setSetting("guard","1");showSettings();}});
        c8.addView(gb);addCard(c8);
    }catch(Exception e){error(e);}
 }
 private void showLabels(){
    clear("Settings — Names and titles");
    add(Ui.muted(this,"Type a new name to replace the original. Leave a box empty to keep the original."));
    final List<String> keys=new ArrayList<>();final List<EditText> boxes=new ArrayList<>();
    for(String[] g:LABEL_GROUPS){
        add(title(g[0]));
        for(int i=1;i<g.length;i++){
            EditText e=edit(g[i]);String cur=db.setting("lbl::"+g[i],"");e.setText(cur==null?"":cur);
            TextView cap=Ui.muted(this,"Original: "+g[i]);add(cap);add(e);keys.add(g[i]);boxes.add(e);
        }
    }
    Button save=btn("Save names");
    save.setOnClickListener(v->{if(!open())return;for(int i=0;i<keys.size();i++)db.setSetting("lbl::"+keys.get(i),boxes.get(i).getText().toString().trim());toast("Saved ✔");showLabels();});
    add(save);
    Button reset=Ui.secondary(this,"Reset all names to the original");
    reset.setOnClickListener(v->confirmDo("Reset all names?","Every title and heading goes back to the original.",()->{for(String k:keys)db.setSetting("lbl::"+k,"");toast("Reset");showLabels();}));
    add(reset);
 }
 private void wipe(String what,String month){
    SQLiteDatabase x=db.getWritableDatabase();
    try{
        x.beginTransaction();
        try{
            String like=month+"-%";
            if(what.equals("receives"))x.delete("receives","date LIKE ?",new String[]{like});
            else if(what.equals("batches")){x.execSQL("DELETE FROM mixing2_batch_log WHERE batch_id IN (SELECT id FROM batches WHERE date LIKE ?)",new Object[]{like});x.delete("batches","date LIKE ?",new String[]{like});}
            else if(what.equals("waste"))x.delete("mixing2_batch_log","batch_id<0 AND date LIKE ?",new String[]{like});
            else if(what.equals("counts"))x.delete("counts","date LIKE ?",new String[]{like});
            else if(what.equals("pm_month")){
                // handled after transaction via PmDb
            }
            else if(what.equals("pm_all")){
                // handled after transaction via PmDb
            }
            else{x.delete("receives",null,null);x.delete("mixing2_batch_log",null,null);x.delete("batches",null,null);x.delete("counts",null,null);}
            x.setTransactionSuccessful();
        }finally{x.endTransaction();}
        if(what.equals("pm_month")){
            new PmDb(this).getWritableDatabase().delete("entries","date LIKE ?",new String[]{month+"-%"});
        }else if(what.equals("pm_all")){
            new PmDb(this).clearAllEntries();
        }
        toast("Deleted");showSettings();
    }catch(Exception e){error(e);}
 }
 private void deleteRm(String code){
    try{
        JSONObject a=db.one("SELECT COUNT(*) n FROM receives WHERE rm_code=?",new String[]{code});JSONObject b=db.one("SELECT COUNT(*) n FROM mixing2_batch_log WHERE rm_code=?",new String[]{code});
        int n=(a==null?0:a.optInt("n"))+(b==null?0:b.optInt("n"));
        if(n>0)throw new Exception("This raw material has "+n+" receive/usage record(s), so it cannot be deleted.");
        db.delete("recipes","rm_code=?",new String[]{code});db.delete("rms","code=?",new String[]{code});
        toast("Raw material deleted");showRmMaster();
    }catch(Exception e){error(e);}
 }
 private void deleteProduct(int code){
    try{
        JSONObject b=db.one("SELECT COUNT(*) n FROM batches WHERE product_code=?",new String[]{String.valueOf(code)});
        int n=b==null?0:b.optInt("n");
        if(n>0)throw new Exception("This product has "+n+" batch record(s), so it cannot be deleted.");
        db.delete("recipes","product_code=?",new String[]{String.valueOf(code)});db.delete("products","code=?",new String[]{String.valueOf(code)});
        toast("Product deleted");showProductMaster();
    }catch(Exception e){error(e);}
 }
 private void showRmPlanner(){
    clear("Batch Planner — What do I need?");
    try{
        add(Ui.muted(this,"Type how many batches you plan for each product. The app adds up the raw material needed and compares it with today’s closing stock."));
        final List<EditText> ins=new ArrayList<>();
        for(JSONObject p:db.rows("SELECT code,name FROM products ORDER BY code",null)){
            LinearLayout r=row();
            r.addView(Ui.text(this,p.optString("name"),14,Ui.INK,false),new LinearLayout.LayoutParams(0,-2,1));
            EditText e=Ui.field(this,"0");e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);e.setGravity(Gravity.CENTER);e.setTag(p.optString("code"));
            r.addView(e,new LinearLayout.LayoutParams(dp(90),dp(48)));ins.add(e);add(r);
        }
        final LinearLayout out=new LinearLayout(this);out.setOrientation(LinearLayout.VERTICAL);
        Button calc=btn("Calculate");calc.setOnClickListener(v->planResult(out,ins));
        add(calc);add(out);
    }catch(Exception ex){error(ex);}
 }
 private void planResult(LinearLayout out,List<EditText> ins){
    try{
        out.removeAllViews();
        Map<String,Double> batches=new HashMap<>();
        for(EditText e:ins){double n=num(e.getText().toString());if(n>0)batches.put(String.valueOf(e.getTag()),n);}
        Map<String,Double> stock=closingMap(today());
        List<String[]> recipe=new ArrayList<>();
        for(JSONObject r:db.rows("SELECT rm_code,product_code,qty FROM recipes WHERE qty<>0",null))recipe.add(new String[]{r.optString("rm_code"),r.optString("product_code"),r.optString("qty")});
        Map<String,String> names=new HashMap<>(),uoms=new HashMap<>();
        for(JSONObject r:db.rows("SELECT code,name,uom FROM rms",null)){names.put(r.optString("code"),r.optString("name"));uoms.put(r.optString("code"),r.optString("uom","kg"));}
        out.addView(title("Raw material needed"));
        if(batches.isEmpty()){out.addView(Ui.muted(this,"Enter at least one batch count above."));}
        else{
            Map<String,Double> need=PlanLogic.need(batches,recipe);
            List<String> codes=new ArrayList<>(need.keySet());
            Collections.sort(codes,(a,b)->{try{return Integer.compare(Integer.parseInt(a),Integer.parseInt(b));}catch(Exception ex){return a.compareTo(b);}});
            int shortN=0;List<View> cards=new ArrayList<>();
            for(String code:codes){
                double nd=need.get(code),have=nz(stock.get(code)),bal=have-nd;boolean ok=bal>=-0.0005;if(!ok)shortN++;
                LinearLayout c=Ui.card(this,ok?Ui.SOFT_GREEN:Ui.SOFT_RED);
                LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
                top.addView(Ui.text(this,code+"  "+(names.containsKey(code)?names.get(code):"(not in RM master)"),14,Ui.INK,true),new LinearLayout.LayoutParams(0,-2,1));
                top.addView(Ui.text(this,ok?"OK":"SHORT",12,ok?Ui.GOOD:Ui.BAD,true));
                c.addView(top);
                c.addView(Ui.muted(this,"Need "+fmt(nd)+"  •  Stock "+fmt(have)+"  •  "+(ok?"Left "+fmt(bal):"Short by "+fmt(-bal))+" "+(uoms.containsKey(code)?uoms.get(code):"")));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(8));c.setLayoutParams(p);
                cards.add(c);
            }
            TextView sum=Ui.text(this,shortN==0?"Stock is enough for this plan ✔":shortN+" raw material(s) are short for this plan",15,shortN==0?Ui.GOOD:Ui.BAD,true);
            sum.setPadding(0,dp(2),0,dp(8));out.addView(sum);
            for(View c:cards)out.addView(c);
        }
        out.addView(title("You can make up to"));
        out.addView(Ui.muted(this,"Whole batches of each product if today’s stock were used for that product alone."));
        List<JSONObject> ps=db.rows("SELECT code,name FROM products ORDER BY code",null);
        double[] mb=new double[ps.size()];double maxAll=1;
        for(int i=0;i<ps.size();i++){List<String[]> pr=new ArrayList<>();for(String[] r:recipe)if(r[1].equals(ps.get(i).optString("code")))pr.add(r);mb[i]=PlanLogic.maxBatches(stock,pr);maxAll=Math.max(maxAll,mb[i]);}
        for(int i=0;i<ps.size();i++)out.addView(Ui.barRow(this,ps.get(i).optString("name"),mb[i]<0?"no recipe":fmt(mb[i])+" batches",mb[i]<0?0:mb[i]/maxAll,Ui.ACCENT));
    }catch(Exception ex){error(ex);}
 }
 private double closingFor(String code,String d)throws Exception{return opening(d,code)+receive(d,code)-use(d,code);}
 private void addLiveCard(LinearLayout parent,String label,TextView value,String color){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(10),dp(10),dp(10));card.setBackground(Ui.shape(this,Ui.CARD,Ui.LINE,14));TextView l=new TextView(this);l.setText(label);l.setTextSize(11);l.setTextColor(Ui.MUTED);value.setTextSize(17);value.setTypeface(null,Typeface.BOLD);value.setTextColor(Ui.NAVY_TEXT);value.setSingleLine(false);value.setMaxLines(3);value.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(l);card.addView(value);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(96),1);cp.setMargins(dp(3),dp(3),dp(3),dp(3));parent.addView(card,cp);}
 private void startLive(final TextView[] views,final List<List<String>> items,final String[] empty,final String[] summary,final String[] sub){
    final int n=views.length;final int[] pos=new int[n];final int[] turn={0};
    boolean any=false;
    for(int i=0;i<n;i++){if(items.get(i).isEmpty())views[i].setText(Ui.twoLine(empty[i],""));else{views[i].setText(Ui.twoLine(summary[i],sub[i]));any=true;}}
    if(!any)return;
    final long gap=(long)(liveGap()*1000);
    final Runnable[] task=new Runnable[1];
    task[0]=()->{
        if(!views[0].isAttachedToWindow())return;
        int w=-1;
        for(int tries=0;tries<n;tries++){int c=turn[0]%n;turn[0]++;if(!items.get(c).isEmpty()){w=c;break;}}
        if(w>=0){
            List<String> it=items.get(w);
            pos[w]=(pos[w]+1)%(it.size()+1);
            CharSequence t;
            if(pos[w]==0)t=Ui.twoLine(summary[w],sub[w]);
            else{String x=it.get(pos[w]-1);int k=x.lastIndexOf("  ");t=k<0?Ui.twoLine(x,""):Ui.twoLine(x.substring(k+2),x.substring(0,k));}
            TextView v=views[w];v.animate().cancel();v.setAlpha(0.25f);v.setTranslationY(dp(5));v.setText(t);v.animate().alpha(1f).translationY(0).setDuration(180).start();
        }
        main.postDelayed(task[0],gap);
    };
    dashboardRunnables.add(task[0]);
    main.postDelayed(task[0],gap);
 }
 private void addSectionTitle(String text,int bg){
    String icon="•";
    String t=text==null?"":text;
    if(t.contains("Batch")) icon="📦";
    else if(t.contains("Receive")) icon="📥";
    else if(t.contains("Mixing")) icon="🥣";
    else if(t.contains("Expiry")) icon="⏰";
    else if(t.contains("Stock Watch")) icon="👁️";
    else if(t.contains("Stock Overview")) icon="📦";
    else if(t.contains("Shift")) icon="🔄";
    LinearLayout row=new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(8),dp(10),dp(8),dp(10));
    row.setBackgroundColor(bg);
    TextView ic=Ui.text(this,icon,15,Ui.NAVY_TEXT,true);
    ic.setPadding(0,0,dp(8),0);
    row.addView(ic);
    TextView h=Ui.text(this,lbl(t),16,Ui.NAVY_TEXT,true);
    row.addView(h);
    add(row);
}
 private void addSimpleTable(String[] headers,List<String> rows){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(8),dp(6),dp(8),dp(6));card.setBackgroundColor(Ui.CARD);if(rows.isEmpty()){TextView e=new TextView(this);e.setText("No entries today");e.setTextSize(13);e.setPadding(dp(6),dp(8),dp(6),dp(8));card.addView(e);}else{for(String value:rows){TextView t=new TextView(this);t.setText(value);t.setTextSize(13);t.setTextColor(Ui.INK);t.setPadding(dp(6),dp(7),dp(6),dp(7));card.addView(t);View divider=new View(this);divider.setBackgroundColor(Ui.LINE);card.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));}}add(card);Space sp=new Space(this);add(sp);}

private void addDashCard(LinearLayout parent,String label,String value){
    LinearLayout card=new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dp(16),dp(12),dp(16),dp(12));
    card.setBackgroundColor(Ui.CARD);

    TextView a=new TextView(this);
    a.setText(label);
    a.setTextSize(11);
    a.setTextColor(Ui.MUTED);

    TextView b=new TextView(this);
    b.setText(value);
    b.setTextSize(24);
    b.setTypeface(null,Typeface.BOLD);
    b.setTextColor(Ui.NAVY_TEXT);

    card.addView(a);
    card.addView(b);
    parent.addView(card,new LinearLayout.LayoutParams(-1,dp(76)));

    Space sp=new Space(this);
    parent.addView(sp,new LinearLayout.LayoutParams(1,dp(7)));
}

private void showMaster(){clear("Master Data — Dynamic Add / Edit");Button rm=btn("RM Master — Add / Edit");rm.setOnClickListener(v->showRmMaster());add(rm);Button p=btn("Product Master — Add / Edit");p.setOnClickListener(v->showProductMaster());add(p);TextView n=new TextView(this);n.setText("Product Code/Name changes are migrated through batch references; new Products automatically receive recipe quantity 0 for every RM.");n.setPadding(0,dp(12),0,0);add(n);}
 private void showRmMaster(){
    clear("RM Master");
    try{
        if(admin()){
            LinearLayout box=Ui.card(this,Ui.SOFT_BLUE);
            box.addView(Ui.text(this,"Add a raw material",15,Ui.NAVY_TEXT,true));
            final EditText c=edit("RM code"),n=edit("Name"),u=edit("Unit (kg)"),ro=edit("Reorder level (optional)");
            ro.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            box.addView(c);box.addView(n);box.addView(u);box.addView(ro);
            Button a=btn("Add RM");box.addView(a);
            a.setOnClickListener(v->{if(!open())return;try{
                String code=c.getText().toString().trim(),name=n.getText().toString().trim(),uom=u.getText().toString().trim();
                if(code.isEmpty()||name.isEmpty())throw new Exception("Code and name are required");
                if(db.one("SELECT code FROM rms WHERE code=?",new String[]{code})!=null)throw new Exception("Duplicate RM Code");
                ContentValues z=new ContentValues();z.put("code",code);z.put("name",name);z.put("uom",uom.isEmpty()?"kg":uom);z.put("opening",0);z.put("reorder",Math.max(0,num(ro.getText().toString())));db.insert("rms",z);
                for(JSONObject p:db.rows("SELECT code FROM products",null)){ContentValues q=new ContentValues();q.put("rm_code",code);q.put("product_code",p.getInt("code"));q.put("qty",0);db.insert("recipes",q);}
                toast("RM added ✔");showRmMaster();
            }catch(Exception e){error(e);}});
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.setMargins(0,0,0,dp(10));box.setLayoutParams(bp);
            add(box);
        }
        for(JSONObject r:db.rows("SELECT * FROM rms ORDER BY CAST(code AS INTEGER)",null)){
            LinearLayout card=Ui.card(this,Ui.CARD);
            final EditText code=edit("Code"),name=edit("Name"),uom=edit("Unit"),open=edit("Opening"),re=edit("Reorder at");
            code.setText(r.getString("code"));name.setText(r.getString("name"));uom.setText(r.getString("uom"));open.setText(fmt(r.optDouble("opening",0)));re.setText(fmt(r.optDouble("reorder",0)));
            int dec=android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED;
            open.setInputType(dec);re.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            LinearLayout r1=row();r1.addView(code,Ui.lp(this,0,dp(48),1,0,0,6,0));r1.addView(name,Ui.lp(this,0,dp(48),2,0,0,0,0));
            card.addView(r1);
            card.addView(Ui.muted(this,"Unit  •  Opening stock  •  Reorder level (warns when stock falls to this)"));
            LinearLayout r2=row();r2.addView(uom,Ui.lp(this,0,dp(48),1,0,0,6,0));r2.addView(open,Ui.lp(this,0,dp(48),1,0,0,6,0));r2.addView(re,Ui.lp(this,0,dp(48),1,0,0,0,0));
            card.addView(r2);
            Button save=btn("Save");Button delRm=Ui.secondary(this,"Delete");delRm.setTextColor(Ui.BAD);LinearLayout br=row();br.addView(save,Ui.lp(this,0,-2,1,0,0,6,0));br.addView(delRm,Ui.lp(this,0,-2,1,0,0,0,0));card.addView(br);
            final String old=r.getString("code");
            save.setOnClickListener(v->saveRm(old,code,name,uom,open,re));
            final String rmName=r.optString("name");
            delRm.setOnClickListener(v->Guard.run(this,db,"Delete raw material",true,()->new AlertDialog.Builder(this).setTitle("Delete RM "+old+"?").setMessage(rmName+"\nIts recipe rows are removed too. Raw materials with receive or usage history cannot be deleted.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(dd,ww)->deleteRm(old)).show()));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(10));card.setLayoutParams(cp);
            add(card);
        }
    }catch(Exception e){error(e);}
 }
 private void saveRm(String old,EditText c,EditText n,EditText u,EditText o,EditText re){
    if(!open())return;
    SQLiteDatabase x=db.getWritableDatabase();
    try{
        String nc=c.getText().toString().trim(),nn=n.getText().toString().trim(),nu=u.getText().toString().trim();
        if(nc.isEmpty()||nn.isEmpty())throw new Exception("Code and name are required");
        if(!nc.equals(old)&&db.one("SELECT code FROM rms WHERE code=?",new String[]{nc})!=null)throw new Exception("Duplicate RM Code");
        x.beginTransaction();
        try{
            ContentValues v=new ContentValues();v.put("code",nc);v.put("name",nn);v.put("uom",nu.isEmpty()?"kg":nu);v.put("opening",num(o.getText().toString()));v.put("reorder",Math.max(0,num(re.getText().toString())));
            x.update("rms",v,"code=?",new String[]{old});
            if(!nc.equals(old)){String[] tbl={"recipes","receives","mixing2_batch_log"};for(String t:tbl){ContentValues q=new ContentValues();q.put("rm_code",nc);x.update(t,q,"rm_code=?",new String[]{old});}}
            x.setTransactionSuccessful();
        }finally{x.endTransaction();}
        toast("Saved ✔");showRmMaster();
    }catch(Exception e){error(e);}
 }
 private void showProductMaster(){
    clear("Product Master — Dynamic Add / Edit");
    try{
        if(admin()){
            LinearLayout box=Ui.card(this,Ui.SOFT_BLUE);
            box.addView(Ui.text(this,"Add a product",15,Ui.NAVY_TEXT,true));
            final EditText c=edit("Product code (number)"),n=edit("Product name");
            c.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            box.addView(c);box.addView(n);
            Button a=btn("Add product");box.addView(a);
            a.setOnClickListener(v->{if(!open())return;try{
                int code=Integer.parseInt(c.getText().toString().trim());String name=n.getText().toString().trim();
                if(name.isEmpty())throw new Exception("Name required");
                if(db.one("SELECT code FROM products WHERE code=?",new String[]{String.valueOf(code)})!=null)throw new Exception("Duplicate Product Code");
                ContentValues p=new ContentValues();p.put("code",code);p.put("name",name);db.insert("products",p);
                for(JSONObject r:db.rows("SELECT code FROM rms",null)){ContentValues q=new ContentValues();q.put("rm_code",r.getString("code"));q.put("product_code",code);q.put("qty",0);db.insert("recipes",q);}
                toast("Product added ✔");showProductMaster();
            }catch(NumberFormatException nf){toast("Product code must be a number");}catch(Exception e){error(e);}});
            addCard(box);
        }
        for(JSONObject p:db.rows("SELECT * FROM products ORDER BY code",null)){
            LinearLayout card=Ui.card(this,Ui.CARD);
            final EditText c=edit("Code"),n=edit("Name");c.setText(p.getString("code"));n.setText(p.getString("name"));
            c.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            LinearLayout r1=row();r1.addView(c,Ui.lp(this,0,dp(48),1,0,0,6,0));r1.addView(n,Ui.lp(this,0,dp(48),2,0,0,0,0));card.addView(r1);
            Button sv=btn("Save");Button del=Ui.secondary(this,"Delete");del.setTextColor(Ui.BAD);
            LinearLayout r2=row();r2.addView(sv,Ui.lp(this,0,-2,1,0,0,6,0));r2.addView(del,Ui.lp(this,0,-2,1,0,0,0,0));card.addView(r2);
            final int old=p.getInt("code");final String pname=p.getString("name");
            sv.setOnClickListener(v->saveProduct(old,c,n));
            del.setOnClickListener(v->Guard.run(this,db,"Delete product",true,()->new AlertDialog.Builder(this).setTitle("Delete product "+old+"?").setMessage(pname+"\\nIts recipe rows are removed too. Products with batch history cannot be deleted.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(dd,ww)->deleteProduct(old)).show()));
            addCard(card);
        }
    }catch(Exception e){error(e);}
 }
 private void saveProduct(int old,EditText c,EditText n){if(!open())return;try{int nc=Integer.parseInt(c.getText().toString().trim());String nn=n.getText().toString().trim();if(nn.isEmpty())throw new Exception("Name required");if(nc!=old&&db.one("SELECT code FROM products WHERE code=?",new String[]{String.valueOf(nc)})!=null)throw new Exception("Duplicate Product Code");db.getWritableDatabase().beginTransaction();ContentValues p=new ContentValues();p.put("code",nc);p.put("name",nn);db.update("products",p,"code=?",new String[]{String.valueOf(old)});if(nc!=old){ContentValues q=new ContentValues();q.put("product_code",nc);db.update("recipes",q,"product_code=?",new String[]{String.valueOf(old)});ContentValues b=new ContentValues();b.put("product_code",nc);db.update("batches",b,"product_code=?",new String[]{String.valueOf(old)});ContentValues bl=new ContentValues();bl.put("product_code",nc);db.update("mixing2_batch_log",bl,"product_code=?",new String[]{String.valueOf(old)});}db.getWritableDatabase().setTransactionSuccessful();db.getWritableDatabase().endTransaction();showProductMaster();}catch(Exception e){try{db.getWritableDatabase().endTransaction();}catch(Exception ignored){}error(e);}

for(int i=0;i<content.getChildCount();i++){
    View vv=content.getChildAt(i);
    if(vv instanceof Button){
        Button bb=(Button)vv;
        bb.setAllCaps(false);
        bb.setTextSize(14);
        bb.setPadding(dp(12),dp(10),dp(12),dp(10));
    }
}
}
 private void showRecipe(){
    clear("Mixing-2 Recipe — Dynamic RM × Product");
    if(!admin())return;
    try{
        // zoom level persisted lightly in settings
        float z0=(float)num(db.setting("recipe_zoom","1"));
        if(z0<0.7f)z0=0.7f; if(z0>1.8f)z0=1.8f;
        final float[] zoom={z0};

        LinearLayout zoomBar=new LinearLayout(this);
        zoomBar.setOrientation(LinearLayout.HORIZONTAL);
        zoomBar.setGravity(Gravity.CENTER_VERTICAL);
        zoomBar.setPadding(0,0,0,dp(8));
        TextView zLab=Ui.text(this,"Zoom "+Math.round(zoom[0]*100)+"%",14,Ui.NAVY_TEXT,true);
        Button zOut=Ui.secondary(this,"−  Zoom out");
        Button zIn=Ui.secondary(this,"+  Zoom in");
        Button zReset=Ui.secondary(this,"100%");
        zoomBar.addView(zOut,Ui.lp(this,0,dp(44),1,0,0,4,0));
        zoomBar.addView(zLab,Ui.lp(this,0,dp(44),1,4,0,4,0));
        zoomBar.addView(zIn,Ui.lp(this,0,dp(44),1,4,0,4,0));
        zoomBar.addView(zReset,Ui.lp(this,0,dp(44),1,4,0,0,0));
        add(zoomBar);
        
add(Ui.muted(this,"Pinch-style control for the RM × Product recipe grid. Cell size and text scale together."));

        // Quick line: Product → RM → qty → Save (writes into grid cell)
        LinearLayout quick=Ui.card(this,Ui.CARD);
        quick.addView(Ui.text(this,"Quick recipe line",15,Ui.NAVY_TEXT,true));
        quick.addView(Ui.muted(this,"Select Product, then RM, enter qty per batch, Save. Goes into the recipe cell. Grid cells stay editable. Guard/PIN required."));
        final Spinner qProd=new Spinner(this);
        final Spinner qRm=new Spinner(this);
        final List<Integer> qProdCodes=new ArrayList<>();
        final List<String> qRmCodes=new ArrayList<>();
        ArrayList<String> qProdLabs=new ArrayList<>();
        ArrayList<String> qRmLabs=new ArrayList<>();
        for(JSONObject pr:db.rows("SELECT code,name FROM products ORDER BY code",null)){
            qProdCodes.add(pr.optInt("code"));
            qProdLabs.add(pr.optInt("code")+" — "+pr.optString("name"));
        }
        for(JSONObject rm:db.rows("SELECT code,name FROM rms ORDER BY CAST(code AS INTEGER)",null)){
            qRmCodes.add(rm.optString("code"));
            qRmLabs.add(rm.optString("code")+" — "+rm.optString("name"));
        }
        qProd.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,qProdLabs));
        qRm.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,qRmLabs));
        final EditText qQty=edit("Qty per batch");
        qQty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        quick.addView(Ui.muted(this,"Product")); quick.addView(qProd);
        quick.addView(Ui.muted(this,"Raw material (RM)")); quick.addView(qRm);
        quick.addView(Ui.muted(this,"Qty in recipe")); quick.addView(qQty);
        Button qSave=btn("Save line to recipe");
        qSave.setOnClickListener(v->{
                try{
                    if(qProdCodes.isEmpty()||qRmCodes.isEmpty())throw new Exception("Add products and RMs first");
                    int pc=qProdCodes.get(qProd.getSelectedItemPosition());
                    String rc=qRmCodes.get(qRm.getSelectedItemPosition());
                    double qty=num(qQty.getText().toString());
                    if(qty<0)throw new Exception("Qty cannot be negative");
                    ContentValues z=new ContentValues();
                    z.put("rm_code",rc);
                    z.put("product_code",pc);
                    z.put("qty",qty);
                    JSONObject exists=db.one("SELECT qty FROM recipes WHERE rm_code=? AND product_code=?",new String[]{rc,String.valueOf(pc)});
                    if(exists==null)db.insert("recipes",z);
                    else db.update("recipes",z,"rm_code=? AND product_code=?",new String[]{rc,String.valueOf(pc)});
                    db.audit(user(),role(),"recipe_line",pc+"|"+rc+"="+qty);
                    toast("Recipe line saved ✔");
                    showRecipe();
                }catch(Exception ex){error(ex);}
        });
        quick.addView(qSave);
        add(quick);


        final HorizontalScrollView hs=new HorizontalScrollView(this);
        hs.setFillViewport(false);
        final TableLayout t=new TableLayout(this);
        t.setStretchAllColumns(false);
        List<JSONObject> ps=db.rows("SELECT * FROM products ORDER BY code",null);
        List<JSONObject> rms=db.rows("SELECT * FROM rms ORDER BY CAST(code AS INTEGER)",null);

        final Runnable applyZoom=()->{
            t.removeAllViews();
            float z=zoom[0];
            int cellW=Math.max(dp(72),(int)(dp(110)*z));
            int cellH=Math.max(dp(40),(int)(dp(52)*z));
            int rmW=Math.max(dp(100),(int)(dp(140)*z));
            float textSp=Math.max(11f,14f*z);
            float headSp=Math.max(11f,13f*z);
            zLab.setText("Zoom "+Math.round(z*100)+"%");

            TableRow h=new TableRow(this);
            TextView corner=tv("RM \\ Product",1);
            corner.setTextSize(headSp);
            corner.setTypeface(null,Typeface.BOLD);
            h.addView(corner,new TableRow.LayoutParams(rmW,cellH));
            for(JSONObject p:ps){
                TextView th=tv(p.optString("name"),1);
                th.setTextSize(headSp);
                th.setTypeface(null,Typeface.BOLD);
                th.setMaxLines(3);
                th.setEllipsize(android.text.TextUtils.TruncateAt.END);
                h.addView(th,new TableRow.LayoutParams(cellW,cellH));
            }
            t.addView(h);

            for(JSONObject r:rms){
                TableRow tr=new TableRow(this);
                TextView lab=tv(r.optString("code")+" "+r.optString("name"),1);
                lab.setTextSize(textSp);
                lab.setMaxLines(3);
                tr.addView(lab,new TableRow.LayoutParams(rmW,cellH));
                for(JSONObject p:ps){
                    EditText e=edit("");
                    e.setTextSize(textSp);
                    e.setPadding(dp(4),dp(4),dp(4),dp(4));
                    e.setGravity(Gravity.CENTER);
                    e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
                    try{
                        JSONObject q=db.one("SELECT qty FROM recipes WHERE rm_code=? AND product_code=?",new String[]{r.getString("code"),p.getString("code")});
                        e.setText(q==null?"0":fmt(q.getDouble("qty")));
                    }catch(Exception ex){e.setText("0");}
                    e.setTag(r.optString("code")+"|"+p.optString("code"));
                    e.setFocusable(false);
                    e.setFocusableInTouchMode(false);
                    e.setClickable(false);
                    e.setLongClickable(false);
                    e.setEnabled(false);
                    tr.addView(e,new TableRow.LayoutParams(cellW,cellH));
                }
                t.addView(tr);
            }
            db.setSetting("recipe_zoom",String.valueOf(zoom[0]));
        };

        hs.addView(t);
        add(hs);
        applyZoom.run();

        zIn.setOnClickListener(v->{
            if(zoom[0]>=1.8f){toast("Max zoom");return;}
            zoom[0]=Math.min(1.8f,zoom[0]+0.15f);
            applyZoom.run();
        });
        zOut.setOnClickListener(v->{
            if(zoom[0]<=0.7f){toast("Min zoom");return;}
            zoom[0]=Math.max(0.7f,zoom[0]-0.15f);
            applyZoom.run();
        });
        zReset.setOnClickListener(v->{zoom[0]=1f;applyZoom.run();});

        final boolean[] recipeUnlocked={false};
        Button unlockBtn=Ui.secondary(this,"Unlock recipe edit (security)");
        unlockBtn.setOnClickListener(v->{
            if(recipeUnlocked[0]){ toast("Already unlocked"); return; }
            Guard.run(this,db,"Unlock recipe grid",true,()->{
                recipeUnlocked[0]=true;
                try{
                    for(int i=1;i<t.getChildCount();i++){
                        TableRow tr=(TableRow)t.getChildAt(i);
                        for(int j=1;j<tr.getChildCount();j++){
                            View cell=tr.getChildAt(j);
                            if(cell instanceof EditText){
                                EditText e=(EditText)cell;
                                e.setEnabled(true);
                                e.setFocusable(true);
                                e.setFocusableInTouchMode(true);
                                e.setClickable(true);
                            }
                        }
                    }
                    unlockBtn.setText("Recipe unlocked — edit cells");
                    toast("Recipe cells unlocked");
                }catch(Exception ex){error(ex);}
            });
        });
        add(unlockBtn);
        add(Ui.muted(this,"Cells are locked until Unlock (Guard/PIN). Save still requires security."));

        Button save=btn("Save All Recipes");
        save.setOnClickListener(v->{
            if(!open())return;
            if(!recipeUnlocked[0]){toast("Unlock recipe edit first");return;}
            if(!admin()){toast("Admin/Editor only");return;}
            Guard.run(this,db,"Save all recipes",true,()->{
            try{
                for(int i=1;i<t.getChildCount();i++){
                    TableRow tr=(TableRow)t.getChildAt(i);
                    for(int j=1;j<tr.getChildCount();j++){
                        View cell=tr.getChildAt(j);
                        if(!(cell instanceof EditText))continue;
                        EditText e=(EditText)cell;
                        String[] k=e.getTag().toString().split("\\|");
                        ContentValues z=new ContentValues();
                        z.put("rm_code",k[0]);
                        z.put("product_code",Integer.parseInt(k[1]));
                        z.put("qty",num(e.getText().toString()));
                        // upsert
                        JSONObject exists=db.one("SELECT qty FROM recipes WHERE rm_code=? AND product_code=?",new String[]{k[0],k[1]});
                        if(exists==null)db.insert("recipes",z);
                        else db.update("recipes",z,"rm_code=? AND product_code=?",new String[]{k[0],k[1]});
                    }
                }
                db.audit(user(),role(),"recipe_save","all");
                toast("Recipe saved ✔");
            }catch(Exception ex){error(ex);}
            });
        });
        add(save);
    }catch(Exception e){error(e);}
}

 private void showDaily(){clear("RM Daily Entry — Add / Edit");try{
EditText d=dateField("Date"),q=edit("Quantity"),v=edit("Voucher No"),note=edit("Note");d.setText(today());
final EditText lot=edit("Lot / Batch No (optional)"),exp=dateField("Expiry date (optional) — long-press to clear");exp.setOnLongClickListener(vv->{exp.setText("");return true;});
Spinner rm=new Spinner(this);ArrayList<String>a=new ArrayList<>();for(JSONObject x:db.rows("SELECT code||' — '||name AS label FROM rms ORDER BY code",null))a.add(x.getString("label"));rm.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,a));
add(d);add(rm);add(v);add(q);add(note);add(lot);add(exp);
d.setTextSize(15);q.setTextSize(15);v.setTextSize(15);note.setTextSize(15);
d.setPadding(dp(12),dp(12),dp(12),dp(12));
q.setPadding(dp(12),dp(12),dp(12),dp(12));
v.setPadding(dp(12),dp(12),dp(12),dp(12));
note.setPadding(dp(12),dp(12),dp(12),dp(12));
rm.setPadding(dp(8),dp(8),dp(8),dp(8));
Button save=btn("Save Receive");
save.setTextSize(15);
save.setAllCaps(false);
save.setPadding(dp(12),dp(10),dp(12),dp(10));save.setOnClickListener(x->{try{
String label=String.valueOf(rm.getSelectedItem());if(label==null||label.equals("null"))throw new Exception("RM required");
String day=ymd(d);if(db.monthLocked(day)){toast("Month locked");return;}String code=label.split(" — ",2)[0].trim();ContentValues z=new ContentValues();z.put("date",day);z.put("rm_code",code);z.put("qty",num(q.getText().toString()));z.put("voucher_no",v.getText().toString().trim());z.put("note",note.getText().toString().trim());z.put("source","manual");z.put("lot",lot.getText().toString().trim());z.put("expiry",expiryOf(exp));db.insert("receives",z);
                try{
                    if("client".equals(db.setting("sync_mode","off"))){
                        org.json.JSONObject payload=new org.json.JSONObject();
                        payload.put("date",day);payload.put("rm_code",code);payload.put("qty",num(q.getText().toString()));
                        payload.put("voucher_no",v.getText().toString().trim());payload.put("note",note.getText().toString().trim());
                        payload.put("source","manual");payload.put("lot",lot.getText().toString().trim());payload.put("expiry",expiryOf(exp));
                        SyncHub.get(this).enqueue("receive", payload);
                    }
                }catch(Exception ignored){}
                toast("Receive saved ✔");showDaily();
}catch(Exception e){error(e);}});add(save);
// List range: today | last 7 days (does not change monthly reports / DB)
String entryRange=db.setting("entry_list_range","7");
if(!"today".equals(entryRange)) entryRange="7";
final String entryRangeF=entryRange;
LinearLayout rangeRow=new LinearLayout(this);
rangeRow.setOrientation(LinearLayout.HORIZONTAL);
rangeRow.setPadding(0,dp(6),0,dp(6));
Button bToday=btn("today".equals(entryRangeF)?"● Today":"Today");
Button b7=btn("7".equals(entryRangeF)?"● Last 7 days":"Last 7 days");
bToday.setOnClickListener(vv->{ db.setSetting("entry_list_range","today"); showDaily(); });
b7.setOnClickListener(vv->{ db.setSetting("entry_list_range","7"); showDaily(); });
rangeRow.addView(bToday,new LinearLayout.LayoutParams(0,dp(48),1));
rangeRow.addView(b7,new LinearLayout.LayoutParams(0,dp(48),1));
add(rangeRow);
String fromDay="today".equals(entryRangeF)?today():PlanLogic.shiftDate(today(),-6);
add(tv("Recent Entries ("+("today".equals(entryRangeF)?"Today":"Last 7 days")+")",1));
for(JSONObject r:db.rows("SELECT * FROM receives WHERE date>=? ORDER BY date DESC,id DESC LIMIT 200",new String[]{fromDay})){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(6),dp(8),dp(6),dp(8));
row.addView(tv("ID: "+r.optString("id")+"   "+r.optString("date")+"   RM: "+r.optString("rm_code")+"   Qty: "+fmt(r.optDouble("qty"))+"   Voucher: "+r.optString("voucher_no")+(r.optString("lot").isEmpty()?"":"   Lot: "+r.optString("lot"))+(r.optString("expiry").isEmpty()?"":"   Exp: "+r.optString("expiry")),1));
row.addView(tv(r.optString("note"),1));
Button editB=btn("Edit");
editB.setTextSize(14);
editB.setAllCaps(false);editB.setOnClickListener(xx->{d.setText(r.optString("date"));q.setText(fmt(r.optDouble("qty")));v.setText(r.optString("voucher_no"));note.setText(r.optString("note"));lot.setText(r.optString("lot"));exp.setText(r.optString("expiry"));String target=r.optString("rm_code");for(int i=0;i<a.size();i++)if(a.get(i).startsWith(target+" — ")){rm.setSelection(i);break;}save.setText("Update Receive #"+r.optString("id"));scrollFormTop();q.requestFocus();save.setOnClickListener(uu->{try{ContentValues z=new ContentValues();z.put("date",ymd(d));z.put("rm_code",String.valueOf(rm.getSelectedItem()).split(" — ",2)[0].trim());z.put("qty",num(q.getText().toString()));z.put("voucher_no",v.getText().toString().trim());z.put("note",note.getText().toString().trim());z.put("lot",lot.getText().toString().trim());z.put("expiry",expiryOf(exp));int n=db.getWritableDatabase().update("receives",z,"id=?",new String[]{r.optString("id")});if(n!=1)throw new Exception("Update failed");toast("Receive updated ✔");showDaily();}catch(Exception e){error(e);}});});
row.setPadding(dp(12),dp(10),dp(12),dp(10));
row.setBackgroundColor(Ui.CARD);
row.addView(editB);
Button delB=Ui.secondary(this,"Delete");
delB.setOnClickListener(xx->Guard.run(this,db,"Delete receive",true,()->new AlertDialog.Builder(this).setTitle("Delete receive #"+r.optString("id")+"?").setMessage(r.optString("date")+"   RM "+r.optString("rm_code")+"   Qty "+fmt(r.optDouble("qty"))).setNegativeButton("Cancel",null).setPositiveButton("Delete",(dd,ww)->{db.delete("receives","id=?",new String[]{r.optString("id")});toast("Receive deleted");showDaily();}).show()));
row.addView(delB);
if(!r.optString("photo").isEmpty()){Button phb=Ui.secondary(this,"View voucher photo");phb.setOnClickListener(xx->viewPhoto(r.optString("photo")));row.addView(phb);}
add(row);
Space rowSpace=new Space(this);
content.addView(rowSpace,new LinearLayout.LayoutParams(1,dp(6)));}
}catch(Exception e){error(e);}}
private void refreshDailyList(){showDaily();}
 private void editReceive(JSONObject r){try{LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);EditText d=dateField("Date"),q=edit("Quantity"),v=edit("Voucher No"),n=edit("Note");d.setText(r.optString("date"));q.setText(fmt(r.optDouble("qty")));v.setText(r.optString("voucher_no"));n.setText(r.optString("note"));Spinner rm=new Spinner(this);ArrayList<String>a=new ArrayList<>();int sel=0,i=0;for(JSONObject x:db.rows("SELECT code||' — '||name AS label FROM rms ORDER BY CAST(code AS INTEGER)",null)){a.add(x.getString("label"));if(x.getString("code").equals(r.optString("rm_code")))sel=i;i++;}rm.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,a));rm.setSelection(sel);b.addView(d);b.addView(rm);b.addView(q);b.addView(v);b.addView(n);new AlertDialog.Builder(this).setTitle("Edit Receive").setView(b).setNegativeButton("Cancel",null).setPositiveButton("Save",(x,w)->{try{double amount=num(q.getText().toString());if(amount<=0)throw new Exception("Quantity required");String code=String.valueOf(rm.getSelectedItem()).split(" — ")[0];ContentValues z=new ContentValues();z.put("date",ymd(d));z.put("rm_code",code);z.put("qty",amount);z.put("voucher_no",v.getText().toString());z.put("note",n.getText().toString());db.update("receives",z,"id=?",new String[]{r.getString("id")});toast("Receive updated ✔");showDaily();}catch(Exception e){error(e);}}).show();}catch(Exception e){error(e);}}
 private void showBatch(){clear("Input Batch — Editable Batch Count");try{
  EditText d=dateField("Production Date"),q=edit("Batch Count");d.setText(today());
  Spinner p=new Spinner(this);ArrayList<String>a=new ArrayList<>();for(JSONObject x:db.rows("SELECT code||' — '||name AS label FROM products ORDER BY code",null))a.add(x.getString("label"));
  p.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,a));
  Spinner shiftSp=new Spinner(this);
  ArrayList<String> shiftLabs=new ArrayList<>();
  shiftLabs.add("— none —"); shiftLabs.add("A"); shiftLabs.add("B"); shiftLabs.add("C"); shiftLabs.add("General");
  shiftSp.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,shiftLabs));
  shiftSp.setSelection(0);
add(d);add(Ui.muted(this,"Product"));add(p);
add(Ui.muted(this,"Shift (optional)"));add(shiftSp);
add(Ui.muted(this,"Batch Count"));add(q);
d.setTextSize(15);q.setTextSize(15);
d.setPadding(dp(12),dp(12),dp(12),dp(12));
q.setPadding(dp(12),dp(12),dp(12),dp(12));
p.setPadding(dp(8),dp(8),dp(8),dp(8));
shiftSp.setPadding(dp(8),dp(8),dp(8),dp(8));
  Button save=btn("Save Batch Count");
save.setTextSize(15);
save.setAllCaps(false);
save.setPadding(dp(12),dp(10),dp(12),dp(10));save.setOnClickListener(v->{try{String label=String.valueOf(p.getSelectedItem());if(label==null||label.equals("null"))throw new Exception("Product required");String code=label.split(" — ")[0];
    int pos=shiftSp.getSelectedItemPosition();
    int sh=(pos<=0)?0:pos; // 0=none, 1=A, 2=B, 3=C, 4=General
    int bc=batchCount(q.getText().toString());if(bc<0)throw new Exception("Batch Count cannot be negative");
    String day=ymd(d);if(db.monthLocked(day))throw new Exception("Month locked");
    ContentValues z=new ContentValues();z.put("date",day);z.put("product_code",Integer.parseInt(code));z.put("shift",sh);z.put("qty",bc);z.put("saved_at",new Date().toString());
    JSONObject existing=db.one("SELECT id FROM batches WHERE date=? AND product_code=? AND shift=?",new String[]{day,code,String.valueOf(sh)});long batchId;if(existing==null){batchId=db.insert("batches",z);}else{batchId=existing.getLong("id");db.update("batches",z,"id=?",new String[]{existing.getString("id")});}syncBatchLog(batchId,ymd(d),Integer.parseInt(code),sh,bc,new Date().toString());
    try{
        if("client".equals(db.setting("sync_mode","off"))){
            org.json.JSONObject payload=new org.json.JSONObject();
            payload.put("date",day);
            payload.put("product_code",Integer.parseInt(code));
            payload.put("shift",sh);
            payload.put("batches",bc);
            payload.put("batch_qty",bc);
            payload.put("batch_id",batchId);
            String pname="";
            try{ org.json.JSONObject pr=db.one("SELECT name FROM products WHERE code=?",new String[]{code}); if(pr!=null) pname=pr.optString("name"); }catch(Exception ignored){}
            payload.put("product_name",pname);
            SyncHub.get(this).enqueue("batch", payload);
            // also push recipe consumption lines as batch_lines optional: skip heavy
        }
    }catch(Exception ignored){}
    toast("Batch Count saved ✔");showBatch();
  }catch(Exception e){error(e);}});add(save);
  String batchRange=db.setting("batch_list_range","7");
  if(!"today".equals(batchRange)) batchRange="7";
  final String batchRangeF=batchRange;
  LinearLayout br=new LinearLayout(this);
  br.setOrientation(LinearLayout.HORIZONTAL);
  br.setPadding(0,dp(6),0,dp(6));
  Button bt=btn("today".equals(batchRangeF)?"● Today":"Today");
  Button b7b=btn("7".equals(batchRangeF)?"● Last 7 days":"Last 7 days");
  bt.setOnClickListener(vv->{ db.setSetting("batch_list_range","today"); showBatch(); });
  b7b.setOnClickListener(vv->{ db.setSetting("batch_list_range","7"); showBatch(); });
  br.addView(bt,new LinearLayout.LayoutParams(0,dp(48),1));
  br.addView(b7b,new LinearLayout.LayoutParams(0,dp(48),1));
  add(br);
  String batchFrom="today".equals(batchRangeF)?today():PlanLogic.shiftDate(today(),-6);
  add(title("Saved Batch Counts — "+("today".equals(batchRangeF)?"Today":"Last 7 days")));
  for(JSONObject x:db.rows("SELECT b.id,b.date,b.product_code,p.name,b.shift,b.qty FROM batches b LEFT JOIN products p ON p.code=b.product_code WHERE b.date>=? ORDER BY b.date DESC,b.shift ASC,b.id DESC LIMIT 200",new String[]{batchFrom})){
    LinearLayout r=row();TextView t=new TextView(this);t.setText(x.optString("date")+" | "+x.optString("name")+" | Shift "+shiftLabel(x.optInt("shift",0))+" | Batch Count: "+batchCountText(x.optDouble("qty",0)));t.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));r.addView(t);
    Button e=btn("Edit");e.setOnClickListener(v->{
      try{
        d.setText(x.optString("date"));
        q.setText(batchCountText(x.optDouble("qty",0)));
        String pc=x.optString("product_code");
        for(int i=0;i<a.size();i++) if(a.get(i).startsWith(pc+" — ")||a.get(i).startsWith(pc+" —")){p.setSelection(i);break;}
        int sh=x.optInt("shift",0);
        if(sh>=0 && sh<shiftSp.getCount()) shiftSp.setSelection(sh); else shiftSp.setSelection(0);
        save.setText("Update Batch #"+x.optString("id"));
        final String editId=x.optString("id");
        save.setOnClickListener(sv->{
          try{
            String label=String.valueOf(p.getSelectedItem());
            if(label==null||label.equals("null"))throw new Exception("Product required");
            String code=label.split(" — ")[0];
            int pos=shiftSp.getSelectedItemPosition();
            int sh2=(pos<=0)?0:pos;
            int bc=batchCount(q.getText().toString());
            if(bc<0)throw new Exception("Batch Count cannot be negative");
            String day=ymd(d);
            if(db.monthLocked(day))throw new Exception("Month locked");
            ContentValues z=new ContentValues();
            z.put("date",day);z.put("product_code",Integer.parseInt(code));z.put("shift",sh2);z.put("qty",bc);z.put("saved_at",new Date().toString());
            db.update("batches",z,"id=?",new String[]{editId});
            syncBatchLog(Long.parseLong(editId),day,Integer.parseInt(code),sh2,bc,new Date().toString());
            toast("Batch updated ✔");
            showBatch();
          }catch(Exception ex){error(ex);}
        });
        scrollFormTop();
        q.requestFocus();
      }catch(Exception ex){error(ex);}
    });r.addView(e,new LinearLayout.LayoutParams(dp(90),dp(52)));
    Button dl=Ui.secondary(this,"Delete");dl.setTextColor(Ui.BAD);dl.setOnClickListener(v->Guard.run(this,db,"Delete batch count",true,()->new AlertDialog.Builder(this).setTitle("Delete this batch count?").setMessage(x.optString("date")+" | "+x.optString("name")+" | Shift "+x.optString("shift")+"\nIts raw material use is removed from stock too.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(dd,ww)->{db.delete("mixing2_batch_log","batch_id=?",new String[]{x.optString("id")});db.delete("batches","id=?",new String[]{x.optString("id")});toast("Batch count deleted");showBatch();}).show()));
    r.addView(dl,Ui.lp(this,dp(90),dp(52),0,6,0,0,0));add(r);
  }
 }catch(Exception e){error(e);}}
 private String batchCountText(double n){return String.valueOf((int)Math.round(n));}
 private int batchCount(String s)throws Exception{String v=s.trim();if(v.isEmpty())throw new Exception("Batch Count required");double n=Double.parseDouble(v);if(n!=Math.rint(n))throw new Exception("Batch Count must be a whole number");if(n>2147483647)throw new Exception("Batch Count too large");return (int)n;}
 private void syncBatchLog(long batchId,String date,int productCode,int shift,int batchCount,String savedAt)throws Exception{db.delete("mixing2_batch_log","batch_id=?",new String[]{String.valueOf(batchId)});for(JSONObject rm:db.rows("SELECT rm_code,qty FROM recipes WHERE product_code=? AND qty<>0 ORDER BY rm_code",new String[]{String.valueOf(productCode)})){ContentValues l=new ContentValues();l.put("batch_id",batchId);l.put("date",date);l.put("product_code",productCode);l.put("shift",shift);l.put("rm_code",rm.getString("rm_code"));l.put("recipe_qty",rm.getDouble("qty"));l.put("batch_count",batchCount);l.put("consumption",batchCount*rm.getDouble("qty"));l.put("created_at",savedAt);db.insert("mixing2_batch_log",l);}}
 private void editBatchCount(JSONObject x){
  EditText bc=edit("Batch Count");bc.setText(batchCountText(x.optDouble("qty",0)));
  new AlertDialog.Builder(this).setTitle("Edit Shift Batch Count")
   .setMessage("এটি এই Shift-এ হওয়া মোট Batch-এর সংখ্যা। ৪ → ৫ করলে পরবর্তী একই calculation logic-এ ৫ ব্যবহার হবে।")
   .setView(bc).setNegativeButton("Cancel",null).setPositiveButton("Save Edit",(dialog,which)->{try{int n=batchCount(bc.getText().toString());ContentValues z=new ContentValues();z.put("qty",n);z.put("saved_at",new Date().toString());db.update("batches",z,"id=?",new String[]{x.getString("id")});syncBatchLog(x.getLong("id"),x.getString("date"),x.getInt("product_code"),x.getInt("shift"),n,new Date().toString());toast("Batch Count updated ✔");showBatch();}catch(Exception e){error(e);}}).show();
 }
 private int findSpinnerIndex(Spinner p,String code){for(int i=0;i<p.getCount();i++){String s=String.valueOf(p.getItemAtPosition(i));if(s.startsWith(code+" — "))return i;}return 0;}
 private void showCustom(){
    clear("Custom Sheets");
    if(!admin()){toast("Admin/Editor only");return;}
    try{
        LinearLayout intro=Ui.card(this,Ui.CARD);
        intro.addView(Ui.text(this,"Factory sheets",17,Color.BLACK,true));
        intro.addView(Ui.muted(this,"Templates for daily use. Formulas can use live stock: rm_stock(\"101\"), today_receive(\"101\"), today_use(\"101\"), today_batch(\"5\"). Export CSV from any open sheet."));
        add(intro);

        LinearLayout tpl=Ui.card(this,Ui.CARD);
        tpl.addView(Ui.text(this,"Ready templates",15,Ui.NAVY_TEXT,true));
        String[][] templates={
            {"Daily production log","prodlog"},
            {"RM issue / return","rmissue"},
            {"QA checklist","qa"},
            {"Wastage detail","waste"},
            {"Shift handover","handover"}
        };
        for(String[] t:templates){
            final String key=t[1];
            Button b=Ui.secondary(this,"+ "+t[0]);
            b.setOnClickListener(v->Guard.run(this,db,"Create template sheet",true,()->createSheetTemplate(key)));
            tpl.addView(b);
        }
        add(tpl);

        LinearLayout create=Ui.card(this,Ui.CARD);
        create.addView(Ui.text(this,"Blank sheet",15,Ui.NAVY_TEXT,true));
        final EditText name=edit("Sheet Name"),desc=edit("Description");
        create.addView(name);create.addView(desc);
        Button mk=Ui.primary(this,"Create blank sheet");
        mk.setOnClickListener(v->{
            try{
                String id="S"+System.currentTimeMillis(),sn=name.getText().toString().trim();
                if(sn.isEmpty())throw new Exception("Sheet name required");
                if(db.one("SELECT id FROM custom_sheets WHERE lower(name)=lower(?)",new String[]{sn})!=null)
                    throw new Exception("Duplicate Sheet Name");
                ContentValues x=new ContentValues();
                x.put("id",id);x.put("name",sn);x.put("description",desc.getText().toString().trim());x.put("access","owner");
                db.insert("custom_sheets",x);
                showCustomDefinition(id,sn);
            }catch(Exception e){error(e);}
        });
        create.addView(mk);
        add(create);

        LinearLayout list=Ui.card(this,Ui.CARD);
        list.addView(Ui.text(this,"Your sheets",15,Ui.NAVY_TEXT,true));
        List<JSONObject> sheets=db.rows("SELECT * FROM custom_sheets ORDER BY name",null);
        if(sheets.isEmpty()) list.addView(Ui.muted(this,"No sheets yet — pick a template above."));
        for(JSONObject sh:sheets){
            final String sid=sh.optString("id"),sname=sh.optString("name");
            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(0,dp(10),0,dp(10));
            card.addView(Ui.text(this,sname,15,Color.BLACK,true));
            String d0=sh.optString("description");
            if(!d0.isEmpty()) card.addView(Ui.muted(this,d0));
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            Button open=Ui.primary(this,"Open");
            open.setOnClickListener(v->openCustom(sid,sname));
            Button edit=Ui.secondary(this,"Columns");
            edit.setOnClickListener(v->showCustomDefinition(sid,sname));
            Button del=Ui.secondary(this,"Delete");
            del.setTextColor(Ui.BAD);
            del.setOnClickListener(v->Guard.run(this,db,"Delete custom sheet",true,()->{
                db.delete("custom_rows","sheet_id=?",new String[]{sid});
                db.delete("custom_cols","sheet_id=?",new String[]{sid});
                db.delete("custom_sheets","id=?",new String[]{sid});
                toast("Deleted");
                showCustom();
            }));
            row.addView(open,new LinearLayout.LayoutParams(0,dp(48),1));
            row.addView(edit,new LinearLayout.LayoutParams(0,dp(48),1));
            row.addView(del,new LinearLayout.LayoutParams(0,dp(48),1));
            card.addView(row);
            View div=new View(this);div.setBackgroundColor(Ui.LINE);
            list.addView(card);
            list.addView(div,new LinearLayout.LayoutParams(-1,1));
        }
        add(list);
    }catch(Exception e){error(e);}
 }

 private void createSheetTemplate(String key){
    try{
        String title, desc;
        String[][] cols;
        if("prodlog".equals(key)){
            title="Daily production log"; desc="Date, product, batch, shift, note";
            cols=new String[][]{{"Date","date",""},{"Product","product_dropdown",""},{"Batch","number",""},{"Shift","text",""},{"Note","text",""}};
        }else if("rmissue".equals(key)){
            title="RM issue / return"; desc="RM movement log";
            cols=new String[][]{{"Date","date",""},{"RM","rm_dropdown",""},{"Qty","number",""},{"Type","text",""},{"Note","text",""}};
        }else if("qa".equals(key)){
            title="QA checklist"; desc="OK/Fail checks";
            cols=new String[][]{{"Date","date",""},{"Item","text",""},{"Result","text",""},{"Remark","text",""}};
        }else if("waste".equals(key)){
            title="Wastage detail"; desc="Wastage by RM";
            cols=new String[][]{{"Date","date",""},{"RM","rm_dropdown",""},{"Qty","number",""},{"Reason","text",""}};
        }else if("handover".equals(key)){
            title="Shift handover"; desc="Shift notes";
            cols=new String[][]{{"Date","date",""},{"Shift","text",""},{"Stock note","text",""},{"Pending","text",""}};
        }else return;
        String base=title;
        String sn=base;
        int n=2;
        while(db.one("SELECT id FROM custom_sheets WHERE lower(name)=lower(?)",new String[]{sn})!=null){
            sn=base+" ("+n+")"; n++;
        }
        String id="S"+System.currentTimeMillis();
        ContentValues x=new ContentValues();
        x.put("id",id);x.put("name",sn);x.put("description",desc);x.put("access","owner");
        db.insert("custom_sheets",x);
        int pos=1;
        for(String[] c:cols){
            ContentValues col=new ContentValues();
            col.put("sheet_id",id);col.put("name",c[0]);col.put("type",c[1]);col.put("formula",c[2]);col.put("position",pos++);
            db.insert("custom_cols",col);
        }
        toast("Template created ✔");
        openCustom(id,sn);
    }catch(Exception e){error(e);}
 }

 private void showCustomDefinition(String id,String currentName){clear("Sheet Definition — "+currentName);try{JSONObject sh=db.one("SELECT * FROM custom_sheets WHERE id=?",new String[]{id});if(sh==null)throw new Exception("Sheet not found");EditText nm=edit("Sheet Name");nm.setText(sh.optString("name"));EditText ds=edit("Description");ds.setText(sh.optString("description"));add(nm);add(ds);Button saveHead=btn("Save Sheet Name / Description");saveHead.setOnClickListener(v->{try{String nn=nm.getText().toString().trim();if(nn.isEmpty())throw new Exception("Sheet name required");JSONObject dup=db.one("SELECT id FROM custom_sheets WHERE lower(name)=lower(?) AND id<>?",new String[]{nn,id});if(dup!=null)throw new Exception("Duplicate Sheet Name");ContentValues x=new ContentValues();x.put("name",nn);x.put("description",ds.getText().toString().trim());db.update("custom_sheets",x,"id=?",new String[]{id});toast("Sheet definition saved ✔");showCustomDefinition(id,nn);}catch(Exception e){error(e);}});add(saveHead);Button addCol=btn("+ Add Column");addCol.setOnClickListener(v->columnDialog(id,-1));add(addCol);List<JSONObject> cs=db.rows("SELECT * FROM custom_cols WHERE sheet_id=? ORDER BY position",new String[]{id});for(JSONObject c:cs){long cid=c.getLong("id");LinearLayout r=row();TextView lab=new TextView(this);lab.setText(c.optString("position")+"  "+c.optString("name")+"  ["+c.optString("type")+"]"+(c.optString("formula").isEmpty()?"":"  "+c.optString("formula")));r.addView(lab,new LinearLayout.LayoutParams(0,dp(58),1));Button ed=btn("Edit");ed.setOnClickListener(v->columnDialog(id,cid));r.addView(ed,new LinearLayout.LayoutParams(dp(78),dp(52)));Button up=btn("↑");up.setOnClickListener(v->{moveColumn(cid,id,-1);});r.addView(up,new LinearLayout.LayoutParams(dp(45),dp(52)));Button dn=btn("↓");dn.setOnClickListener(v->{moveColumn(cid,id,1);});r.addView(dn,new LinearLayout.LayoutParams(dp(45),dp(52)));Button del=btn("Delete");del.setOnClickListener(v->{new AlertDialog.Builder(this).setTitle("Delete Column?").setMessage("Existing row values for this column will no longer be shown.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(a,b)->{db.delete("custom_cols","id=?",new String[]{String.valueOf(cid)});repositionColumns(id);showCustomDefinition(id,nm.getText().toString());}).show();});r.addView(del,new LinearLayout.LayoutParams(dp(82),dp(52)));add(r);}Button open=btn("Open Sheet Rows");open.setOnClickListener(v->openCustom(id,nm.getText().toString()));add(open);Button delSheet=btn("Delete Sheet");delSheet.setOnClickListener(v->{new AlertDialog.Builder(this).setTitle("Delete Sheet?").setMessage("This removes the sheet definition and its rows permanently.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(a,b)->{try{db.getWritableDatabase().beginTransaction();db.delete("custom_rows","sheet_id=?",new String[]{id});db.delete("custom_cols","sheet_id=?",new String[]{id});db.delete("custom_sheets","id=?",new String[]{id});db.getWritableDatabase().setTransactionSuccessful();db.getWritableDatabase().endTransaction();toast("Sheet deleted ✔");showCustom();}catch(Exception e){try{db.getWritableDatabase().endTransaction();}catch(Exception ignored){}error(e);}}).show();});add(delSheet);}catch(Exception e){error(e);}}
 private void columnDialog(String sheetId,long colId){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),0,dp(12),0);EditText name=edit("Column Name"),formula=edit("Formula e.g. =B2*C2 or =SUM(D2:F2) or =IF(E2<0,\"SHORTAGE\",\"OK\")");Spinner type=new Spinner(this);String[] types={"text","number","date","formula","rm_dropdown","product_dropdown"};type.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,types));box.addView(name);box.addView(type);box.addView(formula);try{if(colId>0){JSONObject c=db.one("SELECT * FROM custom_cols WHERE id=?",new String[]{String.valueOf(colId)});if(c!=null){name.setText(c.optString("name"));formula.setText(c.optString("formula"));String ct=c.optString("type","text");for(int i=0;i<types.length;i++)if(types[i].equals(ct))type.setSelection(i);}}}catch(Exception e){error(e);return;}new AlertDialog.Builder(this).setTitle(colId>0?"Edit Column":"Add Column").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{try{String cn=name.getText().toString().trim();String ct=types[type.getSelectedItemPosition()];String f=formula.getText().toString().trim();if(cn.isEmpty())throw new Exception("Column name required");if(ct.equals("formula")&&f.isEmpty())throw new Exception("Formula required for formula column");JSONObject dup=db.one("SELECT id FROM custom_cols WHERE sheet_id=? AND lower(name)=lower(?) AND id<>?",new String[]{sheetId,cn,String.valueOf(colId)});if(dup!=null)throw new Exception("Duplicate Column Name");ContentValues x=new ContentValues();x.put("sheet_id",sheetId);x.put("name",cn);x.put("type",ct);x.put("formula",ct.equals("formula")?f:"");if(colId>0){JSONObject oldCol=db.one("SELECT name FROM custom_cols WHERE id=?",new String[]{String.valueOf(colId)});String oldName=oldCol==null?cn:oldCol.optString("name");db.update("custom_cols",x,"id=?",new String[]{String.valueOf(colId)});if(!oldName.equals(cn)){for(JSONObject rr:db.rows("SELECT id,row_json FROM custom_rows WHERE sheet_id=?",new String[]{sheetId})){JSONObject jo=new JSONObject(rr.optString("row_json","{}"));if(jo.has(oldName)){Object val=jo.remove(oldName);jo.put(cn,val);ContentValues rv=new ContentValues();rv.put("row_json",jo.toString());db.update("custom_rows",rv,"id=?",new String[]{rr.getString("id")});}}}}else{x.put("position",nextColumnPosition(sheetId));db.insert("custom_cols",x);}toast("Column saved ✔");showCustomDefinition(sheetId,db.one("SELECT name FROM custom_sheets WHERE id=?",new String[]{sheetId}).optString("name"));}catch(Exception e){error(e);}}).show();}
 private int nextColumnPosition(String id){try{JSONObject x=db.one("SELECT COALESCE(MAX(position),-1)+1 p FROM custom_cols WHERE sheet_id=?",new String[]{id});return x==null?0:x.getInt("p");}catch(Exception e){return 0;}}
 private void repositionColumns(String id){try{List<JSONObject> cs=db.rows("SELECT id FROM custom_cols WHERE sheet_id=? ORDER BY position,id",new String[]{id});for(int i=0;i<cs.size();i++){ContentValues x=new ContentValues();x.put("position",i);db.update("custom_cols",x,"id=?",new String[]{cs.get(i).getString("id")});}}catch(Exception ignored){}}

 private void exportCustomCsv(String id,String name){
    try{
        List<JSONObject> cols=db.rows("SELECT * FROM custom_cols WHERE sheet_id=? ORDER BY position",new String[]{id});
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<cols.size();i++){
            if(i>0)sb.append(',');
            sb.append('"').append(cols.get(i).optString("name").replace("\"","\"\"")).append('"');
        }
        sb.append('\n');
        for(JSONObject rr:db.rows("SELECT * FROM custom_rows WHERE sheet_id=? ORDER BY id",new String[]{id})){
            JSONObject data=new JSONObject(rr.optString("row_json","{}"));
            for(int i=0;i<cols.size();i++){
                if(i>0)sb.append(',');
                String key=cols.get(i).optString("name");
                String val=data.optString(key,"");
                if("formula".equals(cols.get(i).optString("type")))
                    val=calcFormula(cols.get(i).optString("formula"),data,cols);
                sb.append('"').append(val.replace("\"","\"\"")).append('"');
            }
            sb.append('\n');
        }
        String safe=name.replaceAll("[^a-zA-Z0-9_]","_");
        File f=new File(getCacheDir(),"sheet_"+safe+".csv");
        FileOutputStream out=new FileOutputStream(f);
        out.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.close();
        Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);
        Intent share=new Intent(Intent.ACTION_SEND);
        share.setType("text/csv");
        share.putExtra(Intent.EXTRA_STREAM,uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share,"Share sheet CSV"));
    }catch(Exception e){error(e);}
 }

 private void openCustom(String id,String name){
     clear(name);
     try{
         List<JSONObject> cols=db.rows("SELECT * FROM custom_cols WHERE sheet_id=? ORDER BY position",new String[]{id});
         LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);
         Button def=btn("Columns");
         def.setOnClickListener(v->showCustomDefinition(id,name));
         Button exp=Ui.secondary(this,"Export CSV");
         exp.setOnClickListener(v->exportCustomCsv(id,name));
         top.addView(def,new LinearLayout.LayoutParams(0,dp(48),1));
         top.addView(exp,new LinearLayout.LayoutParams(0,dp(48),1));
         add(top);
         HorizontalScrollView hs=new HorizontalScrollView(this);
         TableLayout table=new TableLayout(this);TableRow h=new TableRow(this);
         for(JSONObject c:cols)h.addView(tv(c.getString("name"),1));
         h.addView(tv("Action",1));table.addView(h);

         for(JSONObject rr:db.rows("SELECT * FROM custom_rows WHERE sheet_id=? ORDER BY id",new String[]{id})){
             JSONObject data=new JSONObject(rr.optString("row_json","{}"));
             TableRow tr=new TableRow(this);ArrayList<View> fields=new ArrayList<>();

             for(JSONObject c:cols){
                 String key=c.optString("name"),ct=c.optString("type","text");
                 View fieldView;
                 if(ct.equals("rm_dropdown")||ct.equals("product_dropdown")){
                     String sql=ct.equals("rm_dropdown")
                         ?"SELECT code,name FROM rms ORDER BY code"
                         :"SELECT code,name FROM products ORDER BY code";
                     List<String> opts=new ArrayList<>();
                     for(JSONObject item:db.rows(sql,null))
                         opts.add(item.optString("code")+" — "+item.optString("name"));
                     if(opts.isEmpty())opts.add("No items available");

                     Spinner sp=new Spinner(this);
                     sp.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,opts));
                     String old=data.optString(key,"");
                     for(int j=0;j<opts.size();j++){
                         if(opts.get(j).equals(old)||opts.get(j).startsWith(old+" — ")){
                             sp.setSelection(j);break;
                         }
                     }
                     fieldView=sp;
                 }else{
                     EditText e=edit("");
                     e.setText(data.optString(key,""));
                     if(ct.equals("formula")){
                         e.setEnabled(false);
                         e.setText(calcFormula(c.optString("formula"),data,cols));
                     }
                     fieldView=e;
                 }
                 fields.add(fieldView);
                 tr.addView(fieldView,new TableRow.LayoutParams(dp(150),dp(52)));
             }

             long rid=rr.optLong("id");
             Button save=btn("Save");
             save.setOnClickListener(v->{
                 try{
                     JSONObject out=new JSONObject();
                     for(int i=0;i<cols.size();i++){
                         JSONObject c=cols.get(i);
                         String ct=c.optString("type");
                         if(ct.equals("formula"))continue;
                         View fv=fields.get(i);String val;
                         if(fv instanceof Spinner){
                             Object selected=((Spinner)fv).getSelectedItem();
                             String label=selected==null?"":selected.toString();
                             val=label.equals("No items available")?"":
                                 (label.contains(" — ")?label.substring(0,label.indexOf(" — ")):label);
                         }else{
                             val=((EditText)fv).getText().toString();
                         }
                         out.put(c.getString("name"),val);
                     }
                     for(int pass=0;pass<cols.size()+1;pass++){
                         for(JSONObject c:cols){
                             if(c.optString("type").equals("formula"))
                                 out.put(c.getString("name"),
                                     calcFormula(c.optString("formula"),out,cols));
                         }
                     }
                     ContentValues values=new ContentValues();
                     values.put("row_json",out.toString());
                     db.update("custom_rows",values,"id=?",new String[]{String.valueOf(rid)});
                     toast("Row saved ✔");openCustom(id,name);
                 }catch(Exception e){error(e);}
             });
             tr.addView(save);

             Button delRow=btn("Delete");
             delRow.setOnClickListener(v->new AlertDialog.Builder(this)
                 .setTitle("Delete Row?")
                 .setNegativeButton("Cancel",null)
                 .setPositiveButton("Delete",(d,w)->{
                     db.delete("custom_rows","id=?",new String[]{String.valueOf(rid)});
                     openCustom(id,name);
                 }).show());
             tr.addView(delRow);table.addView(tr);
         }
         hs.addView(table);add(hs);

         Button addrow=btn("+ Add Row");
         addrow.setOnClickListener(v->{
             try{
                 ContentValues values=new ContentValues();
                 values.put("sheet_id",id);
                 values.put("row_json",new JSONObject().toString());
                 db.insert("custom_rows",values);
                 openCustom(id,name);
             }catch(Exception e){error(e);}
         });
         add(addrow);
     }catch(Exception e){error(e);}
 }
 
 private String replaceLiveToken(String f,String name,int kind){
    try{
        java.util.regex.Pattern pat=java.util.regex.Pattern.compile("(?i)"+name+"\\(\\s*[\"']?([^\"')]+)[\"']?\\s*\\)");
        java.util.regex.Matcher m=pat.matcher(f);
        StringBuffer sb=new StringBuffer();
        while(m.find()){
            String code=m.group(1).trim();
            double v=0;
            if(kind==0)v=liveRmClosing(code);
            else if(kind==1)v=liveTodaySum("receives","qty","rm_code",code);
            else if(kind==2)v=liveTodaySum("mixing2_batch_log","consumption","rm_code",code);
            else if(kind==3)v=liveTodayBatch(code);
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(fmt(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }catch(Exception e){return f;}
 }
 private double liveRmClosing(String code){
    try{
        JSONObject rm=db.one("SELECT opening FROM rms WHERE code=?",new String[]{code});
        double open=rm==null?0:rm.optDouble("opening",0);
        JSONObject r=db.one("SELECT COALESCE(SUM(qty),0) q FROM receives WHERE rm_code=?",new String[]{code});
        JSONObject u=db.one("SELECT COALESCE(SUM(consumption),0) q FROM mixing2_batch_log WHERE rm_code=?",new String[]{code});
        return open+(r==null?0:r.optDouble("q"))-(u==null?0:u.optDouble("q"));
    }catch(Exception e){return 0;}
 }
 private double liveTodaySum(String table,String col,String codeCol,String code){
    try{
        JSONObject r=db.one("SELECT COALESCE(SUM("+col+"),0) q FROM "+table+" WHERE "+codeCol+"=? AND date=?",new String[]{code,today()});
        return r==null?0:r.optDouble("q");
    }catch(Exception e){return 0;}
 }
 private double liveTodayBatch(String productCode){
    try{
        JSONObject r=db.one("SELECT COALESCE(SUM(qty),0) q FROM batches WHERE product_code=? AND date=?",new String[]{productCode,today()});
        return r==null?0:r.optDouble("q");
    }catch(Exception e){return 0;}
 }

private String calcFormula(String formula,JSONObject row,List<JSONObject> cols){try{String f=formula.trim();if(f.startsWith("="))f=f.substring(1).trim();
        f=replaceLiveToken(f,"rm_stock",0);
        f=replaceLiveToken(f,"today_receive",1);
        f=replaceLiveToken(f,"today_use",2);
        f=replaceLiveToken(f,"today_batch",3);
java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)^SUM\\(([^)]+)\\)$").matcher(f);if(m.find()){double sum=0;for(String ref:expandRange(m.group(1),cols))sum+=cellValue(ref,row,cols);return fmt(sum);}m=java.util.regex.Pattern.compile("(?i)^IF\\((.*?),(.*?),(.*?)\\)$").matcher(f);if(m.find()){String cond=m.group(1).trim(),a=stripQuote(m.group(2).trim()),b=stripQuote(m.group(3).trim());return evalCondition(cond,row,cols)?a:b;}String op=findOperator(f);if(op!=null){String[] z=f.split(java.util.regex.Pattern.quote(op),2);double a=cellValue(z[0].trim(),row,cols),b=cellValue(z[1].trim(),row,cols);if(op.equals("+"))return fmt(a+b);if(op.equals("-"))return fmt(a-b);if(op.equals("*"))return fmt(a*b);if(op.equals("/"))return b==0?"0":fmt(a/b);}return row.optString(f,f);}catch(Exception e){return "#ERR";}}
 private String findOperator(String s){for(String op:new String[]{"+","-","*","/"}){int p=s.indexOf(op,1);if(p>0)return op;}return null;}
 private boolean evalCondition(String c,JSONObject row,List<JSONObject> cols){for(String op:new String[]{">=","<=","!=","=","<",">"}){int p=c.indexOf(op);if(p>0){double a=cellValue(c.substring(0,p).trim(),row,cols),b=cellValue(c.substring(p+op.length()).trim(),row,cols);if(op.equals(">="))return a>=b;if(op.equals("<="))return a<=b;if(op.equals("!="))return a!=b;if(op.equals("="))return a==b;if(op.equals("<"))return a<b;return a>b;}}return false;}
 private String stripQuote(String s){return s.length()>=2&&s.startsWith("\"")&&s.endsWith("\"")?s.substring(1,s.length()-1):s;}
 private double cellValue(String ref,JSONObject row,List<JSONObject> cols){try{ref=ref.trim();if(ref.matches("[A-Za-z]+\\d+")){int ci=0;for(char ch:ref.toUpperCase().toCharArray()){if(Character.isLetter(ch))ci=ci*26+(ch-'A'+1);}ci--;if(ci>=0&&ci<cols.size())return num(row.optString(cols.get(ci).getString("name"),"0"));}return Double.parseDouble(ref);}catch(Exception e){return 0;}}
 private List<String> expandRange(String range,List<JSONObject> cols){List<String> out=new ArrayList<>();String[] z=range.trim().split(":");if(z.length!=2){out.add(range.trim());return out;}java.util.regex.Matcher a=java.util.regex.Pattern.compile("([A-Za-z]+)\\d+").matcher(z[0]);java.util.regex.Matcher b=java.util.regex.Pattern.compile("([A-Za-z]+)\\d+").matcher(z[1]);if(!a.find()||!b.find()){out.add(range.trim());return out;}int s=letters(a.group(1)),e=letters(b.group(1));for(int i=Math.min(s,e);i<=Math.max(s,e);i++)out.add(columnName(i)+"1");return out;} private String columnName(int n){StringBuilder x=new StringBuilder();n++;while(n>0){int r=(n-1)%26;x.insert(0,(char)('A'+r));n=(n-1)/26;}return x.toString();}
 private int letters(String s){int n=0;for(char c:s.toUpperCase().toCharArray())n=n*26+(c-'A'+1);return n-1;}
 private void showReports(){
    clear("Reports — Sheets");
    add(Ui.muted(this,"Pick a report · monthly/day sheets · share with boss"));

    EditText month=edit("Month YYYY-MM");
    month.setText(today().substring(0,7));
    add(Ui.muted(this,"Month"));
    add(month);
    add(Ui.muted(this,"Monthly → boss: built offline; internet only when you send."));

    // icon + title + subtitle rows
    String[][] reps={
        {"📨","Send monthly report to boss","Full month summary to owner email / share","boss_m"},
        {"☀️","Send TODAY report to boss","Today only — quick share","boss_d"},
        {"📥","RM Receive Monthly — Day-wise","Receive by RM × each day of month","recv"},
        {"🥣","RM Mixing-2 Monthly — Day-wise","Mixing use by day","mixm"},
        {"🔄","Mixing-2 — Batch × Recipe Usage","Batch log × recipe consumption","mix"},
        {"📝","RM Mixing-2 Batch Log","All mixing batches this month","blog"},
        {"📆","Daily Date Report","One-day RM stock style sheet","daily"},
        {"📊","Final Monthly Usage","Month total usage per RM","fin"},
        {"📑","Export CSV","Raw export for Excel","csv"},
        {"💾","Backup JSON","Full data backup file","backup"},
        {"📥","Restore JSON","Load from backup file","restore"}
    };
    int[] accents={
        Color.rgb(20,55,95), Color.rgb(46,125,50), Color.rgb(21,101,192),
        Color.rgb(123,31,162), Color.rgb(0,150,136), Color.rgb(84,110,122),
        Color.rgb(230,126,34), Color.rgb(25,118,210), Color.rgb(96,125,139),
        Color.rgb(55,71,79), Color.rgb(198,40,40)
    };
    for(int i=0;i<reps.length;i++){
        final String[] it=reps[i];
        final String k=it[3];
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14),dp(12),dp(14),dp(12));
        row.setBackground(Ui.shape(this,Ui.CARD,Ui.LINE,14));
        LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2);
        rlp.setMargins(0,0,0,dp(8));
        TextView chip=Ui.text(this,it[0],18,Color.WHITE,true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10),dp(8),dp(10),dp(8));
        chip.setBackground(Ui.shape(this,accents[i%accents.length],accents[i%accents.length],12));
        row.addView(chip);
        LinearLayout txt=new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(dp(12),0,dp(8),0);
        txt.addView(Ui.text(this,it[1],15,Ui.NAVY_TEXT,true));
        txt.addView(Ui.text(this,it[2],12,Ui.MUTED,false));
        row.addView(txt,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(Ui.text(this,"›",20,Ui.MUTED,true));
        row.setOnClickListener(v->{
            String m=month.getText().toString().trim();
            if(k.equals("boss_m"))sendMonthlyToBoss(m);
            else if(k.equals("boss_d"))sendDailyToBoss(today());
            else if(k.equals("recv"))showReceiveMonthly(m);
            else if(k.equals("mixm"))showMixingMonthlySheet(m);
            else if(k.equals("mix"))showMixing2(m);
            else if(k.equals("blog"))showBatchLogSheet(m);
            else if(k.equals("daily"))showDailyDateSheet(m);
            else if(k.equals("fin"))showFinalMonthlySheet(m);
            else if(k.equals("csv"))exportCsv();
            else if(k.equals("backup"))backupJson();
            else if(k.equals("restore"))restoreJson();
        });
        content.addView(row,rlp);
    }
 }


 private void showReceiveMonthly(String m){if(!validMonth(m)){error(new Exception("Month must be YYYY-MM"));return;}clear("RM Receive Monthly — Day-wise");try{int days=daysInMonth(m);HorizontalScrollView hs=new HorizontalScrollView(this);TableLayout t=new TableLayout(this);TableRow h=new TableRow(this);h.addView(tv("RM",1));for(int d=1;d<=days;d++)h.addView(tv(String.valueOf(d),1));h.addView(tv("Total",1));t.addView(h);for(JSONObject rm:db.rows("SELECT * FROM rms ORDER BY CAST(code AS INTEGER)",null)){TableRow tr=new TableRow(this);tr.addView(tv(rm.optString("code")+" "+rm.optString("name"),1));double total=0;for(int d=1;d<=days;d++){String date=m+"-"+String.format(Locale.US,"%02d",d);double q=receive(date,rm.getString("code"));total+=q;tr.addView(tv(fmt(q),1));}tr.addView(tv(fmt(total),1));t.addView(tr);}hs.addView(t);add(hs);Button pdf=btn("Print / PDF");pdf.setOnClickListener(v->reportReceive(m));add(pdf);}catch(Exception e){error(e);}}
 private void showMixingMonthlySheet(String m){if(!validMonth(m)){error(new Exception("Month must be YYYY-MM"));return;}clear("RM Mixing-2 Monthly — Day-wise");try{int days=daysInMonth(m);HorizontalScrollView hs=new HorizontalScrollView(this);TableLayout t=new TableLayout(this);TableRow h=new TableRow(this);h.addView(tv("RM",1));for(int d=1;d<=days;d++)h.addView(tv(String.valueOf(d),1));h.addView(tv("Total",1));t.addView(h);for(JSONObject rm:db.rows("SELECT * FROM rms ORDER BY CAST(code AS INTEGER)",null)){TableRow tr=new TableRow(this);tr.addView(tv(rm.optString("code")+" "+rm.optString("name"),1));double total=0;for(int d=1;d<=days;d++){String date=m+"-"+String.format(Locale.US,"%02d",d);double q=use(date,rm.getString("code"));total+=q;tr.addView(tv(fmt(q),1));}tr.addView(tv(fmt(total),1));t.addView(tr);}hs.addView(t);add(hs);Button pdf=btn("Print / PDF");pdf.setOnClickListener(v->reportMixingMonthly(m));add(pdf);}catch(Exception e){error(e);}}
 private void showBatchLogSheet(String m){if(!validMonth(m)){error(new Exception("Month must be YYYY-MM"));return;}clear("RM Mixing-2 Batch Log");try{HorizontalScrollView hs=new HorizontalScrollView(this);TableLayout t=new TableLayout(this);TableRow h=new TableRow(this);for(String x:new String[]{"Date","Product","Shift","RM","Recipe","Batches","Consumption"})h.addView(tv(x,1));t.addView(h);for(JSONObject x:db.rows("SELECT l.*,p.name product_name FROM mixing2_batch_log l LEFT JOIN products p ON p.code=l.product_code WHERE l.date LIKE ? ORDER BY l.date,l.product_code,l.shift,l.rm_code",new String[]{m+"-%"})){TableRow tr=new TableRow(this);for(String v:new String[]{x.optString("date"),x.optString("product_name",x.optString("product_code")),x.optString("shift"),x.optString("rm_code"),fmt(x.optDouble("recipe_qty")),fmt(x.optDouble("batch_count")),fmt(x.optDouble("consumption"))})tr.addView(tv(v,1));t.addView(tr);}hs.addView(t);add(hs);Button pdf=btn("Print / PDF");pdf.setOnClickListener(v->reportBatchLog(m));add(pdf);}catch(Exception e){error(e);}}
 private void showDailyDateSheet(String m){
  if(!validMonth(m)){error(new Exception("Month must be YYYY-MM"));return;}
  clear("Daily Date Report");
  try{
   EditText dateInput=dateField("Date");dateInput.setText(today().startsWith(m)?today():m+"-01");add(dateInput);
   Button show=btn("Show Selected Date");add(show);
   Button pdf=btn("Save PDF / Share / Print Selected Date");add(pdf);
   Button monthPdf=btn("Save PDF / Print Full Month");add(monthPdf);
   LinearLayout tableHost=new LinearLayout(this);tableHost.setOrientation(LinearLayout.VERTICAL);add(tableHost);
   Runnable render=()->{try{
    String date=dateInput.getText().toString().trim();
    if(!date.matches("\\d{4}-\\d{2}-\\d{2}")||!date.startsWith(m+"-"))throw new Exception("Enter a valid date in selected month (YYYY-MM-DD)");
    tableHost.removeAllViews();
    HorizontalScrollView hs=new HorizontalScrollView(this);TableLayout t=new TableLayout(this);t.setStretchAllColumns(true);
    TableRow h=new TableRow(this);for(String col:new String[]{"Item Code","Item Name","UoM","Opening","Receive","Use","Closing","Status"})h.addView(tv(col,1));t.addView(h);
    for(JSONObject rm:db.rows("SELECT * FROM rms ORDER BY CAST(code AS INTEGER)",null)){
      String code=rm.getString("code");double o=opening(date,code),rec=receive(date,code),u=use(date,code),c=o+rec-u;
      TableRow tr=new TableRow(this);String status=c<0?"LOW/NEGATIVE":(c==0?"ZERO":"OK");
      for(String val:new String[]{code,rm.optString("name"),rm.optString("uom","KG"),fmt(o),fmt(rec),fmt(u),fmt(c),status})tr.addView(tv(val,1));t.addView(tr);
    }
    hs.addView(t);tableHost.addView(hs,new LinearLayout.LayoutParams(-1,-2));
   }catch(Exception e){error(e);}};
   show.setOnClickListener(v->render.run());pdf.setOnClickListener(v->{try{String date=dateInput.getText().toString().trim();if(!date.matches("\\d{4}-\\d{2}-\\d{2}")||!date.startsWith(m+"-"))throw new Exception("Enter a valid date in selected month (YYYY-MM-DD)");reportDaily(date);}catch(Exception e){error(e);}});
   monthPdf.setOnClickListener(v->reportDailyMonth(m));render.run();
  }catch(Exception e){error(e);}
 }
 private void showFinalMonthlySheet(String m){if(!validMonth(m)){error(new Exception("Month must be YYYY-MM"));return;}clear("Final Monthly Usage");try{TextView monthLabel=tv("Report month: "+m,1);monthLabel.setTextSize(16);monthLabel.setTypeface(null,1);monthLabel.setTextColor(Ui.NAVY_TEXT);add(monthLabel);LinearLayout monthRow=row();EditText monthInput=edit("Select month (YYYY-MM)");monthInput.setInputType(android.text.InputType.TYPE_CLASS_DATETIME|android.text.InputType.TYPE_DATETIME_VARIATION_NORMAL);monthInput.setText(m);monthRow.addView(monthInput,new LinearLayout.LayoutParams(0,-2,1));Button loadMonth=btn("Load Month");monthRow.addView(loadMonth,new LinearLayout.LayoutParams(-2,-2));add(monthRow);loadMonth.setOnClickListener(v->{String chosen=monthInput.getText().toString().trim();if(!validMonth(chosen)){error(new Exception("Enter month as YYYY-MM, for example 2026-10"));return;}showFinalMonthlySheet(chosen);});HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(true);hs.setFillViewport(false);TableLayout t=new TableLayout(this);t.setStretchAllColumns(false);TableRow h=new TableRow(this);for(String col:new String[]{"Item Code","Item Name","UoM","Opening Balance","Total Receive","Total Use","Closing Balance","Status"}){TextView cell=tv(col,1);cell.setMinWidth(dp(100));cell.setPadding(dp(8),dp(8),dp(8),dp(8));h.addView(cell);}t.addView(h);for(JSONObject rm:db.rows("SELECT * FROM rms ORDER BY CAST(code AS INTEGER)",null)){String code=rm.getString("code");double o=opening(m+"-01",code),rec=monthlyReceive(m,code),u=monthlyUse(m,code),cl=o+rec-u;String status=cl<0?"LOW/NEGATIVE":(cl==0?"ZERO":"OK");TableRow tr=new TableRow(this);for(String val:new String[]{code,rm.optString("name"),rm.optString("uom","KG"),fmt(o),fmt(rec),fmt(u),fmt(cl),status}){TextView cell=tv(val,1);cell.setMinWidth(dp(100));cell.setPadding(dp(8),dp(8),dp(8),dp(8));tr.addView(cell);}t.addView(tr);}hs.addView(t);add(hs);Button pdf=btn("Save PDF / Share / Print");pdf.setOnClickListener(v->reportMonthly(m));add(pdf);Button excel=btn("Export Excel-compatible CSV");excel.setOnClickListener(v->exportMonthlyCsv(m));add(excel);Button finalize=btn("Finalize Month (Admin)");finalize.setOnClickListener(v->finalizeMonth(m));add(finalize);Button snapshot=btn("View Saved Finalized Snapshot");snapshot.setOnClickListener(v->viewFinalizedSnapshot(m));add(snapshot);String saved=db.setting("finalized_month_"+m,"");TextView state=tv(saved.isEmpty()?"Finalized snapshot: Not saved":"Finalized snapshot: Saved (fixed historical snapshot)",1);state.setTextColor(saved.isEmpty()?Ui.MUTED:Ui.GOOD);add(state);}catch(Exception e){error(e);}}
 private void viewFinalizedSnapshot(String month){try{String raw=db.setting("finalized_month_"+month,"");if(raw.isEmpty()){toast("No finalized snapshot saved for "+month);return;}JSONObject snapshot=new JSONObject(raw);StringBuilder out=new StringBuilder("Month: ").append(snapshot.optString("month",month)).append("\nFinalized at: ").append(snapshot.optString("finalized_at","Unknown")).append("\n\nCode | Item | UoM | Opening | Receive | Use | Closing | Status\n");JSONArray items=snapshot.optJSONArray("items");if(items!=null)for(int i=0;i<items.length();i++){JSONObject x=items.optJSONObject(i);if(x==null)continue;out.append(x.optString("code")).append(" | ").append(x.optString("name")).append(" | ").append(x.optString("uom")).append(" | ").append(fmt(x.optDouble("opening"))).append(" | ").append(fmt(x.optDouble("receive"))).append(" | ").append(fmt(x.optDouble("use"))).append(" | ").append(fmt(x.optDouble("closing"))).append(" | ").append(x.optString("status")).append("\n");}new AlertDialog.Builder(this).setTitle("Saved snapshot — "+month).setMessage(out.toString()).setPositiveButton("OK",null).show();}catch(Exception e){error(e);}}
 private void finalizeMonth(String month){new AlertDialog.Builder(this).setTitle("Finalize month "+month).setMessage("Save a fixed snapshot of this month’s opening, receive, use and closing balances? This records the current totals; later entries can still change live reports.").setNegativeButton("Cancel",null).setPositiveButton("Finalize",(d,w)->{try{JSONObject snapshot=new JSONObject();snapshot.put("month",month);snapshot.put("finalized_at",new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new java.util.Date()));JSONArray items=new JSONArray();for(JSONObject rm:db.rows("SELECT * FROM rms ORDER BY CAST(code AS INTEGER)",null)){String code=rm.getString("code");double o=opening(month+"-01",code),rec=monthlyReceive(month,code),u=monthlyUse(month,code),cl=o+rec-u;JSONObject item=new JSONObject();item.put("code",code);item.put("name",rm.optString("name"));item.put("uom",rm.optString("uom","KG"));item.put("opening",o);item.put("receive",rec);item.put("use",u);item.put("closing",cl);item.put("status",cl<0?"LOW/NEGATIVE":(cl==0?"ZERO":"OK"));items.put(item);}snapshot.put("items",items);db.setSetting("finalized_month_"+month,snapshot.toString());new AlertDialog.Builder(this).setTitle("Month finalized").setMessage("Saved snapshot for "+month+". The live reports remain available.").setPositiveButton("OK",null).show();}catch(Exception e){error(e);}}).show();}
 private boolean validMonth(String m){return m!=null&&m.matches("\\d{4}-\\d{2}")&&Integer.parseInt(m.substring(5,7))>=1&&Integer.parseInt(m.substring(5,7))<=12;} private int daysInMonth(String m){if(!validMonth(m))throw new IllegalArgumentException("Month must be YYYY-MM");Calendar c=Calendar.getInstance();c.set(Integer.parseInt(m.substring(0,4)),Integer.parseInt(m.substring(5,7))-1,1);return c.getActualMaximum(Calendar.DAY_OF_MONTH);}
 private String dailyStatus(double closing){return closing<0?"LOW/NEGATIVE":(closing==0?"ZERO":"OK");}
 private void reportDaily(String d){try{ArrayList<String[]> rows=new ArrayList<>();for(JSONObject r:db.rows("SELECT * FROM rms ORDER BY CAST(code AS INTEGER)",null)){String code=r.getString("code");double o=opening(d,code),rec=receive(d,code),u=use(d,code),c=o+rec-u;rows.add(new String[]{code,r.optString("name"),r.optString("uom","KG"),fmt(o),fmt(rec),fmt(u),fmt(c),dailyStatus(c)});}createLandscapeTablePdf("RM STOCK - DAILY DATE REPORT","Date: "+d,new String[]{"Item Code","Item Name","UoM","Opening","Receive","Use","Closing Balance","Status"},rows,"Daily-"+d);}catch(Exception e){error(e);}}
 private void reportDailyMonth(String m){try{ArrayList<String[]> rows=new ArrayList<>();int days=daysInMonth(m);for(int d=1;d<=days;d++){String date=m+"-"+String.format(Locale.US,"%02d",d);for(JSONObject r:db.rows("SELECT * FROM rms ORDER BY CAST(code AS INTEGER)",null)){String code=r.getString("code");double o=opening(date,code),rec=receive(date,code),u=use(date,code),c=o+rec-u;rows.add(new String[]{date,code,r.optString("name"),r.optString("uom","KG"),fmt(o),fmt(rec),fmt(u),fmt(c),dailyStatus(c)});}}createLandscapeTablePdf("RM STOCK - DAILY DATE REPORT - FULL MONTH","Month: "+m,new String[]{"Date","Item Code","Item Name","UoM","Opening","Receive","Use","Closing","Status"},rows,"Daily-Report-"+m);}catch(Exception e){error(e);}}
 private void reportMonthly(String m){try{ArrayList<String[]> rows=new ArrayList<>();for(JSONObject r:db.rows("SELECT * FROM rms ORDER BY CAST(code AS INTEGER)",null)){String code=r.getString("code");double o=opening(m+"-01",code),rec=monthlyReceive(m,code),u=monthlyUse(m,code),cl=o+rec-u;rows.add(new String[]{code,r.optString("name"),r.optString("uom","KG"),fmt(o),fmt(rec),fmt(u),fmt(cl),dailyStatus(cl)});}createLandscapeTablePdf("RM STOCK - FINAL MONTHLY USAGE","Month: "+m,new String[]{"Item Code","Item Name","UoM","Opening Balance","Total Receive","Total Use","Closing Balance","Status"},rows,"Monthly-"+m);}catch(Exception e){error(e);}}
 private void createLandscapeTablePdf(String heading,String subheading,String[] headers,List<String[]> rows,String filename)throws Exception{PdfDocument doc=new PdfDocument();Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));final int pageW=842,pageH=595;final float left=20,right=20;float[] weights=headers.length==4?new float[]{1.1f,2.6f,1.2f,2.4f}:headers.length==6?new float[]{1.0f,2.6f,1.1f,1.1f,1.1f,1.6f}:headers.length==9?new float[]{1.0f,.85f,2.0f,.55f,.9f,.9f,.75f,1.0f,1.0f}:(headers.length==7?new float[]{1.0f,2.0f,.8f,.8f,.8f,1.0f,1.2f}:new float[]{1.0f,2.2f,.65f,1.0f,1.0f,.8f,1.15f,1.25f});float total=0;for(float w:weights)total+=w;float[] widths=new float[weights.length];for(int i=0;i<weights.length;i++)widths[i]=(pageW-left-right)*weights[i]/total;int pageNo=0;PdfDocument.Page page=null;Canvas canvas=null;float y=0;for(int index=-1;index<rows.size();index++){boolean headerRow=index==-1;if(page==null||y>pageH-45){if(page!=null){paint.setTextSize(8);canvas.drawText("Page "+pageNo,pageW-60,pageH-15,paint);doc.finishPage(page);}pageNo++;page=doc.startPage(new PdfDocument.PageInfo.Builder(pageW,pageH,pageNo).create());canvas=page.getCanvas();paint.setTextSize(13);paint.setTypeface(Typeface.DEFAULT_BOLD);canvas.drawText(db.setting("company","RM Stock"),left,20,paint);canvas.drawText(lbl(heading),left,38,paint);paint.setTextSize(10);paint.setTypeface(Typeface.DEFAULT);canvas.drawText(subheading,left,53,paint);y=72;paint.setTextSize(8);paint.setTypeface(Typeface.DEFAULT_BOLD);float x=left;for(int i=0;i<headers.length;i++){canvas.drawText(headers[i],x+2,y,paint);x+=widths[i];}y+=7;canvas.drawLine(left,y,pageW-right,y,paint);y+=15;}if(headerRow)continue;String[] cells=rows.get(index);paint.setTextSize(8);paint.setTypeface(Typeface.DEFAULT);float x=left;for(int i=0;i<cells.length;i++){String value=cells[i]==null?"":cells[i];float maxW=widths[i]-5;while(value.length()>1&&paint.measureText(value+"…")>maxW)value=value.substring(0,value.length()-1);if(paint.measureText(value)>maxW&&value.length()>0)value=value.substring(0,1);if(!value.equals(cells[i])&&value.length()>0)value=value+"…";canvas.drawText(value,x+2,y,paint);x+=widths[i];}canvas.drawLine(left,y+4,pageW-right,y+4,paint);y+=17;}if(page!=null){paint.setTextSize(8);canvas.drawText("Page "+pageNo,pageW-60,pageH-15,paint);doc.finishPage(page);}File dir=new File(getFilesDir(),"reports");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create report directory");File f=new File(dir,filename+".pdf");try(FileOutputStream out=new FileOutputStream(f)){doc.writeTo(out);}finally{doc.close();}shareExistingPdf(f);}
 private void reportReceive(String m){reportDaywiseLandscape(m,true);}
 private double receive(String d,String c)throws Exception{JSONObject x=db.one("SELECT COALESCE(SUM(qty),0) v FROM receives WHERE date=? AND rm_code=?",new String[]{d,c});return x==null?0:x.getDouble("v");}
 private double use(String d,String rm)throws Exception{JSONObject x=db.one("SELECT COALESCE(SUM(consumption),0) v FROM mixing2_batch_log WHERE date=? AND rm_code=?",new String[]{d,rm});return x==null?0:x.getDouble("v");}
 private double monthlyReceive(String m,String c)throws Exception{JSONObject x=db.one("SELECT COALESCE(SUM(qty),0) v FROM receives WHERE date LIKE ? AND rm_code=?",new String[]{m+"-%",c});return x==null?0:x.getDouble("v");}
 private double monthlyUse(String m,String c)throws Exception{Calendar cal=Calendar.getInstance();cal.set(Integer.parseInt(m.substring(0,4)),Integer.parseInt(m.substring(5,7))-1,1);int days=cal.getActualMaximum(Calendar.DAY_OF_MONTH);double s=0;for(int i=1;i<=days;i++)s+=use(m+"-"+String.format(Locale.US,"%02d",i),c);return s;}
 private double opening(String d,String c)throws Exception{
  // Opening is the base/master opening plus every dated movement strictly before this day.
  // This naturally carries closing stock across month/year boundaries instead of resetting on day 1.
  JSONObject base=db.one("SELECT opening FROM rms WHERE code=?",new String[]{c});
  double initial=base==null?0:base.optDouble("opening");
  JSONObject rec=db.one("SELECT COALESCE(SUM(qty),0) v FROM receives WHERE rm_code=? AND date<?",new String[]{c,d});
  JSONObject used=db.one("SELECT COALESCE(SUM(consumption),0) v FROM mixing2_batch_log WHERE rm_code=? AND date<?",new String[]{c,d});
  return initial+(rec==null?0:rec.optDouble("v"))-(used==null?0:used.optDouble("v"));
 }
 
 private void sendMonthlyToBoss(String month){
    try{
        if(!validMonth(month)){toast("Month must be YYYY-MM");return;}
        String company=db.setting("company","Inventory Management");
        String bossName=db.setting("boss_name","Boss");
        String bossEmail=db.setting("boss_email","");
        String bossWa=db.setting("boss_whatsapp","");
        StringBuilder body=new StringBuilder();
        body.append(company).append("\nMONTHLY REPORT — ").append(month).append("\n");
        body.append("For: ").append(bossName).append("\n");
        body.append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date())).append("\n\n");
        JSONObject qr=db.one("SELECT COALESCE(SUM(qty),0) q FROM receives WHERE date LIKE ?",new String[]{month+"-%"});
        JSONObject qu=db.one("SELECT COALESCE(SUM(consumption),0) q FROM mixing2_batch_log WHERE date LIKE ?",new String[]{month+"-%"});
        JSONObject qb=db.one("SELECT COALESCE(SUM(qty),0) q FROM batches WHERE date LIKE ?",new String[]{month+"-%"});
        body.append("SUMMARY\n");
        body.append("  Receive: ").append(fmt(qr==null?0:qr.optDouble("q"))).append("\n");
        body.append("  Mixing use: ").append(fmt(qu==null?0:qu.optDouble("q"))).append("\n");
        body.append("  Batches: ").append(fmt(qb==null?0:qb.optDouble("q"))).append("\n\n");
        body.append("BY RAW MATERIAL (this month receive / use + current closing)\n");
        String lastDay=month+"-"+String.format(Locale.US,"%02d",daysInMonth(month));
        Map<String,Double> recAll=sumMap("SELECT rm_code k,SUM(qty) v FROM receives WHERE date<=? GROUP BY rm_code",new String[]{lastDay});
        Map<String,Double> useAll=sumMap("SELECT rm_code k,SUM(consumption) v FROM mixing2_batch_log WHERE date<=? GROUP BY rm_code",new String[]{lastDay});
        for(JSONObject rm:db.rows("SELECT code,name,uom,opening FROM rms ORDER BY CAST(code AS INTEGER)",null)){
            String code=rm.optString("code");
            JSONObject mr=db.one("SELECT COALESCE(SUM(qty),0) q FROM receives WHERE rm_code=? AND date LIKE ?",new String[]{code,month+"-%"});
            JSONObject mu=db.one("SELECT COALESCE(SUM(consumption),0) q FROM mixing2_batch_log WHERE rm_code=? AND date LIKE ?",new String[]{code,month+"-%"});
            double closing=rm.optDouble("opening",0)+nz(recAll.get(code))-nz(useAll.get(code));
            body.append(code).append("  ").append(rm.optString("name"))
                .append("  rec ").append(fmt(mr==null?0:mr.optDouble("q")))
                .append("  use ").append(fmt(mu==null?0:mu.optDouble("q")))
                .append("  close ").append(fmt(closing)).append(" ").append(rm.optString("uom")).append("\n");
        }
        body.append("\n— Inventory Management\n");
        final String textBody=body.toString();
        final String subject=company+" — Monthly report "+month;
        File pdf=createPdf(textBody,"Boss-Monthly-"+month);
        pendingPdf=pdf;
        final Uri pdfUri=FileProvider.getUriForFile(this,"com.rmstock.nativeapp.fileprovider",pdf);
        new AlertDialog.Builder(this)
            .setTitle("Monthly report ready — "+month)
            .setMessage("Built offline for "+bossName+".\nTurn internet on only to send.\nEmail: "+(bossEmail.isEmpty()?"(not set — set in Settings)":bossEmail)+"\nWhatsApp: "+(bossWa.isEmpty()?"(not set)":bossWa))
            .setPositiveButton("Email", (d,w)->{
                Intent i=new Intent(Intent.ACTION_SEND);
                i.setType("application/pdf");
                if(!bossEmail.isEmpty()) i.putExtra(Intent.EXTRA_EMAIL,new String[]{bossEmail});
                i.putExtra(Intent.EXTRA_SUBJECT,subject);
                i.putExtra(Intent.EXTRA_TEXT,textBody);
                i.putExtra(Intent.EXTRA_STREAM,pdfUri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try{startActivity(Intent.createChooser(i,"Email to boss"));}catch(Exception e){toast("No email app");}
                db.audit(user(),role(),"boss_email",month);
            })
            .setNeutralButton("WhatsApp", (d,w)->{
                try{
                    String msg=textBody.length()>3500?textBody.substring(0,3500)+"…":textBody;
                    if(!bossWa.isEmpty()){
                        startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+bossWa.replace("+","")+"?text="+Uri.encode(msg))));
                    }else{
                        Intent i=new Intent(Intent.ACTION_SEND);
                        i.setType("application/pdf");
                        i.putExtra(Intent.EXTRA_STREAM,pdfUri);
                        i.putExtra(Intent.EXTRA_TEXT,msg);
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try{i.setPackage("com.whatsapp");startActivity(i);}catch(Exception e2){i.setPackage(null);startActivity(Intent.createChooser(i,"WhatsApp"));}
                    }
                    db.audit(user(),role(),"boss_wa",month);
                }catch(Exception e){
                    Intent i=new Intent(Intent.ACTION_SEND);
                    i.setType("application/pdf");
                    i.putExtra(Intent.EXTRA_STREAM,pdfUri);
                    i.putExtra(Intent.EXTRA_TEXT,textBody);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i,"Share to WhatsApp"));
                }
            })
            .setNegativeButton("Share…", (d,w)->{
                Intent i=new Intent(Intent.ACTION_SEND);
                i.setType("application/pdf");
                i.putExtra(Intent.EXTRA_SUBJECT,subject);
                i.putExtra(Intent.EXTRA_TEXT,textBody);
                i.putExtra(Intent.EXTRA_STREAM,pdfUri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i,"Share monthly report"));
                db.audit(user(),role(),"boss_share",month);
            })
            .show();
    }catch(Exception e){error(e);}
 }

 
 private void sendDailyToBoss(String day){
    try{
        if(day==null||day.length()<10){toast("Bad date");return;}
        String company=db.setting("company","Inventory Management");
        String bossName=db.setting("boss_name","Boss");
        String bossEmail=db.setting("boss_email","");
        String bossWa=db.setting("boss_whatsapp","");
        StringBuilder body=new StringBuilder();
        body.append(company).append("\nDAILY REPORT — ").append(day).append("\n");
        body.append("For: ").append(bossName).append("\n");
        body.append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date())).append("\n\n");
        JSONObject qr=db.one("SELECT COALESCE(SUM(qty),0) q FROM receives WHERE date=?",new String[]{day});
        JSONObject qu=db.one("SELECT COALESCE(SUM(consumption),0) q FROM mixing2_batch_log WHERE date=?",new String[]{day});
        JSONObject qb=db.one("SELECT COALESCE(SUM(qty),0) q FROM batches WHERE date=?",new String[]{day});
        body.append("TODAY SUMMARY\n");
        body.append("  Receive: ").append(fmt(qr==null?0:qr.optDouble("q"))).append("\n");
        body.append("  Mixing use: ").append(fmt(qu==null?0:qu.optDouble("q"))).append("\n");
        body.append("  Batches: ").append(fmt(qb==null?0:qb.optDouble("q"))).append("\n\n");
        body.append("RECEIVES TODAY\n");
        for(JSONObject r:db.rows("SELECT r.*,m.name FROM receives r LEFT JOIN rms m ON m.code=r.rm_code WHERE r.date=? ORDER BY r.id",new String[]{day})){
            body.append("  ").append(r.optString("rm_code")).append(" ").append(r.optString("name"))
                .append("  ").append(fmt(r.optDouble("qty"))).append("  ").append(r.optString("voucher_no")).append("\n");
        }
        body.append("\nBATCHES TODAY\n");
        for(JSONObject b:db.rows("SELECT b.*,p.name FROM batches b LEFT JOIN products p ON p.code=b.product_code WHERE b.date=? ORDER BY b.id",new String[]{day})){
            body.append("  ").append(b.optString("name")).append(" shift ").append(b.optString("shift"))
                .append("  qty ").append(fmt(b.optDouble("qty"))).append("\n");
        }
        body.append("\n— Inventory Management\n");
        final String textBody=body.toString();
        final String subject=company+" — Daily report "+day;
        File pdf=createPdf(textBody,"Boss-Daily-"+day);
        pendingPdf=pdf;
        final Uri pdfUri=FileProvider.getUriForFile(this,"com.rmstock.nativeapp.fileprovider",pdf);
        offerBossSend(subject,textBody,pdfUri,bossName,bossEmail,bossWa,"daily_"+day);
    }catch(Exception e){error(e);}
 }
 private void offerBossSend(String subject,String textBody,Uri pdfUri,String bossName,String bossEmail,String bossWa,String auditKey){
    new AlertDialog.Builder(this)
        .setTitle("Report ready for "+bossName)
        .setMessage("Built offline. Internet only to send.\nEmail: "+(bossEmail.isEmpty()?"(set in Settings)":bossEmail)+"\nWhatsApp: "+(bossWa.isEmpty()?"(not set)":bossWa))
        .setPositiveButton("Email", (d,w)->{
            Intent i=new Intent(Intent.ACTION_SEND);
            i.setType("application/pdf");
            if(!bossEmail.isEmpty()) i.putExtra(Intent.EXTRA_EMAIL,new String[]{bossEmail});
            i.putExtra(Intent.EXTRA_SUBJECT,subject);
            i.putExtra(Intent.EXTRA_TEXT,textBody);
            i.putExtra(Intent.EXTRA_STREAM,pdfUri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try{startActivity(Intent.createChooser(i,"Email to boss"));}catch(Exception e){toast("No email app");}
            db.audit(user(),role(),"boss_email",auditKey);
        })
        .setNeutralButton("WhatsApp", (d,w)->{
            try{
                String msg=textBody.length()>3500?textBody.substring(0,3500)+"…":textBody;
                if(!bossWa.isEmpty()){
                    startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+bossWa.replace("+","")+"?text="+Uri.encode(msg))));
                }else{
                    Intent i=new Intent(Intent.ACTION_SEND);
                    i.setType("application/pdf");
                    i.putExtra(Intent.EXTRA_STREAM,pdfUri);
                    i.putExtra(Intent.EXTRA_TEXT,msg);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try{i.setPackage("com.whatsapp");startActivity(i);}catch(Exception e2){i.setPackage(null);startActivity(Intent.createChooser(i,"WhatsApp"));}
                }
                db.audit(user(),role(),"boss_wa",auditKey);
            }catch(Exception e){toast(e.getMessage());}
        })
        .setNegativeButton("Share…", (d,w)->{
            Intent i=new Intent(Intent.ACTION_SEND);
            i.setType("application/pdf");
            i.putExtra(Intent.EXTRA_SUBJECT,subject);
            i.putExtra(Intent.EXTRA_TEXT,textBody);
            i.putExtra(Intent.EXTRA_STREAM,pdfUri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i,"Share report"));
            db.audit(user(),role(),"boss_share",auditKey);
        })
        .show();
 }
 private static final int VOICE_FIX=92; private static final int LOC_PERM=93;
 private void showAssistant(){
    clear("Assistant");
    LinearLayout intro=Ui.card(this,Ui.CARD);
    intro.addView(Ui.text(this,"🤖 Inventory Assistant",17,Ui.NAVY_TEXT,true));
    intro.addView(Ui.muted(this,"Offline · BN/EN · voice. Changes need Confirm. Reports open share sheet."));
    intro.addView(Ui.text(this,
        "Fix: moyda 10 not 12 · ময়দা ১২ নয় ১০\n"+
        "Ask: stock atta · low stock · today receive · today batch · expiry\n"+
        "Boss: send daily/monthly to boss · বসকে আজকের রিপোর্ট\n"+
        "Open: entry batch reports pm yield process loss wastage recipe settings more",
        13,Ui.INK,false));
    add(intro);
    final EditText cmd=edit("Type a command…");
    add(cmd);
    LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
    Button mic=Ui.secondary(this,"🎤 Speak");
    Button run=Ui.primary(this,"Run");
    mic.setOnClickListener(v->{
        try{
            Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"bn-BD");
            i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Command…");
            startActivityForResult(i,VOICE_FIX);
        }catch(Exception e){
            try{
                Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US");
                startActivityForResult(i,VOICE_FIX);
            }catch(Exception e2){toast("Speech not available");}
        }
    });
    run.setOnClickListener(v->runAssistantCommand(cmd.getText().toString()));
    row.addView(mic,Ui.lp(this,0,dp(48),1,0,0,6,0));
    row.addView(run,Ui.lp(this,0,dp(48),1,6,0,0,0));
    add(row);
    add(Ui.muted(this,"Quick"));
    String[][] chips={
        {"Today→Boss","send daily to boss"},{"Month→Boss","send monthly to boss"},
        {"Summary","today summary"},
        {"Low stock","low stock"},{"Today receive","today receive"},
        {"Today batch","today batch"},{"Expiry","expiry"},
        {"Entry","open entry"},{"Batch","open batch"},
        {"PM","open pm"},{"Reports","open reports"},
        {"Yield","open yield"},{"Process loss","open process loss"},
        {"Wastage","open wastage"},{"Recipe","open recipe"},
        {"Scan","scan voucher"},{"Help","help"}
    };
    LinearLayout grid=null;
    for(int i=0;i<chips.length;i++){
        if(i%2==0){
            grid=new LinearLayout(this);
            grid.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams gl=new LinearLayout.LayoutParams(-1,-2);
            gl.setMargins(0,0,0,dp(8));
            content.addView(grid,gl);
        }
        final String q=chips[i][1];
        Button b=Ui.secondary(this,chips[i][0]);
        b.setOnClickListener(v->runAssistantCommand(q));
        LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(0,dp(48),1);
        if(i%2==0) bl.setMargins(0,0,dp(6),0); else bl.setMargins(dp(6),0,0,0);
        grid.addView(b,bl);
    }
 }


 
 private String normalizeBnDigits(String t){
    if(t==null) return "";
    String[] bn={"০","১","২","৩","৪","৫","৬","৭","৮","৯"};
    String out=t;
    for(int i=0;i<10;i++) out=out.replace(bn[i], String.valueOf(i));
    return out;
 }

private void runAssistantCommand(String raw){
    if(raw==null||raw.trim().isEmpty()){toast("Empty command");return;}
    String orig=normalizeBnDigits(raw.trim());
    String c=orig.toLowerCase(Locale.US);
    try{
        if(c.equals("help")||c.contains("সাহায্য")||c.equals("?")||c.contains("কী কী")||c.contains("what can")){
            showAssistant();toast("Quick buttons + examples above");return;
        }

        // LOW STOCK
        if(c.contains("low stock")||c.contains("reorder")||c.contains("কম স্টক")||c.contains("শর্ট লিস্ট")||c.contains("short list")||(c.contains("short")&&!c.contains("report"))){
            StringBuilder sb=new StringBuilder();
            int n=0;
            try{
                for(JSONObject rm:db.rows("SELECT * FROM rms ORDER BY CAST(code AS INTEGER)",null)){
                    String code=rm.optString("code");
                    double cl=liveRmClosing(code);
                    double ro=rm.optDouble("reorder",0);
                    if(cl<0||(ro>0&&cl<=ro)){
                        sb.append(code).append(" ").append(rm.optString("name"))
                          .append(" = ").append(fmt(cl)).append(" ").append(rm.optString("uom"));
                        if(ro>0) sb.append(" · reorder ").append(fmt(ro));
                        sb.append("\n");
                        n++;
                        if(n>=15) break;
                    }
                }
            }catch(Exception ignored){}
            if(n==0) toast("সব ঠিক আছে — low/short নেই");
            else new AlertDialog.Builder(this).setTitle("Low / short ("+n+")")
                .setMessage(sb.toString()).setPositiveButton("OK",null).show();
            return;
        }

        // TODAY RECEIVE
        if(c.contains("today receive")||c.contains("আজকের রিসিভ")||c.contains("receive today")||c.contains("আজ রিসিভ")){
            StringBuilder sb=new StringBuilder();
            double tot=0;int n=0;
            try{
                for(JSONObject r:db.rows("SELECT * FROM receives WHERE date=? ORDER BY id DESC",new String[]{today()})){
                    sb.append(r.optString("rm_code")).append("  ").append(fmt(r.optDouble("qty",0))).append("\n");
                    tot+=r.optDouble("qty",0);n++;
                }
            }catch(Exception ignored){}
            if(n==0) toast("আজকে কোনো receive নেই");
            else new AlertDialog.Builder(this).setTitle("Today receive · "+n+" lines · Σ "+fmt(tot))
                .setMessage(sb.toString()).setPositiveButton("OK",null)
                .setNeutralButton("Open Entry",(d,w)->showDaily()).show();
            return;
        }

        // TODAY BATCH / MIXING
        if(c.contains("today batch")||c.contains("today mix")||c.contains("আজকের ব্যাচ")||c.contains("আজ মিক্স")){
            StringBuilder sb=new StringBuilder();
            int n=0;
            try{
                for(JSONObject r:db.rows("SELECT * FROM mixing2_batch_log WHERE date=? ORDER BY id DESC LIMIT 30",new String[]{today()})){
                    sb.append(r.optString("product_name", r.optString("product_code","")))
                      .append(" × ").append(fmt(r.optDouble("batches", r.optDouble("batch_qty",0))))
                      .append("\n");
                    n++;
                }
            }catch(Exception ignored){}
            if(n==0) toast("আজকে batch log খালি");
            else new AlertDialog.Builder(this).setTitle("Today batch ("+n+")")
                .setMessage(sb.toString()).setPositiveButton("OK",null)
                .setNeutralButton("Open Batch",(d,w)->showBatch()).show();
            return;
        }

        // EXPIRY
        if(c.contains("expir")||c.contains("মেয়াদ")||c.contains("মেয়াদ")){
            showDashboard();toast("Dashboard → Expiry Watch");return;
        }

        // STOCK OF ONE RM: "stock atta" / "কত ময়দা" / "closing flour"
        if(c.contains("stock ")||c.contains("closing ")||c.contains("কত ")||c.contains("স্টক ")||c.startsWith("balance ")){
            List<String[]> matches=matchRmCandidates(orig);
            if(!matches.isEmpty()){
                String code=matches.get(0)[0], name=matches.get(0)[1];
                double cl=liveRmClosing(code);
                toast(name+" ("+code+") = "+fmt(cl));
                new AlertDialog.Builder(this).setTitle(name)
                    .setMessage("Code: "+code+"\nLive closing: "+fmt(cl))
                    .setPositiveButton("OK",null).show();
                return;
            }
        }
        // bare RM name + no digits → try stock lookup
        if(!java.util.regex.Pattern.compile("[0-9০-৯]").matcher(c).find()
                && !c.contains("open")&&!c.contains("send")&&!c.contains("boss")&&!c.contains("বস")){
            List<String[]> matches=matchRmCandidates(orig);
            if(matches.size()==1 && orig.length()>=3){
                String code=matches.get(0)[0], name=matches.get(0)[1];
                double cl=liveRmClosing(code);
                new AlertDialog.Builder(this).setTitle(name)
                    .setMessage("Live closing: "+fmt(cl)+"\n(Code "+code+")")
                    .setPositiveButton("OK",null)
                    .setNeutralButton("Fix qty…",(d,w)->{
                        final EditText q=new EditText(this);
                        q.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                        q.setHint("New receive qty");
                        new AlertDialog.Builder(this).setTitle("Set last receive → "+name)
                            .setView(q)
                            .setPositiveButton("Apply",(d2,w2)->runAssistantCommand(name+" "+q.getText().toString()+" kg"))
                            .setNegativeButton("Cancel",null).show();
                    }).show();
                return;
            }
        }

        // OPEN SCREENS
        if(c.contains("open entry")||c.equals("entry")||c.contains("দৈনিক এন্ট্রি")||c.contains("daily entry")){showDaily();return;}
        if(c.contains("open batch")||c.equals("batch")||c.contains("ব্যাচ")||c.contains("input batch")){showBatch();return;}
        if(c.contains("open report")||c.contains("reports")||c.contains("রিপোর্ট খুল")){showReports();return;}
        if(c.contains("open pm")||c.contains("daily pm")||c.contains("packaging")||c.contains("পিএম")){openDailyPm();return;}
        if(c.contains("open dashboard")||c.contains("home")||c.equals("dashboard")||c.contains("ড্যাশ")){showDashboard();return;}
        if(c.contains("open more")||c.equals("more")||c.contains("মোর")){showMore();return;}
        if(c.contains("open settings")||c.equals("settings")||c.contains("সেটিং")){gateOpen("Settings",()->showSettings());return;}
        if(c.contains("stock count")||c.contains("কাউন্ট")||c.contains("physical count")){showCount();return;}
        if(c.contains("owner pack")||c.contains("ওনার")){showOwnerPack();return;}
        if(c.contains("process loss")||c.contains("প্রসেস লস")){showProcessLossSheet();return;}
        if(c.contains("open yield")||c.equals("yield")||c.contains("ইইল্ড")||c.contains("yield %")){showYieldSheet();return;}
        if(c.contains("open wastage")||c.contains("wastage")||c.contains("ওয়েস্ট")||c.contains("ওয়েস্ট")){showWastage();return;}
        if(c.contains("open recipe")||c.contains("recipe")||c.contains("রেসিপি")){gateOpen("Recipe",()->showRecipe());return;}
        if(c.contains("custom sheet")||c.contains("কাস্টম")){showCustom();return;}
        if(c.contains("audit")||c.contains("অডিট")){showAuditLog();return;}
        if(c.contains("planner")||c.contains("batch plan")){showRmPlanner();return;}
        if(c.contains("master rm")||c.contains("raw material master")){gateOpen("Raw material master",()->showRmMaster());return;}
        if(c.contains("master product")||c.contains("product master")){gateOpen("Product master",()->showProductMaster());return;}
        if(c.contains("scan voucher")||c.contains("ocr")||c.contains("scan")||c.contains("ভাউচার")){showAI();return;}
        if(c.contains("backup")||c.contains("ব্যাকআপ")){backupJson();return;}
        if(c.contains("stock tip")||c.contains("smart tip")||c.contains("alert")||c.contains("টিপস")){showDashboard();toast("Smart tips on Dashboard");return;}

        // BOSS
        if(c.contains("boss")||c.contains("বস")||((c.contains("send")||c.contains("পাঠা"))&&(c.contains("report")||c.contains("রিপোর্ট")))){
            java.util.regex.Matcher ym=java.util.regex.Pattern.compile("(20\\d{2}-\\d{2})").matcher(c);
            if(c.contains("month")||c.contains("মাসিক")||c.contains("monthly")||c.contains("মাসের")){
                sendMonthlyToBoss(ym.find()?ym.group(1):today().substring(0,7));return;
            }
            sendDailyToBoss(today());return;
        }
        if((c.contains("send")||c.contains("পাঠা"))&&(c.contains("month")||c.contains("মাসিক"))){
            java.util.regex.Matcher ym=java.util.regex.Pattern.compile("(20\\d{2}-\\d{2})").matcher(c);
            sendMonthlyToBoss(ym.find()?ym.group(1):today().substring(0,7));return;
        }
        if((c.contains("send")||c.contains("পাঠা"))&&(c.contains("daily")||c.contains("today")||c.contains("আজ"))){
            sendDailyToBoss(today());return;
        }

        // BATCH QTY FIX — last batch today
        if((c.contains("batch")||c.contains("ব্যাচ")||c.contains("mixing")||c.contains("মিক্স"))
                && java.util.regex.Pattern.compile("[0-9]").matcher(c).find()){
            Double bq=null;
            java.util.regex.Matcher bm=java.util.regex.Pattern.compile("([0-9]+(?:[\\.,][0-9]+)?)").matcher(c);
            java.util.ArrayList<Double> bnums=new java.util.ArrayList<>();
            while(bm.find()) bnums.add(Double.parseDouble(bm.group(1).replace(',','.')));
            if(bnums.size()>=2 && (c.contains("not")||c.contains("নয়")||c.contains("না"))) bq=bnums.get(bnums.size()-1);
            else if(!bnums.isEmpty()) bq=bnums.get(bnums.size()-1);
            if(bq!=null && bq>0){
                JSONObject brow=db.one("SELECT * FROM mixing2_batch_log WHERE date=? ORDER BY id DESC LIMIT 1",new String[]{today()});
                if(brow==null) brow=db.one("SELECT * FROM mixing2_batch_log ORDER BY id DESC LIMIT 1",null);
                if(brow!=null){
                    final JSONObject bt=brow;
                    final double nb=bq;
                    String label=brow.optString("product_name", brow.optString("product_code","batch"));
                    new AlertDialog.Builder(this)
                        .setTitle("Fix batch qty?")
                        .setMessage(label+" · id "+brow.optInt("id")+"\nNew value: "+fmt(nb))
                        .setPositiveButton("Confirm",(d,w)->{
                            try{
                                int id=bt.optInt("id");
                                android.content.ContentValues cv=new android.content.ContentValues();
                                cv.put("batches", nb);
                                int u=db.getWritableDatabase().update("mixing2_batch_log", cv, "id=?", new String[]{String.valueOf(id)});
                                if(u==0){
                                    cv=new android.content.ContentValues();
                                    cv.put("batch_qty", nb);
                                    db.getWritableDatabase().update("mixing2_batch_log", cv, "id=?", new String[]{String.valueOf(id)});
                                }
                                db.audit(user(),role(),"assistant_batch_fix", label+"="+fmt(nb));
                                toast("Batch updated ✔");
                            }catch(Exception ex){toast("Batch update failed: "+ex.getMessage());}
                        })
                        .setNegativeButton("Cancel",null).show();
                    return;
                }
            }
        }

        // QTY FIX (receive)
        if(java.util.regex.Pattern.compile("[0-9০-৯]").matcher(orig).find()){
            applyVoiceFixCommand(orig);return;
        }

        StringBuilder sug=new StringBuilder("বুঝিনি। চেষ্টা করুন:\n");
        if(c.contains("stock")||c.contains("স্টক")) sug.append("· low stock  ·  stock <RM name>\n");
        if(c.contains("batch")||c.contains("ব্যাচ")||c.contains("mix")) sug.append("· today batch  ·  open batch\n");
        if(c.contains("recv")||c.contains("রিসিভ")||c.contains("receive")) sug.append("· today receive  ·  open entry\n");
        if(c.contains("report")||c.contains("রিপোর্ট")||c.contains("boss")||c.contains("বস")) sug.append("· send daily to boss  ·  send monthly to boss\n");
        if(c.contains("open")||c.contains("খুল")) sug.append("· open entry / batch / pm / reports\n");
        sug.append("· today summary  ·  help");
        new AlertDialog.Builder(this).setTitle("Assistant")
            .setMessage(sug.toString())
            .setPositiveButton("Help",(d,w)->showAssistant())
            .setNegativeButton("OK",null).show();
        db.audit(user(),role(),"assistant_unknown",orig);
    }catch(Exception e){error(e);}
 }


 private void showVoiceFix(){ showAssistant(); }
private void applyVoiceFixCommand(String raw){
    try{
        if(raw==null||raw.trim().isEmpty()){toast("Type or speak a command");return;}
        String cmd=normalizeBnDigits(raw.trim());
        // extract numbers: prefer "not X ... Y" / "X নয় Y" → Y is new qty
        Double newQty=null;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)(?:not|noy|নয়|না)\\s*([0-9]+(?:[\\.,][0-9]+)?)\\s*(?:kg|kgs|কেজি)?\\s*(?:but|হবে|=)?\\s*([0-9]+(?:[\\.,][0-9]+)?)").matcher(cmd);
        if(m.find()) newQty=Double.parseDouble(m.group(2).replace(',','.'));
        if(newQty==null){
            m=java.util.regex.Pattern.compile("(?i)([0-9]+(?:[\\.,][0-9]+)?)\\s*(?:kg|kgs|কেজি)?\\s*(?:not|noy|নয়|না)\\s*([0-9]+(?:[\\.,][0-9]+)?)").matcher(cmd);
            // "12 not 10" → second is correct
            if(m.find()) newQty=Double.parseDouble(m.group(2).replace(',','.'));
        }
        if(newQty==null){
            m=java.util.regex.Pattern.compile("(?i)(?:will be|হবে|=)?\\s*([0-9]+(?:[\\.,][0-9]+)?)\\s*(?:kg|kgs|কেজি)?").matcher(cmd);
            java.util.regex.Matcher all=java.util.regex.Pattern.compile("([0-9]+(?:[\\.,][0-9]+)?)").matcher(cmd);
            java.util.ArrayList<Double> nums=new java.util.ArrayList<>();
            while(all.find()) nums.add(Double.parseDouble(all.group(1).replace(',','.')));
            if(nums.size()>=2 && (cmd.toLowerCase(Locale.US).contains("not")||cmd.contains("নয়")||cmd.contains("না")))
                newQty=nums.get(nums.size()-1);
            else if(!nums.isEmpty()) newQty=nums.get(nums.size()-1);
        }
        if(newQty==null||newQty<=0){toast("Could not read quantity");return;}

        // RM match from command text without numbers
        String namePart=cmd.replaceAll("[0-9]+(?:[\\.,][0-9]+)?"," ").replaceAll("(?i)kg|kgs|কেজি|not|noy|but|will be|হবে|নয়|না", " ");
        List<String[]> matches=matchRmCandidates(namePart+" "+cmd);
        if(matches.isEmpty()){toast("Could not match RM name — try English/Bangla name from master");return;}
        String code=matches.get(0)[0];
        String rmName=matches.get(0)[1];

        // find latest receive for this RM (prefer today)
        JSONObject row=db.one("SELECT * FROM receives WHERE rm_code=? AND date=? ORDER BY id DESC LIMIT 1",new String[]{code,today()});
        if(row==null) row=db.one("SELECT * FROM receives WHERE rm_code=? ORDER BY id DESC LIMIT 1",new String[]{code});
        if(row==null){toast("No receive entry found for "+rmName);return;}
        if(db.monthLocked(row.optString("date"))){toast("Month locked");return;}

        final JSONObject target=row;
        final double qty=newQty;
        new AlertDialog.Builder(this)
            .setTitle("Confirm fix")
            .setMessage(rmName+" ("+code+")\nEntry #"+row.optString("id")+" on "+row.optString("date")+"\n"+fmt(row.optDouble("qty"))+"  →  "+fmt(qty)+"\n\nCommand: "+cmd)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Update", (d,w)->{
                try{
                    ContentValues z=new ContentValues();
                    z.put("qty",qty);
                    z.put("note",(target.optString("note")+" | voice-fix").trim());
                    db.update("receives",z,"id=?",new String[]{target.optString("id")});
                    db.audit(user(),role(),"voice_fix",code+" "+fmt(target.optDouble("qty"))+"→"+fmt(qty));
                    toast("Updated ✔");
                    showVoiceFix();
                }catch(Exception e){error(e);}
            })
            .show();
    }catch(Exception e){error(e);}
 }

 private void sharePdf(String text,String name)throws Exception{File f=createPdf(text,name);Uri u=FileProvider.getUriForFile(this,"com.rmstock.nativeapp.fileprovider",f);new AlertDialog.Builder(this).setTitle("Report ready").setItems(new String[]{"Save PDF","Share PDF","Print PDF"},(d,w)->{if(w==0){pendingPdf=f;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/pdf");i.putExtra(Intent.EXTRA_TITLE,f.getName());startActivityForResult(i,79);}else if(w==1){Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/pdf");i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Share PDF"));}else{printPdf(f);}}).show();}
 private File createPdf(String text,String name)throws Exception{PdfDocument doc=new PdfDocument();Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(10*getResources().getDisplayMetrics().scaledDensity);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));ArrayList<String> lines=new ArrayList<>();for(String raw:text.split("\n")){if(raw.length()<=78)lines.add(raw);else{for(int i=0;i<raw.length();i+=78)lines.add(raw.substring(i,Math.min(raw.length(),i+78)));}}int page=1,y=42;PdfDocument.Page pg=null;Canvas c=null;for(String line:lines){if(pg==null||y>790){if(pg!=null){p.setTextSize(8*getResources().getDisplayMetrics().scaledDensity);c.drawText("Page "+(page-1),520,820,p);doc.finishPage(pg);}pg=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,page++).create());c=pg.getCanvas();p.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);c.drawText(db.setting("company","RM Stock"),24,24,p);p.setTextSize(10*getResources().getDisplayMetrics().scaledDensity);y=42;}c.drawText(line,24,y,p);y+=16;}if(pg!=null){p.setTextSize(8*getResources().getDisplayMetrics().scaledDensity);c.drawText("Page "+(page-1),520,820,p);doc.finishPage(pg);}File dir=new File(getFilesDir(),"reports");dir.mkdirs();File f=new File(dir,name+".pdf");FileOutputStream o=new FileOutputStream(f);doc.writeTo(o);o.close();doc.close();return f;}
 private void printPdf(File f){PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE);pm.print(f.getName(),new PrintAdapter(this,f),null);}
 private static class PrintAdapter extends PrintDocumentAdapter{Context c;File f;PrintAdapter(Context c,File f){this.c=c;this.f=f;}public void onLayout(PrintAttributes oldA,PrintAttributes newA,CancellationSignal s,LayoutResultCallback cb,Bundle m){if(s.isCanceled()){cb.onLayoutCancelled();return;}cb.onLayoutFinished(new PrintDocumentInfo.Builder(f.getName()).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(),true);}public void onWrite(PageRange[] pages,ParcelFileDescriptor dest,CancellationSignal s,WriteResultCallback cb){try{FileInputStream in=new FileInputStream(f);FileOutputStream out=new FileOutputStream(dest.getFileDescriptor());byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);in.close();cb.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});}catch(Exception e){cb.onWriteFailed(e.toString());}}}
 private void moveColumn(long cid,String sheet,int delta){try{List<JSONObject> cs=db.rows("SELECT id,position FROM custom_cols WHERE sheet_id=? ORDER BY position",new String[]{sheet});int idx=-1;for(int i=0;i<cs.size();i++)if(cs.get(i).getLong("id")==cid)idx=i;if(idx<0)return;int ni=idx+delta;if(ni<0||ni>=cs.size())return;long other=cs.get(ni).getLong("id");int p=cs.get(idx).getInt("position"),op=cs.get(ni).getInt("position");ContentValues a=new ContentValues();a.put("position",op);db.update("custom_cols",a,"id=?",new String[]{String.valueOf(cid)});ContentValues b=new ContentValues();b.put("position",p);db.update("custom_cols",b,"id=?",new String[]{String.valueOf(other)});showCustomDefinition(sheet,db.one("SELECT name FROM custom_sheets WHERE id=?",new String[]{sheet}).getString("name"));}catch(Exception e){error(e);}}
 private void showMixing2(String month){clear("Mixing-2 — Batch × Recipe Usage");try{for(JSONObject b:db.rows("SELECT date,product_code,shift,qty FROM batches WHERE date LIKE ? ORDER BY date,product_code,shift",new String[]{month+"-%"})){JSONObject p=db.one("SELECT name FROM products WHERE code=?",new String[]{b.optString("product_code")});LinearLayout r=row();TextView t=new TextView(this);t.setText(b.optString("date")+" | "+(p==null?b.optString("product_code"):p.getString("name"))+" | Shift "+b.optString("shift")+" | Batches "+batchCountText(b.getDouble("qty")));t.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));r.addView(t);Button e=btn("Edit Batch");e.setOnClickListener(v->editBatchCount(b));r.addView(e);Button er=btn("Edit RM Recipe");er.setOnClickListener(v->editMixingRecipe(b.optString("product_code"),month));r.addView(er);add(r);for(JSONObject rm:db.rows("SELECT rm_code,qty FROM recipes WHERE product_code=? AND qty<>0 ORDER BY rm_code",new String[]{b.optString("product_code")})){double use=b.getDouble("qty")*rm.getDouble("qty");TextView u=tv("   RM "+rm.getString("rm_code")+" = "+fmt(use),1);add(u);}}}catch(Exception e){error(e);}}
 private void editMixingRecipe(String productCode,String returnMonth){
  try{
   JSONObject p=db.one("SELECT name FROM products WHERE code=?",new String[]{productCode});
   clear("Mixing-2 RM Recipe — "+(p==null?productCode:p.getString("name")));
   for(JSONObject r:db.rows("SELECT code,name,uom FROM rms ORDER BY CAST(code AS INTEGER)",null)){
    JSONObject q=db.one("SELECT qty FROM recipes WHERE rm_code=? AND product_code=?",new String[]{r.getString("code"),productCode});
    LinearLayout line=row();
    TextView lab=tv(r.getString("code")+" — "+r.getString("name")+" ("+r.getString("uom")+")",1);
    line.addView(lab,new LinearLayout.LayoutParams(0,-2,1));
    EditText e=edit("Recipe Qty"); e.setText(q==null?"0":fmt(q.getDouble("qty"))); e.setTag(r.getString("code"));
    line.addView(e,new LinearLayout.LayoutParams(dp(110),dp(52))); content.addView(line);
   }
   Button save=btn("Save Mixing-2 RM Recipe");
   save.setOnClickListener(v->{if(!open())return;
    try{
     for(int i=1;i<content.getChildCount()-1;i++){
      View vv=content.getChildAt(i);
      if(!(vv instanceof LinearLayout))continue;
      LinearLayout line=(LinearLayout)vv;
      for(int j=0;j<line.getChildCount();j++){
       View c=line.getChildAt(j);
       if(c instanceof EditText&&c.getTag()!=null){
        ContentValues z=new ContentValues(); z.put("qty",num(((EditText)c).getText().toString()));
        db.update("recipes",z,"rm_code=? AND product_code=?",new String[]{String.valueOf(c.getTag()),productCode});
       }
      }
     }
     toast("Mixing-2 RM recipe saved ✔"); showMixing2(returnMonth);
    }catch(Exception e){error(e);}
   });
   add(save);
  }catch(Exception e){error(e);}
 }
 private void exportMonthlyCsv(String month){try{StringBuilder s=new StringBuilder("Item Code,Item Name,UoM,Opening Balance,Total Receive,Total Use,Closing Balance,Status\r\n");for(JSONObject rm:db.rows("SELECT * FROM rms ORDER BY CAST(code AS INTEGER)",null)){String code=rm.getString("code");double o=opening(month+"-01",code),rec=monthlyReceive(month,code),u=monthlyUse(month,code),cl=o+rec-u;s.append(csvCell(code)).append(',').append(csvCell(rm.optString("name"))).append(',').append(csvCell(rm.optString("uom","KG"))).append(',').append(fmt(o)).append(',').append(fmt(rec)).append(',').append(fmt(u)).append(',').append(fmt(cl)).append(',').append(csvCell(cl<0?"LOW/NEGATIVE":cl==0?"ZERO":"OK")).append("\r\n");}shareCsv(s.toString(),"Final-Monthly-Usage-"+month+".csv");}catch(Exception e){error(e);}}
 private String csvCell(String v){return "\""+String.valueOf(v).replace("\"","\"\"")+"\"";}
 private void shareCsv(String data,String name){try{File f=new File(getCacheDir(),name);try(FileOutputStream out=new FileOutputStream(f)){out.write(("\uFEFF"+data).getBytes("UTF-8"));}pendingPdf=f;Uri u=FileProvider.getUriForFile(this,"com.rmstock.nativeapp.fileprovider",f);new AlertDialog.Builder(this).setTitle("Spreadsheet export ready").setItems(new String[]{"Save CSV (opens in Excel)","Share CSV"},(d,w)->{if(w==0){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("text/csv");i.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,82);}else{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/csv");i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Share spreadsheet"));}}).show();}catch(Exception e){error(e);}}
 private void reportDaywiseLandscape(String month,boolean receiveSheet){
  try{
   int days=daysInMonth(month);
   List<JSONObject> rms=db.rows("SELECT code,name FROM rms ORDER BY CAST(code AS INTEGER)",null);
   PdfDocument doc=new PdfDocument();
   Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.BLACK);
   final float left=18f, labelW=174f, totalW=42f, right=18f;
   final float dayW=(842f-left-labelW-totalW-right)/days;
   int page=1; PdfDocument.Page pg=null; Canvas c=null; float y=0;
   for(int ri=0;ri<rms.size();ri++){
    if(pg==null || y>555f){
     if(pg!=null){p.setTextSize(8f);c.drawText("Page "+(page-1),790,580,p);doc.finishPage(pg);}
     pg=doc.startPage(new PdfDocument.PageInfo.Builder(842,595,page++).create()); c=pg.getCanvas();
     p.setTextSize(12f); c.drawText(db.setting("company","RM Stock"),left,22,p);
     p.setTextSize(10f); c.drawText((receiveSheet?"RM Receive Monthly — Day-wise":"RM Mixing-2 Monthly — Day-wise")+" | "+month,left,40,p);
     p.setTextSize(7f); c.drawText("RM Code / Name",left,61,p);
     for(int d=1;d<=days;d++){
      String day=String.valueOf(d); float x=left+labelW+(d-1)*dayW;
      c.drawText(day,x+Math.max(0,(dayW-p.measureText(day))/2f),61,p);
     }
     c.drawText("Total",left+labelW+days*dayW+2,61,p);
     p.setStrokeWidth(.5f); c.drawLine(left,67,842-right,67,p);
     y=82;
    }
    JSONObject rm=rms.get(ri); String code=rm.optString("code");
    String label=code+" - "+rm.optString("name");
    p.setTextSize(7f);
    // Keep both item code and name visible; shrink text to fit the fixed label column.
    while(label.length()>1 && p.measureText(label)>labelW-5) { p.setTextSize(Math.max(5.5f,p.getTextSize()-.25f)); if(p.getTextSize()<=5.5f) break; }
    if(p.measureText(label)>labelW-5){while(label.length()>1&&p.measureText(label+"…")>labelW-5)label=label.substring(0,label.length()-1);label+="…";}
    c.drawText(label,left,y,p); p.setTextSize(5.5f); double total=0;
    for(int d=1;d<=days;d++){
     String date=month+"-"+String.format(Locale.US,"%02d",d);
     double q=receiveSheet?receive(date,code):use(date,code); total+=q;
     String value=fmt(q); float x=left+labelW+(d-1)*dayW;
     float tx=x+Math.max(0,(dayW-p.measureText(value))/2f);
     c.drawText(value,tx,y,p);
    }
    String totalText=fmt(total); c.drawText(totalText,left+labelW+days*dayW+2,y,p);
    y+=14f;
   }
   if(pg!=null){p.setTextSize(8f);c.drawText("Page "+(page-1),790,580,p);doc.finishPage(pg);}
   File dir=new File(getFilesDir(),"reports");dir.mkdirs();
   File f=new File(dir,(receiveSheet?"Receive-Daywise-":"Mixing2-Daywise-")+month+".pdf");
   try(FileOutputStream out=new FileOutputStream(f)){doc.writeTo(out);} doc.close(); shareExistingPdf(f);
  }catch(Exception e){error(e);}
 }
 private void shareExistingPdf(File f)throws Exception{Uri u=FileProvider.getUriForFile(this,"com.rmstock.nativeapp.fileprovider",f);new AlertDialog.Builder(this).setTitle("Report ready").setItems(new String[]{"Save PDF","Share PDF","Print PDF"},(d,w)->{if(w==0){pendingPdf=f;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/pdf");i.putExtra(Intent.EXTRA_TITLE,f.getName());startActivityForResult(i,79);}else if(w==1){Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/pdf");i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Share PDF"));}else printPdf(f);}).show();}
 private void reportBatchLog(String month){try{ArrayList<String[]> rows=new ArrayList<>();for(JSONObject x:db.rows("SELECT l.date,l.product_code,l.shift,l.batch_count,l.rm_code,l.recipe_qty,l.consumption,p.name FROM mixing2_batch_log l LEFT JOIN products p ON p.code=l.product_code WHERE l.date LIKE ? ORDER BY l.date,l.product_code,l.shift,l.rm_code",new String[]{month+"-%"})){rows.add(new String[]{x.optString("date"),x.optString("name",x.optString("product_code")),"Shift "+x.optString("shift"),x.optString("rm_code"),fmt(x.optDouble("recipe_qty")),batchCountText(x.optDouble("batch_count")),fmt(x.optDouble("consumption"))});}createLandscapeTablePdf("RM MIXING-2 BATCH LOG","Month: "+month,new String[]{"Date","Product","Shift","RM Code","Recipe Qty","Batches","Consumption"},rows,"Mixing2-Batch-Log-"+month);}catch(Exception e){error(e);}}
 private void reportMixingMonthly(String month){reportDaywiseLandscape(month,false);}
 private void editCompany(){gateOpen("Settings",()->showSettings());}


 private void addShiftWiseSummary(){
    try{
        String d=today();
        LinearLayout box=Ui.card(this,Ui.CARD);
        box.addView(Ui.text(this,lbl("Shift-wise today"),15,Ui.NAVY_TEXT,true));
        box.addView(Ui.muted(this,"A / B / C / General — batches and Mixing-2 use for "+d));
        String[] shifts=new String[]{"1","2","3","0","A","B","C","General"};
        // normalize: DB may store 1/2/3 or A/B/C
        java.util.LinkedHashMap<String,double[]> map=new java.util.LinkedHashMap<>();
        map.put("A",new double[]{0,0});
        map.put("B",new double[]{0,0});
        map.put("C",new double[]{0,0});
        map.put("General",new double[]{0,0});
        map.put("none",new double[]{0,0});
        for(JSONObject x:db.rows("SELECT shift,SUM(qty) q FROM batches WHERE date=? GROUP BY shift",new String[]{d})){
            String sh=normShift(x.optString("shift"));
            double[] a=map.get(sh); if(a==null){a=map.get("none");}
            a[0]+=x.optDouble("q",0);
        }
        for(JSONObject x:db.rows("SELECT shift,SUM(consumption) q FROM mixing2_batch_log WHERE date=? GROUP BY shift",new String[]{d})){
            String sh=normShift(x.optString("shift"));
            double[] a=map.get(sh); if(a==null){a=map.get("none");}
            a[1]+=x.optDouble("q",0);
        }
        for(java.util.Map.Entry<String,double[]> e:map.entrySet()){
            if(e.getValue()[0]==0&&e.getValue()[1]==0&&e.getKey().equals("Other"))continue;
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0,dp(4),0,dp(4));
            TextView t=Ui.text(this,"Shift "+e.getKey(),14,Ui.INK,true);
            TextView v=Ui.text(this,fmt(e.getValue()[0])+" batch  ·  "+fmt(e.getValue()[1])+" kg use",13,Ui.MUTED,false);
            row.addView(t,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(v,new LinearLayout.LayoutParams(0,-2,2));
            box.addView(row);
        }
        add(box);
    }catch(Exception ignored){}
 }
 private String normShift(String s){
    if(s==null||s.trim().isEmpty())return "none";
    s=s.trim();
    if(s.equals("1")||s.equalsIgnoreCase("A"))return "A";
    if(s.equals("2")||s.equalsIgnoreCase("B"))return "B";
    if(s.equals("3")||s.equalsIgnoreCase("C"))return "C";
    if(s.equals("4")||s.equalsIgnoreCase("General")||s.equalsIgnoreCase("G"))return "General";
    if(s.equals("0")||s.equalsIgnoreCase("none")||s.equals("— none —"))return "none";
    return "none";
 }
 private String shiftLabel(int sh){
    if(sh==1)return "A"; if(sh==2)return "B"; if(sh==3)return "C"; if(sh==4)return "General";
    return "none";
 }
 private void showOwnerPack(){
    clear("Owner pack");
    try{
        String d=today();
        StringBuilder sb=new StringBuilder();
        String company=db.setting("company","Inventory Management");
        sb.append(company).append("\nOwner pack — ").append(d).append("\n");
        sb.append("User: ").append(user()).append(" (").append(role()).append(")\n\n");
        JSONObject tr=db.one("SELECT COALESCE(SUM(qty),0) q FROM receives WHERE date=?",new String[]{d});
        JSONObject tu=db.one("SELECT COALESCE(SUM(consumption),0) q FROM mixing2_batch_log WHERE date=?",new String[]{d});
        JSONObject tb=db.one("SELECT COALESCE(SUM(qty),0) q FROM batches WHERE date=?",new String[]{d});
        sb.append("TODAY\n");
        sb.append("  Receive: ").append(fmt(tr==null?0:tr.optDouble("q"))).append(" kg\n");
        sb.append("  Mixing use: ").append(fmt(tu==null?0:tu.optDouble("q"))).append(" kg\n");
        sb.append("  Batches: ").append(fmt(tb==null?0:tb.optDouble("q"))).append("\n\n");
        sb.append("SHIFT-WISE BATCHES\n");
        for(JSONObject x:db.rows("SELECT shift,SUM(qty) q FROM batches WHERE date=? GROUP BY shift ORDER BY shift",new String[]{d}))
            sb.append("  ").append(normShift(x.optString("shift"))).append(": ").append(fmt(x.optDouble("q"))).append("\n");
        sb.append("\nYIELD (saved formulas)\n");
        for(JSONObject p:db.rows("SELECT code,name FROM products ORDER BY code",null)){
            JSONObject y=db.one("SELECT actual,standard,formula,moisture FROM yield_defs WHERE product_code=? AND enabled=1",new String[]{p.optString("code")});
            if(y==null)continue;
            String fo=y.optString("formula","");
            if(fo.isEmpty())continue;
            Double pct=evalYieldFormulaEx(fo,y.optDouble("actual"),y.optDouble("standard"),y.optDouble("moisture"),todayWastageTotal(),todayBatchTotal());
            sb.append("  ").append(p.optString("name")).append(": ").append(pct==null?"—":fmt(pct)+"%").append("\n");
        }
        sb.append("\nSTOCK WATCH (low / short)\n");
        final Map<String,Double> recMap=sumMap("SELECT rm_code k,SUM(qty) v FROM receives WHERE date<=? GROUP BY rm_code",new String[]{d});
        final Map<String,Double> useMap=sumMap("SELECT rm_code k,SUM(consumption) v FROM mixing2_batch_log WHERE date<=? GROUP BY rm_code",new String[]{d});
        int n=0;
        for(JSONObject rm:db.rows("SELECT code,name,uom,opening,reorder FROM rms ORDER BY CAST(code AS INTEGER)",null)){
            String code=rm.getString("code");
            double closing=rm.optDouble("opening",0)+nz(recMap.get(code))-nz(useMap.get(code));
            double reorder=rm.optDouble("reorder",0);
            if(closing<0||(reorder>0&&closing<=reorder)){
                sb.append("  ").append(code).append(" ").append(rm.optString("name")).append(": ").append(fmt(closing)).append(" ").append(rm.optString("uom")).append(closing<0?" SHORT":" LOW").append("\n");
                n++; if(n>=15)break;
            }
        }
        if(n==0)sb.append("  None\n");
        sb.append("\n— Inventory Management (offline)\n");
        final String body=sb.toString();
        LinearLayout card=Ui.card(this,Ui.CARD);
        card.addView(Ui.text(this,"Owner pack",16,Ui.NAVY_TEXT,true));
        TextView tv=Ui.text(this,body,13,Ui.INK,false);
        tv.setTypeface(Typeface.MONOSPACE);
        card.addView(tv);
        add(card);
        Button share=Ui.primary(this,"Share text (WhatsApp / Email / Drive)");
        share.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT,company+" — Owner pack "+d);
            i.putExtra(Intent.EXTRA_TEXT,body);
            startActivity(Intent.createChooser(i,"Share owner pack"));
            db.audit(user(),role(),"share_owner_pack",d);
        });
        add(share);
        Button pdf=Ui.secondary(this,"Save / share as PDF");
        pdf.setOnClickListener(v->ownerPackPdf(company,d,body));
        add(pdf);
    }catch(Exception e){error(e);}
 }
 private void ownerPackPdf(String company,String d,String body){
    try{
        PdfDocument doc=new PdfDocument();
        int pageW=595,pageH=842,y=40,pageNo=0;
        PdfDocument.Page page=null; android.graphics.Canvas c=null;
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); paint.setTextSize(10); paint.setColor(Color.BLACK);
        Paint head=new Paint(paint); head.setTextSize(14); head.setTypeface(Typeface.DEFAULT_BOLD);
        String[] lines=body.split("\n");
        for(String line:lines){
            if(page==null||y>pageH-40){
                if(page!=null)doc.finishPage(page);
                pageNo++; page=doc.startPage(new PdfDocument.PageInfo.Builder(pageW,pageH,pageNo).create());
                c=page.getCanvas(); y=40;
                c.drawText(company+" — Owner pack",40,y,head); y+=22;
            }
            String L=line.length()>90?line.substring(0,90):line;
            c.drawText(L,40,y,paint); y+=14;
        }
        if(page!=null)doc.finishPage(page);
        File f=new File(getCacheDir(),"owner-pack-"+d+".pdf");
        try(FileOutputStream o=new FileOutputStream(f)){doc.writeTo(o);} doc.close();
        pendingPdf=f;
        offerPdfOrShare(f);
        db.audit(user(),role(),"owner_pack_pdf",d);
    }catch(Exception e){error(e);}
 }
 private void offerPdfOrShare(File f){
    try{
        Uri u=FileProvider.getUriForFile(this,"com.rmstock.nativeapp.fileprovider",f);
        new AlertDialog.Builder(this).setTitle(f.getName())
            .setItems(new CharSequence[]{"Share","Save…","Print"},(d,w)->{
                if(w==0){Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/pdf");i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Share PDF"));}
                else if(w==1){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/pdf");i.putExtra(Intent.EXTRA_TITLE,f.getName());startActivityForResult(i,79);}
                else printPdf(f);
            }).show();
    }catch(Exception e){error(e);}
 }
 private void showAuditLog(){
    clear("Audit log");
    try{
        LinearLayout intro=Ui.card(this,Ui.CARD);
        intro.addView(Ui.text(this,"Offline change trail",16,Ui.NAVY_TEXT,true));
        intro.addView(Ui.muted(this,"Recent actions on this device. Included in unified backup."));
        add(intro);
        List<JSONObject> rows=db.rows("SELECT * FROM audit_log ORDER BY id DESC LIMIT 100",null);
        if(rows.isEmpty()){add(Ui.muted(this,"No audit entries yet"));return;}
        for(JSONObject r:rows){
            LinearLayout c=Ui.card(this,Ui.CARD);
            c.addView(Ui.text(this,r.optString("ts")+" · "+r.optString("user")+" ("+r.optString("role")+")",12,Ui.MUTED,false));
            c.addView(Ui.text(this,r.optString("action"),14,Ui.NAVY_TEXT,true));
            c.addView(Ui.muted(this,r.optString("detail")));
            add(c);
        }
    }catch(Exception e){error(e);}
 }
 private boolean assertCanEdit(String dateOrYm){
    if(!db.canEdit()){toast("Operator role: view only");return false;}
    if(dateOrYm!=null&&db.monthLocked(dateOrYm)){toast("Month locked (finalized)");return false;}
    return true;
 }



 /** line: name|actualPct|standardPct — color by actual vs standard */
 private void applyProcessLossLine(TextView circle, String line){
    try{
        String[] p=line.split("\\|");
        String name=p.length>0?p[0]:"—";
        double actual=p.length>1?num(p[1]):0;
        double std=p.length>2?num(p[2]):0;
        String big=fmt(actual)+"%";
        String small=name+(std>0?" · std "+fmt(std)+"%":"");
        circle.setText(Ui.twoLine(big, small));
        circle.setTextSize(22);
        circle.setTypeface(null,Typeface.BOLD);
        int stroke;
        if(std<=0){
            stroke=Ui.NAVY_TEXT;
            circle.setTextColor(Ui.NAVY_TEXT);
        }else if(actual<=std){
            stroke=Color.parseColor("#2E7D32");
            circle.setTextColor(Color.parseColor("#1B5E20"));
        }else if(actual<=std+2.0){
            stroke=Color.parseColor("#F9A825");
            circle.setTextColor(Color.parseColor("#F57F17"));
        }else{
            stroke=Color.parseColor("#C62828");
            circle.setTextColor(Color.parseColor("#B71C1C"));
        }
        circle.setBackground(Ui.shape(this,Ui.CARD,stroke,48));
    }catch(Exception e){
        circle.setText(line);
    }
 }

 private void addProcessLossLiveCard(){
    try{
        List<JSONObject> products=db.rows("SELECT code,name FROM products ORDER BY code",null);
        List<String> lines=new ArrayList<>();
        for(JSONObject p:products){
            int code=p.optInt("code");
            JSONObject y=db.one("SELECT input_kg,output_kg,formula,standard_pct,enabled FROM process_loss_defs WHERE product_code=?",new String[]{String.valueOf(code)});
            if(y==null||y.optInt("enabled",1)==0)continue;
            String formula=y.optString("formula","");
            if(formula==null||formula.trim().isEmpty())continue;
            double in=y.optDouble("input_kg",0), out=y.optDouble("output_kg",0);
            Double pct=evalProcessLossFormula(formula,in,out);
            if(pct==null)continue;
            double std=y.optDouble("standard_pct",0);
            // name|actual|standard  (for color + display)
            lines.add(p.optString("name","Product "+code)+"|"+fmt(pct)+"|"+fmt(std));
        }
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(12),dp(14),dp(12));
        card.setBackground(Ui.shape(this,Color.parseColor("#FFF3E0"),Ui.LINE,16));
        card.addView(dashIconTitle("📉", "Process Loss %", Color.rgb(230, 126, 34)));
        TextView circle=new TextView(this);
        circle.setGravity(Gravity.CENTER);
        circle.setTextSize(22);
        circle.setTypeface(null,Typeface.BOLD);
        circle.setTextColor(Ui.NAVY_TEXT);
        circle.setPadding(dp(8),dp(10),dp(8),dp(10));
        circle.setBackground(Ui.shape(this,Ui.CARD,Color.parseColor("#E65100"),48));
        circle.setMinHeight(dp(72));
        if(lines.isEmpty()){
            circle.setText("Set in More → Process Loss %");
            circle.setTextSize(14);
            circle.setTypeface(null,Typeface.NORMAL);
            circle.setTextColor(Ui.NAVY_TEXT);
            circle.setBackground(Ui.shape(this,Ui.CARD,Ui.LINE,48));
        }else{
            applyProcessLossLine(circle, lines.get(0));
        }
        card.addView(circle);
        card.addView(Ui.muted(this,"Rotates every "+fmt(liveGap())+"s · formula in Process Loss sheet"));
        add(card);
        if(lines.size()<=1)return;
        final int[] pos={0};
        final long gap=(long)(liveGap()*1000);
        final Runnable[] task=new Runnable[1];
        task[0]=()->{
            if(!circle.isAttachedToWindow())return;
            pos[0]=(pos[0]+1)%lines.size();
            circle.animate().cancel();circle.setAlpha(0.2f);
            applyProcessLossLine(circle, lines.get(pos[0]));
            circle.animate().alpha(1f).setDuration(200).start();
            main.postDelayed(task[0],gap);
        };
        dashboardRunnables.add(task[0]);
        main.postDelayed(task[0],gap);
    }catch(Exception e){/* non-fatal */}
 }

 private Double evalProcessLossFormula(String formula,double inputKg,double outputKg){
    try{
        double loss=inputKg-outputKg;
        String f=formula.toLowerCase(Locale.US).trim();
        f=f.replace("input_kg",String.valueOf(inputKg));
        f=f.replace("output_kg",String.valueOf(outputKg));
        f=f.replace("input",String.valueOf(inputKg));
        f=f.replace("output",String.valueOf(outputKg));
        f=f.replace("loss",String.valueOf(loss));
        f=f.replace("×","*").replace("÷","/").replace(" ","");
        if(f.isEmpty())return null;
        double v=evalSimple(f);
        if(Double.isNaN(v)||Double.isInfinite(v))return null;
        return Math.round(v*100.0)/100.0;
    }catch(Exception e){return null;}
 }

 private void showProcessLossSheet(){
    clear("Process Loss % sheet");
    if(!admin()){toast("Admin/Editor only");return;}
    try{
        LinearLayout intro=Ui.card(this,Ui.CARD);
        intro.addView(Ui.text(this,"Process Loss formulas",16,Ui.NAVY_TEXT,true));
        intro.addView(Ui.muted(this,"Per product: Input kg, Output kg, and formula. Tokens: input, output, loss (=input−output). Example: (input-output)*100/input   or   loss*100/input"));
        add(intro);
        for(JSONObject p:db.rows("SELECT code,name FROM products ORDER BY code",null)){
            int code=p.optInt("code");
            JSONObject y=db.one("SELECT * FROM process_loss_defs WHERE product_code=?",new String[]{String.valueOf(code)});
            LinearLayout card=Ui.card(this,Ui.CARD);
            card.addView(Ui.text(this,code+" — "+p.optString("name"),15,Ui.NAVY_TEXT,true));
            final EditText inEt=edit("Input kg");
            final EditText outEt=edit("Output kg");
            final EditText stdEt=edit("Standard process loss %");
            final EditText fEt=edit("Formula");
            inEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            outEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            stdEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            if(y!=null){
                inEt.setText(fmt(y.optDouble("input_kg",0)));
                outEt.setText(fmt(y.optDouble("output_kg",0)));
                stdEt.setText(fmt(y.optDouble("standard_pct",0)));
                fEt.setText(y.optString("formula",""));
            }else{
                fEt.setText("(input-output)*100/input");
                stdEt.setText("0");
            }
            final android.widget.CheckBox en=new android.widget.CheckBox(this);
            en.setText("Show on dashboard");
            en.setChecked(y==null||y.optInt("enabled",1)==1);
            card.addView(Ui.muted(this,"Input kg")); card.addView(inEt);
            card.addView(Ui.muted(this,"Output kg")); card.addView(outEt);
            card.addView(Ui.muted(this,"Standard process loss %")); card.addView(stdEt);
            card.addView(Ui.muted(this,"Formula (actual loss %)")); card.addView(fEt);
            card.addView(en);
            Double preview=evalProcessLossFormula(fEt.getText().toString(), num(inEt.getText().toString()), num(outEt.getText().toString()));
            TextView prev=Ui.text(this,"Preview: "+(preview==null?"—":fmt(preview)+"%"),14,Ui.INK,true);
            card.addView(prev);
            final int fcode=code;
            Button save=btn("Save");
            save.setOnClickListener(v->{
                Guard.run(this,db,"Save process loss",true,()->{
                    try{
                        ContentValues z=new ContentValues();
                        z.put("product_code",fcode);
                        z.put("input_kg",num(inEt.getText().toString()));
                        z.put("output_kg",num(outEt.getText().toString()));
                        z.put("formula",fEt.getText().toString().trim());
                        z.put("standard_pct",num(stdEt.getText().toString()));
                        z.put("enabled",en.isChecked()?1:0);
                        JSONObject exists=db.one("SELECT product_code FROM process_loss_defs WHERE product_code=?",new String[]{String.valueOf(fcode)});
                        if(exists==null)db.insert("process_loss_defs",z);
                        else db.update("process_loss_defs",z,"product_code=?",new String[]{String.valueOf(fcode)});
                        db.audit(user(),role(),"process_loss",String.valueOf(fcode));
                        toast("Saved ✔");
                        showProcessLossSheet();
                    }catch(Exception e){error(e);}
                });
            });
            card.addView(save);
            add(card);
        }
    }catch(Exception e){error(e);}
 }


 /** Small colored icon chip + title for dashboard cards (keeps current layout). */
 private LinearLayout dashIconTitle(String icon, String title, int accent){
    LinearLayout row=new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0,0,0,dp(6));
    TextView chip=Ui.text(this,icon,14,Color.WHITE,true);
    chip.setGravity(Gravity.CENTER);
    chip.setPadding(dp(8),dp(6),dp(8),dp(6));
    chip.setBackground(Ui.shape(this,accent,accent,10));
    row.addView(chip);
    TextView t=Ui.text(this,title,14,Ui.NAVY_TEXT,true);
    t.setPadding(dp(10),0,0,0);
    row.addView(t,new LinearLayout.LayoutParams(0,-2,1));
    return row;
 }

 private void addYieldLiveCard(){
    try{
        List<JSONObject> products=db.rows("SELECT code,name FROM products ORDER BY code",null);
        List<String> lines=new ArrayList<>();
        for(JSONObject p:products){
            int code=p.optInt("code");
            JSONObject y=db.one("SELECT actual,standard,formula,enabled,stage FROM yield_stages WHERE product_code=? AND enabled=1 AND formula<>'' ORDER BY CASE stage WHEN 'pack' THEN 1 WHEN 'baking' THEN 2 ELSE 3 END LIMIT 1",new String[]{String.valueOf(code)});
            if(y==null) y=db.one("SELECT actual,standard,formula,enabled FROM yield_defs WHERE product_code=?",new String[]{String.valueOf(code)});
            if(y==null||y.optInt("enabled",1)==0)continue;
            double actual=y.optDouble("actual",0), standard=y.optDouble("standard",0);
            String formula=y.optString("formula","");
            if(formula==null||formula.trim().isEmpty())continue;
            Double pct=evalYieldFormula(formula,actual,standard);
            if(pct==null)continue;
            lines.add(p.optString("name","Product "+code)+"  "+fmt(pct)+"%");
        }
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(12),dp(14),dp(12));
        card.setBackground(Ui.shape(this,Ui.SOFT_PURPLE,Ui.LINE,16));
        card.addView(dashIconTitle("📈", lbl("Yield %"), Color.rgb(123, 63, 196)));
        // circle-style big value
        TextView circle=new TextView(this);
        circle.setGravity(Gravity.CENTER);
        circle.setTextSize(22);
        circle.setTypeface(null,Typeface.BOLD);
        circle.setTextColor(Ui.NAVY_TEXT);
        circle.setPadding(dp(8),dp(10),dp(8),dp(10));
        circle.setBackground(Ui.shape(this,Ui.CARD,Ui.NAVY_TEXT,48));
        circle.setMinHeight(dp(72));
        if(lines.isEmpty()){
            circle.setText("Set yield in More → Yield % sheet");
            circle.setTextSize(14);
            circle.setTypeface(null,Typeface.NORMAL);
        }else{
            circle.setText(Ui.twoLine(lines.get(0).substring(lines.get(0).lastIndexOf("  ")+2), lines.get(0).substring(0,Math.max(0,lines.get(0).lastIndexOf("  ")))));
        }
        card.addView(circle);
        TextView hint=Ui.muted(this,"Rotates every "+fmt(liveGap())+"s · edit logic in Yield % sheet");
        card.addView(hint);
        add(card);
        if(lines.size()<=1)return;
        final int[] pos={0};
        final long gap=(long)(liveGap()*1000);
        final Runnable[] task=new Runnable[1];
        task[0]=()->{
            if(!circle.isAttachedToWindow())return;
            pos[0]=(pos[0]+1)%lines.size();
            String x=lines.get(pos[0]);
            int k=x.lastIndexOf("  ");
            CharSequence t=k<0?x:Ui.twoLine(x.substring(k+2),x.substring(0,k));
            circle.animate().cancel();circle.setAlpha(0.2f);
            circle.setText(t);
            circle.animate().alpha(1f).setDuration(200).start();
            main.postDelayed(task[0],gap);
        };
        dashboardRunnables.add(task[0]);
        main.postDelayed(task[0],gap);
    }catch(Exception e){/* non-fatal */}
 }
 private Double evalYieldFormula(String formula,double dryOutput,double inputKg){
    return evalYieldFormulaEx(formula,dryOutput,inputKg,0,0,0);
 }
 private Double evalYieldFormulaEx(String formula,double dryOutput,double inputKg,double moisture,double wastage,double batchQty){
    try{
        String f=formula.toLowerCase(Locale.US).trim();
        // Replace longer tokens first
        f=f.replace("dry_output",String.valueOf(dryOutput));
        f=f.replace("input_kg",String.valueOf(inputKg));
        f=f.replace("wastage",String.valueOf(wastage));
        f=f.replace("moisture",String.valueOf(moisture));
        f=f.replace("batch",String.valueOf(batchQty));
        f=f.replace("input",String.valueOf(inputKg));
        f=f.replace("actual",String.valueOf(dryOutput));
        f=f.replace("standard",String.valueOf(inputKg));
        f=f.replace("×","*").replace("÷","/").replace(" ","");
        if(f.isEmpty())return null;
        double v=evalSimple(f);
        if(Double.isNaN(v)||Double.isInfinite(v))return null;
        return Math.round(v*100.0)/100.0;
    }catch(Exception e){return null;}
 }
 private double todayWastageTotal(){
    try{
        // wastage stored as receives with note tag or separate - try counts/note heuristic
        JSONObject o=db.one("SELECT COALESCE(SUM(qty),0) q FROM receives WHERE date=? AND (note LIKE '%wast%' OR note LIKE '%Wast%' OR source='wastage')",new String[]{today()});
        return o==null?0:o.optDouble("q",0);
    }catch(Exception e){return 0;}
 }
 private double todayBatchTotal(){
    try{JSONObject o=db.one("SELECT COALESCE(SUM(qty),0) q FROM batches WHERE date=?",new String[]{today()});return o==null?0:o.optDouble("q",0);}catch(Exception e){return 0;}
 }

 /** Supports numbers, + - * / and parentheses only. */
 private double evalSimple(String expr){
    return new Object(){
        int i=0;
        double parse(){double v=term();while(i<expr.length()){char c=expr.charAt(i);if(c=='+'){i++;v+=term();}else if(c=='-'){i++;v-=term();}else break;}return v;}
        double term(){double v=factor();while(i<expr.length()){char c=expr.charAt(i);if(c=='*'){i++;v*=factor();}else if(c=='/'){i++;double d=factor();v=d==0?Double.NaN:v/d;}else break;}return v;}
        double factor(){
            if(i<expr.length()&&expr.charAt(i)=='('){i++;double v=parse();if(i<expr.length()&&expr.charAt(i)==')')i++;return v;}
            int start=i;if(i<expr.length()&&(expr.charAt(i)=='+'||expr.charAt(i)=='-'))i++;
            while(i<expr.length()&&(Character.isDigit(expr.charAt(i))||expr.charAt(i)=='.'))i++;
            if(start==i)throw new RuntimeException("bad");
            return Double.parseDouble(expr.substring(start,i));
        }
    }.parse();
 }
 private void showYieldSheet(){
    clear("Yield % sheet");
    try{
        final String[] STAGES=new String[]{"mixing","baking","pack"};
        final String[] STAGE_LBL=new String[]{"Mixing","Baking","Pack"};
        LinearLayout intro=Ui.card(this,Ui.CARD);
        intro.addView(Ui.text(this,"Multi-stage yield (not hardcoded)",16,Ui.NAVY_TEXT,true));
        intro.addView(Ui.muted(this,"Each product has Mixing / Baking / Pack. Enter Dry Output + Input + formula per stage. Tokens: dry_output, input, moisture, wastage, batch."));
        add(intro);

        for(JSONObject p:db.rows("SELECT code,name FROM products ORDER BY code",null)){
            final int code=p.optInt("code");
            String name=p.optString("name");
            LinearLayout card=Ui.card(this,Ui.CARD);
            card.addView(Ui.text(this,code+" · "+name,16,Ui.NAVY_TEXT,true));
            Double chain=1.0; boolean chainOk=true;
            for(int si=0;si<STAGES.length;si++){
                final String stage=STAGES[si];
                JSONObject y=db.one("SELECT actual,standard,formula,enabled FROM yield_stages WHERE product_code=? AND stage=?",new String[]{String.valueOf(code),stage});
                if(y==null){
                    // fallback yield_defs for mixing only
                    if("mixing".equals(stage)){
                        y=db.one("SELECT actual,standard,formula,enabled FROM yield_defs WHERE product_code=?",new String[]{String.valueOf(code)});
                    }
                }
                double actual=y==null?0:y.optDouble("actual",0);
                double standard=y==null?0:y.optDouble("standard",0);
                String formula=y==null?"":y.optString("formula","");
                card.addView(Ui.text(this,STAGE_LBL[si],14,Ui.MUTED,true));
                final EditText aEt=edit("Dry Output kg");aEt.setText(actual==0&&y==null?"":fmt(actual));
                aEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                final EditText sEt=edit("Input kg");sEt.setText(standard==0&&y==null?"":fmt(standard));
                sEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                final EditText fEt=edit("Formula");fEt.setText(formula);
                card.addView(Ui.muted(this,"Dry Output kg"));card.addView(aEt);
                card.addView(Ui.muted(this,"Input kg"));card.addView(sEt);
                card.addView(Ui.muted(this,"Formula"));card.addView(fEt);
                Double pct=null;
                if(formula!=null&&!formula.trim().isEmpty()){
                    pct=evalYieldFormulaEx(formula,actual,standard,0,todayWastageTotal(),todayBatchTotal());
                    if(pct!=null) chain*=pct/100.0; else chainOk=false;
                } else chainOk=false;
                TextView preview=Ui.text(this,STAGE_LBL[si]+" preview: "+(pct==null?"—":fmt(pct)+"%"),13,pct!=null&&pct>=95?Ui.GOOD:(pct!=null&&pct<80?Ui.BAD:Ui.INK),true);
                card.addView(preview);
                Button save=Ui.primary(this,"Save "+STAGE_LBL[si]);
                final String savedStageLabel = STAGE_LBL[si];
                save.setOnClickListener(v->{
                    try{
                        double a=num(aEt.getText().toString());
                        double st=num(sEt.getText().toString());
                        String fo=fEt.getText().toString().trim();
                        if(fo.isEmpty()){toast("Enter formula for "+stage);return;}
                        Double check=evalYieldFormulaEx(fo,a,st,0,todayWastageTotal(),todayBatchTotal());
                        if(check==null){toast("Invalid formula");return;}
                        ContentValues cv=new ContentValues();
                        cv.put("product_code",code);cv.put("stage",stage);cv.put("actual",a);cv.put("standard",st);cv.put("formula",fo);cv.put("enabled",1);
                        db.getWritableDatabase().insertWithOnConflict("yield_stages",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
                        if("mixing".equals(stage)){
                            ContentValues leg=new ContentValues();
                            leg.put("product_code",code);leg.put("actual",a);leg.put("standard",st);leg.put("formula",fo);leg.put("enabled",1);leg.put("stage","mixing");
                            db.getWritableDatabase().insertWithOnConflict("yield_defs",null,leg,SQLiteDatabase.CONFLICT_REPLACE);
                        }
                        ContentValues h=new ContentValues();
                        h.put("date",today());h.put("product_code",code);h.put("stage",stage);
                        h.put("dry_output",a);h.put("input_kg",st);h.put("moisture",0);
                        h.put("wastage",todayWastageTotal());h.put("batch_qty",todayBatchTotal());
                        h.put("formula",fo);h.put("result",check);h.put("note","");h.put("user",user());
                        try{db.insert("yield_history",h);}catch(Exception ignored){}
                        db.audit(user(),role(),"yield_save",name+"/"+stage+"="+fmt(check)+"%");
                        toast(savedStageLabel+" saved · "+fmt(check)+"%");
                        showYieldSheet();
                    }catch(Exception e){error(e);}
                });
                card.addView(save);
            }
            if(chainOk) card.addView(Ui.text(this,"Overall (Mixing×Baking×Pack): "+fmt(chain*100)+"%",15,Ui.NAVY_TEXT,true));
            else card.addView(Ui.muted(this,"Overall: complete all three stage formulas to see chain %"));
            add(card);
        }

        LinearLayout hist=Ui.card(this,Ui.CARD);
        hist.addView(Ui.text(this,"Yield history (recent)",15,Ui.NAVY_TEXT,true));
        try{
            for(JSONObject h:db.rows("SELECT h.*,p.name FROM yield_history h LEFT JOIN products p ON p.code=h.product_code ORDER BY h.id DESC LIMIT 40",null)){
                hist.addView(Ui.text(this,h.optString("date")+" · "+h.optString("name",h.optString("product_code"))+" · "+fmt(h.optDouble("result"))+"%  ["+h.optString("stage")+"]",13,Ui.INK,false));
            }
        }catch(Exception ignored){}
        add(hist);
    }catch(Exception e){error(e);}
}

 private void shareUnifiedBackup(){
    try{
        JSONObject all=new JSONObject();
        all.put("version",5);
        all.put("app","Inventory Management");
        all.put("company",db.setting("company",""));
        JSONArray tables=new JSONArray();
        String[] ts={"rms","products","recipes","batches","receives","approvals","custom_sheets","custom_cols","custom_rows","mixing2_batch_log","counts","settings","yield_defs","yield_stages","yield_history","audit_log","recipe_archive"};
        for(String t:ts){
            JSONObject z=new JSONObject();z.put("table",t);z.put("rows",new JSONArray());
            try{for(JSONObject r:db.rows("SELECT * FROM "+t,null))z.getJSONArray("rows").put(r);}catch(Exception ignored){}
            tables.put(z);
        }
        all.put("tables",tables);
        try{PmDb pm=new PmDb(this);all.put("pm",new JSONObject(pm.exportJson()));}catch(Exception pe){all.put("pm",JSONObject.NULL);}
        File f=new File(getCacheDir(),"inventory-backup-"+today()+".json");
        try(FileOutputStream o=new FileOutputStream(f)){o.write(all.toString(2).getBytes("UTF-8"));}
        Uri uri=FileProvider.getUriForFile(this,"com.rmstock.nativeapp.fileprovider",f);
        Intent intent=new Intent(Intent.ACTION_SEND);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_STREAM,uri);
        intent.putExtra(Intent.EXTRA_SUBJECT,"Inventory backup "+today());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent,"Share unified backup"));
        db.audit(user(),role(),"share_backup",f.getName());
    }catch(Exception e){error(e);}
}

 private void backupJson(){try{
    JSONObject all=new JSONObject();
    all.put("version",5);
    all.put("app","Inventory Management");
    all.put("company",db.setting("company",""));
    JSONArray tables=new JSONArray();
    String[] ts={"rms","products","recipes","batches","receives","approvals","custom_sheets","custom_cols","custom_rows","mixing2_batch_log","counts","settings","yield_defs","yield_stages","yield_history","audit_log","recipe_archive"};
    for(String t:ts){
        JSONObject z=new JSONObject();z.put("table",t);z.put("rows",new JSONArray());
        try{for(JSONObject r:db.rows("SELECT * FROM "+t,null))z.getJSONArray("rows").put(r);}catch(Exception ignored){}
        tables.put(z);
    }
    all.put("tables",tables);
    try{PmDb pm=new PmDb(this);all.put("pm",new JSONObject(pm.exportJson()));}catch(Exception pe){all.put("pm",JSONObject.NULL);}
    File f=new File(getCacheDir(),"inventory-backup.json");
    try(FileOutputStream o=new FileOutputStream(f)){o.write(all.toString(2).getBytes("UTF-8"));}
    pendingPdf=f;
    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");
    i.putExtra(Intent.EXTRA_TITLE,"inventory-backup-"+today()+".json");
    startActivityForResult(i,80);
}catch(Exception e){error(e);}}
 private void restoreJson(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,81);}
 private void importBackup(Uri u){try{InputStream in=getContentResolver().openInputStream(u);ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] z=new byte[8192];int n;while((n=in.read(z))>0)b.write(z,0,n);in.close();JSONObject all=new JSONObject(b.toString("UTF-8"));if(!all.has("tables"))throw new Exception("Invalid backup");new AlertDialog.Builder(this).setTitle("Restore unified backup?").setMessage("RM data, yield sheet and PM (if included) will be replaced. Continue?").setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->{try{restoreTables(all.getJSONArray("tables"));if(all.has("pm")&&all.opt("pm")!=JSONObject.NULL){try{new PmDb(this).importJson(all.get("pm").toString());}catch(Exception pe){toast("RM restored; PM: "+pe.getMessage());}}toast("Backup restored ✔");build();AlertScheduler.schedule(this);requestNotifPermission();showDashboard();}catch(Exception e){error(e);}}).show();}catch(Exception e){error(e);}}
 private void restoreTables(JSONArray tables)throws Exception{SQLiteDatabase x=db.getWritableDatabase();x.beginTransaction();try{String[] ts={"counts","receives","mixing2_batch_log","batches","custom_rows","custom_cols","custom_sheets","approvals","recipes","products","rms","settings","yield_defs","yield_stages","yield_history","audit_log","recipe_archive"};for(String t:ts)x.delete(t,null,null);for(int i=0;i<tables.length();i++){JSONObject t=tables.getJSONObject(i);String tn=t.getString("table");JSONArray rs=t.getJSONArray("rows");for(int j=0;j<rs.length();j++){JSONObject r=rs.getJSONObject(j);ContentValues v=new ContentValues();Iterator<String> it=r.keys();while(it.hasNext()){String k=it.next();Object val=r.get(k);if(val==JSONObject.NULL)continue;v.put(k,String.valueOf(val));}x.insertWithOnConflict(tn,null,v,SQLiteDatabase.CONFLICT_REPLACE);}}x.setTransactionSuccessful();}finally{x.endTransaction();}}
 private void exportCsv(){try{File f=new File(getCacheDir(),"rmstock-export.csv");FileOutputStream o=new FileOutputStream(f);String[] ts={"rms","products","recipes","batches","receives","custom_sheets","custom_cols","custom_rows","mixing2_batch_log","counts"};for(String t:ts){o.write(("\n["+t+"]\n").getBytes("UTF-8"));List<JSONObject> rs=db.rows("SELECT * FROM "+t,null);if(!rs.isEmpty()){JSONArray a=rs.get(0).names();for(int j=0;j<a.length();j++){if(j>0)o.write(',');o.write(csv(a.optString(j)).getBytes("UTF-8"));}o.write('\n');for(JSONObject r:rs){for(int j=0;j<a.length();j++){if(j>0)o.write(',');o.write(csv(r.optString(a.optString(j))).getBytes("UTF-8"));}o.write('\n');}}}o.close();Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/csv");i.putExtra(Intent.EXTRA_STREAM,FileProvider.getUriForFile(this,"com.rmstock.nativeapp.fileprovider",f));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Export CSV"));}catch(Exception e){error(e);}}
 private String csv(String s){return "\""+s.replace("\"","\"\"")+"\"";}
 private void showAI(){
    clear("Offline AI Agent — Voucher Draft");
    add(Ui.muted(this,"Scan voucher → offline OCR → smart RM match → Draft → Admin approve → Daily Entry. Never writes stock before approval."));
    Button b=btn("📷 Scan Voucher");
    b.setOnClickListener(v->startCamera());
    add(b);
    add(Ui.muted(this,"Tips: good light, flat paper, full voucher in frame. Matching uses your RM master list (offline)."));
 }
 private void startCamera(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},CAM);return;}try{File dir=new File(getCacheDir(),"images");dir.mkdirs();File f=File.createTempFile("voucher_",".jpg",dir);photoFile=f;photoUri=FileProvider.getUriForFile(this,"com.rmstock.nativeapp.fileprovider",f);Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(MediaStore.EXTRA_OUTPUT,photoUri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);startActivityForResult(i,PHOTO);}catch(Exception e){error(e);}}
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
    if(r==LOC_PERM){
        boolean dutyOk=false;
        if(g!=null){
            for(int gi=0;gi<g.length;gi++){
                if(g[gi]==android.content.pm.PackageManager.PERMISSION_GRANTED) dutyOk=true;
            }
        }
        if(dutyOk) startDutyServiceSafe();
    }

super.onRequestPermissionsResult(r,p,g);if(r==CAM&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startCamera();}
 @Override protected void onPause(){super.onPause();try{StockWidget.refresh(this);}catch(Exception ignored){}}
 @Override protected void onActivityResult(int r,int c,@Nullable Intent d){super.onActivityResult(r,c,d);if(Guard.onResult(r,c))return;if(r==PHOTO&&c==RESULT_OK&&photoUri!=null)ocr(photoUri); if(r==CHAT_IMG&&c==RESULT_OK){ Uri u=(d!=null&&d.getData()!=null)?d.getData():photoUri; if(u!=null) sendChatAttachment(u,true);} if(r==CHAT_FILE&&c==RESULT_OK&&d!=null&&d.getData()!=null) sendChatAttachment(d.getData(),false); if(r==VOICE_FIX&&c==RESULT_OK&&d!=null){java.util.ArrayList<String> spoken=d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(spoken!=null&&!spoken.isEmpty()){toast(spoken.get(0));runAssistantCommand(spoken.get(0));}} if(r==79&&c==RESULT_OK&&d!=null&&pendingPdf!=null){try{OutputStream out=getContentResolver().openOutputStream(d.getData());FileInputStream in=new FileInputStream(pendingPdf);byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);in.close();out.close();toast("PDF saved ✔");}catch(Exception e){error(e);}} if(r==82&&c==RESULT_OK&&d!=null&&pendingPdf!=null){try{OutputStream out=getContentResolver().openOutputStream(d.getData());FileInputStream in=new FileInputStream(pendingPdf);byte[] z=new byte[8192];int n;while((n=in.read(z))>0)out.write(z,0,n);in.close();out.close();toast("CSV saved — open with Excel ✔");}catch(Exception e){error(e);}} if(r==80&&c==RESULT_OK&&d!=null&&pendingPdf!=null){try{OutputStream out=getContentResolver().openOutputStream(d.getData());FileInputStream in=new FileInputStream(pendingPdf);byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);in.close();out.close();toast("Backup saved ✔");}catch(Exception e){error(e);}} if(r==81&&c==RESULT_OK&&d!=null)importBackup(d.getData()); if(r==83&&c==RESULT_OK&&d!=null){try{InputStream in=getContentResolver().openInputStream(d.getData());new PmDb(this).importJson(new String(readAll(in),"UTF-8"));toast("PM import OK");}catch(Exception e){error(e);}}}
 private byte[] readAll(InputStream in)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] z=new byte[8192];int n;while((n=in.read(z))>0)b.write(z,0,n);in.close();return b.toByteArray();}
 private void ocr(Uri u){toast("OCR running offline…");new Thread(()->{String txt;try{InputStream in=getContentResolver().openInputStream(u);Bitmap bm=BitmapFactory.decodeStream(in);in.close();File base=new File(getFilesDir(),"tessdata");if(!new File(base,"eng.traineddata").exists()){base.mkdirs();for(String lang:new String[]{"eng","ben"}){InputStream a=getAssets().open("tessdata/"+lang+".traineddata");File out=new File(base,lang+".traineddata");FileOutputStream o=new FileOutputStream(out);byte[] buf=new byte[8192];int n;while((n=a.read(buf))>0)o.write(buf,0,n);a.close();o.close();}}TessBaseAPI api=new TessBaseAPI();if(!api.init(getFilesDir().getAbsolutePath(),"eng+ben"))throw new Exception("OCR init failed");api.setImage(bm);txt=api.getUTF8Text();api.recycle();bm.recycle();}catch(Exception e){txt="OCR_ERROR: "+e.getMessage();}final String z=txt;main.post(()->showAIDraft(z));}).start();}
 private void showAIDraft(String raw){
    clear("AI Draft — Admin Approval");
    try{
        String clean=cleanOcrText(raw);
        String dateGuess=findDate(clean);
        String vchGuess=findVoucher(clean);
        String qtyGuess=findQty(clean);
        List<String[]> matches=matchRmCandidates(clean);
        String bestCode=matches.isEmpty()?"":matches.get(0)[0];

        LinearLayout tip=Ui.card(this,Ui.CARD);
        tip.addView(Ui.text(this,"Smart draft (offline)",15,Ui.NAVY_TEXT,true));
        tip.addView(Ui.muted(this,"Fields pre-filled from OCR + RM master fuzzy match. Edit anything before approve."));
        if(!matches.isEmpty()){
            StringBuilder sb=new StringBuilder("RM suggestions:\n");
            for(int i=0;i<Math.min(5,matches.size());i++){
                String[] m=matches.get(i);
                sb.append("  ").append(i+1).append(") ").append(m[0]).append(" — ").append(m[1]).append("  (").append(m[2]).append(")\n");
            }
            tip.addView(Ui.text(this,sb.toString().trim(),13,Ui.INK,false));
        } else tip.addView(Ui.muted(this,"No strong RM match — type code manually or pick from list."));
        add(tip);

        final EditText date=dateField("Date"); date.setText(dateGuess);
        final EditText voucher=edit("Voucher No"); voucher.setText(vchGuess);
        final EditText rm=edit("RM Code"); rm.setText(bestCode);
        final EditText qty=edit("Quantity"); qty.setText(qtyGuess.equals("0")?"":qtyGuess);
        add(Ui.muted(this,"Date"));add(date);
        add(Ui.muted(this,"Voucher"));add(voucher);
        add(Ui.muted(this,"RM Code (or tap a suggestion)"));add(rm);
        if(!matches.isEmpty()){
            LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.VERTICAL);
            for(int i=0;i<Math.min(5,matches.size());i++){
                final String[] m=matches.get(i);
                Button ch=Ui.secondary(this,m[0]+" — "+m[1]+"  ·  "+m[2]);
                ch.setAllCaps(false);
                ch.setOnClickListener(v->{rm.setText(m[0]);toast("Selected "+m[0]);});
                chips.addView(ch,Ui.lp(this,-1,dp(44),0,0,0,0,4));
            }
            add(chips);
        }
        add(Ui.muted(this,"Quantity"));add(qty);

        TextView o=new TextView(this);
        o.setText("OCR text (cleaned):\n"+clean);
        o.setTextSize(12);o.setPadding(0,dp(12),0,dp(12));
        add(o);

        Button approve=btn("Approve & Save to Daily Entry");
        approve.setOnClickListener(v->{
            if(!admin())return;
            try{
                String code=rm.getText().toString().trim();
                if(db.one("SELECT code FROM rms WHERE code=?",new String[]{code})==null)
                    throw new Exception("RM Code not found in master");
                String day=date.getText().toString().trim();
                if(db.monthLocked(day)){toast("Month locked");return;}
                double qv=num(qty.getText().toString());
                if(qv<=0)throw new Exception("Quantity must be > 0");
                ContentValues x=new ContentValues();
                x.put("date",day);x.put("rm_code",code);x.put("qty",qv);
                x.put("note","Offline AI approved");x.put("voucher_no",voucher.getText().toString().trim());
                x.put("source","offline-ai");x.put("photo",saveVoucherPhoto());
                db.insert("receives",x);
                db.audit(user(),role(),"ai_approve",code+" qty="+fmt(qv));
                toast("AI Draft approved ✔");
                showDaily();
            }catch(Exception e){error(e);}
        });
        add(approve);
    }catch(Exception e){error(e);}
 }
 private String cleanOcrText(String s){
    if(s==null)return "";
    s=s.replace('\u00A0',' ');
    s=s.replaceAll("[ \\t]+"," ");
    s=s.replaceAll(" *\\n *","\n");
    return s.trim();
 }
 private String findDate(String s){
    java.util.regex.Matcher m=java.util.regex.Pattern.compile("(20\\d{2}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/.]\\d{1,2}[-/.]20\\d{2})").matcher(s);
    if(m.find()){
        String x=m.group(1).replace('/','-').replace('.','-');
        String[] z=x.split("-");
        try{
            if(z[0].length()==4)return String.format(Locale.US,"%04d-%02d-%02d",Integer.parseInt(z[0]),Integer.parseInt(z[1]),Integer.parseInt(z[2]));
            return String.format(Locale.US,"%04d-%02d-%02d",Integer.parseInt(z[2]),Integer.parseInt(z[1]),Integer.parseInt(z[0]));
        }catch(Exception ignored){}
    }
    // dd MMM yyyy
    m=java.util.regex.Pattern.compile("(?i)(\\d{1,2})\\s*(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\s*(20\\d{2})").matcher(s);
    if(m.find()){
        String[] mon={"jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec"};
        int mi=0; String mm=m.group(2).toLowerCase(Locale.US);
        for(int i=0;i<12;i++)if(mm.startsWith(mon[i])){mi=i+1;break;}
        return String.format(Locale.US,"%04d-%02d-%02d",Integer.parseInt(m.group(3)),mi,Integer.parseInt(m.group(1)));
    }
    return today();
 }
 private String findVoucher(String s){
    java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)(voucher|vch|challan|bill|inv|invoice)[^A-Za-z0-9]{0,10}([A-Z0-9][A-Z0-9\\-/]{2,})").matcher(s);
    if(m.find())return m.group(2);
    m=java.util.regex.Pattern.compile("\\b([A-Z]{1,4}[-/]?\\d{3,})\\b").matcher(s);
    return m.find()?m.group(1):"";
 }
 private String findQty(String s){
    java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)(qty|quantity|qnty|q\\.ty|weight|wt|kg)[^0-9]{0,12}([0-9]+(?:[\\.,][0-9]+)?)").matcher(s);
    if(m.find())return m.group(2).replace(',','.');
    // fallback: largest number that looks like a quantity
    m=java.util.regex.Pattern.compile("\\b([0-9]{1,6}(?:\\.[0-9]{1,3})?)\\b").matcher(s);
    double best=0; String bestS="0";
    while(m.find()){
        try{
            double v=Double.parseDouble(m.group(1));
            if(v>best && v<100000){best=v;bestS=m.group(1);}
        }catch(Exception ignored){}
    }
    return bestS;
 }
 /** Returns list of {code, name, scoreLabel} sorted by score desc */
 private List<String[]> matchRmCandidates(String ocr){
    List<String[]> out=new ArrayList<>();
    try{
        String low=ocr.toLowerCase(Locale.US);
        for(JSONObject r:db.rows("SELECT code,name FROM rms",null)){
            String code=r.optString("code");
            String name=r.optString("name");
            String nlow=name.toLowerCase(Locale.US);
            int score=0;
            if(low.contains(code.toLowerCase(Locale.US))) score+=100;
            if(nlow.length()>=3 && low.contains(nlow)) score+=80;
            // token overlap
            for(String tok:nlow.split("[^a-z0-9]+")){
                if(tok.length()<3)continue;
                if(low.contains(tok)) score+=15;
            }
            // fuzzy: edit distance on short window
            int dist=fuzzyBest(low, nlow);
            if(dist>=0 && dist<=2 && nlow.length()>=4) score+=40-dist*10;
            if(score>0) out.add(new String[]{code,name,score+" pts"});
        }
        Collections.sort(out,(a,b)->Integer.compare(Integer.parseInt(b[2].split(" ")[0]), Integer.parseInt(a[2].split(" ")[0])));
    }catch(Exception ignored){}
    return out;
 }
 private String findRm(String s){
    List<String[]> m=matchRmCandidates(s);
    return m.isEmpty()?"":m.get(0)[0];
 }
 private int fuzzyBest(String hay,String needle){
    if(needle==null||needle.length()<3||hay==null)return 99;
    int best=99;
    int n=needle.length();
    if(hay.contains(needle))return 0;
    // sample sliding compare for small needles
    if(n>24) n=24;
    String nd=needle.length()>24?needle.substring(0,24):needle;
    for(int i=0;i+nd.length()<=hay.length();i+=Math.max(1,nd.length()/2)){
        String win=hay.substring(i,Math.min(hay.length(),i+nd.length()+2));
        best=Math.min(best, editDist(win, nd));
        if(best==0)return 0;
    }
    return best;
 }
 private int editDist(String a,String b){
    int n=a.length(),m=b.length();
    if(Math.abs(n-m)>4)return 9;
    int[] prev=new int[m+1],cur=new int[m+1];
    for(int j=0;j<=m;j++)prev[j]=j;
    for(int i=1;i<=n;i++){
        cur[0]=i;
        for(int j=1;j<=m;j++){
            int cost=a.charAt(i-1)==b.charAt(j-1)?0:1;
            cur[j]=Math.min(Math.min(cur[j-1]+1,prev[j]+1),prev[j-1]+cost);
        }
        int[] t=prev;prev=cur;cur=t;
    }
    return prev[m];
 }
 private void addSmartSuggestions(){
    try{
        String d=today();
        List<String> tips=new ArrayList<>();
        Map<String,Double> rec=sumMap("SELECT rm_code k,SUM(qty) v FROM receives WHERE date<=? GROUP BY rm_code",new String[]{d});
        Map<String,Double> use=sumMap("SELECT rm_code k,SUM(consumption) v FROM mixing2_batch_log WHERE date<=? GROUP BY rm_code",new String[]{d});
        int low=0,shortN=0;
        for(JSONObject rm:db.rows("SELECT code,name,opening,reorder FROM rms",null)){
            String code=rm.optString("code");
            double cl=rm.optDouble("opening",0)+nz(rec.get(code))-nz(use.get(code));
            double re=rm.optDouble("reorder",0);
            if(cl<0){shortN++; if(tips.size()<6) tips.add("SHORT · "+rm.optString("name")+" ("+fmt(cl)+")");}
            else if(re>0&&cl<=re){low++; if(tips.size()<6) tips.add("Reorder · "+rm.optString("name")+" (stock "+fmt(cl)+", reorder "+fmt(re)+")");}
        }
        int warnDays=(int)Math.round(num(db.setting("expiry_days","14"))); if(warnDays<1)warnDays=14;
        try{
            JSONObject eo=db.one("SELECT COUNT(*) n FROM receives WHERE expiry IS NOT NULL AND expiry<>'' AND expiry<=?",new String[]{PlanLogic.shiftDate(d,warnDays)});
            int expN=eo==null?0:eo.optInt("n",0);
            if(expN>0) tips.add("Expiry window · "+expN+" lot(s) within "+warnDays+" days");
        }catch(Exception ignored){}
        try{
            JSONObject b=db.one("SELECT COUNT(*) n FROM batches WHERE date=?",new String[]{d});
            if(b==null||b.optInt("n",0)==0) tips.add("No batch entry today — open Batch tab");
        }catch(Exception ignored){}
        try{
            JSONObject r=db.one("SELECT COUNT(*) n FROM receives WHERE date=?",new String[]{d});
            if(r==null||r.optInt("n",0)==0) tips.add("No RM receive today");
        }catch(Exception ignored){}
        // yield stage pack missing
        try{
            for(JSONObject pr:db.rows("SELECT code,name FROM products ORDER BY code LIMIT 8",null)){
                JSONObject y=db.one("SELECT formula FROM yield_stages WHERE product_code=? AND stage='pack' AND formula<>''",new String[]{pr.optString("code")});
                if(y==null) tips.add("Yield Pack stage empty · "+pr.optString("name"));
                if(tips.size()>=10)break;
            }
        }catch(Exception ignored){}

        if(tips.isEmpty()){
            LinearLayout ok=Ui.card(this,Ui.SOFT_GREEN);
            ok.addView(Ui.text(this,"Smart tips",14,Ui.NAVY_TEXT,true));
            ok.addView(Ui.muted(this,"No urgent rules fired for "+d+" — stock & batches look OK."));
            add(ok);
            return;
        }
        LinearLayout box=Ui.card(this,Ui.SOFT_AMBER);
        box.addView(Ui.text(this,"Smart tips (rules, offline)",15,Ui.NAVY_TEXT,true));
        box.addView(Ui.muted(this,"Reorder · expiry · missing batch/receive · yield gaps"));
        for(String t:tips) box.addView(Ui.text(this,"• "+t,13,Ui.INK,false));
        if(shortN+low>0) box.addView(Ui.muted(this,"Summary: "+shortN+" short, "+low+" at reorder"));
        add(box);
    }catch(Exception ignored){}
 }
private TextView tv(String s,int dummy){TextView t=new TextView(this);t.setText(s);t.setPadding(dp(6),dp(8),dp(6),dp(8));return t;}
 private String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());} private String fmt(double x){return String.format(Locale.US,"%.3f",x).replaceAll("0+$","").replaceAll("\\.$","");} private double num(String s){try{return Double.parseDouble(s.trim());}catch(Exception e){return 0;}} private void copy(String a,File o)throws Exception{if(o.exists()&&o.length()>0)return;InputStream in=getAssets().open(a);FileOutputStream f=new FileOutputStream(o);byte[] b=new byte[8192];int n;while((n=in.read(b))>0)f.write(b,0,n);in.close();f.close();} private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();} private void error(Exception e){toast(e.getMessage()==null?"Error":e.getMessage());}
}
