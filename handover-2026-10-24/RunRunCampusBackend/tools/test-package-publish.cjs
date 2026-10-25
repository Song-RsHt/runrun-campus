// Uses a new synthetic customer on a local backend; cancels every created order.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { randomBytes, randomInt, randomUUID } = require('node:crypto');

const base = new URL(process.argv[2]);
assert.ok(['127.0.0.1', 'localhost'].includes(base.hostname), 'Only a local test endpoint is allowed');
const reportPath = path.resolve(process.argv[3]);
assert.ok(!fs.existsSync(reportPath), 'Use a new report file');
const result = { success: false, checks: [], requests: [], orderIds: [] };
let token;
const pending = new Set();

async function request(route, method = 'GET', body, key, expected = 200) {
  const response = await fetch(new URL(route, base), {
    method, headers: {
      Authorization: `Bearer ${token ?? 'qa-invalid'}`,
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(key ? { 'Idempotency-Key': key } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
    signal: AbortSignal.timeout(10000),
  });
  result.requests.push({ route, method, status: response.status, expected });
  assert.equal(response.status, expected, `${method} ${route}`);
  return response.json();
}

async function main() {
  const sizes = { PACKAGE_SMALL: 100, PACKAGE_MEDIUM: 200, PACKAGE_LARGE: 400 };
  for (const serviceType of Object.keys(sizes)) {
    await request('/api/v1/orders', 'POST', {
      merchant: 'QA 快递发布验收', pickupCode: 'QA-123', pickupLocation: 'QA 驿站',
      destination: 'QA 宿舍', serviceType, useMembership: false,
    }, randomUUID(), 401);
  }
  result.checks.push('All package types pass parsing and still require authentication');
  const auth = await request('/api/v1/auth/register', 'POST', {
    phone: '199' + randomInt(10000000, 100000000), password: randomBytes(24).toString('base64url'),
    displayName: 'QA 快递发布验收', role: 'CUSTOMER',
  });
  token = auth.token;
  result.customerId = auth.user.id;
  for (const [serviceType, fee] of Object.entries(sizes)) {
    const body = { merchant: 'QA 快递发布验收', pickupCode: 'QA-123', pickupLocation: 'QA 驿站',
      destination: 'QA 宿舍', serviceType, couponId: null, useMembership: false };
    const key = randomUUID();
    const order = await request('/api/v1/orders', 'POST', body, key);
    pending.add(order.id);
    result.orderIds.push(order.id);
    assert.equal(order.serviceType, serviceType);
    assert.equal(order.feeCents, fee);
    assert.equal(order.status, 'PENDING_ASSIGNMENT');
    assert.equal(order.packageSizeConfirmed, false);
    const retry = await request('/api/v1/orders', 'POST', body, key);
    assert.equal(retry.id, order.id);
    const events = await request(`/api/v1/orders/${order.id}/events`);
    assert.equal(events.length, 1);
    result.checks.push(`${serviceType}: created with correct fee, duplicate retry creates no extra order/event`);
    const cancelled = await request(`/api/v1/orders/${order.id}/cancel`, 'POST');
    assert.equal(cancelled.status, 'CANCELLED');
    pending.delete(order.id);
    const retryAfterCancel = await request('/api/v1/orders', 'POST', body, key);
    assert.equal(retryAfterCancel.id, order.id);
    assert.equal(retryAfterCancel.status, 'CANCELLED');
    result.checks.push(`${serviceType}: cancellation and later retry preserve the same order`);
  }
  const mine = await request('/api/v1/orders/mine');
  assert.equal(mine.length, 3);
  assert.ok(mine.every(order => order.status === 'CANCELLED'));
  result.checks.push('Customer list contains exactly three cancelled QA orders');
  result.success = true;
}

main().catch(error => { result.error = error.message; process.exitCode = 1; }).finally(async () => {
  for (const id of pending) {
    try { await request(`/api/v1/orders/${id}/cancel`, 'POST'); pending.delete(id); }
    catch { result.cleanupError = 'Some QA orders could not be cancelled'; process.exitCode = 1; }
  }
  result.allQaOrdersCancelled = pending.size === 0;
  if (result.cleanupError) result.success = false;
  fs.writeFileSync(reportPath, JSON.stringify(result, null, 2));
  console.log(JSON.stringify({ success: result.success, checks: result.checks.length,
    requests: result.requests.length, allQaOrdersCancelled: result.allQaOrdersCancelled }));
});
