import { classifyResponse } from '../load-validation.js';

export const options = { vus: 1, iterations: 1 };
export default function () {
  const response = (status, body, headers = {}) => ({ status, headers, json: () => body });
  const list = { items: [], totalElements: 0 };
  const headers = { 'Retry-After': '60', 'Cache-Control': 'no-store' };
  const cases = [
    [response(200, list), '/orders', 'steady', 'success'],
    [response(200, { totalUsers: 45, totalOrders: 35 }), '/dashboard', 'steady', 'success'],
    [response(200, { message: 'error' }), '/orders', 'steady', 'unexpected'],
    [response(401, list), '/orders', 'steady', 'unexpected'],
    [response(500, list), '/orders', 'burst', 'unexpected'],
    [response(0, null), '/orders', 'burst', 'unexpected'],
    [response(429, null, headers), '/orders', 'burst', 'limited'],
    [response(429, null, headers), '/orders', 'steady', 'unexpected'],
    [response(429, null, headers), '/orders', 'recovery', 'unexpected'],
    [response(429, null), '/orders', 'burst', 'unexpected'],
    [{ status: 200, headers: {}, json: () => { throw Error('not JSON'); } }, '/orders', 'steady', 'unexpected'],
  ];
  cases.forEach(([r, path, phase, expected], index) => {
    if (classifyResponse(r, path, phase) !== expected) throw Error(`Classification case ${index + 1} failed`);
  });
  console.log(`Response classification tests: ${cases.length} passed`);
}
