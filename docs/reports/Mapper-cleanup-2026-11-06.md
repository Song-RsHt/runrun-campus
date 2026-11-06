# Mapper 专项返工（2026-11-06）

当前状态：完成整改后提交独立复验；用户此前结论仍为“部分通过，需要返工后再验收，暂不建议合并”。本报告不替代独立验收结论。

## 范围和改动

返工起点为 `4b4cbb9`；源码提交为 `d39d7d1`、`4577164`。仅调整全部 Mapper 的命名、业务参数类型和 SQL 排版，同步必要调用方，并补一项类型收窄后的可空值数据库回归。没有扩展架构、业务状态机、依赖或配置范围。

共审查 22 个 Mapper（包含 OrderStore）、228 个方法；其中 19 个 Mapper 需要修改，另外 3 个 legacy Mapper 已有明确命名、类型和文本块，保留其内容。144 个机械方法名改为操作意图名称，例如：

|原名称|当前名称|含义|
|---|---|---|
|`OrderMapper.createValueV`|`insertOrder`|插入订单并返回 ID|
|`PaymentMapper.startValuePaymentAttempts2`|`insertPaymentAttempt`|插入处理中支付尝试|
|`PaymentMapper.approvedAdjustmentValuePaymentAdjustments2`|`countPendingAdjustments`|统计待补款或待退款的调整|
|`PaymentMapper.availableValueRefundOrders`|`sumReservedRefundCents`|按来源尝试汇总全部退款预留金额，保留原本不筛状态的行为|
|`FulfillmentMapper.deliverWriteV`|`markOrderDelivered`|记录送达及可空确认窗口|
|`CourierEvidenceGovernanceMapper.deleteOneWriteEvidenceDeletionAudits3`|`markDeletionFailed`|记录删除失败原因|

完整方法映射及参数命名见 [mapper-cleanup.json](backend-layered/mapper-cleanup.json)。查询返回的 Map、混合 JDBC 参数集合和数据库 void 函数的 Object 返回值保留；这类容器/驱动接口不属于待收窄的业务入参。

11 个直接 Object 参数全部收窄：

|参数|当前类型|契约依据|
|---|---|---|
|区域、服务点、审核负责人、负责人资格查询 ID|`Long`|BIGINT 外键；负责人允许 null|
|微信摘要、微信绑定摘要及密文|`String`|identity foundation/runtime 的 TEXT 列及现有加密接口|
|调整状态|`String`|原调用方生成的状态字符串和 TEXT 列|
|订单确认截止、退款成功时间|`OffsetDateTime`|TIMESTAMPTZ 列及现有 now()/确认窗口调用；保留 null|
|legacy 财务凭证 SHA-256|`String`|请求契约为 String；小写摘要或 null|

Service 只在既有 JDBC Map 边界读取明确类型。可空负责人分支两侧均保持 Long，避免与 primitive long 混用触发自动拆箱；新增真实 PostgreSQL 测试 `disabledConfigPreservesNullableReviewOwner`，验证未设置负责人时仍可更新关闭接单的配置、开启接单仍返回原错误码且事务不留下额外审计。

SQL 使用 Java 17 文本块，INSERT 列/值、SET 赋值、WHERE 条件、排序、锁和子查询分别换行。保留原有人工排版的文本块；动态筛选及锁选择仍只拼接原有受控片段。动态表名后的前导空白显式保留，避免拼出 `delivery_zonesWHERE`。没有重排绑定参数，也未改变 JDBC 执行方法或返回类型。

## 核验方法和运行记录

外部 JavaParser 3.26.3 工具对照 `4b4cbb9` 与最终源码：22 个 Mapper、228 个方法、217 处 JDBC 调用、12 处 String 初始化表达式通过。对比 SQL lexical token（含引号内容和 `||` 等多字符运算符），展开受控条件分支并核对动态拼接边界；按参数位置归一化局部参数名后，JDBC 操作、参数个数和顺序、RowMapper、结果类型均一致。该工具未加入工程依赖；其结果只作为静态证据，不能替代真实数据库测试。

过程中两轮探索性回归失败：第一轮暴露格式工具拆开 `||`（153 项中 13 个 errors），第二轮暴露动态表名与 WHERE 缺少空白（153 项中 3 failures、62 errors）。均已修复，并强化运算符和拼接边界核对；这些失败记录未删除，也未计入最终通过。随后 153 项受影响测试已通过；最终提交另补可空值回归，以下最终结果以该源码重新运行的日志为准。

最终源码提交 `4577164dfd2276a6e3d80b9c34a9e8a585b5ab56` 的验证结果：

|检查|结果|
|---|---|
|受影响回归（全部 v2 加相关 legacy）|154 项、20 个测试类，0 failures/errors/skipped，退出 0|
|完整 Maven 回归|488 项、78 个测试类，0 failures/errors/skipped，退出 0|
|新增可空负责人数据库用例|通过；完整数量由原 487 增为 488|
|迁移、资源/application、依赖、Docker/Compose、前端、AGENTS 相对业务基线|零差异|

日志 SHA-256、完整方法/类型映射和源码树记录于 [机器核验记录](backend-layered/mapper-cleanup.json)。原日志留在被 Git 忽略的后端 `.refactor-evidence/`；未提交诊断日志或测试凭据。

复验命令（在后端目录运行）：

```powershell
$env:MAVEN_OPTS='-Duser.language=en -Duser.country=US'
mvn -B -ntp '-Dtest=com.zhangxuan.runrunv2.**,AdminCommandCenterPostgresIntegrationTest,AdminDispatchServiceTest,FulfillmentProvenanceServiceTest,UserRoleSchemaMigrationTest,CreditPostgresIntegrationTest' test
mvn -B -ntp test
```

受影响集合覆盖全部 15 个 v2 测试类，以及 legacy 指挥中心、调度、履约来源、角色约束初始化及信用维护相关测试。使用既有 PostgreSQL/Testcontainers 集成测试，未跳过测试、删除断言或替换数据库 fixture。

本轮不重复冷 Compose 构建或固定迁移链演练；上一轮验证仅适用于 verification.json 中记录的旧源码提交。本轮已独立记录测试的最终源码提交、源码树、日志摘要和受保护路径零差异。完整测试中初始化数据库的过程不冒充独立备份恢复验收。

既有 Surefire 退出等待告警及 legacy 缓存调度上下文关闭日志保留。没有修改等待参数、调度业务或测试退出机制来消除告警。未合并、未推送、未部署；最终状态仍为待独立复验。
