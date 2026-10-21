const { chromium } = require('/home/claude/.npm-global/lib/node_modules/playwright');
(async () => {
  const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' });
  const mk = async (guard) => {
    const ctx = await b.newContext({ viewport: { width: 390, height: 800 }, isMobile: true, hasTouch: true });
    await ctx.addInitScript(g => {
      window.__asked = []; window.__g = g;
      if (g !== null) window.AndroidPM = { isDark: () => false, saveFile() {}, print() {}, guardOn: () => window.__g,
        authenticate: (w, f) => { window.__asked.push([w, !!f]); setTimeout(() => window.__authOk && window.__authOk(), 30); },
        toggleGuard: () => { window.__g = !window.__g; setTimeout(() => window.__authOk && window.__authOk(), 30); } };
    }, guard);
    const pg = await ctx.newPage(); pg.errs = []; pg.on('pageerror', e => pg.errs.push(e.message)); pg.on('dialog', d => d.accept());
    return pg;
  };
  let pg = await mk(true); await pg.goto('file://' + process.argv[2]);
  await pg.click('[data-tab=master]'); await pg.waitForTimeout(150);
  console.log('master opened:', await pg.$eval('[data-tab=master]', e => e.getAttribute('aria-selected')), '| asked:', JSON.stringify(await pg.evaluate(() => window.__asked)));
  await pg.click('[data-tab=settings]'); await pg.waitForTimeout(150);
  console.log('settings (within 3 min) asked again?:', await pg.evaluate(() => window.__asked.length), '| guard card:', await pg.$eval('#guard-toggle', e => e.innerText));
  await pg.click('#guard-toggle'); await pg.waitForTimeout(200);
  console.log('after toggle:', await pg.$eval('#guard-toggle', e => e.innerText));
  console.log('tab colours:', JSON.stringify(await pg.$$eval('nav button', els => els.map(e => getComputedStyle(e).getPropertyValue('--tc').trim()))));
  console.log('errors:', JSON.stringify(pg.errs));
  // delete always asks fresh
  pg = await mk(true); await pg.goto('file://' + process.argv[2]);
  await pg.click('[data-tab=entry]');
  await pg.evaluate(() => { S.receive.push({ id: 99, date: todayISO(), code: S.items[0].code, qty: 1 }); save(); render(); });
  await pg.click('[data-tab=daily]'); await pg.click('[data-tab=entry]');
  const hasDel = await pg.$('[data-del]'); if (hasDel) { await pg.click('[data-del]'); await pg.waitForTimeout(150); }
  console.log('delete asked fresh:', JSON.stringify(await pg.evaluate(() => window.__asked)), '| deleted:', await pg.evaluate(() => S.receive.length === 0 || !S.receive.some(x => x.id === 99)));
  // no bridge (normal browser): everything works without prompts
  pg = await mk(null); await pg.goto('file://' + process.argv[2]);
  await pg.click('[data-tab=master]'); await pg.click('[data-tab=settings]');
  console.log('plain browser tab:', await pg.$eval('[data-tab=settings]', e => e.getAttribute('aria-selected')), 'errors:', JSON.stringify(pg.errs));
  await b.close();
})();
