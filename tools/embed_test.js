const { chromium } = require('/home/claude/.npm-global/lib/node_modules/playwright');
(async () => {
  const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' });
  const ctx = await b.newContext({ viewport: { width: 390, height: 800 }, isMobile: true, hasTouch: true });
  await ctx.addInitScript(() => {
    window.__calls = [];
    window.AndroidPM = { isDark: () => true, saveFile: (n, m, b64) => window.__calls.push(['save', n, m, atob(b64).length]), print: n => window.__calls.push(['print', n]) };
  });
  const pg = await ctx.newPage(); const errs = []; pg.on('pageerror', e => errs.push(e.message));
  await pg.goto('file://' + process.argv[2]);
  console.log('embed class:', await pg.evaluate(() => document.documentElement.className), '| theme:', await pg.evaluate(() => document.documentElement.dataset.theme));
  console.log('header visible:', await pg.$eval('header', e => getComputedStyle(e).display !== 'none'), '| nav top:', await pg.$eval('nav', e => getComputedStyle(e).top), '| bg:', await pg.evaluate(() => getComputedStyle(document.body).backgroundColor));
  await pg.click('[data-tab=production]');
  await pg.click('#do-csv'); await pg.click('#do-pdf'); await pg.click('#do-print');
  await pg.waitForTimeout(600);
  console.log('bridge calls:', JSON.stringify(await pg.evaluate(() => window.__calls)));
  console.log('JS errors:', JSON.stringify(errs));
  await b.close();
})();
