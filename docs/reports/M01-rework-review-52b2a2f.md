# M01 身份数据库返工验收

日期：2026-10-27（Asia/Shanghai）。验收提交：`52b2a2f2e82d819c78f3c159cf89a2c959959885`；分支：`feature/m01-identity`；基线 main：`d4daa72`。

实现交接：`docs/reports/M01-implementation-rework.md`。独立只读检查：`m01_rework_schema_qa`（契约/约束）、`m01_rework_safety_qa`（隔离/回滚/实际验证）；总控独立重跑原验证脚本。

结论：**BLOCKED，暂不建议合并该提交。** 本轮范围仅为 M01 身份数据库基础；Spring/Flyway runner、身份服务和业务 API 未实现，不作为此次数据库子范围的拒绝理由。完整 M01 仍是部分实现。

## 已解决的首轮问题

本版本仅修改 5 个文件：新增 rrc_0002 正向/回滚 SQL、verify-m01.ps1、返工交接报告，以及迁移 README。没有改动旧 V1–V43、交接源码、客户端、权威规则或生产配置。

唯一迁移链与 schema 已恢复为 `server/db/migrations/up/rrc_0002__create_identity_foundation.sql` 和 `runrun_v2`，M01 八表对象范围符合 M00。管理员默认未启用，具有安全版本；微信双唯一、资质 eligible 与审核状态分离、照片资源成对引用、移除姓名/学校/学号/有效期等整改均已完成。新脚本不启动旧应用；回滚使用事务和默认 RESTRICT，无 CASCADE。

首轮第二套 rr_* 状态/订单/支付模型及旧运行时 profile 的问题，不继续作为此新提交的阻塞理由；它们不是本轮验收对象。

## 本轮阻塞与修复要求

### R1 管理员审计仍偏离唯一技术契约

正向 SQL 第 108–118 行定义 `admin_audit_logs.actor_id`，契约 M44-14 指定管理员关联为 `administrator_id`。同时 action、target_type 只有 NOT NULL，没有实际允许值 CHECK；M44-14 及资质审计说明要求本阶段同步维护动作/目标约束，不能推迟至 M07。

修复：对齐管理员关联字段和对应唯一约束/索引；本阶段允许值至少覆盖 V2_ADMIN_ACCESS_CHANGED / APP_USER、资质申请/上传/读取/批准/驳回及 COURIER_CERTIFICATION 的适用审计动作，具体值引用契约。添加未知 action、未知 target_type 的拒绝测试以及有效动作插入测试。系统/骑手资质事件仍走独立事件表，不伪造管理员。

### R2 三个外键缺少索引覆盖

M44 公共约定要求索引覆盖所有外键。正向 SQL 第 41、94、139 行的下列外键没有以前导列覆盖的索引，现有索引位于第 155–160 行：

- `wechat_identities.user_id`：`UNIQUE(app_id,user_id)` 不提供 user_id 前导覆盖。
- `courier_certification_events.actor_id`。
- `notification_outbox.recipient_id`：`UNIQUE(event_key,recipient_id,channel)` 不提供 recipient_id 前导覆盖。

修复：补充相应索引，并通过 PostgreSQL catalog 查询逐个验证外键索引覆盖，不能只统计索引数量。

### R3 后续对象回滚保护存在实际漏洞

回滚 SQL 第 12–14 行只检测 r/p/v/m/f，未识别独立序列等后续对象。独立 QA 在无网络临时容器复现：先完成 M00/M01，再执行 `CREATE SEQUENCE runrun_v2.m02_synthetic_sequence;`，随后 M01 down 成功，八张 M01 表全部消失，而后续序列仍存在。这违背交付所称“检测后续对象后拒绝执行”。

修复：识别后续独立序列及函数/类型等非关系对象，同时通过依赖关系正确排除 M01 自有 identity sequence、表复合类型等自动对象。不能简单把所有 S 都视为后续对象，导致正常 M01 回滚也失败。需测试拒绝后 M01 数据/结构原样保留，并保留正常逆序回滚与外部依赖 RESTRICT 场景。

### R4 验证用例有假通过与恢复覆盖不足

`verify-m01.ps1` 第 44–47 行先为 courier_id=1 创建资质，再用同一 courier_id 插入缺少 storage key 的记录。该负例同时违反 courier 唯一约束：独立 QA 仅在临时数据库删除 submitted_photo_pair CHECK 后，原负例仍因 `courier_certifications_courier_uq` 失败。因此该 PASS 不能单独证明照片成对约束生效。

此外，第 39 行只检查空库 public 无表；创建的 rrc_legacy 没有被使用，没有旧表哨兵覆盖。第 55 行恢复后只检查 app_users 表存在，没有核对合成账号、微信绑定、资质记录及唯一/CHECK 等恢复约束。

修复：以另一个合法骑手/已有行 UPDATE 构造只违反目标约束的照片负例，核对 SQLSTATE 和具体 constraint 名；补充 approved 照片配对、手机号/微信双唯一、幂等作用域、资质事件 actor 等适用负例。用合成 public 哨兵证明不读写旧对象，不能把“旧表不存在”描述成已证明“旧表保持不变”。恢复后核对合成数据与约束，并验证恢复后的负例仍被拒绝。无需把“每条后续迁移重复拒绝所有 public 对象”另立为规则。

另：`git diff --check main...52b2a2f` 未通过，验证脚本第 49 行有尾随空白；修正后须重新报告实际命令结果。

## 数据库对象与 API 清单

候选创建八表：`app_users`、`wechat_identities`、`user_sessions`、`courier_certifications`、`courier_certification_events`、`admin_audit_logs`、`idempotency_requests`、`notification_outbox`，全部在 runrun_v2；附 PK/FK/UNIQUE/CHECK、两个照片资源部分唯一索引和六个显式普通索引。未创建业务函数、触发器、订单/支付/任务/结算表或 API。

## 实际验证与未运行项

总控和独立 QA 分别运行 `pwsh -NoProfile -File server/db/tools/verify-m01.ps1`，均退出 0，原脚本 12 项 PASS。验证环境是无网络、无发布端口、无宿主挂载的临时 PostgreSQL 16 容器；测试后容器清理。PASS 只表示现有断言通过，不能掩盖上述用例覆盖问题。

独立补充探针证实：照片 CHECK 被移除后原负例仍失败（目标约束未被唯一验证）；后续独立序列未阻止回滚；合成 public 哨兵值原样保留。探针只改变临时测试数据库，不修改仓库 SQL，不访问旧库或生产环境。

未运行：新应用 runner/业务 API/真实微信/真实支付/共享环境迁移/生产操作。未把这些范围外事项描述为已完成。原脚本 pg_dump/pg_restore 命令成功，但完整恢复数据/约束验收尚未满足。

## 返工与集成状态

实现提交 `52b2a2f` 保留原样，不合并到 main，不修改已交付 SQL。审查记录在独立 `review/m01-rework-52b2a2f` worktree 中维护，避免改动实现 agent 的主工作区。

下一提交应在现有实现分支补齐 R1–R4，附精确 hash、变更/对象/约束清单、实际测试结果、未运行验证和回滚边界，再独立复验。若迁移已在任何共享环境执行，须先核实执行历史并以新增迁移修复；不能把“本轮仅临时测试过”的假设推广到其他环境。仅在数据库子模块通过验收后才可集成；身份 API、真实微信身份和资质证据闭环另验收。
