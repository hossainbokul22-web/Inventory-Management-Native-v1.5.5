# Inventory Management — Native Android

**Current version: 1.5.1**

## v1.5.1
- Fixed missing `requestNotifPermission()` (notification permission on Android 13+)
- Role change restricted to Admin (+ Guard when lock enabled)

## v1.5.0
- Stock count approval flow (pending → variance → approve/adjust or reject)
- Multi-stage yield UI (Mixing / Baking / Pack)
- Recipe copy + archive

## v1.4.x
- Shift-wise dashboard, owner pack, audit log, morning alerts, recipe zoom, unified backup

---

# Inventory Management — Native Android (v1.4.0)

## v1.4.0
- Shift-wise dashboard (A/B/C summary)
- Yield history + tokens: dry_output, input, moisture, wastage, batch
- Owner pack (share text/PDF)
- Month finalize lock
- Roles: Admin / Supervisor / Operator
- Offline audit log
- Share unified backup (Email/Drive/WhatsApp)

# Inventory Management — Native Android (v1.3.3)

## What is new in v1.2.0
- **Daily PM Report inside the app.** Header button "Daily PM" (and More → Daily PM Report) opens the offline packaging report page (`assets/pm/index.html`) in a full screen. It has its own data store (inside the app), Print (A4 landscape), Save/Share for PDF and CSV, backup and restore.
- **New look:** one palette for light and dark mode (follows the phone), rounded cards, 5-item bottom bar (Home, Entry, Batch, Reports, More).
- **Stock Watch + Days of Stock:** the dashboard lists raw materials that are below zero, at or under their *Reorder level*, or will last less than 7 days at the last 7 days' average use. Reorder level is set in RM Master.
- **Batch Planner:** enter planned batches per product, see the raw material needed against today's closing stock, and how many whole batches each product can be made.
- **7-day usage chart** and a bar view of stock on the dashboard.
- **Fixes:** date picker with validation (no more `2026-1-5`), Receive delete, recent receives limited to the latest 100, dashboard stock now calculated with grouped queries, RM Master no longer fails on empty numbers.

## v1.2.1
- **Fingerprint lock** (phone fingerprint, or PIN if there is none) before Settings, Master (RM and Products), Recipe and every delete. One unlock lasts 3 minutes; deletes always ask again. The same lock protects the Master and Settings tabs and all deletes inside Daily PM. Turn it off in Settings → Security.
- **Settings** (gear in the header, or More → Settings): company name, the five tab names, speed of the dashboard cards, protection on/off.
- **More** now lists Master — Raw materials, Master — Products and Recipe separately; RM and Product master have Delete (blocked when history exists).
- **Dashboard live cards** change one at a time in the order Receive, Use, Batch, Closing, 3 seconds apart (adjustable 2.5–5 s), each showing the next item of that card.
- **Tabs:** every bottom tab has its own colour and icon; Daily PM tabs have their own colours too.

## Notes
- The Daily PM page stores its data in the app's WebView storage, separate from the RM database. Use the backup inside the Daily PM page for its data and Reports → Backup JSON for RM data. Uninstalling the app removes both.
- Database version is 5 (adds `rms.reorder`); existing data is kept on update.


This project is a native Android rebuild. It does not use WebView, PWA, localStorage, or a web UI.

## Native architecture
- Java Android UI (programmatic native Views)
- SQLiteOpenHelper offline data layer
- Dynamic RM/Product master
- Dynamic RM × Product recipe matrix
- Native batch and receive entry with editable Batch Count per saved Shift/Product entry
- Native stock calculations
- Native offline camera + Tesseract OCR (English + Bengali)
- Single Owner/Admin AI draft approval into Daily Entry
- Native PdfDocument report generation
- Android PrintManager printing
- Android system document picker for saving PDF
- App-private storage for database, OCR files, camera cache and generated reports
- Native Custom Sheet definitions/rows stored in SQLite

## Dynamic guarantees
- New RM automatically gets recipe rows with quantity 0.
- New Product automatically gets recipe rows with quantity 0 for every RM.
- RM/Product code edits migrate dependent recipe/batch references.
- Business data is stored in SQLite, not hardcoded into screens.

## Build
The repository contains a GitHub Actions workflow that builds a debug APK with Gradle 8.2 and Java 17.


## Final behavior notes
- Personal-use mode has one Owner/Admin only; there is no Member account/role workflow.
- Saved Batch Count entries can be edited. If Shift 1 was entered as 4 but the actual count was 5, Edit changes 4 → 5 in the same `batches` record.
- The edited Batch Count continues through the existing recipe-linked consumption calculation; no separate calculation path is introduced.
- RM/Product names and codes remain dynamic. Product code edits migrate recipe and historical batch references.
- Custom Sheet names remain editable by internal Sheet ID, so renaming does not create a new sheet.

### Batch Count rule
- Batch Count means the total number of batches produced in that Shift for the selected Product and date.
- It is a whole-number value and is editable after saving.
- There is exactly one saved count for each Date + Product + Shift. Saving again replaces that count; it never adds another batch-count row.
- Example: Shift 1 entered as 4, then Edit → 5. Stock usage becomes `5 × recipe quantity` through the same existing calculation path.
- Changing 5 back to 4 similarly recalculates from 4.

## Custom Sheet designer
- Create sheets with a stable internal Sheet ID.
- Rename sheet name/description without changing the Sheet ID or existing rows.
- Add, rename, delete, and reorder columns.
- Column types: text, number, date, formula.
- Formula columns are read-only in row entry and are recalculated from the same row data.
- Supported formula patterns include arithmetic (`=B2*C2`), `=SUM(D2:F2)`, and basic `=IF(E2<0,"SHORTAGE","OK")` conditions.
- Renaming a column migrates existing row JSON keys so saved data is retained.
- Sheets and their rows can be deleted from the Owner-only native designer.

## Completion audit additions
- Daily Receive entries can be edited after saving.
- RM code changes migrate both recipe and historical receive references.
- Reports use the first-day opening calculation for monthly opening.
- Mixing-2 view shows batch × recipe RM usage and links to the same editable batch count.
- Custom Sheet rows can be deleted; columns can be moved up/down; formula evaluation supports multi-letter columns and repeated dependency passes.
- Company name can be edited from Reports.
- JSON backup/restore covers operational and custom tables; CSV export is available.
- PDF output has page numbers, simple line wrapping, company header, and native Android Print/Save/Share flow.

## GitHub upload/build
Upload the **contents of this project folder** to the repository root (not the ZIP file itself). The repository must contain `settings.gradle`, `build.gradle`, `app/`, and `.github/workflows/build-apk.yml` at the root. GitHub Actions installs Android SDK 34 and Gradle 8.2, then builds `app-debug.apk`.


### Historical Mixing-2 Batch Log
The project keeps a native SQLite `mixing2_batch_log` snapshot for each saved Date + Product + Shift + RM combination. Correcting a batch count updates that batch log; later recipe edits do not rewrite the historical recipe quantity/consumption already recorded in the log. The normal stock-usage calculation still uses the current Batch Count × Recipe logic.

## Latest requested report/UI source updates
- Daily Date Report renders a spreadsheet-style table with item code/name, UoM, opening, receive, use, closing and status. Selected-date PDF supports Save, Share and Android Print; the full-month PDF option is also available.
- Final Monthly Usage now renders the same spreadsheet-style columns and includes PDF/Print plus Excel-compatible UTF-8 CSV export.
- The two monthly day-wise sheets (RM Receive and RM Mixing-2) generate landscape A4 PDF pages with repeated title/date headings and item rows.
- The header is compact and bottom navigation is horizontally scrollable for narrow devices.
- Stock opening is calculated from the RM master opening plus all movements strictly before the selected date, carrying balances over day/month/year boundaries.

Build status: APK build could not be verified in this environment because Gradle 8.2 could not be downloaded (network DNS unavailable). This archive contains source only; build and test it in GitHub Actions/Codespaces before installing. Please keep a separate backup of your live app data before updating.

## v1.3.0
- **Voucher photo kept:** a scanned voucher keeps its photo with the receive ("View voucher photo").
- **Lot No and Expiry date** on Receive; dashboard **Expiry Watch** lists lots expiring within N days (Settings, default 14) that still have stock, with a "use this lot first" hint. Usage is not tracked per lot.
- **Wastage** (More): counts as normal use in every stock figure and report; delete with fingerprint.
- **Stock Count** (More): book stock vs counted, difference, history, PDF. Report only; it does not change stock.
- **Daily Summary** (More): one-page PDF (receive, use, batches, alerts) to share.
- **Home-screen widget (English only):** number of items needing attention and the worst three.
- **Full Settings:** company name, tab names, editable screen titles / card names / PDF headings (with reset), links to RM / Product / Recipe / Custom sheets (add, edit, delete), expiry days, live-card speed, month or all-entries clean-up, backup / restore / CSV, protection. Saved batch counts now have Delete.
- Database version 6 (existing data is kept).

## v1.3.1
- App icon: the new blue inventory icon (legacy PNGs + adaptive icon).
- Daily PM → Backup tab: new **Delete one month** (entries, typed openings, run times and manpower of that month; fingerprint + two confirmations).

## v1.3.2 — Fully native Daily PM Report
- **WebView removed.** Daily PM Report is now pure Android Java (no HTML/JS).
- New `PmDb` SQLite database stores packaging items, products, recipes and entries.
- Native tabs: Entry, Daily, Monthly, Production, Master, Settings, Backup.
- Seed data from `assets/pm_seed.json` (same items/products/recipes as before).
- CSV + landscape A4 PDF export for reports; JSON backup/restore.
- Same fingerprint lock protects Master, Settings and deletes.

## v1.3.3
- App display name: **Inventory Management** (launcher + under company in header).
- Company name edited only in **More → Settings** (removed from Reports and from Daily PM Settings).
- Back button: screens opened from More return to More (not straight to Dashboard).

## v1.3.4
- **Unified backup:** one JSON from More → Settings includes RM tables + yield_defs + full PM database. Restore replaces all. Reports and PM no longer have separate backups.
- **Yield % dashboard card:** rotates product yield every live-gap seconds (default ~3s), circle-style display.
- **Yield % sheet (More):** Excel-style Actual / Standard / Formula per product (`actual/standard*100` supported with + − * / and parentheses). Card reads saved logic.

## v1.5.0
- Stock count approval: pending → variance → approve (adjusts stock) / reject
- Multi-stage yield UI: Mixing / Baking / Pack + overall chain %
- Recipe copy product A→B with auto-archive of destination + manual archive
