const { clearSession, getSession } = require('./session')

const BASE_URL = 'https://api.runruncampus.cn'

function errorMessage(body, fallback) {
  if (!body) return fallback
  if (typeof body === 'string') return body
  return body.message || body.detail || body.error || fallback
}

function request(path, options = {}) {
  const session = getSession()
  const header = Object.assign({
    Accept: 'application/json',
    'Content-Type': 'application/json'
  }, options.header || {})

  if (session && session.token) {
    header.Authorization = `Bearer ${session.token}`
  }

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${BASE_URL}${path}`,
      method: options.method || 'GET',
      data: options.data,
      header,
      timeout: 15000,
      success(response) {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.data)
          return
        }
        if (response.statusCode === 401 && !options.keepSessionOnUnauthorized) {
          clearSession()
        }
        const error = new Error(errorMessage(response.data, `请求失败（${response.statusCode}）`))
        error.statusCode = response.statusCode
        error.body = response.data
        reject(error)
      },
      fail(error) {
        reject(new Error(error.errMsg || '网络请求失败'))
      }
    })
  })
}

function upload(path, filePath) {
  const session = getSession()
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${BASE_URL}${path}`,
      filePath,
      name: 'file',
      header: session && session.token ? { Authorization: `Bearer ${session.token}` } : {},
      timeout: 30000,
      success(response) {
        let body = response.data
        try {
          body = response.data ? JSON.parse(response.data) : null
        } catch (_) {
          // 保留文本响应，交给统一错误信息处理。
        }
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(body)
          return
        }
        if (response.statusCode === 401) clearSession()
        const error = new Error(errorMessage(body, `上传失败（${response.statusCode}）`))
        error.statusCode = response.statusCode
        reject(error)
      },
      fail(error) {
        reject(new Error(error.errMsg || '文件上传失败'))
      }
    })
  })
}

module.exports = {
  BASE_URL,
  request,
  upload
}
