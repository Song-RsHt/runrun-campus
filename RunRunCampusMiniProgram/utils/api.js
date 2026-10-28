const { clearSession, getSession } = require('./session')
const { getEnvironment, isMockPaymentEnabled } = require('./environment')

// The mini program never embeds a production endpoint. Configure the isolated
// test URL in WeChat DevTools storage as `rrc_api_base_url`.
function baseUrl() {
  const environment = getEnvironment()
  if (environment === 'production') {
    throw new Error('小程序禁止连接生产环境，请切换到隔离测试环境')
  }
  const configured = wx.getStorageSync('rrc_api_base_url')
  if (typeof configured !== 'string' || !/^https?:\/\//.test(configured)) {
    throw new Error(`未配置隔离${environment} API 地址，请设置 rrc_api_base_url`)
  }
  if (/localhost|127\.0\.0\.1|0\.0\.0\.0/.test(configured) && environment !== 'local') {
    throw new Error('本地地址只能在 local 环境使用')
  }
  return configured.replace(/\/$/, '')
}

function errorMessage(body, fallback) {
  if (!body) return fallback
  if (typeof body === 'string') return body
  return body.message || body.detail || body.error || fallback
}

function request(path, options = {}) {
  if (!path.startsWith('/api/v2/')) throw new Error(`禁止调用非 v2 接口：${path}`)
  const session = getSession()
  const header = Object.assign({
    Accept: 'application/json',
    'Content-Type': 'application/json',
    'X-RRC-Environment': getEnvironment(),
    'X-RRC-Payment-Mode': isMockPaymentEnabled() ? 'MOCK' : 'REAL_DISABLED'
  }, options.header || {})
  if (session && session.token) header.Authorization = `Bearer ${session.token}`
  return new Promise((resolve, reject) => {
    let url
    try { url = `${baseUrl()}${path}` } catch (error) { reject(error); return }
    wx.request({ url, method: options.method || 'GET', data: options.data, header, timeout: 15000,
      success(response) {
        if (response.statusCode >= 200 && response.statusCode < 300) { resolve(response.data); return }
        if (response.statusCode === 401 && !options.keepSessionOnUnauthorized) clearSession()
        const error = new Error(errorMessage(response.data, `请求失败（${response.statusCode}）`))
        error.statusCode = response.statusCode; error.body = response.data; reject(error)
      },
      fail(error) { reject(new Error(error.errMsg || '网络请求失败')) }
    })
  })
}

function upload(path, filePath, formData = {}) {
  if (!path.startsWith('/api/v2/')) return Promise.reject(new Error(`禁止调用非 v2 接口：${path}`))
  const session = getSession()
  return new Promise((resolve, reject) => {
    let url
    try { url = `${baseUrl()}${path}` } catch (error) { reject(error); return }
    wx.uploadFile({ url, filePath, name: 'file', formData, header: Object.assign({
      'X-RRC-Environment': getEnvironment(),
      'X-RRC-Payment-Mode': isMockPaymentEnabled() ? 'MOCK' : 'REAL_DISABLED'
    }, session && session.token ? { Authorization: `Bearer ${session.token}` } : {}), timeout: 30000,
      success(response) {
        let body = response.data
        try { body = response.data ? JSON.parse(response.data) : null } catch (_) {}
        if (response.statusCode >= 200 && response.statusCode < 300) { resolve(body); return }
        if (response.statusCode === 401) clearSession()
        const error = new Error(errorMessage(body, `上传失败（${response.statusCode}）`)); error.statusCode = response.statusCode; reject(error)
      },
      fail(error) { reject(new Error(error.errMsg || '文件上传失败')) }
    })
  })
}

module.exports = { request, upload, baseUrl }
