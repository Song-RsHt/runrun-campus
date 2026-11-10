# M09 管理员资质列表 503 修复复验

日期：2026-11-05。独立 worktree：`D:/biancheng/RunRunCampus-worktrees/rrc-m09-environment`；分支：`prepare/m09-v2-environment`。

## 基线与范围

环境交付基线 `008d283801bc0d605713ce43be7a1bb0ad83d319` 保留不变，原报告中的 503 是当时的实际结果。修复代码提交为 `f66cebdd5d92f845b605a738c3479a976dce78ab`，是其后继提交。

仅修复管理员资质列表：移除不存在的 `app_users.display_name`，对可空筛选参数显式指定 text 类型，按管理端 DTO 返回 camelCase 字段，使用账号创建时间作为注册时间，大页码使用 long 偏移避免溢出。新系统未采集姓名、学校、学号和有效期，返回 null；展示名称使用“骑手 #ID”，搜索匹配账号 phone 字段。没有新增数据库列或迁移，也没有导入旧数据。

修改文件：

- `CourierCertificationV2Service.java`：列表查询与响应映射。
- `CourierCertificationAdminListDatabaseTest.java`：真实 PostgreSQL 16 回归。
- `tools/verify-v2-environment.ps1`：增加无筛选、PENDING 和 query 列表的 HTTP 验收。
- `docs/甲方本地v2测试环境.md`：更新当前列表状态，保留原环境报告。
- 本报告。

## 已运行：数据库回归

在后端目录执行：

```powershell
mvn -B -ntp -Pv2-identity '-Dtest=CourierCertificationAdminListDatabaseTest,CourierCertificationV2DatabaseE2ETest' test
```

结果：3 tests，0 failures/errors/skipped，BUILD SUCCESS。新增测试在 Testcontainers PostgreSQL 16 空库上按 `deploy/v2-test/migrations.txt` 执行全部 12 份 SQL，验证空列表、合成骑手非空列表、DTO、状态/账号筛选、分页、页码溢出边界和权限拒绝。权限服务为 mock，SQL 使用实际数据库；不是完整 HTTP 审批验收。

## 干净 clone 的 Compose 复验

干净 clone 目录：`C:/Users/29375/AppData/Local/Temp/rrc-certification-fix-2f0eb26f/RunRunCampus`。从本地已提交 Git 对象克隆，检出完整 `f66cebdd5d92f845b605a738c3479a976dce78ab`，在该 clone 创建本地 `m09/integration`；不依赖原 worktree 的未提交文件。未声称 GitHub 已有该分支。

实际执行的主要命令（`$qa` 为新建临时目录）：

```powershell
git -c safe.directory=D:/biancheng/RunRunCampus/.git clone --no-local --branch prepare/m09-v2-environment D:/biancheng/RunRunCampus-worktrees/rrc-m09-environment "$qa/RunRunCampus"
cd "$qa/RunRunCampus"
git checkout -b m09/integration
git rev-parse HEAD
Copy-Item .env.v2-test.example .env.v2-test
./tools/initialize-v2-environment.ps1 -ConfirmIsolatedTest
docker compose --env-file .env.v2-test -f compose.v2-test.yml up -d --build
./tools/verify-v2-environment.ps1
```

结果：仓库 Dockerfile 实际构建成功；PostgreSQL 和 backend 健康；完整 12 份迁移回执校验通过、runrun_v2 schema 完整、旧 public 合成哨兵未变；test/mock/禁用微信门禁和隔离管理员一次性审计通过。无筛选、status=PENDING、query=test-courier 三种管理员列表请求均 200 且返回分页对象；HTTP 数据为空，非空数据映射由上节真实数据库测试覆盖。

|请求|实际结果|
|-|-|
|GET /api/v2/auth/session（匿名）|401|
|POST /api/v2/auth/logout（匿名）|401|
|GET /api/v2/notifications（匿名）|401|
|GET /api/v2/courier/certification（匿名）|401|
|GET /api/v2/courier/certification/admin（匿名，额外 curl）|401|
|GET /api/v2/courier/certification（ADMIN）|403|
|GET /api/v2/courier/certification/admin（三种筛选，ADMIN）|200|

另运行 `node handover-2026-10-24/RunRunCampusAdmin/tools/v2-admin-qa.cjs --base-url http://localhost:8082`：登录、session、通知、资质列表、退出及会话失效全部通过。凭据从本机 Compose config 捕获到内存并注入子进程环境，finally 清除；未打印或写入报告。审批 mutation 明确 SKIP，无合成审批 fixture。

重复性验证：执行 `down -v` 清理本项目测试卷后，再执行 `up -d --build` 和自动验收，第二次自动验收全部通过；最后已清理本项目三个测试容器、专用网络及三个卷。镜像及构建缓存保留。日志只保存在 clone 的 ignored `*.local.log`，不提交任何环境值或构建产物。

## 未验收与已知风险

- 本轮未验收完整学生证上传、图片受控读取、审批、业绩详情及小程序真机流程；列表通过不能据此宣布资质业务完整通过。业绩详情仍有既存 SQL 问题，未纳入本次列表修复。
- 原有列表完成订单计数仍是固定 0，不代表已完成业绩统计验收。
- 未重跑整套 M00–M09 专项测试、前端构建、迁移回滚/备份恢复；原环境报告中的验证保留其原提交适用范围。
- 未访问生产、真实微信、真实支付或真实用户数据。公网域名、Nginx、HTTPS 和生产部署未验收；本机测试无需公网 HTTPS。

## 发布与回滚

未推送 GitHub，未合并 main。用户指定的远程 `m09/integration` 基线目标仍为完整 `008d283801bc0d605713ce43be7a1bb0ad83d319`；发布后继修复需要明确新的发布目标，不能默默把已核对基线换成后继提交。发布前只读检查远程分支，不强推覆盖未知提交。

获授权发布原环境基线时：

```powershell
git ls-remote origin refs/heads/m09/integration
git push origin 008d283801bc0d605713ce43be7a1bb0ad83d319:refs/heads/m09/integration
git ls-remote origin refs/heads/m09/integration
```

核对远程完整 hash；若远程出现未知提交，先审核差异，不执行覆盖。建议建立可拉取的 `m09/integration`，但先明确发布原基线还是包含修复的交付提交。

修复无 schema 变化，源码可 `git revert f66cebdd5d92f845b605a738c3479a976dce78ab` 后重新构建；回退会恢复列表 503。停止测试环境用 `docker compose --env-file .env.v2-test -f compose.v2-test.yml down`；只在确认本项目合成数据可弃时加 `-v`。不对业务或证据数据强行运行 down SQL。
