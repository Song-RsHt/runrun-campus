const { request, upload } = require('../../utils/api')
const { requireSession } = require('../../utils/session')
const { STATUS_TEXT, dateTime, orderView } = require('../../utils/format')

const PACKAGE_SERVICES = [
  { code: 'PACKAGE_SMALL', label: '小件' },
  { code: 'PACKAGE_MEDIUM', label: '中件' },
  { code: 'PACKAGE_MEDIUM_LARGE', label: '中大件' },
  { code: 'PACKAGE_LARGE', label: '大件' },
  { code: 'PACKAGE_EXTRA_LARGE', label: '特大件' },
  { code: 'PACKAGE_XL', label: '超大件' }
]

const SERVICE_TEXT = {
  LOBBY: '楼下取餐',
  DOOR: '送到门口',
  EXPRESS: '夜间急送',
  PACKAGE_SMALL: '小件',
  PACKAGE_MEDIUM: '中件',
  PACKAGE_MEDIUM_LARGE: '中大件',
  PACKAGE_LARGE: '大件',
  PACKAGE_EXTRA_LARGE: '特大件',
  PACKAGE_XL: '超大件'
}

Page({
  data: {
    id: null,
    session: null,
    order: null,
    events: [],
    deliveryCode: '',
    completionCode: '',
    payerReference: '',
    packageServices: PACKAGE_SERVICES,
    packageIndex: 0,
    unableReason: '',
    loading: true,
    acting: false,
    error: ''
  },

  onLoad(options) {
    const session = requireSession()
    if (!session) return
    const id = Number(options.id)
    if (!id) {
      wx.showModal({ title: '无法打开订单', content: '订单编号无效。', showCancel: false })
      return
    }
    this.setData({ id, session })
    this.load()
  },

  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },

  async load() {
    this.setData({ loading: true, error: '' })
    try {
      const [order, events] = await Promise.all([
        request(`/api/v1/orders/${this.data.id}`),
        request(`/api/v1/orders/${this.data.id}/events`)
      ])
      const view = orderView(order)
      view.updatedAtText = dateTime(order.updatedAt)
      view.isPackageOrder = String(order.serviceType || '').startsWith('PACKAGE_')
      view.serviceText = SERVICE_TEXT[order.serviceType] || order.serviceType
      view.proposedPackageServiceText = SERVICE_TEXT[order.proposedPackageServiceType] || order.proposedPackageServiceType
      view.paymentText = order.paymentMethod === 'PERSONAL_QR_MANUAL'
        ? (order.paymentStatus === 'PAID' ? '平台已确认到账' : '人工扫码待确认')
        : (order.paymentStatus === 'PAID' ? '已支付' : '待支付')
      const packageIndex = Math.max(0, PACKAGE_SERVICES.findIndex(item => item.code === order.serviceType))
      this.setData({
        order: view,
        packageIndex,
        events: events.map(event => Object.assign({}, event, {
          statusText: STATUS_TEXT[event.status] || event.status,
          createdAtText: dateTime(event.createdAt)
        }))
      })
    } catch (error) {
      this.setData({ error: error.message })
    } finally {
      this.setData({ loading: false })
    }
  },

  bindField(event) {
    this.setData({ [event.currentTarget.dataset.field]: event.detail.value, error: '' })
  },

  selectPackageSize(event) {
    this.setData({ packageIndex: Number(event.detail.value) })
  },

  async confirmAction(title, content, action) {
    const confirmed = await new Promise(resolve => {
      wx.showModal({ title, content, success: result => resolve(result.confirm) })
    })
    if (!confirmed || this.data.acting) return
    this.setData({ acting: true, error: '' })
    try {
      await action()
      await this.load()
    } catch (error) {
      this.setData({ error: error.message })
    } finally {
      this.setData({ acting: false })
    }
  },

  acceptOrder() {
    this.confirmAction('确认接单', '接单后请按订单信息完成取件与配送。', () =>
      request(`/api/v1/orders/${this.data.id}/accept`, { method: 'POST' })
    )
  },

  cancelOrder() {
    this.confirmAction('取消订单', '确认取消这笔尚未完成的订单？', () =>
      request(`/api/v1/orders/${this.data.id}/cancel`, { method: 'POST' })
    )
  },

  markPickedUp() {
    if (!this.data.order.pickupProofUploaded) {
      this.setData({ error: '请先上传取件凭证' })
      return
    }
    this.confirmAction('确认已取件', '状态更新后，用户将看到订单进入配送中。', () =>
      request(`/api/v1/orders/${this.data.id}/pickup`, { method: 'POST' })
    )
  },

  confirmPackageSize() {
    const selected = this.data.packageServices[this.data.packageIndex]
    if (!selected) return
    if (selected.code !== this.data.order.serviceType && !this.data.order.packageSizeReviewProofUploaded) {
      this.setData({ error: '规格调整前请先上传包裹规格核验照片' })
      return
    }
    this.confirmAction('确认包裹规格', `现场规格：${selected.label}`, () =>
      request(`/api/v1/orders/${this.data.id}/package-size`, {
        method: 'POST',
        data: { serviceType: selected.code }
      })
    )
  },

  approvePackageSize() {
    this.confirmAction('同意规格调整', '同意后将按调整后的规格继续履约。', () =>
      request(`/api/v1/orders/${this.data.id}/package-size/approve`, { method: 'POST' })
    )
  },

  rejectPackageSize() {
    this.confirmAction('拒绝规格调整', '拒绝后订单将取消，确认继续？', () =>
      request(`/api/v1/orders/${this.data.id}/package-size/reject`, { method: 'POST' })
    )
  },

  completeOrder() {
    if (!this.data.order.deliveryProofUploaded) {
      this.setData({ error: '请先上传送达凭证' })
      return
    }
    if (!/^\d{6}$/.test(this.data.completionCode)) {
      this.setData({ error: '请输入用户提供的 6 位收货码' })
      return
    }
    this.confirmAction('确认完成配送', '请确保包裹已交付给用户。', () =>
      request(`/api/v1/orders/${this.data.id}/complete`, {
        method: 'POST',
        data: { deliveryCode: this.data.completionCode }
      })
    )
  },

  requestManualPayment() {
    this.confirmAction('通知用户付款', '请确认已经到达交付地点，再通知用户扫码付款。', () =>
      request(`/api/v1/orders/${this.data.id}/manual-payment/request`, { method: 'POST' })
    )
  },

  async submitManualPayment() {
    if (!this.data.payerReference.trim()) {
      this.setData({ error: '请输入付款微信昵称或手机号后四位' })
      return
    }
    await this.confirmAction('提交核款', '提交后需等待管理员确认到账，请勿提前向骑手提供收货码。', () =>
      request(`/api/v1/orders/${this.data.id}/manual-payment/submit`, {
        method: 'POST',
        data: { payerReference: this.data.payerReference.trim() }
      })
    )
  },

  reportUnable() {
    if (!this.data.unableReason.trim()) {
      this.setData({ error: '请填写无法继续履约的原因' })
      return
    }
    this.confirmAction('申请重新派单', '提交后订单会释放给其他骑手。', () =>
      request(`/api/v1/orders/${this.data.id}/unable`, {
        method: 'POST',
        data: { reason: this.data.unableReason.trim() }
      })
    )
  },

  async uploadProof(event) {
    if (this.data.acting) return
    const type = event.currentTarget.dataset.type
    try {
      const result = await wx.chooseMedia({
        count: 1,
        mediaType: ['image'],
        sourceType: ['camera', 'album'],
        sizeType: ['compressed']
      })
      const file = result.tempFiles && result.tempFiles[0]
      if (!file) return
      this.setData({ acting: true, error: '' })
      await upload(`/api/v1/orders/${this.data.id}/proofs/${type}`, file.tempFilePath)
      await this.load()
    } catch (error) {
      if (!String(error.errMsg || error.message).includes('cancel')) {
        this.setData({ error: error.message || '凭证上传失败' })
      }
    } finally {
      this.setData({ acting: false })
    }
  },

  async updateLocation() {
    if (this.data.acting) return
    this.setData({ acting: true, error: '' })
    try {
      const location = await wx.getLocation({ type: 'gcj02' })
      await request(`/api/v1/orders/${this.data.id}/location`, {
        method: 'POST',
        data: { latitude: location.latitude, longitude: location.longitude }
      })
      wx.showToast({ title: '位置已更新', icon: 'success' })
    } catch (error) {
      if (!String(error.errMsg || error.message).includes('cancel')) {
        this.setData({ error: error.message || '位置更新失败' })
      }
    } finally {
      this.setData({ acting: false })
    }
  },

  async showDeliveryCode() {
    try {
      const result = await request(`/api/v1/orders/${this.data.id}/delivery-code`)
      this.setData({ deliveryCode: result.deliveryCode })
    } catch (error) {
      this.setData({ error: error.message })
    }
  }
})
