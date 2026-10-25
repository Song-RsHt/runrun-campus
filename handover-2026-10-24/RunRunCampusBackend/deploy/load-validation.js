export function classifyResponse(response, path, phase) {
  if (response.status === 429) {
    return phase === 'burst' && response.headers['Retry-After'] === '60'
      && response.headers['Cache-Control'] === 'no-store' ? 'limited' : 'unexpected';
  }
  if (response.status !== 200) return 'unexpected';
  try {
    const body = response.json();
    const valid = path.endsWith('/dashboard')
      ? typeof body.totalUsers === 'number' && typeof body.totalOrders === 'number'
      : Array.isArray(body.items) && typeof body.totalElements === 'number';
    return valid ? 'success' : 'unexpected';
  } catch (_) {
    return 'unexpected';
  }
}
