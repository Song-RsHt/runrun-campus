# M01 身份数据库基础最终验收

日期：2026-10-27（Asia/Shanghai）。结论：**PASS，仅针对身份数据库基础子模块；建议合并。** M01 整体仍为部分实现，身份业务与 API 未验收。

## 提交、负责人与范围

用户交付分支 `feature/m01-identity`，初始数据库提交 `52b2a2f2e82d819c78f3c159cf89a2c959959885`，四项整改提交 `51926cf313982e155fe5137c19ad5f713c4e0024`。静态契约 QA：`m01_rework_schema_qa`；安全与独立运行 QA：`m01_rework_safety_qa`。

51926cf 实测 20 项通过，审计定义和外键索引整改通过静态验收；独立索引探针又发现其回滚把后续模块新增在 M01 表上的索引误判为自有。总控在独立 worktree `D:/biancheng/RunRunCampus-worktrees/rrc-m01-rollback-fix`、分支 `fix/m01-rollback-ownership` 分配限定修补，未修改用户原分支。

最终代码验收对象：`22602ce5382cf0413e338472a3d32c1674f8ab25`。该提交只修 M01 down、验证脚本和修补交接报告，正向 SQL 与 51926cf 完全相同。实现 agent：`m01_rollback_fix`；总控复跑与独立安全 QA 均通过。以前的 BLOCKED 报告保留作历史，不代表当前候选状态。

## 修改文件清单

相对集成前 main，数据库实现与验证涉及：

- `server/db/migrations/up/rrc_0002__create_identity_foundation.sql`
- `server/db/migrations/down/rrc_0002__create_identity_foundation.rollback.sql`
- `server/db/migrations/README.md`
- `server/db/tools/verify-m01.ps1`
- `docs/reports/M01-implementation-rework.md`
- `docs/reports/M01-rollback-ownership-fix.md`

总控另提交本验收报告和 `docs/当前实现状态.md`。旧交接源码、V1–V43、客户端、生产配置及权威业务规则均未修改；未新增依赖，不提交缓存、备份、凭据或真实用户数据。

## 数据库对象与接口

唯一新链 rrc_0001 → rrc_0002；所有 M01 对象在 `runrun_v2`，没有第二套 rr_* 初始化或旧表引用。创建八表：

|表|交付内容|
|---|---|
|app_users|身份、手机号唯一、管理员默认关闭、安全版本和并发版本|
|wechat_identities|按 AppID 的身份/用户双唯一及用户外键|
|user_sessions|会话摘要、有效期和安全版本字段|
|courier_certifications|eligible 与审核状态分离、并发版本、照片资源/存储键成对引用|
|courier_certification_events|事件请求去重、操作者类型与关联约束|
|admin_audit_logs|administrator_id、动作/目标 CHECK、请求去重、理由和 metadata|
|idempotency_requests|操作者/作用域/键唯一、请求摘要、资源和响应字段|
|notification_outbox|事件/接收人/渠道唯一、状态、重试及错误字段|

另有八个自有 identity 序列；索引共 29 个（8 PK、9 UNIQUE 约束索引、2 个照片资源部分唯一索引、10 个普通索引）。精确 `(table_name,index_name)` 清单位于 down 脚本；QA 已与实际 pg_index 逐对核对一致。列、外键及 CHECK 的精确定义见正向 SQL。本次没有业务 API、业务函数或触发器，没有创建账号或业务数据；验证合成数据仅存在于临时容器。

## 实际测试和独立复验

实现 agent、总控、独立安全 QA 分别执行 `pwsh -NoProfile -File server/db/tools/verify-m01.ps1`，最终版本均退出 0，**26 项 PASS**，覆盖空库链、旧 public 哨兵、八表与字段范围、外键索引、重复执行拒绝、微信唯一、提交/批准照片目标约束、审计/资质事件/幂等负例、后续对象拒绝、备份恢复数据和约束、恢复后的拒绝用例以及 M01/M00 逆序回滚。

后续表、独立序列、M01 表上的新增索引分别单独建立并测试；索引拒绝后八张表、合成账号/微信/资质行数 `3:1:2` 和该索引均在。移除后续对象后可正常逆序回滚，不误伤 M01 自有 identity 序列。

安全 QA 对最终提交另用独立临时容器验证：29 项索引清单与 catalog 逐对一致；`CREATE INDEX m02_later_index ON runrun_v2.app_users(created_at)` 后 down 明确拒绝并保留数据/索引；移除该索引后正常回滚成功。此前对 51926cf 的独立函数、enum/domain/composite、序列及外部依赖视图探针也已执行；22602ce 保留相应检查，只收紧索引归属。

容器采用 PostgreSQL 16、无网络、无发布端口、无宿主挂载；备份只在容器内，结束后仅清理本轮容器及其匿名卷。`git diff --check` 通过，正向 SQL 未被总控修补改动，没有连接旧库或生产环境，也未执行真实支付。

## 未实现、风险及下一步

未实现/未运行：Spring/Flyway 或其他应用 runner、身份注册/登录/绑定业务 API、受控管理员启停服务、真实微信兑换、私有证据生命周期、真实支付、客户端联调、共享环境及生产迁移。会话表存在不等于停用即时生效业务已实现；本次不开放资质照片上传/读取/审核。完整证据闭环仍受 M00 的 M07 集成门禁约束。

当前仅使用精确 psql 文件执行。回滚的对象登记检查是手工隔离回滚保护，不是通用迁移版本管理器；执行前仍须核实后续迁移已按逆序回退，并确认目标与备份。后续模块新增/修改共享对象须自带对应迁移及回滚，不得通过扩大 M01 的放行清单删除其对象。共享库或生产回滚、完整运营数据恢复不属于本次验收。

建议合并代码链 52b2a2f → 51926cf → 22602ce 及本次验收文档；仅把数据库基础标记已实现/已测试，M01 整体保持部分实现，其他模块状态不变。后续从更新后的 main 开展 runner 与身份服务，不能从缺少总控补丁的旧候选开始集成。
