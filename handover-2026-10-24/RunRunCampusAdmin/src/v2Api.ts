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
  if (response.status === 204) return undefined as T
  try {
    return await response.json() as T
  } catch {
    throw new V2ApiError('服务端响应格式异常，请重试或重新登录', 502, 'INVALID_RESPONSE')
  }
}

export type V2AdminAccount = { id: string; role: 'ADMIN'; status: string; adminEnabled: boolean; securityVersion: number; courierEligible: boolean; permissions: string[] }
// 服务端权限缺失时拒绝建立会话，不能推断或补授权限。
export function validateV2AdminAccount(value: unknown): asserts value is V2AdminAccount {
  const account = value as Partial<V2AdminAccount> | null
  if (!account || typeof account.id !== 'string' || !/^\d+$/.test(account.id)
    || account.role !== 'ADMIN' || account.status !== 'ACTIVE' || account.adminEnabled !== true
    || !Number.isSafeInteger(account.securityVersion) || typeof account.courierEligible !== 'boolean'
    || !Array.isArray(account.permissions) || !account.permissions.every(item => typeof item === 'string')) {
    throw new V2ApiError('管理员会话信息异常或资格已停用，请重新登录；如持续出现请联系管理员', 401, 'INVALID_ADMIN_SESSION')
  }
}
export async function loginV2Admin(phone: string, password: string) {
  const result = await v2Request<{ token: string | null; account: V2AdminAccount | null }>('/api/v2/auth/password/login', undefined, { method: 'POST', body: JSON.stringify({ phone, password }) })
  try {
    validateV2AdminAccount(result?.account)
    if (typeof result.token !== 'string' || !result.token) throw new V2ApiError('登录响应异常，请重新登录', 401)
    return result
  } catch (reason) {
    if (result?.token) await logoutV2Admin(result.token).catch(() => undefined)
    throw reason
  }
}
export async function getV2AdminSession(token: string) {
  const account = await v2Request<unknown>('/api/v2/auth/session', token)
  validateV2AdminAccount(account)
  return account
}
export function logoutV2Admin(token: string, allSessions = false) { return v2Request<{ revoked: boolean }>('/api/v2/auth/logout', token, { method: 'POST', headers: { 'Idempotency-Key': `admin-v2-${crypto.randomUUID?.() ?? Date.now()}` }, body: JSON.stringify({ allSessions }) }) }
export function getV2Couriers(token: string, filters: { query: string; status: CourierCertificationStatus | ''; page: number; size: number }) { const params = new URLSearchParams({ page: String(filters.page), size: String(filters.size) }); if (filters.query) params.set('query', filters.query); if (filters.status) params.set('status', filters.status); return v2Request<AdminCourierPage>(`/api/v2/courier/certification/admin?${params}`, token) }
export function getV2CourierPerformance(token: string, courierId: number, page: number, size: number) { return v2Request<AdminCourierPerformance>(`/api/v2/courier/certification/admin/${courierId}/performance?page=${page}&size=${size}`, token) }
export function decideV2CourierCertification(token: string, id: number, input: { decision: 'APPROVE' | 'REJECT'; expectedVersion: number; photoResourceId: string | null; reason: string }, idempotencyKey = crypto.randomUUID?.() ?? `${Date.now()}-${id}`) { return v2Request(`/api/v2/courier/certification/${id}/decision`, token, { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: JSON.stringify(input) }) }
export async function getV2StudentCardPhoto(token: string, id: number, photoResourceId: string | null | undefined, signal: AbortSignal) { const resource = encodeURIComponent(photoResourceId ?? ''); const response = await fetch(`${API_BASE}/api/v2/courier/certification/${id}/student-card?photoResourceId=${resource}`, { headers: { Authorization: `Bearer ${token}`, Accept: 'image/jpeg, image/png' }, signal }); if (!response.ok) throw new V2ApiError(response.status === 401 ? '会话已失效，请重新登录' : '学生证照片读取失败', response.status); return response.blob() }
export async function getV2Notifications(token: string): Promise<V2Notification[]> {
  const value = await v2Request<unknown>('/api/v2/notifications', token)
  const validDate = (date: unknown) => typeof date === 'string' && date.trim() !== '' && Number.isFinite(Date.parse(date))
  // TypeScript 类型不能校验 HTTP 响应；异常数据交给页面错误态，避免日期渲染抛异常。
  if (!Array.isArray(value) || !value.every(item => item && Number.isSafeInteger(item.id) && item.id > 0
    && (item.orderId === null || (Number.isSafeInteger(item.orderId) && item.orderId > 0))
    && typeof item.type === 'string' && item.type.trim() !== ''
    && item.content && typeof item.content === 'object' && !Array.isArray(item.content)
    && validDate(item.createdAt) && (item.readAt === null || validDate(item.readAt)))) {
    throw new V2ApiError('站内消息响应异常，请刷新重试；如持续出现请联系管理员', 502, 'INVALID_NOTIFICATIONS')
  }
  return value as V2Notification[]
}
export function markV2NotificationRead(token: string, id: number) { return v2Request<{ id: number; read: boolean }>(`/api/v2/notifications/${id}/read`, token, { method: 'POST' }) }

