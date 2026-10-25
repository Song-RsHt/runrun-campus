import { useEffect, useState } from 'react'
import { ApiError, dispatchOrder, getCommandCenter, getDispatchCandidates, getPlatformSettings, updatePlatformSettings } from './api'
import type { AuthResponse, CommandQueue, CommandSnapshot, DispatchCandidates, PlatformSettings } from './types'

const queueLabels: Record<CommandQueue, string> = {
  LATE_WAITING: '待接超时', LATE_DELIVERY: '配送超时', WAITING: '待接订单', ACTIVE: '配送进行中',
}

export function CommandCenter({ session, onOpenOrder, onSessionExpired }: {
  session: AuthResponse
  onOpenOrder: (id: number) => void
  onSessionExpired: () => void
}) {
  const [queue, setQueue] = useState<CommandQueue>('LATE_WAITING')
  const [orderPage, setOrderPage] = useState(0)
  const [courierPage, setCourierPage] = useState(0)
  const [snapshot, setSnapshot] = useState<CommandSnapshot | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [dispatchOrderId, setDispatchOrderId] = useState<number | null>(null)
  const [success, setSuccess] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    let timer: number | undefined
    const load = async () => {
      setLoading(true)
      try {
        const result = await getCommandCenter(session.token, queue, orderPage, courierPage, controller.signal)
        if (controller.signal.aborted) return
        setSnapshot(result)
        setError('')
      } catch (reason) {
        if (controller.signal.aborted) return
        if (reason instanceof ApiError && reason.status === 401) {
          onSessionExpired()
          return
        }
        setError(reason instanceof Error ? reason.message : '指挥台加载失败')
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false)
          if (autoRefresh) timer = window.setTimeout(load, 30_000)
        }
      }
    }
    void load()
    return () => { controller.abort(); window.clearTimeout(timer) }
  }, [session.token, queue, orderPage, courierPage, revision, autoRefresh])

  const orders = snapshot?.orders
  const couriers = snapshot?.couriers
  const counts: Record<CommandQueue, number> | null = orders ? {
    WAITING: orders.waiting, LATE_WAITING: orders.lateWaiting,
    ACTIVE: orders.active, LATE_DELIVERY: orders.lateDelivery,
  } : null

  return <section className="command-center" aria-label="运营指挥台">
    <div className="command-toolbar">
      <div><p className="eyebrow accent">LIVE OPERATIONS · V2.3</p><h2>履约预警、运力与智能推荐</h2>
        <p className="muted" role="status">{snapshot ? `数据时间：${new Date(snapshot.generatedAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })}` : '正在读取数据'}{loading ? ' · 更新中…' : ''}</p></div>
      <div className="command-controls">
        <label><input type="checkbox" checked={autoRefresh} onChange={(event) => setAutoRefresh(event.target.checked)} />每 30 秒刷新</label>
        <button className="secondary-button" disabled={loading} onClick={() => setRevision((value) => value + 1)}>刷新指挥台</button>
      </div>
    </div>
    {error && <p className="command-error" role="alert">{error}。{snapshot ? '当前展示上次成功数据，请刷新后再核对。' : '请点击刷新指挥台重试。'}</p>}
    {success && <p className="command-success" role="status">{success}</p>}
    {snapshot && <p className="muted">待接满 {snapshot.waitMinutes} 分钟、接单后满 {snapshot.deliveryMinutes} 分钟未完成时提醒。仅供运营跟进；“估算”表示历史记录缺失，按创建时间计算。</p>}
    {counts && <div className="dashboard-grid">
      {(Object.keys(queueLabels) as CommandQueue[]).map((key) => <button key={key}
        className={`metric-card ${key.startsWith('LATE') && counts[key] > 0 ? 'alert' : ''} ${queue === key ? 'command-selected' : ''}`}
        aria-pressed={queue === key} onClick={() => { setQueue(key); setOrderPage(0) }}>
        <span>{queueLabels[key]}</span><strong>{counts[key]}</strong><small>查看对应订单</small>
      </button>)}
    </div>}
    {orders && <section className="dashboard-section">
      <h3>{queueLabels[queue]} · {orders.queue === queue ? orders.totalElements : '—'} 单</h3>
      <p className="muted">按等待或履约时间从长到短排列，点击订单查看详情与异常记录。</p>
      {orders.queue !== queue || (loading && orders.page !== orderPage) ? <p role="status">正在切换队列…</p> : <>
        <div className="table-scroll"><table><thead><tr><th>订单</th><th>骑手 / 状态</th><th>送达位置</th><th>已等待 / 已履约</th><th>处理</th></tr></thead>
          <tbody>{orders.items.map((order) => <tr key={order.id}>
            <td>{order.orderNo}</td><td>{order.courierName ?? '待接单'}<small>{order.status === 'ACCEPTED' ? '已接单' : order.status === 'PICKED_UP' ? '配送中' : order.status === 'PENDING_REASSIGNMENT' ? '待运营改派' : '等待骑手接单'}</small></td>
            <td>{order.destination}</td><td>{Math.max(0, Math.floor((Date.parse(snapshot!.generatedAt) - Date.parse(order.stageStart)) / 60_000))} 分钟{order.estimated ? '（估算）' : ''}</td>
            <td><div className="command-row-actions">
              {(order.status === 'PENDING_ASSIGNMENT' || order.status === 'PENDING_REASSIGNMENT') && session.user.permissions.includes('ORDER_DISPATCH') &&
                <button className="primary-button" onClick={() => { setSuccess(''); setDispatchOrderId(order.id) }}>{order.status === 'PENDING_REASSIGNMENT' ? '确认改派' : '调度派单'}</button>}
              <button className="secondary-button" onClick={() => onOpenOrder(order.id)}>查看详情</button>
            </div></td>
          </tr>)}</tbody></table></div>
        {orders.items.length === 0 && <p className="table-state">当前没有{queueLabels[queue]}订单</p>}
        <div className="command-pagination"><button disabled={loading || orders.page === 0} onClick={() => setOrderPage(orders.page - 1)}>上一页</button>
          <span>{orders.totalElements === 0 ? '0 / 0' : `${orders.page + 1} / ${Math.ceil(orders.totalElements / orders.size)}`}</span>
          <button disabled={loading || (orders.page + 1) * orders.size >= orders.totalElements} onClick={() => setOrderPage(orders.page + 1)}>下一页</button></div>
      </>}
    </section>}
    {couriers && <section className="dashboard-section">
      <h3>在线运力 · {couriers.online} 人上线 / {couriers.idle} 人无在途订单</h3>
      <p className="muted">仅统计启用、认证通过且主动上线的骑手；上线状态不代表实时定位。按在途单量从少到多、上线时间从早到晚排列。</p>
      <div className="command-couriers">{couriers.items.map((courier) => <article key={courier.id}>
        <strong>{courier.displayName}</strong><small>骑手 #{courier.id} · 上线于 {formatTime(courier.onlineSince)}</small><span>{courier.activeOrders === 0 ? '暂无在途订单' : `${courier.activeOrders} 单进行中`}</span>
      </article>)}</div>
      {couriers.online === 0 && <p className="table-state">暂无上线骑手{orders && orders.waiting > 0 ? '，请及时跟进待接订单' : ''}</p>}
      <div className="command-pagination"><button disabled={loading || couriers.page === 0} onClick={() => setCourierPage(couriers.page - 1)}>上一页</button>
        <span>{couriers.online === 0 ? '0 / 0' : `${couriers.page + 1} / ${Math.ceil(couriers.online / couriers.size)}`}</span>
        <button disabled={loading || (couriers.page + 1) * couriers.size >= couriers.online} onClick={() => setCourierPage(couriers.page + 1)}>下一页</button></div>
    </section>}
    {session.user.permissions.includes('PLATFORM_SETTINGS_MANAGE') &&
      <PlatformSettingsPanel session={session} onChanged={() => setRevision((value) => value + 1)} />}
    {dispatchOrderId !== null && <DispatchDialog session={session} orderId={dispatchOrderId}
      onClose={() => setDispatchOrderId(null)} onSessionExpired={onSessionExpired}
      onDispatched={(message) => { setDispatchOrderId(null); setSuccess(message); setRevision((value) => value + 1) }} />}
  </section>
}

function PlatformSettingsPanel({ session, onChanged }: { session: AuthResponse; onChanged: () => void }) {
  const [draft, setDraft] = useState<PlatformSettings | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  useEffect(() => {
    let active = true
    void getPlatformSettings(session.token).then((settings) => {
      if (active) { setDraft(settings); setError('') }
    }).catch((reason) => {
      if (active) setError(reason instanceof Error ? reason.message : '运营设置加载失败')
    })
    return () => { active = false }
  }, [session.token])

  const save = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!draft) return
    setSaving(true); setError(''); setSuccess('')
    try {
      const updated = await updatePlatformSettings(session.token, draft)
      setDraft(updated); setSuccess('运营设置已保存'); onChanged()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '运营设置保存失败')
    } finally { setSaving(false) }
  }

  return <section className="dashboard-section" aria-label="运营设置">
    <h3>运营设置</h3>
    <p className="muted">营业时间仅用于展示；订单与工单超时阈值只产生运营提醒，不自动取消或处理。接单上限由服务端强制执行。</p>
    {error && <p className="command-error" role="alert">{error}</p>}
    {success && <p className="command-success" role="status">{success}</p>}
    {!draft ? <p>正在读取设置…</p> : <form onSubmit={(event) => void save(event)} className="command-controls">
      <label>展示营业时间<input required maxLength={80} value={draft.businessHours}
        onChange={(event) => setDraft({ ...draft, businessHours: event.target.value })} /></label>
      <label>待接超时（分钟）<input required type="number" min={1} max={1440} value={draft.waitMinutes}
        onChange={(event) => setDraft({ ...draft, waitMinutes: Number(event.target.value) })} /></label>
      <label>配送超时（分钟）<input required type="number" min={1} max={1440} value={draft.deliveryMinutes}
        onChange={(event) => setDraft({ ...draft, deliveryMinutes: Number(event.target.value) })} /></label>
      <label>骑手同时接单上限<input required type="number" min={1} max={10} value={draft.maxActiveOrders}
        onChange={(event) => setDraft({ ...draft, maxActiveOrders: Number(event.target.value) })} /></label>
      <label>工单待领取超时（分钟）<input required type="number" min={1} max={1440} value={draft.issuePendingMinutes}
        onChange={(event) => setDraft({ ...draft, issuePendingMinutes: Number(event.target.value) })} /></label>
      <label>工单处理中超时（分钟）<input required type="number" min={1} max={1440} value={draft.issueProcessingMinutes}
        onChange={(event) => setDraft({ ...draft, issueProcessingMinutes: Number(event.target.value) })} /></label>
      <button className="primary-button" disabled={saving} type="submit">{saving ? '保存中…' : '保存设置'}</button>
    </form>}
  </section>
}

function DispatchDialog({ session, orderId, onClose, onDispatched, onSessionExpired }: {
  session: AuthResponse
  orderId: number
  onClose: () => void
  onDispatched: (message: string) => void
  onSessionExpired: () => void
}) {
  const [page, setPage] = useState(0)
  const [data, setData] = useState<DispatchCandidates | null>(null)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    setLoading(true)
    void getDispatchCandidates(session.token, orderId, page).then((result) => {
      if (!active) return
      setData(result); setPage(result.page); setSelectedId(null); setError('')
    }).catch((reason) => {
      if (!active) return
      if (reason instanceof ApiError && reason.status === 401) { onSessionExpired(); return }
      setError(reason instanceof Error ? reason.message : '可用骑手加载失败')
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [session.token, orderId, page, onSessionExpired])

  const selected = data?.items.find((candidate) => candidate.courierId === selectedId)
  const confirm = async () => {
    if (!selected) return
    setSubmitting(true); setError('')
    try {
      const result = await dispatchOrder(session.token, orderId, selected.courierId)
      onDispatched(`订单 ${result.orderNo} 已派给 ${result.courierName}`)
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) { onSessionExpired(); return }
      setError(reason instanceof Error ? reason.message : '派单失败，请刷新后重试')
    } finally { setSubmitting(false) }
  }

  return <div className="dispatch-layer" role="dialog" aria-modal="true" aria-labelledby="dispatch-title">
    <button className="dispatch-backdrop" aria-label="关闭派单窗口" onClick={onClose} />
    <section className="dispatch-dialog">
      <header><div><p className="eyebrow accent">MANUAL DISPATCH</p><h2 id="dispatch-title">为 {data?.orderNo ?? '订单'} 选择骑手</h2></div>
        <button className="close-button" aria-label="关闭" onClick={onClose}>×</button></header>
      <p className="muted">推荐综合当前负载、上线时长、完成量、同类经验和有效评分；不使用位置数据，也不会自动派单。确认时服务端会再次校验。</p>
      {error && <p className="command-error" role="alert">{error}</p>}
      {loading && <p role="status">正在读取可用骑手…</p>}
      {!loading && data && <>
        <div className="dispatch-candidates">{data.items.map((candidate) => <button key={candidate.courierId}
          className={selectedId === candidate.courierId ? 'selected' : ''} aria-pressed={selectedId === candidate.courierId}
          onClick={() => setSelectedId(candidate.courierId)}>
          <span className="dispatch-candidate-main"><strong>{candidate.displayName}{candidate.recommended && <b>首选推荐</b>}</strong>
            <small>骑手 #{candidate.courierId} · {candidate.onlineSince ? `上线于 ${formatTime(candidate.onlineSince)}` : '上线时间未知'}</small>
            <span className="recommendation-reasons">{candidate.reasons.map((reason) => <i key={reason}>{reason}</i>)}</span>
          </span>
          <em><strong>{candidate.recommendationScore}</strong><small>推荐分</small></em>
        </button>)}</div>
        {data.totalElements === 0 && <p className="table-state">当前没有可派单的在线骑手</p>}
        <div className="command-pagination"><button disabled={loading || data.page === 0} onClick={() => setPage(data.page - 1)}>上一页</button>
          <span>{data.totalElements === 0 ? '0 / 0' : `${data.page + 1} / ${Math.ceil(data.totalElements / data.size)}`}</span>
          <button disabled={loading || (data.page + 1) * data.size >= data.totalElements} onClick={() => setPage(data.page + 1)}>下一页</button></div>
      </>}
      <footer><span>{selected ? `确认将订单派给 ${selected.displayName}？系统推荐仅供参考。` : '请先选择一名骑手，最终决定由运营确认'}</span>
        <button className="secondary-button" disabled={submitting} onClick={onClose}>取消</button>
        <button className="primary-button" disabled={!selected || submitting} onClick={() => void confirm()}>{submitting ? '派单中…' : '确认派单'}</button></footer>
    </section>
  </div>
}

function formatTime(value: string) {
  return new Date(value).toLocaleTimeString('zh-CN', { timeZone: 'Asia/Shanghai', hour: '2-digit', minute: '2-digit' })
}
