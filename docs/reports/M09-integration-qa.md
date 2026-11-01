# M09 集成验收记录

日期：2026-11-01（Asia/Shanghai）  
分支：`m09/integration`  
worktree：`D:/biancheng/RunRunCampus-worktrees/rrc-m09-integration`

## 已运行

- `tools/run-v2-isolated-tests.ps1 -KeepDatabase`：M00→M08 迁移链成功，`runrun_v2` 初始化 44 张表；Maven Surefire 共 99 项，0 failures、0 errors、0 skipped，BUILD SUCCESS。
- 管理端 `pnpm install --frozen-lockfile`：通过。
- 管理端 `pnpm build`：通过。
- 小程序 `scripts/build-miniapp.ps1 -Environment local -ApiBaseUrl http://127.0.0.1:8080 -AllowedHost 127.0.0.1 -MockPayment`：通过，生成 `.build/environment.json`，`mockPayment=true`。
- 小程序 `node --check app.js`：通过。
- 管理端 `tools/v2-shell-scan.cjs`：`legacyV1Calls: []`。
- QA 脚本支持显式参数 `node tools/v2-admin-qa.cjs --base-url <isolated-host>`，并继续拒绝未获批准的非本地主机。

## 未完成及阻塞

- `node tools/v2-admin-qa.cjs` 已运行，因未提供隔离 HTTP 后端和 `RR_V2_TEST_TOKEN`/合成管理员凭据，安全门禁返回：`BLOCKED: set V2_ADMIN_PHONE/V2_ADMIN_PASSWORD or RR_V2_TEST_TOKEN for an isolated v2 fixture.`
- 未执行浏览器 Network、通知/资质照片真实读取、资质审核幂等请求和过期 session 浏览器回登录验证。
- 未使用真实微信授权、真实支付、生产域名或预发布公网环境。
- 微信开发者工具未在当前环境启动，因此无截图；仅记录了构建元数据和静态检查结果。
- 用户确认阶段一只使用虚拟支付，但虚拟支付仍需可访问的隔离 `/api/v2` 后端；当前未搭建或提供隔离 HTTPS 地址，且未收到可用的临时 `RR_V2_TEST_TOKEN` 字符串，因此无法运行浏览器 Network QA 或微信开发者工具请求联调。不得以虚拟支付替代后端地址和管理员会话凭证。

## 安全边界

本次只使用 Docker 隔离 PostgreSQL 和本地构建配置；没有写入 token、密码、真实用户数据或生产地址。隔离 PostgreSQL 容器已在验证结束后移除。
