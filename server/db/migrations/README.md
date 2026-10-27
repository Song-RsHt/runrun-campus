# 独立 v2 数据库迁移入口（M00）

本目录是全新阶段一系统的独立迁移链；旧交接 V1–V43 保留原样，禁止复制或导入。

- PostgreSQL 16；对象命名空间 `runrun_v2`。
- 正向迁移目录 `up/`，手工回滚目录 `down/`，迁移号 `rrc_NNNN` 只增不重用。
- M00 只建 schema。发现 public 关系（含旧 Flyway history）、public 函数/类型（含 enum/domain）或非系统 schema 时拒绝初始化；重复执行拒绝。
- 当前采用显式 psql 执行，不声称已接入 Flyway。后续 runner 的历史表、扫描规则、事务行为须单独验收，禁止同时扫描 down/ 或旧迁移目录，禁止自动 baseline/clean。

M01 身份基础迁移为 `up/rrc_0002__create_identity_foundation.sql`，回滚为同号 `down/rrc_0002__create_identity_foundation.rollback.sql`。它只创建 `runrun_v2` 内的 `app_users`、微信身份、会话、资质/资质事件、管理员审计、幂等请求和通知 outbox；订单、支付、履约、结算及证据生命周期由登记表中的后续模块唯一创建。M01 不使用旧 public 表，也不采集资质姓名、学校、学号或有效期。

M05 调整与售后迁移为 `up/rrc_0004__adjustments_after_sales.sql`，回滚为同号 `down/rrc_0004__adjustments_after_sales.rollback.sql`。M05 只建立独立调整单、售后案件、欠款和 M05 事件表，并通过已有 M02–M04 表及完成阻断投影实现同订单锁下的申请、审批、完成、凭证访问和售后结案；原始支付记录保持不可覆盖。`server/db/tools/verify-m05.ps1` 使用临时 PostgreSQL 执行完整迁移链、依赖安全回滚和 pg_dump/pg_restore 验证。

M06 结算迁移为 `up/rrc_0500__settlement_freeze.sql`，回滚为同号 `down/rrc_0500__settlement_freeze.rollback.sql`。它建立不可覆盖的分账流水、骑手交接分配、冻结事件及逐单 MOCK 付款登记；写函数按订单排他锁执行，理由/二次确认/金额守恒/冻结余额和幂等约束由数据库复核。阶段一不连接真实打款。

## 执行与回滚

先由操作人核实目标是全新隔离数据库，连接配置仅从受控环境注入，不打印或提交：

```powershell
psql -X --set ON_ERROR_STOP=1 --dbname "$env:RRC_V2_DATABASE_URL" --file server/db/migrations/up/rrc_0001__create_v2_schema.sql
psql -X --set ON_ERROR_STOP=1 --dbname "$env:RRC_V2_DATABASE_URL" --file server/db/migrations/down/rrc_0001__create_v2_schema.rollback.sql
```

两脚本均显式事务。回滚不使用 CASCADE，关系非空主动拒绝；其他依赖也由 PostgreSQL RESTRICT 拒绝，避免误删后续对象。生产执行不在授权范围。

## 可复现验证

```powershell
./server/db/tools/verify-m00.ps1
```

只需现有 Docker Desktop/PowerShell，不新增依赖。脚本创建随机 `rrc-m00-` 容器，`--network none`，不发布端口、不挂载宿主目录。trust 认证仅用于该不可联网的临时容器。通过 docker exec/psql 验证空库、重复拒绝、旧库哨兵拒绝、回滚安全、pg_dump/pg_restore、恢复主键与合成行。备份仅在容器 `/tmp/m00.dump`；不生成或提交凭据。finally 仅清理本轮唯一容器及其匿名卷，不触碰共享容器或卷。失败时控制台保留失败步骤，原始敏感输出被抑制。
