// Isolated v2 ADMIN API QA. Only targets localhost by default; never use production.
const assert = require('node:assert/strict')
const cliArgs = process.argv.slice(2)
const baseArgIndex = cliArgs.indexOf('--base-url')
const cliBase = baseArgIndex >= 0 ? cliArgs[baseArgIndex + 1] : undefined
if (baseArgIndex >= 0 && (!cliBase || cliBase.startsWith('--'))) {
  console.error('FAIL: --base-url requires an absolute HTTP(S) URL')
  process.exit(2)
}
const base = cliBase || process.env.V2_ADMIN_BASE_URL || 'http://127.0.0.1:8080'
const url = new URL(base)
if (!['127.0.0.1', 'localhost', '::1'].includes(url.hostname) && process.env.V2_QA_ALLOW_NONLOCAL !== '1') {
  console.error('BLOCKED: v2 QA refuses non-local API hosts; set V2_QA_ALLOW_NONLOCAL=1 only for an approved isolated test host.')
  process.exit(2)
}
const phone = process.env.V2_ADMIN_PHONE
const password = process.env.V2_ADMIN_PASSWORD
const suppliedToken = process.env.RR_V2_TEST_TOKEN
const allowMutation = process.env.V2_QA_ALLOW_MUTATION === '1'

async function call(path, { token, method = 'GET', body, headers = {}, expected } = {}) {
  const requestHeaders = { Accept: 'application/json', ...headers }
  if (token) requestHeaders.Authorization = `Bearer ${token}`
  if (body !== undefined) requestHeaders['Content-Type'] = 'application/json'
  const response = await fetch(base + path, { method, headers: requestHeaders, body: body === undefined ? undefined : JSON.stringify(body) })
  if (expected !== undefined) assert.equal(response.status, expected, `${method} ${path}`)
  return response
}

async function main() {
  if (!suppliedToken && (!phone || !password)) { console.error('BLOCKED: set V2_ADMIN_PHONE/V2_ADMIN_PASSWORD or RR_V2_TEST_TOKEN for an isolated v2 fixture.'); process.exit(2) }
  let token = suppliedToken
  if (!token) {
    if (!phone || !password) throw new Error('set V2_ADMIN_PHONE and V2_ADMIN_PASSWORD, or RR_V2_TEST_TOKEN')
    const login = await call('/api/v2/auth/password/login', { method: 'POST', body: { phone, password }, expected: 200 })
    const result = await login.json()
    assert.ok(result.token && result.account, 'login must return token and account')
    assert.equal(result.account.role, 'ADMIN')
    assert.equal(result.account.adminEnabled, true)
    token = result.token
    console.log('PASS login: single ADMIN account')
  }

  const session = await call('/api/v2/auth/session', { token, expected: 200 }).then(r => r.json())
  assert.equal(session.role, 'ADMIN')
  assert.equal(session.adminEnabled, true)
  console.log('PASS session restore')

  await call('/api/v2/notifications', { expected: 401 })
  await call('/api/v2/notifications', { token, expected: 200 }).then(async r => {
    const items = await r.json()
    assert.ok(Array.isArray(items), 'notifications must be an array')
    if (items[0]) {
      await call(`/api/v2/notifications/${items[0].id}/read`, { token, method: 'POST', expected: 200 })
      await call(`/api/v2/notifications/${items[0].id}/read`, { token, method: 'POST', expected: 200 })
      console.log('PASS notification read idempotency')
    } else console.log('PASS notification isolation/list (empty fixture)')
  })

  await call('/api/v2/courier/certification/admin?status=PENDING&page=0&size=20', { token, expected: 200 })
  console.log('PASS certification list and ADMIN authorization')

  if (allowMutation) {
    const id = Number(process.env.V2_QA_CERTIFICATION_ID)
    const version = Number(process.env.V2_QA_EXPECTED_VERSION)
    const photoResourceId = process.env.V2_QA_PHOTO_RESOURCE_ID || null
    assert.ok(id > 0 && Number.isInteger(version), 'mutation fixture requires certification id and expected version')
    const key = `v2-admin-qa-${crypto.randomUUID()}`
    const body = { decision: process.env.V2_QA_DECISION || 'REJECT', expectedVersion: version, photoResourceId, reason: process.env.V2_QA_REASON || '隔离 QA 审核' }
    await call(`/api/v2/courier/certification/${id}/decision`, { token, method: 'POST', body, headers: { 'Idempotency-Key': key }, expected: 200 })
    console.log('PASS certification decision contract and idempotency key')
  } else console.log('SKIP certification mutation: set V2_QA_ALLOW_MUTATION=1 in an isolated fixture only')

  const logoutKey = `v2-admin-qa-${Date.now()}`
  await call('/api/v2/auth/logout', { token, method: 'POST', headers: { 'Idempotency-Key': logoutKey }, body: { allSessions: false }, expected: 200 })
  await call('/api/v2/auth/session', { token, expected: 401 })
  console.log('PASS logout and session invalidation')
}

main().catch(error => { console.error(`FAIL: ${error.message}`); process.exit(1) })


