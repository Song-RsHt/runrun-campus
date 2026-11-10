const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const base = 'http://127.0.0.1:8082';
const env = Object.fromEntries(fs.readFileSync(process.env.RRC_QA_ENV_FILE || path.join(root, '.env.v2-test'), 'utf8')
    .split(/\r?\n/).filter(x => x && !x.startsWith('#') && x.includes('='))
    .map(x => [x.slice(0, x.indexOf('=')), x.slice(x.indexOf('=') + 1)]));
assert.equal(env.RRC_V2_ADMIN_PHONE, 'test-admin');
assert.equal(env.RRC_V2_ENVIRONMENT, 'test');
assert.equal(env.RRC_PAYMENT_MOCK_ENABLED, 'true');
let page, session, relaunched = false, failPolicy = false, failConsent = false, count = 0;
const urls = [];
function check(ok, label) { assert.ok(ok, label); count++; console.log('PASS: ' + label); }
async function request(url, options = {}) {
    urls.push(url);
    assert.ok(url.startsWith('/api/v2/'));
    if (failPolicy && url.endsWith('/policy')) throw new Error('合成网络失败');
    if (failConsent && url.endsWith('/consent')) throw new Error('合成回执失败');
    const response = await fetch(base + url, { method: options.method || 'GET',
        headers: { 'Content-Type': 'application/json', ...(session ? { Authorization: 'Bearer ' + session.token } : {}),
            'Idempotency-Key': 'mini-entry-' + Date.now(), ...options.header },
        body: options.data ? JSON.stringify(options.data) : undefined });
    if (!response.ok) throw new Error('HTTP ' + response.status);
    return response.json();
}
vm.runInNewContext(fs.readFileSync(path.join(root, 'RunRunCampusMiniProgram/pages/login/login.js'), 'utf8'), {
    require(name) { return name.endsWith('/api') ? { request } : {
        saveSession(value) { session = value; }, clearSession() { session = null; }
    }; },
    Page(value) { page = value; page.setData = value => Object.assign(page.data, value); },
    wx: { reLaunch() { relaunched = true; } }
});
(async () => {
    await page.loadPrivacyPolicy();
    check(page.data.privacyPolicy?.version === '2026-10-24' && page.data.privacyPolicy.content, 'real anonymous policy loads into mini Page data (Node harness)');
    page.togglePrivacyContent();
    check(page.data.privacyExpanded, 'protocol content expands');
    Object.assign(page.data, { phone: '13800000001', password: 'synthetic-password' });
    check(!page.canSubmit(), 'login blocked until explicit agreement');
    page.togglePrivacy();
    check(page.canSubmit(), 'login precondition ready after agreement and real policy');
    failPolicy = true; await page.loadPrivacyPolicy();
    check(!page.data.privacyPolicy && !page.data.privacyAccepted && page.data.error.includes('隐私政策加载失败'), 'policy failure clears stale version and shows understandable error');
    failPolicy = false; await page.loadPrivacyPolicy(); page.togglePrivacy();
    const login = await request('/api/v2/auth/password/login', { method: 'POST', data: { phone: 'test-admin', password: env.RRC_V2_ADMIN_PASSWORD } });
    await page.finishLogin(login);
    check(relaunched && session?.token, 'real version consent succeeds before entering home');
    await request('/api/v2/auth/logout', { method: 'POST', data: { allSessions: false } });
    session = null; relaunched = false; failConsent = true;
    // 故障注入只验证小程序清理路径；后端同意接口已在上一条使用真实 HTTP。
    try { await page.finishLogin(login); assert.fail('must reject'); } catch (error) {
        check(error.message.includes('协议同意记录失败') && !session && !relaunched, 'consent failure keeps user on login and clears session (injected)');
    }
    check(urls.every(url => !url.includes('/api/v1')), 'mini login flow never calls v1');
    console.log('PASS total=' + count + '; Node harness is not WeChat DevTools compilation');
})().catch(error => { console.error('FAIL: ' + error.message); process.exitCode = 1; });
