const { request } = require('../../utils/api')
const { requireSession } = require('../../utils/session')
const { uuid } = require('../../utils/format')

const SERVICES = {
  TAKEOUT: [
    { code: 'LOBBY', label: '楼下取餐', hint: '宿舍楼下交付' },
    { code: 'DOOR', label: '送到门口', hint: '送至宿舍门口' },
    { code: 'EXPRESS', label: '夜间急送', hint: '22:00 后优先派送' }
  ],
  PACKAGE: [
    { code: 'PACKAGE_SMALL', label: '小件', hint: '文件袋、小配件、小盒药品等' },
    { code: 'PACKAGE_MEDIUM', label: '中件', hint: '鞋盒、教材、小型电器等' },
    { code: 'PACKAGE_MEDIUM_LARGE', label: '中大件', hint: '小号行李箱、成箱日用品等' },
    { code: 'PACKAGE_LARGE', label: '大件', hint: '大号被褥包、中号行李箱等' },
    { code: 'PACKAGE_EXTRA_LARGE', label: '特大件', hint: '大号行李箱、床上用品包等' },
    { code: 'PACKAGE_XL', label: '超大件', hint: '需推车但一名骑手可安全搬运' }
  ]
}

Page({
  data: {
    kind: 'TAKEOUT',
    services: SERVICES.TAKEOUT,
    serviceIndex: 0,
    stations: [],
    stationIndex: 0,
    zones: [],
    zoneIndex: 0,
    merchant: '',
    pickupCode: '',
    destination: '',
    loading: true,
    submitting: false,
    error: ''
  },

  onLoad() {
    const session = requireSession()
    if (!session) return
    if (session.user.role !== 'CUSTOMER') {
      wx.showModal({
        title: '当前账号是骑手',
        content: '发布代取订单请使用用户账号。',
        showCancel: false,
        success: () => wx.navigateBack()
      })
      return
    }
    this.loadConfig()
  },

  async loadConfig() {
    try {
      const config = await request('/api/v1/service-config')
      this.config = config
      this.applyKind('TAKEOUT', config)
    } catch (error) {
      this.setData({ error: error.message })
    } finally {
      this.setData({ loading: false })
    }
  },

  applyKind(kind, config = this.config) {
    const stations = (config.pickupStations || []).filter(station => station.kind === kind)
    this.setData({
      kind,
      services: SERVICES[kind],
      serviceIndex: 0,
      stations,
      stationIndex: 0,
      zones: config.deliveryZones || [],
      zoneIndex: 0,
      merchant: '',
      pickupCode: ''
    })
  },

  selectKind(event) {
    this.applyKind(event.currentTarget.dataset.kind)
  },

  bindField(event) {
    this.setData({ [event.currentTarget.dataset.field]: event.detail.value, error: '' })
  },

  selectService(event) {
    this.setData({ serviceIndex: Number(event.currentTarget.dataset.index) })
  },

  selectStation(event) {
    this.setData({ stationIndex: Number(event.detail.value) })
  },

  selectZone(event) {
    this.setData({ zoneIndex: Number(event.detail.value) })
  },

  async submit() {
    const { merchant, pickupCode, destination, services, serviceIndex, stations, stationIndex, zones, zoneIndex } = this.data
    if (!merchant.trim() || !pickupCode.trim() || !destination.trim() || !stations[stationIndex] || !zones[zoneIndex]) {
      this.setData({ error: '请完整填写代取信息' })
      return
    }

    const confirmed = await new Promise(resolve => {
      wx.showModal({
        title: '确认发布',
        content: `${merchant.trim()}\n${stations[stationIndex].displayName} → ${destination.trim()}\n请确认取件信息真实有效。`,
        confirmText: '确认发布',
        success: result => resolve(result.confirm)
      })
    })
    if (!confirmed || this.data.submitting) return

    const data = {
      merchant: merchant.trim(),
      pickupCode: pickupCode.trim(),
      pickupLocation: stations[stationIndex].displayName,
      destination: destination.trim(),
      serviceType: services[serviceIndex].code,
      couponId: null,
      useMembership: false,
      pickupStation: stations[stationIndex].code,
      deliveryZone: zones[zoneIndex].code
    }

    this.setData({ submitting: true, error: '' })
    try {
      const order = await request('/api/v1/orders', {
        method: 'POST',
        data,
        header: { 'Idempotency-Key': uuid() }
      })
      wx.redirectTo({ url: `/pages/order-detail/order-detail?id=${order.id}` })
    } catch (error) {
      this.setData({ error: error.message })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
