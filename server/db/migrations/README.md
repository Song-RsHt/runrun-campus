# 独立 v2 数据库迁移入口（M00）

本目录是 RunRunCampus 全新阶段一系统的迁移根目录，和 `handover-2026-10-24/RunRunCampusBackend/src/main/resources/db/migration` 完全分离。后者的 V1–V43 只作历史参考，禁止复制、改写、导入或在同一数据库运行。

## M00 已冻结的约定

- 数据库目标：全新、隔离的 PostgreSQL 数据库；不得连接生产库或旧数据库。
- 迁移根目录：`server/db/migrations`。
- 迁移命名：`rrc_NNNN__description.sql`；四位递增编号由总控登记，不能复用或重排。
- v2 对象命名空间：`runrun_v2`。后续表、序列、视图、函数和索引必须显式归属该 schema，并在登记表中声明负责人。
- M00 只创建 schema，不创建业务表、索引、触发器、账号、管理员、订单、支付或旧数据。
- Flyway（或等价 runner）必须关闭 `baselineOnMigrate` 和危险的 `clean` 行为；应用启动必须指向本目录且不得同时加载旧 V1–V43 目录。
- 模拟支付、模拟管理员付款登记和测试控制路由不属于 M00，必须由后续模块在隔离环境实现并在生产配置中拒绝。

## 空库执行

在隔离数据库中执行迁移工具的 dry-run/validate 后再 migrate。若暂时没有应用迁移 runner，可使用 PostgreSQL 客户端进行语法验证：

```powershell
psql --set ON_ERROR_STOP=1 --dbname "$env:RRC_V2_DATABASE_URL" --file server/db/migrations/rrc_0001__create_v2_schema.sql
```

`RRC_V2_DATABASE_URL` 必须由本地受控环境注入，不得写入仓库。执行后应确认 `pg_namespace.nspname = 'runrun_v2'`，且没有业务关系。不要把旧库的 `flyway_schema_history` 或任何旧行复制到新库。

## 回滚与备份

`rrc_0001__create_v2_schema.rollback.sql` 仅允许在 M00 之后、后续迁移尚未运行的隔离数据库执行。脚本在 schema 非空时主动失败，避免误删后续对象。生产迁移、生产回滚和真实支付均不在阶段一范围内。
