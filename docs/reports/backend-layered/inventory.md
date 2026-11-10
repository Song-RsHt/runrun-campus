# 重构前完整 Java 盘点

基线：5f17d0c0ba0c345705d0cfc5d4ca9bdc10c322ab。范围：所有自有 main/test Java。清单由源码生成，动态调用与未使用判定仍须人工审阅。

main 216 个，test 77 个。

## 包结构

|包|main|test|
|---|---:|---:|
|com.zhangxuan.runruncampus|1|0|
|com.zhangxuan.runruncampus.address|4|1|
|com.zhangxuan.runruncampus.admin|35|19|
|com.zhangxuan.runruncampus.api|8|4|
|com.zhangxuan.runruncampus.auth|15|9|
|com.zhangxuan.runruncampus.config|1|2|
|com.zhangxuan.runruncampus.coupon|4|1|
|com.zhangxuan.runruncampus.courier|8|5|
|com.zhangxuan.runruncampus.credit|14|2|
|com.zhangxuan.runruncampus.feedback|6|1|
|com.zhangxuan.runruncampus.membership|5|1|
|com.zhangxuan.runruncampus.message|15|4|
|com.zhangxuan.runruncampus.order|30|9|
|com.zhangxuan.runruncampus.payment|18|3|
|com.zhangxuan.runruncampus.privacy|2|2|
|com.zhangxuan.runruncampus.user|3|0|
|com.zhangxuan.runrunv2|47|14|

## 直接数据库访问

|类|职责|事务声明数|锁所在行|
|---|---|---:|---|
|com.zhangxuan.runruncampus.address.AddressRepository|JPA Repository|0||
|com.zhangxuan.runruncampus.admin.AdminAuditLogRepository|JPA Repository|0||
|com.zhangxuan.runruncampus.admin.AdminCommandCenterService|JDBC/持久化使用者|1||
|com.zhangxuan.runruncampus.admin.AdminDispatchService|JDBC/持久化使用者|2||
|com.zhangxuan.runruncampus.admin.FulfillmentProvenanceService|JDBC/持久化使用者|4||
|com.zhangxuan.runruncampus.api.PlatformSettingsService|JDBC/持久化使用者|2||
|com.zhangxuan.runruncampus.auth.AdminMfaRepository|JPA Repository|0||
|com.zhangxuan.runruncampus.auth.SessionRepository|JPA Repository|0||
|com.zhangxuan.runruncampus.auth.UserRoleSchemaMigration|JDBC/持久化使用者|0||
|com.zhangxuan.runruncampus.coupon.UserCouponRepository|JPA Repository|0|17|
|com.zhangxuan.runruncampus.courier.CourierCertificationRepository|JPA Repository|0|21, 26, 31, 35|
|com.zhangxuan.runruncampus.credit.CreditEventRepository|JPA Repository|0||
|com.zhangxuan.runruncampus.credit.CreditProfileRepository|JPA Repository|0|14|
|com.zhangxuan.runruncampus.credit.RiskSignalMaintenance|JDBC/持久化使用者|1||
|com.zhangxuan.runruncampus.credit.RiskSignalRepository|JPA Repository|0|18|
|com.zhangxuan.runruncampus.feedback.FeedbackRepository|JPA Repository|0|58|
|com.zhangxuan.runruncampus.membership.UserMembershipRepository|JPA Repository|0|16, 20|
|com.zhangxuan.runruncampus.message.OrderMessageRepository|JPA Repository|0||
|com.zhangxuan.runruncampus.message.PushDeliveryRepository|JPA Repository|1||
|com.zhangxuan.runruncampus.order.OrderChatRepository|JPA Repository|0||
|com.zhangxuan.runruncampus.order.OrderEventRepository|JPA Repository|0||
|com.zhangxuan.runruncampus.order.OrderIssueRepository|JPA Repository|0|93|
|com.zhangxuan.runruncampus.order.OrderProofRepository|JPA Repository|0||
|com.zhangxuan.runruncampus.order.OrderRepository|JPA Repository|0|256|
|com.zhangxuan.runruncampus.payment.WechatPaymentRepository|JPA Repository|0|15|
|com.zhangxuan.runruncampus.payment.WechatRefundRepository|JPA Repository|0|17|
|com.zhangxuan.runruncampus.user.UserRepository|JPA Repository|0|18, 112|
|com.zhangxuan.runrunv2.CourierCertificationV2Service|JDBC/持久化使用者|6|33, 121, 125|
|com.zhangxuan.runrunv2.CourierEvidenceGovernanceService|JDBC/持久化使用者|4|45, 66, 86, 87|
|com.zhangxuan.runrunv2.DeploymentIdentityService|JDBC/持久化使用者|3||
|com.zhangxuan.runrunv2.FulfillmentQueries|JDBC/持久化使用者|2||
|com.zhangxuan.runrunv2.FulfillmentService|JDBC/持久化使用者|15||
|com.zhangxuan.runrunv2.IdentityService|JDBC/持久化使用者|5|42, 61, 77, 151, 168, 173|
|com.zhangxuan.runrunv2.IdentityStartupGuard|JDBC/持久化使用者|0||
|com.zhangxuan.runrunv2.LocalTestBootstrap|JDBC/持久化使用者|0|42|
|com.zhangxuan.runrunv2.MockPaymentAdapter|JDBC/持久化使用者|0||
|com.zhangxuan.runrunv2.NotificationOutboxWorker|JDBC/持久化使用者|0||
|com.zhangxuan.runrunv2.NotificationService|JDBC/持久化使用者|4||
|com.zhangxuan.runrunv2.OrderIdempotency|JDBC/持久化使用者|0|18|
|com.zhangxuan.runrunv2.OrderService|JDBC/持久化使用者|2||
|com.zhangxuan.runrunv2.OrderStore|JDBC/持久化使用者|0|24|
|com.zhangxuan.runrunv2.PaymentService|JDBC/持久化使用者|11|328, 358|
|com.zhangxuan.runrunv2.ServiceConfigService|JDBC/持久化使用者|3||

## 调度和启动初始化

|类|所在行|
|---|---|
|com.zhangxuan.runruncampus.auth.AdminAccountInitializer|10, 18|
|com.zhangxuan.runruncampus.auth.UserRoleSchemaMigration|4, 12|
|com.zhangxuan.runruncampus.config.ProductionConfigurationValidator|8, 16|
|com.zhangxuan.runruncampus.courier.CourierCertificationMaintenance|29|
|com.zhangxuan.runruncampus.credit.RiskSignalMaintenance|19|
|com.zhangxuan.runruncampus.message.PushDeliveryProcessor|27|
|com.zhangxuan.runruncampus.payment.WechatRefundProcessor|31|
|com.zhangxuan.runrunv2.CourierEvidenceGovernanceService|63|
|com.zhangxuan.runrunv2.FulfillmentCompletionJob|13|
|com.zhangxuan.runrunv2.IdentityStartupGuard|6, 11|
|com.zhangxuan.runrunv2.LocalTestBootstrap|4, 16|
|com.zhangxuan.runrunv2.NotificationOutboxWorker|43|
|com.zhangxuan.runrunv2.PaymentExpiryJob|14|

## 超长/高风险类

|类|行数|事务数|
|---|---:|---:|
|com.zhangxuan.runruncampus.order.OrderService|573|21|
|com.zhangxuan.runruncampus.order.CampusOrder|442|0|
|com.zhangxuan.runrunv2.PaymentService|388|11|
|com.zhangxuan.runruncampus.courier.CourierCertificationService|299|11|
|com.zhangxuan.runrunv2.FulfillmentService|298|15|
|com.zhangxuan.runruncampus.payment.PaymentService|248|8|
|com.zhangxuan.runruncampus.auth.AdminMfaService|225|5|
|com.zhangxuan.runruncampus.auth.AuthService|214|8|
|com.zhangxuan.runruncampus.payment.ManualQrPaymentService|199|6|
|com.zhangxuan.runruncampus.credit.CreditService|196|7|
|com.zhangxuan.runrunv2.IdentityService|179|5|
|com.zhangxuan.runruncampus.admin.AdminAccountService|134|8|
|com.zhangxuan.runrunv2.CourierCertificationV2Service|128|6|
|com.zhangxuan.runruncampus.membership.MembershipService|121|6|
|com.zhangxuan.runruncampus.address.AddressService|109|5|
|com.zhangxuan.runruncampus.admin.AdminUserService|106|5|

## 完整文件清单

|文件|行数|
|---|---:|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/address/AddressController.java|67|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/address/AddressRepository.java|12|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/address/AddressService.java|109|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/address/UserAddress.java|85|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminAccountController.java|103|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminAccountService.java|134|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminAuditAction.java|45|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminAuditController.java|58|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminAuditLog.java|65|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminAuditLogRepository.java|39|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminAuditService.java|50|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminAuditTargetType.java|14|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminCommandCenterController.java|27|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminCommandCenterService.java|142|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminCouponService.java|54|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminCourierController.java|117|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminCourierService.java|145|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminDashboardController.java|39|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminDashboardService.java|50|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminDispatchController.java|44|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminDispatchService.java|218|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminFeedbackController.java|66|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminFeedbackService.java|94|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminIssueController.java|97|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminIssueService.java|149|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminOrderController.java|188|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminOrderDeliveryProofController.java|60|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminOrderLifecycleController.java|49|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminOrderLifecycleService.java|114|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminOrderService.java|155|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminPermission.java|28|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminPushDeliveryController.java|41|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminRole.java|47|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminUserController.java|104|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminUserService.java|106|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/CampusDispatchExperimentController.java|28|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/CampusDispatchExperimentService.java|105|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/FulfillmentProvenanceController.java|51|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/FulfillmentProvenanceService.java|160|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/api/ApiExceptionHandler.java|41|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/api/ApiRateLimitFilter.java|85|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/api/ApiRateLimitService.java|73|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/api/AppVersionController.java|45|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/api/HealthController.java|17|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/api/PlatformSettingsController.java|47|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/api/PlatformSettingsService.java|61|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/api/ServiceConfigurationController.java|38|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/auth/AdminAccountInitializer.java|49|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/auth/AdminMfaController.java|74|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/auth/AdminMfaCredential.java|37|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/auth/AdminMfaCrypto.java|128|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/auth/AdminMfaRepository.java|12|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/auth/AdminMfaService.java|225|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/auth/AuthController.java|146|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/auth/AuthService.java|214|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/auth/LoginAttemptService.java|70|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/auth/LoginCaptchaService.java|126|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/auth/PasswordConfig.java|15|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/auth/Session.java|75|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/auth/SessionClientType.java|17|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/auth/SessionRepository.java|19|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/auth/UserRoleSchemaMigration.java|46|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/config/ProductionConfigurationValidator.java|95|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/coupon/CouponController.java|27|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/coupon/CouponService.java|89|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/coupon/UserCoupon.java|70|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/coupon/UserCouponRepository.java|21|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/courier/CourierCertification.java|179|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/courier/CourierCertificationController.java|108|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/courier/CourierCertificationMaintenance.java|65|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/courier/CourierCertificationRepository.java|39|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/courier/CourierCertificationService.java|299|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/courier/CourierCertificationStatus.java|9|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/courier/CourierOperationsController.java|53|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/courier/CourierOperationsService.java|83|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/credit/AdminCreditController.java|83|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/credit/CreditController.java|32|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/credit/CreditEvent.java|78|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/credit/CreditEventRepository.java|14|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/credit/CreditEventType.java|8|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/credit/CreditProfile.java|48|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/credit/CreditProfileRepository.java|18|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/credit/CreditRiskLevel.java|14|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/credit/CreditService.java|196|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/credit/RiskSignal.java|93|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/credit/RiskSignalMaintenance.java|99|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/credit/RiskSignalRepository.java|22|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/credit/RiskSignalStatus.java|8|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/credit/RiskSignalType.java|8|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/feedback/FeedbackController.java|39|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/feedback/FeedbackHandlingStatus.java|8|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/feedback/FeedbackRepository.java|62|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/feedback/FeedbackService.java|43|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/feedback/FeedbackType.java|8|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/feedback/UserFeedback.java|79|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/membership/MembershipController.java|36|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/membership/MembershipPlanType.java|25|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/membership/MembershipService.java|121|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/membership/UserMembership.java|73|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/membership/UserMembershipRepository.java|24|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/message/AliyunPushNotificationGateway.java|39|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/message/AliyunPushRequestFactory.java|61|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/message/OrderMessage.java|63|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/message/OrderMessageController.java|37|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/message/OrderMessageRepository.java|13|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/message/OrderMessageService.java|60|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/message/OrderMessageType.java|18|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/message/OrderPushListener.java|22|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/message/OrderPushRequested.java|28|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/message/PushDeliveryProcessor.java|63|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/message/PushDeliveryRepository.java|111|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/message/PushDeliveryStatus.java|6|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/message/PushDeliveryTask.java|6|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/message/PushDestination.java|6|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/message/PushNotificationGateway.java|6|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/CampusOrder.java|442|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/CourierReassignmentService.java|57|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/CourierSettlementStatus.java|8|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/DeliveryZone.java|22|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/LocalOrderProofStorage.java|46|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/ManualPaymentStatus.java|10|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderChatMessage.java|49|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderChatRepository.java|32|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderChatService.java|87|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderController.java|330|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderEvent.java|43|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderEventRepository.java|10|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderIssue.java|84|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderIssueHandlingStatus.java|8|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderIssueRepository.java|97|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderIssueService.java|85|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderIssueType.java|10|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderPaymentMethod.java|8|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderPaymentStatus.java|9|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderProof.java|69|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderProofRepository.java|13|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderProofService.java|167|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderProofStorage.java|13|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderProofType.java|8|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderRepository.java|260|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderService.java|573|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderStatus.java|11|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OssOrderProofStorage.java|107|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/PickupStation.java|25|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/ServiceType.java|33|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/DisabledWechatPayGateway.java|19|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/ManualQrPaymentConfiguration.java|10|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/ManualQrPaymentProperties.java|7|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/ManualQrPaymentService.java|199|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/PaymentBusinessType.java|7|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/PaymentController.java|54|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/PaymentService.java|248|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/PaymentStatus.java|8|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/RefundStatus.java|8|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/WechatPayApiV3Gateway.java|200|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/WechatPayConfiguration.java|23|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/WechatPayGateway.java|23|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/WechatPayment.java|107|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/WechatPaymentRepository.java|28|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/WechatPayProperties.java|10|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/WechatRefund.java|48|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/WechatRefundProcessor.java|60|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/WechatRefundRepository.java|32|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/privacy/PrivacyController.java|61|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/privacy/PrivacyService.java|141|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/RunRunCampusBackendApplication.java|14|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/user/AppUser.java|163|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/user/UserRepository.java|116|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/user/UserRole.java|8|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/CourierCertificationV2Controller.java|30|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/CourierCertificationV2Service.java|128|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/CourierEvidenceGovernanceController.java|24|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/CourierEvidenceGovernanceService.java|91|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/DeploymentIdentityService.java|104|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/DisabledWechatGateway.java|13|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/DisabledWechatSubscriptionGateway.java|13|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/FulfillmentCompletionJob.java|16|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/FulfillmentController.java|37|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/FulfillmentEvidence.java|24|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/FulfillmentModels.java|31|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/FulfillmentQueries.java|70|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/FulfillmentService.java|298|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/IdentityAdminCommand.java|35|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/IdentityApplication.java|30|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/IdentityController.java|44|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/IdentityErrorHandler.java|55|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/IdentityFailure.java|15|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/IdentityPermission.java|15|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/IdentitySecrets.java|52|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/IdentityService.java|179|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/IdentityStartupGuard.java|25|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/IdentityWebConfiguration.java|61|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/LocalTestBootstrap.java|65|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/LocalTestCors.java|30|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/MockPaymentAdapter.java|41|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/NotificationController.java|30|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/NotificationOutboxWorker.java|75|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/NotificationService.java|39|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/OrderConfiguration.java|25|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/OrderController.java|60|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/OrderIdempotency.java|33|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/OrderJson.java|20|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/OrderModels.java|47|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/OrderService.java|67|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/OrderStore.java|35|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/OriginalPaymentWriter.java|10|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/PaymentController.java|26|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/PaymentExpiryJob.java|17|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/PaymentModels.java|22|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/PaymentService.java|388|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/PaymentTestController.java|21|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/ServiceConfigService.java|143|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/V2StudentCardStorage.java|13|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/WechatGateway.java|7|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/WechatHttpGateway.java|77|
|handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/WechatSubscriptionGateway.java|9|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/address/AddressServiceTest.java|113|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/AdminAccountServiceTest.java|175|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/AdminAuthorizationControllerTest.java|195|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/AdminCommandCenterPostgresIntegrationTest.java|291|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/AdminCouponPostgresIntegrationTest.java|119|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/AdminCouponServiceTest.java|85|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/AdminCourierServiceTest.java|90|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/AdminDispatchServiceTest.java|211|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/AdminFeedbackServiceTest.java|57|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/AdminIssueResultNotificationTest.java|57|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/AdminIssueServiceTest.java|64|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/AdminListPostgresIntegrationTest.java|203|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/AdminOrderDeliveryProofControllerTest.java|79|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/AdminOrderLifecycleServiceTest.java|183|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/AdminOrderServiceTest.java|146|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/AdminPaginationPostgresIntegrationTest.java|218|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/AdminPushDeliveryControllerTest.java|35|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/AdminUserServiceTest.java|73|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/CampusDispatchExperimentServiceTest.java|62|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/admin/FulfillmentProvenanceServiceTest.java|100|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/api/ApiExceptionHandlerTest.java|31|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/api/ApiRateLimitFilterTest.java|64|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/api/ApiRateLimitServiceTest.java|75|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/api/AppVersionControllerTest.java|25|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/auth/AdminMfaCryptoTest.java|57|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/auth/AdminMfaPostgresIntegrationTest.java|336|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/auth/AuthControllerRateLimitTest.java|63|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/auth/AuthControllerTest.java|29|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/auth/AuthPasswordPostgresIntegrationTest.java|75|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/auth/AuthServiceTest.java|238|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/auth/LoginAttemptServiceTest.java|61|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/auth/LoginCaptchaServiceTest.java|39|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/auth/UserRoleSchemaMigrationTest.java|48|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/config/ProductionConfigurationValidatorTest.java|68|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/config/ProductionFoundationTest.java|80|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/coupon/CouponServiceTest.java|142|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/courier/CourierCertificationGovernanceTest.java|36|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/courier/CourierCertificationMaintenanceTest.java|103|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/courier/CourierCertificationPostgresIntegrationTest.java|71|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/courier/CourierCertificationServiceTest.java|218|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/courier/CourierOperationsServiceTest.java|100|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/credit/CreditPostgresIntegrationTest.java|80|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/credit/CreditServiceTest.java|119|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/feedback/FeedbackServiceTest.java|99|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/membership/MembershipServiceTest.java|150|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/message/AliyunPushRequestFactoryTest.java|47|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/message/OrderMessageServiceTest.java|123|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/message/PushDeliveryPostgresIntegrationTest.java|98|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/message/PushDeliveryProcessorTest.java|58|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/order/CourierReassignmentServiceTest.java|117|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/order/OrderAuthorizationTest.java|174|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/order/OrderChatPostgresIntegrationTest.java|131|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/order/OrderChatServiceTest.java|133|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/order/OrderConcurrencyPostgresIntegrationTest.java|524|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/order/OrderIssueServiceTest.java|134|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/order/OrderMinePostgresIntegrationTest.java|101|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/order/OrderProofServiceTest.java|235|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/order/OrderServiceTest.java|949|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/payment/ManualQrPaymentServiceTest.java|126|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/payment/PaymentServiceTest.java|221|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/payment/WechatPaymentTest.java|52|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/privacy/PrivacyPostgresIntegrationTest.java|85|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runruncampus/privacy/PrivacyServiceTest.java|141|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runrunv2/CourierCertificationAdminListDatabaseTest.java|83|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runrunv2/CourierCertificationV2DatabaseE2ETest.java|41|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runrunv2/CourierEvidenceGovernanceServiceTest.java|42|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runrunv2/FulfillmentIntegrationTest.java|402|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runrunv2/IdentityIntegrationTest.java|313|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runrunv2/LocalTestBoundaryTest.java|45|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runrunv2/NotificationRuntimeTest.java|42|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runrunv2/OrderIntegrationTest.java|266|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runrunv2/OrderRequestJsonContractTest.java|15|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runrunv2/PaymentEnvironmentTest.java|30|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runrunv2/PaymentIntegrationTest.java|302|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runrunv2/V2FeatureWiringIntegrationTest.java|203|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runrunv2/V2JdbcContractDatabaseTest.java|80|
|handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runrunv2/WechatHttpGatewayTest.java|75|
