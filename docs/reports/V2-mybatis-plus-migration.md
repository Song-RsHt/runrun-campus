# v2 MyBatis-Plus 持久化改造

日期：2026-11-07。分支 `refactor/v2-mybatis-plus`，独立 worktree `D:/biancheng/RunRunCampus-worktrees/rrc-mybatis-plus`。当前为实施记录，尚未完成，不能作为 QA 通过依据。不合并、不推送、不替换 m09/integration。

## 基线与范围

参考工作区干净，HEAD 为 `3ff3cfeea48890f03177e29fd997c6dfbe3b5b69`，直接父提交为 `0dadf2ee5c30c3634ef5154639a31d6baf9427d4`；实际核对祖先关系后创建本 worktree/分支。保留用户认可的业务子包、Service 接口/Impl 配对及中文编码约束。只读参考苍穹外卖 Mapper 的职责和排版，不采用其密码、默认值、自动填充或业务实现。

已阅读 AGENTS、阶段一业务基线、技术契约、订单履约、支付退款、账号、本地验证、决策、后端维护指南、业务子包报告及 Mapper 专项返工报告，以及实际 pom、v2 配置、入口、迁移清单。2026-10-27 全新系统决定覆盖旧文档中的历史兼容描述。

完整盘点见 [持久化清单](v2-mybatis-plus/persistence-inventory.md) 及其 JSON，覆盖 16 个类的 199 个 JDBC 执行入口。清单保存原 SQL 表达式、参数和顺序、行映射、调用方及事务声明；不是仅列类名。业务/API、数据库结构及固定迁移链均不在变更范围。

## 依赖决策

当前 JDK 为 Temurin 17.0.18，pom 固定 Java 17、Spring Boot 3.5.16、PostgreSQL 驱动 42.7.12；测试和 Compose 均为 PostgreSQL 16。上述版本不升级。

选择固定 `com.baomidou:mybatis-plus-spring-boot3-starter:3.5.12`，Apache License 2.0。官方 [安装说明](https://baomidou.com/getting-started/install/) 明确 Boot 3 使用该 starter，禁止另加重复 MyBatis starter。其 [发布 POM](https://repo.maven.apache.org/maven2/com/baomidou/mybatis-plus-spring-boot3-starter/3.5.12/mybatis-plus-spring-boot3-starter-3.5.12.pom) 使用 MyBatis-Spring 3.0.4、同版 MyBatis-Plus 核心和自动配置；[core POM](https://repo.maven.apache.org/maven2/com/baomidou/mybatis-plus-core/3.5.12/mybatis-plus-core-3.5.12.pom) 固定 MyBatis 3.5.19，均 Apache-2.0。[MyBatis-Spring 官方兼容表](https://mybatis.org/spring/) 明确 3.0 对应 MyBatis 3.5+、Spring 6.x 和 Java 17+；本项目继续由 Boot 3.5.16 管理 Spring/JDBC 版本，不导入 starter POM 中的旧 Boot BOM 覆盖项目父版本。

为保持 legacy 完全不加载 v2 XML/Mapper，计划排除 starter 的 `mybatis-plus-spring-boot-autoconfigure`，仅在 v2 config 中使用官方 `MybatisSqlSessionFactoryBean`、限定 MapperScan 和现有 DataSource/Spring 事务。无需新增事务管理器，也不更改 legacy 启动源码或生产配置。替代方案是应用全局自动配置，但会让 legacy 创建不需要的 MyBatis 工厂并可能扫描 v2 XML，因此不采用。

现有分页采用显式 count + LIMIT/OFFSET 或 (created_at,id) 游标，保持其顺序、总数和边界。没有使用 IPage/PaginationInnerInterceptor，故不添加解析器。[官方分页说明](https://baomidou.com/plugins/pagination/) 指出自 3.5.9 起插件需要单独的匹配版 `mybatis-plus-jsqlparser`；若后续确需该插件应重新说明，不将解析器作为本轮无用途依赖引入。

## 基线验证

日志保留在仓库外 `D:/biancheng/rrc-mybatis-evidence/`，不提交缓存、凭据和制品。

|命令|结果|
|---|---|
|后端 `mvn -B -ntp -DskipTests test-compile`|0；518 主源码、78 测试源码|
|后端 `mvn -B -ntp clean test`|0；500 tests，0 failures/errors/skipped|
|根目录 `pwsh -NoProfile -File tools/run-v2-isolated-tests.ps1`|运行中，结果后续填写|
|基线干净 clone Compose `up --build -d`|0；隔离 PostgreSQL 16、固定迁移、后端启动|
|基线 clone `verify-v2-environment.ps1`|0；管理员登录、session、通知、三种资质列表等门禁通过；未运行客户端构建|

基线 clone 为 `D:/biancheng/rrc-mybatis-evidence/baseline-source`，项目 `rrc-mp-baseline-20261107`；执行后 down 保留卷，未影响其他项目。完整回归中的既有 Surefire 退出告警另行保留，不修改任务或退出策略隐藏。

## 实施与验收状态

下一阶段：配置及简单查询；随后通知、资质证据、身份幂等、订单、支付退款、履约、启动及整体回归。每阶段核对原 SQL、运行受影响数据库测试并提交，不将本基线结果冒充最终改造通过。最终提交号、实际依赖树、改动文件、逐方法对应、映射语义、锁和隔离证据、最终测试/未运行项、风险与回滚方式将在实施后补全。

当前尚不建议进入独立 QA：持久化迁移尚未完成。
