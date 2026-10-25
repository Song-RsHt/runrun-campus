const { request } = require('../../utils/api')
const { requireSession } = require('../../utils/session')
const { orderView } = require('../../utils/format')

Page({
  data: {
    session: null,
    config: null,
    activeOrders: [],
    loading: true,
    error: ''
  },

  onShow() {
    const session = requireSession()
    if (!session) return
    this.setData({ session })
    this.load()
  },

  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  },

  async load() {
    this.setData({ loading: true, error: '' })
    try {
      const [config, orders] = await Promise.all([
        request('/api/v1/service-config'),
        request('/api/v1/orders/mine/active')
      ])
      this.setData({ config, activeOrders: orders.slice(0, 3).map(orderView) })
    } catch (error) {
      this.setData({ error: error.message })
    } finally {
      this.setData({ loading: false })
    }
  },

  createOrder() {
    wx.navigateTo({ url: '/pages/create-order/create-order' })
  },

  openOrders() {
    wx.switchTab({ url: '/pages/orders/orders' })
  },

  openCourier() {
    wx.switchTab({ url: '/pages/courier/courier' })
  },

  openOrder(event) {
    wx.navigateTo({ url: `/pages/order-detail/order-detail?id=${event.currentTarget.dataset.id}` })
  }
})
