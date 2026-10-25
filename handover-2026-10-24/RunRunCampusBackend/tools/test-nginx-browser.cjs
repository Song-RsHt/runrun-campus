const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

(async () => {
  const report = path.resolve(process.argv[2]);
  const browser = await chromium.launch({channel: 'msedge', headless: true});
  const result = {success: false, checks: [], errors: [], violations: []};
  try {
    // Trust exception is confined to this disposable self-signed test endpoint.
    const page = await browser.newPage({ignoreHTTPSErrors: true, viewport: {width: 1440, height: 1000}});
    page.on('pageerror', error => result.errors.push(error.message));
    await page.addInitScript(() => {
      window.qaViolations = [];
      document.addEventListener('securitypolicyviolation', event => {
        window.qaViolations.push({directive: event.effectiveDirective, blocked: event.blockedURI});
      });
    });
    const response = await page.goto('https://127.0.0.1:28443');
    assert.equal(response.status(), 200);
    const csp = response.headers()['content-security-policy'];
    assert.ok(csp && !csp.includes('unsafe-inline') && !csp.includes('unsafe-eval'));
    result.checks.push('Production CSP delivered without unsafe-inline or unsafe-eval');
    await page.getByRole('button', {name: '登录运营后台', exact: true}).waitFor();
    await page.getByLabel('手机号', {exact: true}).fill('19903000999');
    await page.getByLabel('密码', {exact: true}).fill('SyntheticQaPassword');
    assert.ok(await page.evaluate(() => [...document.styleSheets].some(sheet => sheet.href && sheet.cssRules.length > 0)));
    assert.deepEqual(await page.evaluate(() => window.qaViolations), []);
    assert.deepEqual(result.errors, []);
    result.checks.push('Built React login screen, form interaction and CSS load without CSP violations');
    await page.screenshot({path: path.join(report, 'login-csp.png'), fullPage: true});
    // The probe must run as a real page script: DevTools evaluation can bypass eval CSP.
    await page.evaluate(() => {
      const script = document.createElement('script');
      script.src = '/qa-probe.js';
      document.head.appendChild(script);
    });
    await page.waitForFunction(() => ['script-src-elem', 'script-src', 'connect-src'].every(
      directive => window.qaViolations.some(event => event.directive === directive)));
    assert.equal(await page.evaluate(() => Boolean(window.qaInlineExecuted || window.qaEvalExecuted)), false);
    result.violations = await page.evaluate(() => window.qaViolations);
    result.checks.push('Inline scripts, eval and off-origin connections are blocked');
    const download = page.waitForEvent('download');
    await page.evaluate(() => {
      const link = document.createElement('a');
      link.href = URL.createObjectURL(new Blob(['qa,csv\n1,ok'], {type: 'text/csv'}));
      link.download = 'qa.csv'; link.click();
    });
    const csv = path.join(report, 'csp-download.csv');
    await (await download).saveAs(csv);
    assert.equal(fs.readFileSync(csv, 'utf8'), 'qa,csv\n1,ok');
    result.checks.push('Existing blob CSV download mechanism works with CSP');
    result.success = true;
  } catch (error) {
    result.error = error.message; process.exitCode = 1;
  } finally {
    await browser.close();
    fs.writeFileSync(path.join(report, 'browser-acceptance.json'), JSON.stringify(result, null, 2));
    console.log(JSON.stringify(result));
  }
})();
