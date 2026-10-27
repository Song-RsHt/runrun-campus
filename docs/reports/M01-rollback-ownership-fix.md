# M01 回滚索引归属限定修补

日期：2026-10-27（Asia/Shanghai）。实现 agent：`m01_rollback_fix`。

基线：`51926cf313982e155fe5137c19ad5f713c4e0024`；分支：`fix/m01-rollback-ownership`；worktree：`D:/biancheng/RunRunCampus-worktrees/rrc-m01-rollback-fix`。本修补尚待总控与独立只读 QA 验收，不自行合并 main。

## 问题与修改

原提交的验证脚本独立执行 20 项 PASS，但安全 QA 补充探针发现：`CREATE INDEX m02_later_index ON runrun_v2.app_users(created_at)` 后，M01 down 仍成功删除八张基础表和新增索引。原因是回滚仅按索引所属表判断 M01 所有权，误把后续模块加在 M01 表上的索引也视为 M01 自有。

现改为精确的 `(table_name,index_name)` 登记，同时核对表在 runrun_v2 中：10 个显式普通索引、2 个照片资源部分唯一索引、8 个主键索引、9 个唯一约束索引，共 29 个。只允许这些 M01 原始索引随表回滚；即使新增索引挂在 M01 表上，也拒绝回滚。原事务、其他对象检查和默认 RESTRICT 均保留，未引入 CASCADE。

验证将后续表、独立序列、M01 表上的后续索引分开建立/清理，避免同时存在多个违规对象掩盖单项漏检。索引拒绝后检查八表全在、合成行数仍为 `3:1:2`、新增索引仍在；独立序列拒绝后检查三条账号及该序列仍在。移除后续对象后，原正常 M01/M00 逆序回滚继续通过。

## 修改文件清单

- `server/db/migrations/down/rrc_0002__create_identity_foundation.rollback.sql`
- `server/db/tools/verify-m01.ps1`
- `docs/reports/M01-rollback-ownership-fix.md`

正向建表 SQL、M00、其他模块、旧交接代码、V1–V43 和用户原分支均未修改。

## 数据库对象与 API 清单

本修补不创建数据库对象，不改变正向八表、约束、索引、序列或 API。精确索引登记位于回滚 SQL 的 `m01_index(table_name,index_name)` VALUES 清单；实测现有正向迁移生成的索引数为 29，正常回滚能逐项放行。API 清单为空。

## 验证结果

实际运行：`pwsh -NoProfile -File server/db/tools/verify-m01.ps1`，退出码 0，**26 项 PASS**。原 20 项断言内容保留，后续表用例改为单独测试，新增 6 项断言覆盖独立序列、索引数量、独立后续索引拒绝及拒绝后结构/数据保留。原照片目标约束名匹配、审计/事件/幂等负例、pg_dump/pg_restore 合成数据与恢复约束及恢复后负例均保留。

使用 PostgreSQL 16 临时容器，`--network none`、不发布端口、不挂载宿主目录。备份只在该容器内，finally 只清理该唯一容器及其匿名卷。未新增项目依赖、连接真实数据库或打印凭据。

`git diff --check`：提交前通过。提交 hash 由交接消息与分支 HEAD 给出。

## 未实现、风险与回滚边界

未实现/未运行：应用迁移 runner、业务 API、真实微信/支付、共享环境迁移和生产操作。对象登记检查不是通用迁移版本管理器，不能替代执行历史；操作前仍须确认后续迁移已按逆序回滚。

本补丁代码可单独 revert。数据库测试只使用临时合成数据；实际共享库恢复及生产回滚不在本次授权范围。若后续模块变更 M01 对象，仍须按所属模块交付迁移及对应回滚，不得通过扩大 M01 索引放行范围来删除后续对象。

建议合并：在独立 QA 对此新提交复验通过后，可由总控与已通过的身份数据库基础一起集成；不代表完整身份服务或 API 已实现。
