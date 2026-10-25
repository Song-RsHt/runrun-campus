const { request } = require('../../utils/api')
const { requireSession } = require('../../utils/session')
const { orderView } = require('../../utils/format')

Page({
  data: {
    session: null,
    filter: 'ACTIVE',
    orders: [],
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

  selectFilter(event) {
    this.setData({ filter: event.currentTarget.dataset.filter })
    this.load()
  },

  async load() {
    this.setData({ loading: true, error: '' })
    const path = this.data.filter === 'ACTIVE'
      ? '/api/v1/orders/mine/active'
      : '/api/v1/orders/mine/history?page=0&size=50'
    try {
      const response = await request(path)
      const orders = Array.isArray(response) ? response : response.items || []
      this.setData({ orders: orders.map(orderView) })
    } catch (error) {
      this.setData({ error: error.message, orders: [] })
    } finally {
      this.setData({ loading: false })
    }
  },

  openOrder(event) {
    wx.navigateTo({ url: `/pages/order-detail/order-detail?id=${event.currentTarget.dataset.id}` })
  },

  createOrder() {
    wx.navigateTo({ url: '/pages/create-order/create-order' })
  }
})
