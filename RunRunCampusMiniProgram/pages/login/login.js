const { request } = require('../../utils/api')
const { clearSession, saveSession } = require('../../utils/session')

function isCompletePrivacyPolicy(policy) {
  const fields = ['version', 'title', 'effectiveDate', 'operatorName', 'contact', 'content']
  if (!policy || Array.isArray(policy) || !fields.every(field => typeof policy[field] === 'string' && policy[field].trim())) return false
  const date = policy.effectiveDate
  // 日期必须是实际存在的日历日期，避免 Date.parse 将二月三十日自动归一化。
  return /^\d{4}-\d{2}-\d{2}$/.test(date) && Number.isFinite(Date.parse(date)) && new Date(date).toISOString().slice(0, 10) === date
}

Page({
  data: {
    mode: 'login',
    phone: '',
    password: '',
    displayName: '',
    role: 'USER',
    privacyPolicy: null,
    privacyAccepted: false,
    privacyExpanded: false,
    captcha: null,
    captchaCode: '',
    failedAttempts: 0,
    loginTicket: null,
    loading: false,
    error: ''
  },

  onLoad() {
    this.loadPrivacyPolicy()
  },

  async loadPrivacyPolicy() {
    try {
      const privacyPolicy = await request('/api/v2/privacy/policy', {
        keepSessionOnUnauthorized: true
      })
      if (!isCompletePrivacyPolicy(privacyPolicy)) {
        throw new Error('协议响应不完整，请重试')
      }
      this.setData({ privacyPolicy, privacyAccepted: false, error: '' })
    } catch (error) {
      this.setData({ privacyPolicy: null, privacyAccepted: false, error: `隐私政策加载失败：${error.message}` })
    }
  },

  bindField(event) {
    const field = event.currentTarget.dataset.field
    this.setData({ [field]: event.detail.value, error: '' })
  },

  selectRole(event) {
    this.setData({ role: event.currentTarget.dataset.role })
  },

  toggleMode() {
    this.setData({
      mode: this.data.mode === 'login' ? 'register' : 'login',
      captcha: null,
      captchaCode: '',
      failedAttempts: 0,
      error: ''
    })
  },

  togglePrivacy() {
    this.setData({ privacyAccepted: !this.data.privacyAccepted })
  },

  togglePrivacyContent() {
    this.setData({ privacyExpanded: !this.data.privacyExpanded })
  },

  async refreshCaptcha() {
    try {
      const captcha = await request('/api/v2/auth/captcha', {
        method: 'POST',
        keepSessionOnUnauthorized: true
      })
      this.setData({ captcha, captchaCode: '' })
    } catch (error) {
      this.setData({ error: `验证码加载失败：${error.message}` })
    }
  },

  canSubmit() {
    const { mode, phone, password, displayName, privacyAccepted, privacyPolicy, captcha, captchaCode } = this.data
    if (!/^1\d{10}$/.test(phone) || password.length < 6 || !privacyAccepted || !privacyPolicy) return false
    if (mode === 'register' && !displayName.trim()) return false
    return !(mode === 'login' && captcha && captchaCode.length !== 4)
  },

  async submit() {
    if (!this.canSubmit() || this.data.loading) {
      this.setData({ error: '请完整填写并核对当前表单' })
      return
    }

    const device = wx.getDeviceInfo ? wx.getDeviceInfo() : wx.getSystemInfoSync()
    const common = {
      clientType: 'WECHAT_MP',
      deviceName: `${device.brand || 'WeChat'} ${device.model || 'Mini Program'}`.slice(0, 80)
    }
    const registering = this.data.mode === 'register'
    if (registering) {
      try {
        const login = await new Promise((resolve, reject) => wx.login({ success: resolve, fail: reject }))
        const ticket = await request('/api/v2/auth/wechat/login', { method: 'POST', data: { code: login.code }, keepSessionOnUnauthorized: true })
        if (ticket.token) return this.finishLogin(ticket)
        this.setData({ loginTicket: ticket.loginTicket, error: '请点击微信手机号授权按钮完成注册' })
      } catch (error) { this.setData({ error: error.message }) }
      return
    }
    const data = Object.assign({}, common, {
      phone: this.data.phone,
      password: this.data.password,
      captchaToken: this.data.captcha ? this.data.captcha.challengeToken : null,
      captchaCode: this.data.captchaCode || null
    })

    this.setData({ loading: true, error: '' })
    try {
      const response = await request('/api/v2/auth/password/login', {
        method: 'POST',
        data,
        keepSessionOnUnauthorized: true
      })
      await this.finishLogin(response)
    } catch (error) {
      const failedAttempts = registering ? 0 : this.data.failedAttempts + (error.statusCode === 401 ? 1 : 0)
      this.setData({ error: error.message, failedAttempts })
      if (!registering && (error.statusCode === 428 || failedAttempts >= 3 || this.data.captcha)) {
        await this.refreshCaptcha()
      }
    } finally {
      this.setData({ loading: false })
    }
  },

  async bindWechatPhone(event) {
    if (!this.data.privacyAccepted || !this.data.privacyPolicy) {
      this.setData({ error: '请先阅读并同意当前隐私政策' }); return
    }
    if (!this.data.loginTicket || !event.detail || event.detail.errMsg !== 'getPhoneNumber:ok') {
      this.setData({ error: '需要微信手机号授权才能注册' }); return
    }
    this.setData({ loading: true, error: '' })
    try {
      const response = await request('/api/v2/auth/wechat/bind-phone', { method: 'POST', data: {
        loginTicket: this.data.loginTicket, phoneCode: event.detail.code, requestedRole: this.data.role
      }, keepSessionOnUnauthorized: true })
      await this.finishLogin(response)
    } catch (error) { this.setData({ error: error.message }) } finally { this.setData({ loading: false }) }
  },

  async finishLogin(response) {
    if (!this.data.privacyAccepted || !isCompletePrivacyPolicy(this.data.privacyPolicy)) throw new Error('请先阅读并同意当前隐私政策')
    const acceptedVersion = this.data.privacyPolicy.version
    const user = response.account
    if (!response.token || !user) throw new Error('登录响应异常，请重新登录')
    saveSession({ token: response.token, user })
    try {
      const receipt = await request('/api/v2/privacy/consent', { method: 'POST', data: { version: acceptedVersion } })
      // HTTP 成功不能代替同意已落库的确认，回执须对应用户刚刚勾选的版本。
      if (!receipt || Array.isArray(receipt) || receipt.accepted !== true || receipt.version !== acceptedVersion) {
        throw new Error('协议同意回执异常或版本不一致')
      }
      wx.reLaunch({ url: '/pages/home/home' })
    } catch (error) {
      // 同意回执失败不能留下可复用的客户端会话。
      await request('/api/v2/auth/logout', { method: 'POST', data: { allSessions: false },
        header: { 'Idempotency-Key': 'privacy-failed-' + Date.now() }, keepSessionOnUnauthorized: true }).catch(() => {})
      clearSession()
      throw new Error('协议同意记录失败，请重新登录：' + error.message)
    }
  }
})
