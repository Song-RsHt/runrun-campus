// Real-browser acceptance against the explicitly labelled, disposable QA backend.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { chromium } = require('playwright');
const { createHmac } = require('node:crypto');
const BASE = 'http://127.0.0.1:28180';
const OUT = path.resolve(process.argv[2]);
const PASSWORD = 'AdminRoleQA2026';
const roles = ['SUPER_ADMIN', 'OPERATIONS', 'SUPPORT', 'REVIEWER', 'AUDITOR'];
const rights = {
  SUPER_ADMIN: ['DASHBOARD_VIEW','ORDER_VIEW','ORDER_EXPORT','USER_VIEW','USER_MANAGE','COURIER_VIEW','COURIER_REVIEW','ISSUE_VIEW','ISSUE_MANAGE','FEEDBACK_VIEW','FEEDBACK_MANAGE','AUDIT_VIEW','ADMIN_MANAGE'],
  OPERATIONS: ['DASHBOARD_VIEW','ORDER_VIEW','ORDER_EXPORT','USER_VIEW','COURIER_VIEW','COURIER_REVIEW','ISSUE_VIEW','ISSUE_MANAGE','FEEDBACK_VIEW','FEEDBACK_MANAGE'],
  SUPPORT: ['DASHBOARD_VIEW','ORDER_VIEW','USER_VIEW','ISSUE_VIEW','ISSUE_MANAGE','FEEDBACK_VIEW','FEEDBACK_MANAGE'],
  REVIEWER: ['DASHBOARD_VIEW','USER_VIEW','COURIER_VIEW','COURIER_REVIEW'],
  AUDITOR: ['DASHBOARD_VIEW','ORDER_VIEW','USER_VIEW','COURIER_VIEW','ISSUE_VIEW','FEEDBACK_VIEW','AUDIT_VIEW'],
};
const views = [
  ['运营总览','DASHBOARD_VIEW','dashboard'], ['订单管理','ORDER_VIEW','orders'],
  ['骑手管理','COURIER_VIEW','couriers'], ['用户管理','USER_VIEW','users'],
  ['异常工单','ISSUE_VIEW','issues'], ['意见反馈','FEEDBACK_VIEW','feedback'],
  ['管理员账号','ADMIN_MANAGE','accounts'], ['操作日志','AUDIT_VIEW','audit-logs'],
];
const result = { checks: [], requests: [], browserErrors: [], success: false };
const sessions = {}, pages = {}, fixtures = {};
const mfaSecrets = {}, recoveryCodes = {}, lastSteps = {};
let authWindow = Date.now(), authCount = 0;
let browser, activePage;
function check(name, value) { assert.ok(value, name); result.checks.push(name); }
async function authBudget() {
  if (Date.now() - authWindow >= 61000) { authWindow = Date.now(); authCount = 0; }
  if (authCount >= 26) {
    await new Promise(resolve => setTimeout(resolve, Math.max(0, 61000 - (Date.now() - authWindow))));
    authWindow = Date.now(); authCount = 0;
  }
  authCount++;
}
function totp(secret, step = Math.floor(Date.now() / 30000)) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  const bits = [...secret].map(c => alphabet.indexOf(c).toString(2).padStart(5,'0')).join('');
  const bytes = Buffer.from(bits.match(/.{8}/g).map(byte => parseInt(byte,2)));
  const counter = Buffer.alloc(8); counter.writeBigUInt64BE(BigInt(step));
  const hash = createHmac('sha1',bytes).update(counter).digest();
  return String((hash.readUInt32BE(hash[19] & 15) & 0x7fffffff) % 1000000).padStart(6,'0');
}
async function api(url, token, method = 'GET', body, expected = 200) {
  if (url.startsWith('/api/v1/auth/')) await authBudget();
  const response = await fetch(BASE + url, {method, headers: {
    ...(token ? {Authorization: `Bearer ${token}`} : {}),
    ...(body ? {'Content-Type':'application/json'} : {}),
  }, ...(body ? {body: JSON.stringify(body)} : {})});
  result.requests.push({url, method, expected, actual: response.status});
  assert.equal(response.status, expected, `${method} ${url}`);
  const text = await response.text();
  return response.headers.get('content-type')?.includes('json') ? JSON.parse(text) : text;
}
async function login(role, password = PASSWORD) {
  const context = await browser.newContext({viewport: {width: 1440, height: 1000}});
  const page = await context.newPage(); activePage = page;
  page.setDefaultTimeout(10000);
  page.on('pageerror', e => result.browserErrors.push(e.message));
  page.on('dialog', dialog => dialog.accept());
  await page.goto(BASE);
  await page.getByLabel('手机号', {exact:true}).fill(`1990300000${roles.indexOf(role)}`);
  await page.getByLabel('密码', {exact:true}).fill(password);
  await authBudget();
  const response = page.waitForResponse(r => r.url().endsWith('/auth/admin/login'));
  await page.getByRole('button', {name:'登录运营后台', exact:true}).click();
  const challenge = await (await response).json();
  check(`${role}: password alone yields no session`, !challenge.token && !challenge.session && Boolean(challenge.challengeToken));
  check(`${role}: challenge never stored in browser session storage`, await page.evaluate(() => sessionStorage.length === 0));
  const auth = await finishMfa(page, role, challenge);
  await page.locator('.sidebar-user').getByText('QA_' + role, {exact:true}).waitFor();
  check(`${role}: account name has usable sidebar width`, await page.locator('.sidebar-user > span:nth-child(2)').evaluate(element => element.clientWidth >= 90));
  check(`${role}: password login and exact permission set`,
    JSON.stringify([...auth.user.permissions].sort()) === JSON.stringify([...rights[role]].sort()));
  sessions[role] = auth; pages[role] = page;
  return page;
}
async function finishMfa(page, role, challenge) {
  const enrolling = ['ENROLL','REPLACE'].includes(challenge.step);
  await authBudget();
  let code;
  if (enrolling) {
    mfaSecrets[role] = await page.getByLabel('手动添加密钥',{exact:true}).inputValue();
    await page.waitForFunction(() => {
      const canvas = document.querySelector('canvas');
      return canvas && canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data.some((value, index) => index % 4 === 3 && value > 0);
    });
    check(`${role}: local QR canvas rendered`, true);
    code = totp(mfaSecrets[role]);
    lastSteps[role] = Math.floor(Date.now()/30000);
    await shot(page, role.toLowerCase()+'-mfa-enrollment');
  } else {
    if (lastSteps[role] === Math.floor(Date.now()/30000)) {
      await new Promise(resolve => setTimeout(resolve, 30100 - Date.now()%30000));
    }
    code = totp(mfaSecrets[role]);
    lastSteps[role] = Math.floor(Date.now()/30000);
  }
  await page.getByLabel(enrolling ? '认证器动态码' : '动态码或恢复码',{exact:true}).fill(code);
  const verified = page.waitForResponse(r => r.url().endsWith('/auth/admin/verify'));
  await page.getByRole('button',{name:enrolling ? '确认绑定认证器' : '确认验证',exact:true}).click();
  const response = await verified; assert.equal(response.status(),200);
  const data = await response.json();
  if (enrolling) {
    recoveryCodes[role] = (await page.getByLabel('一次性恢复码',{exact:true}).inputValue()).split('\n');
    check(`${role}: ten recovery codes displayed once`, recoveryCodes[role].length === 10 && new Set(recoveryCodes[role]).size === 10);
    assert.ok(await page.getByRole('button',{name:'进入运营后台',exact:true}).isDisabled());
    await page.getByLabel('我已安全保存恢复码',{exact:true}).check();
    await page.getByRole('button',{name:'进入运营后台',exact:true}).click();
  }
  return data.session;
}
async function adminApiLogin(role, password = PASSWORD) {
  const challenge = await api('/api/v1/auth/admin/login',null,'POST',{phone:`1990300000${roles.indexOf(role)}`,password});
  const verified = await api('/api/v1/auth/admin/verify',null,'POST',{challengeToken:challenge.challengeToken,code:recoveryCodes[role].shift()});
  return verified.session;
}
async function nav(page, label) {
  activePage = page;
  await page.getByRole('navigation').getByRole('button', {name:new RegExp(label + '$')}).click();
  await page.getByRole('heading', {name:label, exact:true, level:1}).waitFor();
}
async function shot(page, name) { await page.screenshot({path:path.join(OUT, name + '.png'), fullPage:true}); }
async function action(page, label, endpoint, method='POST', target=page) {
  const response = page.waitForResponse(r => r.url().endsWith(endpoint) && r.request().method() === method);
  await target.getByRole('button', {name:label, exact:true}).click();
  assert.equal((await response).status(), 200, label);
}
async function detail(page, text) {
  await page.getByRole('row').filter({hasText:text}).getByRole('button', {name:'查看详情', exact:true}).click();
  await page.getByRole('dialog').waitFor();
}
async function close(page) { await page.getByRole('dialog').getByRole('button', {name:'关闭',exact:true}).click(); }
function digest() {
  return execFileSync('docker', ['exec','runrun-adminqa-db-20260903','psql','-U','qa','-d','qa','-At','-c',
    ['app_users','campus_orders','courier_certifications','order_issues','user_feedback','admin_audit_logs'].map(
      table => `select md5(coalesce(jsonb_agg(to_jsonb(t) order by id)::text,'[]')) from ${table} t;`).join(' ')], {encoding:'utf8'});
}
async function main() {
  for (const name of ['runrun-adminqa-api-20260903','runrun-adminqa-db-20260903']) {
    assert.equal(execFileSync('docker',['inspect','-f','{{index .Config.Labels "runrun.adminqa"}}',name],{encoding:'utf8'}).trim(),'20260903');
  }
  fs.mkdirSync(OUT, {recursive:true});
  browser = await chromium.launch({channel:'msedge', headless:true});
  const superPage = await login('SUPER_ADMIN');
  await nav(superPage, '管理员账号');
  for (const role of roles.slice(1)) {
    await superPage.getByLabel('姓名',{exact:true}).fill('QA_' + role);
    await superPage.getByLabel('手机号',{exact:true}).fill(`1990300000${roles.indexOf(role)}`);
    await superPage.getByLabel('初始密码',{exact:true}).fill(PASSWORD);
    await superPage.locator('.admin-create-form select').selectOption(role);
    await action(superPage,'创建管理员','/api/v1/admin/accounts');
    await superPage.getByRole('row').filter({hasText:'QA_' + role}).waitFor();
  }
  check('SUPER_ADMIN: create all four job accounts through UI', true);
  await shot(superPage,'super-created-accounts');
  const customer = await api('/api/v1/auth/register',null,'POST',{phone:'19903000100',password:PASSWORD,displayName:'QA_CUSTOMER',role:'CUSTOMER'});
  for (const role of roles) {
    const order = await api('/api/v1/orders',customer.token,'POST',{merchant:'QA_' + role,pickupCode:'1234',pickupLocation:'QA Station',destination:'QA Building',serviceType:'PACKAGE_MEDIUM',couponId:null,useMembership:false});
    const issue = await api(`/api/v1/orders/${order.id}/issues`,customer.token,'POST',{type:'OTHER',description:'QA_' + role});
    const feedback = await api('/api/v1/feedback',customer.token,'POST',{type:'OTHER',content:'QA_' + role});
    const courier = await api('/api/v1/auth/register',null,'POST',{phone:`1990300020${roles.indexOf(role)}`,password:PASSWORD,displayName:'QA_COURIER_' + role,role:'COURIER'});
    const cert = await api('/api/v1/courier-certification/apply',courier.token,'POST',{realName:'QA Rider',studentId:'QA' + roles.indexOf(role),campus:'QA Campus'});
    fixtures[role] = {order,issue,feedback,courierId:courier.user.id,certId:cert.id};
  }
  for (const role of roles.slice(1)) await login(role);
  for (const role of roles) {
    const page = pages[role], token = sessions[role].token;
    const permits = p => rights[role].includes(p);
    for (const [label,permission,endpoint] of views) {
      const visible = await page.getByRole('navigation').getByRole('button',{name:new RegExp(label+'$')}).count();
      check(`${role}: ${label} navigation`, Boolean(visible) === permits(permission));
      await api('/api/v1/admin/' + endpoint,token,'GET',null,permits(permission)?200:403);
    }
    for (const [url,permission] of [[`/api/v1/admin/orders/${fixtures[role].order.id}`,'ORDER_VIEW'],[`/api/v1/admin/couriers/${fixtures[role].courierId}/performance`,'COURIER_VIEW'],['/api/v1/admin/orders/export','ORDER_EXPORT'],['/api/v1/courier-certification/pending','COURIER_REVIEW']]) {
      await api(url,token,'GET',null,permits(permission)?200:403);
    }
    await nav(page,'运营总览'); await shot(page,role.toLowerCase()+'-dashboard');
    if (permits('ORDER_VIEW')) {
      await nav(page,'订单管理');
      check(`${role}: export button`, Boolean(await page.getByRole('button',{name:'导出 CSV',exact:true}).count()) === permits('ORDER_EXPORT'));
      if (permits('ORDER_EXPORT')) {
        const download = page.waitForEvent('download');
        await page.getByRole('button',{name:'导出 CSV',exact:true}).click();
        await (await download).saveAs(path.join(OUT,role.toLowerCase()+'-orders.csv'));
      }
    }
    await nav(page,'用户管理'); await detail(page,'QA_CUSTOMER');
    check(`${role}: user suspension button`, Boolean(await page.getByRole('button',{name:'停用此账号',exact:true}).count()) === permits('USER_MANAGE'));
    await close(page);
    if (permits('COURIER_VIEW')) {
      await nav(page,'骑手管理'); await detail(page,'QA_COURIER_' + role);
      check(`${role}: review buttons`, Boolean(await page.getByRole('button',{name:'通过认证',exact:true}).count()) === permits('COURIER_REVIEW'));
      if (permits('COURIER_REVIEW')) {
        await action(page,'通过认证',`/api/v1/courier-certification/${fixtures[role].certId}/approve`);
        await page.getByRole('dialog').waitFor({state:'hidden'});
        await detail(page,'QA_COURIER_' + role);
        check(`${role}: approve courier through UI`, true);
      }
      await shot(page,role.toLowerCase()+'-courier'); await close(page);
    }
    for (const [label,kind,claimButton] of [['异常工单','issue','领取此工单'],['意见反馈','feedback','领取此反馈']]) {
      const permission = kind === 'issue' ? 'ISSUE' : 'FEEDBACK';
      if (!permits(permission + '_VIEW')) continue;
      await nav(page,label); await detail(page,'QA_' + role);
      check(`${role}: ${kind} claim button`, Boolean(await page.getByRole('button',{name:claimButton,exact:true}).count()) === permits(permission + '_MANAGE'));
      if (permits(permission + '_MANAGE')) {
        const endpoint = `/api/v1/admin/${kind === 'issue' ? 'issues' : 'feedback'}/${fixtures[role][kind].id}`;
        await action(page,claimButton,endpoint+'/claim');
        await page.getByLabel('处理结果',{exact:true}).fill('QA resolved by ' + role);
        await action(page,'标记为已解决',endpoint+'/resolve');
        await page.getByRole('dialog').getByText('QA resolved by ' + role,{exact:true}).waitFor();
        check(`${role}: ${kind} claim and resolve through UI`,true);
      }
      await shot(page,role.toLowerCase()+'-'+kind); await close(page);
    }
    const f = fixtures[role];
    const forbidden = [
      ['USER_MANAGE',`/api/v1/admin/users/${customer.user.id}/suspend`,'POST'],
      ['USER_MANAGE',`/api/v1/admin/users/${customer.user.id}/restore`,'POST'],
      ['USER_MANAGE',`/api/v1/admin/users/${customer.user.id}/unlock-login`,'POST'],
      ['COURIER_REVIEW',`/api/v1/courier-certification/${f.certId}/approve`,'POST'],
      ['COURIER_REVIEW',`/api/v1/courier-certification/${f.certId}/reject`,'POST',{reason:'QA denied'}],
      ['ISSUE_MANAGE',`/api/v1/admin/issues/${f.issue.id}/claim`,'POST'],
      ['ISSUE_MANAGE',`/api/v1/admin/issues/${f.issue.id}/resolve`,'POST',{resolution:'QA denied'}],
      ['FEEDBACK_MANAGE',`/api/v1/admin/feedback/${f.feedback.id}/claim`,'POST'],
      ['FEEDBACK_MANAGE',`/api/v1/admin/feedback/${f.feedback.id}/resolve`,'POST',{resolution:'QA denied'}],
      ['ADMIN_MANAGE','/api/v1/admin/accounts','POST',{phone:'19903000999',password:PASSWORD,displayName:'QA forbidden',adminRole:'SUPER_ADMIN'}],
      ['ADMIN_MANAGE',`/api/v1/admin/accounts/${sessions.SUPER_ADMIN.user.id}/role`,'PUT',{adminRole:'SUPPORT'}],
      ['ADMIN_MANAGE',`/api/v1/admin/accounts/${sessions.SUPER_ADMIN.user.id}/suspend`,'POST'],
      ['ADMIN_MANAGE',`/api/v1/admin/accounts/${sessions.SUPER_ADMIN.user.id}/restore`,'POST'],
      ['ADMIN_MANAGE',`/api/v1/admin/accounts/${sessions.SUPER_ADMIN.user.id}/password`,'PUT',{password:PASSWORD+'new'}],
      ['ADMIN_MANAGE',`/api/v1/admin/accounts/${sessions.SUPER_ADMIN.user.id}/unlock-login`,'POST'],
    ];
    const before = digest();
    for (const [permission,url,method,body] of forbidden) if (!permits(permission)) await api(url,token,method,body,403);
    check(`${role}: denied mutations leave business data and audit unchanged`,digest() === before);
    console.log('PASS role: ' + role);
  }
  // Actual user management plus revocation of already issued administrator sessions.
  await nav(superPage,'用户管理'); await detail(superPage,'QA_CUSTOMER');
  await action(superPage,'停用此账号',`/api/v1/admin/users/${customer.user.id}/suspend`);
  await api('/api/v1/orders/mine',customer.token,'GET',null,401);
  await action(superPage,'恢复此账号',`/api/v1/admin/users/${customer.user.id}/restore`);
  check('SUPER_ADMIN: suspend and restore customer, revoke old session',true);
  await close(superPage); await nav(superPage,'管理员账号');
  const supportRow = superPage.getByRole('row').filter({hasText:'QA_SUPPORT'});
  const changed = superPage.waitForResponse(r => r.url().endsWith(`/accounts/${sessions.SUPPORT.user.id}/role`) && r.request().method() === 'PUT');
  await supportRow.locator('select').selectOption('REVIEWER');
  assert.equal((await changed).status(),200);
  await api('/api/v1/admin/orders',sessions.SUPPORT.token,'GET',null,401);
  await pages.SUPPORT.getByRole('navigation').getByRole('button',{name:/订单管理$/}).click();
  await pages.SUPPORT.getByRole('heading',{name:'管理员登录',exact:true}).waitFor();
  check('Changed role: old browser session exits on next request',true);
  await shot(pages.SUPPORT,'role-change-session-expired');
  const changedLogin = await adminApiLogin('SUPPORT');
  check('Changed role: re-login has new role',changedLogin.user.adminRole === 'REVIEWER');
  await api('/api/v1/admin/orders',changedLogin.token,'GET',null,403);
  const auditorRow = superPage.getByRole('row').filter({hasText:'QA_AUDITOR'});
  await action(superPage,'停用账号',`/api/v1/admin/accounts/${sessions.AUDITOR.user.id}/suspend`,'POST',auditorRow);
  await api('/api/v1/admin/audit-logs',sessions.AUDITOR.token,'GET',null,401);
  await action(superPage,'恢复账号',`/api/v1/admin/accounts/${sessions.AUDITOR.user.id}/restore`,'POST',auditorRow);
  check('Admin suspension: old session stays invalid after restore',true);
  await api('/api/v1/admin/audit-logs',sessions.AUDITOR.token,'GET',null,401);
  const activeAuditor = await adminApiLogin('AUDITOR');
  await auditorRow.getByRole('button',{name:'重置密码',exact:true}).click();
  await superPage.getByLabel('新密码',{exact:true}).fill(PASSWORD+'new');
  await action(superPage,'确认重置密码',`/api/v1/admin/accounts/${sessions.AUDITOR.user.id}/password`,'PUT');
  await api('/api/v1/admin/audit-logs',activeAuditor.token,'GET',null,401);
  await api('/api/v1/auth/admin/login',null,'POST',{phone:'19903000004',password:PASSWORD},401);
  await login('AUDITOR',PASSWORD+'new');
  check('Admin password reset: old password rejected, new password logs in',true);
  // Existing authenticator login, one-time recovery, replacement, and supervised reset.
  await login('OPERATIONS');
  const operationsPage = pages.OPERATIONS;
  const oldSession = sessions.OPERATIONS.token;
  const oldCodes = [...recoveryCodes.OPERATIONS];
  await operationsPage.getByTitle('认证器设置',{exact:true}).click();
  await operationsPage.getByLabel('当前密码',{exact:true}).fill(PASSWORD);
  await operationsPage.getByLabel('动态码或恢复码',{exact:true}).fill(oldCodes[0]);
  const replacing = operationsPage.waitForResponse(r => r.url().endsWith('/mfa/replace'));
  await operationsPage.getByRole('button',{name:'验证并更换认证器',exact:true}).click();
  const replacement = await replacing;
  assert.equal(replacement.status(),200);
  const replaced = await finishMfa(operationsPage,'OPERATIONS',await replacement.json());
  await operationsPage.getByRole('dialog').waitFor({state:'hidden'});
  sessions.OPERATIONS = replaced;
  await api('/api/v1/admin/dashboard',oldSession,'GET',null,401);
  check('MFA replacement: browser receives new session and old session is revoked',true);
  await operationsPage.getByTitle('退出登录',{exact:true}).click();
  await operationsPage.getByLabel('手机号',{exact:true}).fill('19903000001');
  await operationsPage.getByLabel('密码',{exact:true}).fill(PASSWORD);
  await authBudget();
  await action(operationsPage,'登录运营后台','/api/v1/auth/admin/login');
  await operationsPage.getByLabel('动态码或恢复码',{exact:true}).fill(oldCodes[1]);
  await authBudget();
  const obsolete = operationsPage.waitForResponse(r => r.url().endsWith('/auth/admin/verify'));
  await operationsPage.getByRole('button',{name:'确认验证',exact:true}).click();
  assert.equal((await obsolete).status(),401);
  await operationsPage.getByRole('alert').waitFor();
  check('MFA replacement: old recovery code rejected through UI',true);
  const recoveryCode = recoveryCodes.OPERATIONS.shift();
  await operationsPage.getByLabel('动态码或恢复码',{exact:true}).fill(recoveryCode);
  await authBudget();
  const recovered = operationsPage.waitForResponse(r => r.url().endsWith('/auth/admin/verify'));
  await operationsPage.getByRole('button',{name:'确认验证',exact:true}).click();
  const recoveredResponse = await recovered;
  assert.equal(recoveredResponse.status(),200);
  sessions.OPERATIONS = (await recoveredResponse.json()).session;
  await operationsPage.getByRole('heading',{name:'运营总览',exact:true,level:1}).waitFor();
  const replay = await api('/api/v1/auth/admin/login',null,'POST',{phone:'19903000001',password:PASSWORD});
  await api('/api/v1/auth/admin/verify',null,'POST',{challengeToken:replay.challengeToken,code:recoveryCode},401);
  check('MFA recovery: browser login succeeds and used recovery code cannot be replayed',true);

  await nav(superPage,'管理员账号');
  const operationsRow = superPage.getByRole('row').filter({hasText:'QA_OPERATIONS'});
  await operationsRow.getByRole('button',{name:'重置认证器',exact:true}).click();
  await superPage.getByLabel('身份核验记录及重置原因',{exact:true}).fill('QA 当面核验校园证件');
  await action(superPage,'已核验身份，确认重置',`/api/v1/admin/accounts/${sessions.OPERATIONS.user.id}/mfa/reset`);
  const grant = await superPage.getByLabel('一次性重置凭证',{exact:true}).inputValue();
  check('MFA reset: one-time grant displayed',grant.length >= 40);
  await shot(superPage,'super-mfa-reset-grant');
  await api('/api/v1/admin/dashboard',sessions.OPERATIONS.token,'GET',null,401);
  await operationsPage.reload();
  await operationsPage.getByRole('heading',{name:'管理员登录',exact:true}).waitFor();
  await operationsPage.getByLabel('手机号',{exact:true}).fill('19903000001');
  await operationsPage.getByLabel('密码',{exact:true}).fill(PASSWORD);
  await authBudget();
  await action(operationsPage,'登录运营后台','/api/v1/auth/admin/login');
  await operationsPage.getByRole('heading',{name:'重新绑定验证',exact:true}).waitFor();
  check('MFA reset: no enrollment secret before grant verification',await operationsPage.getByLabel('手动添加密钥',{exact:true}).count() === 0);
  await operationsPage.getByLabel('一次性重置凭证',{exact:true}).fill('invalid-grant');
  await authBudget();
  const invalidGrant = operationsPage.waitForResponse(r => r.url().endsWith('/auth/admin/verify'));
  await operationsPage.getByRole('button',{name:'确认验证',exact:true}).click();
  assert.equal((await invalidGrant).status(),401);
  await operationsPage.getByRole('alert').waitFor();
  await operationsPage.getByLabel('一次性重置凭证',{exact:true}).fill(grant);
  await authBudget();
  const granting = operationsPage.waitForResponse(r => r.url().endsWith('/auth/admin/verify'));
  await operationsPage.getByRole('button',{name:'确认验证',exact:true}).click();
  const grantResponse = await granting;
  assert.equal(grantResponse.status(),200);
  const enrollment = (await grantResponse.json()).challenge;
  sessions.OPERATIONS = await finishMfa(operationsPage,'OPERATIONS',enrollment);
  await operationsPage.getByRole('heading',{name:'运营总览',exact:true,level:1}).waitFor();
  await operationsPage.reload();
  await operationsPage.getByRole('heading',{name:'运营总览',exact:true,level:1}).waitFor();
  check('MFA reset: grant and new authenticator restore access, refresh keeps no recovery codes',await operationsPage.getByLabel('一次性恢复码',{exact:true}).count() === 0);
  check('No uncaught browser exceptions',result.browserErrors.length === 0);
  result.success = true;
}
main().catch(async error => {
  result.error = error.message;
  if (activePage) await shot(activePage,'failure').catch(()=>{});
  console.error(error.stack); process.exitCode = 1;
}).finally(async () => {
  if (browser) await browser.close();
  fs.writeFileSync(path.join(OUT,'acceptance.json'),JSON.stringify(result,null,2));
  console.log(JSON.stringify({success:result.success,checks:result.checks.length,requests:result.requests.length}));
});
