const { getSession } = require('./utils/session')
const { getEnvironment, isMockPaymentEnabled } = require('./utils/environment')

App({
  globalData: {
    session: null,
    environment: 'test',
    mockPayment: false
  },

  onLaunch() {
    this.globalData.session = getSession()
    this.globalData.environment = getEnvironment()
    this.globalData.mockPayment = isMockPaymentEnabled()
  }
})
