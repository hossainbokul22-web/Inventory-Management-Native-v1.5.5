package com.rmstock.nativeapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** One admin phone = LAN server; others queue offline and flush on hotspot. */
public final class SyncHub {
    public static final int DEFAULT_PORT = 9876;
    private static SyncHub inst;
    private final Context app;
    private final StockDb db;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final AtomicBoolean serverOn = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private volatile String lastStatus = "Idle";

    public static synchronized SyncHub get(Context c) {
        if (inst == null) inst = new SyncHub(c.getApplicationContext());
        return inst;
    }

    private SyncHub(Context app) {
        this.app = app;
        this.db = new StockDb(app);
        ensureQueueTable();
        ensureChatTable();
        ensureCallSignalTable();
    }

    private void ensureCallSignalTable() {
        try {
            db.getWritableDatabase().execSQL(
                "CREATE TABLE IF NOT EXISTS call_signals(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, payload TEXT, created_at TEXT)");
        } catch (Exception ignored) {}
    }

    public void storeCallSignal(org.json.JSONObject data) {
        ensureCallSignalTable();
        try {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put("payload", data.toString());
            v.put("created_at", String.valueOf(System.currentTimeMillis()));
            db.getWritableDatabase().insert("call_signals", null, v);
            // keep last 40
            db.getWritableDatabase().execSQL(
                "DELETE FROM call_signals WHERE id NOT IN (SELECT id FROM call_signals ORDER BY id DESC LIMIT 40)");
        } catch (Exception ignored) {}
    }

    public org.json.JSONArray loadCallSignals(int limit) {
        ensureCallSignalTable();
        org.json.JSONArray arr = new org.json.JSONArray();
        try (android.database.Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT payload FROM call_signals ORDER BY id DESC LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            java.util.ArrayList<org.json.JSONObject> tmp = new java.util.ArrayList<>();
            while (c.moveToNext()) {
                try { tmp.add(new org.json.JSONObject(c.getString(0))); } catch (Exception ignored) {}
            }
            for (int i = tmp.size() - 1; i >= 0; i--) arr.put(tmp.get(i));
        } catch (Exception ignored) {}
        return arr;
    }

    public void pullCallSignals(Runnable onUi) {
        pool.execute(() -> {
            try {
                if ("client".equals(db.setting("sync_mode", "off"))) {
                    org.json.JSONObject req = new org.json.JSONObject();
                    req.put("type", "call_pull");
                    req.put("pin", db.setting("sync_pin", "1234"));
                    org.json.JSONObject res = request(db.setting("sync_server_ip", "192.168.43.1"), port(), req);
                    if (res != null && res.optBoolean("ok")) {
                        org.json.JSONArray msgs = res.optJSONArray("signals");
                        if (msgs != null) {
                            ensureCallSignalTable();
                            for (int i = 0; i < msgs.length(); i++) {
                                storeCallSignal(msgs.getJSONObject(i));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            notifyUi(onUi);
        });
    }

    private void ensureChatTable() {
        try {
            db.getWritableDatabase().execSQL(
                "CREATE TABLE IF NOT EXISTS chat_messages(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "sender TEXT, body TEXT, ts TEXT, created_at TEXT)");
        } catch (Exception ignored) {}
    }

    private void ensureQueueTable() {
        try {
            db.getWritableDatabase().execSQL(
                "CREATE TABLE IF NOT EXISTS sync_queue(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "kind TEXT, payload TEXT, created_at TEXT, tries INTEGER DEFAULT 0, status TEXT DEFAULT 'pending')");
        } catch (Exception ignored) {}
    }

    public String status() { return lastStatus; }
    public boolean isServerOn() { return serverOn.get(); }

    public int pendingCount() {
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM sync_queue WHERE status='pending'", null)) {
            if (c.moveToFirst()) return c.getInt(0);
        } catch (Exception ignored) {}
        return 0;
    }

    public void enqueue(String kind, JSONObject payload) {
        try {
            ContentValues v = new ContentValues();
            v.put("kind", kind);
            v.put("payload", payload.toString());
            v.put("created_at", String.valueOf(System.currentTimeMillis()));
            v.put("status", "pending");
            db.getWritableDatabase().insert("sync_queue", null, v);
            lastStatus = "Queued " + kind + " · pending " + pendingCount();
        } catch (Exception e) {
            lastStatus = "Queue error: " + e.getMessage();
        }
    }

    public String localIpHint() {
        // Prefer real LAN/hotspot IPv4 from interfaces (not only WifiManager)
        try {
            java.util.List<String> found = new java.util.ArrayList<>();
            java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces();
            while (en != null && en.hasMoreElements()) {
                java.net.NetworkInterface ni = en.nextElement();
                if (ni == null || !ni.isUp() || ni.isLoopback()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress a = addrs.nextElement();
                    if (a.isLoopbackAddress() || !(a instanceof java.net.Inet4Address)) continue;
                    String ip = a.getHostAddress();
                    if (ip == null || ip.startsWith("169.254.")) continue; // link-local
                    found.add(ip);
                }
            }
            // Prefer hotspot-ish 192.168.43.x / 192.168.137.x then other 192.168/10/172
            for (String ip : found) if (ip.startsWith("192.168.43.") || ip.startsWith("192.168.137.")) return ip;
            for (String ip : found) if (ip.startsWith("192.168.")) return ip;
            for (String ip : found) if (ip.startsWith("10.")) return ip;
            for (String ip : found) if (ip.startsWith("172.")) return ip;
            if (!found.isEmpty()) return found.get(0);
        } catch (Exception ignored) {}
        try {
            WifiManager wm = (WifiManager) app.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) return Formatter.formatIpAddress(ip);
            }
        } catch (Exception ignored) {}
        return "192.168.43.1";
    }

    public void startServer(Runnable onUi) {
        if (serverOn.get()) { notifyUi(onUi); return; }
        pool.execute(() -> {
            try {
                int port = port();
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(port));
                serverOn.set(true);
                lastStatus = "Server ON · " + localIpHint() + ":" + port;
                notifyUi(onUi);
                while (serverOn.get()) {
                    try {
                        Socket s = serverSocket.accept();
                        pool.execute(() -> handleClient(s));
                    } catch (Exception e) {
                        if (serverOn.get()) {
                            lastStatus = "Accept: " + e.getMessage();
                            try { Thread.sleep(300); } catch (Exception ignored) {}
                            // keep listening — one bad client must not kill server
                            continue;
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                lastStatus = "Server failed: " + e.getMessage();
                serverOn.set(false);
            }
            notifyUi(onUi);
        });
    }

    public void stopServer(Runnable onUi) {
        serverOn.set(false);
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        lastStatus = "Server OFF";
        notifyUi(onUi);
    }

    private void handleClient(Socket s) {
        try (Socket sock = s;
             BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8))) {
            String line = in.readLine();
            if (line == null) return;
            JSONObject req = new JSONObject(line);
            String pin = db.setting("sync_pin", "1234");
            if (!pin.equals(req.optString("pin", ""))) {
                out.write(new JSONObject().put("ok", false).put("error", "bad pin").toString());
                out.newLine(); out.flush(); return;
            }
            String type = req.optString("type");
            if ("ping".equals(type)) {
                out.write(new JSONObject().put("ok", true).put("role", "server").put("pending", pendingCount()).toString());
            } else if ("chat_pull".equals(type)) {
                out.write(new JSONObject().put("ok", true).put("messages", loadChatJson(80)).toString());
            } else if ("push".equals(type)) {
                boolean applied = applyIncoming(req.optString("kind"), req.optJSONObject("data"));
                out.write(new JSONObject().put("ok", applied).toString());
                if (applied) lastStatus = "Received " + req.optString("kind");
            } else {
                out.write(new JSONObject().put("ok", false).put("error", "unknown type").toString());
            }
            out.newLine(); out.flush();
        } catch (Exception e) {
            lastStatus = "Client err: " + e.getMessage();
        }
    }

    private boolean applyIncoming(String kind, JSONObject data) {
        if (data == null) return false;
        try {
            if ("call_signal".equals(kind)) {
                storeCallSignal(data);
                lastStatus = "Call signal";
                return true;
            }
            if ("device_register".equals(kind)) {
                db.upsertDevice(
                    data.optString("device_id"),
                    data.optString("device_code"),
                    data.optString("name"),
                    data.optString("role"),
                    data.optString("ip"),
                    data.optBoolean("active", true));
                lastStatus = "Device registered " + data.optString("device_code");
                return true;
            }
            if ("device_status".equals(kind)) {
                // Admin pushed activate/deactivate to this device
                String myId = db.setting("device_id", "");
                if (myId.length() > 0 && myId.equals(data.optString("device_id"))) {
                    db.setSetting("device_active", data.optBoolean("active", true) ? "1" : "0");
                    lastStatus = "Device status updated";
                }
                return true;
            }
            if ("chat".equals(kind)) {
                storeChat(data.optString("sender", "User"), data.optString("body", ""), data.optString("ts", ""));
                lastStatus = "Chat from " + data.optString("sender");
                return true;
            }
            if ("location".equals(kind)) {
                DutyLocationService.storeLocal(db, data);
                lastStatus = "Location from " + data.optString("user");
                return true;
            }
            if ("receive".equals(kind)) {
                ContentValues z = new ContentValues();
                z.put("date", data.optString("date"));
                z.put("rm_code", data.optString("rm_code"));
                z.put("qty", data.optDouble("qty", 0));
                z.put("voucher_no", data.optString("voucher_no"));
                z.put("note", data.optString("note"));
                z.put("source", data.optString("source", "sync"));
                z.put("lot", data.optString("lot"));
                z.put("expiry", data.optString("expiry"));
                z.put("photo", data.optString("photo"));
                db.insert("receives", z);
                db.audit("sync", "client", "sync_receive", data.optString("rm_code"));
                return true;
            }
            if ("wastage".equals(kind)) {
                ContentValues z = new ContentValues();
                long nid = -System.currentTimeMillis();
                z.put("batch_id", nid);
                z.put("date", data.optString("date"));
                z.put("product_code", 0);
                z.put("shift", 0);
                z.put("rm_code", data.optString("rm_code"));
                z.put("recipe_qty", 0);
                z.put("batch_count", 0);
                z.put("consumption", data.optDouble("qty", 0));
                z.put("created_at", "WASTE|" + data.optString("note"));
                db.insert("mixing2_batch_log", z);
                db.audit("sync", "client", "sync_wastage", data.optString("rm_code"));
                return true;
            }
            if ("batch".equals(kind)) {
                ContentValues z = new ContentValues();
                if (data.has("date")) z.put("date", data.optString("date"));
                if (data.has("product_code")) z.put("product_code", data.optInt("product_code"));
                if (data.has("product_name")) z.put("product_name", data.optString("product_name"));
                if (data.has("batches")) z.put("batches", data.optDouble("batches"));
                if (data.has("batch_qty")) z.put("batch_qty", data.optDouble("batch_qty"));
                if (data.has("shift")) z.put("shift", data.optString("shift"));
                if (data.has("qty")) z.put("qty", data.optDouble("qty"));
                if (data.has("rm_code")) z.put("rm_code", data.optString("rm_code"));
                if (data.has("consumption")) z.put("consumption", data.optDouble("consumption"));
                db.getWritableDatabase().insert("mixing2_batch_log", null, z);
                db.audit("sync", "client", "sync_batch", data.optString("product_name", "batch"));
                return true;
            }
        } catch (Exception e) {
            lastStatus = "Apply fail: " + e.getMessage();
            return false;
        }
        return false;
    }

    public void flushQueue(Runnable onUi) {
        pool.execute(() -> {
            String mode = db.setting("sync_mode", "off");
            String net = db.setting("sync_net", "hotspot");
            String http = db.setting("sync_http_url", "");
            boolean canFlush = "client".equals(mode) || (http != null && http.startsWith("http"));
            if (!canFlush) {
                lastStatus = "Set Client (LAN/hotspot) or Internet webhook URL";
                notifyUi(onUi); return;
            }
            String host = db.setting("sync_server_ip", "192.168.43.1");
            int port = port();
            String pin = db.setting("sync_pin", "1234");
            List<long[]> ids = new ArrayList<>();
            List<String[]> jobs = new ArrayList<>();
            try (Cursor c = db.getReadableDatabase().rawQuery(
                    "SELECT id,kind,payload FROM sync_queue WHERE status='pending' ORDER BY id LIMIT 50", null)) {
                while (c.moveToNext()) {
                    ids.add(new long[]{c.getLong(0)});
                    jobs.add(new String[]{c.getString(1), c.getString(2)});
                }
            } catch (Exception e) {
                lastStatus = "Queue read: " + e.getMessage();
                notifyUi(onUi); return;
            }
            int ok = 0, fail = 0;
            for (int i = 0; i < jobs.size(); i++) {
                try {
                    JSONObject req = new JSONObject();
                    req.put("type", "push");
                    req.put("pin", pin);
                    req.put("kind", jobs.get(i)[0]);
                    req.put("data", new JSONObject(jobs.get(i)[1]));
                    boolean sent = false;
                    String netMode = db.setting("sync_net", "hotspot");
                    if ("internet".equals(netMode)) {
                        sent = httpPush(req);
                    } else {
                        // Hotspot / Office LAN — TCP only, one retry
                        for (int attempt = 0; attempt < 2 && !sent; attempt++) {
                            try {
                                JSONObject res = request(host, port, req);
                                sent = res != null && res.optBoolean("ok");
                            } catch (Exception lanEx) {
                                if (attempt == 0) try { Thread.sleep(400); } catch (Exception ignored) {}
                            }
                        }
                    }
                    if (sent) {
                        ContentValues u = new ContentValues();
                        u.put("status", "sent");
                        db.getWritableDatabase().update("sync_queue", u, "id=?", new String[]{String.valueOf(ids.get(i)[0])});
                        ok++;
                    } else fail++;
                } catch (Exception e) { fail++; }
            }
            lastStatus = "Flush · ok " + ok + " · fail " + fail + " · left " + pendingCount();
            notifyUi(onUi);
        });
    }

    public void pingServer(Runnable onUi) {
        pool.execute(() -> {
            try {
                JSONObject req = new JSONObject();
                req.put("type", "ping");
                req.put("pin", db.setting("sync_pin", "1234"));
                JSONObject res = request(db.setting("sync_server_ip", "192.168.43.1"), port(), req);
                lastStatus = res == null ? "Ping failed — hotspot/IP/PIN" : ("Ping OK");
            } catch (Exception e) {
                lastStatus = "Ping error: " + e.getMessage();
            }
            notifyUi(onUi);
        });
    }

    private boolean httpPush(JSONObject req) {
        String url = db.setting("sync_http_url", "");
        if (url == null || !url.startsWith("http")) return false;
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(15000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("X-Sync-Pin", db.setting("sync_pin", "1234"));
            byte[] body = req.toString().getBytes(StandardCharsets.UTF_8);
            c.getOutputStream().write(body);
            int code = c.getResponseCode();
            boolean ok = code >= 200 && code < 300;
            try {
                java.io.InputStream is = ok ? c.getInputStream() : c.getErrorStream();
                if (is != null) {
                    byte[] buf = new byte[2048];
                    int n = is.read(buf);
                    if (n > 0) {
                        JSONObject res = new JSONObject(new String(buf, 0, n, StandardCharsets.UTF_8));
                        if (res.has("ok")) ok = res.optBoolean("ok");
                    }
                }
            } catch (Exception ignored) {}
            c.disconnect();
            if (ok) lastStatus = "HTTP push OK";
            return ok;
        } catch (Exception e) {
            lastStatus = "HTTP push: " + e.getMessage();
            return false;
        }
    }

    /** Admin / any device: pull events from relay since last cursor. */
    public void pullFromInternet(Runnable onUi) {
        pool.execute(() -> {
            try {
                String url = db.setting("sync_http_url", "");
                if (url == null || !url.startsWith("http")) {
                    lastStatus = "No webhook URL";
                    notifyUi(onUi);
                    return;
                }
                long since = 0;
                try { since = Long.parseLong(db.setting("sync_http_since", "0")); } catch (Exception ignored) {}
                String pin = db.setting("sync_pin", "1234");
                String sep = url.contains("?") ? "&" : "?";
                String full = url + sep + "pull=1&since=" + since + "&pin=" + java.net.URLEncoder.encode(pin, "UTF-8");
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(full).openConnection();
                c.setConnectTimeout(12000);
                c.setReadTimeout(20000);
                c.setRequestMethod("GET");
                c.setRequestProperty("X-Sync-Pin", pin);
                int code = c.getResponseCode();
                if (code < 200 || code >= 300) {
                    lastStatus = "HTTP pull HTTP " + code;
                    c.disconnect();
                    notifyUi(onUi);
                    return;
                }
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                java.io.InputStream in = c.getInputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                c.disconnect();
                JSONObject res = new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
                if (!res.optBoolean("ok")) {
                    lastStatus = "HTTP pull not ok";
                    notifyUi(onUi);
                    return;
                }
                org.json.JSONArray events = res.optJSONArray("events");
                int applied = 0;
                long maxTs = since;
                if (events != null) {
                    for (int i = 0; i < events.length(); i++) {
                        JSONObject ev = events.getJSONObject(i);
                        long ts = ev.optLong("ts", 0);
                        if (ts > maxTs) maxTs = ts;
                        String kind = ev.optString("kind");
                        JSONObject data = ev.optJSONObject("data");
                        if (data == null) data = ev;
                        // skip our own echoes if device_id matches
                        String myId = db.setting("device_id", "");
                        if (myId.length() > 0 && myId.equals(data.optString("device_id"))) continue;
                        if (applyIncoming(kind, data)) applied++;
                    }
                }
                if (maxTs > since) db.setSetting("sync_http_since", String.valueOf(maxTs));
                lastStatus = "HTTP pull: " + applied + " applied";
            } catch (Exception e) {
                lastStatus = "HTTP pull: " + e.getMessage();
            }
            notifyUi(onUi);
        });
    }

    /** Flush outbox + pull inbox (internet / hybrid). */
    public void syncInternetNow(Runnable onUi) {
        pool.execute(() -> {
            // flush pending via existing logic
            final Object lock = new Object();
            final boolean[] done = {false};
            flushQueue(() -> {
                pullFromInternet(() -> {
                    synchronized (lock) {
                        done[0] = true;
                        lock.notifyAll();
                    }
                    notifyUi(onUi);
                });
            });
            // if flush returned immediately without client mode, still pull
            try {
                synchronized (lock) {
                    if (!done[0]) {
                        // fallback pull after short wait
                    }
                }
            } catch (Exception ignored) {}
        });
        // Also always pull in parallel for admin
        pullFromInternet(onUi);
    }


    
    public void storeChat(String sender, String body, String ts) {
        ensureChatTable();
        try {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put("sender", sender == null ? "?" : sender);
            v.put("body", body == null ? "" : body);
            if (ts == null || ts.isEmpty()) {
                ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
            }
            v.put("ts", ts);
            v.put("created_at", String.valueOf(System.currentTimeMillis()));
            db.getWritableDatabase().insert("chat_messages", null, v);
        } catch (Exception ignored) {}
    }

    public org.json.JSONArray loadChatJson(int limit) {
        ensureChatTable();
        org.json.JSONArray arr = new org.json.JSONArray();
        try (android.database.Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT sender,body,ts FROM chat_messages ORDER BY id DESC LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            java.util.ArrayList<org.json.JSONObject> tmp = new java.util.ArrayList<>();
            while (c.moveToNext()) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("sender", c.getString(0));
                o.put("body", c.getString(1));
                o.put("ts", c.getString(2));
                tmp.add(o);
            }
            for (int i = tmp.size() - 1; i >= 0; i--) arr.put(tmp.get(i));
        } catch (Exception ignored) {}
        return arr;
    }

    public void sendChat(String sender, String body, Runnable onUi) {
        pool.execute(() -> {
            try {
                String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                        .format(new java.util.Date());
                org.json.JSONObject data = new org.json.JSONObject();
                data.put("sender", sender);
                data.put("body", body);
                data.put("ts", ts);
                storeChat(sender, body, ts);
                if ("client".equals(db.setting("sync_mode", "off"))) {
                    org.json.JSONObject req = new org.json.JSONObject();
                    req.put("type", "push");
                    req.put("pin", db.setting("sync_pin", "1234"));
                    req.put("kind", "chat");
                    req.put("data", data);
                    try {
                        org.json.JSONObject res = request(
                                db.setting("sync_server_ip", "192.168.43.1"), port(), req);
                        if (res == null || !res.optBoolean("ok")) enqueue("chat", data);
                    } catch (Exception e) {
                        enqueue("chat", data);
                    }
                }
                lastStatus = "Chat sent/saved";
            } catch (Exception e) {
                lastStatus = "Chat error: " + e.getMessage();
            }
            notifyUi(onUi);
        });
    }

    public void pullChat(Runnable onUi) {
        pool.execute(() -> {
            try {
                if ("client".equals(db.setting("sync_mode", "off"))) {
                    org.json.JSONObject req = new org.json.JSONObject();
                    req.put("type", "chat_pull");
                    req.put("pin", db.setting("sync_pin", "1234"));
                    org.json.JSONObject res = request(
                            db.setting("sync_server_ip", "192.168.43.1"), port(), req);
                    if (res != null && res.optBoolean("ok")) {
                        org.json.JSONArray msgs = res.optJSONArray("messages");
                        if (msgs != null) {
                            ensureChatTable();
                            db.getWritableDatabase().delete("chat_messages", null, null);
                            for (int i = 0; i < msgs.length(); i++) {
                                org.json.JSONObject m = msgs.getJSONObject(i);
                                storeChat(m.optString("sender"), m.optString("body"), m.optString("ts"));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            notifyUi(onUi);
        });
    }


    private JSONObject request(String host, int port, JSONObject req) throws Exception {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 5000);
            s.setSoTimeout(8000);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out.write(req.toString()); out.newLine(); out.flush();
            String line = in.readLine();
            return line == null ? null : new JSONObject(line);
        }
    }

    public void testLanConnection(Runnable onUi) {
        pool.execute(() -> {
            try {
                String host = db.setting("sync_server_ip", "192.168.43.1");
                JSONObject req = new JSONObject();
                req.put("type", "ping");
                req.put("pin", db.setting("sync_pin", "1234"));
                JSONObject res = request(host, port(), req);
                if (res != null && res.optBoolean("ok")) {
                    lastStatus = "LAN OK · " + host + ":" + port() + " · pending on server " + res.optInt("pending", 0);
                } else {
                    lastStatus = "LAN fail · check Wi-Fi, server ON, IP, PIN";
                }
            } catch (Exception e) {
                lastStatus = "LAN fail: " + e.getMessage();
            }
            notifyUi(onUi);
        });
    }

    private int port() {
        try { return Integer.parseInt(db.setting("sync_port", String.valueOf(DEFAULT_PORT))); }
        catch (Exception e) { return DEFAULT_PORT; }
    }

    private void notifyUi(Runnable onUi) { if (onUi != null) main.post(onUi); }
}
