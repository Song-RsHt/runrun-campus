# M00–M08 数据库迁移链交付

日期：2026-10-28。分支：`fix/m08-migration-chain`。

## 修改文件

- `server/db/migrations/up/rrc_0600__user_notifications.sql`
- `server/db/migrations/down/rrc_0600__user_notifications.rollback.sql`
- `server/db/migrations/up/rrc_0700__notification_outbox_runtime.sql`
- `server/db/migrations/down/rrc_0700__notification_outbox_runtime.rollback.sql`
- `server/db/tools/verify-v2-chain.ps1`
- `server/db/migrations/README.md`
- `docs/reports/M00-migration-registry.md`

M02、M03、M04 的独立迁移与回滚已在 worktree 现有链中登记并由 runner 实际执行：`rrc_0100`、`rrc_0200`、`rrc_0300`。

## 对象和顺序

顺序为：M00 `0001` → M01 `0002/0003` → M02 `0100` → M03 `0200` → M04 `0300` → M05 `0004` → M07 证据 `0006/0101` → M06 `0500` → M07 通知 `0600` → M08 `0700`。

`rrc_0600` 新建 `runrun_v2.user_notifications` 及用户、订单和创建时间索引，消费 M01 的 `notification_outbox`。`rrc_0700` 只扩展既有 outbox，增加 `claimed_at`、`claimed_by`、`sent_at`、`last_error_code`，添加领取索引和 `claim_notification_outbox(text,integer)` 函数；没有重新创建 outbox、用户或其他基础对象。

## 验证

```powershell
pwsh -NoProfile -File server/db/tools/verify-v2-chain.ps1
```

最近一次在 PostgreSQL 16、无网络、无端口、无宿主挂载的临时容器中通过：

- M00–M08 空库初始化成功；每个迁移重复执行均明确失败。
- 旧 `public.flyway_schema_history` 与 `public.campus_orders` 哨兵保持不变。
- M07 通知表、M01 outbox、M08 扩展列、索引和领取函数存在。
- 业务订单、支付、履约凭证、定位、保全删除记录、结算和通知合成数据成功写入。
- M08 运行数据、M07 通知数据、M04 凭证和 M07 证据记录均阻止危险回滚；失败后数据仍保留。
- M08 默认撤销 PUBLIC 函数执行权限；若部署角色 `rrc_notification_worker` 已存在，仅授予其领取函数及 outbox 所需最小权限。`rrc_notification_reader` 无函数权限。
- 两个 worker 分别领取 3 条消息无重叠；失败消息可重试并递增 `attempt_count`，未来 `next_attempt_at` 不会提前领取。
- 并发验证现在使用两个独立 PowerShell job 启动的 `docker exec psql` 会话；验证库临时触发器让每行领取睡眠 1 秒，确保两个会话真实重叠。两会话分别返回 3 条，6 个领取 ID 无重复，测试触发器随后删除。
- `pg_dump/pg_restore` 保留业务、证据、通知数据及 M08 函数。
- 空库完整逆向回滚成功。

## 未运行和风险

未运行 Spring/Flyway 应用 runner、业务 Java/API、客户端、真实微信/支付、共享环境或生产迁移，也未运行全仓库 Maven 测试；当前 runner 使用显式 `psql` 文件执行。当前集成 `main` 尚未登记/包含 `0600` 与 `0700`，因此本分支不能宣称已与 main 一致；需总控集成后再做独立 QA。M08 通知函数只提供数据库领取边界，不代表发送器或 API 已实现。

## 回滚与建议

仅在隔离数据库按逆序执行对应 `down/` 文件；有后续对象、业务数据或证据记录时脚本拒绝回滚，不使用 `CASCADE`。共享或生产环境应使用备份恢复和审批，不执行本地 down 脚本。建议提交独立 QA，不合并 main。
