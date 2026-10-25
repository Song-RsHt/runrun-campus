import http from 'k6/http';
import execution from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import { classifyWriteResponse, validWritePair } from './order-write-validation.js';

const flows = new Counter('write_flows');
const requests = new Counter('probe_requests');
const successes = new Counter('probe_successes');
const limited = new Counter('probe_limited');
const conflicts = new Counter('write_conflicts');
const unexpected = new Rate('probe_unexpected');
const inconsistent = new Rate('write_inconsistent');
const latency = new Trend('probe_success_latency', true);
const created = new Counter('write_orders_created');
const accepted = new Counter('write_orders_accepted');
const couponOrders = new Counter('write_coupon_orders');
const membershipOrders = new Counter('write_membership_orders');
const phases = {
  warmup: { rate: 1, seconds: 10, start: 0, offset: 0 },
  steady: { rate: 2, seconds: 30, start: 12, offset: 100 },
  burst: { rate: 20, seconds: 30, start: 45, offset: 1000 },
  recovery: { rate: 2, seconds: 20, start: 140, offset: 2000 },
};
const scenarios = {};
const thresholds = { probe_unexpected: ['rate==0'], write_inconsistent: ['rate==0'], dropped_iterations: ['count==0'] };
for (const [phase, spec] of Object.entries(phases)) {
  scenarios[phase] = {
    executor: 'constant-arrival-rate', rate: spec.rate, timeUnit: '1s', duration: `${spec.seconds}s`,
    startTime: `${spec.start}s`, preAllocatedVUs: 10, maxVUs: 30, gracefulStop: '5s', tags: { phase },
  };
  thresholds[`write_flows{phase:${phase}}`] = [`count>=${spec.rate * spec.seconds}`];
  thresholds[`write_orders_created{phase:${phase}}`] = ['count>0'];
  thresholds[`write_orders_accepted{phase:${phase}}`] = ['count>0'];
  thresholds[`probe_limited{phase:${phase}}`] = [phase === 'burst' ? 'count>0' : 'count==0'];
  for (const operation of ['create', 'accept']) {
    thresholds[`probe_success_latency{phase:${phase},operation:${operation}}`] =
      [phase === 'burst' ? 'p(95)<1000' : 'p(95)<500'];
  }
}
export const options = { scenarios, thresholds, maxRedirects: 0,
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max', 'count'] };
http.setResponseCallback(http.expectedStatuses(200, 409, 429));

function sendPair(batch, operation, phase, expectations) {
  const responses = http.batch(batch);
  const outcomes = responses.map((response, index) => {
    const outcome = classifyWriteResponse(response, operation, phase, expectations[index]);
    const tags = { phase, operation };
    requests.add(1, tags);
    successes.add(outcome.kind === 'success' ? 1 : 0, tags);
    limited.add(outcome.kind === 'limited' ? 1 : 0, tags);
    conflicts.add(outcome.kind === 'conflict' ? 1 : 0, tags);
    unexpected.add(outcome.kind === 'unexpected', tags);
    if (outcome.kind === 'success') latency.add(response.timings.duration, tags);
    return outcome;
  });
  inconsistent.add(!validWritePair(outcomes, operation), { phase, operation });
  return outcomes;
}

export default function () {
  const phase = execution.scenario.name;
  const index = phases[phase].offset + execution.scenario.iterationInTest;
  const customer = index % 40;
  const coupon = index % 3 === 1;
  const membership = index % 3 === 2;
  const expected = { pickupCode: String(index), discountCents: membership ? 200 : coupon ? 100 : 0,
    membershipUsed: membership, couponTitle: coupon ? `写入压测券-${index}` : null };
  const payload = JSON.stringify({ merchant: '隔离写入验收', pickupCode: expected.pickupCode,
    pickupLocation: '测试驿站', destination: '测试宿舍', serviceType: 'PACKAGE_MEDIUM',
    couponId: coupon ? -100000-index : null, useMembership: membership });
  const createRequest = { method: 'POST', url: 'http://api:8080/api/v1/orders', body: payload,
    params: { timeout: '5s', headers: { Authorization: `Bearer write-probe-customer-${customer}`,
      'Content-Type': 'application/json', 'Idempotency-Key': `00000000-0000-4000-8000-${String(index).padStart(12, '0')}` },
      tags: { name: 'POST /orders', phase, operation: 'create' } } };
  flows.add(1, { phase });
  const creations = sendPair([createRequest, createRequest], 'create', phase, [expected, expected]);
  const order = creations.find(o => o.kind === 'success');
  created.add(order ? 1 : 0, { phase });
  couponOrders.add(order && coupon ? 1 : 0, { phase });
  membershipOrders.add(order && membership ? 1 : 0, { phase });
  if (!order) { accepted.add(0, { phase }); return; }
  const couriers = [customer, (customer + 1) % 40];
  const batch = couriers.map(courier => ({ method: 'POST',
    url: `http://api:8080/api/v1/orders/${order.orderId}/accept`, body: null,
    params: { timeout: '5s', headers: { Authorization: `Bearer write-probe-courier-${courier}` },
      tags: { name: 'POST /orders/:id/accept', phase, operation: 'accept' } } }));
  const outcomes = sendPair(batch, 'accept', phase, couriers.map(courier =>
    ({ ...expected, orderId: order.orderId, courierName: `写入测试骑手-${courier}` })));
  accepted.add(outcomes.filter(o => o.kind === 'success').length, { phase });
}

export function handleSummary(data) {
  return { '/results/k6-summary.json': JSON.stringify({ phases, metrics: data.metrics, state: data.state }, null, 2) };
}
