import type { AdminCourierPage, AdminCourierPerformance, CourierCertificationStatus, V2Notification } from './types'

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
export class V2ApiError extends Error { constructor(message: string, public readonly status: number, public readonly code?: string) { super(message) } }

async function v2Request<T>(path: string, token?: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  headers.set('Accept', 'application/json')
  if (init?.body) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${API_BASE}${path}`, { ...init, headers })
  if (!response.ok) {
    const payload = await response.json().catch(() => null) as { message?: string; code?: string } | null
    throw new V2ApiError(payload?.message ?? (response.status === 401 ? '会话已失效，请重新登录' : response.status === 403 ? '当前账号无权执行此操作' : '请求失败，请稍后重试'), response.status, payload?.code)
  }
  return response.status === 204 ? undefined as T : await response.json() as T
}

export type V2AdminAccount = { id: string; role: 'ADMIN'; status: string; adminEnabled: boolean; securityVersion: number; courierEligible: boolean; permissions: string[] }
export function loginV2Admin(phone: string, password: string) { return v2Request<{ token: string | null; account: V2AdminAccount | null }>('/api/v2/auth/password/login', undefined, { method: 'POST', body: JSON.stringify({ phone, password }) }) }
export function getV2AdminSession(token: string) { return v2Request<V2AdminAccount>('/api/v2/auth/session', token) }
export function logoutV2Admin(token: string, allSessions = false) { return v2Request<{ revoked: boolean }>('/api/v2/auth/logout', token, { method: 'POST', headers: { 'Idempotency-Key': `admin-v2-${crypto.randomUUID?.() ?? Date.now()}` }, body: JSON.stringify({ allSessions }) }) }
export function getV2Couriers(token: string, filters: { query: string; status: CourierCertificationStatus | ''; page: number; size: number }) { const params = new URLSearchParams({ page: String(filters.page), size: String(filters.size) }); if (filters.query) params.set('query', filters.query); if (filters.status) params.set('status', filters.status); return v2Request<AdminCourierPage>(`/api/v2/courier/certification/admin?${params}`, token) }
export function getV2CourierPerformance(token: string, courierId: number, page: number, size: number) { return v2Request<AdminCourierPerformance>(`/api/v2/courier/certification/admin/${courierId}/performance?page=${page}&size=${size}`, token) }
export function decideV2CourierCertification(token: string, id: number, input: { decision: 'APPROVE' | 'REJECT'; expectedVersion: number; photoResourceId: string | null; reason: string }, idempotencyKey = crypto.randomUUID?.() ?? `${Date.now()}-${id}`) { return v2Request(`/api/v2/courier/certification/${id}/decision`, token, { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: JSON.stringify(input) }) }
export async function getV2StudentCardPhoto(token: string, id: number, photoResourceId: string | null | undefined, signal: AbortSignal) { const resource = encodeURIComponent(photoResourceId ?? ''); const response = await fetch(`${API_BASE}/api/v2/courier/certification/${id}/student-card?photoResourceId=${resource}`, { headers: { Authorization: `Bearer ${token}`, Accept: 'image/jpeg, image/png' }, signal }); if (!response.ok) throw new V2ApiError(response.status === 401 ? '会话已失效，请重新登录' : '学生证照片读取失败', response.status); return response.blob() }
export function getV2Notifications(token: string) { return v2Request<V2Notification[]>('/api/v2/notifications', token) }
export function markV2NotificationRead(token: string, id: number) { return v2Request<{ id: number; read: boolean }>(`/api/v2/notifications/${id}/read`, token, { method: 'POST' }) }

