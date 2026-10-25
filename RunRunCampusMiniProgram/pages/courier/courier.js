const { request, upload } = require('../../utils/api')
const { requireSession } = require('../../utils/session')
const { CERTIFICATION_TEXT, money, orderView } = require('../../utils/format')

Page({
  data: {
    session: null,
    certification: null,
    operations: null,
    availableOrders: [],
    loading: true,
    acting: false,
    error: ''
  },

  onShow() {
    const session = requireSession()
    if (!session) return
    this.setData({ session })
    if (session.user.role === 'COURIER') this.load()
    else this.setData({ loading: false })
  },

  onPullDownRefresh() {
    if (!this.data.session || this.data.session.user.role !== 'COURIER') {
      wx.stopPullDownRefresh()
      return
    }
    this.load().finally(() => wx.stopPullDownRefresh())
  },

  async load() {
    this.setData({ loading: true, error: '' })
    try {
      const certification = await request('/api/v1/courier-certification/me')
      certification.statusText = CERTIFICATION_TEXT[certification.status] || certification.status
      this.setData({ certification })
      if (certification.status === 'APPROVED') {
        const [operations, orders] = await Promise.all([
          request('/api/v1/courier/operations/me'),
          request('/api/v1/orders/available')
        ])
        operations.todayGrossText = money(operations.todayGrossServiceFeeCents)
        operations.totalGrossText = money(operations.totalGrossServiceFeeCents)
        this.setData({ operations, availableOrders: orders.map(orderView) })
      } else {
        this.setData({ operations: null, availableOrders: [] })
      }
    } catch (error) {
      this.setData({ error: error.message })
    } finally {
      this.setData({ loading: false })
    }
  },

  async applyCertification() {
    if (this.data.acting) return
    this.setData({ acting: true, error: '' })
    try {
      const certification = await request('/api/v1/courier-certification/apply', {
        method: 'POST',
        data: {}
      })
      certification.statusText = CERTIFICATION_TEXT[certification.status] || certification.status
      this.setData({ certification })
      await this.chooseCertificationPhoto()
    } catch (error) {
      this.setData({ error: error.message })
    } finally {
      this.setData({ acting: false })
    }
  },

  async chooseCertificationPhoto() {
    const certification = this.data.certification
    if (!certification || !certification.id) {
      this.setData({ error: '请先提交骑手认证申请' })
      return
    }
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
      await upload(`/api/v1/courier-certification/${certification.id}/student-card`, file.tempFilePath)
      wx.showToast({ title: '认证材料已上传', icon: 'success' })
      await this.load()
    } catch (error) {
      if (!String(error.errMsg || error.message).includes('cancel')) {
        this.setData({ error: error.message || '认证材料上传失败' })
      }
    } finally {
      this.setData({ acting: false })
    }
  },

  async toggleAvailability(event) {
    if (this.data.acting) return
    this.setData({ acting: true, error: '' })
    try {
      const operations = await request('/api/v1/courier/operations/me/availability', {
        method: 'PUT',
        data: { acceptingOrders: event.detail.value }
      })
      operations.todayGrossText = money(operations.todayGrossServiceFeeCents)
      operations.totalGrossText = money(operations.totalGrossServiceFeeCents)
      this.setData({ operations })
    } catch (error) {
      this.setData({ error: error.message })
      await this.load()
    } finally {
      this.setData({ acting: false })
    }
  },

  async acceptOrder(event) {
    const id = event.currentTarget.dataset.id
    const confirmed = await new Promise(resolve => {
      wx.showModal({
        title: '确认接单',
        content: '接单后请及时完成取件和配送。',
        success: result => resolve(result.confirm)
      })
    })
    if (!confirmed || this.data.acting) return
    this.setData({ acting: true, error: '' })
    try {
      await request(`/api/v1/orders/${id}/accept`, { method: 'POST' })
      wx.navigateTo({ url: `/pages/order-detail/order-detail?id=${id}` })
    } catch (error) {
      this.setData({ error: error.message })
      await this.load()
    } finally {
      this.setData({ acting: false })
    }
  },

  openOrder(event) {
    wx.navigateTo({ url: `/pages/order-detail/order-detail?id=${event.currentTarget.dataset.id}` })
  }
})
