// Environment is selected at runtime by the isolated test harness. Production is intentionally unsupported.
const ENV_KEY = 'rrc_api_environment'
const ALLOWED = ['local', 'test', 'staging']

function getEnvironment() {
  const value = wx.getStorageSync(ENV_KEY) || 'test'
  return ALLOWED.includes(value) ? value : 'test'
}

function isMockPaymentEnabled() {
  return getEnvironment() !== 'production' && wx.getStorageSync('rrc_mock_payment_enabled') === true
}

function environmentLabel() {
  return `${getEnvironment()} / ${isMockPaymentEnabled() ? '模拟支付' : '真实支付关闭'}`
}

module.exports = { getEnvironment, isMockPaymentEnabled, environmentLabel }
