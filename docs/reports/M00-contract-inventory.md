# M00 契约与决策实现状态清单

核查基线：`3da72bc0ef183dcb435bfec1b612e7deba3ee4e7`。日期：2026-10-26。此文件是现状证据，不定义新接口、字段或错误码；唯一技术定义仍为 C。

方法：检查全部控制器路由，并搜索后端源码、迁移、管理端和小程序。未发现 `/api/v2`、`v2_orders`、`wechat_identities`、`v2_admin_enabled`、`code2session`、`runrun_session_v2` 或 `/v2-admin` 实现。继续阅读相近旧服务实际行为，以下“旧能力可复用”不代表契约合规。未运行HTTP集成或真实微信联调。

## 40组接口（C A02中的A01–A40）

| 编号 | 契约路径摘要 | 状态/差距 | 代码核查定位 |
|---|---|---|---|
| A01 | POST `/auth/wechat/login` | 未实现v2；旧入口仅手机号密码；无微信code后端兑换及loginTicket | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/auth/AuthController.java` |
| A02 | POST `/auth/wechat/bind-phone` | 未实现v2；无微信手机号兑换/唯一绑定/旧高权限人工恢复分支 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/auth/AuthService.java` |
| A03 | POST `/auth/password/login` | 未实现v2；可复用密码登录服务；缺v2路径、账号响应及稳定错误体 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/auth/AuthController.java` |
| A04 | POST `/admin/account-recoveries/{userId}/resolve` | 未实现v2；旧账号管理不等于恢复票据审批及绑定审计 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminUserService.java` |
| A05 | PATCH `/admin/access/{userId}` | 未实现v2；无v2资格及安全版本；缺受控首启和请求即时资格校验 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/user/AppUser.java` |
| A06 | GET `/service-config` | 未实现v2；旧设置/枚举可参考；缺版本化区域、GCJ02服务点及结构化营业配置 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/api/PlatformSettingsService.java` |
| A07 | POST `/quotes` | 未实现v2；九档整数分价格匹配；缺独立报价接口及配置快照校验 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/ServiceType.java` |
| A08 | POST `/orders` | 未实现v2；仍建campus_orders、可用月卡/券、人工收款/货到付款；无新待支付事务模型 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderService.java` |
| A09 | GET `/orders`；GET `/order-history`；GET `/order-history/{flowVersion}/{orderId}` | 未实现v2；旧mine/history不满足两源授权后统一游标分页 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderController.java` |
| A10 | GET `/orders/{id}` | 未实现v2；旧详情无v2任务/调整/allowedActions和版本隔离 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderController.java` |
| A11 | POST `/payments/{id}/attempts`；GET `/payments/{id}` | 未实现v2；旧微信支付服务不是MOCK分层支付单/尝试和补款接口 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/PaymentService.java` |
| A12 | POST `/orders/{id}/cancel` | 未实现v2；旧取消规则不能证明四阶段取消、原款退款/补贴事务 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderService.java` |
| A13 | GET `/courier/order-pool`；POST `/orders/{id}/accept` | 未实现v2；旧接单有资格/锁能力；缺原款成功门禁与独立任务容量 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderService.java` |
| A14 | POST `/orders/{id}/proofs` | 未实现v2；旧凭证上传不具v2任务/上传票据/送达后有限权限 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderProofService.java` |
| A15 | POST `/orders/{id}/locations` | 未实现v2；旧位置写入不等于ARRIVAL/DELIVERY不可覆盖事件与服务器采信 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderService.java` |
| A16 | POST `/orders/{id}/pickup` | 未实现v2；旧取件不能证明每单有效取件凭证及独立任务约束 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderService.java` |
| A17 | POST `/orders/{id}/deliver` | 未实现v2；旧完成依收货码；缺双证据送达、实际时间及释放任务容量 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderService.java` |
| A18 | POST `/orders/{id}/confirm` | 未实现v2；缺已送达独立确认窗口与调整/售后同锁完成 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderService.java` |
| A19 | POST `/orders/{id}/adjustments` | 未实现v2；旧package-size流程不是独立调整单及送达后有限申请 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderService.java` |
| A20 | POST `/admin/adjustments/{id}/decision` | 未实现v2；旧规格审批不满足三种管理员决定、独立补款退款及提醒期限 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderService.java` |
| A21 | POST `/orders/{id}/after-sales`；POST `/admin/after-sales/{caseId}/resolve` | 未实现v2；已有问题处理；缺登记即冻结全单、定责与调整补款一并裁定 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderIssueService.java` |
| A22 | POST `/orders/{id}/release`；POST `/admin/orders/{id}/handovers`；POST `/handovers/{id}/confirm` | 未实现v2；已有旧重派；缺已取件双确认交接和独立任务切权 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/CourierReassignmentService.java` |
| A23 | POST `/admin/orders/{id}/refulfillments`；POST `/tasks/{id}/respond` | 未实现v2；旧强制完成/归档不是新OFFERED任务及骑手同意 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminOrderLifecycleService.java` |
| A24 | POST `/orders/{id}/arrival-subsidies` | 未实现v2；无取消前服务器到场事件引用及100米/30分钟100分补贴链路 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderService.java` |
| A25 | GET `/orders/{id}/proofs/{proofId}/access` | 未实现v2；已有受控存储读取；缺未补款所有路径屏蔽及保全/删除状态判定 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderProofService.java` |
| A26 | POST `/admin/orders/{id}/close-with-debt` | 未实现v2；无30天后结案转独立欠款 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderService.java` |
| A27 | POST `/admin/debts/{id}/events` | 未实现v2；无独立欠款还款/免债/终止追偿及审计账本 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/PaymentService.java` |
| A28 | POST `/admin/orders/{id}/refunds`；POST `/admin/orders/{id}/compensations` | 未实现v2；旧支付退款不可作为售后授权、责任守恒和可退余额锁通过证明 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/PaymentService.java` |
| A29 | POST `/admin/orders/{id}/allocations`；POST `/admin/orders/{id}/risk-holds` | 未实现v2；无按任务分配和独立风险冻解聚合 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/admin/AdminOrderLifecycleService.java` |
| A30 | POST `/admin/orders/{id}/payout-registrations` | 未实现v2；旧人工核款/结算标志不是逐单MOCK付款登记及冻结强校验 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/ManualQrPaymentService.java` |
| A31 | GET `/notifications`；POST `/notifications/{id}/read` | 未实现v2；旧消息/推送可复用部分机制；缺v2站内通知及微信订阅outbox | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/message/OrderMessageService.java` |
| A32 | PATCH `/admin/service-config`；GET `/admin/review-queue` | 未实现v2；缺配置乐观版本、有效值班负责人和审核超时队列 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/api/PlatformSettingsService.java` |
| A33 | POST `/test/payment-attempts/{id}/result`；POST `/test/refunds/{id}/result` | 未实现v2；无隔离ADMIN模拟结果入口及生产不注册验证 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/payment/PaymentController.java` |
| A34 | POST `/admin/evidence-holds`；POST `/admin/evidence-holds/{id}/release` | 未实现v2；缺多来源订单/资质保全、解除与删除advisory lock排他 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderProofService.java` |
| A35 | POST `/orders/{id}/uploads` | 未实现v2；旧multipart不等于受控上传票据、资源归属与限量协议 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderProofService.java` |
| A36 | GET `/courier/tasks`；GET `/courier/order-history`；GET `/courier/order-history/{flowVersion}/{orderId}` | 未实现v2；缺本人OFFERED/结束任务列表、两源去重统一分页及只读历史 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/order/OrderController.java` |
| A37 | GET `/courier/certification`；POST `/courier/certification/applications` | 未实现v2；已有申请、重申请保留资格；缺v2契约、共享version/事件及首次账号锁 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/courier/CourierCertificationService.java` |
| A38 | POST `/courier/certification/applications/{id}/student-card` | 未实现v2；已有本人PENDING、8MiB和JPEG/PNG头检查；缺可解码校验、幂等及expectedVersion | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/courier/CourierCertificationService.java` |
| A39 | GET `/admin/courier-certifications`；GET `/admin/courier-certifications/{id}`；GET `/admin/courier-certifications/{id}/student-card` | 未实现v2；已有旧管理员照片读取；缺v2启用资格、photoResourceId和读取审计/删除状态 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/courier/CourierCertificationService.java` |
| A40 | POST `/admin/courier-certifications/{id}/decision` | 未实现v2；已有行锁/审核/批准需照片；缺版本与本轮稳定资源关联、共享增版事件 | `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runruncampus/courier/CourierCertificationService.java` |

## 37个错误码（C E01）

实际ApiExceptionHandler仅构造message，校验/上传错误也无稳定code。逐码搜索主源码没有下列业务码定义；HTTP状态或中文提示相似不能视为实现。

| code | HTTP | 实现状态 |
|---|---|---|
| VALIDATION_ERROR | 400 | 未定义；旧message异常不符合E01 |
| AUTHENTICATION_FAILED | 401 | 未定义；旧message异常不符合E01 |
| WECHAT_AUTH_FAILED | 401 | 未定义；旧message异常不符合E01 |
| FORBIDDEN | 403 | 仅发现HttpStatus.FORBIDDEN等状态常量；错误体不输出E01的code，未实现 |
| V2_ADMIN_NOT_ENABLED | 403 | 未定义；旧message异常不符合E01 |
| ADMIN_ACCESS_MANAGEMENT_FORBIDDEN | 403 | 未定义；旧message异常不符合E01 |
| MFA_REQUIRED | 403 | 未定义；旧message异常不符合E01 |
| MANUAL_RECOVERY_REQUIRED | 403 | 未定义；旧message异常不符合E01 |
| COURIER_NOT_APPROVED | 403 | 未定义；旧message异常不符合E01 |
| DELIVERY_PROOF_PAYMENT_REQUIRED | 403 | 未定义；旧message异常不符合E01 |
| RESOURCE_NOT_FOUND | 404 | 未定义；旧message异常不符合E01 |
| PROOF_EXPIRED | 404 | 未定义；旧message异常不符合E01 |
| CERTIFICATION_STATE_CONFLICT | 409 | 未定义；旧message异常不符合E01 |
| IDENTITY_BINDING_CONFLICT | 409 | 未定义；旧message异常不符合E01 |
| ORDER_FLOW_VERSION_MISMATCH | 409 | 未定义；旧message异常不符合E01 |
| ORDER_STATE_CONFLICT | 409 | 未定义；旧message异常不符合E01 |
| OUTSIDE_BUSINESS_HOURS | 409 | 未定义；旧message异常不符合E01 |
| PAYMENT_RESULT_PENDING | 409 | 未定义；旧message异常不符合E01 |
| PAYMENT_CLOSED | 409 | 未定义；旧message异常不符合E01 |
| IDEMPOTENCY_KEY_CONFLICT | 409 | 未定义；旧message异常不符合E01 |
| COURIER_CAPACITY_EXCEEDED | 409 | 未定义；旧message异常不符合E01 |
| PROOF_NOT_READY | 409 | 未定义；旧message异常不符合E01 |
| ADJUSTMENT_ALREADY_OPEN | 409 | 未定义；旧message异常不符合E01 |
| ADJUSTMENT_UNRESOLVED | 409 | 未定义；旧message异常不符合E01 |
| AFTER_SALES_UNRESOLVED | 409 | 未定义；旧message异常不符合E01 |
| SERVICE_LOCATION_UNCONFIGURED | 409 | 未定义；旧message异常不符合E01 |
| ARRIVAL_EVIDENCE_INVALID | 409 | 未定义；旧message异常不符合E01 |
| SETTLEMENT_FROZEN | 409 | 未定义；旧message异常不符合E01 |
| SETTLEMENT_NOT_ELIGIBLE | 409 | 未定义；旧message异常不符合E01 |
| ALLOCATION_UNAPPROVED | 409 | 未定义；旧message异常不符合E01 |
| AMOUNT_EXCEEDS_AVAILABLE | 409 | 未定义；旧message异常不符合E01 |
| CONFIG_VERSION_CONFLICT | 409 | 未定义；旧message异常不符合E01 |
| REVIEW_OWNER_REQUIRED | 409 | 未定义；旧message异常不符合E01 |
| EVIDENCE_DELETION_IN_PROGRESS | 409 | 未定义；旧message异常不符合E01 |
| UPLOAD_INVALID | 400 | 未定义；旧message异常不符合E01 |
| V1_NEW_ORDER_DISABLED | 410 | 未定义；旧message异常不符合E01 |
| NEW_ORDER_DISABLED | 503 | 未定义；旧message异常不符合E01 |

## 66条决策（D001–D066）

历史修订按最新决定裁决；文档/历史范围与运行功能分开标记。生产事实均未连接核实。

| 编号 | 当前核查结果 |
|---|---|
| D001 | 缺模拟支付及送达确认窗口；旧完成仍校验收货码 |
| D002 | 缺MOCK付款/补款/退款与关单链路 |
| D003 | 旧v1能力存在，未做新旧版本隔离及集成验证 |
| D004 | 旧取消存在，缺新支付四阶段原子规则 |
| D005 | 缺独立调整单及原款不覆盖证明 |
| D006 | 旧规格改动存在，非新管理员审核模型 |
| D007 | 历史措辞，按D040–D045核查，不能另行实现旧口径 |
| D008 | 旧重派存在，缺独立任务/交接双确认和旧任务只读 |
| D009 | 旧问题处理存在，缺新定责/赔付/重新履约事务 |
| D010 | 无v2待支付独立状态及接单池排除证明 |
| D011 | 缺照片+一次位置共同确认及自动完成并发互斥 |
| D012 | 旧上传存在，缺当前任务/状态权限重试契约 |
| D013 | 旧POST /api/v1/orders仍调用create，未见410停止建单门禁 |
| D014 | 九种分价可复用，但旧create仍支持月卡和优惠券 |
| D015 | 无MOCK适配器及生产拒绝启用校验 |
| D016 | V1–V43文件存在；生产执行史无可信证据，V42/V43待核实 |
| D017 | 缺独立账本/冻结/MOCK付款登记 |
| D018 | 旧凭证读取存在，缺新关联任务权限及补款隐藏 |
| D019 | 旧清理存在，缺保全、位置正文删除与失败持久重试；按D053/D054补充 |
| D020 | 已具备G/B/C/D及专题权威文档；源码符合性尚未实现 |
| D021 | V44–V47尚无迁移；M01统一负责，不占号 |
| D022 | 仅发现v1路由，无v2；小程序仍请求旧API |
| D023 | 仅手机号密码登录，无微信绑定及恢复链路 |
| D024 | 旧注册/骑手资格服务可复用；微信新注册与人工恢复分流未实现 |
| D025 | 旧结算不能代替新30/70追加账本及独立调整 |
| D026 | 密码登录存在；旧新单仍使用人工收款/货到付款 |
| D027 | 历史一次文档修改范围，不是当前运行功能验收项 |
| D028 | 旧凭证类型存在，缺新任务每单取件/规格可选门禁 |
| D029 | 无真实微信身份后端兑换，模拟身份也不算接入 |
| D030 | 缺取消前到场事件、距离/时间及平台补贴链路 |
| D031 | 已由D034替代；九档整数价代码匹配，配置化快照仍缺 |
| D032 | 需在新状态机同锁拒绝履约、允许受控售后；未实现v2 |
| D033 | 旧建单仍可进入create；生产停单目标未能由当前源码证明 |
| D034 | 九档价格和包裹区域费匹配；区域仍枚举、缺版本启停与快照 |
| D035 | 旧营业展示不能校验结构化时段；三处仍为夜间急送文案 |
| D036 | 缺新原款30分钟关单、重试不续期及迟到到账处理 |
| D037 | 旧规格审批不能证明审核中配送与拒绝原价新事务 |
| D038 | 缺新调整2小时提醒/24小时升级的实际接收人 |
| D039 | 缺管理员关闭未决调整按原价结案入口 |
| D040 | 缺批准起15分钟提示和一般售后先登记链路 |
| D041 | 缺已送达待确认/补款分离和任务释放容量 |
| D042 | 缺结清后完整10分钟独立窗口、实际送达不覆盖 |
| D043 | 冻结范围已由D060修订；旧问题模块无新冻结事务 |
| D044 | 缺逾期>30天结案转欠款及争议一并裁定 |
| D045 | 缺结案后还款不重开窗口与凭证可用性链路 |
| D046 | 缺原款独立释放条件与模拟付款 |
| D047 | 缺补款追加/降档冲减/已付应收，不自动跨单抵扣证明 |
| D048 | 缺逐案责任金额守恒及追偿账本 |
| D049 | 旧风险信号不等于管理员确认冻结服务 |
| D050 | 缺引用取消前打卡、服务器接收时间及100分独立权益 |
| D051 | 旧取件点枚举无新坐标配置/版本快照事件 |
| D052 | 有旧站内消息和推送outbox；无微信订阅及新审核升级 |
| D053 | 缺新保全定期复核与追偿结束后7天删除约束 |
| D054 | 缺补贴专用证据保留/清理条件 |
| D055 | 缺新任务邀请同意及容量/交接，不能恢复旧任务 |
| D056 | 无稳定错误码、v1 410门禁、不可变流程版本及v2拒旧 |
| D057 | 旧建单幂等+行锁可复用；缺新支付单/尝试和全链路同事务审计 |
| D058 | 当前有备份哈希、命令和依赖核查；微信、数据库/恢复验收未运行 |
| D059 | 旧五角色/MFA存在；缺v2启用安全版本及独立后台 |
| D060 | 缺售后全单未发权益冻结、多案去重、付款强锁校验 |
| D061 | 缺按任务自动/人工分配、v2混合投影和V44–V47模型 |
| D062 | 小程序仍v1会话及固定生产域名；缺构建断言和新保全删除审计 |
| D063 | 缺order_flow_version=2不可变与独立递增version实现 |
| D064 | 缺送达后有效骑手有限调整权限及与最终完成同锁竞争 |
| D065 | 缺本人任务与v1/v2授权后去重统一分页 |
| D066 | 旧资质事实可复用；缺v2共享version、可解码校验、资源标识、保全和事件 |

## 关键证据索引

- 身份/密码：AuthController、AuthService、AppUser；管理员资格目标对照C01/M44-14。
- 订单/价格：OrderController、OrderService、CampusOrder、ServiceType、DeliveryZone；create仍使用旧付款方式并支持优惠。
- 履约/证据：OrderProofService、CourierReassignmentService、OrderIssueService；无新独立任务和证据保全模型。
- 资质：CourierCertificationService、CourierCertification；已有行锁、待审申请、8MiB和文件头检查，未具备version和稳定photoResourceId；文件头检查不等于完整图像解码。
- 错误体：api/ApiExceptionHandler.java；message-only。
- 客户端：管理端src/App.tsx仍旧角色菜单，小程序utils/api.js固定生产地址、utils/session.js仅runrun_session_v1。
- 迁移：运行目录与交接副本各43份V1–V43，逐文件SHA256一致。未发现V44–V47，不能据此推断数据库实际执行版本。
