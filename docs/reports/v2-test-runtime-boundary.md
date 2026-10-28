# v2 测试运行边界

`application-v2-test.yml` 明确关闭旧 Flyway 和 JPA schema 自动建表，并要求数据源由 `RRC_V2_JDBC_URL` 注入。v2 启动配置只允许连接隔离数据库；`tools/run-v2-isolated-tests.ps1` 会拒绝包含 `public` 或 `v1` 的连接地址。

v2 集成测试使用 Testcontainers PostgreSQL 16，在测试初始化阶段显式执行 `runrun_v2` 迁移。该配置不读取 `src/main/resources/db/migration` 中的历史 V1–V43。真实微信、支付和生产数据库不在测试范围内。
