# M09 甲方本地 v2 测试环境交付记录

日期：2026-11-05（Asia/Shanghai）。准备分支：`prepare/m09-v2-environment`。工作目录：`D:/biancheng/RunRunCampus-worktrees/rrc-m09-environment`。起点：`345968cf55606bb157fa3688e00a4120ddaa2685`，即已有 M09 专项测试集成基线。

## 分支状态和边界

已核对 M09 基线祖先、M00–M08 SQL、v2 main、管理端 package/lockfile、小程序构建脚本均包含在本分支。原 feature/m01-identity 工作区的 59 项未提交状态未由本任务修改；main 保持 `e0b51a53b5e5b1bf2b7ef39229948a311775b0f5`，源仓库原本地 m09/integration 保持 `345968c`。

只读 `git ls-remote --heads origin refs/heads/m09/integration` 未返回远程分支。本次没有推送、合并 main、连接生产、调用真实微信/支付、导入真实用户或真实数据库备份。GitHub 可拉取的目标分支仍需要获授权的交付人员发布；不能把本地准备完成描述成远程已经可克隆。

## 修改文件清单

根目录及编排：

- `.env.v2-test.example`
- `.gitattributes`（只增加测试 shell/清单的 LF 约束）
- `compose.v2-test.yml`
- `deploy/v2-test/backend-entrypoint.sh`
- `deploy/v2-test/migrate.sh`
- `deploy/v2-test/migrations.txt`
- `deploy/v2-test/verify-receipts.sh`
- `deploy/nginx/api-test.v2.conf.example`
- `tools/initialize-v2-environment.ps1`
- `tools/verify-v2-environment.ps1`
- `tools/verify-v2-local-migrations.ps1`
- `tools/build-v2-clients.ps1`
- `docs/甲方本地v2测试环境.md`
- `docs/reports/M09-local-environment.md`
- `server/db/migrations/down/rrc_0007__notifications.rollback.sql`
- `server/db/migrations/up/rrc_0701__restore_identity_audit_actions.sql`
- `server/db/migrations/down/rrc_0701__restore_identity_audit_actions.rollback.sql`
- `server/db/migrations/README.md`

后端工程 `handover-2026-10-24/RunRunCampusBackend/`：

- `.dockerignore`
- `Dockerfile.v2-test`
- `src/main/resources/application-v2-local-v2-test.yml`
- `src/main/java/com/zhangxuan/runrunv2/DisabledWechatGateway.java`
- `src/main/java/com/zhangxuan/runrunv2/LocalTestBootstrap.java`
- `src/main/java/com/zhangxuan/runrunv2/LocalTestCors.java`
- `src/main/java/com/zhangxuan/runrunv2/WechatHttpGateway.java`
- `src/main/java/com/zhangxuan/runrunv2/NotificationController.java`
- `src/main/java/com/zhangxuan/runrunv2/CourierCertificationV2Controller.java`
- `src/test/java/com/zhangxuan/runrunv2/LocalTestBoundaryTest.java`

管理端：`handover-2026-10-24/RunRunCampusAdmin/src/App.tsx`。仅本地测试构建允许合成账号，普通构建仍使用原手机号表单。

未改变旧 dev Compose、旧 V1–V43 SQL、现有 v2/JUnit profile 默认行为、生产 Nginx。新增 overlay 明确选用 v2 main，而不是依赖默认 jar 的旧 main。

## Compose、变量和初始化

服务为 `postgres`（16，无发布端口）、`migrate`（显式 psql 成功后退出）、`backend`（容器 0.0.0.0:8082，宿主 127.0.0.1:8082）。项目名 rrc-v2-local-test；专用 bridge network v2_isolated；专用 v2_database、v2_migration_state、v2_evidence 卷。后端 readiness 包含 readinessState 与 db 连通性检查，在 ApplicationRunner 初始化结束后才可健康；以非 root 用户写入专用证据卷。

env 文件输入：RRC_V2_JDBC_URL、RRC_V2_DB_USERNAME、RRC_V2_DB_PASSWORD、RRC_V2_ENVIRONMENT、RRC_IDENTITY_ENCRYPTION_KEY、RRC_IDENTITY_HASH_KEY、RRC_PAYMENT_MOCK_ENABLED、RRC_V2_ADMIN_PHONE、RRC_V2_ADMIN_PASSWORD、RRC_V2_BOOTSTRAP_CONFIRM。Compose 固定注入 SPRING_PROFILES_ACTIVE=local-v2-test、SERVER_ADDRESS=0.0.0.0、SERVER_PORT=8082、RRC_EVIDENCE_ROOT=/app/evidence。所有随机值在本机生成；示例不含实际秘密。

迁移顺序：`0001 → 0002 → 0003 → 0100 → 0200 → 0300 → 0004 → 0006 → 0101 → 0500 → 0007 → 0701`，M00–M08 共 11 份，另加 M09 审计修复 1 份，总计 12 份。完整名称由 migrations.txt 唯一登记。前 11 份是 M09 runner 的实际链，0701 是本次实际启动后发现的集成审计约束修复，不能把另一套 0600/0700 通知链混跑进去。44 张表位于 runrun_v2，不启动旧 public Flyway。

原 SQL 重复执行拒绝；编排仅在独立回执卷内保留 SHA-256，匹配则跳过已完成 SQL。有 SQL 已提交而回执未保存的中断时明确失败，不自动 baseline。修复 M08 空库回滚误拒绝自身 identity sequence 的问题：只接受实际归属 user_notifications.id 的序列，后续对象或通知数据仍阻止回滚，不使用 CASCADE。

执行 `Copy-Item .env.v2-test.example .env.v2-test` 后，显式运行 `tools/initialize-v2-environment.ps1 -ConfirmIsolatedTest`；启动仅允许固定容器 JDBC、test、模拟支付、关闭微信、test-admin 与 CREATE_ISOLATED_TEST_ADMIN 同时成立。首次空账号库初始化受控部署身份，复用已有管理员创建/启用服务并审计；重复启动不重置密码、不重复授权。正常 v2 password login/session/logout 使用 runrun_v2.user_sessions，不复用 v1 会话。密码与 Token 不写入报告或仓库。

## 已运行和结果

|检查|结果|
|---|---|
|后端 -Pv2-identity 打包|通过|
|既有 run-v2-isolated-tests.ps1|99 tests，0 failures/errors/skipped，BUILD SUCCESS|
|新增 LocalTestBoundaryTest|8 tests，0 failures/errors/skipped；干净 clone 复验通过|
|精确 12 份迁移的回归脚本|干净 clone 通过：逐份重复拒绝、旧 public 哨兵库拒绝且不变、后续对象拒绝回滚、保留通知拒绝回滚、空库完整逆序回滚、容器内合成 pg_dump/pg_restore 保留通知/审计及修复约束|
|管理端/小程序构建|通过；共同使用 http://localhost:8082；小程序产物为环境元数据|
|git diff --check、准备 worktree 干净|通过；环境文件/构建产物保持 ignored|
|干净 clone Compose、API 和重启验收|通过：完整启动、保留卷重启、down -v 后重建，结束清理三个测试卷及容器|
|Windows PowerShell 5.1 / PowerShell 7 验收脚本|均通过；修正原生命令转义与 GET 请求体兼容问题|
|运行入口错误配置负例|4 类覆盖：production 标识、外部 JDBC、关闭模拟支付、非合成管理员；均在 JVM 前拒绝|
|既有管理端 v2-admin-qa.cjs|失败：登录/session/通知通过，资质管理员列表返回 503；见已知业务阻塞|

首次 Compose 后端启动因 0101 覆盖审计动作白名单而失败，初始化事务完整回滚、账号数仍为 0；新增 0701 恢复 V2_ADMIN_CREATED/V2_DEPLOYER_CREATED/SERVICE_CONFIG_CHANGED 三个既有动作，并保留精确前置约束验证、重复执行拒绝和审计数据回滚保护。未改写历史 up 文件。

Docker Desktop 29 的 internal-only 网络未实际发布 localhost:8082（HostConfig 有映射但 NetworkSettings.Ports 为空），因此改用独立 bridge；数据源固定与禁用真实适配器的门禁保留，数据库无宿主端口。网络本身不作为出站防火墙。

99 项测试结束时 Surefire 提示 fork JVM 退出等待超过 30 秒后终止，但测试报告为 99/99 通过且进程 BUILD SUCCESS；本记录未将该提示隐藏为额外测试成功。

## API 结果

以下为干净 clone 中实际 HTTP 结果：

|请求|未登录|合成管理员|
|---|---|---|
|GET /api/v2/auth/session|401|200；logout 后 401|
|POST /api/v2/auth/logout|401|200|
|GET /api/v2/notifications|401|200|
|GET /api/v2/courier/certification|401|403，正确拒绝 ADMIN 访问 COURIER 查询|

四个指定路由均无 404。curl.exe -i http://localhost:8082/api/v2/auth/session 返回 HTTP/1.1 401，错误码 AUTHENTICATION_FAILED。本地管理端 CORS preflight 为 200；关闭微信的登录接口为 401。

额外业务 QA 发现 GET /api/v2/courier/certification/admin?status=PENDING&page=0&size=20 返回 503：现有 CourierCertificationV2Service.adminList 查询 u.display_name，但当前 v2 app_users 没有该字段。该问题需要业务/schema 契约修复，未在环境任务中添加占位列或改写业务查询，不能宣称管理端完整业务验收通过。

## 干净 clone 验证方式

验证仓库最初位于 `C:/Users/29375/AppData/Local/Temp/rrc-m09-environment-3f87e6c3`。通过 Git 克隆已提交对象，未复制 worktree 的 env、target、dist 或 node_modules；环境文件从克隆中的示例重新生成。源码仓库属主与当前用户不同，仅对本次 Git 命令指定 safe.directory，不改全局 Git 设置。

```powershell
$qa = Join-Path ([IO.Path]::GetTempPath()) ('rrc-m09-environment-' + [guid]::NewGuid().ToString('N'))
git -c safe.directory=D:/biancheng/RunRunCampus/.git clone --no-hardlinks --branch prepare/m09-v2-environment D:/biancheng/RunRunCampus $qa
cd $qa
git checkout -b m09/integration
git checkout m09/integration
git rev-parse HEAD
git status --short
Copy-Item .env.v2-test.example .env.v2-test
.\tools\initialize-v2-environment.ps1 -ConfirmIsolatedTest
docker compose --env-file .env.v2-test -f compose.v2-test.yml up -d --build
.\tools\verify-v2-environment.ps1 -BuildClients
.\tools\verify-v2-local-migrations.ps1
docker compose --env-file .env.v2-test -f compose.v2-test.yml down
docker compose --env-file .env.v2-test -f compose.v2-test.yml up -d --build
.\tools\verify-v2-environment.ps1
docker compose --env-file .env.v2-test -f compose.v2-test.yml down -v
docker compose --env-file .env.v2-test -f compose.v2-test.yml up -d --build
.\tools\verify-v2-environment.ps1
docker compose --env-file .env.v2-test -f compose.v2-test.yml down -v
```


最终提交还通过本机独立 bare QA 镜像模拟发布：在 C:\Users\29375\AppData\Local\Temp\rrc-m09-delivery-fa2b727f/remote.git 中把 m09/integration 指向最终准备提交，再克隆到 C:\Users\29375\AppData\Local\Temp\rrc-m09-delivery-fa2b727f/RunRunCampus，执行 git checkout m09/integration、生成全新 env、up --build、带前端构建的自动验收及 down -v。QA 镜像仅用于本机验证，不推送 GitHub，不改变源仓库的 m09/integration。

本地 QA 分支模拟发布后的 m09/integration；不更改源仓库被其他 worktree 检出的 m09/integration。甲方使用正式 GitHub 克隆方式见操作文档。

## Nginx / HTTPS

示例仅代理 api-test.runruncampus.cn 的 /api/v2/ 到隔离测试主机 127.0.0.1:8082，保留完整前缀并透传 401，其他路径返回 404。容器 Nginx 要另接隔离网络并指 backend:8082，不能把容器 loopback 当宿主。证书/DNS/防火墙由甲方管理，仓库不保存证书或私钥。localhost 无需公网域名。

现有公网 404 未修改、未验证或宣称修复。甲方应用示例且 nginx -t 通过后，应对 https://api-test.runruncampus.cn/api/v2/auth/session 验证 401；本地 401、公网 404 时检查 server_name、upstream 旧 8080、前缀剥离与 location 匹配。

## 未运行、风险与回滚

- 已知业务阻塞：管理员资质列表 503（引用不存在的 display_name）。环境可以启动、登录并执行指定路由验收，但不能标记 M09 全业务验收通过。
- 未运行生产操作、真实微信/支付、公网 HTTPS/Nginx 安装及证书验证；这是明确边界，不是生产验收。
- 未运行真实浏览器 Network/资质图片/审核闭环、微信开发者工具/真机与小程序完整登录。现有小程序 UI 不能直接输入 test-admin；本次验证构建入口和隔离 API 配置，不声称已完成全业务联调。
- 首次 Docker 构建需下载镜像和 Maven 依赖，可能较慢。镜像使用仓库既有 Maven/Java 17/PostgreSQL 版本系列，无新增应用依赖。
- env、数据库、迁移回执和图片卷应配套保留；改密钥/密码不会自动转换已有数据。进程在 SQL 提交和回执保存之间中断时需本地排障或确认合成数据可弃后重建。
- GitHub 远程分支未发布，甲方目前不能靠 checkout 获得本交付。

停止保留数据使用 `docker compose --env-file .env.v2-test -f compose.v2-test.yml down`；确认本项目合成数据可弃后同命令 `down -v` 清理三个测试卷。源码回滚可在准备分支执行 `git revert <最终交付提交>`。有业务/证据数据时不强行执行 down SQL、不使用 CASCADE，不清理其他项目。

## 交付 hash 与待授权发布命令

最终完整提交 hash 由 `git rev-parse prepare/m09-v2-environment` 输出，交付回复提供该值；本文件不尝试嵌入自身提交 hash。

建议在获明确推送授权后创建 m09/integration 远程分支，作为隔离测试环境交付；资质列表 503 需继续单独修复与业务验收，不合并 main：

```powershell
git -C D:/biancheng/RunRunCampus-worktrees/rrc-m09-environment status --short
git -C D:/biancheng/RunRunCampus-worktrees/rrc-m09-environment rev-parse HEAD
git -C D:/biancheng/RunRunCampus-worktrees/rrc-m09-environment push origin prepare/m09-v2-environment
git -C D:/biancheng/RunRunCampus-worktrees/rrc-m09-environment push origin prepare/m09-v2-environment:refs/heads/m09/integration
```

不使用 --force。如果期间已由别人创建远程分支而非快进，停止发布并审核差异。发布后甲方执行以下命令应得到分支 m09/integration，HEAD 等于交付回复的最终完整 hash：

```powershell
git clone https://github.com/Song-RsHt/runrun-campus.git RunRunCampus
cd RunRunCampus
git checkout m09/integration
git branch --show-current
git rev-parse HEAD
```
