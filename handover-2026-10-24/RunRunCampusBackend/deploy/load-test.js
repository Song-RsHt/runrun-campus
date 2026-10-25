import http from 'k6/http';
import execution from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import { classifyResponse } from './load-validation.js';

const requests = new Counter('probe_requests');
const successes = new Counter('probe_successes');
const limited = new Counter('probe_limited');
const unexpected = new Rate('probe_unexpected');
const latency = new Trend('probe_success_latency', true);
const phases = {
  warmup: { rate: 5, seconds: 10, start: 0 },
  steady: { rate: 15, seconds: 30, start: 12 },
  burst: { rate: 80, seconds: 30, start: 45 },
  recovery: { rate: 10, seconds: 20, start: 80 },
};
const scenarios = {};
const thresholds = { probe_unexpected: ['rate==0'], dropped_iterations: ['count==0'] };
for (const [phase, spec] of Object.entries(phases)) {
  scenarios[phase] = {
    executor: 'constant-arrival-rate', rate: spec.rate, timeUnit: '1s', duration: `${spec.seconds}s`,
    startTime: `${spec.start}s`, preAllocatedVUs: 20, maxVUs: 40, gracefulStop: '5s', tags: { phase },
  };
  thresholds[`probe_requests{phase:${phase}}`] = [`count>=${spec.rate * spec.seconds}`];
  thresholds[`probe_successes{phase:${phase}}`] = ['count>0'];
  thresholds[`probe_limited{phase:${phase}}`] = [phase === 'burst' ? 'count>0' : 'count==0'];
  if (phase !== 'warmup') thresholds[`probe_success_latency{phase:${phase}}`] = [phase === 'burst' ? 'p(95)<1000' : 'p(95)<500'];
}
export const options = {
  scenarios, thresholds, maxRedirects: 0,
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max', 'count'],
};
http.setResponseCallback(http.expectedStatuses(200, 429));
const paths = ['/api/v1/admin/dashboard', '/api/v1/admin/orders?size=20',
  '/api/v1/admin/couriers?size=20', '/api/v1/admin/users?size=20'];

export default function () {
  const iteration = execution.scenario.iterationInTest;
  const phase = execution.scenario.name;
  const path = paths[iteration % paths.length];
  // These synthetic sessions exist only in the isolated database copy.
  const response = http.get(`http://api:8080${path}`, {
    timeout: '5s', headers: { Authorization: `Bearer load-probe-session-${iteration % 20}` },
    tags: { phase, endpoint: path.split('?')[0] },
  });
  const outcome = classifyResponse(response, path, phase);
  const tags = { phase };
  requests.add(1, tags);
  successes.add(outcome === 'success' ? 1 : 0, tags);
  limited.add(outcome === 'limited' ? 1 : 0, tags);
  unexpected.add(outcome === 'unexpected', tags);
  if (outcome === 'success') latency.add(response.timings.duration, tags);
}

export function handleSummary(data) {
  return { '/results/k6-summary.json': JSON.stringify({ phases, metrics: data.metrics, state: data.state }, null, 2) };
}
