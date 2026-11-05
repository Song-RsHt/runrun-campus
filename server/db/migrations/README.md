# 独立 v2 数据库迁移入口（M00）

## 甲方本地 M09 运行入口（2026-11-05）

本地 Compose 使用根目录 `deploy/v2-test/migrations.txt` 的固定清单：M00–M08 已测 `0001/0002/0003/0100/0200/0300/0004/0006/0101/0500/0007`，随后追加 `rrc_0701__restore_identity_audit_actions.sql`，共 12 份。不要与下面历史记录中的另一套 `0600/0700` 通知链混跑。启动、回执重试、管理员及隔离规则见 `docs/甲方本地v2测试环境.md`。

0701 修复 0101 覆盖审计白名单时遗漏 M01/M02 的 V2_ADMIN_CREATED、V2_DEPLOYER_CREATED、SERVICE_CONFIG_CHANGED；不新增管理员权限语义，不修改历史 up 文件。迁移只接受精确已知前置约束，重复或后续约束变化拒绝。逆序回滚先执行 0701 对应 down；存在恢复动作的审计记录或后续白名单变化时拒绝，保留审计证据。可用根目录 `tools/verify-v2-local-migrations.ps1` 在无网络、无宿主挂载的临时 PostgreSQL 16 中复验全链与保护。

本目录是全新阶段一系统的独立迁移链；旧交接 V1–V43 保留原样，禁止复制或导入。

- PostgreSQL 16；对象命名空间 `runrun_v2`。
- 正向迁移目录 `up/`，手工回滚目录 `down/`，迁移号 `rrc_NNNN` 只增不重用。
- M00 只建 schema。发现 public 关系（含旧 Flyway history）、public 函数/类型（含 enum/domain）或非系统 schema 时拒绝初始化；重复执行拒绝。
- 当前采用显式 psql 执行，不声称已接入 Flyway。后续 runner 的历史表、扫描规则、事务行为须单独验收，禁止同时扫描 down/ 或旧迁移目录，禁止自动 baseline/clean。

M01 身份基础迁移为 `up/rrc_0002__create_identity_foundation.sql`，回滚为同号 `down/rrc_0002__create_identity_foundation.rollback.sql`。它只创建 `runrun_v2` 内的 `app_users`、微信身份、会话、资质/资质事件、管理员审计、幂等请求和通知 outbox；订单、支付、履约、结算及证据生命周期由登记表中的后续模块唯一创建。M01 不使用旧 public 表，也不采集资质姓名、学校、学号或有效期。

M05 调整与售后迁移为 `up/rrc_0004__adjustments_after_sales.sql`，回滚为同号 `down/rrc_0004__adjustments_after_sales.rollback.sql`。M05 只建立独立调整单、售后案件、欠款和 M05 事件表，并通过已有 M02–M04 表及完成阻断投影实现同订单锁下的申请、审批、完成、凭证访问和售后结案；原始支付记录保持不可覆盖。`server/db/tools/verify-m05.ps1` 使用临时 PostgreSQL 执行完整迁移链、依赖安全回滚和 pg_dump/pg_restore 验证。

M02、M03、M04 的独立迁移分别为 `up/rrc_0100__orders_and_service_config.sql`、`up/rrc_0200__mock_payments.sql`、`up/rrc_0300__fulfillment.sql`，各自对应 `down/` 同号回滚脚本；它们按 M00 登记顺序执行，不创建跨模块占位表。M06 结算迁移为 `up/rrc_0500__settlement_freeze.sql`，回滚为同号 `down/rrc_0500__settlement_freeze.rollback.sql`。阶段一不连接真实打款。

M07 通知读取表为 `up/rrc_0600__user_notifications.sql`，回滚为同号脚本；它消费 M01 已创建的 `notification_outbox`，不重建 outbox。M08 运行时迁移为 `up/rrc_0700__notification_outbox_runtime.sql`，只向 M01 outbox 增加领取/发送状态列、索引和 `claim_notification_outbox(text,integer)` 函数；迁移撤销该函数的 PUBLIC EXECUTE，并在预先存在 `rrc_notification_worker` 角色时只授予该角色 schema 使用、outbox SELECT/UPDATE 和函数 EXECUTE。角色由部署管理，不由迁移创建；回滚先拒绝仍有领取/发送运行数据的 outbox。M08 不创建第二个 outbox 或其他基础通知对象。

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

完整链路验证使用 `verify-v2-chain.ps1`。脚本按固定登记顺序执行 M00 至 M08，逐个拒绝重复执行，检查旧 `public` schema 哨兵保持不变，验证业务/证据记录阻止危险回滚，并在临时容器中完成 pg_dump/pg_restore。M02、M03、M04 的迁移和回滚文件均在本目录链中实际执行。

```powershell
./server/db/tools/verify-v2-chain.ps1
```

脚本只连接无网络、无端口的临时 PostgreSQL 容器；不会执行生产迁移，也不会扫描旧 V1–V43。

只需现有 Docker Desktop/PowerShell，不新增依赖。脚本创建随机 `rrc-m00-` 容器，`--network none`，不发布端口、不挂载宿主目录。trust 认证仅用于该不可联网的临时容器。通过 docker exec/psql 验证空库、重复拒绝、旧库哨兵拒绝、回滚安全、pg_dump/pg_restore、恢复主键与合成行。备份仅在容器 `/tmp/m00.dump`；不生成或提交凭据。finally 仅清理本轮唯一容器及其匿名卷，不触碰共享容器或卷。失败时控制台保留失败步骤，原始敏感输出被抑制。

M08 通知迁移为 `up/rrc_0007__notifications.sql`，依赖 M00→M07 完整对象。它只扩展 M01 已有 `notification_outbox`，新增可靠站内消息、模板、入队和 claim/complete 函数；迁移前置检查拒绝重复执行，回滚记录基线对象并拒绝后续对象或未完成通知。
