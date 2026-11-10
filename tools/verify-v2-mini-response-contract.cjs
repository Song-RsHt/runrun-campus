// 真实 Page 源码的异常响应边界；不调用微信身份、支付或外部服务。
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync(path.join(__dirname, '../RunRunCampusMiniProgram/pages/login/login.js'), 'utf8');
const policy = { version: '2026-10-24', title: '合成协议', effectiveDate: '2026-10-24', operatorName: '合成运营方', contact: '合成联系方式', content: '合成测试正文' };
let count = 0;
async function run(body, receipt, changeVersion = false) {
    let page, session, home = false, logouts = 0;
    vm.runInNewContext(source, {
        require(name) { return name.endsWith('/api') ? { request: async url => {
            if (url.endsWith('/policy')) return body;
            if (url.endsWith('/consent')) {
                if (changeVersion) page.data.privacyPolicy = { ...policy, version: 'replacement' };
                return receipt;
            }
            assert.ok(url.endsWith('/logout')); logouts++; return { revoked: true };
        } } : { saveSession(value) { session = value; }, clearSession() { session = null; } }; },
        Page(value) { page = value; page.setData = value => Object.assign(page.data, value); },
        wx: { reLaunch() { home = true; } }
    });
    // 加载失败必须清理之前的勾选及协议。
    Object.assign(page.data, { privacyPolicy: policy, privacyAccepted: true, phone: '13800000001', password: 'synthetic-password' });
    await page.loadPrivacyPolicy();
    const validPolicy = !!page.data.privacyPolicy;
    if (validPolicy) page.togglePrivacy();
    let error = '';
    try { await page.finishLogin({ token: 'synthetic-not-a-real-token', account: { id: '1' } }); }
    catch (failure) { error = failure.message; }
    return { page, session, home, logouts, error, validPolicy };
}
function check(ok, label) { assert.ok(ok, label); console.log('PASS: ' + label); count++; }
(async () => {
    for (const receipt of [undefined, null, {}, [], { accepted: false, version: policy.version },
        { accepted: 'true', version: policy.version }, { accepted: true, version: 'stale' }, { accepted: true }]) {
        const result = await run(policy, receipt);
        check(!result.home && !result.session && result.logouts === 1 && result.error.includes('协议同意记录失败'),
            'invalid consent body revokes and clears session: ' + JSON.stringify(receipt));
    }
    for (const field of Object.keys(policy)) {
        for (const value of [undefined, null, '', '   ', 123, {}, []]) {
            const result = await run({ ...policy, [field]: value }, { accepted: true, version: policy.version });
            check(!result.validPolicy && !result.page.data.privacyAccepted && !result.page.canSubmit()
                && !result.home && !result.session && result.page.data.error.includes('隐私政策加载失败'),
                'invalid policy field blocked: ' + field + '=' + JSON.stringify(value));
        }
    }
    for (const date of ['2026-02-30', '2026-13-01', 'not-a-date']) {
        const result = await run({ ...policy, effectiveDate: date }, { accepted: true, version: policy.version });
        check(!result.validPolicy && !result.home, 'invalid calendar date blocked: ' + date);
    }
    const good = await run(policy, { accepted: true, version: policy.version });
    check(good.home && good.session && !good.logouts && !good.error, 'complete policy and matching receipt enter home');
    const replaced = await run(policy, { accepted: true, version: 'replacement' }, true);
    check(!replaced.home && !replaced.session && replaced.logouts === 1, 'receipt must match version captured before asynchronous request');
    console.log('PASS total=' + count + '; Node harness, not WeChat compilation');
})().catch(error => { console.error('FAIL: ' + error.message); process.exitCode = 1; });
