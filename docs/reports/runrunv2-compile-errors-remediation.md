# runrunv2 编译与运行时绑定整改报告

日期：2026-11-05（Asia/Shanghai）。分支：`fix/runrunv2-compile-errors`。独立 worktree：`D:/biancheng/RunRunCampus-worktrees/rrc-fix-runrunv2`。

## 基线、提交与结论

严格从指定 M09 提交 `39bd56a5a22388da8198e9dda0c618e7c97f13db` 创建整改分支；没有改用本机指向其他提交的 `m09/integration`。起始 worktree 干净。首次在 `D:/biancheng` 执行 git 检查提示不是仓库，随后在实际仓库核对根目录、状态、worktree 和 origin。主工作区已有其他未提交工作，未操作这些工作。

代码提交：`fce5f51269da99ef65545186c38595f95a810531`。报告和验证记录为后续独立文档提交；文档交付 hash 可用 `git log -1 --format=%H -- docs/reports/runrunv2-compile-errors-remediation.md` 核对。

指定基线的 Maven package 和 test-compile 本来通过；本轮没有伪造“修复了 Java 编译错误”的结论。实际修复的是 Maven 不会检查的 SQL 语法、JDBC 类型、HTTP multipart 绑定、绑定异常分类，以及专项覆盖和测试上下文生命周期。最终全后端测试 483 项通过，其中 v2 120 项、旧应用 363 项；0 failures/errors/skipped。全套退出仍有旧应用测试上下文的 Surefire 提示，见风险；v2 专项独立运行无该提示。

不改变 main；main 核对值为 `e0b51a53b5e5b1bf2b7ef39229948a311775b0f5`。没有推送 GitHub、合并 main 或声称远程分支已经可拉取。

## 修改文件清单

以下后端路径均以 `handover-2026-10-24/RunRunCampusBackend/` 为前缀：

|文件|变化|
|---|---|
|`src/main/java/com/zhangxuan/runrunv2/CourierCertificationV2Controller.java`|A38 文本版本字段改用 RequestParam 绑定|
|`src/main/java/com/zhangxuan/runrunv2/CourierCertificationV2Service.java`|业绩 SQL 别名、重复 FROM 与分页溢出修复|
|`src/main/java/com/zhangxuan/runrunv2/CourierEvidenceGovernanceService.java`|TIMESTAMPTZ 与可空 BIGINT 参数明确绑定|
|`src/main/java/com/zhangxuan/runrunv2/IdentityErrorHandler.java`|三个具体参数绑定异常映射到既有 400 VALIDATION_ERROR|
|`src/test/java/com/zhangxuan/runrunv2/FulfillmentIntegrationTest.java`|容器对应上下文关闭；新增实际送达任务的业绩 HTTP、分页、权限回归|
|`src/test/java/com/zhangxuan/runrunv2/IdentityIntegrationTest.java`|类结束关闭上下文及连接池|
|`src/test/java/com/zhangxuan/runrunv2/OrderIntegrationTest.java`|同上|
|`src/test/java/com/zhangxuan/runrunv2/PaymentIntegrationTest.java`|同上|
|`src/test/java/com/zhangxuan/runrunv2/V2JdbcContractDatabaseTest.java`|新增 3 项真实 PostgreSQL SQL/JDBC 回归|
|`src/test/java/com/zhangxuan/runrunv2/V2FeatureWiringIntegrationTest.java`|新增 5 项完整迁移链、真实 Spring/HTTP/文件存储/事务回归|
|`tools/run-v2-isolated-tests.ps1`|默认运行全部 v2 Test；显式 v2 Maven profile；使用环境交付的迁移清单|

根目录增加 `tools/run-v2-isolated-tests.ps1`，转发到后端 runner，使用子进程限定测试环境变量范围。报告附件为 `docs/reports/runrunv2-compile-verification.md`，含各套件结果、脚本输出、数据库对象核对及原始证据索引。

## 错误根因、定义方与调用方

|编号|根因与证据|修复及回归|
|---|---|---|
|R01|`performance()` 的聚合查询把 PostgreSQL 关键字 `month` 用作未转义别名；真实 PG 返回 syntax error at or near month|内部别名改为 month_count，外部 JSON 字段不变；空列表和真实送达任务 HTTP 回归|
|R02|同一方法的明细 SELECT 已有 FROM/JOIN，又拼接含 FROM/JOIN 的 base，形成重复 FROM|SELECT 只拼接一次既有 base；保留原有状态条件、字段及统计口径|
|R03|page 和 size 为 int，`p*s` 在传入数据库前溢出，最大页码可能变负 OFFSET|乘法前提升到 long；验证最大 int 页码返回空页。此处是保持合法分页值的算术扩宽，不是转换业务 ID 或掩盖契约类型|
|R04|`hold()`、`requestCertificationDeletion()` 传入 Java Instant；列为 TIMESTAMPTZ，PostgreSQL JDBC setObject 无 Instant 映射；实际错误 Can't infer the SQL type|在 JDBC 边界使用 `instant.atOffset(UTC)`，不改 API 的 Instant、保留期限或时刻；真实 PG 微秒精度往返断言|
|R05|修复 Instant 后，保全审计 `jsonb_build_object` 的两个 ID 恰有一个 null，驱动无法推断多态参数，实际错误 could not determine data type of parameter $5|按已有外键定义给两个参数 `SqlParameterValue(Types.BIGINT, value)`，保留 null；不伪造默认 ID；真实审计与 HTTP 幂等回归|
|R06|A38 和总契约明确 expectedVersion 是 multipart 文本字段；控制器却声明 RequestPart long。合法表单实际触发 MissingServletRequestPartException 并返回 503|改为 `RequestParam("expectedVersion") long`，保持必填、字段名和业务版本校验；真实 PNG 上传/读取/审批回归|
|R07|缺参数、缺文件、格式错误触发具体 Spring 绑定异常，落入通用 Exception 503；负例实际复现预期 400、实际 503|仅将 MissingServletRequestParameterException、MissingServletRequestPartException、MethodArgumentTypeMismatchException 归为既有验证错误。SQL、存储等异常仍不会变成成功；负例断言版本与照片不变、存储未写入|
|R08|4 个 v2 Spring 集成测试缓存上下文，其类级 PostgreSQL 容器已结束，定时任务及连接池仍访问关闭的容器；基线出现连接失效和 Surefire 退出超时|DirtiesContext AFTER_CLASS 与 fixture 同生命周期结束；不禁用定时功能、不延长退出超时、不减少测试|
|R09|原专项固定 9 类，仅 99 项，漏掉 AdminListDatabase、GovernanceService、LocalTestBoundary；仓库根还没有用户指定的 runner；预检查迁移链遗漏已存在的 M09 0701|默认限定包的通配 Test，加入根转发入口；消费既有 migrations.txt 的 12 份迁移。原测试全部保留，新专项共 14 类、120 项|

重现日志保留首次 3 个 JDBC errors、其后可空审计参数 error、2 个上传 failures、单独的 multipart 根因和错误格式参数 failure；修复后相应回归全部运行通过。没有删除失败测试、降低断言、增加模糊重载或为编译引入占位实现。

## 按要求分类归档

|类别|结果|
|---|---|
|名称无法解析|Java 名称错误未复现；真实 package 与 test-compile 通过|
|import 无法解析|未复现；不引入旧 runruncampus 类|
|方法不存在|未复现；定义方、调用方与测试一致|
|参数数量错误|Java 调用未复现；SQL/HTTP 绑定另列|
|参数类型错误|静态未复现；运行时 R04–R07 已修复|
|构造器不匹配|未复现；Spring 应用及所有测试构造器编译、装配通过|
|record 字段访问错误|未复现；外部访问器调用编译通过。Java 嵌套 record 的封闭类可合法访问其私有字段，不将合法代码改成“修复”|
|Spring Bean 缺失|未复现；本地完整应用和真实 Spring 测试加载通过|
|profile 条件错误|本轮未复现；local-v2-test 禁微信、模拟支付与本地门禁通过|
|测试 fixture/测试数据错误|R08；专项覆盖不足及清单偏差见 R09|
|SQL/JDBC 类型映射错误|R01–R05；全部使用 runrun_v2 对象|
|仅 IDE 提示但 Maven 正常|用户反馈 runrun_v2 无法解析。47 个主源码、14 个测试已编译；数据库核对 33 个静态对象全部存在。但未操作 IDEA，不能断言每条 IDE 提示均无害|
|运行时错误|R01–R08；与 Java 编译结果分开，不以成功启动代替功能调用|

IDE 的 schema 检查依赖 PostgreSQL 方言及隔离数据库/DDL 的关联；没有关联时可能对嵌入 SQL 报无法解析。不得关联生产数据库，也不应通过关闭检查或 SuppressWarnings 抹掉提示。独立 QA 应核对具体诊断对应的 SQL，参照本轮数据库和功能调用证据。

## 重点类与契约核对

|范围|证据及边界|
|---|---|
|IdentityApplication、IdentityController、IdentityWebConfiguration|仅扫描 v2，排除旧 JPA/Flyway；21 项身份测试和 Compose session/logout 门禁。权限注解字段与定义一致，缺声明仍拒绝|
|CourierCertificationV2Controller/Service|定义、调用、资源 ID、版本和幂等键核对；真实 multipart、私有存储、管理员读取审计、APPROVE 重放及普通用户拒绝；列表和业绩 PostgreSQL 回归|
|CourierEvidenceGovernanceController/Service|HTTP 创建/解除保全的幂等与真实审计；保全时刻/删除时刻映射；真实文件删除失败记录 FAILED、重试 SUCCEEDED，照片删除不撤销资格|
|NotificationController/Service/OutboxWorker|真实 SQL 函数调用；recipient、eventKey、channel、payload 和 attempt_count 映射；失败保留 FAILED 与重试时间；站内通知归属及已读接口|
|OrderController/Service、ServiceConfigService、OrderStore|14 项 PostgreSQL 订单测试和 JSON 白名单测试；金额为整数分、版本/幂等及 Clock 路径未变|
|PaymentController/Service、PaymentTestController、MockPaymentAdapter|25 项支付测试和 3 项环境测试；显式隔离开关及生产拒绝；参数、版本与幂等键核对；没有真实支付|
|FulfillmentController/Service/Queries|30 项真实 PostgreSQL 履约测试，含新增业绩 HTTP；状态、容量、权限及金额不变；测试中的证据端口替身不代表真实订单凭证能力已实现|
|LocalTestBootstrap、LocalTestCors|8 项边界测试及 Compose 真实启动、管理员审计、CORS；仅 local-v2-test；没有匿名初始化接口|
|V2StudentCardStorage|真实临时目录 PNG 写入、读取、删除和存储失败重试；测试目录不进入提交|
|WechatHttpGateway、DisabledWechatGateway、订阅 gateway|真实 HTTP 客户端测试使用 mock；本地采用禁用 gateway；订阅失败不伪报发送成功。未访问真实微信|
|ObjectMapper、JdbcTemplate、事务管理器、Clock|Spring 自动配置/已有 v2 Clock 装配及真实服务事务验证；没有新增重复 Bean、公共构造器或生产配置|

## API、Bean、DTO、构造器和数据库变化

没有新增或改名 API 路由，没有扩展服务公共方法签名、DTO、record、Bean 或构造器。A38 的 Java 注解恢复既有文本字段绑定；非法表单绑定恢复既有 E01 的 400 VALIDATION_ERROR。SQL 聚合内部别名变化不影响响应字段。没有修改金额、状态、角色、幂等、并发、删除期限、迁移或数据库结构。

没有新增、删除或修改任何数据库对象；没有改动 server/db/migrations、旧 V1–V43、旧业务、小程序、管理端或生产配置。runner 只改为执行已经在指定基线存在的 12 份隔离环境迁移。33 个源码直接引用对象在该链下存在：30 个关系对象、3 个函数；动态表名 delivery_zones/service_points 另由订单测试实际执行。

## 已运行命令和结果

完整命令、各套件结果、脚本输出与原始日志索引见 [验证附件](runrunv2-compile-verification.md)。后端命令工作目录是本 worktree 的 `handover-2026-10-24/RunRunCampusBackend`；其他命令在仓库根目录。

|命令|结果|
|---|---|
|`mvn -B -q -Pv2-identity -DskipTests package`|基线和最终代码均退出 0；另外在中途 clean 后重新打包通过|
|`mvn -B -q -Pv2-identity test-compile`|基线和最终代码均退出 0|
|`pwsh -NoProfile -File tools/run-v2-isolated-tests.ps1`|基线在后端目录运行 99 项；最终在根目录运行 120 项，0 failures/errors/skipped，BUILD SUCCESS|
|`mvn -B -ntp -Pv2-identity test`|最终 483 项，0 failures/errors/skipped，BUILD SUCCESS；旧测试的退出提示单列|
|`rg -n "package com\.zhangxuan\.runrunv2\|import com\.zhangxuan\.runrunv2" .../src`|结果单独保存；另检查 JDBC 调用和实际数据库对象|
|`tools/initialize-v2-environment.ps1 -ConfirmIsolatedTest`|从示例创建 ignored 本地 env，随机值未打印或提交|
|`docker compose --env-file .env.v2-test -f compose.v2-test.yml up -d --build`|基线、中间源码和最终代码均真实构建启动通过，退出 0|
|`tools/verify-v2-environment.ps1`|基线及最终镜像均通过，退出 0；最终健康、隔离、12 份回执、HTTP 权限、禁微信及退出失效门禁全部通过|
|`server/db/tools/verify-m07.ps1`|退出 0；重复迁移拒绝及隔离证据 schema 通过|
|`server/db/tools/verify-m08.ps1 -PrerequisiteRoot <本worktree>`|退出 0；链、重复拒绝、对象归属和回滚保护通过|
|`git diff --check` 及基线到代码提交范围检查|无输出，退出 0|
|已提交代码的干净本地 clone：package、test-compile|均退出 0；没有复制 env、target、dist 或 node_modules。不是远程 GitHub 发布验证|

最终环境验收后执行同一 Compose 项目的 `down`，退出 0；只停止本轮容器及专用网络，保留三个测试卷和 ignored 本地 env，没有使用 `-v`。需要复核运行环境时按上表原始 up 命令重新启动。

## 未运行、既有缺口与风险

- 未运行生产数据库、生产配置、真实微信、真实支付、真实用户、真机、管理端/小程序浏览器全业务验收或公网 HTTPS。没有把这些项目标为通过。
- 未运行 IDEA 本体的所有检查，未更改 IDE 的检查开关、数据源或工程缓存。数据库对象存在和 Maven 通过不能证明用户看到的每一项提示都已逐条消失。
- 未重新执行 M00–M06 的全部专项、全部迁移备份恢复演练；本轮 M07/M08 和 Compose 的范围见各命令输出。
- 完整 Maven 483 项全部通过，但 Surefire 仍提示 `The exit has elapsed 30 seconds after System.exit(0)`。线程证据有旧 `com.zhangxuan.runruncampus.payment.WechatRefundProcessor` 等调度上下文等待已停止的测试连接池；不改旧模块、不延长退出超时。最终 v2 独立 120 项无此提示。此项不是测试失败，也不能隐去。
- B01：基线的 `PendingFulfillmentEvidence` 仍明确返回 PROOF_NOT_READY，真实订单凭证适配器未实现/未验收。本轮停止在该业务边界，没有增加空实现或接入旧上传服务；不能据此签署完整履约闭环通过。
- B02：A37 重申请的 expectedVersion 在基线 Controller/Service 中未接收；本轮未改变申请 API 或并发语义。需要单独业务契约整改和 QA，不将重申请的完整并发验收记为本轮通过。
- B03：学生证上传基线仅检查 JPEG/PNG 文件头，尚未执行契约要求的完整图像解码验证。本轮用真实生成 PNG 验证绑定、存储和审批，没有把伪造图片验证认定为通过；未跨入照片验证规则变更。
- 管理员资质列表基线的完成单数仍为固定 0；业绩查询保留其既有统计口径与字段，没有将其扩大为“业绩业务口径已验收”。
- 新 JDBC 单层测试对身份/幂等边界使用 mock，数据库为真实 PostgreSQL；新增完整 Spring 测试另行验证真实身份、幂等、事务、存储和通知。微信交换/订阅只使用测试替身，真实服务从未调用。
- 未推送本分支；甲方从 GitHub 获取整改仍需后续获授权的发布。干净本地 clone 验证只证明已提交 Git 对象的可构建性，不冒充远程发布。

## 回滚与独立 QA 建议

代码回滚：`git revert fce5f51269da99ef65545186c38595f95a810531`，然后重新构建。此提交没有 schema 变化，不需要数据库 down SQL；回滚会恢复上述已记录缺陷。报告提交可按交付时给出的文档 hash 单独 revert，保留报告也不影响代码回滚。

建议进入独立 QA，只读复核本次编译、SQL/JDBC/HTTP 绑定和隔离环境修复；不建议据此认定 v2 全业务已实现或准许生产。B01–B03 需作为独立业务事项跟踪。不要合并 main。
