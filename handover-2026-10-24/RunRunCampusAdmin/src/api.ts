import type {
  CommandQueue,
  CommandSnapshot,
  PlatformSettings,
  DispatchCandidates,
  DispatchResult,
  AdminAccountItem,
  AdminRole,
  AdminAuditAction,
  AdminAuditPage,
  AdminCourierPage,
  AdminCourierPerformance,
  AdminDashboard,
  AdminFeedbackItem,
  AdminFeedbackPage,
  AdminIssuePage,
  AdminOrderDetail,
  AdminOrderPage,
  AdminUserPage,
  AdminUserItem,
  CreditProfile,
  RiskSignalPage,
  RiskSignalStatus,
  AdminCouponGrant,
  MfaChallenge,
  MfaVerification,
  LoginSession,
  CourierCertificationStatus,
  OrderStatus,
  OrderIssueType,
  OrderIssueHandlingStatus,
  AdminIssueItem,
  FeedbackHandlingStatus,
  FeedbackType,
  UserRole,
  AuthUser,
  PushDeliveryOverview,
  PushDeliveryStatus,
} from './types'

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message)
  }
}

async function request<T>(path: string, token?: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  headers.set('Accept', 'application/json')
  if (init?.body) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`${API_BASE}${path}`, { ...init, headers })
  if (!response.ok) {
    const payload = await response.json().catch(() => null) as { message?: string } | null
    throw new ApiError(payload?.message ?? '请求失败，请稍后重试', response.status)
  }
  return response.json() as Promise<T>
}

export function login(phone: string, password: string): Promise<MfaChallenge> {
  return request('/api/v1/auth/admin/login', undefined, {
    method: 'POST',
    body: JSON.stringify({ phone, password }),
  })
}

export function verifyAdminMfa(challengeToken: string, code: string): Promise<MfaVerification> {
  return request('/api/v1/auth/admin/verify', undefined, {
    method: 'POST',
    body: JSON.stringify({ challengeToken, code, clientType: 'ADMIN_WEB', deviceName: '运营后台浏览器' }),
  })
}

export function getLoginSessions(token: string): Promise<LoginSession[]> {
  return request('/api/v1/auth/sessions', token)
}

export function getCurrentUser(token: string): Promise<AuthUser> {
  return request('/api/v1/auth/me', token)
}

export function getPushDeliveries(token: string, status: PushDeliveryStatus,
                                  page: number, size: number): Promise<PushDeliveryOverview> {
  return request(`/api/v1/admin/push-deliveries?status=${status}&page=${page}&size=${size}`, token)
}

export function revokeLoginSession(token: string, id: number): Promise<{ revoked: number }> {
  return request(`/api/v1/auth/sessions/${id}`, token, { method: 'DELETE' })
}

export function revokeOtherLoginSessions(token: string): Promise<{ revoked: number }> {
  return request('/api/v1/auth/sessions/others', token, { method: 'DELETE' })
}

export function logoutCurrentSession(token: string): Promise<{ revoked: number }> {
  return request('/api/v1/auth/logout', token, { method: 'POST' })
}

export function replaceAdminMfa(token: string, password: string, code: string): Promise<MfaChallenge> {
  return request('/api/v1/admin/accounts/me/mfa/replace', token, { method: 'POST', body: JSON.stringify({ password, code }) })
}

export function resetAdminMfa(token: string, id: number, reason: string): Promise<{ resetGrant: string }> {
  return request(`/api/v1/admin/accounts/${id}/mfa/reset`, token, { method: 'POST', body: JSON.stringify({ reason }) })
}

export function getDashboard(token: string): Promise<AdminDashboard> {
  return request('/api/v1/admin/dashboard', token)
}

export function getCommandCenter(token: string, queue: CommandQueue, orderPage: number,
  courierPage: number, signal: AbortSignal): Promise<CommandSnapshot> {
  const params = new URLSearchParams({ queue, orderPage: String(orderPage), courierPage: String(courierPage) })
  return request(`/api/v1/admin/command-center?${params}`, token, { signal })
}

export function getPlatformSettings(token: string): Promise<PlatformSettings> {
  return request('/api/v1/admin/platform-settings', token)
}

export function updatePlatformSettings(token: string, settings: PlatformSettings): Promise<PlatformSettings> {
  return request('/api/v1/admin/platform-settings', token, {
    method: 'PUT', body: JSON.stringify(settings),
  })
}

export function getDispatchCandidates(token: string, orderId: number, page: number): Promise<DispatchCandidates> {
  return request(`/api/v1/admin/orders/${orderId}/dispatch-candidates?page=${page}`, token)
}

export function dispatchOrder(token: string, orderId: number, courierId: number): Promise<DispatchResult> {
  return request(`/api/v1/admin/orders/${orderId}/dispatch`, token, {
    method: 'POST', body: JSON.stringify({ courierId }),
  })
}

export function getAdminAccounts(token: string): Promise<AdminAccountItem[]> {
  return request('/api/v1/admin/accounts', token)
}

export function createAdminAccount(
  token: string,
  input: { phone: string; password: string; displayName: string; adminRole: AdminRole },
): Promise<AdminAccountItem> {
  return request('/api/v1/admin/accounts', token, { method: 'POST', body: JSON.stringify(input) })
}

export function changeAdminRole(token: string, id: number, adminRole: AdminRole): Promise<AdminAccountItem> {
  return request(`/api/v1/admin/accounts/${id}/role`, token, {
    method: 'PUT',
    body: JSON.stringify({ adminRole }),
  })
}

export function suspendAdminAccount(token: string, id: number): Promise<AdminAccountItem> {
  return request(`/api/v1/admin/accounts/${id}/suspend`, token, { method: 'POST' })
}

export function restoreAdminAccount(token: string, id: number): Promise<AdminAccountItem> {
  return request(`/api/v1/admin/accounts/${id}/restore`, token, { method: 'POST' })
}

export function resetAdminPassword(token: string, id: number, password: string): Promise<AdminAccountItem> {
  return request(`/api/v1/admin/accounts/${id}/password`, token, {
    method: 'PUT',
    body: JSON.stringify({ password }),
  })
}

export function unlockAdminLogin(token: string, id: number): Promise<AdminAccountItem> {
  return request(`/api/v1/admin/accounts/${id}/unlock-login`, token, { method: 'POST' })
}

export function changeOwnAdminPassword(
  token: string,
  currentPassword: string,
  newPassword: string,
): Promise<{ changed: boolean }> {
  return request('/api/v1/admin/accounts/me/password', token, {
    method: 'PUT',
    body: JSON.stringify({ currentPassword, newPassword }),
  })
}

export function getOrders(
  token: string,
  filters: { query: string; status: OrderStatus | ''; archived: boolean; page: number; size: number },
): Promise<AdminOrderPage> {
  const params = new URLSearchParams({ page: String(filters.page), size: String(filters.size), archived: String(filters.archived) })
  if (filters.query) params.set('query', filters.query)
  if (filters.status) params.set('status', filters.status)
  return request(`/api/v1/admin/orders?${params}`, token)
}

export function getOrderDetail(token: string, id: number): Promise<AdminOrderDetail> {
  return request(`/api/v1/admin/orders/${id}`, token)
}

export async function getDeliveryProofPhoto(token: string, id: number, signal: AbortSignal): Promise<Blob> {
  const response = await fetch(`${API_BASE}/api/v1/admin/orders/${id}/delivery-proof`, {
    headers: { Authorization: `Bearer ${token}`, Accept: 'image/jpeg, image/png, image/webp' }, signal,
  })
  if (!response.ok) throw new ApiError('送达凭证读取失败', response.status)
  return response.blob()
}

export function confirmManualPayment(token: string, id: number): Promise<void> {
  return request(`/api/v1/admin/orders/${id}/manual-payment/confirm`, token, { method: 'POST' })
}

export function rejectManualPayment(token: string, id: number, reason: string): Promise<void> {
  return request(`/api/v1/admin/orders/${id}/manual-payment/reject`, token, {
    method: 'POST', body: JSON.stringify({ reason }),
  })
}

export function confirmCourierSettlement(token: string, id: number): Promise<void> {
  return request(`/api/v1/admin/orders/${id}/courier-settlement/confirm`, token, { method: 'POST' })
}

export function forceCompleteOrder(token: string, id: number, reason: string): Promise<{ orderId: number; action: string }> {
  return request(`/api/v1/admin/orders/${id}/force-complete`, token, {
    method: 'POST', body: JSON.stringify({ reason }),
  })
}

export function archiveOrder(token: string, id: number, reason: string): Promise<{ orderId: number; action: string }> {
  return request(`/api/v1/admin/orders/${id}/archive`, token, {
    method: 'POST', body: JSON.stringify({ reason }),
  })
}

export function unarchiveOrder(token: string, id: number): Promise<{ orderId: number; action: string }> {
  return request(`/api/v1/admin/orders/${id}/unarchive`, token, { method: 'POST' })
}

export async function exportOrders(
  token: string,
  filters: { query: string; status: OrderStatus | ''; archived: boolean },
): Promise<{ blob: Blob; truncated: boolean }> {
  const params = new URLSearchParams({ archived: String(filters.archived) })
  if (filters.query) params.set('query', filters.query)
  if (filters.status) params.set('status', filters.status)
  const response = await fetch(`${API_BASE}/api/v1/admin/orders/export?${params}`, {
    headers: { Accept: 'text/csv', Authorization: `Bearer ${token}` },
  })
  if (!response.ok) {
    const payload = await response.json().catch(() => null) as { message?: string } | null
    throw new ApiError(payload?.message ?? '订单导出失败，请稍后重试', response.status)
  }
  return { blob: await response.blob(), truncated: response.headers.get('X-Export-Truncated') === 'true' }
}

export function getCouriers(
  token: string,
  filters: { query: string; status: CourierCertificationStatus | ''; page: number; size: number },
): Promise<AdminCourierPage> {
  const params = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })
  if (filters.query) params.set('query', filters.query)
  if (filters.status) params.set('status', filters.status)
  return request(`/api/v1/admin/couriers?${params}`, token)
}

export function getCourierPerformance(
  token: string,
  courierId: number,
  page: number,
  size: number,
): Promise<AdminCourierPerformance> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  return request(`/api/v1/admin/couriers/${courierId}/performance?${params}`, token)
}

export function approveCourierCertification(token: string, id: number): Promise<void> {
  return request(`/api/v1/courier-certification/${id}/approve`, token, { method: 'POST' })
}

export async function getStudentCardPhoto(token: string, id: number, signal: AbortSignal): Promise<Blob> {
  const response = await fetch(`${API_BASE}/api/v1/courier-certification/${id}/student-card`, {
    headers: { Authorization: `Bearer ${token}`, Accept: 'image/jpeg, image/png' }, signal,
  })
  if (!response.ok) throw new ApiError('学生证照片读取失败', response.status)
  return response.blob()
}

export function rejectCourierCertification(token: string, id: number, reason: string): Promise<void> {
  return request(`/api/v1/courier-certification/${id}/reject`, token, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
}

export function getUsers(
  token: string,
  filters: { query: string; role: UserRole | ''; page: number; size: number },
): Promise<AdminUserPage> {
  const params = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })
  if (filters.query) params.set('query', filters.query)
  if (filters.role) params.set('role', filters.role)
  return request(`/api/v1/admin/users?${params}`, token)
}

export function suspendUser(token: string, id: number): Promise<AdminUserItem> {
  return request(`/api/v1/admin/users/${id}/suspend`, token, { method: 'POST' })
}

export function restoreUser(token: string, id: number): Promise<AdminUserItem> {
  return request(`/api/v1/admin/users/${id}/restore`, token, { method: 'POST' })
}

export function unlockUserLogin(token: string, id: number): Promise<AdminUserItem> {
  return request(`/api/v1/admin/users/${id}/unlock-login`, token, { method: 'POST' })
}

export function resetUserPassword(token: string, id: number, password: string): Promise<AdminUserItem> {
  return request(`/api/v1/admin/users/${id}/password`, token, {
    method: 'PUT',
    body: JSON.stringify({ password }),
  })
}

export function issueOneYuanCoupon(token: string, id: number): Promise<AdminCouponGrant> {
  return request(`/api/v1/admin/users/${id}/coupons/one-yuan`, token, { method: 'POST' })
}

export function getUserCredit(token: string, id: number): Promise<CreditProfile> {
  return request(`/api/v1/admin/users/${id}/credit?page=0&size=50`, token)
}

export function adjustUserCredit(token: string, id: number, delta: number, reason: string): Promise<unknown> {
  return request(`/api/v1/admin/users/${id}/credit/adjustments`, token, {
    method: 'POST',
    body: JSON.stringify({ delta, reason }),
  })
}

export function reverseCreditEvent(token: string, userId: number, eventId: number, reason: string): Promise<unknown> {
  return request(`/api/v1/admin/users/${userId}/credit/events/${eventId}/reversal`, token, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
}

export function getRiskSignals(
  token: string,
  filters: { status: RiskSignalStatus | ''; page: number; size: number },
): Promise<RiskSignalPage> {
  const params = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })
  if (filters.status) params.set('status', filters.status)
  return request(`/api/v1/admin/risk-signals?${params}`, token)
}

export function confirmRiskSignal(token: string, id: number, delta: number, reason: string): Promise<unknown> {
  return request(`/api/v1/admin/risk-signals/${id}/confirm`, token, {
    method: 'POST',
    body: JSON.stringify({ delta, reason }),
  })
}

export function dismissRiskSignal(token: string, id: number, reason: string): Promise<unknown> {
  return request(`/api/v1/admin/risk-signals/${id}/dismiss`, token, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
}

export function getIssues(
  token: string,
  filters: {
    query: string
    type: OrderIssueType | ''
    handlingStatus: OrderIssueHandlingStatus | ''
    overdueOnly: boolean
    page: number
    size: number
  },
): Promise<AdminIssuePage> {
  const params = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })
  if (filters.query) params.set('query', filters.query)
  if (filters.type) params.set('type', filters.type)
  if (filters.handlingStatus) params.set('handlingStatus', filters.handlingStatus)
  if (filters.overdueOnly) params.set('overdueOnly', 'true')
  return request(`/api/v1/admin/issues?${params}`, token)
}

export function claimIssue(token: string, id: number): Promise<AdminIssueItem> {
  return request(`/api/v1/admin/issues/${id}/claim`, token, { method: 'POST' })
}

export function resolveIssue(token: string, id: number, resolution: string): Promise<AdminIssueItem> {
  return request(`/api/v1/admin/issues/${id}/resolve`, token, {
    method: 'POST',
    body: JSON.stringify({ resolution }),
  })
}

export function getFeedback(
  token: string,
  filters: { query: string; type: FeedbackType | ''; handlingStatus: FeedbackHandlingStatus | ''; page: number; size: number },
): Promise<AdminFeedbackPage> {
  const params = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })
  if (filters.query) params.set('query', filters.query)
  if (filters.type) params.set('type', filters.type)
  if (filters.handlingStatus) params.set('handlingStatus', filters.handlingStatus)
  return request(`/api/v1/admin/feedback?${params}`, token)
}

export function claimFeedback(token: string, id: number): Promise<AdminFeedbackItem> {
  return request(`/api/v1/admin/feedback/${id}/claim`, token, { method: 'POST' })
}

export function resolveFeedback(token: string, id: number, resolution: string): Promise<AdminFeedbackItem> {
  return request(`/api/v1/admin/feedback/${id}/resolve`, token, {
    method: 'POST',
    body: JSON.stringify({ resolution }),
  })
}

export function getAuditLogs(
  token: string,
  filters: { query: string; action: AdminAuditAction | ''; page: number; size: number },
): Promise<AdminAuditPage> {
  const params = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })
  if (filters.query) params.set('query', filters.query)
  if (filters.action) params.set('action', filters.action)
  return request(`/api/v1/admin/audit-logs?${params}`, token)
}
