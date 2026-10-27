# M01 身份基础返工交接

日期：2026-10-27。分支：`feature/m01-identity`。

本次返工遵循 M00 唯一迁移链和对象登记：正向 `server/db/migrations/up/rrc_0002__create_identity_foundation.sql`，回滚 `server/db/migrations/down/rrc_0002__create_identity_foundation.rollback.sql`。脚本显式要求先存在空的 `runrun_v2`，所有对象限定该 schema，不扫描 Flyway 旧目录、不读取 `public` 旧表，也不修改 V1–V43。

## M01 对象

仅创建登记表中的身份基础对象：

- `app_users`：手机号、USER/COURIER/ADMIN、账号状态、v2 管理员启用标记、安全版本和并发版本。
- `wechat_identities`、`user_sessions`：微信身份唯一绑定和安全版本会话失效。
- `courier_certifications`、`courier_certification_events`：资质状态与 `eligible` 分离、并发版本、稳定照片资源/存储键成对引用、追加事件。
- `admin_audit_logs`、`idempotency_requests`、`notification_outbox`：M01 必需的审计、幂等和可靠通知基础设施。

资质表没有姓名、学校、学号或有效期字段；照片正文不入库。订单、支付、任务、结算、凭证生命周期和通知读取表均未在 M01 重建，分别留给 M02–M07 的登记所有者。

## 约束和运行门禁

所有可变聚合的 `version` 从 0 开始并要求非负；管理员安全版本独立于普通并发版本。管理员审计使用 `administrator_id`，并限制本阶段动作与目标枚举；微信身份、手机号、会话 token 摘要、资质照片资源和幂等键有唯一约束；所有 M01 外键均有前导列索引；资质照片资源/存储键必须成对，SYSTEM 资质事件可无 actor，COURIER/ADMIN 事件必须有 actor。回滚识别非 M01 表、独立序列、索引、函数和用户类型，只允许 M01 自有 identity 序列及表复合类型，使用默认 RESTRICT，并在发现后续对象时拒绝，不使用 `DROP ... CASCADE`。

## 验证

```powershell
pwsh -NoProfile -File server/db/tools/verify-m01.ps1
```

验证脚本使用无网络、无端口、无宿主挂载的临时 PostgreSQL 16 容器，覆盖：M00 前置及旧 `public` 哨兵保留、M01 八表清单、资质敏感字段缺失、三项外键索引前导覆盖、重复迁移拒绝、微信双唯一、提交/批准照片成对约束、审计动作/目标、资质事件 actor、幂等作用域、后续表和独立序列阻止回滚、失败后结构保留、pg_dump/pg_restore 数据与约束及恢复后负例、M01 回滚后 M00 回滚。最近一次运行 20 项全部 `PASS`。

未运行：Spring/Flyway runner（M00 明确尚未接入）、业务 API、真实微信/支付、共享环境和生产迁移。该分支只包含数据库迁移、验证脚本和迁移 README，不包含业务接口实现。
