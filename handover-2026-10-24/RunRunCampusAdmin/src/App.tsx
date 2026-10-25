import { useEffect, useMemo, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { AdminMfaChallenge, AdminMfaSettings } from './AdminMfa'
import { CommandCenter } from './CommandCenter'
import {
  ApiError,
  approveCourierCertification,
  changeAdminRole,
  changeOwnAdminPassword,
  claimIssue,
  claimFeedback,
  createAdminAccount,
  exportOrders,
  getAuditLogs,
  getAdminAccounts,
  getCourierPerformance,
  getStudentCardPhoto,
  getCouriers,
  getCurrentUser,
  getPushDeliveries,
  getDashboard,
  getFeedback,
  getIssues,
  getLoginSessions,
  getOrderDetail,
  getDeliveryProofPhoto,
  getOrders,
  getUsers,
  getUserCredit,
  adjustUserCredit,
  reverseCreditEvent,
  getRiskSignals,
  confirmRiskSignal,
  confirmManualPayment,
  rejectManualPayment,
  confirmCourierSettlement,
  forceCompleteOrder,
  archiveOrder,
  unarchiveOrder,
  dismissRiskSignal,
  login,
  issueOneYuanCoupon,
  logoutCurrentSession,
  rejectCourierCertification,
  restoreUser,
  resolveIssue,
  resolveFeedback,
  restoreAdminAccount,
  resetAdminPassword,
  resetUserPassword,
  resetAdminMfa,
  revokeLoginSession,
  revokeOtherLoginSessions,
  suspendUser,
  suspendAdminAccount,
  unlockAdminLogin,
  unlockUserLogin,
} from './api'
import type {
  AdminAccountItem,
  AdminAuditAction,
  AdminPermission,
  AdminRole,
  AdminAuditPage,
  AdminCourierItem,
  AdminCourierPage,
  AdminCourierPerformance,
  AdminDashboard,
  AdminFeedbackItem,
  AdminFeedbackPage,
  AdminIssueItem,
  AdminIssuePage,
  AdminOrderDetail,
  AdminOrderPage,
  AdminUserItem,
  AdminUserPage,
  CreditProfile,
  RiskSignalItem,
  RiskSignalPage,
  RiskSignalStatus,
  AuthResponse,
  MfaChallenge,
  LoginSession,
  CourierCertificationStatus,
  OrderStatus,
  OrderIssueType,
  OrderIssueHandlingStatus,
  ServiceType,
  UserRole,
  FeedbackHandlingStatus,
  FeedbackType,
  PushDeliveryOverview,
  PushDeliveryStatus,
} from './types'

const SESSION_KEY = 'runrun-campus-admin-session'
const PAGE_SIZE = 20

const statusLabels: Record<OrderStatus, string> = {
  PENDING_ASSIGNMENT: '待接单',
  PENDING_REASSIGNMENT: '待运营改派',
  ACCEPTED: '已接单',
  PICKED_UP: '配送中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

const serviceLabels: Record<ServiceType, string> = {
  LOBBY: '楼下取餐',
  DOOR: '送到门口',
  EXPRESS: '夜间急送',
  PACKAGE_SMALL: '快递代取·小件',
  PACKAGE_MEDIUM: '快递代取·中件',
  PACKAGE_MEDIUM_LARGE: '快递代取·中大件',
  PACKAGE_LARGE: '快递代取·大件',
  PACKAGE_EXTRA_LARGE: '快递代取·特大件',
  PACKAGE_XL: '快递代取·超大件',
}

const certificationStatusLabels: Record<CourierCertificationStatus, string> = {
  NOT_APPLIED: '未申请',
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}

const roleLabels: Record<UserRole, string> = {
  CUSTOMER: '普通用户',
  COURIER: '配送骑手',
  ADMIN: '平台管理员',
}

const creditRiskLabels = { NORMAL: '正常', WATCH: '关注', HIGH_RISK: '高风险' } as const
const creditEventLabels = { ADMIN_ADJUSTMENT: '人工调整', RISK_CONFIRMED: '风险核实', REVERSAL: '撤销更正' } as const
const riskSignalTypeLabels = {
  FREQUENT_CANCELLATION: '频繁取消',
  REPEATED_ISSUES: '重复异常',
  ORDER_TIMEOUT: '订单超时',
} as const
const riskSignalStatusLabels: Record<RiskSignalStatus, string> = {
  PENDING: '待审核', CONFIRMED: '已确认', DISMISSED: '已驳回',
}

const issueTypeLabels: Record<OrderIssueType, string> = {
  MISSING_ITEM: '餐品缺失',
  WRONG_ORDER: '餐品错误',
  CANNOT_CONTACT: '联系不上',
  DELIVERY_DELAY: '配送延迟',
  OTHER: '其他问题',
}

const issueHandlingStatusLabels: Record<OrderIssueHandlingStatus, string> = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  RESOLVED: '已解决',
}

const feedbackTypeLabels: Record<FeedbackType, string> = {
  FUNCTION: '功能建议',
  ACCOUNT: '账号问题',
  OTHER: '其他反馈',
}

const auditActionLabels: Record<AdminAuditAction, string> = {
  COURIER_CERTIFICATION_APPROVED: '通过骑手认证',
  COURIER_CERTIFICATION_REJECTED: '驳回骑手认证',
  USER_SUSPENDED: '停用用户账号',
  USER_RESTORED: '恢复用户账号',
  ORDER_ISSUE_CLAIMED: '领取异常工单',
  ORDER_ISSUE_RESOLVED: '解决异常工单',
  FEEDBACK_CLAIMED: '领取用户反馈',
  FEEDBACK_RESOLVED: '解决用户反馈',
  ADMIN_CREATED: '创建管理员',
  ADMIN_ROLE_CHANGED: '调整管理员岗位',
  ADMIN_SUSPENDED: '停用管理员账号',
  ADMIN_RESTORED: '恢复管理员账号',
  ADMIN_PASSWORD_RESET: '重置管理员密码',
  ADMIN_PASSWORD_CHANGED: '管理员修改密码',
  USER_PASSWORD_RESET: '重置用户密码',
  USER_LOGIN_UNLOCKED: '解除用户登录锁定',
  ADMIN_LOGIN_UNLOCKED: '解除管理员登录锁定',
  ADMIN_MFA_LOGIN_FAILED: '双因素认证失败',
  ADMIN_MFA_LOGIN_SUCCEEDED: '双因素登录成功',
  ADMIN_MFA_ENROLLED: '绑定认证器',
  ADMIN_MFA_REPLACED: '更换认证器',
  ADMIN_MFA_RECOVERY_USED: '使用恢复码',
  ADMIN_MFA_RESET: '重置认证器',
  ORDER_DISPATCHED: '人工调度派单',
  ORDER_REASSIGNED: '人工确认改派',
  ORDER_FORCE_COMPLETED: '管理员人工完成订单',
  ORDER_ARCHIVED: '归档订单',
  ORDER_UNARCHIVED: '恢复归档订单',
  PLATFORM_SETTINGS_UPDATED: '调整运营设置',
  COUPON_ISSUED: '发放1元优惠券',
  CREDIT_ADJUSTED: '人工调整信用分',
  CREDIT_EVENT_REVERSED: '撤销信用记录',
  RISK_SIGNAL_CONFIRMED: '确认风险信号',
  RISK_SIGNAL_DISMISSED: '驳回风险信号',
  MANUAL_PAYMENT_CONFIRMED: '确认人工扫码到账',
  MANUAL_PAYMENT_REJECTED: '驳回付款申报',
  COURIER_SETTLEMENT_CONFIRMED: '登记骑手结算',
}

const auditTargetLabels: Record<AdminAuditPage['items'][number]['targetType'], string> = {
  COURIER_CERTIFICATION: '骑手认证',
  USER: '用户账号',
  ORDER_ISSUE: '异常工单',
  FEEDBACK: '意见反馈',
  ADMIN_ACCOUNT: '管理员账号',
  ORDER: '订单',
  PLATFORM_SETTINGS: '运营设置',
  CREDIT_EVENT: '信用记录',
  RISK_SIGNAL: '风险信号',
}

const adminRoleLabels: Record<AdminRole, string> = {
  SUPER_ADMIN: '超级管理员',
  OPERATIONS: '运营管理员',
  SUPPORT: '客服管理员',
  REVIEWER: '认证审核员',
  AUDITOR: '审计查看员',
}

function readSession(): AuthResponse | null {
  try {
    const value = sessionStorage.getItem(SESSION_KEY)
    if (!value) return null
    const session = JSON.parse(value) as AuthResponse
    return session.user.role === 'ADMIN' && Array.isArray(session.user.permissions) ? session : null
  } catch {
    return null
  }
}

export default function App() {
  const [session, setSession] = useState<AuthResponse | null>(readSession)

  const handleLogin = (value: AuthResponse) => {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(value))
    setSession(value)
  }

  const handleLogout = () => {
    sessionStorage.removeItem(SESSION_KEY)
    setSession(null)
  }

  useEffect(() => {
    const token = session?.token
    if (!token) return
    let active = true
    let checking = false
    const refresh = async () => {
      if (checking) return
      checking = true
      try {
        const user = await getCurrentUser(token)
        if (!active || readSession()?.token !== token) return
        if (user.role !== 'ADMIN') { handleLogout(); return }
        if (JSON.stringify(readSession()?.user) !== JSON.stringify(user)) {
          const updated = { token, user }
          sessionStorage.setItem(SESSION_KEY, JSON.stringify(updated))
          setSession(current => current?.token === token ? updated : current)
        }
      } catch (reason) {
        if (active && readSession()?.token === token && reason instanceof ApiError &&
          (reason.status === 401 || reason.status === 403)) handleLogout()
      } finally {
        checking = false
      }
    }
    const onVisible = () => { if (!document.hidden) void refresh() }
    void refresh()
    window.addEventListener('focus', onVisible)
    document.addEventListener('visibilitychange', onVisible)
    const timer = window.setInterval(onVisible, 60_000)
    return () => {
      active = false
      window.removeEventListener('focus', onVisible)
      document.removeEventListener('visibilitychange', onVisible)
      window.clearInterval(timer)
    }
  }, [session?.token])

  return session
    ? <AdminConsole session={session} onLogout={handleLogout} onSessionChange={handleLogin} />
    : <LoginPage onLogin={handleLogin} />
}

function LoginPage({ onLogin }: { onLogin: (session: AuthResponse) => void }) {
  const [challenge, setChallenge] = useState<MfaChallenge | null>(null)
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      setChallenge(await login(phone.trim(), password))
      setPassword('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '登录失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-brand" aria-label="跑跑代取运营后台介绍">
        <div className="brand-mark">跑</div>
        <p className="eyebrow">RUNRUN CAMPUS</p>
        <h1>把每一单，<br />都管理得清清楚楚。</h1>
        <p className="brand-copy">订单、骑手与履约证据统一管理，为校园配送提供可追踪的运营入口。</p>
        <div className="brand-stats">
          <span><strong>01</strong> 订单总览</span>
          <span><strong>02</strong> 履约跟踪</span>
          <span><strong>03</strong> 异常核查</span>
        </div>
      </section>

      <section className="login-panel">
        {challenge ? <AdminMfaChallenge challenge={challenge} onComplete={onLogin} onCancel={() => setChallenge(null)} /> : <form className="login-card" onSubmit={submit}>
          <div>
            <p className="eyebrow accent">运营工作台</p>
            <h2>管理员登录</h2>
            <p className="muted">使用管理员密码登录，然后完成认证器验证。</p>
          </div>
          <label>
            手机号
            <input
              autoComplete="username"
              inputMode="numeric"
              maxLength={11}
              pattern="1[0-9]{10}"
              placeholder="请输入 11 位手机号"
              required
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
            />
          </label>
          <label>
            密码
            <input
              autoComplete="current-password"
              minLength={6}
              placeholder="请输入密码"
              required
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="primary-button login-button" disabled={submitting} type="submit">
            {submitting ? '正在验证…' : '登录运营后台'}
          </button>
          <p className="security-note">仅管理员可访问。请勿在公共设备保存或共享账号。</p>
        </form>}
      </section>
    </main>
  )
}

type AdminView = 'dashboard' | 'orders' | 'couriers' | 'users' | 'risk' | 'issues' | 'feedback' | 'accounts' | 'push' | 'audit'

const viewPermissions: Record<AdminView, AdminPermission> = {
  dashboard: 'DASHBOARD_VIEW',
  orders: 'ORDER_VIEW',
  couriers: 'COURIER_VIEW',
  users: 'USER_VIEW',
  risk: 'USER_VIEW',
  issues: 'ISSUE_VIEW',
  feedback: 'FEEDBACK_VIEW',
  accounts: 'ADMIN_MANAGE',
  push: 'PUSH_DELIVERY_VIEW',
  audit: 'AUDIT_VIEW',
}

function hasPermission(session: AuthResponse, permission: AdminPermission) {
  return session.user.permissions.includes(permission)
}

function AdminConsole({ session, onLogout, onSessionChange }: { session: AuthResponse; onLogout: () => void; onSessionChange: (session: AuthResponse) => void }) {
  const [mfaSettingsOpen, setMfaSettingsOpen] = useState(false)
  const [sessionsOpen, setSessionsOpen] = useState(false)
  const [activeView, setActiveView] = useState<AdminView>('dashboard')
  const [dashboardFilter, setDashboardFilter] = useState('')
  const [draftQuery, setDraftQuery] = useState('')
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<OrderStatus | ''>('')
  const [archived, setArchived] = useState(false)
  const [page, setPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)
  const [orders, setOrders] = useState<AdminOrderPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [detail, setDetail] = useState<AdminOrderDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [exporting, setExporting] = useState(false)
  const [exportMessage, setExportMessage] = useState('')
  const [passwordDialogOpen, setPasswordDialogOpen] = useState(false)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newOwnPassword, setNewOwnPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordChanging, setPasswordChanging] = useState(false)
  const [ownPasswordError, setOwnPasswordError] = useState('')

  useEffect(() => {
    if (!hasPermission(session, viewPermissions[activeView])) setActiveView('dashboard')
  }, [activeView, session.user.permissions])

  useEffect(() => {
    if (!hasPermission(session, 'ORDER_VIEW')) setSelectedId(null)
  }, [session.user.permissions])

  const visibleView = hasPermission(session, viewPermissions[activeView]) ? activeView : 'dashboard'

  const expireSession = (reason: unknown) => {
    if (reason instanceof ApiError && reason.status === 401) {
      onLogout()
      return true
    }
    return false
  }

  useEffect(() => {
    if (activeView !== 'orders') return
    let active = true
    setLoading(true)
    setError('')
    getOrders(session.token, { query, status, archived, page, size: PAGE_SIZE })
      .then((result) => active && setOrders(result))
      .catch((reason: unknown) => {
        if (!active || expireSession(reason)) return
        setError(reason instanceof Error ? reason.message : '订单加载失败')
      })
      .finally(() => active && setLoading(false))
    return () => { active = false }
  }, [activeView, archived, page, query, reloadKey, session.token, status])

  useEffect(() => {
    if (selectedId === null) {
      setDetail(null)
      return
    }
    let active = true
    setDetailLoading(true)
    setDetailError('')
    getOrderDetail(session.token, selectedId)
      .then((result) => active && setDetail(result))
      .catch((reason: unknown) => {
        if (!active || expireSession(reason)) return
        setDetailError(reason instanceof Error ? reason.message : '订单详情加载失败')
      })
      .finally(() => active && setDetailLoading(false))
    return () => { active = false }
  }, [selectedId, session.token])

  const pageSummary = useMemo(() => {
    if (!orders || orders.totalElements === 0) return '共 0 条订单'
    const start = orders.page * orders.size + 1
    const end = Math.min(start + orders.items.length - 1, orders.totalElements)
    return `第 ${start}–${end} 条，共 ${orders.totalElements} 条`
  }, [orders])

  const search = (event: FormEvent) => {
    event.preventDefault()
    setPage(0)
    setQuery(draftQuery.trim())
  }

  const downloadOrders = async () => {
    setExporting(true)
    setExportMessage('')
    try {
      const result = await exportOrders(session.token, { query, status, archived })
      const url = URL.createObjectURL(result.blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `跑跑代取${archived ? '归档' : '未归档'}订单-${new Date().toISOString().slice(0, 10)}.csv`
      link.click()
      URL.revokeObjectURL(url)
      setExportMessage(result.truncated ? '已导出前 10,000 条，结果因上限截断' : '订单导出完成')
    } catch (reason) {
      if (expireSession(reason)) return
      setExportMessage(reason instanceof Error ? reason.message : '订单导出失败')
    } finally {
      setExporting(false)
    }
  }

  const submitOwnPassword = async (event: FormEvent) => {
    event.preventDefault()
    setOwnPasswordError('')
    if (newOwnPassword !== confirmPassword) {
      setOwnPasswordError('两次输入的新密码不一致')
      return
    }
    setPasswordChanging(true)
    try {
      await changeOwnAdminPassword(session.token, currentPassword, newOwnPassword)
      window.alert('密码已修改，请使用新密码重新登录。')
      onLogout()
    } catch (reason) {
      if (expireSession(reason)) return
      setOwnPasswordError(reason instanceof Error ? reason.message : '密码修改失败')
    } finally {
      setPasswordChanging(false)
    }
  }

  return (
    <div className="admin-layout">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <span className="brand-mark small">跑</span>
          <span><strong>跑跑代取</strong><small>运营后台</small></span>
        </div>
        <nav aria-label="后台功能" onClickCapture={() => setDashboardFilter('')}>
          <button className={`nav-item ${activeView === 'dashboard' ? 'active' : ''}`} onClick={() => setActiveView('dashboard')}><span>总</span>运营指挥台</button>
          {hasPermission(session, 'ORDER_VIEW') && <button className={`nav-item ${activeView === 'orders' ? 'active' : ''}`} onClick={() => setActiveView('orders')}><span>订</span>订单管理</button>}
          {hasPermission(session, 'COURIER_VIEW') && <button className={`nav-item ${activeView === 'couriers' ? 'active' : ''}`} onClick={() => setActiveView('couriers')}><span>骑</span>骑手管理</button>}
          {hasPermission(session, 'USER_VIEW') && <button className={`nav-item ${activeView === 'users' ? 'active' : ''}`} onClick={() => setActiveView('users')}><span>用</span>用户管理</button>}
          {hasPermission(session, 'USER_VIEW') && <button className={`nav-item ${activeView === 'risk' ? 'active' : ''}`} onClick={() => setActiveView('risk')}><span>风</span>风险审核</button>}
          {hasPermission(session, 'ISSUE_VIEW') && <button className={`nav-item ${activeView === 'issues' ? 'active' : ''}`} onClick={() => setActiveView('issues')}><span>异</span>异常工单</button>}
          {hasPermission(session, 'FEEDBACK_VIEW') && <button className={`nav-item ${activeView === 'feedback' ? 'active' : ''}`} onClick={() => setActiveView('feedback')}><span>馈</span>意见反馈</button>}
          {hasPermission(session, 'ADMIN_MANAGE') && <button className={`nav-item ${activeView === 'accounts' ? 'active' : ''}`} onClick={() => setActiveView('accounts')}><span>管</span>管理员账号</button>}
          {hasPermission(session, 'PUSH_DELIVERY_VIEW') && <button className={`nav-item ${activeView === 'push' ? 'active' : ''}`} onClick={() => setActiveView('push')}><span>推</span>推送告警</button>}
          {hasPermission(session, 'AUDIT_VIEW') && <button className={`nav-item ${activeView === 'audit' ? 'active' : ''}`} onClick={() => setActiveView('audit')}><span>审</span>操作日志</button>}
        </nav>
        <div className="sidebar-user">
          <span className="avatar">{session.user.displayName.slice(0, 1)}</span>
          <span><strong>{session.user.displayName}</strong><small>{session.user.adminRole ? adminRoleLabels[session.user.adminRole] : maskPhone(session.user.phone)}</small></span>
          <span className="sidebar-account-actions"><button onClick={() => {
            setCurrentPassword('')
            setNewOwnPassword('')
            setConfirmPassword('')
            setOwnPasswordError('')
            setPasswordDialogOpen(true)
          }} title="修改密码">改密</button><button onClick={() => setMfaSettingsOpen(true)} title="认证器设置">认证器</button><button onClick={() => setSessionsOpen(true)} title="登录设备">设备</button><button onClick={() => { void logoutCurrentSession(session.token).finally(onLogout) }} title="退出登录">退出</button></span>
        </div>
      </aside>

      {visibleView === 'dashboard'
        ? <DashboardManagement session={session} onNavigate={(view, filter = '') => {
            setDashboardFilter(filter)
            if (view === 'orders') {
              setStatus(filter as OrderStatus | '')
              setArchived(false)
              setQuery('')
              setDraftQuery('')
              setPage(0)
            }
            setActiveView(view)
          }} onOpenOrder={setSelectedId} onSessionExpired={onLogout} />
        : visibleView === 'orders' ? <main className="content">
        <header className="page-header">
          <div>
            <p className="eyebrow accent">ORDER OPERATIONS</p>
            <h1>订单管理</h1>
            <p className="muted">检索订单并核对用户、骑手、状态记录和履约证据。归档仅隐藏管理列表，不删除订单。</p>
          </div>
          <div className="header-actions">
            <span className="action-message">{exportMessage}</span>
            {hasPermission(session, 'ORDER_EXPORT') && <button className="secondary-button" disabled={exporting} onClick={downloadOrders}>{exporting ? '正在导出…' : '导出 CSV'}</button>}
            <button className="secondary-button" onClick={() => setReloadKey((value) => value + 1)}>刷新数据</button>
          </div>
        </header>

        <section className="summary-row" aria-label="订单状态摘要">
          <article><span>当前结果</span><strong>{orders?.totalElements ?? '—'}</strong><small>笔订单</small></article>
          <article><span>筛选状态</span><strong className="text-value">{status ? statusLabels[status] : '全部订单'}</strong><small>可随时切换</small></article>
          <article><span>当前视图</span><strong className="text-value">{archived ? '已归档' : '未归档'}</strong><small>不影响经营统计</small></article>
        </section>

        <section className="orders-card">
          <form className="filters order-filters" onSubmit={search}>
            <label className="search-field">
              <span>搜索</span>
              <input
                placeholder="订单号、手机号、昵称、商家或送达位置"
                value={draftQuery}
                onChange={(event) => setDraftQuery(event.target.value)}
              />
            </label>
            <label>
              <span>订单状态</span>
              <select
                value={status}
                onChange={(event) => {
                  setPage(0)
                  setStatus(event.target.value as OrderStatus | '')
                }}
              >
                <option value="">全部状态</option>
                {Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </label>
            <label>
              <span>归档状态</span>
              <select value={archived ? 'archived' : 'active'} onChange={(event) => {
                setPage(0)
                setArchived(event.target.value === 'archived')
              }}>
                <option value="active">未归档</option>
                <option value="archived">已归档</option>
              </select>
            </label>
            <button className="primary-button filter-button" type="submit">查询订单</button>
          </form>

          {error && <ErrorState message={error} onRetry={() => setReloadKey((value) => value + 1)} />}
          {!error && loading && <div className="table-state">正在加载订单…</div>}
          {!error && !loading && orders?.items.length === 0 && <div className="table-state">没有符合条件的订单</div>}
          {!error && !loading && orders && orders.items.length > 0 && (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr><th>订单</th><th>状态</th><th>用户</th><th>骑手</th><th>送达位置</th><th>付款</th><th>创建时间</th><th></th></tr>
                </thead>
                <tbody>
                  {orders.items.map((order) => (
                    <tr key={order.id}>
                      <td><strong>{order.orderNo}</strong><small>{order.merchant} · {serviceLabels[order.serviceType]}{order.archivedAt ? ' · 已归档' : ''}</small></td>
                      <td><span className={`status status-${order.status.toLowerCase()}`}>{statusLabels[order.status]}</span></td>
                      <td><strong>{order.customer.displayName}</strong><small>{maskPhone(order.customer.phone)}</small></td>
                      <td>{order.courier ? <><strong>{order.courier.displayName}</strong><small>{maskPhone(order.courier.phone)}</small></> : <span className="muted">未分配</span>}</td>
                      <td>{order.destination}</td>
                      <td className="money">{order.paymentMethod === 'CASH_ON_DELIVERY'
                        ? `${order.paymentStatus === 'PAID' ? '到付已结清' : '到付'} ${formatMoney(order.feeCents)}`
                        : order.paymentMethod === 'PERSONAL_QR_MANUAL'
                          ? `${manualPaymentStatusLabel(order.manualPaymentStatus)} ${formatMoney(order.feeCents)}`
                          : `${order.paymentStatus === 'PAID' ? '线上已支付' : '线上待支付'} ${formatMoney(order.feeCents)}`}</td>
                      <td>{formatDate(order.createdAt)}</td>
                      <td><button className="text-button" onClick={() => setSelectedId(order.id)}>查看详情</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <footer className="pagination">
            <span>{pageSummary}</span>
            <div>
              <button disabled={!orders || orders.page === 0} onClick={() => setPage((value) => value - 1)}>上一页</button>
              <span>{orders && orders.totalPages > 0 ? `${orders.page + 1} / ${orders.totalPages}` : '0 / 0'}</span>
              <button disabled={!orders || orders.page + 1 >= orders.totalPages} onClick={() => setPage((value) => value + 1)}>下一页</button>
            </div>
          </footer>
        </section>
      </main> : visibleView === 'couriers'
        ? <CourierManagement key={dashboardFilter} initialStatus={dashboardFilter as CourierCertificationStatus | ''} session={session} onSessionExpired={onLogout} />
        : visibleView === 'users'
          ? <UserManagement session={session} onSessionExpired={onLogout} />
          : visibleView === 'risk'
            ? <RiskManagement session={session} onSessionExpired={onLogout} />
          : visibleView === 'issues'
            ? <IssueManagement
                key={dashboardFilter}
                initialStatus={dashboardFilter as OrderIssueHandlingStatus | ''}
                session={session}
                onSessionExpired={onLogout}
                onOpenOrder={(id) => {
                  setSelectedId(id)
                  setActiveView('orders')
                }}
              />
            : visibleView === 'feedback'
              ? <FeedbackManagement key={dashboardFilter} initialStatus={dashboardFilter as FeedbackHandlingStatus | ''} session={session} onSessionExpired={onLogout} />
              : visibleView === 'accounts'
                ? <AdminAccountManagement session={session} onSessionExpired={onLogout} />
                : visibleView === 'push'
                  ? <PushDeliveryManagement session={session} onSessionExpired={onLogout} />
                  : <AuditManagement session={session} onSessionExpired={onLogout} />}

      {selectedId !== null && hasPermission(session, 'ORDER_VIEW') && (
        <OrderDrawer
          detail={detail}
          error={detailError}
          loading={detailLoading}
          onClose={() => setSelectedId(null)}
          session={session}
          onRetry={() => {
            setReloadKey((value) => value + 1)
            const id = selectedId
            setSelectedId(null)
            window.setTimeout(() => setSelectedId(id), 0)
          }}
        />
      )}


      {mfaSettingsOpen && <AdminMfaSettings session={session} onClose={() => setMfaSettingsOpen(false)} onComplete={value => { onSessionChange(value); setMfaSettingsOpen(false) }} />}
      {sessionsOpen && <SessionSecurityDialog session={session} onClose={() => setSessionsOpen(false)} onSessionExpired={onLogout} />}
      {passwordDialogOpen && <div className="drawer-layer">
        <button className="drawer-backdrop" aria-label="关闭修改密码" onClick={() => setPasswordDialogOpen(false)} />
        <aside className="drawer compact-drawer" aria-modal="true" role="dialog">
          <header><div><p className="eyebrow accent">ACCOUNT SECURITY</p><h2>修改我的密码</h2></div><button className="close-button" onClick={() => setPasswordDialogOpen(false)} aria-label="关闭">×</button></header>
          <form className="drawer-content rejection-form" onSubmit={submitOwnPassword}>
            <p className="readonly-note">修改成功后，当前账号在所有设备上的登录都会失效，需要使用新密码重新登录。</p>
            <label>当前密码<input autoComplete="current-password" required type="password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} /></label>
            <label>新密码<input autoComplete="new-password" minLength={8} maxLength={72} required type="password" value={newOwnPassword} onChange={(event) => setNewOwnPassword(event.target.value)} /></label>
            <label>确认新密码<input autoComplete="new-password" minLength={8} maxLength={72} required type="password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} /></label>
            {ownPasswordError && <p className="form-error">{ownPasswordError}</p>}
            <button className="primary-button account-action-button" disabled={passwordChanging || newOwnPassword.length < 8 || confirmPassword.length < 8} type="submit">{passwordChanging ? '正在修改…' : '确认修改密码'}</button>
          </form>
        </aside>
      </div>}
    </div>
  )
}

function DashboardManagement({ session, onNavigate, onOpenOrder, onSessionExpired }: {
  session: AuthResponse
  onNavigate: (view: AdminView, filter?: string) => void
  onOpenOrder: (id: number) => void
  onSessionExpired: () => void
}) {
  const [dashboard, setDashboard] = useState<AdminDashboard | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    getDashboard(session.token)
      .then((result) => active && setDashboard(result))
      .catch((reason: unknown) => {
        if (!active) return
        if (reason instanceof ApiError && reason.status === 401) {
          onSessionExpired()
          return
        }
        setError(reason instanceof Error ? reason.message : '运营数据加载失败')
      })
      .finally(() => active && setLoading(false))
    return () => { active = false }
  }, [reloadKey, session.token])

  const metrics = dashboard ? [
    { label: '订单总量', value: dashboard.totalOrders, note: '平台累计订单', view: 'orders' as AdminView },
    { label: '注册用户', value: dashboard.totalUsers, note: '全部平台账号', view: 'users' as AdminView },
    { label: '配送骑手', value: dashboard.totalCouriers, note: '骑手角色账号', view: 'couriers' as AdminView },
    { label: '待审认证', value: dashboard.pendingCertifications, note: '等待运营审核', view: 'couriers' as AdminView, filter: 'PENDING', alert: dashboard.pendingCertifications > 0 },
    { label: '待处理异常', value: dashboard.pendingIssues, note: '尚未被领取', view: 'issues' as AdminView, filter: 'PENDING', alert: dashboard.pendingIssues > 0 },
    { label: '处理中异常', value: dashboard.processingIssues, note: '已领取未解决', view: 'issues' as AdminView, filter: 'PROCESSING' },
    { label: '待处理反馈', value: dashboard.pendingFeedback, note: '尚未被领取', view: 'feedback' as AdminView, filter: 'PENDING', alert: dashboard.pendingFeedback > 0 },
    { label: '处理中反馈', value: dashboard.processingFeedback, note: '已领取未解决', view: 'feedback' as AdminView, filter: 'PROCESSING' },
  ].filter((metric) => hasPermission(session, viewPermissions[metric.view])) : []

  return <main className="content operations-content">
    <header className="page-header">
      <div>
        <p className="eyebrow accent">OPERATIONS OVERVIEW</p>
        <h1>运营指挥台</h1>
        <p className="muted">跟进超时订单，掌握在线运力，直达待处理事项。</p>
      </div>
      <button className="secondary-button" onClick={() => setReloadKey((value) => value + 1)}>刷新待办统计</button>
    </header>

    <CommandCenter session={session} onOpenOrder={onOpenOrder} onSessionExpired={onSessionExpired} />
    <h2>运营待办与平台概况</h2>
    {error && <section className="dashboard-section"><ErrorState message={error} onRetry={() => setReloadKey((value) => value + 1)} /></section>}
    {!error && loading && <section className="dashboard-section table-state">正在加载运营数据…</section>}
    {!error && !loading && dashboard && <>
      <section className="dashboard-grid" aria-label="运营指标">
        {metrics.map((metric) => <button
          className={`metric-card ${metric.alert ? 'alert' : ''}`}
          key={metric.label}
          onClick={() => onNavigate(metric.view, metric.filter)}
        >
          <span>{metric.label}</span>
          <strong>{metric.value}</strong>
          <small>{metric.note}</small>
        </button>)}
      </section>
      <section className="dashboard-section">
        <div>
          <p className="eyebrow accent">QUICK ACTIONS</p>
          <h2>快捷处理</h2>
          <p className="muted">直接进入当前主要运营工作区。</p>
        </div>
        <div className="quick-actions">
          {hasPermission(session, 'ISSUE_VIEW') && <button onClick={() => onNavigate('issues', 'PENDING')}><strong>领取异常工单</strong><span>{dashboard.pendingIssues} 项待领取</span></button>}
          {hasPermission(session, 'COURIER_VIEW') && <button onClick={() => onNavigate('couriers', 'PENDING')}><strong>审核骑手认证</strong><span>{dashboard.pendingCertifications} 项待审核</span></button>}
          {hasPermission(session, 'FEEDBACK_VIEW') && <button onClick={() => onNavigate('feedback', 'PENDING')}><strong>领取意见反馈</strong><span>{dashboard.pendingFeedback} 项待领取</span></button>}
          {hasPermission(session, 'AUDIT_VIEW') && <button onClick={() => onNavigate('audit')}><strong>查看操作日志</strong><span>追踪管理操作</span></button>}
        </div>
      </section>
    </>}
  </main>
}

function CourierManagement({ session, onSessionExpired, initialStatus = '' }: {
  initialStatus?: CourierCertificationStatus | ''
  session: AuthResponse
  onSessionExpired: () => void
}) {
  const [draftQuery, setDraftQuery] = useState('')
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<CourierCertificationStatus | ''>(initialStatus)
  const [page, setPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)
  const [couriers, setCouriers] = useState<AdminCourierPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<AdminCourierItem | null>(null)
  const [performance, setPerformance] = useState<AdminCourierPerformance | null>(null)
  const [performancePage, setPerformancePage] = useState(0)
  const [performanceReloadKey, setPerformanceReloadKey] = useState(0)
  const [performanceLoading, setPerformanceLoading] = useState(false)
  const [performanceError, setPerformanceError] = useState('')
  const [rejecting, setRejecting] = useState(false)
  const [rejectionReason, setRejectionReason] = useState('')
  const [actionLoading, setActionLoading] = useState(false)
  const [actionError, setActionError] = useState('')

  const handleError = (reason: unknown, fallback: string) => {
    if (reason instanceof ApiError && reason.status === 401) {
      onSessionExpired()
      return ''
    }
    return reason instanceof Error ? reason.message : fallback
  }

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    getCouriers(session.token, { query, status, page, size: PAGE_SIZE })
      .then((result) => active && setCouriers(result))
      .catch((reason: unknown) => {
        if (!active) return
        setError(handleError(reason, '骑手数据加载失败'))
      })
      .finally(() => active && setLoading(false))
    return () => { active = false }
  }, [page, query, reloadKey, session.token, status])

  const selectedCourierId = selected?.courierId

  useEffect(() => {
    if (!selectedCourierId) {
      setPerformance(null)
      return
    }
    let active = true
    setPerformanceLoading(true)
    setPerformanceError('')
    getCourierPerformance(session.token, selectedCourierId, performancePage, 10)
      .then((result) => active && setPerformance(result))
      .catch((reason: unknown) => {
        if (!active) return
        setPerformanceError(handleError(reason, '骑手配送统计加载失败'))
      })
      .finally(() => active && setPerformanceLoading(false))
    return () => { active = false }
  }, [performancePage, performanceReloadKey, selectedCourierId, session.token])

  const pageSummary = useMemo(() => {
    if (!couriers || couriers.totalElements === 0) return '共 0 名骑手'
    const start = couriers.page * couriers.size + 1
    const end = Math.min(start + couriers.items.length - 1, couriers.totalElements)
    return `第 ${start}–${end} 名，共 ${couriers.totalElements} 名`
  }, [couriers])

  const pendingOnPage = couriers?.items.filter((courier) => courier.status === 'PENDING').length ?? 0

  const search = (event: FormEvent) => {
    event.preventDefault()
    setPage(0)
    setQuery(draftQuery.trim())
  }

  const approve = async () => {
    if (!selected?.certificationId || !window.confirm(`确认通过 ${selected.displayName} 的骑手认证？`)) return
    setActionLoading(true)
    setActionError('')
    try {
      await approveCourierCertification(session.token, selected.certificationId)
      setSelected(null)
      setReloadKey((value) => value + 1)
    } catch (reason) {
      setActionError(handleError(reason, '审核操作失败'))
    } finally {
      setActionLoading(false)
    }
  }

  const reject = async (event: FormEvent) => {
    event.preventDefault()
    if (!selected?.certificationId || !rejectionReason.trim()) return
    setActionLoading(true)
    setActionError('')
    try {
      await rejectCourierCertification(session.token, selected.certificationId, rejectionReason.trim())
      setSelected(null)
      setRejecting(false)
      setRejectionReason('')
      setReloadKey((value) => value + 1)
    } catch (reason) {
      setActionError(handleError(reason, '驳回操作失败'))
    } finally {
      setActionLoading(false)
    }
  }

  return (
    <main className="content">
      <header className="page-header">
        <div>
          <p className="eyebrow accent">COURIER OPERATIONS</p>
          <h1>骑手管理</h1>
          <p className="muted">统一查看骑手账号与认证资料，处理待审核申请。</p>
        </div>
        <button className="secondary-button" onClick={() => setReloadKey((value) => value + 1)}>刷新数据</button>
      </header>

      <section className="summary-row" aria-label="骑手认证摘要">
        <article><span>当前结果</span><strong>{couriers?.totalElements ?? '—'}</strong><small>名骑手</small></article>
        <article><span>本页待审核</span><strong>{pendingOnPage}</strong><small>项申请</small></article>
        <article><span>筛选状态</span><strong className="text-value">{status ? certificationStatusLabels[status] : '全部骑手'}</strong><small>认证状态</small></article>
      </section>

      <section className="orders-card">
        <form className="filters" onSubmit={search}>
          <label className="search-field">
            <span>搜索</span>
            <input
              placeholder="昵称或手机号"
              value={draftQuery}
              onChange={(event) => setDraftQuery(event.target.value)}
            />
          </label>
          <label>
            <span>认证状态</span>
            <select
              value={status}
              onChange={(event) => {
                setPage(0)
                setStatus(event.target.value as CourierCertificationStatus | '')
              }}
            >
              <option value="">全部状态</option>
              {Object.entries(certificationStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
          <button className="primary-button filter-button" type="submit">查询骑手</button>
        </form>

        {error && <ErrorState message={error} onRetry={() => setReloadKey((value) => value + 1)} />}
        {!error && loading && <div className="table-state">正在加载骑手…</div>}
        {!error && !loading && couriers?.items.length === 0 && <div className="table-state">没有符合条件的骑手</div>}
        {!error && !loading && couriers && couriers.items.length > 0 && (
          <div className="table-scroll">
            <table className="courier-table">
              <thead><tr><th>骑手账号</th><th>已完成订单</th><th>快递完成单</th><th>学生证照片</th><th>认证状态</th><th>提交时间</th><th></th></tr></thead>
              <tbody>
                {couriers.items.map((courier) => (
                  <tr key={courier.courierId}>
                    <td><strong>{courier.displayName}</strong><small>{maskPhone(courier.phone)}</small></td>
                    <td><strong>{courier.completedOrderCount}</strong><small>单</small></td>
                    <td><strong>{courier.completedPackageOrderCount}</strong><small>单快递</small></td>
                    <td>{courier.photoUploaded ? '已上传' : <span className="muted">未上传</span>}</td>
                    <td><CertificationStatus status={courier.status} /></td>
                    <td>{courier.submittedAt ? formatDate(courier.submittedAt) : '—'}</td>
                    <td><button className="text-button" onClick={() => {
                      setSelected(courier)
                      setPerformance(null)
                      setPerformancePage(0)
                      setRejecting(false)
                      setRejectionReason('')
                      setActionError('')
                    }}>查看详情</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <footer className="pagination">
          <span>{pageSummary}</span>
          <div>
            <button disabled={!couriers || couriers.page === 0} onClick={() => setPage((value) => value - 1)}>上一页</button>
            <span>{couriers && couriers.totalPages > 0 ? `${couriers.page + 1} / ${couriers.totalPages}` : '0 / 0'}</span>
            <button disabled={!couriers || couriers.page + 1 >= couriers.totalPages} onClick={() => setPage((value) => value + 1)}>下一页</button>
          </div>
        </footer>
      </section>

      {selected && (
        <div className="drawer-layer">
          <button className="drawer-backdrop" aria-label="关闭骑手详情" onClick={() => setSelected(null)} />
          <aside className="drawer" aria-modal="true" role="dialog">
            <header>
              <div><p className="eyebrow accent">COURIER DETAIL</p><h2>{selected.displayName}</h2></div>
              <button className="close-button" onClick={() => setSelected(null)} aria-label="关闭">×</button>
            </header>
            <div className="drawer-content">
              <div className="detail-hero courier-hero">
                <CertificationStatus status={selected.status} />
                <strong>{selected.approved ? '可接单' : '不可接单'}</strong>
                <small>{maskPhone(selected.phone)} · 注册于 {formatDate(selected.registeredAt)}</small>
              </div>
              <DetailSection title="账号与认证资料">
                <DetailGrid items={[
                  ['账号昵称', selected.displayName],
                  ['完整手机号', selected.phone],
                  ['已完成订单', `${selected.completedOrderCount} 单`],
                  ['快递完成单', `${selected.completedPackageOrderCount} 单`],
                  ['学生证照片', selected.photoUploaded ? '已上传' : '未上传'],
                  ['提交时间', selected.submittedAt ? formatDate(selected.submittedAt) : '—'],
                ]} />
                {selected.photoUploaded && selected.certificationId && hasPermission(session, 'COURIER_REVIEW') &&
                  <StudentCardPreview key={selected.certificationId} token={session.token}
                    certificationId={selected.certificationId} />}
              </DetailSection>
              <DetailSection title="快递配送统计">
                {performanceError && <ErrorState message={performanceError} onRetry={() => setPerformanceReloadKey((value) => value + 1)} />}
                {!performanceError && performanceLoading && !performance && <div className="drawer-state">正在加载配送统计…</div>}
                {!performanceError && performance && (
                  <DetailGrid items={[
                    ['今日完成', `${performance.todayCompletedPackageOrders} 单`],
                    ['近 7 天完成', `${performance.lastSevenDaysCompletedPackageOrders} 单`],
                    ['本月完成', `${performance.currentMonthCompletedPackageOrders} 单`],
                    ['累计完成', `${performance.totalCompletedPackageOrders} 单`],
                    ['小件累计', `${performance.smallCompletedPackageOrders} 单`],
                    ['中件累计', `${performance.mediumCompletedPackageOrders} 单`],
                    ['中大件累计', `${performance.mediumLargeCompletedPackageOrders} 单`],
                    ['大件累计', `${performance.largeCompletedPackageOrders} 单`],
                    ['特大件累计', `${performance.specialLargeCompletedPackageOrders} 单`],
                    ['超大件累计', `${performance.extraLargeCompletedPackageOrders} 单`],
                  ]} />
                )}
              </DetailSection>
              <DetailSection title="快递订单明细">
                {!performanceError && performanceLoading && performance && <p className="muted">正在刷新订单…</p>}
                {!performanceError && !performanceLoading && performance?.orders.items.length === 0 && <p className="empty-inline">该骑手暂无快递订单</p>}
                {!performanceError && performance && performance.orders.items.length > 0 && (
                  <>
                    <div className="table-scroll courier-order-detail-table">
                      <table>
                        <thead><tr><th>订单</th><th>状态</th><th>用户</th><th>送达地点</th><th>服务费</th><th>创建时间</th></tr></thead>
                        <tbody>
                          {performance.orders.items.map((order) => (
                            <tr key={order.id}>
                              <td><strong>{order.orderNo}</strong><small>{serviceLabels[order.serviceType]}</small></td>
                              <td><span className={`status status-${order.status.toLowerCase()}`}>{statusLabels[order.status]}</span></td>
                              <td>{order.customerName}</td>
                              <td>{order.destination}</td>
                              <td className="money">{formatMoney(order.feeCents)}</td>
                              <td>{formatDate(order.createdAt)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                    <footer className="pagination compact-pagination">
                      <span>共 {performance.orders.totalElements} 单</span>
                      <div>
                        <button disabled={performance.orders.page === 0 || performanceLoading} onClick={() => setPerformancePage((value) => value - 1)}>上一页</button>
                        <span>{performance.orders.totalPages > 0 ? `${performance.orders.page + 1} / ${performance.orders.totalPages}` : '0 / 0'}</span>
                        <button disabled={performance.orders.page + 1 >= performance.orders.totalPages || performanceLoading} onClick={() => setPerformancePage((value) => value + 1)}>下一页</button>
                      </div>
                    </footer>
                  </>
                )}
              </DetailSection>
              {(selected.reviewedAt || selected.rejectionReason) && (
                <DetailSection title="审核记录">
                  <DetailGrid items={[
                    ['审核人', selected.reviewerName ?? '—'],
                    ['审核时间', selected.reviewedAt ? formatDate(selected.reviewedAt) : '—'],
                    ['驳回原因', selected.rejectionReason ?? '—'],
                  ]} />
                </DetailSection>
              )}
              {selected.status === 'PENDING' && selected.certificationId && hasPermission(session, 'COURIER_REVIEW') && (
                <DetailSection title="审核操作">
                  {actionError && <p className="form-error">{actionError}</p>}
                  {!rejecting ? (
                    <div className="review-actions">
                      <button className="primary-button" disabled={actionLoading || !selected.photoUploaded}
                        onClick={approve}>通过认证</button>
                      <button className="danger-button" disabled={actionLoading} onClick={() => setRejecting(true)}>驳回申请</button>
                    </div>
                  ) : (
                    <form className="rejection-form" onSubmit={reject}>
                      <label>驳回原因<textarea maxLength={300} required value={rejectionReason} onChange={(event) => setRejectionReason(event.target.value)} /></label>
                      <div className="review-actions">
                        <button className="danger-button" disabled={actionLoading || !rejectionReason.trim()} type="submit">确认驳回</button>
                        <button className="secondary-button" disabled={actionLoading} onClick={() => setRejecting(false)} type="button">取消</button>
                      </div>
                    </form>
                  )}
                </DetailSection>
              )}
            </div>
          </aside>
        </div>
      )}
    </main>
  )
}

function RiskManagement({ session, onSessionExpired }: {
  session: AuthResponse
  onSessionExpired: () => void
}) {
  const [status, setStatus] = useState<RiskSignalStatus | ''>('PENDING')
  const [page, setPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)
  const [signals, setSignals] = useState<RiskSignalPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [actionId, setActionId] = useState<number | null>(null)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    getRiskSignals(session.token, { status, page, size: PAGE_SIZE })
      .then((result) => active && setSignals(result))
      .catch((reason: unknown) => {
        if (!active) return
        if (reason instanceof ApiError && reason.status === 401) onSessionExpired()
        else setError(reason instanceof Error ? reason.message : '风险信号加载失败')
      })
      .finally(() => active && setLoading(false))
    return () => { active = false }
  }, [page, reloadKey, session.token, status])

  const review = async (signal: RiskSignalItem, confirmed: boolean) => {
    const reason = window.prompt(confirmed ? '填写核实结论和扣分依据（至少5个字）' : '填写驳回原因（至少5个字）', '')?.trim()
    if (!reason || reason.length < 5) return
    let delta = 0
    if (confirmed) {
      const entered = window.prompt('填写扣分值（-100 至 -1）', '-10')
      if (entered === null) return
      delta = Number(entered)
      if (!Number.isInteger(delta) || delta < -100 || delta >= 0) {
        window.alert('扣分值必须是 -100 至 -1 的整数。')
        return
      }
    }
    if (!window.confirm(confirmed
      ? `确认该风险并调整 ${signal.displayName} 的信用分 ${delta} 分？`
      : `确认驳回 ${signal.displayName} 的风险提示？`)) return
    setActionId(signal.id)
    setError('')
    try {
      if (confirmed) await confirmRiskSignal(session.token, signal.id, delta, reason)
      else await dismissRiskSignal(session.token, signal.id, reason)
      setReloadKey((value) => value + 1)
    } catch (reasonValue) {
      if (reasonValue instanceof ApiError && reasonValue.status === 401) onSessionExpired()
      else setError(reasonValue instanceof Error ? reasonValue.message : '风险信号处理失败')
    } finally {
      setActionId(null)
    }
  }

  return (
    <main className="content">
      <header className="page-header">
        <div>
          <p className="eyebrow accent">RISK REVIEW</p>
          <h1>风险审核</h1>
          <p className="muted">系统只生成提示；确认、扣分或驳回均由有权限的管理员人工决定。</p>
        </div>
        <button className="secondary-button" onClick={() => setReloadKey((value) => value + 1)}>刷新数据</button>
      </header>
      <section className="summary-row" aria-label="风险审核摘要">
        <article><span>当前结果</span><strong>{signals?.totalElements ?? '—'}</strong><small>条风险信号</small></article>
        <article><span>处理原则</span><strong className="text-value">人工复核</strong><small>不自动处罚</small></article>
        <article><span>当前状态</span><strong className="text-value">{status ? riskSignalStatusLabels[status] : '全部状态'}</strong><small>审核筛选</small></article>
      </section>
      <section className="orders-card">
        <div className="filters">
          <label><span>处理状态</span><select value={status} onChange={(event) => {
            setPage(0)
            setStatus(event.target.value as RiskSignalStatus | '')
          }}><option value="">全部状态</option>{Object.entries(riskSignalStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        </div>
        {error && <ErrorState message={error} onRetry={() => setReloadKey((value) => value + 1)} />}
        {!error && loading && <div className="table-state">正在加载风险信号…</div>}
        {!error && !loading && signals?.items.length === 0 && <div className="table-state">当前没有风险信号</div>}
        {!error && !loading && signals && signals.items.length > 0 && <div className="table-scroll"><table>
          <thead><tr><th>用户</th><th>信号类型</th><th>检测依据</th><th>关联订单</th><th>状态</th><th>产生时间</th><th></th></tr></thead>
          <tbody>{signals.items.map((signal) => <tr key={signal.id}>
            <td><strong>{signal.displayName}</strong><small>{maskPhone(signal.phone)} · ID {signal.userId}</small></td>
            <td>{riskSignalTypeLabels[signal.type]}</td>
            <td>{signal.details}</td>
            <td>{signal.orderId ? `ID ${signal.orderId}` : '—'}</td>
            <td><span className="status">{riskSignalStatusLabels[signal.status]}</span>{signal.reviewNote && <small>{signal.reviewNote}</small>}</td>
            <td>{formatDate(signal.createdAt)}</td>
            <td>{signal.status === 'PENDING' && hasPermission(session, 'USER_MANAGE')
              ? <span className="table-actions"><button className="text-button" disabled={actionId === signal.id} onClick={() => void review(signal, true)}>确认并扣分</button><button className="text-button" disabled={actionId === signal.id} onClick={() => void review(signal, false)}>驳回</button></span>
              : <span className="muted">{signal.reviewerName ?? '只读'}</span>}</td>
          </tr>)}</tbody>
        </table></div>}
        <footer className="pagination"><span>共 {signals?.totalElements ?? 0} 条</span><div>
          <button disabled={!signals || signals.page === 0} onClick={() => setPage((value) => value - 1)}>上一页</button>
          <span>{signals && signals.totalPages > 0 ? `${signals.page + 1} / ${signals.totalPages}` : '0 / 0'}</span>
          <button disabled={!signals || signals.page + 1 >= signals.totalPages} onClick={() => setPage((value) => value + 1)}>下一页</button>
        </div></footer>
      </section>
    </main>
  )
}

function UserManagement({ session, onSessionExpired }: {
  session: AuthResponse
  onSessionExpired: () => void
}) {
  const [draftQuery, setDraftQuery] = useState('')
  const [query, setQuery] = useState('')
  const [role, setRole] = useState<UserRole | ''>('')
  const [page, setPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)
  const [users, setUsers] = useState<AdminUserPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<AdminUserItem | null>(null)
  const [actionLoading, setActionLoading] = useState(false)
  const [actionError, setActionError] = useState('')
  const [passwordTarget, setPasswordTarget] = useState<AdminUserItem | null>(null)
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordSubmitting, setPasswordSubmitting] = useState(false)
  const [passwordError, setPasswordError] = useState('')
  const [couponMessage, setCouponMessage] = useState('')
  const [credit, setCredit] = useState<CreditProfile | null>(null)
  const [creditLoading, setCreditLoading] = useState(false)
  const [creditError, setCreditError] = useState('')
  const [creditDelta, setCreditDelta] = useState('-5')
  const [creditReason, setCreditReason] = useState('')

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    getUsers(session.token, { query, role, page, size: PAGE_SIZE })
      .then((result) => active && setUsers(result))
      .catch((reason: unknown) => {
        if (!active) return
        if (reason instanceof ApiError && reason.status === 401) {
          onSessionExpired()
          return
        }
        setError(reason instanceof Error ? reason.message : '用户数据加载失败')
      })
      .finally(() => active && setLoading(false))
    return () => { active = false }
  }, [page, query, reloadKey, role, session.token])

  useEffect(() => {
    if (!selected || selected.role === 'ADMIN') {
      setCredit(null)
      return
    }
    let active = true
    setCreditLoading(true)
    setCreditError('')
    getUserCredit(session.token, selected.id)
      .then((result) => active && setCredit(result))
      .catch((reason: unknown) => {
        if (!active) return
        if (reason instanceof ApiError && reason.status === 401) onSessionExpired()
        else setCreditError(reason instanceof Error ? reason.message : '信用档案加载失败')
      })
      .finally(() => active && setCreditLoading(false))
    return () => { active = false }
  }, [selected?.id, selected?.role, session.token])

  const pageSummary = useMemo(() => {
    if (!users || users.totalElements === 0) return '共 0 个账号'
    const start = users.page * users.size + 1
    const end = Math.min(start + users.items.length - 1, users.totalElements)
    return `第 ${start}–${end} 个，共 ${users.totalElements} 个`
  }, [users])

  const search = (event: FormEvent) => {
    event.preventDefault()
    setPage(0)
    setQuery(draftQuery.trim())
  }

  const changeAccountStatus = async () => {
    if (!selected || selected.role === 'ADMIN') return
    const verb = selected.enabled ? '停用' : '恢复'
    if (!window.confirm(`确认${verb} ${selected.displayName} 的账号？`)) return
    setActionLoading(true)
    setActionError('')
    try {
      const updated = selected.enabled
        ? await suspendUser(session.token, selected.id)
        : await restoreUser(session.token, selected.id)
      setSelected(updated)
      setReloadKey((value) => value + 1)
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) {
        onSessionExpired()
      } else {
        setActionError(reason instanceof Error ? reason.message : '账号状态修改失败')
      }
    } finally {
      setActionLoading(false)
    }
  }

  const unlockLogin = async () => {
    if (!selected || selected.role === 'ADMIN' || !isLoginLocked(selected.loginLockedUntil)) return
    if (!window.confirm(`确认解除 ${selected.displayName} 的登录锁定？`)) return
    setActionLoading(true)
    setActionError('')
    try {
      const updated = await unlockUserLogin(session.token, selected.id)
      setSelected(updated)
      setReloadKey((value) => value + 1)
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onSessionExpired()
      else setActionError(reason instanceof Error ? reason.message : '登录解锁失败')
    } finally {
      setActionLoading(false)
    }
  }

  const openPasswordReset = (user: AdminUserItem) => {
    setPasswordTarget(user)
    setNewPassword('')
    setConfirmPassword('')
    setPasswordError('')
  }

  const submitPasswordReset = async (event: FormEvent) => {
    event.preventDefault()
    if (!passwordTarget) return
    if (newPassword !== confirmPassword) {
      setPasswordError('两次输入的新密码不一致')
      return
    }
    setPasswordSubmitting(true)
    setPasswordError('')
    try {
      const updated = await resetUserPassword(session.token, passwordTarget.id, newPassword)
      if (selected?.id === updated.id) setSelected(updated)
      setReloadKey((value) => value + 1)
      setPasswordTarget(null)
      window.alert(`${passwordTarget.displayName} 的密码已重置，原有登录会话已失效。`)
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onSessionExpired()
      else setPasswordError(reason instanceof Error ? reason.message : '密码重置失败')
    } finally {
      setPasswordSubmitting(false)
    }
  }

  const grantCoupon = async () => {
    if (!selected || selected.role !== 'CUSTOMER') return
    if (!window.confirm(`确认向 ${selected.displayName} 发放一张1元优惠券？`)) return
    setActionLoading(true)
    setActionError('')
    setCouponMessage('')
    try {
      const granted = await issueOneYuanCoupon(session.token, selected.id)
      setCouponMessage(`已发放“${granted.title}”，有效期至 ${formatDate(granted.expiresAt)}`)
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onSessionExpired()
      else setActionError(reason instanceof Error ? reason.message : '优惠券发放失败')
    } finally {
      setActionLoading(false)
    }
  }

  const reloadCredit = async () => {
    if (!selected || selected.role === 'ADMIN') return
    setCreditLoading(true)
    setCreditError('')
    try {
      setCredit(await getUserCredit(session.token, selected.id))
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onSessionExpired()
      else setCreditError(reason instanceof Error ? reason.message : '信用档案加载失败')
    } finally {
      setCreditLoading(false)
    }
  }

  const submitCreditAdjustment = async (event: FormEvent) => {
    event.preventDefault()
    if (!selected || selected.role === 'ADMIN') return
    const delta = Number(creditDelta)
    if (!Number.isInteger(delta) || delta < -100 || delta > 100 || delta === 0) {
      setCreditError('调整值必须是 -100 至 100 之间且不为 0 的整数')
      return
    }
    const reason = creditReason.trim()
    if (reason.length < 5) {
      setCreditError('调整原因至少填写5个字')
      return
    }
    if (!window.confirm(`确认调整 ${selected.displayName} 的信用分 ${delta > 0 ? '+' : ''}${delta} 分？`)) return
    setActionLoading(true)
    setCreditError('')
    try {
      await adjustUserCredit(session.token, selected.id, delta, reason)
      setCreditReason('')
      await reloadCredit()
    } catch (reasonValue) {
      if (reasonValue instanceof ApiError && reasonValue.status === 401) onSessionExpired()
      else setCreditError(reasonValue instanceof Error ? reasonValue.message : '信用分调整失败')
    } finally {
      setActionLoading(false)
    }
  }

  const reverseCredit = async (eventId: number) => {
    if (!selected || selected.role === 'ADMIN') return
    const reason = window.prompt('填写撤销原因（至少5个字）', '')?.trim()
    if (!reason || reason.length < 5) return
    if (!window.confirm('确认撤销这条信用变动？系统会生成一条反向流水，原记录不会删除。')) return
    setActionLoading(true)
    setCreditError('')
    try {
      await reverseCreditEvent(session.token, selected.id, eventId, reason)
      await reloadCredit()
    } catch (reasonValue) {
      if (reasonValue instanceof ApiError && reasonValue.status === 401) onSessionExpired()
      else setCreditError(reasonValue instanceof Error ? reasonValue.message : '信用记录撤销失败')
    } finally {
      setActionLoading(false)
    }
  }

  return (
    <main className="content">
      <header className="page-header">
        <div>
          <p className="eyebrow accent">USER DIRECTORY</p>
          <h1>用户管理</h1>
          <p className="muted">查询平台账号、角色与注册信息，为客服核查提供统一入口。</p>
        </div>
        <button className="secondary-button" onClick={() => setReloadKey((value) => value + 1)}>刷新数据</button>
      </header>

      <section className="summary-row" aria-label="用户目录摘要">
        <article><span>当前结果</span><strong>{users?.totalElements ?? '—'}</strong><small>个账号</small></article>
        <article><span>本页管理员</span><strong>{users?.items.filter((user) => user.role === 'ADMIN').length ?? 0}</strong><small>个账号</small></article>
        <article><span>角色筛选</span><strong className="text-value">{role ? roleLabels[role] : '全部角色'}</strong><small>账号类型</small></article>
      </section>

      <section className="orders-card">
        <form className="filters" onSubmit={search}>
          <label className="search-field">
            <span>搜索</span>
            <input placeholder="昵称或手机号" value={draftQuery} onChange={(event) => setDraftQuery(event.target.value)} />
          </label>
          <label>
            <span>账号角色</span>
            <select value={role} onChange={(event) => {
              setPage(0)
              setRole(event.target.value as UserRole | '')
            }}>
              <option value="">全部角色</option>
              {Object.entries(roleLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
          <button className="primary-button filter-button" type="submit">查询用户</button>
        </form>

        {error && <ErrorState message={error} onRetry={() => setReloadKey((value) => value + 1)} />}
        {!error && loading && <div className="table-state">正在加载用户…</div>}
        {!error && !loading && users?.items.length === 0 && <div className="table-state">没有符合条件的用户</div>}
        {!error && !loading && users && users.items.length > 0 && (
          <div className="table-scroll">
            <table className="user-table">
              <thead><tr><th>账号</th><th>角色</th><th>账号状态</th><th>手机号</th><th>骑手接单资格</th><th>注册时间</th><th></th></tr></thead>
              <tbody>
                {users.items.map((user) => (
                  <tr key={user.id}>
                    <td><strong>{user.displayName}</strong><small>ID {user.id}</small></td>
                    <td><span className={`status role-${user.role.toLowerCase()}`}>{roleLabels[user.role]}</span></td>
                    <td><span className={`status account-${!user.enabled ? 'suspended' : isLoginLocked(user.loginLockedUntil) ? 'locked' : 'enabled'}`}>{!user.enabled ? '已停用' : isLoginLocked(user.loginLockedUntil) ? '登录锁定' : '正常'}</span></td>
                    <td>{maskPhone(user.phone)}</td>
                    <td>{user.role === 'COURIER' ? (user.courierApproved ? '可接单' : '不可接单') : '—'}</td>
                    <td>{formatDate(user.registeredAt)}</td>
                    <td><button className="text-button" onClick={() => {
                      setSelected(user)
                      setActionError('')
                      setCouponMessage('')
                      setCredit(null)
                      setCreditError('')
                      setCreditDelta('-5')
                      setCreditReason('')
                    }}>查看详情</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <footer className="pagination">
          <span>{pageSummary}</span>
          <div>
            <button disabled={!users || users.page === 0} onClick={() => setPage((value) => value - 1)}>上一页</button>
            <span>{users && users.totalPages > 0 ? `${users.page + 1} / ${users.totalPages}` : '0 / 0'}</span>
            <button disabled={!users || users.page + 1 >= users.totalPages} onClick={() => setPage((value) => value + 1)}>下一页</button>
          </div>
        </footer>
      </section>

      {selected && (
        <div className="drawer-layer">
          <button className="drawer-backdrop" aria-label="关闭用户详情" onClick={() => setSelected(null)} />
          <aside className="drawer" aria-modal="true" role="dialog">
            <header>
              <div><p className="eyebrow accent">USER DETAIL</p><h2>{selected.displayName}</h2></div>
              <button className="close-button" onClick={() => setSelected(null)} aria-label="关闭">×</button>
            </header>
            <div className="drawer-content">
              <div className="detail-hero user-hero">
                <span className={`status role-${selected.role.toLowerCase()}`}>{roleLabels[selected.role]}</span>
                <strong>ID {selected.id}</strong>
                <small>注册于 {formatDate(selected.registeredAt)}</small>
              </div>
              <DetailSection title="账号资料">
                <DetailGrid items={[
                  ['显示昵称', selected.displayName],
                  ['完整手机号', selected.phone],
                  ['账号角色', roleLabels[selected.role]],
                  ['账号状态', selected.enabled ? '正常' : '已停用'],
                  ['登录状态', isLoginLocked(selected.loginLockedUntil) ? `锁定至 ${formatDate(selected.loginLockedUntil!)}` : '未锁定'],
                  ['连续失败次数', String(selected.failedLoginAttempts)],
                  ['骑手接单资格', selected.role === 'COURIER' ? (selected.courierApproved ? '可接单' : '不可接单') : '不适用'],
                  ['注册时间', formatDate(selected.registeredAt)],
                ]} />
              </DetailSection>
              {selected.role !== 'ADMIN' && <DetailSection title="信用与风控">
                {creditLoading && <p className="readonly-note">正在读取信用档案…</p>}
                {creditError && <p className="form-error">{creditError}</p>}
                {credit && <>
                  <DetailGrid items={[
                    ['当前信用分', String(credit.score)],
                    ['信用等级', creditRiskLabels[credit.riskLevel]],
                    ['待审风险信号', String(credit.pendingSignals)],
                    ['累计变动记录', String(credit.totalElements)],
                  ]} />
                  <p className="readonly-note">风险信号本身不扣分；管理员确认并填写依据后才会产生信用变动。</p>
                  {hasPermission(session, 'USER_MANAGE') && <form className="review-form" onSubmit={submitCreditAdjustment}>
                    <label>调整分值<input type="number" min="-100" max="100" required value={creditDelta} onChange={(event) => setCreditDelta(event.target.value)} /></label>
                    <label>调整原因<textarea minLength={5} maxLength={300} required placeholder="填写核实依据或信用恢复原因" value={creditReason} onChange={(event) => setCreditReason(event.target.value)} /></label>
                    <button className="primary-button" disabled={actionLoading} type="submit">{actionLoading ? '正在处理…' : '提交信用调整'}</button>
                  </form>}
                  <div className="credit-events">
                    {credit.events.length === 0 && <p className="readonly-note">暂无信用变动记录。</p>}
                    {credit.events.map((item) => {
                      const reversed = credit.events.some((candidate) => candidate.originalEventId === item.id)
                      return <article key={item.id} className="credit-event-row">
                        <strong>{item.delta > 0 ? '+' : ''}{item.delta} 分 · {creditEventLabels[item.type]}</strong>
                        <span>{item.reason}</span>
                        <small>变动后 {item.balanceAfter} 分 · {item.operatorName} · {formatDate(item.createdAt)}</small>
                        {hasPermission(session, 'USER_MANAGE') && item.type !== 'REVERSAL' && <button className="text-button" disabled={actionLoading || reversed} onClick={() => void reverseCredit(item.id)}>{reversed ? '已撤销' : '撤销此记录'}</button>}
                      </article>
                    })}
                  </div>
                </>}
              </DetailSection>}
              {actionError && <p className="form-error">{actionError}</p>}
              {selected.role === 'ADMIN' ? (
                <p className="readonly-note">管理员账号受保护，不能在用户目录中管理。</p>
              ) : !hasPermission(session, 'USER_MANAGE') ? (
                <p className="readonly-note">当前岗位可查看账号资料，但无权修改账号状态或重置密码。</p>
              ) : (
                <div className="review-actions">
                  <button className={selected.enabled ? 'danger-button account-action-button' : 'primary-button account-action-button'} disabled={actionLoading} onClick={changeAccountStatus}>{actionLoading ? '正在处理…' : selected.enabled ? '停用此账号' : '恢复此账号'}</button>
                  {isLoginLocked(selected.loginLockedUntil) && <button className="primary-button account-action-button" disabled={actionLoading} onClick={unlockLogin}>解除登录锁定</button>}
                  <button className="secondary-button account-action-button" disabled={actionLoading} onClick={() => openPasswordReset(selected)}>重置登录密码</button>
                </div>
              )}
              {selected.role === 'CUSTOMER' && hasPermission(session, 'COUPON_ISSUE') && (
                <DetailSection title="优惠券发放">
                  <p className="readonly-note">固定发放一张1元优惠券，满2元可用，30天内有效。每次操作都会写入审计日志。</p>
                  {couponMessage && <p className="form-success" role="status">{couponMessage}</p>}
                  <button className="primary-button account-action-button" disabled={actionLoading || !selected.enabled} onClick={grantCoupon}>{!selected.enabled ? '账号停用，无法发放' : actionLoading ? '正在发放…' : '发放1元优惠券'}</button>
                </DetailSection>
              )}
            </div>
          </aside>
        </div>
      )}
      {passwordTarget && (
        <div className="drawer-layer">
          <button className="drawer-backdrop" aria-label="关闭密码重置" onClick={() => setPasswordTarget(null)} />
          <aside className="drawer" aria-modal="true" role="dialog">
            <header>
              <div><p className="eyebrow accent">PASSWORD RESET</p><h2>重置{roleLabels[passwordTarget.role]}密码</h2></div>
              <button className="close-button" onClick={() => setPasswordTarget(null)} aria-label="关闭">×</button>
            </header>
            <form className="drawer-content review-form" onSubmit={submitPasswordReset}>
              <p className="readonly-note">正在为 {passwordTarget.displayName} 重置密码。提交后，该账号在所有设备上的登录会立即失效，并解除登录锁定；已停用账号仍保持停用。</p>
              <label>新密码<input autoComplete="new-password" minLength={6} maxLength={72} required type="password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} /></label>
              <label>确认新密码<input autoComplete="new-password" minLength={6} maxLength={72} required type="password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} /></label>
              {passwordError && <p className="form-error">{passwordError}</p>}
              <button className="primary-button account-action-button" disabled={passwordSubmitting || newPassword.length < 6 || confirmPassword.length < 6} type="submit">{passwordSubmitting ? '正在重置…' : '确认重置密码'}</button>
            </form>
          </aside>
        </div>
      )}
    </main>
  )
}

function IssueManagement({ session, onSessionExpired, onOpenOrder, initialStatus = '' }: {
  initialStatus?: OrderIssueHandlingStatus | ''
  session: AuthResponse
  onSessionExpired: () => void
  onOpenOrder: (id: number) => void
}) {
  const [draftQuery, setDraftQuery] = useState('')
  const [query, setQuery] = useState('')
  const [type, setType] = useState<OrderIssueType | ''>('')
  const [handlingStatus, setHandlingStatus] = useState<OrderIssueHandlingStatus | ''>(initialStatus)
  const [overdueOnly, setOverdueOnly] = useState(false)
  const [page, setPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)
  const [issues, setIssues] = useState<AdminIssuePage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<AdminIssueItem | null>(null)
  const [resolution, setResolution] = useState('')
  const [actionLoading, setActionLoading] = useState(false)
  const [actionError, setActionError] = useState('')

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    getIssues(session.token, { query, type, handlingStatus, overdueOnly, page, size: PAGE_SIZE })
      .then((result) => active && setIssues(result))
      .catch((reason: unknown) => {
        if (!active) return
        if (reason instanceof ApiError && reason.status === 401) {
          onSessionExpired()
          return
        }
        setError(reason instanceof Error ? reason.message : '异常工单加载失败')
      })
      .finally(() => active && setLoading(false))
    return () => { active = false }
  }, [handlingStatus, overdueOnly, page, query, reloadKey, session.token, type])

  const pageSummary = useMemo(() => {
    if (!issues || issues.totalElements === 0) return '共 0 条异常反馈'
    const start = issues.page * issues.size + 1
    const end = Math.min(start + issues.items.length - 1, issues.totalElements)
    return `第 ${start}–${end} 条，共 ${issues.totalElements} 条`
  }, [issues])

  const search = (event: FormEvent) => {
    event.preventDefault()
    setPage(0)
    setQuery(draftQuery.trim())
  }

  const claim = async () => {
    if (!selected) return
    setActionLoading(true)
    setActionError('')
    try {
      const updated = await claimIssue(session.token, selected.id)
      setSelected(updated)
      setReloadKey((value) => value + 1)
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onSessionExpired()
      else setActionError(reason instanceof Error ? reason.message : '领取工单失败')
    } finally {
      setActionLoading(false)
    }
  }

  const resolve = async (event: FormEvent) => {
    event.preventDefault()
    if (!selected || !resolution.trim()) return
    setActionLoading(true)
    setActionError('')
    try {
      const updated = await resolveIssue(session.token, selected.id, resolution.trim())
      setSelected(updated)
      setResolution('')
      setReloadKey((value) => value + 1)
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onSessionExpired()
      else setActionError(reason instanceof Error ? reason.message : '解决工单失败')
    } finally {
      setActionLoading(false)
    }
  }

  return (
    <main className="content">
      <header className="page-header">
        <div>
          <p className="eyebrow accent">ISSUE CENTER</p>
          <h1>异常工单</h1>
          <p className="muted">集中查看订单异常反馈，并快速跳转核对完整订单证据。</p>
        </div>
        <button className="secondary-button" onClick={() => setReloadKey((value) => value + 1)}>刷新数据</button>
      </header>

      <section className="summary-row" aria-label="异常反馈摘要">
        <article><span>当前结果</span><strong>{issues?.totalElements ?? '—'}</strong><small>条反馈</small></article>
        <article className={issues?.overdueTotal ? 'alert' : ''}><span>超时工单</span><strong>{issues?.overdueTotal ?? '—'}</strong><small>条需优先处理</small></article>
        <article><span>状态筛选</span><strong className="text-value">{handlingStatus ? issueHandlingStatusLabels[handlingStatus] : '全部状态'}</strong><small>处理进度</small></article>
      </section>

      <section className="orders-card">
        <form className="filters issue-filters" onSubmit={search}>
          <label className="search-field">
            <span>搜索</span>
            <input placeholder="订单号、反馈人、手机号或问题描述" value={draftQuery} onChange={(event) => setDraftQuery(event.target.value)} />
          </label>
          <label>
            <span>问题类型</span>
            <select value={type} onChange={(event) => {
              setPage(0)
              setType(event.target.value as OrderIssueType | '')
            }}>
              <option value="">全部类型</option>
              {Object.entries(issueTypeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
          <label>
            <span>处理状态</span>
            <select value={handlingStatus} onChange={(event) => {
              setPage(0)
              setHandlingStatus(event.target.value as OrderIssueHandlingStatus | '')
            }}>
              <option value="">全部状态</option>
              {Object.entries(issueHandlingStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
          <label>
            <span>时效状态</span>
            <select value={overdueOnly ? 'overdue' : ''} onChange={(event) => {
              setPage(0)
              setOverdueOnly(event.target.value === 'overdue')
            }}>
              <option value="">全部时效</option>
              <option value="overdue">仅看超时</option>
            </select>
          </label>
          <button className="primary-button filter-button" type="submit">查询反馈</button>
        </form>

        {!error && issues && issues.overdueTotal > 0 && <p className="issue-overdue-alert" role="status">有 {issues.overdueTotal} 个工单已超时，请优先处理。</p>}
        {error && <ErrorState message={error} onRetry={() => setReloadKey((value) => value + 1)} />}
        {!error && loading && <div className="table-state">正在加载异常反馈…</div>}
        {!error && !loading && issues?.items.length === 0 && <div className="table-state">暂无符合条件的异常反馈</div>}
        {!error && !loading && issues && issues.items.length > 0 && (
          <div className="table-scroll">
            <table className="issue-table">
              <thead><tr><th>关联订单</th><th>问题类型</th><th>处理状态</th><th>负责人</th><th>反馈内容</th><th>提交时间</th><th></th></tr></thead>
              <tbody>
                {issues.items.map((issue) => (
                  <tr key={issue.id} className={issue.overdue ? 'issue-overdue-row' : ''}>
                    <td><strong>{issue.orderNo}</strong><small>{statusLabels[issue.orderStatus]}</small></td>
                    <td><span className="status issue-status">{issueTypeLabels[issue.type]}</span></td>
                    <td><span className={`status handling-${issue.handlingStatus.toLowerCase()}`}>{issueHandlingStatusLabels[issue.handlingStatus]}</span>
                      {issue.overdue && <small className="overdue-status">已超时 {issue.overdueMinutes} 分钟</small>}</td>
                    <td>{issue.assigneeName ?? <span className="muted">未领取</span>}</td>
                    <td className="description-cell">{issue.description}</td>
                    <td>{formatDate(issue.createdAt)}</td>
                    <td><button className="text-button" onClick={() => {
                      setSelected(issue)
                      setResolution('')
                      setActionError('')
                    }}>查看详情</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <footer className="pagination">
          <span>{pageSummary}</span>
          <div>
            <button disabled={!issues || issues.page === 0} onClick={() => setPage((value) => value - 1)}>上一页</button>
            <span>{issues && issues.totalPages > 0 ? `${issues.page + 1} / ${issues.totalPages}` : '0 / 0'}</span>
            <button disabled={!issues || issues.page + 1 >= issues.totalPages} onClick={() => setPage((value) => value + 1)}>下一页</button>
          </div>
        </footer>
      </section>

      {selected && (
        <div className="drawer-layer">
          <button className="drawer-backdrop" aria-label="关闭异常详情" onClick={() => setSelected(null)} />
          <aside className="drawer" aria-modal="true" role="dialog">
            <header>
              <div><p className="eyebrow accent">ISSUE DETAIL</p><h2>{issueTypeLabels[selected.type]}</h2></div>
              <button className="close-button" onClick={() => setSelected(null)} aria-label="关闭">×</button>
            </header>
            <div className="drawer-content">
              <div className="detail-hero issue-hero">
                <span className="status issue-status">{issueTypeLabels[selected.type]}</span>
                <strong>{selected.orderNo}</strong>
                <small>{statusLabels[selected.orderStatus]} · {formatDate(selected.createdAt)}</small>
              </div>
              <DetailSection title="反馈内容">
                <p className="issue-description">{selected.description}</p>
              </DetailSection>
              <DetailSection title="反馈人与关联订单">
                <DetailGrid items={[
                  ['反馈人', selected.reporterName],
                  ['完整手机号', selected.reporterPhone],
                  ['关联订单', selected.orderNo],
                  ['订单状态', statusLabels[selected.orderStatus]],
                  ['提交时间', formatDate(selected.createdAt)],
                  ['处理状态', issueHandlingStatusLabels[selected.handlingStatus]],
                  ['时效状态', selected.overdue ? `已超时 ${selected.overdueMinutes} 分钟` : selected.slaDeadlineAt ? '时限内' : '已结束'],
                  ['处理时限', selected.slaDeadlineAt ? formatDate(selected.slaDeadlineAt) : '—'],
                  ['负责人', selected.assigneeName ?? '未领取'],
                  ['处理时间', selected.handledAt ? formatDate(selected.handledAt) : '—'],
                ]} />
              </DetailSection>
              {selected.resolution && <DetailSection title="处理结果"><p className="issue-description">{selected.resolution}</p></DetailSection>}
              {actionError && <p className="form-error">{actionError}</p>}
              {selected.handlingStatus === 'PENDING' && hasPermission(session, 'ISSUE_MANAGE') && (
                <button className="primary-button open-order-button" disabled={actionLoading} onClick={claim}>
                  {actionLoading ? '正在领取…' : '领取此工单'}
                </button>
              )}
              {selected.handlingStatus === 'PROCESSING' && selected.assigneeId === session.user.id && hasPermission(session, 'ISSUE_MANAGE') && (
                <form className="rejection-form" onSubmit={resolve}>
                  <label>处理结果<textarea maxLength={500} required value={resolution} onChange={(event) => setResolution(event.target.value)} /></label>
                  <button className="primary-button open-order-button" disabled={actionLoading || !resolution.trim()} type="submit">
                    {actionLoading ? '正在提交…' : '标记为已解决'}
                  </button>
                </form>
              )}
              {hasPermission(session, 'ORDER_VIEW') && <button className="primary-button open-order-button" onClick={() => onOpenOrder(selected.orderId)}>打开完整订单详情</button>}
              {selected.handlingStatus === 'PROCESSING' && selected.assigneeId !== session.user.id && (
                <p className="readonly-note">该工单已由 {selected.assigneeName} 领取，只有负责人可以提交处理结果。</p>
              )}
            </div>
          </aside>
        </div>
      )}
    </main>
  )
}

function FeedbackManagement({ session, onSessionExpired, initialStatus = '' }: {
  initialStatus?: FeedbackHandlingStatus | ''
  session: AuthResponse
  onSessionExpired: () => void
}) {
  const [draftQuery, setDraftQuery] = useState('')
  const [query, setQuery] = useState('')
  const [type, setType] = useState<FeedbackType | ''>('')
  const [handlingStatus, setHandlingStatus] = useState<FeedbackHandlingStatus | ''>(initialStatus)
  const [page, setPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)
  const [feedback, setFeedback] = useState<AdminFeedbackPage | null>(null)
  const [selected, setSelected] = useState<AdminFeedbackItem | null>(null)
  const [resolution, setResolution] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [actionLoading, setActionLoading] = useState(false)
  const [actionError, setActionError] = useState('')

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    getFeedback(session.token, { query, type, handlingStatus, page, size: PAGE_SIZE })
      .then((result) => active && setFeedback(result))
      .catch((reason: unknown) => {
        if (!active) return
        if (reason instanceof ApiError && reason.status === 401) {
          onSessionExpired()
          return
        }
        setError(reason instanceof Error ? reason.message : '意见反馈加载失败')
      })
      .finally(() => active && setLoading(false))
    return () => { active = false }
  }, [handlingStatus, page, query, reloadKey, session.token, type])

  const pageSummary = useMemo(() => {
    if (!feedback || feedback.totalElements === 0) return '共 0 条反馈'
    const start = feedback.page * feedback.size + 1
    const end = Math.min(start + feedback.items.length - 1, feedback.totalElements)
    return `第 ${start}–${end} 条，共 ${feedback.totalElements} 条`
  }, [feedback])

  const claim = async () => {
    if (!selected) return
    setActionLoading(true)
    setActionError('')
    try {
      const updated = await claimFeedback(session.token, selected.id)
      setSelected(updated)
      setReloadKey((value) => value + 1)
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onSessionExpired()
      else setActionError(reason instanceof Error ? reason.message : '领取反馈失败')
    } finally {
      setActionLoading(false)
    }
  }

  const resolve = async (event: FormEvent) => {
    event.preventDefault()
    if (!selected || !resolution.trim()) return
    setActionLoading(true)
    setActionError('')
    try {
      const updated = await resolveFeedback(session.token, selected.id, resolution.trim())
      setSelected(updated)
      setResolution('')
      setReloadKey((value) => value + 1)
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onSessionExpired()
      else setActionError(reason instanceof Error ? reason.message : '反馈处理失败')
    } finally {
      setActionLoading(false)
    }
  }

  return <main className="content">
    <header className="page-header">
      <div>
        <p className="eyebrow accent">USER FEEDBACK</p>
        <h1>意见反馈</h1>
        <p className="muted">统一接收、领取和处理用户从 App 提交的建议与账号问题。</p>
      </div>
      <button className="secondary-button" onClick={() => setReloadKey((value) => value + 1)}>刷新数据</button>
    </header>

    <section className="summary-row" aria-label="意见反馈摘要">
      <article><span>当前结果</span><strong>{feedback?.totalElements ?? '—'}</strong><small>条反馈</small></article>
      <article><span>本页待处理</span><strong>{feedback?.items.filter((item) => item.handlingStatus === 'PENDING').length ?? 0}</strong><small>条反馈</small></article>
      <article><span>状态筛选</span><strong className="text-value">{handlingStatus ? issueHandlingStatusLabels[handlingStatus] : '全部状态'}</strong><small>处理进度</small></article>
    </section>

    <section className="orders-card">
      <form className="filters issue-filters" onSubmit={(event) => {
        event.preventDefault()
        setPage(0)
        setQuery(draftQuery.trim())
      }}>
        <label className="search-field"><span>搜索</span><input placeholder="用户昵称、手机号或反馈内容" value={draftQuery} onChange={(event) => setDraftQuery(event.target.value)} /></label>
        <label><span>反馈类型</span><select value={type} onChange={(event) => {
          setPage(0)
          setType(event.target.value as FeedbackType | '')
        }}><option value="">全部类型</option>{Object.entries(feedbackTypeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label><span>处理状态</span><select value={handlingStatus} onChange={(event) => {
          setPage(0)
          setHandlingStatus(event.target.value as FeedbackHandlingStatus | '')
        }}><option value="">全部状态</option>{Object.entries(issueHandlingStatusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <button className="primary-button filter-button" type="submit">查询反馈</button>
      </form>

      {error && <ErrorState message={error} onRetry={() => setReloadKey((value) => value + 1)} />}
      {!error && loading && <div className="table-state">正在加载意见反馈…</div>}
      {!error && !loading && feedback?.items.length === 0 && <div className="table-state">暂无符合条件的意见反馈</div>}
      {!error && !loading && feedback && feedback.items.length > 0 && <div className="table-scroll"><table>
        <thead><tr><th>反馈用户</th><th>反馈类型</th><th>处理状态</th><th>负责人</th><th>反馈内容</th><th>提交时间</th><th></th></tr></thead>
        <tbody>{feedback.items.map((item) => <tr key={item.id}>
          <td><strong>{item.userName}</strong><small>{maskPhone(item.userPhone)}</small></td>
          <td><span className="status issue-status">{feedbackTypeLabels[item.type]}</span></td>
          <td><span className={`status handling-${item.handlingStatus.toLowerCase()}`}>{issueHandlingStatusLabels[item.handlingStatus]}</span></td>
          <td>{item.assigneeName ?? <span className="muted">未领取</span>}</td>
          <td className="description-cell">{item.content}</td><td>{formatDate(item.createdAt)}</td>
          <td><button className="text-button" onClick={() => {
            setSelected(item)
            setResolution('')
            setActionError('')
          }}>查看详情</button></td>
        </tr>)}</tbody>
      </table></div>}
      <footer className="pagination"><span>{pageSummary}</span><div>
        <button disabled={!feedback || feedback.page === 0} onClick={() => setPage((value) => value - 1)}>上一页</button>
        <span>{feedback && feedback.totalPages > 0 ? `${feedback.page + 1} / ${feedback.totalPages}` : '0 / 0'}</span>
        <button disabled={!feedback || feedback.page + 1 >= feedback.totalPages} onClick={() => setPage((value) => value + 1)}>下一页</button>
      </div></footer>
    </section>

    {selected && <div className="drawer-layer">
      <button className="drawer-backdrop" aria-label="关闭反馈详情" onClick={() => setSelected(null)} />
      <aside className="drawer" aria-modal="true" role="dialog">
        <header><div><p className="eyebrow accent">FEEDBACK DETAIL</p><h2>{feedbackTypeLabels[selected.type]}</h2></div><button className="close-button" onClick={() => setSelected(null)} aria-label="关闭">×</button></header>
        <div className="drawer-content">
          <div className="detail-hero issue-hero"><span className="status issue-status">{feedbackTypeLabels[selected.type]}</span><strong>反馈 #{selected.id}</strong><small>{formatDate(selected.createdAt)}</small></div>
          <DetailSection title="反馈内容"><p className="issue-description">{selected.content}</p></DetailSection>
          <DetailSection title="处理信息"><DetailGrid items={[
            ['反馈用户', selected.userName], ['完整手机号', selected.userPhone],
            ['处理状态', issueHandlingStatusLabels[selected.handlingStatus]], ['负责人', selected.assigneeName ?? '未领取'],
            ['提交时间', formatDate(selected.createdAt)], ['处理时间', selected.handledAt ? formatDate(selected.handledAt) : '—'],
          ]} /></DetailSection>
          {selected.resolution && <DetailSection title="客服回复"><p className="issue-description">{selected.resolution}</p></DetailSection>}
          {actionError && <p className="form-error">{actionError}</p>}
          {selected.handlingStatus === 'PENDING' && hasPermission(session, 'FEEDBACK_MANAGE') && <button className="primary-button open-order-button" disabled={actionLoading} onClick={claim}>{actionLoading ? '正在领取…' : '领取此反馈'}</button>}
          {selected.handlingStatus === 'PROCESSING' && selected.assigneeId === session.user.id && hasPermission(session, 'FEEDBACK_MANAGE') && <form className="rejection-form" onSubmit={resolve}>
            <label>对用户回复<textarea maxLength={500} required placeholder="填写用户可见的处理说明" value={resolution} onChange={(event) => setResolution(event.target.value)} /></label>
            <button className="primary-button open-order-button" disabled={actionLoading || !resolution.trim()} type="submit">{actionLoading ? '正在提交…' : '回复并标记为已解决'}</button>
          </form>}
          {selected.handlingStatus === 'PROCESSING' && selected.assigneeId !== session.user.id && <p className="readonly-note">该反馈已由 {selected.assigneeName} 领取，只有负责人可以提交处理结果。</p>}
        </div>
      </aside>
    </div>}
  </main>
}

function AdminAccountManagement({ session, onSessionExpired }: {
  session: AuthResponse
  onSessionExpired: () => void
}) {
  const [accounts, setAccounts] = useState<AdminAccountItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)
  const [displayName, setDisplayName] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [adminRole, setAdminRole] = useState<AdminRole>('OPERATIONS')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [changingId, setChangingId] = useState<number | null>(null)
  const [passwordTarget, setPasswordTarget] = useState<AdminAccountItem | null>(null)
  const [mfaTarget, setMfaTarget] = useState<AdminAccountItem | null>(null)
  const [mfaReason, setMfaReason] = useState('')
  const [mfaGrant, setMfaGrant] = useState('')
  const [mfaError, setMfaError] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [passwordError, setPasswordError] = useState('')

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    getAdminAccounts(session.token)
      .then((result) => active && setAccounts(result))
      .catch((reason: unknown) => {
        if (!active) return
        if (reason instanceof ApiError && reason.status === 401) onSessionExpired()
        else setError(reason instanceof Error ? reason.message : '管理员账号加载失败')
      })
      .finally(() => active && setLoading(false))
    return () => { active = false }
  }, [reloadKey, session.token])

  const create = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setFormError('')
    try {
      await createAdminAccount(session.token, { phone, password, displayName: displayName.trim(), adminRole })
      setDisplayName('')
      setPhone('')
      setPassword('')
      setAdminRole('OPERATIONS')
      setReloadKey((value) => value + 1)
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onSessionExpired()
      else setFormError(reason instanceof Error ? reason.message : '管理员创建失败')
    } finally {
      setSubmitting(false)
    }
  }

  const updateRole = async (account: AdminAccountItem, role: AdminRole) => {
    if (account.adminRole === role || account.id === session.user.id) return
    if (!window.confirm(`确认将 ${account.displayName} 调整为“${adminRoleLabels[role]}”？该账号需要重新登录。`)) return
    setChangingId(account.id)
    setError('')
    try {
      await changeAdminRole(session.token, account.id, role)
      setReloadKey((value) => value + 1)
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onSessionExpired()
      else setError(reason instanceof Error ? reason.message : '岗位调整失败')
    } finally {
      setChangingId(null)
    }
  }

  const changeAccountStatus = async (account: AdminAccountItem) => {
    if (account.id === session.user.id) return
    const verb = account.enabled ? '停用' : '恢复'
    if (!window.confirm(`确认${verb}管理员“${account.displayName}”？${account.enabled ? '该账号将立即退出登录。' : ''}`)) return
    setChangingId(account.id)
    setError('')
    try {
      if (account.enabled) await suspendAdminAccount(session.token, account.id)
      else await restoreAdminAccount(session.token, account.id)
      setReloadKey((value) => value + 1)
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onSessionExpired()
      else setError(reason instanceof Error ? reason.message : '管理员状态修改失败')
    } finally {
      setChangingId(null)
    }
  }

  const unlockAccountLogin = async (account: AdminAccountItem) => {
    if (account.id === session.user.id || !isLoginLocked(account.loginLockedUntil)) return
    if (!window.confirm(`确认解除管理员“${account.displayName}”的登录锁定？`)) return
    setChangingId(account.id)
    setError('')
    try {
      await unlockAdminLogin(session.token, account.id)
      setReloadKey((value) => value + 1)
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onSessionExpired()
      else setError(reason instanceof Error ? reason.message : '管理员登录解锁失败')
    } finally {
      setChangingId(null)
    }
  }

  const submitPasswordReset = async (event: FormEvent) => {
    event.preventDefault()
    if (!passwordTarget) return
    setSubmitting(true)
    setPasswordError('')
    try {
      await resetAdminPassword(session.token, passwordTarget.id, newPassword)
      setPasswordTarget(null)
      setNewPassword('')
      setReloadKey((value) => value + 1)
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onSessionExpired()
      else setPasswordError(reason instanceof Error ? reason.message : '密码重置失败')
    } finally {
      setSubmitting(false)
    }
  }

  const submitMfaReset = async (event: FormEvent) => {
    event.preventDefault()
    if (!mfaTarget) return
    setSubmitting(true); setMfaError('')
    try {
      const result = await resetAdminMfa(session.token, mfaTarget.id, mfaReason.trim())
      setMfaGrant(result.resetGrant)
      setReloadKey(value => value + 1)
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onSessionExpired()
      else setMfaError(reason instanceof Error ? reason.message : '认证器重置失败')
    } finally { setSubmitting(false) }
  }

  return <main className="content">
    <header className="page-header">
      <div><p className="eyebrow accent">ADMIN ACCESS</p><h1>管理员账号</h1><p className="muted">创建独立后台账号并按岗位授予最小必要权限。</p></div>
      <button className="secondary-button" onClick={() => setReloadKey((value) => value + 1)}>刷新数据</button>
    </header>

    <section className="orders-card admin-account-card">
      <form className="admin-create-form" onSubmit={create}>
        <label><span>姓名</span><input maxLength={48} required value={displayName} onChange={(event) => setDisplayName(event.target.value)} /></label>
        <label><span>手机号</span><input inputMode="numeric" maxLength={11} pattern="1[0-9]{10}" required value={phone} onChange={(event) => setPhone(event.target.value)} /></label>
        <label><span>初始密码</span><input minLength={8} maxLength={72} required type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></label>
        <label><span>管理员岗位</span><select value={adminRole} onChange={(event) => setAdminRole(event.target.value as AdminRole)}>{Object.entries(adminRoleLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <button className="primary-button filter-button" disabled={submitting} type="submit">{submitting ? '正在创建…' : '创建管理员'}</button>
        {formError && <p className="form-error admin-form-error">{formError}</p>}
      </form>

      {error && <ErrorState message={error} onRetry={() => setReloadKey((value) => value + 1)} />}
      {!error && loading && <div className="table-state">正在加载管理员账号…</div>}
      {!error && !loading && <div className="table-scroll"><table>
        <thead><tr><th>管理员</th><th>手机号</th><th>岗位</th><th>状态</th><th>创建时间</th><th>岗位调整</th><th>账号操作</th></tr></thead>
        <tbody>{accounts.map((account) => <tr key={account.id}>
          <td><strong>{account.displayName}</strong><small>{account.id === session.user.id ? '当前账号' : `ID ${account.id}`}</small></td>
          <td>{maskPhone(account.phone)}</td><td><strong>{adminRoleLabels[account.adminRole]}</strong><small>{account.permissions.length} 项权限</small></td>
          <td><span className={`status account-${!account.enabled ? 'suspended' : isLoginLocked(account.loginLockedUntil) ? 'locked' : 'enabled'}`}>{!account.enabled ? '已停用' : isLoginLocked(account.loginLockedUntil) ? '登录锁定' : '正常'}</span><small>{account.failedLoginAttempts > 0 ? `失败 ${account.failedLoginAttempts} 次` : ''}</small></td>
          <td>{formatDate(account.createdAt)}</td><td>
            <select className="role-select" disabled={account.id === session.user.id || changingId === account.id} value={account.adminRole} onChange={(event) => updateRole(account, event.target.value as AdminRole)}>
              {Object.entries(adminRoleLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </td><td>{account.id === session.user.id ? <span className="muted">当前账号</span> : <span className="account-row-actions"><button className="text-button" disabled={changingId === account.id} onClick={() => changeAccountStatus(account)}>{account.enabled ? '停用账号' : '恢复账号'}</button><button className="text-button" onClick={() => {
            setPasswordTarget(account)
            setNewPassword('')
            setPasswordError('')
          }}>重置密码</button>{account.adminRole !== 'SUPER_ADMIN' && <button className="text-button" onClick={() => { setMfaTarget(account); setMfaReason(''); setMfaGrant(''); setMfaError('') }}>重置认证器</button>}{isLoginLocked(account.loginLockedUntil) && <button className="text-button" disabled={changingId === account.id} onClick={() => unlockAccountLogin(account)}>解除锁定</button>}</span>}</td>
        </tr>)}</tbody>
      </table></div>}
    </section>

    {mfaTarget && <div className="drawer-layer">
      <button className="drawer-backdrop" aria-label="关闭认证器重置" onClick={() => setMfaTarget(null)} />
      <aside className="drawer compact-drawer" aria-modal="true" role="dialog" aria-label="重置管理员认证器">
        <header><h2>重置管理员认证器</h2><button className="close-button" onClick={() => setMfaTarget(null)} aria-label="关闭">×</button></header>
        <div className="drawer-content">
          {mfaGrant ? <section className="login-card">
            <p>已撤销 {mfaTarget.displayName} 的会话和旧认证器。请当面将以下凭证交给本人，24 小时内用密码和此凭证重新绑定。</p>
            <label>一次性重置凭证<textarea aria-label="一次性重置凭证" className="mfa-reset-grant" readOnly rows={3} value={mfaGrant} /></label>
            <p className="readonly-note">凭证只在此处显示一次。丢失或过期后须重新人工核验并重置。</p>
          </section> : <form className="login-card" onSubmit={submitMfaReset}>
            <p className="readonly-note">请先人工核实 {mfaTarget.displayName} 的身份。本操作撤销该账号所有会话、旧认证器和恢复码。</p>
            <label>身份核验记录及重置原因<textarea required maxLength={200} rows={4} value={mfaReason} onChange={event => setMfaReason(event.target.value)} /></label>
            {mfaError && <p className="form-error" role="alert">{mfaError}</p>}
            <button className="primary-button login-button" disabled={submitting || !mfaReason.trim()} type="submit">{submitting ? '正在重置…' : '已核验身份，确认重置'}</button>
          </form>}
        </div>
      </aside>
    </div>}
    {passwordTarget && <div className="drawer-layer">
      <button className="drawer-backdrop" aria-label="关闭密码重置" onClick={() => setPasswordTarget(null)} />
      <aside className="drawer compact-drawer" aria-modal="true" role="dialog">
        <header><div><p className="eyebrow accent">PASSWORD RESET</p><h2>重置管理员密码</h2></div><button className="close-button" onClick={() => setPasswordTarget(null)} aria-label="关闭">×</button></header>
        <form className="drawer-content rejection-form" onSubmit={submitPasswordReset}>
          <p className="readonly-note">正在为 {passwordTarget.displayName} 重置密码。提交后，该账号已登录的设备会立即退出。</p>
          <label>新密码<input autoComplete="new-password" minLength={8} maxLength={72} required type="password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} /></label>
          {passwordError && <p className="form-error">{passwordError}</p>}
          <button className="primary-button account-action-button" disabled={submitting || newPassword.length < 8} type="submit">{submitting ? '正在重置…' : '确认重置密码'}</button>
        </form>
      </aside>
    </div>}
  </main>
}

function SessionSecurityDialog({ session, onClose, onSessionExpired }: {
  session: AuthResponse
  onClose: () => void
  onSessionExpired: () => void
}) {
  const [items, setItems] = useState<LoginSession[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [changingId, setChangingId] = useState<number | null>(null)

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      setItems(await getLoginSessions(session.token))
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) {
        onSessionExpired()
        return
      }
      setError(reason instanceof Error ? reason.message : '登录设备加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [session.token])

  const revoke = async (item: LoginSession) => {
    setChangingId(item.id)
    setError('')
    try {
      await revokeLoginSession(session.token, item.id)
      await load()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '设备下线失败')
    } finally {
      setChangingId(null)
    }
  }

  const revokeOthers = async () => {
    setChangingId(-1)
    setError('')
    try {
      await revokeOtherLoginSessions(session.token)
      await load()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '其他设备下线失败')
    } finally {
      setChangingId(null)
    }
  }

  return <div className="drawer-layer">
    <button className="drawer-backdrop" aria-label="关闭登录设备" onClick={onClose} />
    <aside className="drawer compact-drawer" aria-modal="true" role="dialog" aria-label="登录设备">
      <header><div><p className="eyebrow accent">ACCOUNT SECURITY</p><h2>登录设备</h2></div><button className="close-button" onClick={onClose} aria-label="关闭">×</button></header>
      <div className="drawer-content security-sessions">
        <p className="readonly-note">仅显示尚未过期的会话。发现陌生设备时，可以立即将其下线。</p>
        {loading && <div className="drawer-state">正在读取登录设备…</div>}
        {error && <p className="form-error" role="alert">{error}</p>}
        {!loading && items.map(item => <article className="security-session-card" key={item.id}>
          <div><strong>{item.deviceName}</strong>{item.current && <span className="status account-enabled">当前设备</span>}</div>
          <span>{item.clientType === 'ADMIN_WEB' ? '运营后台' : item.clientType === 'ANDROID' ? 'Android 客户端' : '未知客户端'}</span>
          <small>登录时间 {formatDate(item.createdAt)} · 有效期至 {formatDate(item.expiresAt)}</small>
          {!item.current && <button className="text-button" disabled={changingId !== null} onClick={() => void revoke(item)}>{changingId === item.id ? '正在下线…' : '下线此设备'}</button>}
        </article>)}
        {!loading && items.every(item => item.current) && <p className="empty-inline">当前没有其他登录设备</p>}
        {!loading && items.some(item => !item.current) && <button className="primary-button" disabled={changingId !== null} onClick={() => void revokeOthers()}>{changingId === -1 ? '正在下线…' : '下线其他全部设备'}</button>}
      </div>
    </aside>
  </div>
}

function PushDeliveryManagement({ session, onSessionExpired }: {
  session: AuthResponse
  onSessionExpired: () => void
}) {
  const [status, setStatus] = useState<PushDeliveryStatus>('DEAD')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PushDeliveryOverview | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    getPushDeliveries(session.token, status, page, PAGE_SIZE)
      .then(result => { if (active) setData(result) })
      .catch((reason: unknown) => {
        if (!active) return
        if (reason instanceof ApiError && reason.status === 401) { onSessionExpired(); return }
        setError(reason instanceof Error ? reason.message : '推送任务加载失败')
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [page, revision, session.token, status])

  const deliveries = data?.deliveries
  return <main className="content">
    <header className="page-header">
      <div><p className="eyebrow accent">PUSH DELIVERY</p><h1>推送告警</h1>
        <p className="muted">查看订单与骑手认证通知的自动重试状态；失败不会回滚业务。</p></div>
      <button className="secondary-button" disabled={loading} onClick={() => setRevision(value => value + 1)}>刷新</button>
    </header>
    <section className="metric-grid compact-metrics">
      <article><span>需人工关注</span><strong>{data?.dead ?? 0}</strong><small>5 次失败</small></article>
      <article><span>等待重试</span><strong>{data?.pending ?? 0}</strong><small>按退避时间执行</small></article>
      <article><span>发送处理中</span><strong>{data?.processing ?? 0}</strong><small>超时后自动恢复</small></article>
    </section>
    <section className="data-section">
      <div className="table-toolbar">
        <label>任务状态<select value={status} onChange={event => { setStatus(event.target.value as PushDeliveryStatus); setPage(0) }}>
          <option value="DEAD">需人工关注</option><option value="PENDING">等待重试</option>
          <option value="PROCESSING">发送处理中</option><option value="SENT">发送成功</option>
        </select></label>
      </div>
      {error && <ErrorState message={error} onRetry={() => setRevision(value => value + 1)} />}
      {loading && !deliveries && <p className="muted">正在读取推送任务…</p>}
      {!loading && !error && deliveries?.items.length === 0 && <p className="muted">当前状态没有推送任务。</p>}
      {deliveries && deliveries.items.length > 0 && <div className="table-scroll"><table><thead><tr>
        <th>任务</th><th>业务目标</th><th>接收账号</th><th>尝试次数</th><th>最近状态</th><th>失败原因</th>
      </tr></thead><tbody>{deliveries.items.map(item => <tr key={item.id}>
        <td>#{item.id}<small>{item.title}</small></td><td>{item.orderId ? `订单 #${item.orderId}` : '骑手认证'}</td><td>#{item.recipientId}</td>
        <td>{item.attempts}/5</td><td>{formatDate(item.updatedAt)}<small>{item.status === 'PENDING' ? `下次 ${formatDate(item.nextAttemptAt)}` : item.status}</small></td>
        <td>{item.lastError ?? '—'}</td>
      </tr>)}</tbody></table></div>}
      <footer className="pagination"><span>共 {deliveries?.totalElements ?? 0} 条</span><div>
        <button disabled={!deliveries || deliveries.page === 0 || loading} onClick={() => setPage(value => value - 1)}>上一页</button>
        <span>{deliveries && deliveries.totalPages > 0 ? `${deliveries.page + 1} / ${deliveries.totalPages}` : '0 / 0'}</span>
        <button disabled={!deliveries || deliveries.page + 1 >= deliveries.totalPages || loading} onClick={() => setPage(value => value + 1)}>下一页</button>
      </div></footer>
    </section>
  </main>
}

function AuditManagement({ session, onSessionExpired }: {
  session: AuthResponse
  onSessionExpired: () => void
}) {
  const [draftQuery, setDraftQuery] = useState('')
  const [query, setQuery] = useState('')
  const [action, setAction] = useState<AdminAuditAction | ''>('')
  const [page, setPage] = useState(0)
  const [reloadKey, setReloadKey] = useState(0)
  const [logs, setLogs] = useState<AdminAuditPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    getAuditLogs(session.token, { query, action, page, size: PAGE_SIZE })
      .then((result) => active && setLogs(result))
      .catch((reason: unknown) => {
        if (!active) return
        if (reason instanceof ApiError && reason.status === 401) {
          onSessionExpired()
          return
        }
        setError(reason instanceof Error ? reason.message : '操作日志加载失败')
      })
      .finally(() => active && setLoading(false))
    return () => { active = false }
  }, [action, page, query, reloadKey, session.token])

  const pageSummary = useMemo(() => {
    if (!logs || logs.totalElements === 0) return '共 0 条操作记录'
    const start = logs.page * logs.size + 1
    const end = Math.min(start + logs.items.length - 1, logs.totalElements)
    return `第 ${start}–${end} 条，共 ${logs.totalElements} 条`
  }, [logs])

  const search = (event: FormEvent) => {
    event.preventDefault()
    setPage(0)
    setQuery(draftQuery.trim())
  }

  return (
    <main className="content">
      <header className="page-header">
        <div>
          <p className="eyebrow accent">AUDIT TRAIL</p>
          <h1>操作日志</h1>
          <p className="muted">查看管理员执行的关键写操作，记录不可在后台修改或删除。</p>
        </div>
        <button className="secondary-button" onClick={() => setReloadKey((value) => value + 1)}>刷新数据</button>
      </header>

      <section className="summary-row" aria-label="操作日志摘要">
        <article><span>当前结果</span><strong>{logs?.totalElements ?? '—'}</strong><small>条操作</small></article>
        <article><span>本页通过</span><strong>{logs?.items.filter((log) => log.action === 'COURIER_CERTIFICATION_APPROVED').length ?? 0}</strong><small>次认证</small></article>
        <article><span>操作筛选</span><strong className="text-value">{action ? auditActionLabels[action] : '全部操作'}</strong><small>日志类型</small></article>
      </section>

      <section className="orders-card">
        <form className="filters" onSubmit={search}>
          <label className="search-field">
            <span>搜索</span>
            <input placeholder="管理员、手机号或操作摘要" value={draftQuery} onChange={(event) => setDraftQuery(event.target.value)} />
          </label>
          <label>
            <span>操作类型</span>
            <select value={action} onChange={(event) => {
              setPage(0)
              setAction(event.target.value as AdminAuditAction | '')
            }}>
              <option value="">全部操作</option>
              {Object.entries(auditActionLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
          <button className="primary-button filter-button" type="submit">查询日志</button>
        </form>

        {error && <ErrorState message={error} onRetry={() => setReloadKey((value) => value + 1)} />}
        {!error && loading && <div className="table-state">正在加载操作日志…</div>}
        {!error && !loading && logs?.items.length === 0 && <div className="table-state">暂无符合条件的操作日志</div>}
        {!error && !loading && logs && logs.items.length > 0 && (
          <div className="table-scroll">
            <table className="audit-table">
              <thead><tr><th>操作时间</th><th>管理员</th><th>操作类型</th><th>操作摘要</th><th>目标记录</th></tr></thead>
              <tbody>
                {logs.items.map((log) => (
                  <tr key={log.id}>
                    <td>{formatDate(log.createdAt)}</td>
                    <td><strong>{log.administratorName}</strong><small>{maskPhone(log.administratorPhone)}</small></td>
                    <td><span className={`status audit-${log.action === 'COURIER_CERTIFICATION_REJECTED' || log.action === 'USER_SUSPENDED' ? 'rejected' : 'approved'}`}>{auditActionLabels[log.action]}</span></td>
                    <td>{log.summary}</td>
                    <td><strong>{auditTargetLabels[log.targetType]}</strong><small>ID {log.targetId}</small></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <footer className="pagination">
          <span>{pageSummary}</span>
          <div>
            <button disabled={!logs || logs.page === 0} onClick={() => setPage((value) => value - 1)}>上一页</button>
            <span>{logs && logs.totalPages > 0 ? `${logs.page + 1} / ${logs.totalPages}` : '0 / 0'}</span>
            <button disabled={!logs || logs.page + 1 >= logs.totalPages} onClick={() => setPage((value) => value + 1)}>下一页</button>
          </div>
        </footer>
      </section>
    </main>
  )
}

function CertificationStatus({ status }: { status: CourierCertificationStatus }) {
  return <span className={`status certification-${status.toLowerCase()}`}>{certificationStatusLabels[status]}</span>
}

function OrderDrawer({ detail, error, loading, onClose, onRetry, session }: {
  detail: AdminOrderDetail | null
  error: string
  loading: boolean
  onClose: () => void
  onRetry: () => void
  session: AuthResponse
}) {
  const [actionLoading, setActionLoading] = useState(false)
  const [actionError, setActionError] = useState('')
  const [rejectReason, setRejectReason] = useState('')
  const [completionReason, setCompletionReason] = useState('')
  const [archiveReason, setArchiveReason] = useState('')

  const runOrderAction = async (action: () => Promise<unknown>) => {
    setActionLoading(true)
    setActionError('')
    try {
      await action()
      setRejectReason('')
      setCompletionReason('')
      setArchiveReason('')
      onRetry()
    } catch (reason) {
      setActionError(reason instanceof Error ? reason.message : '操作失败')
    } finally {
      setActionLoading(false)
    }
  }

  return (
    <div className="drawer-layer">
      <button className="drawer-backdrop" aria-label="关闭订单详情" onClick={onClose} />
      <aside className="drawer" aria-modal="true" role="dialog">
        <header>
          <div><p className="eyebrow accent">ORDER DETAIL</p><h2>{detail?.order.orderNo ?? '订单详情'}</h2></div>
          <button className="close-button" onClick={onClose} aria-label="关闭">×</button>
        </header>
        {loading && <div className="drawer-state">正在加载详情…</div>}
        {error && <ErrorState message={error} onRetry={onRetry} />}
        {!loading && !error && detail && (
          <div className="drawer-content">
            {actionError && <p className="form-error" role="alert">{actionError}</p>}
            <div className="detail-hero">
              <span className={`status status-${detail.order.status.toLowerCase()}`}>{statusLabels[detail.order.status]}</span>
              <strong>{formatMoney(detail.order.feeCents)}</strong>
              <small>{detail.order.merchant} · {serviceLabels[detail.order.serviceType]}</small>
            </div>

            <DetailSection title="配送信息">
              <DetailGrid items={[
                ['取件点', detail.pickupLocation],
                ['取件码', detail.pickupCode],
                ['送达位置', detail.order.destination],
                ['创建时间', formatDate(detail.order.createdAt)],
              ]} />
            </DetailSection>

            <DetailSection title="参与账号">
              <AccountCard label="下单用户" user={detail.order.customer} />
              {detail.order.courier ? <AccountCard label="配送骑手" user={detail.order.courier} /> : <p className="empty-inline">暂未分配骑手</p>}
            </DetailSection>

            <DetailSection title="费用明细">
              <DetailGrid items={[
                ['原始服务费', formatMoney(detail.originalFeeCents)],
                ['优惠金额', `-${formatMoney(detail.discountCents)}`],
                ['付款方式', detail.order.paymentMethod === 'CASH_ON_DELIVERY'
                  ? '货到付款'
                  : detail.order.paymentMethod === 'PERSONAL_QR_MANUAL' ? '个人收款码·人工核款' : '微信 APP 支付'],
                ['结清状态', detail.order.paymentStatus === 'PAID'
                  ? `已结清 ${formatMoney(detail.order.paidCents)}`
                  : `待结清 ${formatMoney(detail.order.feeCents - detail.order.paidCents)}`],
                ['优惠券', detail.couponTitle ?? '未使用'],
                ['月卡抵扣', detail.membershipUsed ? (detail.membershipPlanTitle ?? '已使用') : '未使用'],
              ]} />
            </DetailSection>

            {detail.order.paymentMethod === 'PERSONAL_QR_MANUAL' && <DetailSection title="人工核款与骑手结算">
              <DetailGrid items={[
                ['核款状态', manualPaymentStatusLabel(detail.order.manualPaymentStatus)],
                ['付款人备注', detail.payerReference ?? '—'],
                ['通知付款', detail.paymentRequestedAt ? formatDate(detail.paymentRequestedAt) : '—'],
                ['用户提交', detail.paymentSubmittedAt ? formatDate(detail.paymentSubmittedAt) : '—'],
                ['核款人员', detail.paymentReviewerName ?? '—'],
                ['核款时间', detail.paymentReviewedAt ? formatDate(detail.paymentReviewedAt) : '—'],
                ['核款说明', detail.paymentReviewNote ?? '—'],
                ['骑手结算', courierSettlementStatusLabel(detail.order.courierSettlementStatus)],
                ['结算登记', detail.courierSettledAt ? `${formatDate(detail.courierSettledAt)} · ${detail.courierSettlementReviewerName ?? '管理员'}` : '—'],
              ]} />
              {hasPermission(session, 'PAYMENT_REVIEW') && detail.order.manualPaymentStatus === 'SUBMITTED' && <>
                <button className="primary-button open-order-button" disabled={actionLoading} onClick={() => {
                  if (window.confirm(`确认已收到 ${formatMoney(detail.order.feeCents)} 吗？确认后用户才能查看收货码。`)) {
                    void runOrderAction(() => confirmManualPayment(session.token, detail.order.id))
                  }
                }}>{actionLoading ? '处理中…' : '确认到账'}</button>
                <form className="rejection-form" onSubmit={(event) => {
                  event.preventDefault()
                  void runOrderAction(() => rejectManualPayment(session.token, detail.order.id, rejectReason.trim()))
                }}>
                  <label>驳回原因<textarea required maxLength={200} value={rejectReason} onChange={(event) => setRejectReason(event.target.value)} /></label>
                  <button className="danger-button" disabled={actionLoading || !rejectReason.trim()} type="submit">驳回付款申报</button>
                </form>
              </>}
              {hasPermission(session, 'PAYMENT_REVIEW') && detail.order.courierSettlementStatus === 'PENDING' &&
                <button className="secondary-button open-order-button" disabled={actionLoading} onClick={() => {
                  if (window.confirm(`确认已向骑手结算 ${formatMoney(detail.order.feeCents)} 吗？此操作只登记结果，不会自动转账。`)) {
                    void runOrderAction(() => confirmCourierSettlement(session.token, detail.order.id))
                  }
                }}>{actionLoading ? '处理中…' : '登记为已结算给骑手'}</button>}
            </DetailSection>}

            <DetailSection title={`订单进度 · ${detail.events.length}`}>
              {detail.events.length === 0 ? <p className="empty-inline">暂无状态记录</p> : (
                <ol className="timeline">
                  {detail.events.map((event) => <li key={event.id}><i /><div><strong>{statusLabels[event.status]}</strong><span>{event.actor.displayName} · {formatDate(event.createdAt)}</span></div></li>)}
                </ol>
              )}
            </DetailSection>

            <DetailSection title={`异常反馈 · ${detail.issues.length}`}>
              {detail.issues.length === 0 ? <p className="empty-inline">暂无异常反馈</p> : detail.issues.map((issue) => (
                <article className="evidence-card issue-card" key={issue.id}>
                  <strong>{issueTypeLabel(issue.type)}</strong>
                  <p>{issue.description}</p>
                  <small>{issue.reporter.displayName} · {formatDate(issue.createdAt)}</small>
                </article>
              ))}
            </DetailSection>

            <DetailSection title={`履约凭证 · ${detail.proofs.length}`}>
              {detail.proofs.length === 0 ? <p className="empty-inline">暂无凭证记录</p> : detail.proofs.map((proof) => (
                <article className="evidence-card" key={proof.id}>
                  <strong>{proof.type === 'SIZE_REVIEW' ? '规格核验照片' : proof.type === 'PICKUP' ? '取餐凭证' : '送达凭证'}</strong>
                  <span>{proof.originalFileName}</span>
                  <small>{formatFileSize(proof.sizeBytes)} · {proof.uploader.displayName} · {formatDate(proof.createdAt)}</small>
                  {proof.type === 'DELIVERY' && hasPermission(session, 'ORDER_FORCE_COMPLETE') &&
                    <DeliveryProofPreview token={session.token} orderId={detail.order.id} />}
                </article>
              ))}
            </DetailSection>

            {hasPermission(session, 'ORDER_FORCE_COMPLETE') && detail.order.status === 'PICKED_UP' && !detail.order.archivedAt &&
              <DetailSection title="管理员人工完成">
                <p className="readonly-note">仅在已核对送达凭证和确认收款后使用；此操作跳过收货码，不会自动收款或退款，完成后计入已完成订单。</p>
                {detail.proofs.some((proof) => proof.type === 'DELIVERY')
                  && detail.order.paymentStatus === 'PAID'
                  && detail.order.paidCents >= detail.order.feeCents
                  && detail.order.courier
                  && (detail.order.paymentMethod !== 'PERSONAL_QR_MANUAL' || detail.order.feeCents === 0
                    || detail.order.manualPaymentStatus === 'CONFIRMED')
                  ? <form className="rejection-form" onSubmit={(event) => {
                      event.preventDefault()
                      if (window.confirm(`确认人工完成订单 ${detail.order.orderNo}？请确保送达和收款均已核实。`)) {
                        void runOrderAction(() => forceCompleteOrder(session.token, detail.order.id, completionReason.trim()))
                      }
                    }}>
                      <label>操作原因<textarea required minLength={5} maxLength={200} value={completionReason}
                        onChange={(event) => setCompletionReason(event.target.value)} placeholder="填写核实情况及无法使用收货码的原因" /></label>
                      <button className="primary-button open-order-button" type="submit"
                        disabled={actionLoading || completionReason.trim().length < 5}>
                        {actionLoading ? '处理中…' : '确认人工完成'}
                      </button>
                    </form>
                  : <p className="empty-inline">暂不可操作：需有送达凭证、已确认收款且订单已有骑手；货到付款未确认收款时不能人工完成。</p>}
              </DetailSection>}

            {hasPermission(session, 'ORDER_ARCHIVE') && (detail.order.status === 'COMPLETED'
              || detail.order.status === 'CANCELLED') &&
              <DetailSection title="订单归档">
                <p className="readonly-note">归档只从管理后台默认列表隐藏；用户订单、支付、履约凭证和操作日志均保留。</p>
                {detail.order.archivedAt ? <>
                  <p className="empty-inline">归档时间：{formatDate(detail.order.archivedAt)}</p>
                  <button className="secondary-button open-order-button" disabled={actionLoading}
                    onClick={() => void runOrderAction(() => unarchiveOrder(session.token, detail.order.id))}>
                    {actionLoading ? '处理中…' : '恢复到未归档列表'}
                  </button>
                </> : detail.order.paymentStatus === 'REFUNDING'
                  || detail.order.courierSettlementStatus === 'PENDING'
                  || detail.order.status === 'COMPLETED' && detail.order.paymentStatus !== 'PAID'
                  ? <p className="empty-inline">仍有待处理的收款、退款或骑手结算，暂不能归档。</p>
                  : <form className="rejection-form" onSubmit={(event) => {
                      event.preventDefault()
                      if (window.confirm(`将订单 ${detail.order.orderNo} 从默认管理列表归档隐藏？记录不会被删除。`)) {
                        void runOrderAction(() => archiveOrder(session.token, detail.order.id, archiveReason.trim()))
                      }
                    }}>
                      <label>归档原因<textarea required minLength={5} maxLength={200} value={archiveReason}
                        onChange={(event) => setArchiveReason(event.target.value)} placeholder="填写归档原因" /></label>
                      <button className="secondary-button open-order-button" type="submit"
                        disabled={actionLoading || archiveReason.trim().length < 5}>
                        {actionLoading ? '处理中…' : '归档并隐藏'}
                      </button>
                    </form>}
              </DetailSection>}

            <DetailSection title="位置与评价">
              <DetailGrid items={[
                ['骑手位置', detail.courierLatitude !== null && detail.courierLongitude !== null ? `${detail.courierLatitude.toFixed(6)}, ${detail.courierLongitude.toFixed(6)}` : '未共享'],
                ['位置更新时间', detail.courierLocationUpdatedAt ? formatDate(detail.courierLocationUpdatedAt) : '—'],
                ['用户评分', detail.ratingScore ? `${detail.ratingScore} 星` : '未评价'],
                ['评价内容', detail.ratingComment ?? '—'],
              ]} />
            </DetailSection>
          </div>
        )}
      </aside>
    </div>
  )
}

function StudentCardPreview({ token, certificationId }: { token: string; certificationId: number }) {
  const [url, setUrl] = useState<string | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    const controller = new AbortController()
    let objectUrl: string | undefined
    void getStudentCardPhoto(token, certificationId, controller.signal).then((blob) => {
      if (controller.signal.aborted) return
      objectUrl = URL.createObjectURL(blob); setUrl(objectUrl); setError('')
    }).catch((reason) => {
      if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : '照片加载失败')
    })
    return () => { controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [token, certificationId])
  if (error) return <p className="form-error">{error}</p>
  return url ? <img src={url} alt="骑手提交的学生证照片" style={{ maxWidth: '100%', maxHeight: 320 }} />
    : <p className="muted">正在加载学生证照片…</p>
}

function DeliveryProofPreview({ token, orderId }: { token: string; orderId: number }) {
  const [url, setUrl] = useState<string | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    const controller = new AbortController()
    let objectUrl: string | undefined
    void getDeliveryProofPhoto(token, orderId, controller.signal).then((blob) => {
      if (controller.signal.aborted) return
      objectUrl = URL.createObjectURL(blob)
      setUrl(objectUrl)
      setError('')
    }).catch((reason) => {
      if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : '送达凭证加载失败')
    })
    return () => { controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [token, orderId])
  if (error) return <p className="form-error">{error}</p>
  return url ? <img src={url} alt="骑手上传的送达凭证" style={{ maxWidth: '100%', maxHeight: 320 }} />
    : <p className="muted">正在加载送达凭证…</p>
}

function DetailSection({ title, children }: { title: string; children: ReactNode }) {
  return <section className="detail-section"><h3>{title}</h3>{children}</section>
}

function DetailGrid({ items }: { items: Array<[string, string]> }) {
  return <dl className="detail-grid">{items.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
}

function AccountCard({ label, user }: { label: string; user: AdminOrderDetail['order']['customer'] }) {
  return <article className="account-card"><span className="avatar">{user.displayName.slice(0, 1)}</span><div><small>{label}</small><strong>{user.displayName}</strong><span>{maskPhone(user.phone)}</span></div></article>
}

function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return <div className="error-state" role="alert"><strong>暂时无法读取数据</strong><span>{message}</span><button onClick={onRetry}>重新加载</button></div>
}

function formatMoney(cents: number) {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(cents / 100)
}

function manualPaymentStatusLabel(status: AdminOrderDetail['order']['manualPaymentStatus']) {
  return {
    NOT_REQUESTED: '待骑手到达',
    AWAITING_PAYMENT: '待用户付款',
    SUBMITTED: '待管理员核款',
    CONFIRMED: '已确认到账',
    REJECTED: '核款未通过',
  }[status]
}

function courierSettlementStatusLabel(status: AdminOrderDetail['order']['courierSettlementStatus']) {
  return {
    NOT_APPLICABLE: '尚未生成结算',
    PENDING: '待人工结算',
    SETTLED: '已登记结算',
  }[status]
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}

function isLoginLocked(lockedUntil: string | null) {
  return lockedUntil !== null && new Date(lockedUntil).getTime() > Date.now()
}

function formatFileSize(bytes: number) {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function maskPhone(phone: string) {
  return phone.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2')
}

function issueTypeLabel(type: string) {
  return issueTypeLabels[type as OrderIssueType] ?? type
}
