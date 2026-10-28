# Internet sync relay (option ②)

## Deploy (free Cloudflare Worker)

1. Create Worker + KV binding name **SYNC_KV**
2. Variable **SYNC_PIN** = team PIN (same as app)
3. Paste `cloudflare-worker.js` → Deploy
4. Copy Worker URL into app: Admin Chamber → Team sync → webhook URL
5. Network = `internet`, Mode supervisors = `client`

## Flow

- Supervisor entry → queue → POST to Worker
- Admin opens app / **Internet pull now** → GET `?pull=1&since=...` → applies to local DB
