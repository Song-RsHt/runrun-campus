# runrunv2 整改验证附件

日期：2026-11-05（Asia/Shanghai）。代码：`fce5f51269da99ef65545186c38595f95a810531`；基线：`39bd56a5a22388da8198e9dda0c618e7c97f13db`。

环境：Windows / PowerShell 7、JDK 17.0.18、Maven 3.9.16、Docker Desktop Engine 29.9.2、真实 PostgreSQL 16-alpine 容器。没有生产、真实微信或真实支付连接。

原始逐次命令结果保存在 `D:/biancheng/RunRunCampus-worktrees/rrc-fix-runrunv2-evidence/`，没有放入 Git。下列收录每套件完整计数和验收脚本输出，不复制可能含认证上下文的整套应用日志、Surefire XML、env、构建产物或缓存。末尾提供原始日志文件校验值；独立 QA 可按原始命令重跑。

## 命令及退出状态

后端工作目录为 `<ROOT>/handover-2026-10-24/RunRunCampusBackend`。ROOT 是独立 worktree，不是主工作区。

|工作目录|命令|退出状态与原始文件|
|---|---|---|
|后端|`mvn -B -q -Pv2-identity -DskipTests package`|基线 0：01-package.log；最终 0：27-package-delivery.log。quiet 模式结果为空，没有隐藏错误输出|
|后端|`mvn -B -q -Pv2-identity test-compile`|基线 0：02-test-compile.log；最终 0：28-test-compile-delivery.log，结果为空|
|后端|`pwsh -NoProfile -File tools/run-v2-isolated-tests.ps1`|基线 0：03-isolated-baseline.log，99 项通过；原始退出超时提示保留|
|ROOT|`Copy-Item .env.v2-test.example .env.v2-test`，随后 `pwsh -NoProfile -File tools/initialize-v2-environment.ps1 -ConfirmIsolatedTest`|0：04-initialize.log；本地随机值没有打印|
|ROOT|`docker compose --env-file .env.v2-test -f compose.v2-test.yml up -d --build`|基线 0：04-compose-build.log；最终代码 0：25-compose-delivery.log|
|ROOT|`rg -n "package com\.zhangxuan\.runrunv2\|import com\.zhangxuan\.runrunv2" handover-2026-10-24/RunRunCampusBackend/src`|0：05-source-index.log；另有 19-jdbc-index.log|
|ROOT|`pwsh -NoProfile -File tools/verify-v2-environment.ps1`|基线 0：06-environment.log；最终 0：33-environment-delivery.log|
|ROOT|`pwsh -NoProfile -File server/db/tools/verify-m07.ps1`|0：07-m07.log|
|ROOT|`pwsh -NoProfile -File server/db/tools/verify-m08.ps1 -PrerequisiteRoot D:/biancheng/RunRunCampus-worktrees/rrc-fix-runrunv2`|0：08-m08.log|
|后端|`mvn -B -q -Pv2-identity '-Dtest=V2JdbcContractDatabaseTest' test`|重现错误退出 1：09-jdbc-before.log（3 errors）、10-jdbc-after-initial.log（仅可空审计参数仍 error）；最终专项 3/3 通过|
|后端|`mvn -B -ntp -Pv2-identity '-Dtest=V2FeatureWiringIntegrationTest' test`|重现错误退出 1：20-feature-before.log（2 failures）；multipart 根因单跑退出 1：21-multipart-before.log；修复后 4/4 通过：22-feature-after.log|
|后端|`mvn -B -q -Pv2-identity '-Dtest=V2FeatureWiringIntegrationTest#malformedMultipartFieldsAreValidationErrorsWithoutWritingAFile' test`|重现错误退出 1：23-binding-before.log（预期 400、实际 503）；最终专项 5/5 通过|
|后端|`mvn -B -ntp -Pv2-identity test`|最终 0：24-full-delivery.log，483 项全部通过；此前 12-full-maven.log 为 478 项通过，不与最终新增测试重复计数|
|ROOT|`pwsh -NoProfile -File tools/run-v2-isolated-tests.ps1`|最终 0：29-isolated-final.log，120 项全部通过；中间 114/115 项结果分别保存，不累加为总数|
|ROOT|`git diff --check`，`git diff --check 39bd56a5a22388da8198e9dda0c618e7c97f13db..fce5f51269da99ef65545186c38595f95a810531`|0，无输出|
|ROOT|`git -c safe.directory=D:/biancheng/RunRunCampus/.git clone --no-local --branch fix/runrunv2-compile-errors D:/biancheng/RunRunCampus-worktrees/rrc-fix-runrunv2 D:/biancheng/RunRunCampus-worktrees/rrc-fix-runrunv2-evidence/clean-clone`|0：30-clone.log；HEAD 为代码提交，初始 status 干净，没有复制 env、target 或缓存|
|干净 clone 后端|上述 package、test-compile 两条原始命令|均 0：31-clone-package.log、32-clone-test-compile.log；结果为空|

本地 clone 从已提交 Git 对象产生，并非 GitHub 发布验证；没有推送分支。Docker 冷构建实际下载依赖，耗时较长，没有改 Dockerfile、跳过测试编译或复制宿主 target 来绕过构建。

## 首次失败分类

失败均为新增真实回归揭露的运行时问题，不是原先的 Java 测试编译失败。

- `V2JdbcContractDatabaseTest.performanceExecutesAgainstTheDeliveredSchema`：SQL syntax error at or near month。
- `V2JdbcContractDatabaseTest.evidenceHoldPersistsItsInstantAndNullableOrderReference`：Instant SQL 类型无法推断；修复后继续揭露可空审计参数类型无法推断。
- `V2JdbcContractDatabaseTest.deletionRequestPersistsItsInstantWithoutChangingTheResource`：Instant SQL 类型无法推断。
- `V2FeatureWiringIntegrationTest.multipartTextVersionUploadReadAndApprovalUseRealWiring`、`cleanupRetriesStorageFailureAndDoesNotRevokeEligibility`：合法文本字段触发 MissingServletRequestPartException，503。
- `V2FeatureWiringIntegrationTest.malformedMultipartFieldsAreValidationErrorsWithoutWritingAFile`：非法文本版本号返回 503，契约要求验证错误 400。

最终失败测试名单为空；v2、旧应用、环境均没有最终测试失败。完整 Maven 的旧应用上下文退出超时提示单独保留，不能等同于 483 个测试失败或忽略为没有发生。

## 最终全后端套件结果

```text
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.595 s -- in com.zhangxuan.runruncampus.address.AddressServiceTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.390 s -- in com.zhangxuan.runruncampus.admin.AdminAccountServiceTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.540 s -- in com.zhangxuan.runruncampus.admin.AdminAuthorizationControllerTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 20.18 s -- in com.zhangxuan.runruncampus.admin.AdminCommandCenterPostgresIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.106 s -- in com.zhangxuan.runruncampus.admin.AdminCouponPostgresIntegrationTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.088 s -- in com.zhangxuan.runruncampus.admin.AdminCouponServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.361 s -- in com.zhangxuan.runruncampus.admin.AdminCourierServiceTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.573 s -- in com.zhangxuan.runruncampus.admin.AdminDispatchServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.157 s -- in com.zhangxuan.runruncampus.admin.AdminFeedbackServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.096 s -- in com.zhangxuan.runruncampus.admin.AdminIssueResultNotificationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.015 s -- in com.zhangxuan.runruncampus.admin.AdminIssueServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.615 s -- in com.zhangxuan.runruncampus.admin.AdminListPostgresIntegrationTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.225 s -- in com.zhangxuan.runruncampus.admin.AdminOrderDeliveryProofControllerTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.025 s -- in com.zhangxuan.runruncampus.admin.AdminOrderLifecycleServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.015 s -- in com.zhangxuan.runruncampus.admin.AdminOrderServiceTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.497 s -- in com.zhangxuan.runruncampus.admin.AdminPaginationPostgresIntegrationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.072 s -- in com.zhangxuan.runruncampus.admin.AdminPushDeliveryControllerTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.008 s -- in com.zhangxuan.runruncampus.admin.AdminUserServiceTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.011 s -- in com.zhangxuan.runruncampus.admin.CampusDispatchExperimentServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.020 s -- in com.zhangxuan.runruncampus.admin.FulfillmentProvenanceServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in com.zhangxuan.runruncampus.api.ApiExceptionHandlerTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.033 s -- in com.zhangxuan.runruncampus.api.ApiRateLimitFilterTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in com.zhangxuan.runruncampus.api.ApiRateLimitServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.002 s -- in com.zhangxuan.runruncampus.api.AppVersionControllerTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.074 s -- in com.zhangxuan.runruncampus.auth.AdminMfaCryptoTest
[INFO] Tests run: 22, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 13.31 s -- in com.zhangxuan.runruncampus.auth.AdminMfaPostgresIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.108 s -- in com.zhangxuan.runruncampus.auth.AuthControllerRateLimitTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.068 s -- in com.zhangxuan.runruncampus.auth.AuthControllerTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.887 s -- in com.zhangxuan.runruncampus.auth.AuthPasswordPostgresIntegrationTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.107 s -- in com.zhangxuan.runruncampus.auth.AuthServiceTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in com.zhangxuan.runruncampus.auth.LoginAttemptServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.012 s -- in com.zhangxuan.runruncampus.auth.LoginCaptchaServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in com.zhangxuan.runruncampus.auth.UserRoleSchemaMigrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in com.zhangxuan.runruncampus.config.ProductionConfigurationValidatorTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.014 s -- in com.zhangxuan.runruncampus.config.ProductionFoundationTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.010 s -- in com.zhangxuan.runruncampus.coupon.CouponServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.012 s -- in com.zhangxuan.runruncampus.courier.CourierCertificationGovernanceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.016 s -- in com.zhangxuan.runruncampus.courier.CourierCertificationMaintenanceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.164 s -- in com.zhangxuan.runruncampus.courier.CourierCertificationPostgresIntegrationTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.019 s -- in com.zhangxuan.runruncampus.courier.CourierCertificationServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.087 s -- in com.zhangxuan.runruncampus.courier.CourierOperationsServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.813 s -- in com.zhangxuan.runruncampus.credit.CreditPostgresIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.292 s -- in com.zhangxuan.runruncampus.credit.CreditServiceTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.008 s -- in com.zhangxuan.runruncampus.feedback.FeedbackServiceTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.103 s -- in com.zhangxuan.runruncampus.membership.MembershipServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.015 s -- in com.zhangxuan.runruncampus.message.AliyunPushRequestFactoryTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.186 s -- in com.zhangxuan.runruncampus.message.OrderMessageServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.917 s -- in com.zhangxuan.runruncampus.message.PushDeliveryPostgresIntegrationTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.099 s -- in com.zhangxuan.runruncampus.message.PushDeliveryProcessorTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.008 s -- in com.zhangxuan.runruncampus.order.CourierReassignmentServiceTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.263 s -- in com.zhangxuan.runruncampus.order.OrderAuthorizationTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.557 s -- in com.zhangxuan.runruncampus.order.OrderChatPostgresIntegrationTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.188 s -- in com.zhangxuan.runruncampus.order.OrderChatServiceTest
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 8.219 s -- in com.zhangxuan.runruncampus.order.OrderConcurrencyPostgresIntegrationTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.014 s -- in com.zhangxuan.runruncampus.order.OrderIssueServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.024 s -- in com.zhangxuan.runruncampus.order.OrderMinePostgresIntegrationTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.188 s -- in com.zhangxuan.runruncampus.order.OrderProofServiceTest
[INFO] Tests run: 55, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.121 s -- in com.zhangxuan.runruncampus.order.OrderServiceTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.010 s -- in com.zhangxuan.runruncampus.payment.ManualQrPaymentServiceTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.300 s -- in com.zhangxuan.runruncampus.payment.PaymentServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in com.zhangxuan.runruncampus.payment.WechatPaymentTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.273 s -- in com.zhangxuan.runruncampus.privacy.PrivacyPostgresIntegrationTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.026 s -- in com.zhangxuan.runruncampus.privacy.PrivacyServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.160 s -- in com.zhangxuan.runrunv2.CourierCertificationAdminListDatabaseTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.613 s -- in com.zhangxuan.runrunv2.CourierCertificationV2DatabaseE2ETest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.213 s -- in com.zhangxuan.runrunv2.CourierEvidenceGovernanceServiceTest
[INFO] Tests run: 30, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 9.421 s -- in com.zhangxuan.runrunv2.FulfillmentIntegrationTest
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.083 s -- in com.zhangxuan.runrunv2.IdentityIntegrationTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.425 s -- in com.zhangxuan.runrunv2.LocalTestBoundaryTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.542 s -- in com.zhangxuan.runrunv2.NotificationRuntimeTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.649 s -- in com.zhangxuan.runrunv2.OrderIntegrationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.003 s -- in com.zhangxuan.runrunv2.OrderRequestJsonContractTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.196 s -- in com.zhangxuan.runrunv2.PaymentEnvironmentTest
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.480 s -- in com.zhangxuan.runrunv2.PaymentIntegrationTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.207 s -- in com.zhangxuan.runrunv2.V2FeatureWiringIntegrationTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.935 s -- in com.zhangxuan.runrunv2.V2JdbcContractDatabaseTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.151 s -- in com.zhangxuan.runrunv2.WechatHttpGatewayTest
[ERROR] Surefire is going to kill self fork JVM. The exit has elapsed 30 seconds after System.exit(0).
[INFO] Tests run: 483, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## 最终 v2 专项

```text
PASS: PostgreSQL 16 v2 chain initialized in runrun_v2; public sentinel isolated (44 tables)
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.177 s -- in com.zhangxuan.runrunv2.CourierCertificationAdminListDatabaseTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.544 s -- in com.zhangxuan.runrunv2.CourierCertificationV2DatabaseE2ETest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.343 s -- in com.zhangxuan.runrunv2.CourierEvidenceGovernanceServiceTest
[INFO] Tests run: 30, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 16.81 s -- in com.zhangxuan.runrunv2.FulfillmentIntegrationTest
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.681 s -- in com.zhangxuan.runrunv2.IdentityIntegrationTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.240 s -- in com.zhangxuan.runrunv2.LocalTestBoundaryTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.773 s -- in com.zhangxuan.runrunv2.NotificationRuntimeTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.747 s -- in com.zhangxuan.runrunv2.OrderIntegrationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.004 s -- in com.zhangxuan.runrunv2.OrderRequestJsonContractTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.163 s -- in com.zhangxuan.runrunv2.PaymentEnvironmentTest
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.763 s -- in com.zhangxuan.runrunv2.PaymentIntegrationTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.697 s -- in com.zhangxuan.runrunv2.V2FeatureWiringIntegrationTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.041 s -- in com.zhangxuan.runrunv2.V2JdbcContractDatabaseTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.109 s -- in com.zhangxuan.runrunv2.WechatHttpGatewayTest
[INFO] Tests run: 120, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## M07 与 M08 完整输出

### 07-m07.log

```text
PASS: repeated M07 migration rejected
PASS: isolated v2 certification/evidence schema

```

### 08-m08.log

```text
PASS: M00→M07→M08 strict migration, duplicate rejection, ownership and rollback guard

```

## 源码引用对象的实际数据库核对

从 v2 主源码提取 runrun_v2 的直接对象引用，在本地 Compose PostgreSQL 使用 to_regclass 和 pg_proc/pg_namespace 核对。该检查只验证对象存在，不冒充每条 SQL 或全部业务分支均已执行。

```text
admin_audit_logs|RELATION
app_users|RELATION
claim_notification_outbox|FUNCTION
complete_notification_outbox|FUNCTION
courier_certification_events|RELATION
courier_certifications|RELATION
delivery_zones|RELATION
evidence_deletion_audits|RELATION
evidence_holds|RELATION
fulfillment_completion_blocks|RELATION
fulfillment_tasks|RELATION
idempotency_requests|RELATION
identity_audit_events|RELATION
identity_tickets|RELATION
location_events|RELATION
mock_channel_results|RELATION
notification_outbox|RELATION
payment_adjustments|RELATION
payment_admin_audit|RELATION
payment_attempts|RELATION
payment_financial_events|RELATION
payment_orders|RELATION
payment_result_events|RELATION
platform_settings|RELATION
refund_orders|RELATION
run_notification_review_timers|FUNCTION
service_points|RELATION
user_notifications|RELATION
user_sessions|RELATION
v2_order_events|RELATION
v2_order_proofs|RELATION
v2_orders|RELATION
wechat_identities|RELATION

```

## 最终 Compose 构建及环境验证

实际执行仓库 Dockerfile 和 up -d --build，退出 0；以下仅省略镜像层及依赖下载进度，完整构建日志单独保存。

```text
#12 651.0 [INFO] Recompiling the module because of changed source code.
#12 651.0 [INFO] Compiling 216 source files with javac [debug parameters release 17] to target/classes
#12 654.9 [INFO] Recompiling the module because of changed dependency.
#12 654.9 [INFO] Compiling 77 source files with javac [debug parameters release 17] to target/test-classes
#12 684.7 [INFO] Tests are skipped.
#12 734.2 [INFO] BUILD SUCCESS
#12 734.2 [INFO] Total time:  12:10 min
 Image rrc-v2-local-test-backend Built
 Container rrc-v2-local-test-backend-1 Recreated
 Container rrc-v2-local-test-postgres-1 Healthy
 Container rrc-v2-local-test-migrate-1 Started
 Container rrc-v2-local-test-migrate-1 Exited
 Container rrc-v2-local-test-backend-1 Started
```

最终 verify-v2-environment.ps1 的完整输出，退出 0：

```text
PASS: Docker engine available
PASS: Compose available
PASS: only isolated database and test environment
PASS: mock payment and dedicated v2 profile enabled
PASS: backend container listens on 0.0.0.0:8082
PASS: dedicated Compose bridge network (no shared external network)
PASS: database has no published port
PASS: synthetic administrator explicitly authorized
PASS: no injected real WeChat/payment or legacy datasource overrides
PASS: postgres exists
PASS: postgres healthy
PASS: backend exists
PASS: backend healthy
PASS: non-root backend can write isolated evidence volume
PASS: runrun_v2 schema exists
PASS: all 44 M00-M08 v2 tables exist
PASS: v2 public schema has no legacy tables/Flyway history
PASS: separate legacy public sentinel unchanged
PASS: M08 notification runtime functions exist
 Container rrc-v2-local-test-migrate-run-4a17ba757123 Creating
 Container rrc-v2-local-test-migrate-run-4a17ba757123 Created
PASS: all 12 M00-M08 plus M09 repair receipts match source checksums
PASS: isolated administrator enabled
PASS: controlled administrator initialization audited once
PASS: local admin browser CORS preflight accepted
PASS: GET /api/v2/auth/session anonymous => 401 (never 404)
PASS: POST /api/v2/auth/logout anonymous => 401 (never 404)
PASS: GET /api/v2/notifications anonymous => 401 (never 404)
PASS: GET /api/v2/courier/certification anonymous => 401 (never 404)
PASS: synthetic administrator password login succeeds
PASS: independent v2 session issued (not printed)
PASS: v2 session authenticates
PASS: authenticated notifications reachable
PASS: ADMIN certification list ?page=0&size=20 => 200
PASS: certification list returns a page
PASS: ADMIN certification list ?status=PENDING&page=0&size=20 => 200
PASS: certification list returns a page
PASS: ADMIN certification list ?query=test-courier&page=0&size=20 => 200
PASS: certification list returns a page
PASS: ADMIN cannot use COURIER-only certification query
PASS: real WeChat gateway disabled
PASS: logout revokes v2 session
PASS: revoked session rejected
NOT RUN: client builds; rerun with -BuildClients (Node/pnpm required).

```

NOT RUN 的 client builds 是明确未运行项；本轮没有验收前端、真机或公网。Dockerfile 的 Tests are skipped 仅适用于其原有 -DskipTests 打包步骤，测试类仍真实编译；120 项 v2 及 483 项全后端测试已在上节的独立 Maven 命令实际执行。

## 干净本地 clone

HEAD=fce5f51269da99ef65545186c38595f95a810531；git status --short 初始无输出；package exit=0；test-compile exit=0；不运行默认旧应用入口。

## 原始日志 SHA-256

原始文件位于附件开头给出的独立证据目录；网络诊断临时日志不收录。

|文件|SHA-256|
|---|---|
|01-package.log|e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855|
|02-test-compile.log|e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855|
|03-isolated-baseline.log|b57601be5aa84531ae5472c4ca7eb40036bd949d6a121e4b16b8cd8615ebd1a5|
|04-compose-build.log|74e36056241b1afd45fa8b8865e6afb120dcf6129810cc09d5fcec7402ccc088|
|04-initialize.log|eeed910b5c170f66d9d20effbfe8867f39a35d5cc97d19e49668ee2bf332e73e|
|05-source-index.log|0f913d8224258ffc26a9d4de52d840fdb338e2e9ac1c69c3713118b997a5dc57|
|06-environment.log|0702acab123c9a8780e858c418e4cd05fe7b1bfc72041b4dcf60b9430586065f|
|07-m07.log|1ea4948e9fd480875a0496b1cdbb59d7e95f4267767f87c377bb87a66e275cf5|
|08-m08.log|d8939b57205b0eff0fb2c8986e988e39bcd35844083dcc6b1de8474e14c866cd|
|09-jdbc-before.log|6a0165b04fa5e59b6e6218109ebc8e22e24b974dfd44f866960ee88971a3eac4|
|10-jdbc-after-initial.log|a1b096a0be3451663c975c0417b7a7f052047986da79e7295c1575b48a0b5987|
|11-isolated-final.log|6e03e1138ffe15334d2a2b1ac8bbb45ccefab1f21ce8742a167a7c1b800bfd4c|
|12-full-maven.log|c6080e856c910ff571ce5c8c386bbd28d6dacf012e6712fc6fdb477a95ce97c2|
|13-compose-final.log|8a2919ebf474f18d4f009e95b03d6ac650061b988c71cedc0479ffc446e1b36e|
|14-clean.log|e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855|
|15-package-final.log|e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855|
|16-test-compile-final.log|e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855|
|18-isolated-delivery.log|439fd00c96fd95daefdeea7ec47cb70fe97d6b84f7510212e48ef25ac170a7b5|
|19-jdbc-index.log|6cda0fc03d296431490544319d7c4db20bcefd17a93bacd3e0ed020b7513a070|
|20-feature-before.log|7b591c60c95c79f1f6ae9e59a07ea87e0743612c366ba42f2b4b409a16db6e72|
|21-multipart-before.log|79960428febfe7f822f01ad0c41e97a27accab035885132db254dc8319de154b|
|22-feature-after.log|21aa5c6d6221382f30c6ef9528795af0d58e5ab741713d6165f046727a494ae2|
|23-binding-before.log|8a93a6c04819a76dcce7b4d6f0b69d62af33c167b6f66ba43617de0036a067fe|
|24-full-delivery.log|db9084e8b0bf62ee94aac49b42a18b5a3e338dba90d5cd9f9b160955edd6316b|
|25-compose-delivery.log|7c3236f13a1df9a52b7515194cef64e4dc31e15100f23fc5b6e0cd22fc8bfc1a|
|26-sql-object-catalog.log|aca495995536d9017e7d81a53e318a15e543ac879e7819137876a4078362d0eb|
|27-package-delivery.log|e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855|
|28-test-compile-delivery.log|e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855|
|29-isolated-final.log|e408de875ee8180f48e0c8336a0dcdaeea631aa9e6d884e358a9c3a78e6be381|
|30-clone.log|22d57f9defe9760004a26a6c099cabf7254251790ae673acf382a4cc13eb50fb|
|31-clone-package.log|e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855|
|32-clone-test-compile.log|e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855|
|33-environment-delivery.log|55cad4d124fd19ab6327693ae9af5cc9dc929e00b815502d6ccb9d29d04d4a3b|
|34-compose-stop.log|9910b9ce3d445f3ffd2ca47d27c46b3ef4592710f27a27b7d0200e6c6e8fa4dc|
