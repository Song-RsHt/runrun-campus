// v2 ADMIN API smoke test. Requires RR_V2_TEST_TOKEN from a controlled test login.
const assert = require('node:assert/strict');
const base = process.env.V2_ADMIN_BASE_URL || 'http://127.0.0.1:8080';
const token = process.env.RR_V2_TEST_TOKEN;
if (!token) { console.error('BLOCKED: set RR_V2_TEST_TOKEN; legacy five-role QA is not a v2 pass.'); process.exit(2); }
async function call(path, method='GET', body, expected=200) {
  const r = await fetch(base + path, {method, headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'}, ...(body ? {body:JSON.stringify(body)} : {})});
  assert.equal(r.status, expected, `${method} ${path}`); return r;
}
(async()=>{
  const session = await (await call('/api/v2/auth/session')).json();
  assert.equal(session.role, 'ADMIN'); assert.equal(session.adminEnabled, true);
  const notifications = await (await call('/api/v2/notifications')).json(); assert.ok(Array.isArray(notifications));
  await call('/api/v2/courier/certification/admin?status=PENDING&page=0&size=20');
  const logoutKey = `v2-admin-qa-${Date.now()}`;
  const logout = await fetch(base + '/api/v2/auth/logout', {method:'POST',headers:{Authorization:`Bearer ${token}`,'Idempotency-Key':logoutKey,'Content-Type':'application/json'},body:JSON.stringify({allSessions:false})});
  assert.equal(logout.status, 200); console.log('PASS: v2 single-ADMIN session, notification and certification routes');
})().catch(error=>{console.error(`FAIL: ${error.message}`); process.exit(1);});
