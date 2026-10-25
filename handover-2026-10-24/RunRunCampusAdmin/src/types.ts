export type UserRole = 'CUSTOMER' | 'COURIER' | 'ADMIN'
export type OrderStatus = 'PENDING_ASSIGNMENT' | 'PENDING_REASSIGNMENT' | 'ACCEPTED' | 'PICKED_UP' | 'COMPLETED' | 'CANCELLED'
export type ServiceType = 'LOBBY' | 'DOOR' | 'EXPRESS' | 'PACKAGE_SMALL' | 'PACKAGE_MEDIUM' | 'PACKAGE_MEDIUM_LARGE' | 'PACKAGE_LARGE' | 'PACKAGE_EXTRA_LARGE' | 'PACKAGE_XL'
export type CourierCertificationStatus = 'NOT_APPLIED' | 'PENDING' | 'APPROVED' | 'REJECTED'
export type AdminRole = 'SUPER_ADMIN' | 'OPERATIONS' | 'SUPPORT' | 'REVIEWER' | 'AUDITOR'
export type AdminPermission =
  | 'DASHBOARD_VIEW'
  | 'ORDER_VIEW'
  | 'ORDER_DISPATCH'
  | 'ORDER_FORCE_COMPLETE'
  | 'ORDER_ARCHIVE'
  | 'PAYMENT_REVIEW'
  | 'PLATFORM_SETTINGS_MANAGE'
  | 'PUSH_DELIVERY_VIEW'
  | 'ORDER_EXPORT'
  | 'USER_VIEW'
  | 'USER_MANAGE'
  | 'COUPON_ISSUE'
  | 'COURIER_VIEW'
  | 'COURIER_REVIEW'
  | 'ISSUE_VIEW'
  | 'ISSUE_MANAGE'
  | 'FEEDBACK_VIEW'
  | 'FEEDBACK_MANAGE'
  | 'AUDIT_VIEW'
  | 'ADMIN_MANAGE'

export interface AuthUser {
  id: number
  phone: string
  displayName: string
  role: UserRole
  adminRole: AdminRole | null
  permissions: AdminPermission[]
}

export interface AuthResponse {
  token: string
  user: AuthUser
}

export interface PlatformSettings {
  businessHours: string
  waitMinutes: number
  deliveryMinutes: number
  maxActiveOrders: number
  issuePendingMinutes: number
  issueProcessingMinutes: number
}

export type PushDeliveryStatus = 'PENDING' | 'PROCESSING' | 'SENT' | 'DEAD'
export interface PushDeliveryOverview {
  dead: number
  pending: number
  processing: number
  deliveries: {
    items: Array<{
      id: number; recipientId: number; orderId: number | null; title: string
      destination: 'COURIER_CERTIFICATION' | null
      status: PushDeliveryStatus; attempts: number; nextAttemptAt: string
      lastError: string | null; createdAt: string; updatedAt: string; sentAt: string | null
    }>
    page: number
    size: number
    totalElements: number
    totalPages: number
  }
}

export interface MfaChallenge {
  step: 'ENROLL' | 'VERIFY' | 'RECOVER' | 'REPLACE'
  challengeToken: string
  expiresAt: string
  secret: string | null
  otpauthUri: string | null
}

export interface MfaVerification {
  session: AuthResponse | null
  recoveryCodes: string[]
  challenge: MfaChallenge | null
}

export interface LoginSession {
  id: number
  deviceName: string
  clientType: 'ANDROID' | 'ADMIN_WEB' | 'UNKNOWN'
  createdAt: string
  expiresAt: string
  current: boolean
}

export interface AdminDashboard {
  totalUsers: number
  totalCouriers: number
  totalOrders: number
  pendingOrders: number
  activeDeliveries: number
  pendingCertifications: number
  pendingIssues: number
  processingIssues: number
  pendingFeedback: number
  processingFeedback: number
}

export type CommandQueue = 'WAITING' | 'LATE_WAITING' | 'ACTIVE' | 'LATE_DELIVERY'
export interface CommandSnapshot {
  generatedAt: string
  waitMinutes: number
  deliveryMinutes: number
  orders: null | {
    waiting: number; lateWaiting: number; active: number; lateDelivery: number
    queue: CommandQueue
    items: { id: number; orderNo: string; status: OrderStatus; destination: string;
      courierName: string | null; stageStart: string; estimated: boolean }[]
    page: number; size: number; totalElements: number
  }
  couriers: null | {
    online: number; idle: number
    items: { id: number; displayName: string; activeOrders: number; onlineSince: string }[]
    page: number; size: number
  }
}

export interface DispatchCandidates {
  orderId: number
  orderNo: string
  generatedAt: string
  items: Array<{
    courierId: number
    displayName: string
    activeOrders: number
    onlineSince: string | null
    completedOrders: number
    matchedCompletedOrders: number
    averageRating: number | null
    ratingCount: number
    recommendationScore: number
    recommended: boolean
    reasons: string[]
  }>
  page: number
  size: number
  totalElements: number
}

export interface DispatchResult {
  orderId: number
  orderNo: string
  status: OrderStatus
  courierId: number
  courierName: string
  dispatchedAt: string
}

export interface AdminAccountItem {
  id: number
  phone: string
  displayName: string
  adminRole: AdminRole
  permissions: AdminPermission[]
  enabled: boolean
  createdAt: string
  failedLoginAttempts: number
  loginLockedUntil: string | null
}

export interface UserSummary {
  id: number
  displayName: string
  phone: string
  role: UserRole
}

export interface AdminOrderListItem {
  id: number
  orderNo: string
  status: OrderStatus
  serviceType: ServiceType
  merchant: string
  destination: string
  feeCents: number
  paymentMethod: 'WECHAT_APP' | 'CASH_ON_DELIVERY' | 'PERSONAL_QR_MANUAL'
  paymentStatus: 'UNPAID' | 'PAID' | 'REFUNDING' | 'REFUNDED'
  paidCents: number
  manualPaymentStatus: 'NOT_REQUESTED' | 'AWAITING_PAYMENT' | 'SUBMITTED' | 'CONFIRMED' | 'REJECTED'
  courierSettlementStatus: 'NOT_APPLICABLE' | 'PENDING' | 'SETTLED'
  customer: UserSummary
  courier: UserSummary | null
  createdAt: string
  updatedAt: string
  archivedAt: string | null
}

export interface AdminOrderPage {
  items: AdminOrderListItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AdminOrderDetail {
  order: AdminOrderListItem
  pickupCode: string
  pickupLocation: string
  originalFeeCents: number
  discountCents: number
  couponTitle: string | null
  membershipUsed: boolean
  membershipPlanTitle: string | null
  ratingScore: number | null
  ratingComment: string | null
  ratedAt: string | null
  courierLatitude: number | null
  courierLongitude: number | null
  courierLocationUpdatedAt: string | null
  payerReference: string | null
  paymentRequestedAt: string | null
  paymentSubmittedAt: string | null
  paymentReviewedAt: string | null
  paymentReviewerName: string | null
  paymentReviewNote: string | null
  courierSettledAt: string | null
  courierSettlementReviewerName: string | null
  events: Array<{
    id: number
    status: OrderStatus
    actor: UserSummary
    createdAt: string
  }>
  issues: Array<{
    id: number
    type: string
    description: string
    reporter: UserSummary
    createdAt: string
  }>
  proofs: Array<{
    id: number
  type: 'SIZE_REVIEW' | 'PICKUP' | 'DELIVERY'
    originalFileName: string
    contentType: string
    sizeBytes: number
    uploader: UserSummary
    createdAt: string
  }>
}

export interface AdminCourierItem {
  courierId: number
  displayName: string
  phone: string
  approved: boolean
  registeredAt: string
  completedOrderCount: number
  completedPackageOrderCount: number
  certificationId: number | null
  realName: string | null
  studentId: string | null
  campus: string | null
  status: CourierCertificationStatus
  rejectionReason: string | null
  submittedAt: string | null
  reviewedAt: string | null
  reviewerName: string | null
  validUntil: string | null
  approvedValidUntil: string | null
  photoUploaded: boolean
}

export interface AdminCourierPage {
  items: AdminCourierItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AdminCourierPerformance {
  todayCompletedPackageOrders: number
  lastSevenDaysCompletedPackageOrders: number
  currentMonthCompletedPackageOrders: number
  totalCompletedPackageOrders: number
  smallCompletedPackageOrders: number
  mediumCompletedPackageOrders: number
  mediumLargeCompletedPackageOrders: number
  largeCompletedPackageOrders: number
  specialLargeCompletedPackageOrders: number
  extraLargeCompletedPackageOrders: number
  orders: AdminCourierOrderPage
}

export interface AdminCourierOrderPage {
  items: Array<{
    id: number
    orderNo: string
    status: OrderStatus
    serviceType: ServiceType
    destination: string
    feeCents: number
    customerName: string
    createdAt: string
    updatedAt: string
  }>
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AdminUserItem {
  id: number
  displayName: string
  phone: string
  role: UserRole
  enabled: boolean
  courierApproved: boolean
  registeredAt: string
  failedLoginAttempts: number
  loginLockedUntil: string | null
}

export interface AdminUserPage {
  items: AdminUserItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type CreditRiskLevel = 'NORMAL' | 'WATCH' | 'HIGH_RISK'
export type CreditEventType = 'ADMIN_ADJUSTMENT' | 'RISK_CONFIRMED' | 'REVERSAL'

export interface CreditEventItem {
  id: number
  type: CreditEventType
  delta: number
  balanceAfter: number
  reason: string
  operatorName: string
  riskSignalId: number | null
  originalEventId: number | null
  createdAt: string
}

export interface CreditProfile {
  userId: number
  displayName: string
  score: number
  riskLevel: CreditRiskLevel
  pendingSignals: number
  events: CreditEventItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type RiskSignalType = 'FREQUENT_CANCELLATION' | 'REPEATED_ISSUES' | 'ORDER_TIMEOUT'
export type RiskSignalStatus = 'PENDING' | 'CONFIRMED' | 'DISMISSED'

export interface RiskSignalItem {
  id: number
  userId: number
  displayName: string
  phone: string
  type: RiskSignalType
  status: RiskSignalStatus
  orderId: number | null
  details: string
  createdAt: string
  reviewedAt: string | null
  reviewerName: string | null
  reviewNote: string | null
}

export interface RiskSignalPage {
  items: RiskSignalItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AdminCouponGrant {
  couponId: number
  userId: number
  displayName: string
  title: string
  discountCents: number
  minimumOrderCents: number
  expiresAt: string
}

export type OrderIssueType = 'MISSING_ITEM' | 'WRONG_ORDER' | 'CANNOT_CONTACT' | 'DELIVERY_DELAY' | 'OTHER'
export type OrderIssueHandlingStatus = 'PENDING' | 'PROCESSING' | 'RESOLVED'

export interface AdminIssueItem {
  id: number
  type: OrderIssueType
  description: string
  createdAt: string
  orderId: number
  orderNo: string
  orderStatus: OrderStatus
  reporterId: number
  reporterName: string
  reporterPhone: string
  handlingStatus: OrderIssueHandlingStatus
  assigneeId: number | null
  assigneeName: string | null
  resolution: string | null
  handledAt: string | null
  updatedAt: string
  slaDeadlineAt: string | null
  overdue: boolean
  overdueMinutes: number
}

export interface AdminIssuePage {
  items: AdminIssueItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  overdueTotal: number
}

export type FeedbackType = 'FUNCTION' | 'ACCOUNT' | 'OTHER'
export type FeedbackHandlingStatus = 'PENDING' | 'PROCESSING' | 'RESOLVED'

export interface AdminFeedbackItem {
  id: number
  type: FeedbackType
  content: string
  createdAt: string
  userId: number
  userName: string
  userPhone: string
  handlingStatus: FeedbackHandlingStatus
  assigneeId: number | null
  assigneeName: string | null
  resolution: string | null
  handledAt: string | null
  updatedAt: string
}

export interface AdminFeedbackPage {
  items: AdminFeedbackItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type AdminAuditAction =
  | 'COURIER_CERTIFICATION_APPROVED'
  | 'COURIER_CERTIFICATION_REJECTED'
  | 'USER_SUSPENDED'
  | 'USER_RESTORED'
  | 'ORDER_ISSUE_CLAIMED'
  | 'ORDER_ISSUE_RESOLVED'
  | 'FEEDBACK_CLAIMED'
  | 'FEEDBACK_RESOLVED'
  | 'ADMIN_CREATED'
  | 'ADMIN_ROLE_CHANGED'
  | 'ADMIN_SUSPENDED'
  | 'ADMIN_RESTORED'
  | 'ADMIN_PASSWORD_RESET'
  | 'ADMIN_PASSWORD_CHANGED'
  | 'USER_PASSWORD_RESET'
  | 'USER_LOGIN_UNLOCKED'
  | 'ADMIN_LOGIN_UNLOCKED'
  | 'ADMIN_MFA_LOGIN_FAILED'
  | 'ADMIN_MFA_LOGIN_SUCCEEDED'
  | 'ADMIN_MFA_ENROLLED'
  | 'ADMIN_MFA_REPLACED'
  | 'ADMIN_MFA_RECOVERY_USED'
  | 'ADMIN_MFA_RESET'
  | 'ORDER_DISPATCHED'
  | 'ORDER_REASSIGNED'
  | 'ORDER_FORCE_COMPLETED'
  | 'ORDER_ARCHIVED'
  | 'ORDER_UNARCHIVED'
  | 'PLATFORM_SETTINGS_UPDATED'
  | 'COUPON_ISSUED'
  | 'CREDIT_ADJUSTED'
  | 'CREDIT_EVENT_REVERSED'
  | 'RISK_SIGNAL_CONFIRMED'
  | 'RISK_SIGNAL_DISMISSED'
  | 'MANUAL_PAYMENT_CONFIRMED'
  | 'MANUAL_PAYMENT_REJECTED'
  | 'COURIER_SETTLEMENT_CONFIRMED'

export interface AdminAuditItem {
  id: number
  action: AdminAuditAction
  targetType: 'COURIER_CERTIFICATION' | 'USER' | 'ORDER_ISSUE' | 'FEEDBACK' | 'ADMIN_ACCOUNT' | 'ORDER' | 'PLATFORM_SETTINGS' | 'CREDIT_EVENT' | 'RISK_SIGNAL'
  targetId: number
  summary: string
  administratorId: number
  administratorName: string
  administratorPhone: string
  createdAt: string
}

export interface AdminAuditPage {
  items: AdminAuditItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
