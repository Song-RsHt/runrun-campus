# 苍穹外卖风格后端返工

基线：`030924f625bb1c9403062e2601fdb09d27189551`；分支：`refactor/backend-sky-style`；独立 worktree：`D:/biancheng/RunRunCampus-worktrees/rrc-refactor-backend-sky`。起始工作区干净。参考工程只读，不修改 main、m09/integration 或 feature/m01-identity。

## 参考核对与实施前差距

已实际阅读参考工程的 EmployeeController、EmployeeService、EmployeeServiceImpl、EmployeeMapper，以及 AddressBook 的 Controller/Service/Impl 和 sky-pojo 的 DTO、VO、Entity 示例。采用其技术层目录、业务接口与实现分离、四空格、中文职责说明和业务步骤表达；采用构造器注入，保留当前 record 及 Jackson 行为。

参考工程的 Controller 仍有 admin/user 子包，本项目按用户明确要求进一步集中到各自 controller 包。参考源码出现的字段注入、MD5、默认密码、登录参数日志、Lombok/MyBatis、Result 包装及空 Javadoc 标签均不照搬。

现有差距：业务子包分散；47 个业务服务尚需接口/实现分离；三个 v2 Models 容器及多处 legacy Service 仍内嵌对外响应；JPA 实体与内部值均在 model；四空格、显式 import、控制块花括号及中文说明未统一。上传 Service 的 MultipartFile 与 legacy 服务的 HTTP 错误类型需要边界适配，保持原来的鉴权、文件读取、失败状态、响应正文及事务时序。

## 目录与全类迁移计划

重构前：`controller/<业务>`、`service/<业务>`、`mapper/<业务>`、`repository/<业务>`、`dto/request|response/<业务>`、`model/<业务>`、`enums/<业务>`；两个应用均独立。

目标（在 `com.zhangxuan.runrunv2` 和 `com.zhangxuan.runruncampus` 下分别建立，不创建空目录）：

```text
应用启动类
controller/          HTTP 入口
service/             业务接口
service/impl/        业务实现与事务
mapper/              JDBC SQL 与映射
repository/          legacy JPA Repository
dto/                 请求
vo/                  响应
entity/              JPA 持久化实体
model/               内部投影、快照和值
enums/               枚举
constant/            真正常量
exception/           业务失败
handler/             HTTP 异常映射
security/、config/   安全与运行配置
integration/         渠道、存储和工作器，按需要隔离子包
task/                薄调度入口
bootstrap/           受控初始化和启动校验
```

实施前已生成完整的 384 个顶层类迁移映射、47 组 Service 接口/Impl 清单及 76 个内嵌类型的抽取计划，见 [迁移清单](backend-sky-style/migration-plan.md) 和 [机器映射](backend-sky-style/class-mapping.json)。三个同应用 DTO 重名组将使用明确的账号/用户、订单/信用、MFA 名称解决，不增加业务子包。

实施前分析提交为 `ee224d2`，随后按计划继续实施，未停留在方案。以下记录最终交付及实际验证。

## 最终提交与实施映射

最终 Java 源码提交：`1bd1ce8e7540ae83ed66fa4907e5cf8e6e96d73b`。其后交付提交只包含本报告、最终映射及维护指南；最终交付提交对象使用 `git log -1 --format=%H -- docs/reports/Backend-sky-style-refactor.md` 定位，交付时另在会话提供完整提交号，避免在提交内部写入自身尚未生成的哈希。

后端工程 Git tree 为 `da0449c554b4d8ace1528e612e7fcf19c89abbcc`，src tree 为 `c4e0e0f6c2dcbc3ac8152a247795a6ffca5a3b95`；交付提交的这两个 tree 必须相同。

最终实际类路径见 [最终类映射](backend-sky-style/final-mapping.md) 和 [最终机器清单](backend-sky-style/final-class-mapping.json)。覆盖基线 384 个顶层类型、76 个内嵌类型及新增边界类型；实施前清单保留历史计划，不覆盖为事后计划。三个 Models 容器已拆散，成员分别归入 DTO、VO、model、enums；原类型及最终去向逐项列出。当前主源码 518 个顶层类型（legacy 375、v2 143），测试源码 78 个。

最终技术层结构如下，括号为顶层类型数量；未建立空目录：

```text
com.zhangxuan.runrunv2/                  com.zhangxuan.runruncampus/
├── IdentityApplication.java            ├── RunRunCampusBackendApplication.java
├── controller/ (8)                     ├── controller/ (33)
├── service/ (10 个业务接口 + 写入端口)    ├── service/ (37 个业务接口)
│   └── impl/ (10)                      │   └── impl/ (37)
├── mapper/ (16，含 OrderStore)          ├── mapper/ (6)
├── dto/ (23)                           ├── repository/ (21)
├── vo/ (28)                            ├── dto/ (42)
├── model/ (14)                         ├── vo/ (86)
├── enums/ (5)                          ├── entity/ (20)
├── exception/、handler/                ├── model/ (22)
├── security/、config/                  ├── enums/、constant/
├── integration/                       ├── exception/、handler/
│   ├── idempotency/                    ├── security/、config/
│   ├── notification/                   ├── integration/
│   ├── payment/                        │   ├── maintenance/
│   ├── storage/                        │   ├── payment/、push/
│   ├── web/                            │   └── storage/、web/
│   └── wechat/                         ├── task/ (4)
├── task/ (4)                           └── bootstrap/ (2)
└── bootstrap/ (3)
```

全部 47 组接口与 Impl 对照表见最终类映射的 Service 小节。`OriginalPaymentWriter` 是保留的原始支付原子写入端口，由 `PaymentService` 继承及其 Impl 实现，不额外创建同名业务服务。Worker、存储、网关、计价和校验组件按原职责保留；不制造空接口。依赖业务接口、构造器注入、实现注解及公共业务方法对应关系由架构测试核对。

静态校验、评分和常量已检查归属：`InputValidation`、`OrderInput`、`PrivacyPolicyValidation`、`DispatchScoreCalculator` 承担业务输入校验或评分，放在 model；明确常量进入 constant。请求进入 dto，响应及分页进入 vo，JPA 实体进入 entity。保留 record，不引入 JavaBean、Lombok、MyBatis、MapStruct 或新依赖。

## 可追踪调用链

|场景|Controller → Service 接口 → Impl → 数据访问|
|---|---|
|v2 创建订单|`OrderController` → `OrderService` → `OrderServiceImpl` → `OrderMapper`、`OrderStore`；原始支付委托 `OriginalPaymentWriter`，保持同一事务|
|v2 通知已读|`NotificationController` → `NotificationService` → `NotificationServiceImpl` → `NotificationMapper`；更新条件仍包含账号 ID|
|v2 资质审核|`CourierCertificationV2Controller` → `CourierCertificationV2Service` → `CourierCertificationV2ServiceImpl` → `CourierCertificationV2Mapper`；证据治理协作者仍走接口|
|legacy 私有送达凭证|`AdminOrderDeliveryProofController` → `AdminOrderDeliveryProofService` → `AdminOrderDeliveryProofServiceImpl` → `OrderRepository`、`OrderProofRepository`；存储通过 `OrderProofStorage`|
|legacy 指挥台|`AdminCommandCenterController` → `AdminCommandCenterService` → `AdminCommandCenterServiceImpl` → `AdminCommandCenterMapper`；权限及重复读快照仍由实现承担|

Controller 保留路由、严格 JSON、HTTP 认证上下文及响应头，Service 不引入 HTTP 类型，Mapper/Repository 不反向依赖 Service。新旧应用仍各有同名业务类和独立扫描，不跨应用复用业务。

## 中文注释与排版人工抽查

本轮阅读参考工程的真实文件后，抽查了以下源码和调用链，不用中文字符计数代替审查：

|抽查对象|核对结果|
|---|---|
|v2 `IdentityService`/Impl、`DeploymentIdentityService`/Impl|中文解释临时绑定、会话撤销、受控主机授权和账号锁；错误码、第三方字段和 BCrypt 策略保留|
|v2 `OrderController`、`OrderService`/Impl、`OrderMapper`|入口严格校验可沿接口追踪到业务；幂等重放先于可变营业配置，支付义务原子写入原因明确；SQL 列、绑定次序可逐项核对|
|v2 `PaymentService`/Impl、`FulfillmentService`/Impl|接口说明 UNKNOWN 对账、退款身份复用、版本及锁顺序前置条件；实现保留补偿和完成阻塞说明|
|v2 通知接口、Impl、Mapper、投递任务|区分站内通知和 outbox；领取、租约、网络发送及完成回写边界有说明；批量上限和最近 100 条返回口径明确|
|v2 资质 Controller/接口/Impl/VO|修正误写的“旧系统骑手资质”；图片读取仍在权限、状态和版本校验之后；资质响应无内部存储键|
|legacy `OrderService`、`CouponService`、`MembershipService`、`PrivacyService`|修正通用拼接说明；分别描述 legacy 下单、请求去重、权益扣减及隐私版本，未把 v2 支付义务规则写入 legacy 接口|
|legacy `AdminMfaServiceImpl`、异常 Handler、上传适配|说明失败计数不回滚和延迟读取的原因；Handler 恢复原状态、message 及限流头|
|legacy 指挥台 Mapper、OrderRepository、v2 PaymentMapper|四空格、显式 import、花括号；SQL 文本块按字段、子句和条件排版，保留上一轮业务方法名与明确参数类型|

同类依赖字段集中排列，没有逐字段空行；一行一条语句，控制块保留花括号。文本块缩进另行核对运行时字符串，避免将格式修正变成 SQL、JPQL 或隐私政策正文变化。英文协议标识、错误码、日志分类及测试方法名保留；注释以中文表达职责、业务前置条件及事务原因，不填空标签，不逐行解释简单赋值。

## 行为保持证据与架构门禁

1. 以基线、类映射和 Java 17 语法树核对：41 个 Controller、170 个路由方法、210 个 record、20 个 JPA 实体、38 个枚举、200 个方法事务声明、19 组类启用条件及 12 组方法调度/启用条件。除了类型搬迁与下面明确的异常适配，所核对声明一致。
2. 49 个原数据访问类型（包含内嵌类型）及 216 个 JDBC 调用核对 SQL/JPQL 内容、条件和参数绑定次序；按迁移映射修正 JPQL 枚举全名，忽略 SQL 排版空白。SQL 文本块收尾另外比较解析后的运行时字符串，未改变字段、版本条件和锁。此核对是所列声明与数据访问的不变量检查，不宣称形式化证明整个业务等价。
3. 业务接口移除 MultipartFile，入口通过各应用自己的 `MultipartUploads` 延迟适配 `UploadSource`。适配不预读、不开事务；原鉴权、锁和文件读取时序保留。私有凭证、资质和隐私专项测试与完整回归同时核验。
4. legacy 服务将 Web 异常转为中立 `BusinessFailure`，Handler 保留原响应。MFA 失败的 noRollbackFor 覆盖新业务异常，并按完整类名保留加密协作者原 Web 异常的原有匹配。架构测试检查两类失败均不回滚、意外内部异常仍回滚；真实 PostgreSQL MFA 测试保留失败计数与挑战消费断言。
5. `BackendLayeringTest` 共 7 项检查：真实包路径、47 组接口与实现、构造器业务依赖、Controller/Service/Mapper 方向、新旧应用隔离、8 个原调度的表达式及启用条件、MFA 回滚策略。Task 不直接访问数据库；未关闭调度掩盖退出告警。基线的 78 个测试类型全部保留并列出迁移映射；语法树核对未移除原业务测试方法，替换的三个架构方法已加强为技术层和调度条件检查，并补充业务异常响应契约用例。
6. 与基线相比，`server/db/migrations`、pom.xml、application 资源、Compose、部署脚本、管理端和小程序没有差异。新旧主类保留原扫描范围；v2 仍排除 JPA/Flyway，legacy 不扫描 v2。生产配置值及模拟功能生产门禁未变。

## 实际验证记录

日期：2026-11-06，Asia/Shanghai。Java 17、Maven 3.9.16、Docker 本地隔离环境；真实 PostgreSQL/Testcontainers 测试未跳过。以下测试数量分别统计，不能相加为去重总数。

|命令及位置|退出码|结果|
|---|---|---|
|后端：`mvn -B -ntp clean test`|0|默认编译 518 个主源码、测试编译 78 个源码；500 项，失败 0、错误 0、跳过 0|
|后端：`mvn -B -ntp -Dtest=BackendLayeringTest,AuthControllerTest,AdminAuthorizationControllerTest,AdminOrderDeliveryProofControllerTest,OrderProofServiceTest,PrivacyServiceTest,PrivacyPostgresIntegrationTest,AdminMfaPostgresIntegrationTest test`|0|受影响专项 62 项，失败 0、错误 0、跳过 0|
|后端：`mvn -B -ntp -Pv2-identity -DskipTests package`|0|v2 profile 打包；此命令仅验证制品，不充当测试通过|
|根目录：`pwsh -NoProfile -File tools/run-v2-isolated-tests.ps1`|0|最终源码 128 项，失败 0、错误 0、跳过 0；44 张 v2 表及 M08 函数、旧 public 哨兵隔离检查通过|
|根目录：`pwsh -NoProfile -File tools/verify-v2-local-migrations.ps1`|0|12 条固定链初始化及重复拒绝、旧库隔离、保留数据/后续对象回滚保护、备份恢复、12 条空库逆序回滚；30 条 PASS|
|最终源码 clean clone：`docker compose --env-file .env.v2-test -f compose.v2-test.yml up --build -d`|0|从提交克隆，按原 Dockerfile 在容器内打包，postgres 与 backend 健康，migrate 正常退出；未复用工作树 target|
|最终 clone：`pwsh -NoProfile -File tools/verify-v2-environment.ps1`|0|41 条 PASS：管理员登录、独立 session、通知、三种资质列表、登出撤销、权限、私有卷、迁移回执及隔离哨兵|
|`git diff --check`|0|无空白错误；交付提交后再次核对|

验证日志保留在工作树外 `D:/biancheng/rrc-sky-tools/`，不提交原始运行日志、凭据、缓存、target 或备份。已知 Surefire 告警 `The exit has elapsed 30 seconds after System.exit(0)` 在完整回归复现，Maven 最终 BUILD SUCCESS、退出码 0；保持单独记录，不声称已修复。

源码 clean clone 为 `D:/biancheng/rrc-sky-tools/sky-clean-clone-1bd1ce8`；交付末端另使用 `D:/biancheng/rrc-sky-tools/sky-clean-delivery` 干净克隆包含报告的提交，核对上述后端 tree 不变，再执行相同 Compose 启动与 41 项环境检查。项目名分别使用 `rrc-sky-final-20261106` 和 `rrc-sky-delivery-20261106`：先设置 `$env:COMPOSE_PROJECT_NAME`，再执行 Compose 和验证脚本，凭据均在各 clone 内重新生成。镜像构建缓存可命中，但没有复制本地制品或修改 Dockerfile。验收后仅停止本轮 Compose 项目的容器与网络，保留隔离卷及忽略的本地凭据，方便复验。

探索检查中曾因子进程未继承正确的 COMPOSE_PROJECT_NAME 退出 1，报 postgres 不存在；明确设置本轮隔离项目名后重跑通过，没有改动源码或环境校验断言。原始失败不计为通过；最终检查采用上文明确的项目名。

未运行：管理端及小程序构建（本轮禁止修改客户端，verify 未使用 `-BuildClients`）；真实微信身份及真实支付外部联调、预发布/生产部署；独立 QA。本轮 Compose 使用新生成、Git 忽略的合成凭据及独立项目名，不导入其他环境数据，不在报告输出密码、令牌或真实用户信息。

## 分阶段提交与回滚

|提交|阶段|
|---|---|
|`ee224d2`|参考对照、目录计划、类迁移及接口清单|
|`f2a6541`|v2 身份、会话、受控初始化|
|`c16f1fb`|v2 通知及 outbox 工作器边界|
|`21a500b`|v2 订单、支付、履约|
|`c28d4a2`|v2 资质、证据、存储|
|`2e6381b`|legacy 身份、隐私、账号|
|`8c2f6f5`|legacy 订单、支付、客服|
|`e9f1f23`|legacy 管理、骑手、消息|
|`6ecc989`|上传传输边界适配|
|`a8c40e9`|legacy 中立业务失败及 HTTP 适配|
|`0026cd6`|显式 import 清理|
|`1ef230d`|DTO 重名消解及类型分类复核|
|`211aaab`|四空格、中文职责和业务说明|
|`f90254b`|测试移包、架构和调度门禁|
|`8d6fdf9`|SQL/JPQL 文本块缩进收尾|
|`1bd1ce8`|按实际业务修正中文接口、返回值和资质说明|

回滚仅操作本轮独立分支：评审任一中间状态时，使用 `git worktree add --detach <新的独立路径> <阶段提交>`，不 reset 或切换用户工作区。需撤销最后一阶段时，在独立回滚分支用 `git revert <阶段提交>`；跨阶段按表从后向前撤销，因为后续引用依赖此前的接口和移包。完整撤销从末端逆序 revert 本轮全部提交，或创建基线 `030924f` 的新 worktree 对照；不改写已发布分支历史。中间阶段只表示可回溯提交，不表示该中间组合已通过最终全量门禁。没有数据库、依赖或配置变更，故不执行数据库回滚，也不触碰旧 V1–V43。

维护指南按 AGENTS 规则修改前已保存可恢复副本、原路径和 SHA-256 清单于忽略的 `work/backups/RunRunCampus/`。外部工具及其缓存留在工作树外，不进入提交。

## 风险与独立 QA 建议

包名和 Java 类型名称发生大量变化；仓库内引用、JPQL、测试和启动扫描已同步，若有仓库外脚本使用旧类全名，需按最终映射核对。业务服务现在经接口代理，后续维护应继续把事务留在 Impl，避免用 Impl 类型注入或增加同类自调用。未改变原有事务声明和业务锁顺序，不将结构改进解释为业务问题已经修复。

已知风险是既有 Surefire 退出等待告警，以及大范围结构变更需要独立人工复查；完整回归不能替代用户阅读习惯验收。本轮建议进入独立 QA，暂不建议合并。当前不推送、不合并、不部署，不替换 `m09/integration`。独立 QA 应重点按最终映射打开 Controller → 接口 → Impl → Mapper 调用链，抽查中文注释、DTO/VO/Entity 分类、MFA 失败事务及 outbox 边界，再决定是否进入发布流程。
