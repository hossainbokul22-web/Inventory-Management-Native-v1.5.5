package com.rmstock.nativeapp;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

/** Home-screen widget (English only): how many raw materials need attention, and the worst three. */
public class StockWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { for (int id : ids) m.updateAppWidget(id, build(c)); }

    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, StockWidget.class));
        if (ids != null && ids.length > 0) for (int id : ids) m.updateAppWidget(id, build(c));
    }

    private static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_stock);
        String head = "Stock OK", list = "";
        try {
            StockDb db = new StockDb(c);
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            Map<String, Double> rec = sum(db, "SELECT rm_code k,SUM(qty) v FROM receives WHERE date<=? GROUP BY rm_code", new String[]{today});
            Map<String, Double> use = sum(db, "SELECT rm_code k,SUM(consumption) v FROM mixing2_batch_log WHERE date<=? GROUP BY rm_code", new String[]{today});
            Map<String, Double> u7 = sum(db, "SELECT rm_code k,SUM(consumption) v FROM mixing2_batch_log WHERE date>=? AND date<? GROUP BY rm_code", new String[]{PlanLogic.shiftDate(today, -7), today});
            List<String> lines = new ArrayList<>();
            int n = 0;
            for (JSONObject r : db.rows("SELECT code,name,uom,opening,reorder FROM rms ORDER BY CAST(code AS INTEGER)", null)) {
                String code = r.optString("code");
                double cl = r.optDouble("opening", 0) + nz(rec.get(code)) - nz(use.get(code));
                double avg = nz(u7.get(code)) / 7.0;
                int st = PlanLogic.status(cl, r.optDouble("reorder", 0), avg);
                if (st == PlanLogic.OK) continue;
                n++;
                if (lines.size() < 3) lines.add(r.optString("name") + "  " + String.format(Locale.US, "%.1f", cl) + " " + r.optString("uom"));
            }
            if (n > 0) { head = n + " item" + (n == 1 ? "" : "s") + " need attention"; list = android.text.TextUtils.join("\n", lines); }
            db.close();
        } catch (Exception e) { head = "Open RM Stock"; }
        v.setTextViewText(R.id.w_head, head);
        v.setTextViewText(R.id.w_list, list);
        Intent i = new Intent(c, MainActivity.class);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        return v;
    }

    private static double nz(Double x) { return x == null ? 0 : x; }

    private static Map<String, Double> sum(StockDb db, String sql, String[] a) throws Exception {
        Map<String, Double> m = new HashMap<>();
        for (JSONObject o : db.rows(sql, a)) m.put(o.optString("k"), o.optDouble("v", 0));
        return m;
    }
}
