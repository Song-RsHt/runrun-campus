# v2 测试运行边界

日期：2026-10-28。分支：`fix/v2-test-runtime`。

## profile 与数据源

`application-v2-test.yml` 是 v2 集成测试唯一 profile：关闭 Flyway，关闭 Hibernate schema 自动建表，`ddl-auto=none`，绑定 `RRC_V2_JDBC_URL`、`RRC_V2_DB_USERNAME`、`RRC_V2_DB_PASSWORD`，并设置 `rrc.v2.isolated=true`。身份、订单、支付、履约和资质测试的 `@SpringBootTest` 已统一使用 `spring.config.name=application-v2-test`，因此不会合并旧 `application.yml`/`application-dev.yml` 的 public 数据源或旧 Flyway 目录。

测试仍由每个 JUnit 集成类使用 Testcontainers PostgreSQL 16 启动临时实例，以保持测试并行隔离；每个类只按其依赖顺序执行 `server/db/migrations/up` 下的 `rrc_*` 新系统迁移。通知 outbox 随 M01/M03 进入 `runrun_v2`，没有 public 通知回退。

## 可复现 runner

从后端目录运行：

```powershell
pwsh -NoProfile -File tools/run-v2-isolated-tests.ps1
```

runner 自己启动 PostgreSQL 16 临时容器（无网络、无宿主挂载、随机名称），按 `0001,0002,0003,0100,0200,0300,0004,0006,0101,0500` 顺序初始化 `runrun_v2`，再创建 `public.v2_test_sentinel` 验证 public 与 v2 隔离，最后调用 Maven v2 专项测试。失败时退出非零并清理容器；`-KeepDatabase` 仅用于本地排障。测试类自身的 Testcontainers 实例仍是最终被测数据库，runner 的初始化库只负责验证完整链和 public 哨兵，不会被业务测试使用。

runner 默认覆盖 Identity、Order、Payment、Fulfillment、Certification、Notification、支付环境、微信适配和订单 JSON 合约测试；可通过 `-Test` 传入 Surefire 类名列表。真实微信、真实支付、生产配置和生产数据库均不在 runner 范围内。

## 已运行结果

- `mvn -B -ntp -Dtest=NotificationRuntimeTest,PaymentEnvironmentTest,WechatHttpGatewayTest test`：8 项通过，0 失败/错误/跳过。
- `mvn -B -ntp -Dtest=IdentityIntegrationTest,OrderIntegrationTest,PaymentIntegrationTest,FulfillmentIntegrationTest,CourierCertificationV2DatabaseE2ETest,OrderRequestJsonContractTest test`：91 项通过，0 失败/错误/跳过；日志显示 `runrun-v2-test` profile，未加载旧 public Flyway。
- `pwsh -NoProfile -File tools/run-v2-isolated-tests.ps1`：完整 runner 通过，99 项通过，0 失败/错误/跳过；临时 PostgreSQL 16 v2 链初始化 41 张 `runrun_v2` 表，public 仅保留哨兵表，专项测试通过。
- 新增 `NotificationRuntimeTest` 断言通知 outbox 位于 `runrun_v2` 且 `public.notification_outbox` 不存在；支付/履约已有通知事务回滚覆盖。

## 未运行与已知风险

- 未运行全仓库 `mvn test`；旧 public V1–V43 测试仍属于历史代码路径，不能与 v2 runner 混合执行。
- 未连接真实微信、真实支付、生产数据库或共享环境；未做前端/真机联调。
- 各测试类当前各自启动容器并执行依赖迁移，运行时间较长；后续可在不改变隔离边界的前提下引入共享 Testcontainers 生命周期。
- runner 预检库和实际 JUnit 测试库是两个临时实例；预检通过不替代各模块事务测试。

## 回滚

本次只修改测试 profile、v2 测试源码、runner 与本报告，未修改 `server/db/migrations`、生产配置、旧迁移、小程序或管理端。回滚源码可执行 `git revert <本次提交 hash>`；若只需撤销测试入口，恢复 `tools/run-v2-isolated-tests.ps1` 和 `application-v2-test.yml` 即可。临时容器由 runner finally 清理，不触碰共享卷。
