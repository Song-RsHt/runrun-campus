# v2 MyBatis-Plus 持久化改造交付

日期：2026-11-07。分支 `refactor/v2-mybatis-plus`；独立 worktree：`D:/biancheng/RunRunCampus-worktrees/rrc-mybatis-plus`。本轮持久化改造与验证已完成，建议进入独立 QA；独立 QA 尚未执行。不合并 main、不推送、不替换 m09/integration。

## 1. 基线、源码与阶段提交

参考工作区 `rrc-refactor-backend-sky` 起始干净，HEAD `3ff3cfeea48890f03177e29fd997c6dfbe3b5b69` 直接继承 `0dadf2ee5c30c3634ef5154639a31d6baf9427d4`。核对祖先关系及 origin 后，以前者创建本 worktree 和分支。保留最新业务子包、Service 接口/Impl 配对及 AGENTS 中文编码约束。未覆盖其他工作区或未提交工作。

最终源码提交：`6b2ff0c1e126e26210fa738290ed129f377928b4`。其后只更新交付文档；最终交付提交为本报告所在提交，可运行 `git log -1 --format=%H -- docs/reports/V2-mybatis-plus-migration.md` 定位。最终交付与该源码的后端 Git tree 相同，复验记录见附件 verification.json。

|提交|阶段与验证|
|---|---|
|`a9a8e7c`|基线、官方依赖选型和完整 JDBC 盘点，先完成基线验证|
|`16acdd8`|隔离工厂、配置查询和区域 BaseMapper/Wrapper；订单 15 项重跑通过，legacy MFA 22 项及架构 7 项通过|
|`0cca9a5`|通知、outbox XML 和显式 JSONB 构造映射；通知基础测试 1 项、真实装配 5 项通过|
|`dce6d92`|资质、证据及 null BIGINT 审计绑定；相关数据库/服务回归 13 项通过|
|`ace9203`|身份、会话、幂等、启动校验及本地合成初始化；49 项通过|
|`170cc43`|订单创建、共享锁及不可变快照；订单/支付 40 项通过|
|`3150a3c`|支付、退款、模拟渠道、金额条件及结果锁；相关回归 58 项通过|
|`6bb8d73`|履约条件更新和游标投影；显式映射修正后 31 项通过|
|`6b2ff0c`|收尾映射、资质 BaseMapper、void/null 行为、并发与架构门禁；专项 6 项及最终完整验证通过|
|本报告所在提交|维护/启动文档、逐方法对应及交付证据|

启动/初始化 Mapper 与身份阶段一同迁移，是为了统一账号模型和直接实例化测试 fixture，避免并存两条持久化路径；没有改变初始化门禁、执行顺序或事务。

已阅读 AGENTS、阶段一业务基线、实现契约、订单履约、支付退款、账号、本地验证、决策记录、后端维护指南、最新业务子包及 Mapper 返工报告、实际 pom/v2 配置/启动入口/固定迁移清单。2026-10-27 全新系统决定继续覆盖历史兼容描述。苍穹外卖仅作层次与中文风格只读参考，未采用其密码算法、默认密码、日志或业务实现。

## 2. 依赖、兼容性、许可证与影响

固定使用 `com.baomidou:mybatis-plus-spring-boot3-starter:3.5.12`。当前 Java 17（本机 Temurin 17.0.18）、Spring Boot 3.5.16、PostgreSQL 16 和驱动 42.7.12 均未升级。没有添加第二个 MyBatis starter，也没有 Lombok、Redis、消息队列或其他组件。

官方依据：

- [MyBatis-Plus 安装说明](https://baomidou.com/getting-started/install/)明确 Boot 3 使用 `mybatis-plus-spring-boot3-starter`。
- [3.5.12 starter 发布 POM](https://repo.maven.apache.org/maven2/com/baomidou/mybatis-plus-spring-boot3-starter/3.5.12/mybatis-plus-spring-boot3-starter-3.5.12.pom)固定配套模块与 MyBatis-Spring 3.0.4；[core POM](https://repo.maven.apache.org/maven2/com/baomidou/mybatis-plus-core/3.5.12/mybatis-plus-core-3.5.12.pom)固定 MyBatis 3.5.19。
- [MyBatis-Spring 兼容表](https://mybatis.org/spring/)说明 3.0 系列适配 Spring 6.x、Java 17+ 和 MyBatis 3.5+；项目仍由 Boot 3.5.16 管理 Spring/JDBC，不导入 starter 发布时的旧 Boot BOM。
- [官方分页说明](https://baomidou.com/plugins/pagination/)说明自 3.5.9 起 PaginationInnerInterceptor 需要匹配的独立 `mybatis-plus-jsqlparser`。本轮保留原 count、LIMIT/OFFSET 和游标 SQL，不使用分页插件，故没有引入无用途解析器。

|直接及重要新增传递依赖|版本|用途|许可证|
|---|---|---|---|
|mybatis-plus-spring-boot3-starter|3.5.12|官方 Boot 3 依赖入口|Apache-2.0|
|mybatis-plus、core、annotation、spring、extension|均 3.5.12|BaseMapper、实体映射、Spring 工厂及会话支持|Apache-2.0|
|org.mybatis:mybatis|3.5.19|Mapper XML、参数及构造映射|Apache-2.0|
|org.mybatis:mybatis-spring|3.0.4|Spring 事务连接、Mapper 代理及异常翻译|Apache-2.0|

[实际依赖树](v2-mybatis-plus/dependency-tree.txt)和[相对基线的依赖差量](v2-mybatis-plus/dependency-delta.json)显示只增加上述 8 个 artifact，没有移除或改变其他已解析依赖版本。Spring JDBC/Hikari 原已存在，继续由项目父版本管理。

排除 starter 的 `mybatis-plus-spring-boot-autoconfigure`，只在 v2 config 显式创建官方 MybatisSqlSessionFactoryBean 和 SqlSessionTemplate。全局自动配置会令 legacy 创建不需要的工厂并可能加载 v2 XML；本实现无需修改 legacy 主类或生产配置即可隔离。没有新增数据源、连接池或事务管理器。

## 3. 修改文件与逐方法对应

完整路径见[源码阶段文件差异](v2-mybatis-plus/changed-source-files.txt)和[最终文件清单](v2-mybatis-plus/changed-files.txt)。主要修改为 v2 Mapper 接口、16 个业务分包 XML、3 个实体、必要内部投影、v2 工厂/处理器、两处必要 Service 调用、测试/fixture、SQL 核对工具、pom 和维护文档。

[原 JDBC → 新 Mapper/XML 对应表](v2-mybatis-plus/migration-map.md)覆盖 16 个原持久化类的 199 个 JDBC 执行入口；[冻结机器清单](v2-mybatis-plus/persistence-inventory.json)保存每项原 SQL/受控分支、原参数顺序与可空性、原 RowMapper、返回值、调用方及事务声明，并附最终映射和对应测试。两个私有 helper 的固定表名/SET 分支明确展开，没有暴露客户端 SQL。

v2 主源码不再存在 JdbcTemplate、SqlParameterValue 或手工取连接的数据访问路径。原 JDBC Mapper 实现已替换，没有两条可同时运行的持久化路径。测试中的 JDBC 保留为独立数据库事实检查；直接构造旧 Mapper 的数据库测试改为 V2MapperFixture，单元测试直接 mock Mapper，保留原断言目的。

## 4. BaseMapper、Wrapper 和 XML 边界

- BaseMapper：账号及首次资质创建，保留数据库默认值与 identity 回填；区域单表读取。3 个实体分别位于 entity/identity、entity/courier、entity/configuration。
- LambdaQueryWrapper：区域启用条件、代码匹配和 id 升序。列引用是服务端实体方法，不接受客户端列名、表名、排序或 SQL。
- XML：行锁、咨询锁、版本/金额条件更新、upsert、JSONB、审计/事件/outbox、数据库函数、聚合及原分页。其余多表业务写入保留明确列和现有默认值/数据库函数语义，不强套 IService/ServiceImpl。
- OrderStore 保留事务与 expectedVersion 检查、资源解析和 404，只委托 OrderReadMapper/XML 读取。定位事件内部投影再附加调用方已锁定订单版本，避免误将响应版本当成定位表字段。

SQL 核对工具对最终源码执行 404 次 SQL 展开，比较含字符串、`||`、`::` 等运算符的 token 与参数数量/顺序，全部通过。5 个 BaseMapper/Wrapper/委托入口由项目真实数据库用例及订单/身份回归覆盖；缺失其他 XML 或未知动态表达式会使工具失败。保留原 SELECT 内容与受控条件；履约响应固定流程版本取自原表固定为 2 的 CHECK 列，展示状态仍读取原 status。

## 5. 类型、主键、null 与分页

|语义|实现及验证|
|---|---|
|schema/identity|实体明确 runrun_v2、表名与 IdType.AUTO；XML 全限定表名。账号/资质用 PostgreSQL generated keys，其他原 INSERT RETURNING 继续保留；没有雪花 ID、search_path、DDL 自动更新|
|BIGINT/金额|原 Long/long 不收窄；项目数据库测试验证超出 int 的 ID 和 4,294,967,297 分快照|
|JSONB|写参数仍显式 `::jsonb`，审计 jsonb_build_object 的可空 BIGINT 显式绑定；订单快照/outbox 通过 XML 指定处理器并使用原应用 JSON 配置；不注册为 Map/Object 默认处理器，结构与快照不变|
|TIMESTAMPTZ|record 使用 OffsetDateTime 处理器；非整点时区 +05:30 和 .123456 微秒实测保持 instant/nano。Map 保留 JDBC Timestamp，业务时区、Clock 和原数据库时间函数不变|
|null 插入/更新|BaseMapper 创建只写原已赋值列，其他列使用原数据库默认值。必须清空的负责人、确认窗口、照片引用及退款时间仍显式 XML SET/绑定，不全局修改 updateStrategy|
|枚举/状态|保留原状态字符串；ServiceType 使用显式枚举名称映射，无新值/状态转换|
|查询行数|原 queryForObject/queryForMap 标记 SingleRowRequired；零行 EmptyResultDataAccessException、多行 IncorrectResultSizeDataAccessException，单行值可 null。列表无记录为空列表；OrderStore 原 404 不变|
|Map|继续使用 LinkedCaseInsensitiveMap、原别名和驱动类型；callSettersOnNulls 与 returnInstanceForEmptyRow 保留 null 键/行；已验证可空负责人、经纬度和 Timestamp/Long|
|函数 void|原咨询锁 RowMapper 返回 null，专用 VoidFunctionTypeHandler 保留列表中的 null，其他函数返回/副作用按原方式处理|
|分页|保留独立 count、稳定排序、LIMIT/OFFSET 及 (created_at,id) 游标。status/cursor 条件使用 XML if 与绑定参数，作用域固定；不改总数/顺序/边界|

## 6. 事务、锁、幂等及一致性证据

[声明核对附件](v2-mybatis-plus/transaction-boundaries.txt)记录基线与最终 **59 个事务/调度方法声明完全一致**，包括 REQUIRED/MANDATORY、readOnly、原隔离/回滚配置及调度条件。Mapper 不声明事务，不手工 commit、不取独立连接；SqlSessionTemplate 复用原 Spring DataSource/事务连接。数据库测试在 JDBC 事务内写 Mapper、由 JDBC 读到未提交事实，回滚后 Mapper 查不到，验证连接与事务参与一致。

订单创建与 OriginalPaymentWriter 的 MANDATORY 原款写入未拆开；身份手机号/微信咨询锁与票据/账号行锁顺序、幂等组件操作者锁后读取幂等行的顺序、履约账号容量锁/订单锁、完成与调整/售后、退款余额及版本条件均沿用原调用顺序。只改 IdentityServiceImpl 同位置的普通资质创建委托和证据审计的 JDBC 专用参数封装，未移动事务边界或业务判断。

所有 FOR UPDATE/SHARE/KEY SHARE/SKIP LOCKED、pg_advisory_xact_lock、WHERE 条件及 version 递增经 SQL 对照；原触发器与函数完全未改。幂等成功快照、订单/支付原子写入和业务 outbox 原子入队由原事务及数据库触发器继续保证。通知 worker 源码未改，claim/complete 仍为各自短事务，微信网络调用在其间事务之外。

工厂禁用二级缓存，使用 STATEMENT 级本地缓存，防止锁或同事务可变投影被缓存改变原 JDBC 语义。有写副作用的函数查询和 INSERT RETURNING 使用 affectData、刷新语义；没有乐观锁插件、@Version、逻辑删除或全局自动填充。

数据库回归继续覆盖幂等重放/冲突、并发关单支付、退款上限、容量与版本竞争、调整/确认互斥、资质审核及证据保全。新增 outbox 测试覆盖两个并发 claim 无重叠、有效租约不可再领、过期租约回收、失败退避及下一次重试、JSONB payload 与最终 SENT。

## 7. 新旧应用隔离与变化范围

V2PersistenceConfiguration 仅在原 v2 ComponentScan 内装配；MapperScan 限定 `com.zhangxuan.runrunv2.mapper` 且只注册 @Mapper 接口。classpath mapper/v2 扫描和 namespace 由工厂及架构测试实际解析核对。legacy classpath 中没有 MyBatis-Plus 自动配置类，不创建 v2 Mapper/SqlSessionFactory；完整 legacy JPA 回归及启动继续通过。

两个启动类与 application 资源零修改；v2 原 JPA/Flyway 排除保持，不启动 public Flyway、不扫描 legacy Entity/Repository。test 和 local-v2-test 分别通过隔离 runner 和 Compose 启动。Map/JSONB 处理器仅存在于 v2 工厂，legacy 未注册。

相对基线：数据库迁移及顺序、V1–V43、legacy 主业务源码、管理端、小程序、Compose/Dockerfile/部署脚本、生产/application 配置均为零差异；API DTO/VO、Controller、业务规则和数据库结构零变化。运行新增项只有本轮依赖和 v2 工厂/Mapper/XML；没有新环境变量、数据源或事务管理器。

人工抽查订单、身份和通知完整 Controller → 接口 → Impl → Mapper 链；中文职责注释、业务子包、显式 import 和四空格保持。XML 按列、条件、排序和锁排版；没有 Object 业务参数、数字后缀方法、客户端 SQL 拼接或敏感参数日志。

## 8. 验证命令、退出码和数量

以下均为实际运行，测试集合有交叠，不相加为去重数量。日志在仓库外 `D:/biancheng/rrc-mybatis-evidence/`；附件 verification.json 保存命令、结果和 SHA-256，不提交原始运行日志、env 或制品。

|基线验证（3ff3cfe）|退出码/结果|
|---|---|
|后端 `mvn -B -ntp -DskipTests test-compile`|0；518 主源码、78 测试源码|
|后端 `mvn -B -ntp clean test`|0；500 tests，0 failures/errors/skipped|
|根目录 `pwsh -NoProfile -File tools/run-v2-isolated-tests.ps1`|0；128 tests，0 failures/errors/skipped|
|干净基线 clone Compose `up --build -d`|0；PostgreSQL 16、固定迁移、后端启动|
|基线 `verify-v2-environment.ps1`|0；41 条 PASS，含管理员/session/通知/三种资质列表|

|最终源码验证（6b2ff0c）|退出码/结果|
|---|---|
|后端 `mvn -B -ntp -DskipTests test-compile`|0；最终含 533 主源码、81 测试源码|
|后端 `mvn -B -ntp clean test`|0；**506 tests，0 failures/errors/skipped**，含全部 legacy 与 v2|
|后端 `mvn -B -ntp -Pv2-identity -DskipTests package`|0；仅制品构建，不替代完整测试|
|后端 `tools/run-v2-isolated-tests.ps1`|0；**134 tests，0 failures/errors/skipped**，真实 PostgreSQL 16|
|根目录 `tools/verify-v2-local-migrations.ps1`|0；固定 12 条链、重复拒绝、public 隔离、非空回滚保护、备份恢复及逆序回滚，30 条 PASS|
|干净 clone `docker compose --env-file .env.v2-test -f compose.v2-test.yml up --build -d`|0；从源码构建，migrate 正常退出、postgres/backend healthy|
|clone `pwsh -NoProfile -File tools/verify-v2-environment.ps1`|0；**41 条 PASS**：管理员登录/session/通知/三种资质列表/权限/登出撤销/环境及旧 public 哨兵|
|host `jar tf ...jar` 与容器实际 `/app/app.jar` 检查|0；各含 **16 个 Mapper XML**，见 jar-xml.txt|
|后端 `node tools/verify-v2-persistence-sql.cjs`|0；404 次 SQL 展开，5 个 BaseMapper/Wrapper 入口另有数据库验证|
|V2PersistenceArchitectureTest + MybatisPersistenceDatabaseTest|0；新增 6 项，全部通过；包含最终完整/隔离测试|
|事务/调度 AST 对照|0；59 个方法声明完全一致|
|`git diff 3ff3cfe --check`、`git diff --check`、受保护路径差异与 v2 JDBC 扫描|0；无空白错误，受保护路径零修改，v2 main 无计划迁移 JDBC 残留|

基线 clone：`D:/biancheng/rrc-mybatis-evidence/baseline-source`，Compose 项目 `rrc-mp-baseline-20261107`。最终源码 clone：`D:/biancheng/rrc-mybatis-evidence/final-source`，项目 `rrc-mp-final-20261107`；没有复制 host target 参与 Docker 构建。两轮凭据均由原脚本生成到 Git 忽略的 env；验证后 down 保留隔离卷，不清空其他环境。文档提交 4356b3e 已在同一干净 clone 核对后端 tree 与 6b2ff0c 一致（d21074d89600ecdf2d727292450fb29133e879cb），Compose 重建退出码 0，环境复验再次 41 条 PASS、退出码 0。其后仅补记交付证据，后端 tree 不变。

探索性失败均保留在外部日志，没有算作通过：配置阶段遗漏一个 Wrapper 方法（修复后订单 15 项通过）；履约首轮 29 个构造映射错误（显式映射后 31 项通过）；新增测试首次有三个 accessor 编译错误，以及首次数据库测试揭示咨询锁 void 对象/null 不等价（专用处理器后 3 项通过）。最终完整回归全部通过，不删测试、不跳过、不降低断言。

既有 Surefire “System.exit 后等待 30 秒并终止 fork JVM”告警以及上下文关闭后后台连接告警仍出现，最终 BUILD SUCCESS、退出码 0；基线同样存在。未通过关闭业务任务或修改等待参数隐藏。

## 9. 未运行项与风险

未运行：管理端/小程序构建、真实微信身份/订阅/支付外部联调、预发布/生产部署、独立 QA。本轮均不在持久化改造验收范围；本地模拟验证不能称为真实支付或生产验收。Docker/网络未造成最终阻塞，冷构建的 Maven 依赖下载较慢但已完成。

已知风险：既有 Surefire/关闭后连接告警仍需另行治理；后续升级 MyBatis-Plus 时必须核对手动工厂、starter 排除项、XML 和处理器边界；后续修改可空字段不能误用 BaseMapper 默认忽略 null 的更新。当前项目映射和并发语义已通过数据库及隔离验证，仍须独立 QA 判断是否验收，不据此合并或替换集成分支。

## 10. 回滚与交付结论

按表中提交从后向前在独立回滚分支执行 git revert，整轮回滚应逆序撤销全部源码阶段；不要停在只撤销依赖/工厂、却保留新 Mapper 的中间状态发布。文档提交可先撤销。仅需复看原实现可另建 detached worktree 指向 3ff3cfe，不切换用户现有工作区、不 reset、不改写历史。

本轮没有数据库结构变化，回滚应用源码不运行任何数据库 down 脚本，不删除订单、流水、证据或迁移。维护规则文档修改前，已按 AGENTS 在忽略目录 `work/backups/RunRunCampus/20261107-170748/` 保存可恢复副本及原路径/SHA-256 清单。

建议进入独立 QA，重点复验映射边界、支付/履约竞态、幂等/outbox、旧应用隔离和最终 clean clone 启动。交付分支保持干净，无密钥、缓存或构建制品进入提交；不合并 main、不推送，独立 QA 通过后再由用户决定是否替换 m09/integration。
