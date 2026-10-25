const STATUS_TEXT = {
  PENDING_ASSIGNMENT: '等待接单',
  PENDING_REASSIGNMENT: '重新派单',
  ACCEPTED: '骑手已接单',
  PICKED_UP: '配送中',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

const CERTIFICATION_TEXT = {
  NOT_APPLIED: '未申请',
  PENDING: '审核中',
  APPROVED: '已通过',
  REJECTED: '未通过'
}

function money(cents) {
  return `¥${((Number(cents) || 0) / 100).toFixed(2)}`
}

function dateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = number => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function orderView(order) {
  return Object.assign({}, order, {
    feeText: money(order.feeCents),
    statusText: STATUS_TEXT[order.status] || order.status,
    createdAtText: dateTime(order.createdAt)
  })
}

function uuid() {
  const bytes = new Uint8Array(16)
  for (let index = 0; index < bytes.length; index += 1) {
    bytes[index] = Math.floor(Math.random() * 256)
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

module.exports = {
  CERTIFICATION_TEXT,
  STATUS_TEXT,
  dateTime,
  money,
  orderView,
  uuid
}
