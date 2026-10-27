# M00 迁移与公共契约冻结登记

日期：2026-10-27。状态：待独立只读验收。本登记仅分配实现所有权，不改权威规则。

## 公共契约

唯一技术来源为 `docs/数据模型与接口契约.md`：

- 通用数据约定：snake_case DB/camelCase API，整数 cents，BIGINT ID 对外字符串，带时区时间，GCJ02。
- A01：`/api/v2`；业务写 Idempotency-Key；更新聚合 expectedVersion；幂等记录与业务、副作用审计、事件及适用 outbox 同事务；认证入口与报价等例外以原文为准。
- E01：HTTP/稳定 code/message 使用唯一错误码表，模块不得另创重复错误体/错误码。
- S01：订单状态和一致性、订单聚合锁、确认窗口及完成竞态唯一来源；不实现被现行范围覆盖的混合历史。
- C01：USER/COURIER/ADMIN、管理员默认未启用、即时资格/安全版本检查、资格管理身份和适用 MFA；新系统不继承旧账号或旧 SUPER_ADMIN。
- 固定 order_flow_version=2 与递增 version 分离；其数据库定义按 M44，冲突处理按 A01/E01。

## M00 对象

正向：`server/db/migrations/up/rrc_0001__create_v2_schema.sql`；回滚：`down/rrc_0001__create_v2_schema.rollback.sql`。仅创建 `runrun_v2` schema，无业务表/API/账号/数据。后续对象均显式使用此 schema，不使用旧 public 表。

## 唯一归属与迁移顺序

|模块/范围|唯一创建对象|依赖/延迟外键策略|
|---|---|---|
|M01 / 0002–0099|app_users、wechat_identities、会话、管理员启用字段、courier_certifications/courier_certification_events；admin_audit_logs、idempotency_requests、notification_outbox 基础设施|先账号，再资质/审计/幂等/outbox。M01 自身业务写入前基础设施必须到位；outbox主体使用契约的类型标识/JSON，不提前引用未建订单。M07消费而不重建这些表|
|M02 / 0100–0199|delivery_zones、service_points、platform_settings、v2_orders、v2_order_events|依赖M01账号；先配置/地点后订单；订单引用配置/账号；v2_order_events.task_id→M04 fulfillment_tasks 的FK由M04目标表建成后新增后置迁移补齐，补齐前不开放依赖该关联的事件接口；本模块不重建幂等/审计/outbox|
|M03 / 0200–0299|payment_orders、payment_attempts、refund_orders、debts、debt_events|依赖订单/账号；对尚未创建的调整/售后FK在M05后置迁移补齐，不引用不存在表；完整关联验收前不得合并依赖该FK的端点|
|M04 / 0300–0399|fulfillment_tasks、handover_records、v2_order_proofs、location_events|先tasks再handover/proof/location，避免交接循环FK；task→handover回指同模块末尾ALTER ADD FK；同单关联复合FK/事务断言须逐条登记|
|M05 / 0400–0499|order_adjustments、after_sales_cases|引用已存在订单/任务/凭证/支付；再补M03延迟FK。重新履约复用M04任务/交接，不重复建表|
|M06 / 0500–0599|settlement_entries、rider_allocations、rider_allocation_lines、settlement_freezes、freeze_events、payout_registrations|依赖M03–M05；补贴使用结算条目和位置事件，交接只消费handover_records|
|M07 / 0600–0699|evidence_holds、evidence_deletion_audits、user_notifications；资质/凭证生命周期所需增量列|资质表由M01创建；凭证表M04创建；outbox由M01创建；本模块仅ALTER/消费。订单或资质资源用带类型标识，不伪造order_id|
|M08|无|只消费已验收v2 API|

编号范围是预留，不允许跳过前序迁移或改变业务字段。每条迁移提交前逐项登记实际列/约束/索引及跨模块FK；前向引用采用目标表存在后的ALTER迁移，绝不先写不存在的FK或创建占位重复表。延迟FK尚未补齐时，只可隔离分支开发，不可标记完整验收或合并依赖关联约束的API。具体字段仍以技术契约为准；发现契约无法适用于全新系统的冲突，暂停相关模块交总控裁决，不自行改权威规则。
## 分段集成门禁

M01运行增量登记（2026-10-27）：`rrc_0003__identity_runtime.sql`，依赖0002，唯一创建`identity_tickets`、`identity_audit_events`及索引；增加`app_users.controlled_identity`和部署身份/管理员启用约束，扩展管理员建档/部署身份创建审计动作。对应回滚先检查运行票据、身份审计、部署身份及新管理员审计为空，否则拒绝丢弃运行凭据/证据；随后按0003→0002→0001回滚，均不使用CASCADE。定义见权威契约“M01全新身份运行契约”。

以下只规定模块分段开发与开放的先后，不变更权威业务规则，也不表示证据安全要求可以延期：

- M07 可在 M01 后并行推进共享证据生命周期；依赖 M04 表的外键和服务在 M04 表建成后接入。
- M01 只验收账号及未审核资质基础；照片上传、读取、审核在 M07 共享生命周期联调通过前不验收开放。
- M04 在证据模块未齐时仅做隔离 schema/状态分支开发。上传、证据读取、保全、删除及依赖这些能力的取件/送达闭环，须在 M07 共享生命周期联调通过后才验收开放。
- M03 原款支付可独立验收；调整/售后相关支付在 M05 建表并补齐延迟外键、同单关联和集成验证后才开启。

## M02 实际迁移登记（2026-10-27）

新增 `rrc_0100__orders_and_service_config.sql` 及同号受限回滚，依赖 0001–0003。本次用户明确选择“本次只实现 M02，建单等待 M03 集成”，不调整 payment_orders 的 M03 唯一归属，不新增 0200 迁移或支付占位表。

- delivery_zones：代码/名称/包裹加价/启用标记/config_version 及公共列，唯一 code；种子 STANDARD 0、MEIYUAN 200。
- service_points：代码/名称/可空成对经纬度/GCJ02/启用标记/config_version 及公共列，唯一 code、范围与成对坐标约束；无地点种子。
- platform_settings：单例、结构化时段/时区、建单开关、负责人及全部基线时限/补贴/比例/config_version/version；负责人 FK→app_users 并建索引。
- v2_orders：契约 M44-04 全部列，状态/九服务/金额/流程版本 CHECK；FK→app_users/delivery_zones/service_points；order_no 唯一，用户时间、区域、服务点、关单和确认窗口索引。guard_order_update 函数及触发器保护版本和原始快照。
- v2_order_events：order/task/actor/type/request/payload/created_at；FK→v2_orders/app_users，订单/类型/请求唯一，order/actor/task 索引。task_id 的 FK 仍由 M04 建成 fulfillment_tasks 后追加；M02 事件只写 NULL task_id，不开放任务事件写入接口。
- 不重建 M01 基础设施；仅扩展 admin_audit_logs 的 action/target CHECK，加入 SERVICE_CONFIG_CHANGED/SERVICE_CONFIG。A32 审计、M02 幂等记录与业务数据同事务。无 M02 外发通知需求，不写伪 outbox。
- 回滚只允许空订单/事件、无业务幂等/配置审计、服务点为空且种子配置未修改的状态。所有 DROP 均 RESTRICT，无 CASCADE；存在后续对象依赖时拒绝回滚。备份恢复和数据库约束验证入口为 `server/db/tools/verify-m02.ps1`。

## M04 实际迁移登记（2026-10-27）

隔离开发分支 `feature/m04-fulfillment`，根目录 `D:/biancheng/RunRunCampus-worktrees/rrc-m04-fulfillment`，基于已集成 M01–M03 的 `8fb3a86`。`rrc_0300__fulfillment.sql` 依赖 0001、0002、0003、0100、0200；不改写已执行脚本。

- fulfillment_tasks：订单、骑手、状态、同单 source_task_id、自增并发 version、服务器接受/取件/送达/结束/创建时间及 superseded_at。每单 ACTIVE 部分唯一索引，骑手状态和创建顺序、来源索引；复合唯一键供同单/同骑手关联使用。OFFERED 不占容量，ACTIVE 计数由账号行锁串行化，DELIVERED 同事务释放。
- v2_order_proofs：订单/任务/上传者/种类、私有对象键及状态、保留/删除/创建时间。task/order/uploader 复合 FK；对象键唯一，PICKUP/DELIVERY 每任务唯一，SPECIFICATION 可追加；订单任务种类和清理索引。
- location_events：订单/任务/骑手复合 FK，ARRIVAL/DELIVERY，成对有效 GCJ02 坐标、服务器收到时间、request_id、accepted/decision_reason、清理字段。任务/事件种类/请求唯一；每任务至多一个 accepted DELIVERY 部分唯一索引；同单位置复合唯一键与时间索引。
- fulfillment_completion_blocks：M04 完成阻塞投影，不是调整单/售后单。order/source_type/source_id 复合主键，source_type 只支持 ADJUSTMENT/AFTER_SALES。M05 在自己的订单事务中调用 blockCompletion/unblockCompletion，M03 财务状态另直接参与阻塞判定。
- 补齐 v2_order_events(task_id,order_id)→fulfillment_tasks(id,order_id) 复合后置 FK；M02 不再存在未校验的非空任务事件。
- fulfillment_order_guard 函数/触发器：实际 delivered_at 一旦写入不可覆盖；M03 取消同事务将 ACTIVE/OFFERED 任务结束为 CANCELLED，立即释放容量。
- fulfillment_schema_manifest：复用 M03 schema signature，记录本模块五表及 v2_orders/v2_order_events 的列、约束、索引、触发器指纹。回滚拒绝业务记录、幂等/事件及后续模块附加对象；只允许无数据的原始模块回滚，无 CASCADE。
- handover_records 仍保留 M04 预留所有权，本提交没有创建占位交接表；完整管理员交接双确认由 M06 集成实现。HANDED_OVER 状态与旧任务拒写已由本模块校验。
- M07 未验收：FulfillmentEvidence 是私有凭证签发/验证端口，默认实现拒绝；不连旧上传/公开存储。真实凭证生命周期联调前不开放取件/送达闭环。M05 邀请入口先用 MANDATORY 事务服务端口，不暴露缺少异常处置前置条件的管理员 HTTP。
- 验证入口：`server/db/tools/verify-m04.ps1` 和后端 `mvn -Dtest=FulfillmentIntegrationTest test`。本登记不代表独立 QA 或生产验收，证据见 M04 交付报告。
