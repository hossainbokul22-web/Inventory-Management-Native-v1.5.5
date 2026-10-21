# Inventory Management — v1.5.1 notes (BN)

## v1.5.1 fix
- `requestNotifPermission()` method restored (was called from onCreate/Settings/backup restore but missing in v1.5.0 source → build/compile risk).
- Role change: **Admin only** always; if fingerprint lock is ON, change also requires Guard confirm.

## Month lock
- When a month is finalized (`Settings → Month close lock`), code blocks stock-count pending save and count approve via `monthLocked()`.
- Other entry paths should call `assertCanEdit(date)` where edits are sensitive; treat finalized months as closed for counts/approvals.

## Stock Count flow
- Pending count → variance shown → **Approve & adjust stock** writes a `receives` row with `source=count_adjust` (qty = variance). This **changes book stock**. Test on sample data first.
- Reject leaves stock unchanged.

## Roles
- Admin / Supervisor / Operator are **device settings**, not a multi-user server login.
- Security depends on fingerprint/PIN Guard for sensitive screens. If Guard is OFF, physical access to the phone can change settings — keep Guard ON in production.

## Daily PM
- Native `PmDb` + seed `pm_seed.json`. Old WebView/localStorage PM data is **not** auto-migrated; use JSON backup/import if you still have an export from the old page.

## Alarms
- Daily alerts use `AlarmManager.setAndAllowWhileIdle` (not guaranteed exact to the second). Morning window is fine; a few minutes drift is possible on some OEMs.
