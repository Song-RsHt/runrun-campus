import { classifyWriteResponse, validWritePair } from '../order-write-validation.js';

export const options = { vus: 1, iterations: 1 };
export default function () {
  let passed = 0;
  const assert = (value, name) => { if (!value) throw Error(name); passed++; };
  const expected = { pickupCode: '100', discountCents: 100, membershipUsed: false,
    couponTitle: '测试券', orderId: 7, courierName: '骑手一' };
  const body = { id: 7, orderNo: 'RP-00000000000000000007', serviceType: 'PACKAGE_MEDIUM',
    pickupCode: '100', originalFeeCents: 200, discountCents: 100, feeCents: 100,
    membershipUsed: false, couponTitle: '测试券', status: 'PENDING_ASSIGNMENT', courierName: null };
  const response = (status, data, headers = {}) => ({ status, headers, json: () => data });
  const classify = (r, operation = 'create', phase = 'steady') => classifyWriteResponse(r, operation, phase, expected).kind;
  assert(classify(response(200, body)) === 'success', 'valid creation rejected');
  assert(classify(response(200, { ...body, status: 'ACCEPTED', courierName: '骑手一' }), 'accept') === 'success', 'valid acceptance rejected');
  for (const [field, wrong] of Object.entries({ id: 0, orderNo: 'invalid', serviceType: 'LOBBY',
    pickupCode: '101', originalFeeCents: 100, discountCents: 0, feeCents: 200, membershipUsed: true,
    couponTitle: null, status: 'ACCEPTED', courierName: '骑手一' })) {
    assert(classify(response(200, { ...body, [field]: wrong })) === 'unexpected', `invalid ${field} accepted`);
  }
  for (const wrong of [{ id: 8 }, { status: 'PENDING_ASSIGNMENT' }, { courierName: '另一骑手' }]) {
    assert(classify(response(200, { ...body, status: 'ACCEPTED', courierName: '骑手一', ...wrong }), 'accept') === 'unexpected', 'wrong accepted order');
  }
  for (const status of [0, 400, 401, 403, 500, 503]) {
    assert(classify(response(status, body)) === 'unexpected', `unexpected HTTP ${status} accepted`);
  }
  assert(classify({ status: 200, headers: {}, json: () => { throw Error('not JSON'); } }) === 'unexpected', 'non-JSON accepted');
  assert(classify(response(409, { message: '订单已被其他骑手接走' }), 'accept') === 'conflict', 'expected race rejected');
  assert(classify(response(409, { message: '订单已被其他骑手接走' })) === 'unexpected', 'creation conflict accepted');
  assert(classify(response(409, { message: 'other conflict' }), 'accept') === 'unexpected', 'unrelated conflict accepted');
  const headers = { 'Retry-After': '60', 'Cache-Control': 'no-store' };
  assert(classify(response(429, null, headers), 'create', 'burst') === 'limited', 'burst limit rejected');
  for (const phase of ['warmup', 'steady', 'recovery']) {
    assert(classify(response(429, null, headers), 'create', phase) === 'unexpected', 'normal phase limit accepted');
  }
  assert(classify(response(429, null), 'create', 'burst') === 'unexpected', 'missing limit headers accepted');
  const success = { kind: 'success', orderId: 7 };
  const limited = { kind: 'limited' };
  const conflict = { kind: 'conflict' };
  const cases = [
    [[success, success], 'create', true],
    [[success, { kind: 'success', orderId: 8 }], 'create', false],
    [[success, limited], 'create', true],
    [[limited, limited], 'create', true],
    [[success, conflict], 'create', false],
    [[success, conflict], 'accept', true],
    [[success, limited], 'accept', true],
    [[success, success], 'accept', false],
    [[conflict, conflict], 'accept', false],
    [[limited, conflict], 'accept', false],
    [[limited, limited], 'accept', true],
    [[success, { kind: 'unexpected' }], 'accept', false],
    [[success], 'accept', false],
  ];
  for (const [outcomes, operation, valid] of cases) {
    assert(validWritePair(outcomes, operation) === valid, `incorrect ${operation} pair classification`);
  }
  console.log(`Write validation tests: ${passed} passed`);
}
