from pathlib import Path
import re
base=Path('app/src/main/java/com/rmstock/nativeapp')
src=(base/'MainActivity.java').read_text()
ui=(base/'Ui.java').read_text()
pm=(base/'PmActivity.java').read_text()
db=(base/'StockDb.java').read_text()
manifest=Path('app/src/main/AndroidManifest.xml').read_text()
checks={
 'opening carries all earlier receives/uses': 'WHERE rm_code=? AND date<?' in src,
 'daily report keeps 8 columns': '"Item Code","Item Name","UoM","Opening","Receive","Use","Closing Balance","Status"' in src,
 'monthly report keeps 8 columns': '"Opening Balance","Total Receive","Total Use"' in src,
 'landscape A4 table PDF kept': 'createLandscapeTablePdf("RM STOCK - DAILY DATE REPORT"' in src and 'new PdfDocument.PageInfo.Builder(842,595' in src,
 'month finalization kept': 'finalized_month_' in src,
 'bottom nav has Home/Entry/Batch/Reports/More': all(x in src for x in ['"Home","Entry","Batch","Reports","More"','tabName(key)']),
 'dashboard four live cards kept': all(x in src for x in ['"Today RM Receive"','"Today Mixing-2 Use"','"Today Batch"','"Today Closing Stock"']),
 'dashboard stock uses grouped SQL (no per-RM queries)': 'sumMap("SELECT rm_code k,SUM(qty) v FROM receives WHERE date<=?' in src and 'closingFor(rm' not in src,
 'stock watch + days of stock': 'Stock Watch' in src and 'PlanLogic.daysLeft' in src,
 'batch planner screen': 'showRmPlanner()' in src and 'PlanLogic.need(' in src and 'PlanLogic.maxBatches(' in src,
 'date picker + validation': 'new DatePickerDialog' in src and 'PlanLogic.validDate' in src and 'ymd(d)' in src,
 'receive delete + 100 row limit': '"receives","id=?"' in src and 'LIMIT 100' in src,
 'reorder level column + migration': 'reorder REAL' in db and ('VER=7' in db or ('VER=' in db)) and 'ALTER TABLE rms ADD COLUMN reorder' in db,
 'dark mode palette': 'Configuration.UI_MODE_NIGHT_YES' in ui and Path('app/src/main/res/values-night/styles.xml').exists(),
 'Daily PM fully native (no WebView)': 'import android.webkit' not in pm and Path('app/src/main/java/com/rmstock/nativeapp/PmDb.java').exists() and Path('app/src/main/assets/pm_seed.json').exists(),
 'PM native tabs + SQLite': all(x in pm for x in ['showEntry()','showDaily()','showMonthly()','showMaster()','PmDb']) and 'CREATE TABLE items' in Path('app/src/main/java/com/rmstock/nativeapp/PmDb.java').read_text(),
 'fingerprint / lock guard': 'BiometricPrompt' in (base/'Guard.java').read_text() and 'Guard.run(this,db,"Delete receive",true' in src and 'USE_BIOMETRIC' in manifest,
 'Settings screen (company, tab names, card speed, protection)': 'private void showSettings()' in src and '"tab_"+' in src and 'live_gap' in src,
 'master: add/edit/delete RM and products behind the lock': 'deleteRm(' in src and 'deleteProduct(' in src and 'gateOpen("Product master"' in src and 'gateOpen("Recipe"' in src,
 'live cards change one at a time with a gap': 'private void startLive(' in src and 'liveGap()' in src and 'startTicker' not in src,
 'PM page asks the lock for Master, Settings and deletes': 'Guard.run' in pm and 'Master' in pm and 'Settings' in pm,
 'each tab has its own colour and icon': 'Ui.TAB[i]' in src and Path('app/src/main/res/drawable/ic_nav_home.xml').exists(),
 'PM activity registered': '.PmActivity' in manifest,
 'v1.3: lot/expiry/photo + counts table + migration': all(x in db for x in ['lot TEXT,expiry TEXT,photo TEXT','CREATE TABLE IF NOT EXISTS counts','if(o<6)']),
 'v1.3: wastage, stock count, daily summary, expiry watch': all(x in src for x in ['showWastage()','showCount()','showSummary()','"Expiry Watch"']),
 'v1.3: editable names + data clean-up + batch delete': all(x in src for x in ['"lbl::"','wipe(','Delete batch count']),
 'v1.3: English widget registered': Path('app/src/main/java/com/rmstock/nativeapp/StockWidget.java').exists() and 'StockWidget' in Path('app/src/main/AndroidManifest.xml').read_text(),
 'v1.4: shift dashboard + owner pack + audit': all(x in Path('app/src/main/java/com/rmstock/nativeapp/MainActivity.java').read_text() for x in ['addShiftWiseSummary','showOwnerPack','showAuditLog','shareUnifiedBackup']) and 'yield_history' in Path('app/src/main/java/com/rmstock/nativeapp/StockDb.java').read_text(),
 'v1.3.4: yield sheet + unified backup': 'yield_defs' in Path('app/src/main/java/com/rmstock/nativeapp/StockDb.java').read_text() and 'addYieldLiveCard' in Path('app/src/main/java/com/rmstock/nativeapp/MainActivity.java').read_text() and '"pm"' in Path('app/src/main/java/com/rmstock/nativeapp/MainActivity.java').read_text(),
 'v1.3.1: launcher icon + native PM': Path('app/src/main/res/mipmap-xxxhdpi/ic_launcher.png').exists() and '@mipmap/ic_launcher' in Path('app/src/main/AndroidManifest.xml').read_text() and 'class PmDb' in Path('app/src/main/java/com/rmstock/nativeapp/PmDb.java').read_text(),
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS' if v else 'FAIL')+' '+k)
print(f'RESULT: {len(checks)-len(failed)}/{len(checks)} source checks passed')
if failed: raise SystemExit(1)
