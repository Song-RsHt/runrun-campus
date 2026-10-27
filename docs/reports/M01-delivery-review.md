# M01 数据库交付只读验收

日期：2026-10-27（Asia/Shanghai）。总控协调；独立检查：`m01_schema_review`、`m01_runtime_review`。

结论：**BLOCKED，退回修改，不建议合并。** 当前反馈不足以将 M01 身份模块或全新数据库标记为完成。用户转交交付反馈不视为另行批准替换 M00 的迁移链或修改权威技术契约。

## 验收对象与文件清单

实际文件在主工作区 `D:/biancheng/RunRunCampus`，均未跟踪，HEAD 仍为 `146c078`，没有实现分支/worktree 或提交 hash 可供验收。以下 SHA-256 固定本轮查看的版本，后续修改必须重新检查。

|文件|SHA-256|
|---|---|
|`handover-2026-10-24/RunRunCampusBackend/src/main/resources/db/migration-new/V1__new_system_schema.sql`|`AAC62CA1BA684F7961716156CE0AB5C58B52AD64E928AB78A2534AECF4D57282`|
|`handover-2026-10-24/RunRunCampusBackend/src/main/resources/application-new-init.yml`|`1C56157D2A9458582196227C8CC1B797C6C085795D688EA2D8EF67193234ADE4`|
|`handover-2026-10-24/RunRunCampusBackend/db/new-init-test-data.sql`|`6B69EC75CD3C9CEF946D3F4D898C6A05AB58B2AE16E9D1758F7CD04062C4B470`|
|`handover-2026-10-24/RunRunCampusBackend/db/new-init-rollback.sql`|`AF0B63A20219C432DA09EFE63574D10ACFDF537725F3C1DD2C54E0337743FA9A`|
|`docs/新系统数据库初始化.md`|`68952DEC22CA0B95269953DB622CD44B452D085BBE689FA91FDDA27500E3260D`|

本报告所称 SQL 行号均指上述初始化脚本；其他文件使用各自行号。报告与状态更新在独立 `review/m01-delivery` / `D:/biancheng/RunRunCampus-worktrees/rrc-m01-review` 管理，不把这五个候选文件提交到 main。

## 阻塞项与修复要求

1. **第二套迁移链与对象归属冲突。** SQL 从第 4 行起创建未限定 schema 的 `rr_*` 表，profile 使用另一套 `migration-new/V1`。M00 已冻结 `server/db/migrations/up/rrc_NNNN`、`runrun_v2` 和 M01–M07 各自对象范围；本交付跨所有模块重建对象，且没有空库前置断言。应先按现行契约和 M00 归属拆分、映射到唯一新链；不得让两套成为并行事实来源。若另提整体替换，必须先记录方案与影响并获得明确范围确认，不能直接执行。

2. **状态和字段不是契约定义。** SQL 第 105 行使用 `ASSIGNED/DELIVERED_PENDING_CONFIRM`，S01 是 `ACCEPTED/DELIVERED_PENDING_CONFIRMATION`；第 133 行无任务 `OFFERED/ACTIVE`，也无每单最多一个 ACTIVE 的部分唯一约束。第 158–159 行用 `TOP_UP/PENDING/SUCCEEDED` 替代支付契约的 `SUPPLEMENT/UNPAID/PAID`，缺少支付单 PROCESSING/FAILED。调整、售后和欠款状态也偏离。必须逐项按 M44–M47/S01 对齐，不能由后端后续自行解释另一套枚举。

3. **身份与资质违背已确认边界。** SQL 第 378–380 行强制姓名、campus、student_id，与 A37 新资质不采集姓名/学号/学校的要求冲突。缺少稳定照片资源及批准照片关联、独立 eligible 资格语义、资质事件；用户定义也未提供管理员安全版本/会话失效结构。应仅保留新版批准的字段，并落实新身份、管理员默认关闭及资格审计边界；不能据建表声称身份 API 已实现。

4. **订单快照、时限和配置缺口。** 第 80–108 行无 address_snapshot/pricing_snapshot/policy_snapshot、订单自身 pending_payment_deadline。区域和服务点没有契约 config_version；第 61–68 行使用 NUMERIC(9,6)、默认 WGS84，且未约束经纬度成对为空/有效，与 NUMERIC(10,7)/GCJ02 不符。付款重试不得改变订单原 30 分钟期限，不能只靠单次支付 expires_at 代替。

5. **证据保全删除无法按契约完成。** 第 217–250 行缺 proof.storage_status/delete_after，定位坐标强制 NOT NULL、无 delete_after/deleted_at，不能清空坐标正文并保留事件。没有 evidence_deletion_audits；资质照片生命周期与资源引用也未建立。须具备共享锁、保全、删除状态/失败重试与访问审计，不能等前端上传后再补。

6. **结算与幂等结构不足。** 第 311–321 行付款缺 order_id、beneficiary_type、reference 与 MOCK 标记，无法逐单记录平台/骑手付款；账本缺来源幂等唯一约束，缺 rider_allocations/rider_allocation_lines、freeze_events。第 402–410 行幂等表无 scope/status/resource 语义且 actor 可空，不能替代 A01/M45-07；通知没有契约要求的独立可靠 outbox，审计缺 request_id 去重。需要按契约逐对象列出差异及负例，不接受“相关表已包含”作为完整证明。

7. **Flyway location 分离不等于运行隔离。** `application-new-init.yml` 第 12–14 行保留 JPA validate；旧 `user/AppUser.java` 第 8–9 行仍映射 app_users，主应用与旧实体扫描未隔离。`auth/UserRoleSchemaMigration.java` 和 `auth/AdminAccountInitializer.java` 仍为未按新 profile 隔离的启动组件。因此只建 rr_* 后不能按说明直接启动旧应用并认定为新系统；应使用独立迁移执行入口，并在新服务运行时隔离旧实体/初始化器/定时任务。

8. **回滚及文档不可作为可复现门禁。** `new-init-rollback.sql` 第 3–9 行在没有数据库/环境断言时批量 DROP ... CASCADE，可能连带移除外部依赖；若由 Flyway 应用 V1，再手工删表，history 仍记录成功，不会自动重跑 V1。说明第 48–54 行还把现有 Compose 创建的旧开发数据库与新的库名混用、没有显式创建新库，并把 JDBC URL 传给 psql。应给出隔离目标验证、受限回滚或独立目标库恢复、迁移历史一致性以及正确的原生 PostgreSQL 连接命令；不得使用现有共享 Compose 默认配置冒充隔离方案。

9. **测试角色赋予范围过宽。** `new-init-test-data.sql` 第 5–7 行 SELECT 覆盖全部 rr_users，除两个指定手机号外全部赋予 ADMIN；第 8 行直接启用管理员且无审计。应严格限定本批合成用户、隔离目标和测试身份用途，不能把这个 fixture 当成受控管理员启用实现，也不能在已有账号的共享库使用。

## 数据库与 API 清单

按 CREATE TABLE 自动提取，候选定义 29 张表（均为未验收草稿）：rr_users、rr_wechat_identities、rr_roles、rr_user_roles、rr_delivery_zones、rr_service_points、rr_platform_settings、rr_orders、rr_order_events、rr_delivery_tasks、rr_task_assignments、rr_payments、rr_payment_attempts、rr_adjustments、rr_after_sales、rr_proofs、rr_location_events、rr_refunds、rr_settlement_ledger、rr_settlement_adjustments、rr_settlement_freezes、rr_payouts、rr_debts、rr_debt_payments、rr_notifications、rr_audit_logs、rr_courier_certifications、rr_retention_holds、rr_idempotency_keys。

交付方还须提交逐列/约束/索引和模型映射，不以文字数量代替核对。未发现 API 实现。本轮不执行 SQL、不改候选文件、不删除候选成果。

## 验证状态

已独立完成：文件与 Git 状态核对、逐段 DDL/配置/回滚/文档阅读、与技术契约及 M00 登记比对、旧实体和启动组件静态检查。旧 V1–V43 跟踪文件无本轮修改。

交付方报告：PostgreSQL 16 初始化成功；3 用户/1 订单/1 支付单；删表后 rr_* 为零；未连接生产。这些是转交反馈，当前未提供可复现测试脚本、原始执行记录或提交 hash，本轮未独立复现，不能将其标记为总控已测试。

未运行：新 Spring/Flyway 完整启动集成、契约负例与并发约束、旧库拒绝/生产配置拒绝、备份恢复、完整 M01 身份业务与客户端联调。静态阻塞明确后暂停执行，避免将不一致迁移引入新环境。没有连接生产数据库或执行真实收付款。

## 状态与再次验收

实现状态：仅存在部分 DDL 候选，身份/API 未实现；验收状态 BLOCKED；建议合并：**否**。候选提交 hash：**未提供，文件尚未提交**。M00 已通过的实现和测试状态不变；本轮 M01 测试反馈不计入其他模块已实现或已测试。

再次分配实现前必须确认精确对象/目录所有权。在独立分支/worktree 整理候选，先对齐唯一迁移链、技术契约和上述阻塞项，再提交实际数据库对象/约束/API 清单、测试脚本与结果、未运行验证、风险和确切 hash。全部必需验证及只读验收通过后才允许业务代码进入 main。
