# 后端业务子包归类（现行结构）

日期：2026-11-06。分支：`refactor/backend-sky-style`；工作区：`D:/biancheng/RunRunCampus-worktrees/rrc-refactor-backend-sky`。不推送、不合并、不部署。

## 最新确认与基线

用户在本会话确认采用 `controller/order`、`service/order`、`service/impl/order`、`mapper/order` 这样的业务子包结构。该确认替代此前完整任务中的技术层平铺要求。此前对“包与类同级”的反馈处理有误：不应把用户认可的业务子包一并删除；现已恢复并将对应业务类实际归入子包。

本次起点为已交付提交 `2836dee1658596f031b7af6c166299eb84057029`，起始 Git 工作区干净。沿用此前接口/Impl、中文注释、四空格和 Mapper 整理成果，不退回旧实现。原始重构基线仍为 `030924f625bb1c9403062e2601fdb09d27189551`。

最终源码提交：`2e73735e21a6b1aef8563e05ef9ffbd377299287`；后端工程 Git tree：`34bdddf0dc32a7828f4fed9782b0bae78097d0f1`。后续提交仅更新交付文档；最终交付提交可用 `git log -1 --format=%H -- docs/reports/Backend-business-subpackages.md` 定位，交付会话同时提供提交号。不能将旧平铺提交的运行结果直接当作本次通过。

## 实际结构与全类清单

两个应用分别组织，未合并命名空间。技术层保持 Controller → Service 接口 → Impl → Mapper/Repository；层内按业务归类。

```text
com.zhangxuan.runrunv2/
├── IdentityApplication.java
├── controller/
│   ├── identity/IdentityController.java
│   ├── order/OrderController.java
│   ├── payment/PaymentController.java
│   └── courier/、evidence/、fulfillment/、notification/
├── service/
│   ├── order/OrderService.java
│   ├── payment/PaymentService.java、OriginalPaymentWriter.java
│   ├── identity/、configuration/、courier/、evidence/、fulfillment/、notification/
│   └── impl/
│       ├── order/OrderServiceImpl.java
│       ├── payment/PaymentServiceImpl.java
│       └── identity/、configuration/、courier/、evidence/、fulfillment/、notification/
├── mapper/order/、payment/、identity/、configuration/、courier/等
├── dto/order/、payment/、identity/、courier/、fulfillment/等
├── vo/order/、payment/、identity/、courier/、fulfillment/等
├── model/order/、identity/、fulfillment/、notification/、upload/、resource/
├── enums/fulfillment/、payment/、order/
├── task/payment/、fulfillment/、notification/、evidence/
└── config/、security/、handler/、exception/、bootstrap/、integration/<职责>/

com.zhangxuan.runruncampus/
├── RunRunCampusBackendApplication.java
├── controller/address/、auth/、admin/、order/、payment/等
├── service/address/、auth/、admin/、order/、payment/等
├── service/impl/address/、auth/、admin/、order/、payment/等
├── mapper/admin/、api/、identity/、credit/
├── repository/address/、auth/、admin/、order/、payment/等
├── dto/<业务>/、vo/<业务>/、entity/<业务>/、model/<业务>/
├── enums/<业务>/、constant/<业务>/、task/<业务>/
└── config/、security/、handler/、exception/、bootstrap/、integration/<职责>/
```

迁移前后总数均为 **518 个主源码、78 个测试源码**；实际移动 **465 个主源码和 63 个测试源码，共 528 个文件**。47 组 Service 接口/Impl 保留，接口与实现按相同业务配对。基础设施仍保留真实职责，不为 Worker、Gateway、Calculator 或 Storage 机械新增 Service 接口。

[完整迁移表与 47 组接口/Impl 对照](backend-sky-style/final-mapping.md) 覆盖从本次基线到当前的全部 596 个源码类型，并保留从原始基线到当前的映射；[机器清单](backend-sky-style/final-class-mapping.json) 包含实际路径、分类、接口配对及测试映射。路径均逐项检查，不以“目录已创建”代替类归位。

DTO、VO、Entity 继续按类型区分，然后分别按业务归类：请求放 dto，响应放 vo，JPA 实体放 entity，内部模型放 model；未恢复巨型 Models 容器，也未改变 record、Jackson、校验或构造器行为。

## 调用链和人工抽查

|场景|当前实际路径|
|---|---|
|v2 订单|`controller.order.OrderController` → `service.order.OrderService` → `service.impl.order.OrderServiceImpl` → `mapper.order.OrderMapper`、`OrderStore`|
|v2 身份|`controller.identity.IdentityController` → `service.identity.IdentityService` → `service.impl.identity.IdentityServiceImpl` → `mapper.identity.IdentityMapper`|
|v2 通知|`controller.notification.NotificationController` → `service.notification.NotificationService` → `service.impl.notification.NotificationServiceImpl` → `mapper.notification.NotificationMapper`|
|legacy 地址|`controller.address.AddressController` → `service.address.AddressService` → `service.impl.address.AddressServiceImpl` → `repository.address.AddressRepository`|
|legacy 订单|`controller.order.OrderController` → `service.order.OrderService` → `service.impl.order.OrderServiceImpl` → `repository.order.OrderRepository`|

人工打开并检查上述订单、身份、通知及地址的入口、接口、实现和数据访问文件，核对 package、import 和构造器依赖；中文 Javadoc、复杂事务说明、四空格、SQL 文本块及上一轮业务方法名均保留。可直接进入对应子包找到类文件；接口和实现不再散落在各技术层根包。

`BackendLayeringTest` 仍为 7 项，更新为验证业务类必须位于业务子包，文件路径与 package 一致，47 组接口与 Impl 属于同一业务子包。Controller/Service/Mapper 方向检查也改为覆盖子包，避免平铺时代的 endsWith 判断漏检。两个应用隔离、8 个调度表达式与启用条件、MFA 回滚检查继续保留。

## 行为保持证据

1. 对本次基线与当前 **518 个主源码类**做 Java 17 语法树比较。排除 package/import 和按映射更新的全限定名后，所有类体一致，变化数为 0；注解、字符串、SQL、事务声明、控制分支和参数顺序均包含在比较中。本次没有业务方法或模型字段修改。
2. Java 符号分析在搬迁前解析全部 596 个源码的类型引用，分别同步 v2 和 legacy 的 import、类引用及测试；JPQL 中的枚举全限定名同步迁移。真实 PostgreSQL/JPA 测试重新运行，编译成功不替代字符串查询验证。
3. 主源码和测试分别核对实际路径与 package，均为 0 处不一致；类数量不变。保留此前的上传延迟读取、中立业务异常、失败事务规则和 Controller HTTP 适配。
4. 相对本次基线，数据库迁移、pom.xml、application 资源、Compose、部署脚本、管理端和小程序均无变化。扫描仍以各自启动类命名空间为边界，未扩大到公共父包；未改变事务、权限、金额、状态机、幂等、outbox、证据保留或生产模拟门禁。

## 重新验证

以下命令在本次最终源码执行，测试数量按实际结果统计，各项不可相加为去重总数。

|检查|命令|退出码与结果|
|---|---|---|
|默认编译与测试编译|后端目录 `mvn -B -ntp -DskipTests test-compile`|0；v2、legacy 分阶段均通过，518 个主源码、78 个测试源码|
|架构专项|后端目录 `mvn -B -ntp -Dtest=BackendLayeringTest test`|0；7 项，0 failures/errors/skipped|
|完整回归|后端目录 `mvn -B -ntp clean test`|0；500 项，0 failures/errors/skipped|
|v2 打包|后端目录 `mvn -B -ntp -Pv2-identity -DskipTests package`|0；制品打包通过；此命令不替代测试|
|v2 隔离 runner|根目录 `pwsh -NoProfile -File tools/run-v2-isolated-tests.ps1`|0；128 项，0 failures/errors/skipped|
|固定迁移链|根目录 `pwsh -NoProfile -File tools/verify-v2-local-migrations.ps1`|0；12 条初始化及重复拒绝、旧库隔离、回滚保护、备份恢复和逆序回滚通过|
|最终源码 clean clone Compose|`docker compose --env-file .env.v2-test -f compose.v2-test.yml up --build -d`|0；镜像从源码打包，postgres/backend 健康、migrate 正常完成|
|环境与接口复验|`pwsh -NoProfile -File tools/verify-v2-environment.ps1`|0；41 条 PASS，覆盖管理员登录、session、通知、三种资质列表、登出撤销、权限、迁移回执和隔离检查|
|差异检查|根目录 `git diff 2836dee HEAD --check` 和 `git diff --check`|0；无空白错误|

构建源码 clone 位于 `D:/biancheng/rrc-sky-tools/sky-business-source-2e73735`，项目名 `rrc-sky-business-source-20261106`。使用原 Dockerfile，从 clone 源码打包，不复制工作区 target。凭据由原初始化脚本生成在忽略文件中，不输出、不提交。包含本报告的末端提交另建干净 clone `D:/biancheng/rrc-sky-tools/sky-business-delivery`，核对后端 tree 相同后复验 Compose 与环境脚本，项目名 `rrc-sky-business-delivery-20261106`；其实际提交号在交付会话给出。Compose 与验证脚本都在同一进程设置 `$env:COMPOSE_PROJECT_NAME` 后执行。验收后停止本轮容器和网络，保留隔离卷和本地凭据，方便复验。

完整回归仍出现既有 Surefire 退出等待告警及测试容器关闭后的连接告警，最终 BUILD SUCCESS、退出码 0。没有关闭任务、跳过集成测试或降低断言隐藏告警。本次语法树审计第一轮因 LF/CRLF 差异报告不一致；统一换行后 518 个类体全部一致，未据此修改业务代码。

日志保留在工作区外 `D:/biancheng/rrc-sky-tools/business-*.log`，不提交工具、缓存、env、凭据、备份或制品。

未运行：管理端/小程序构建、真实微信/支付外部联调、预发布和生产部署、独立 QA。新结构需独立 QA 验证用户阅读习惯，不能只凭测试通过宣称验收。

## 提交、回滚与风险

|提交|内容|
|---|---|
|`e06f730`|v2 类及测试归入业务子包，保留接口与 Impl|
|`0541d9c`|legacy 类及测试归入业务子包，更新 JPQL 引用|
|`2e73735`|架构门禁验证业务子包、服务配对及原有运行边界|
|本报告所在交付提交|当前维护指南、全类映射和验证记录|

回滚在独立分支进行，按上述提交逆序 revert；不 reset、不改写已发布分支。只需查看旧结构时，使用 `git worktree add --detach <新的独立路径> 2836dee`，不切换用户工作区。不运行数据库回滚，因为本轮没有数据库变更。

维护指南修改前已按 AGENTS 保存可恢复副本、原路径和 SHA-256 清单于当前工作区忽略的 `work/backups/RunRunCampus/`。所有类搬迁可从 Git 还原；不删除业务源码。

风险是仓库外脚本可能引用旧 Java 全名，以及既有 Surefire 退出告警。本轮继续保留中文接口、事务、锁与敏感信息边界。建议进入独立 QA，重点检查业务归属和实际目录可读性，暂不建议合并；不替换 `m09/integration`，不推送、不合并，待后续发布指令。
