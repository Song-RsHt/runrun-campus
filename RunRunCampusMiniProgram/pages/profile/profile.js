const { request } = require('../../utils/api')
const { clearSession, requireSession } = require('../../utils/session')

function maskPhone(phone) {
  return phone ? phone.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2') : '—'
}

Page({
  data: {
    session: null,
    maskedPhone: '',
    loading: false,
    error: ''
  },

  onShow() {
    const session = requireSession()
    if (!session) return
    this.setData({
      session,
      maskedPhone: maskPhone(session.user.phone),
      avatar: session.user.displayName ? session.user.displayName.slice(0, 1) : '跑'
    })
  },

  async logout() {
    const confirmed = await new Promise(resolve => {
      wx.showModal({ title: '退出登录', content: '确认退出当前账号？', success: result => resolve(result.confirm) })
    })
    if (!confirmed) return
    this.setData({ loading: true, error: '' })
    try {
      await request('/api/v1/auth/logout', { method: 'POST' })
    } catch (error) {
      if (error.statusCode !== 401) this.setData({ error: error.message })
    } finally {
      clearSession()
      this.setData({ loading: false })
      wx.reLaunch({ url: '/pages/login/login' })
    }
  }
})
