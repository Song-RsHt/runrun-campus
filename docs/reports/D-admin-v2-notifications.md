# 整改任务 D：管理端 v2 接口与通知联调

分支：`fix/admin-v2-notifications`；工作树：`D:/biancheng/RunRunCampus-worktrees/rrc-fix-admin-notifications`

## 修改文件

- `handover-2026-10-24/RunRunCampusAdmin/src/api.ts`：资质审核统一为 `POST /api/v2/courier/certification/{id}/decision`，携带 `decision`、`expectedVersion`、`photoResourceId`、`reason` 和自动生成的 `Idempotency-Key`；增加通知列表和已读 API；移除旧 `/api/v1/admin/push-deliveries` 调用。
- `handover-2026-10-24/RunRunCampusAdmin/src/types.ts`：增加 v2 通知类型，补充资质版本号和照片资源 ID字段。
- `handover-2026-10-24/RunRunCampusAdmin/src/App.tsx`：增加站内消息页面、读取/已读操作和 `AUDIT_VIEW` 门禁；审核缺少版本号或照片资源 ID 时禁用；移除旧推送页面。
- `handover-2026-10-24/RunRunCampusAdmin/.env.test.example`：隔离地址 `VITE_API_BASE_URL=http://127.0.0.1:8082`。

## API 与页面联调清单

| 页面/操作 | API | 保护 |
|---|---|---|
| 资质通过/驳回 | `POST /api/v2/courier/certification/{id}/decision` | ADMIN、`COURIER_REVIEW`、版本号、照片资源 ID、幂等键 |
| 站内消息列表 | `GET /api/v2/notifications` | Bearer 身份，服务端按当前用户过滤 |
| 标记已读 | `POST /api/v2/notifications/{id}/read` | Bearer 身份，服务端按当前用户过滤 |
| 站内消息导航 | — | 管理端 `AUDIT_VIEW` |
| 资质照片 | `GET /api/v2/courier/certification/{id}/student-card?photoResourceId=...` | 管理员授权、no-store |

## 验证命令及结果

```powershell
cd D:\biancheng\RunRunCampus-worktrees\rrc-fix-admin-notifications\handover-2026-10-24\RunRunCampusAdmin
pnpm install --frozen-lockfile
pnpm build
```

管理端构建通过。完整 M00→M08 隔离验证：

```powershell
cd D:\biancheng\RunRunCampus-worktrees\rrc-m08
powershell -NoProfile -ExecutionPolicy Bypass -File .\server\db\tools\verify-m08.ps1 `
  -PrerequisiteRoot D:\biancheng\RunRunCampus-worktrees\rrc-main-integration
```

退出码 0，输出 `PASS: M00→M07→M08 strict migration, duplicate rejection, ownership and rollback guard`。验证器覆盖通知入队、站内消息读取/已读、outbox 幂等、并发 claim、租约恢复、失败退避和未授权访问拒绝。

通知专项契约测试本身不启动 Docker；需要真实 PostgreSQL 行为验证时，使用上述隔离 runner/Testcontainers。定时 worker 会先检测 `claim_notification_outbox(integer)`，在仅有 M00–M07 的数据库上安全跳过，不产生 SQL 错误。

## 未运行验证

- 未连接真实微信、支付或生产配置。
- 未在运行中的隔离后端执行浏览器端到端联调；需独立 QA 使用测试身份重跑 API 权限和页面流程。
- 完整 Maven 回归依赖 Docker/Testcontainers；Docker 不可用时只能记录环境错误，不能将其解释为业务断言失败。

## 已知风险

现有资质列表若缺少 `version` 或 `photoResourceId`，页面会安全禁用审核按钮，需后端列表补充字段后才能审核。通知接口当前按后端实际数组返回消费；若改为分页对象需同步适配。微信仅使用尽力发送适配器，失败不影响站内消息或业务事务。

## 回滚方式

仅回滚本提交即可恢复原管理端页面和 API 封装：`git revert <commit-hash>`。不涉及数据库迁移和生产配置。建议提交后进入独立 QA，重点复核管理员权限、版本冲突、照片资源归属、通知归属隔离和重复点击幂等。
