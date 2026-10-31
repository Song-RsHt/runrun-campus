# 管理端 v2 壳独立交付报告

分支：`fix/admin-v2-shell`；worktree：`D:/biancheng/RunRunCampus-worktrees/rrc-fix-admin-v2-shell`；提交：`d31f358df6709e49771f8b6c327ad9a5458875bf`

## 修改文件

- `handover-2026-10-24/RunRunCampusAdmin/src/App.tsx`：v2 独立首页、导航、ADMIN 门禁、会话恢复/失效回登录、legacy 控制台隔离；通知与骑手资质审核接入 v2 client。
- `handover-2026-10-24/RunRunCampusAdmin/src/v2Api.ts`：统一 v2 请求、Bearer、错误码、401/403、幂等键、照片受控读取。
- `handover-2026-10-24/RunRunCampusAdmin/src/styles.css`：v2/legacy 标识样式。
- `handover-2026-10-24/RunRunCampusAdmin/tools/v2-shell-scan.cjs`：legacy `/api/v1` 静态扫描。`tools/v2-admin-qa.cjs`：单一 ADMIN v2 API QA。
- `handover-2026-10-24/RunRunCampusAdmin/package.json`：`check:v2-shell` 脚本。`tools/v2-admin-qa.cjs` 改用隔离测试 token 环境变量名。

## v2 页面和路由

v2 壳使用客户端视图：首页、通知列表/已读、骑手资质列表/详情/学生证受控读取/审核。登录使用 `/api/v2/auth/password/login`，恢复使用 `/api/v2/auth/session`，登出使用 `/api/v2/auth/logout`；session key 为 `runrun_admin_session_v2`。仅 `ADMIN` 且 `adminEnabled` 的账号进入。

## legacy 清单和允许的 v1 调用

订单、用户、风险、工单、反馈、管理员账号、审计、旧 MFA/设备管理以及旧指挥台仍在 `LegacyAdminConsole`，不在 v2 导航；它们仅通过 `src/api.ts` 保留旧 `/api/v1` 方法。`tools/v2-shell-scan.cjs` 会列出并限制 v1 字面量只出现在 `api.ts`。

## API、权限和 session

资质审核调用 `POST /api/v2/courier/certification/{id}/decision`，携带 `decision`、`expectedVersion`、`photoResourceId`、`reason` 和 `Idempotency-Key`。通知列表和已读调用 v2 接口，服务端负责当前用户隔离。v2 client 将 401/403 转换为可识别错误，页面自动清 session 并回到登录页；API 地址只来自 `VITE_API_BASE_URL`，默认相对路径，不含生产域名或 token。

## 验证

已运行：`pnpm install --frozen-lockfile`、`pnpm build`、`pnpm run check:v2-shell`、`node --check tools/v2-admin-qa.cjs`。`node tools/v2-admin-qa.cjs` 在缺少隔离测试凭证时按预期返回 BLOCKED（退出码 2）。扫描输出列出 `src/api.ts` 中允许保留的 legacy 调用；v2 client 无 `/api/v1`。

未运行：当前 v2 管理员模型下的浏览器/Playwright 隔离环境联调、真实后端 session/通知/审核请求、Network 截图（当前未启动后端，且禁止生产连接）。现有 `admin-role-qa.cjs` 仍验证旧五角色 `/auth/admin/login` 流程，本次在登录响应等待处超时；该脚本与单一 `ADMIN` v2 模型不适用，不能解释为 v2 业务失败，也不能作为浏览器联调通过证据。

已知风险：需要独立 QA 使用隔离测试后端验证登录返回结构、通知用户隔离、已读幂等、版本冲突及浏览器 Network；legacy 组件仍保留旧实现，不能作为 v2 验收证据。旧五角色 QA 脚本不适用于本 v2 管理员模型。

回滚：`git revert <commit>`。建议先进入独立 QA，不合并 main。




