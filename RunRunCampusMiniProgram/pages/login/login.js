const { request } = require('../../utils/api')
const { clearSession, saveSession } = require('../../utils/session')

Page({
  data: {
    mode: 'login',
    phone: '',
    password: '',
    displayName: '',
    role: 'CUSTOMER',
    privacyPolicy: null,
    privacyAccepted: false,
    privacyExpanded: false,
    captcha: null,
    captchaCode: '',
    failedAttempts: 0,
    loading: false,
    error: ''
  },

  onLoad() {
    this.loadPrivacyPolicy()
  },

  async loadPrivacyPolicy() {
    try {
      const privacyPolicy = await request('/api/v1/privacy/policy', {
        keepSessionOnUnauthorized: true
      })
      this.setData({ privacyPolicy })
    } catch (error) {
      this.setData({ error: `隐私政策加载失败：${error.message}` })
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
      const captcha = await request('/api/v1/auth/captcha', {
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
    const data = registering ? Object.assign({}, common, {
      phone: this.data.phone,
      password: this.data.password,
      displayName: this.data.displayName.trim(),
      role: this.data.role,
      privacyPolicyVersion: this.data.privacyPolicy.version
    }) : Object.assign({}, common, {
      phone: this.data.phone,
      password: this.data.password,
      captchaToken: this.data.captcha ? this.data.captcha.challengeToken : null,
      captchaCode: this.data.captchaCode || null
    })

    this.setData({ loading: true, error: '' })
    try {
      const response = await request(`/api/v1/auth/${registering ? 'register' : 'login'}`, {
        method: 'POST',
        data,
        keepSessionOnUnauthorized: true
      })
      saveSession({ token: response.token, user: response.user })
      if (response.user.privacyPolicyVersion !== this.data.privacyPolicy.version) {
        try {
          await request('/api/v1/privacy/consent', {
            method: 'POST',
            data: { version: this.data.privacyPolicy.version }
          })
          response.user.privacyPolicyVersion = this.data.privacyPolicy.version
          saveSession({ token: response.token, user: response.user })
        } catch (error) {
          clearSession()
          throw new Error(`隐私政策确认失败：${error.message}`)
        }
      }
      wx.reLaunch({ url: '/pages/home/home' })
    } catch (error) {
      const failedAttempts = registering ? 0 : this.data.failedAttempts + (error.statusCode === 401 ? 1 : 0)
      this.setData({ error: error.message, failedAttempts })
      if (!registering && (error.statusCode === 428 || failedAttempts >= 3 || this.data.captcha)) {
        await this.refreshCaptcha()
      }
    } finally {
      this.setData({ loading: false })
    }
  }
})
