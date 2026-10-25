export function classifyWriteResponse(response, operation, phase, expected) {
  if (response.status === 429) {
    return { kind: phase === 'burst' && response.headers['Retry-After'] === '60'
      && response.headers['Cache-Control'] === 'no-store' ? 'limited' : 'unexpected' };
  }
  try {
    const body = response.json();
    if (response.status === 409 && operation === 'accept') {
      return { kind: body.message === '订单已被其他骑手接走' ? 'conflict' : 'unexpected' };
    }
    if (response.status !== 200 || !Number.isInteger(body.id) || body.id <= 0
      || !/^RP-\d{20}$/.test(body.orderNo) || body.serviceType !== 'PACKAGE_MEDIUM'
      || body.pickupCode !== expected.pickupCode || body.originalFeeCents !== 200
      || body.discountCents !== expected.discountCents || body.feeCents !== 200 - expected.discountCents
      || body.membershipUsed !== expected.membershipUsed
      || body.couponTitle !== expected.couponTitle) return { kind: 'unexpected' };
    if (operation === 'create' && (body.status !== 'PENDING_ASSIGNMENT' || body.courierName !== null)) {
      return { kind: 'unexpected' };
    }
    if (operation === 'accept' && (body.id !== expected.orderId || body.status !== 'ACCEPTED'
      || body.courierName !== expected.courierName)) return { kind: 'unexpected' };
    return { kind: 'success', orderId: body.id };
  } catch (_) {
    return { kind: 'unexpected' };
  }
}

export function validWritePair(outcomes, operation) {
  if (outcomes.length !== 2 || outcomes.some(o => o.kind === 'unexpected')) return false;
  const successes = outcomes.filter(o => o.kind === 'success');
  if (operation === 'create') {
    return outcomes.every(o => o.kind === 'success' || o.kind === 'limited')
      && (successes.length < 2 || successes[0].orderId === successes[1].orderId);
  }
  return successes.length === 1
    || (successes.length === 0 && outcomes.every(o => o.kind === 'limited'));
}
