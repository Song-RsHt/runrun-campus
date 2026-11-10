const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
// 仅用于本轮独立合成夹具；不读取浏览器凭据或操作其他 Compose 项目。
const root = path.resolve(__dirname, '..');
const envFile = process.env.RRC_QA_ENV_FILE || path.join(root, '.env.v2-test');
const fixture = Object.fromEntries(fs.readFileSync(envFile, 'utf8').split(/\r?\n/)
    .filter(line => line && !line.startsWith('#') && line.includes('='))
    .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const project = process.env.COMPOSE_PROJECT_NAME;
const origin = process.env.RRC_LOCAL_ADMIN_ORIGIN || 'http://127.0.0.1:5186';
if (!/^rrc-entry-[a-z0-9-]+$/.test(project || '') || fixture.RRC_V2_ADMIN_PHONE !== 'test-admin'
    || fixture.RRC_V2_ENVIRONMENT !== 'test' || fixture.RRC_PAYMENT_MOCK_ENABLED !== 'true'
    || !/^http:\/\/127\.0\.0\.1:\d+$/.test(origin)) throw new Error('Requires dedicated rrc-entry-* synthetic fixture');
const { chromium } = require(process.env.RRC_PLAYWRIGHT_MODULE || 'playwright');
const evidence = process.env.RRC_QA_OUTPUT;
const api = 'http://127.0.0.1:8082';
let count = 0;
function check(ok, label) { assert.ok(ok, label); count++; console.log('PASS: ' + label); }
function sql(statement) {
    execFileSync('docker', ['compose', '--env-file', envFile, '-f', path.join(root, 'compose.v2-test.yml'),
        'exec', '-T', 'postgres', 'psql', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-U', fixture.RRC_V2_DB_USERNAME, '-d', 'rrc_v2_test'],
        { input: statement, stdio: ['pipe', 'pipe', 'pipe'] });
}
async function request(url, token, init = {}) {
    return fetch(api + url, { ...init, headers: { 'Content-Type': 'application/json', Origin: origin,
        ...(token ? { Authorization: 'Bearer ' + token } : {}), ...init.headers } });
}
(async () => {
    let browser;
    try {
        const policy = await request('/api/v2/privacy/policy');
        const body = await policy.json();
        check(policy.status === 200 && body.version === '2026-10-24' && body.title && body.content.includes('一、我们处理的信息'), 'anonymous v2 policy fields and approved version');
        check((await request('/api/v2/auth/session')).status === 401, 'anonymous session denied');
        check((await request('/api/v2/privacy/consent', null, { method: 'POST', body: JSON.stringify({ version: body.version }) })).status === 401, 'anonymous consent denied');
        const preflight = await request('/api/v2/auth/session', null, { method: 'OPTIONS', headers: { 'Access-Control-Request-Method': 'GET', 'Access-Control-Request-Headers': 'authorization' } });
        check(preflight.status === 200 && preflight.headers.get('access-control-allow-origin') === origin, 'exact Origin OPTIONS succeeds');
        check((await request('/api/v2/auth/session', null, { method: 'OPTIONS', headers: { Origin: 'http://127.0.0.1:5999', 'Access-Control-Request-Method': 'GET' } })).status === 403, 'unconfigured Origin denied');
        browser = await chromium.launch({ channel: 'msedge', headless: true });
        const page = await browser.newPage();
        const errors = [];
        const lists = [];
        page.on('response', response => { if (response.url().includes('/certification/admin?')) lists.push({ url: response.url(), status: response.status() }); });
        const v1 = [];
        page.on('pageerror', error => errors.push(error.message));
        page.on('console', msg => { if (msg.type() === 'error' && msg.text().includes('includes')) errors.push(msg.text()); });
        page.on('request', req => { if (req.url().includes('/api/v1')) v1.push(req.url()); });
        async function login() {
            await page.locator('input[autocomplete=username]').fill('test-admin');
            await page.locator('input[type=password]').fill(fixture.RRC_V2_ADMIN_PASSWORD);
            await page.getByRole('button', { name: '登录运营后台' }).click();
        }
        async function home() { await page.getByRole('heading', { name: '管理端 v2 首页' }).waitFor(); }
        await page.goto(origin + '/v2-admin');
        await login(); await home(); check(true, 'real ADMIN login renders home');
        const stored = await page.evaluate(() => JSON.parse(sessionStorage.getItem('runrun_admin_session_v2')));
        const response = await request('/api/v2/auth/session', stored.token);
        const account = await response.json();
        check(response.status === 200 && Array.isArray(account.permissions) && account.permissions.includes('COURIER_VIEW'), 'restored account permissions JSON');
        check(response.headers.get('access-control-allow-origin') === origin, 'authenticated response exact CORS');
        check((await request('/api/v2/courier/certification', stored.token)).status === 403, 'ADMIN denied courier API');
        check((await request('/api/v2/admin/access/1', stored.token, { method: 'PATCH' })).status === 403, 'ADMIN cannot grant admin access');
        assert.match(account.id, /^\d+$/);
        const notificationEventKey = 'entry-nonempty-regression-' + Date.now();
        const notificationMessage = 'Entry synthetic notification ' + Date.now();
        sql(`INSERT INTO runrun_v2.user_notifications(user_id,type,content)
            VALUES (${account.id},'CERTIFICATION_UPDATED','{"eventKey":"${notificationEventKey}","message":"${notificationMessage}"}'::jsonb)
            ON CONFLICT DO NOTHING;`);
        const notices = await (await request('/api/v2/notifications', stored.token)).json();
        const notice = notices.find(item => item.content?.eventKey === notificationEventKey);
        check(notice && notice.content.message === notificationMessage && notice.orderId === null
            && Number.isFinite(Date.parse(notice.createdAt)) && notice.readAt === null
            && !('created_at' in notice) && !('value' in notice.content), 'real nonempty notification has camelCase fields, plain JSON and ISO date');
        await page.reload(); await home(); check(true, 'real refresh restores server session');
        if (evidence) await page.screenshot({ path: path.join(evidence, 'admin-home.png') });
        await page.getByRole('button', { name: /通知/ }).first().click();
        await page.getByRole('heading', { name: '站内消息', exact: true }).waitFor(); check(true, 'notification page renders');
        await page.getByText(notificationMessage, { exact: true }).waitFor();
        check(true, 'real nonempty message and creation date render without white screen');
        await page.getByRole('button', { name: '标记已读', exact: true }).first().click();
        await page.getByText('已读', { exact: true }).first().waitFor();
        await page.getByRole('button', { name: '刷新', exact: true }).click();
        await page.getByText(notificationMessage, { exact: true }).waitFor();
        const readNotices = await (await request('/api/v2/notifications', stored.token)).json();
        check(readNotices.find(item => item.id === notice.id)?.readAt, 'mark read persists and notification refresh restores state');
        await page.reload(); await home();
        await page.getByRole('button', { name: /通知/ }).first().click();
        await page.getByText(notificationMessage, { exact: true }).waitFor();
        await page.getByText('已读', { exact: true }).first().waitFor();
        if (evidence) await page.screenshot({ path: path.join(evidence, 'admin-nonempty-notifications.png') });
        for (const bad of [{ ...notice, createdAt: undefined }, { ...notice, createdAt: 'invalid' },
            { ...notice, readAt: 'invalid' }, { ...notice, content: null }]) {
            await page.route('**/api/v2/notifications', route => route.fulfill({ json: [bad] }));
            await page.getByRole('button', { name: '刷新', exact: true }).click();
            await page.getByText('站内消息响应异常，请刷新重试；如持续出现请联系管理员', { exact: true }).waitFor();
            check(true, 'malformed notification displays understandable error: ' + JSON.stringify({ createdAt: bad.createdAt, readAt: bad.readAt, nullContent: bad.content === null }));
            await page.unroute('**/api/v2/notifications');
        }
        await page.getByRole('button', { name: '刷新', exact: true }).click();
        await page.getByText(notificationMessage, { exact: true }).waitFor();
        await page.getByRole('button', { name: /骑手资质/ }).first().click();
        await page.getByRole('heading', { name: /骑手/ }).first().waitFor();
        await page.waitForTimeout(300);
        check(lists.some(item => item.status === 200), 'certification page and default list');
        const pending = page.getByRole('combobox').first();
        await pending.selectOption('PENDING');
        await page.waitForTimeout(250);
        await page.getByPlaceholder(/搜索|手机号/).first().fill('synthetic-no-match');
        await page.getByRole('button', { name: '查询骑手', exact: true }).click();
        await page.waitForTimeout(400);
        check(lists.some(item => item.url.includes('status=PENDING') && item.status === 200)
            && lists.some(item => item.url.includes('synthetic-no-match') && item.status === 200), 'PENDING and search list renders');
        if (evidence) await page.screenshot({ path: path.join(evidence, 'admin-certifications.png') });
        await page.getByRole('button', { name: '退出', exact: true }).click();
        await page.getByRole('heading', { name: '管理员登录' }).waitFor();
        await page.waitForTimeout(250);
        check(await page.evaluate(() => sessionStorage.getItem('runrun_admin_session_v2')) === null, 'logout clears permissions and token');
        check((await request('/api/v2/auth/session', stored.token)).status === 401, 'logout revokes server session');
        // 响应异常和权限不足使用明确的客户端故障注入，不冒充后端权限验证。
        await page.route('**/api/v2/auth/password/login', async route => {
            const response = await route.fetch(); const json = await response.json(); delete json.account.permissions;
            await route.fulfill({ response, json });
        });
        await login(); await page.getByRole('alert').waitFor();
        check((await page.getByRole('alert').innerText()).includes('会话信息异常'), 'malformed login displays understandable error');
        check(await page.evaluate(() => sessionStorage.getItem('runrun_admin_session_v2')) === null, 'malformed login stores no permissions');
        await page.unroute('**/api/v2/auth/password/login');
        await login(); await home();
        await page.route('**/api/v2/auth/session', async route => {
            const response = await route.fetch(); const json = await response.json(); json.permissions = [];
            await route.fulfill({ response, json });
        });
        await page.reload(); await home();
        check(await page.getByRole('button', { name: /骑手资质/ }).count() === 0, 'UI hides restricted page when server permissions empty (injected)');
        await page.unroute('**/api/v2/auth/session');
        await page.reload(); await home();
        await page.route('**/api/v2/auth/session', async route => {
            const response = await route.fetch(); const json = await response.json(); delete json.permissions;
            await route.fulfill({ response, json });
        });
        await page.reload(); await page.getByRole('heading', { name: '管理员登录' }).waitFor();
        check(await page.evaluate(() => sessionStorage.getItem('runrun_admin_session_v2')) === null,
            'malformed restored account clears previous account permissions (injected)');
        await page.unroute('**/api/v2/auth/session');
        await login(); await home();
        // 仅修改隔离库的合成账号；结束前恢复资格，所有旧会话保持失效。
        sql("UPDATE runrun_v2.app_users SET v2_admin_enabled=false, v2_admin_security_version=v2_admin_security_version+1 WHERE phone='test-admin';");
        await page.reload(); await page.getByRole('heading', { name: '管理员登录' }).waitFor();
        check(await page.evaluate(() => sessionStorage.getItem('runrun_admin_session_v2')) === null, 'real disabled ADMIN invalidates browser session');
        await login(); await page.getByRole('alert').waitFor(); check(true, 'disabled ADMIN login rejected without blank page');
        sql("UPDATE runrun_v2.app_users SET v2_admin_enabled=true, v2_admin_security_version=v2_admin_security_version+1 WHERE phone='test-admin';");
        await login(); await home();
        sql("UPDATE runrun_v2.user_sessions SET expires_at=now()-interval '1 second' WHERE user_id=(SELECT id FROM runrun_v2.app_users WHERE phone='test-admin');");
        await page.reload(); await page.getByRole('heading', { name: '管理员登录' }).waitFor();
        check(await page.evaluate(() => sessionStorage.getItem('runrun_admin_session_v2')) === null, 'real expired session returns to login');
        check(errors.length === 0, 'browser has no uncaught errors or includes exception');
        check(v1.length === 0, 'browser uses no v1 requests');
        console.log('PASS total=' + count);
    } finally {
        if (browser) await browser.close();
        sql("UPDATE runrun_v2.app_users SET v2_admin_enabled=true WHERE phone='test-admin'; UPDATE runrun_v2.user_sessions SET invalidated_at=now() WHERE user_id=(SELECT id FROM runrun_v2.app_users WHERE phone='test-admin') AND invalidated_at IS NULL;");
    }
})().catch(error => { console.error('FAIL: ' + error.message); process.exitCode = 1; });
