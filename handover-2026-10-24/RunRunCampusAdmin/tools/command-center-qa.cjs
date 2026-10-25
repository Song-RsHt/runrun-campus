// Synthetic API fixtures: verifies browser behavior without using real accounts or orders.
// Run against local Vite on 5179 with Playwright available on NODE_PATH.
const { chromium } = require('playwright')
const assert = require('node:assert/strict')

async function main() {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
    const errors = []
    page.on('pageerror', (error) => { errors.push(error.message); console.error('Browser error:', error.message) })
    const now = '2026-09-13T04:00:00Z'
    let failure = false
    let empty = false
    let role = 'OPERATIONS'
    let commandRequests = 0
    let dispatchRequests = 0
    let sessionRequests = 0
    let otherSessionActive = true
    let couponRequests = 0
    const permissions = ['DASHBOARD_VIEW', 'ORDER_VIEW', 'ORDER_DISPATCH', 'COURIER_VIEW', 'COURIER_REVIEW',
      'USER_VIEW', 'COUPON_ISSUE', 'ISSUE_VIEW', 'ISSUE_MANAGE', 'FEEDBACK_VIEW', 'FEEDBACK_MANAGE']
    await page.addInitScript(({ permissions }) => sessionStorage.setItem('runrun-campus-admin-session', JSON.stringify({
      token: 'synthetic-qa-only', user: { id: 1, phone: '19900000000', displayName: '测试运营',
        role: 'ADMIN', adminRole: 'OPERATIONS', permissions },
    })), { permissions })
    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url())
      let body
      if (url.pathname === '/api/v1/auth/sessions' && route.request().method() === 'GET') {
        sessionRequests++
        body = [
          { id: 101, deviceName: '运营后台浏览器', clientType: 'ADMIN_WEB', createdAt: now,
            expiresAt: '2026-09-20T04:00:00Z', current: true },
          ...(otherSessionActive ? [{ id: 102, deviceName: '测试 Android 手机', clientType: 'ANDROID',
            createdAt: '2026-09-12T04:00:00Z', expiresAt: '2026-09-19T04:00:00Z', current: false }] : []),
        ]
      } else if (url.pathname === '/api/v1/auth/sessions/others' && route.request().method() === 'DELETE') {
        otherSessionActive = false
        body = { revoked: 1 }
      } else if (url.pathname === '/api/v1/admin/command-center') {
        commandRequests++
        if (failure) return route.fulfill({ status: 503, json: { message: '测试网络中断' } })
        const queue = url.searchParams.get('queue')
        const pageIndex = Number(url.searchParams.get('orderPage'))
        const waiting = queue.includes('WAITING')
        body = { generatedAt: now, waitMinutes: 10, deliveryMinutes: 30,
          orders: { waiting: empty ? 0 : 21, lateWaiting: empty ? 0 : 21, active: empty ? 0 : 1,
            lateDelivery: empty ? 0 : 1, queue, page: pageIndex, size: 20,
            totalElements: empty ? 0 : waiting ? 21 : 1,
            items: empty ? [] : [{ id: pageIndex + 11, orderNo: `QA-${waiting ? 'WAIT' : 'DELIVERY'}-${pageIndex}`,
              status: waiting ? 'PENDING_ASSIGNMENT' : 'PICKED_UP', destination: '测试宿舍 1 栋',
              courierName: waiting ? null : '测试骑手', stageStart: '2026-09-13T03:20:00Z', estimated: false }] },
          couriers: role === 'SUPPORT' ? null : { online: empty ? 0 : 2, idle: empty ? 0 : 1, page: 0, size: 20,
            items: empty ? [] : [
              { id: 2, displayName: '测试骑手甲', activeOrders: 0, onlineSince: '2026-09-13T02:30:00Z' },
              { id: 3, displayName: '测试骑手乙', activeOrders: 2, onlineSince: '2026-09-13T03:10:00Z' },
            ] },
        }
      } else if (url.pathname === '/api/v1/admin/dashboard') {
        body = { totalUsers: 30, totalCouriers: 5, totalOrders: 200, pendingOrders: 21, activeDeliveries: 1,
          pendingCertifications: 2, pendingIssues: 3, processingIssues: 1, pendingFeedback: 4, processingFeedback: 2 }
      } else if (url.pathname === '/api/v1/admin/users' && route.request().method() === 'GET') {
        body = { items: [{ id: 5, displayName: '测试同学', phone: '19900000000', role: 'CUSTOMER',
          enabled: true, courierApproved: false, registeredAt: now, failedLoginAttempts: 0,
          loginLockedUntil: null }], page: 0, size: 20, totalElements: 1, totalPages: 1 }
      } else if (url.pathname === '/api/v1/admin/users/5/coupons/one-yuan' && route.request().method() === 'POST') {
        couponRequests++
        body = { couponId: 51, userId: 5, displayName: '测试同学', title: '管理员赠送1元券',
          discountCents: 100, minimumOrderCents: 200, expiresAt: '2026-10-14T04:00:00Z' }
      } else if (/\/orders\/\d+\/dispatch-candidates$/.test(url.pathname)) {
        body = { orderId: 11, orderNo: 'QA-WAIT-0', generatedAt: now, page: 0, size: 20, totalElements: 2,
          items: [
            { courierId: 2, displayName: '测试骑手甲', activeOrders: 0, onlineSince: '2026-09-13T02:30:00Z',
              completedOrders: 28, matchedCompletedOrders: 12, averageRating: 4.8, ratingCount: 16,
              recommendationScore: 91, recommended: true,
              reasons: ['当前无在途订单', '已完成 12 单同类订单', '历史评分 4.8（16 次）', '已上线 90 分钟'] },
            { courierId: 3, displayName: '测试骑手乙', activeOrders: 2, onlineSince: '2026-09-13T03:10:00Z',
              completedOrders: 40, matchedCompletedOrders: 18, averageRating: 4.9, ratingCount: 22,
              recommendationScore: 29, recommended: false,
              reasons: ['当前有 2 单进行中', '已完成 18 单同类订单', '历史评分 4.9（22 次）', '已上线 50 分钟'] },
          ] }
      } else if (/\/orders\/\d+\/dispatch$/.test(url.pathname) && route.request().method() === 'POST') {
        dispatchRequests++
        body = { orderId: 11, orderNo: 'QA-WAIT-0', status: 'ACCEPTED', courierId: 2,
          courierName: '测试骑手甲', dispatchedAt: now }
      } else if (/\/orders\/\d+$/.test(url.pathname)) {
        body = { order: { id: 11, orderNo: 'QA-DETAIL', status: 'PENDING_ASSIGNMENT', serviceType: 'LOBBY',
          merchant: '测试站点', destination: '测试宿舍', feeCents: 200, createdAt: now, updatedAt: now,
          customer: { id: 5, displayName: '测试同学', phone: '19900000000', role: 'CUSTOMER' }, courier: null },
          pickupCode: 'TEST', pickupLocation: '测试站点', originalFeeCents: 200, discountCents: 0,
          events: [], issues: [], proofs: [], membershipUsed: false, courierLatitude: null,
          courierLongitude: null, courierLocationUpdatedAt: null, ratingScore: null, ratingComment: null, ratedAt: null }
      } else if (/\/(orders|couriers|issues|feedback)$/.test(url.pathname)) {
        body = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
      } else {
        errors.push(`Unexpected API: ${url.pathname}`)
        return route.fulfill({ status: 404, json: { message: 'Unknown QA route' } })
      }
      return route.fulfill({ json: body })
    })
    await page.goto('http://127.0.0.1:5179')
    await page.getByText('QA-WAIT-0', { exact: true }).waitFor()
    await page.getByRole('button', { name: '设备', exact: true }).click()
    const sessionDialog = page.getByRole('dialog', { name: '登录设备' })
    await sessionDialog.getByText('测试 Android 手机', { exact: true }).waitFor()
    assert.equal(await sessionDialog.getByText('当前设备', { exact: true }).count(), 1)
    await page.screenshot({ path: 'tools/qa-output/v2.2-admin-session-center.png', fullPage: true })
    await sessionDialog.getByRole('button', { name: '下线其他全部设备', exact: true }).click()
    await sessionDialog.getByText('当前没有其他登录设备', { exact: true }).waitFor()
    assert(sessionRequests >= 2)
    assert.equal(otherSessionActive, false)
    await sessionDialog.getByRole('button', { name: '关闭', exact: true }).click()
    await page.getByRole('navigation').getByRole('button', { name: '用户管理', exact: false }).click()
    await page.getByText('测试同学', { exact: true }).waitFor()
    await page.getByRole('button', { name: '查看详情', exact: true }).click()
    const userDialog = page.getByRole('dialog')
    await userDialog.getByRole('button', { name: '发放1元优惠券', exact: true }).waitFor()
    page.once('dialog', (dialog) => dialog.accept())
    await userDialog.getByRole('button', { name: '发放1元优惠券', exact: true }).click()
    await userDialog.getByText(/已发放“管理员赠送1元券”/).waitFor()
    assert.equal(couponRequests, 1)
    await page.screenshot({ path: 'tools/qa-output/v2.3-admin-coupon-grant.png', fullPage: true })
    await userDialog.getByRole('button', { name: '关闭', exact: true }).click()
    await page.getByRole('navigation').getByRole('button', { name: '运营指挥台', exact: false }).click()
    await page.getByText('QA-WAIT-0', { exact: true }).waitFor()
    await page.getByRole('button', { name: '配送超时', exact: false }).click()
    await page.getByText('QA-DELIVERY-0', { exact: true }).waitFor()
    await page.getByRole('button', { name: '查看详情', exact: true }).click()
    await page.getByRole('dialog').getByRole('heading', { name: 'QA-DETAIL' }).waitFor()
    await page.getByRole('button', { name: '关闭', exact: true }).click()
    await page.getByRole('button', { name: '待接超时', exact: false }).click()
    await page.getByText('QA-WAIT-0', { exact: true }).waitFor()
    await page.getByRole('button', { name: '调度派单', exact: true }).click()
    const dispatchDialog = page.getByRole('dialog')
    await dispatchDialog.getByRole('heading', { name: /为 QA-WAIT-0 选择骑手/ }).waitFor()
    assert.equal(await dispatchDialog.getByText('首选推荐', { exact: true }).count(), 1)
    assert.equal(await dispatchDialog.getByText('已完成 12 单同类订单', { exact: true }).count(), 1)
    await dispatchDialog.getByRole('button', { name: /测试骑手甲/ }).click()
    await page.screenshot({ path: 'tools/qa-output/v2.1-smart-dispatch-dialog.png', fullPage: true })
    await page.setViewportSize({ width: 390, height: 844 })
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), 'mobile dispatch must not overflow horizontally')
    await page.screenshot({ path: 'tools/qa-output/v2.1-smart-dispatch-dialog-mobile.png', fullPage: true })
    await page.setViewportSize({ width: 1440, height: 1100 })
    await dispatchDialog.getByRole('button', { name: '确认派单', exact: true }).click()
    await page.getByText('订单 QA-WAIT-0 已派给 测试骑手甲', { exact: true }).waitFor()
    assert.equal(dispatchRequests, 1)
    await page.locator('.command-center .command-pagination').first().getByRole('button', { name: '下一页' }).click()
    await page.getByText('QA-WAIT-1', { exact: true }).waitFor()

    for (const [label, endpoint, field, expected] of [
      ['待处理异常', 'issues', 'handlingStatus', 'PENDING'],
      ['处理中反馈', 'feedback', 'handlingStatus', 'PROCESSING'],
      ['待审认证', 'couriers', 'status', 'PENDING'],
    ]) {
      const request = page.waitForRequest((r) => new URL(r.url()).pathname === `/api/v1/admin/${endpoint}`)
      await page.getByRole('button', { name: label, exact: false }).click()
      assert.equal(new URL((await request).url()).searchParams.get(field), expected)
      await page.getByRole('navigation').getByRole('button', { name: '运营指挥台', exact: false }).click()
      await page.getByText('QA-WAIT-0', { exact: true }).waitFor()
    }
    await page.screenshot({ path: 'tools/qa-output/v2.1-command-center-desktop.png', fullPage: true })
    await page.setViewportSize({ width: 390, height: 844 })
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), 'mobile page must not overflow horizontally')
    await page.screenshot({ path: 'tools/qa-output/v2.1-command-center-mobile.png', fullPage: true })
    await page.setViewportSize({ width: 1440, height: 1100 })
    failure = true
    await page.getByRole('button', { name: '刷新指挥台', exact: true }).click()
    await page.getByRole('alert').filter({ hasText: '上次成功数据' }).waitFor()
    assert.equal(await page.getByText('QA-WAIT-0', { exact: true }).count(), 1)
    failure = false
    empty = true
    await page.getByRole('button', { name: '刷新指挥台', exact: true }).click()
    await page.getByText('当前没有待接超时订单', { exact: true }).waitFor()
    assert.equal(await page.getByRole('alert').count(), 0)
    role = 'SUPPORT'
    await page.getByRole('button', { name: '刷新指挥台', exact: true }).click()
    await page.waitForFunction(() => !document.querySelector('.command-couriers'))
    assert.equal(await page.getByRole('heading', { name: /在线运力/ }).count(), 0)
    await page.clock.install()
    await page.getByRole('checkbox', { name: '每 30 秒刷新' }).uncheck()
    await page.getByRole('checkbox', { name: '每 30 秒刷新' }).check()
    await page.waitForFunction(() => !document.querySelector('.command-controls button').disabled)
    const before = commandRequests
    const refreshed = page.waitForResponse((r) => r.url().includes('/command-center'))
    await page.clock.fastForward(31_000)
    await refreshed
    assert(commandRequests > before)
    await page.getByRole('checkbox', { name: '每 30 秒刷新' }).uncheck()
    await page.getByRole('button', { name: '刷新指挥台', exact: true }).waitFor({ state: 'visible' })
    assert.deepEqual(errors, [])
    console.log('PASS: fixed coupon grant, session center, queues, explainable recommendation and manual dispatch, pagination, order drawer, three filtered shortcuts, desktop/mobile renders, stale error, empty state, permission sections, auto refresh')
  } finally { await browser.close() }
}
main().catch((error) => { console.error(error); process.exitCode = 1 })
