# M00 实施交接单

日期：2026-10-27。分支 `feature/m00-contract`，worktree `D:/biancheng/RunRunCampus-worktrees/rrc-m00`。待只读QA，不合并main。

## 文件清单

- server/db/migrations/up/rrc_0001__create_v2_schema.sql
- server/db/migrations/down/rrc_0001__create_v2_schema.rollback.sql
- server/db/migrations/README.md
- server/db/tools/verify-m00.ps1
- docs/reports/M00-migration-registry.md
- docs/reports/M00-implementation.md

## DB与API

实际创建对象仅 runrun_v2 schema。无业务表、索引、触发器、API、账号或业务数据。验证脚本临时合成表不是应用迁移对象，随唯一隔离容器清理。正向/回滚分目录且都有事务；拒绝旧库/重复初始化，回滚拒绝非空schema。

## 已实现与未实现

已实现：独立迁移链、空库前置断言、受限回滚、对象唯一归属/FK顺序登记、A01/E01/S01/C01公共契约引用、可复现隔离容器验证和备份恢复。

未实现：M01及后续业务、API、迁移runner、共享环境部署、真实微信/支付/真机。新runner接入须另验收，不声称已配置Flyway。

## 验证

实际运行 `./server/db/tools/verify-m00.ps1`，退出0：12项PASS（PG ready、空库初始化、重复拒绝、旧flyway/campus_orders哨兵拒绝、哨兵保留、public enum拒绝、其他schema拒绝、非空回滚拒绝、恢复主键、恢复合成行、空回滚、回滚后重初始化）。PostgreSQL 16-alpine，Docker 29.8.0。备份在隔离容器/tmp内经pg_dump/pg_restore，校验恢复结构与合成行；无宿主端口、网络或挂载，无凭据日志，finally清理唯一容器。

初次脚本路径与PowerShell函数大小写递归问题已修正，最终重跑上述12项全部通过；本机psql不可用通过容器psql完成。未运行：应用集成测试（新应用尚不存在）、共享环境/生产迁移、真实微信、真实支付、客户端与真机。

## 风险与交付

新runner尚未接入；公共基础设施/延迟FK仅登记，须在相应模块实际实现和验收。M00并不表示任何业务功能已实现。

提交hash由最终分支HEAD及总控交接记录给出。建议：独立只读QA通过后才合并；总控另行维护状态表/删除旧报告，这些不属于本agent提交范围。
