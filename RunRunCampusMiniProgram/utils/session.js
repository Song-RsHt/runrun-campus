const SESSION_KEY = 'runrun_session_v1'

function getSession() {
  return wx.getStorageSync(SESSION_KEY) || null
}

function saveSession(session) {
  wx.setStorageSync(SESSION_KEY, session)
  getApp().globalData.session = session
}

function clearSession() {
  wx.removeStorageSync(SESSION_KEY)
  getApp().globalData.session = null
}

function requireSession() {
  const session = getSession()
  if (!session) {
    wx.reLaunch({ url: '/pages/login/login' })
    return null
  }
  return session
}

module.exports = {
  clearSession,
  getSession,
  requireSession,
  saveSession
}
