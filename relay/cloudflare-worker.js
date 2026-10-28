/**
 * Cloudflare Worker relay for Inventory Management Native
 * 1. dash.cloudflare.com → Workers → Create
 * 2. KV namespace bind name: SYNC_KV
 * 3. Settings → Variables: SYNC_PIN = your team pin
 * 4. Deploy → copy URL into app webhook field
 *
 * POST /  + header X-Sync-Pin  body: {type,pin,kind,data}
 * GET  /?pull=1&since=0&pin=PIN
 */
const MAX = 500;
export default {
  async fetch(request, env) {
    const pinExpected = env.SYNC_PIN || "1234";
    const url = new URL(request.url);
    const pin = request.headers.get("X-Sync-Pin") || url.searchParams.get("pin") || "";
    if (pin !== pinExpected) return json({ ok: false, error: "bad pin" }, 401);
    if (request.method === "POST") {
      let body;
      try { body = await request.json(); } catch { return json({ ok: false, error: "bad json" }, 400); }
      const id = Date.now() + "-" + Math.random().toString(36).slice(2, 8);
      const row = { id, kind: body.kind || "unknown", data: body.data || body, ts: Date.now() };
      let list = [];
      try { list = JSON.parse(await env.SYNC_KV.get("events") || "[]"); } catch { list = []; }
      list.push(row);
      if (list.length > MAX) list = list.slice(-MAX);
      await env.SYNC_KV.put("events", JSON.stringify(list));
      return json({ ok: true, id });
    }
    if (request.method === "GET" && url.searchParams.get("pull") === "1") {
      const since = parseInt(url.searchParams.get("since") || "0", 10) || 0;
      let list = [];
      try { list = JSON.parse(await env.SYNC_KV.get("events") || "[]"); } catch { list = []; }
      return json({ ok: true, events: list.filter(e => (e.ts || 0) > since), server_time: Date.now() });
    }
    return json({ ok: true, service: "inventory-sync-relay" });
  }
};
function json(o, status = 200) {
  return new Response(JSON.stringify(o), {
    status,
    headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
  });
}
