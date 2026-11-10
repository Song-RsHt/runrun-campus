# 测试 JVM 退出告警修复与复验

日期：2026-11-10，北京时间。状态：**开发复验通过，待独立 QA**；未推送、合并或部署。

## 版本和依据

- 分支/worktree：`fix/v2-client-integration-entry`，`D:/biancheng/RunRunCampus-worktrees/rrc-fix-v2-client-entry`。
- 本轮起点：`2cb3786b75bc40504a65bbc16768518485e818bd`，开工工作区干净；包含客户端功能代码 `074c1dd2ec03f6201d1594d30693062c89f9b2ec`。
- **修复固定提交：`5c5e1e05d0c6021554a0befce2f688a2e4dd8889`**。后续文档提交不改测试或业务代码。
- 用户提供的独立定位：`D:/biancheng/rrc-entry-qa-retry-evidence/JVM退出告警独立定位记录.md`；WSL/Docker修复记录同目录。实际读取定位报告、代表类源码及8份线程栈中的关闭调用链；不操作该QA检出或保留卷。
- 基线独立完整回归511项通过但30秒强杀重现，8份栈显示主线程等待Spring退出钩子；退出钩子依次等待多个Hikari池，数据库已停止导致连接重试。不是通过Maven退出0认定已解决。

## 根因、改动与边界

13个legacy集成测试使用类级PostgreSQLContainer。Testcontainers在类结束时停库，而Spring测试上下文仍在缓存，连接池和调度器继续持有已失效数据库；最终JVM退出集中关闭多个上下文，累计超过Surefire原有期限。

新增测试辅助基类 `src/test/java/com/zhangxuan/runruncampus/support/PostgresSpringContextTest.java`，13个测试类继承它。基类的静态JUnit `@AfterAll` 使用对应测试类的 `TestContextManager`，调用 `markApplicationContextDirty(EXHAUSTIVE)`，从实际Spring上下文缓存移除并关闭上下文。这样连接池、JPA、Web容器和调度资源按Spring原有销毁流程在每类结束时释放，不留下缓存中的已关闭上下文。

用户级 `@AfterAll` 在扩展的 `afterAll` 回调之前执行，因此不依赖SpringExtension与TestcontainersExtension之间的注册顺序；没有仅凭AFTER_CLASS注解推断关闭顺序。本轮用日志和Docker实际事件验证池关闭先于数据库停止。

涉及13类（均在后端 `src/test/java/com/zhangxuan/runruncampus/` 下）：

- `controller/admin/`：AdminCommandCenterPostgresIntegrationTest、AdminListPostgresIntegrationTest、AdminPaginationPostgresIntegrationTest。
- `controller/auth/`：AdminMfaPostgresIntegrationTest、AuthPasswordPostgresIntegrationTest。
- `controller/credit/`：CreditPostgresIntegrationTest；`controller/order/`：OrderConcurrencyPostgresIntegrationTest、OrderMinePostgresIntegrationTest；`controller/privacy/`：PrivacyPostgresIntegrationTest。
- `message/`：PushDeliveryPostgresIntegrationTest；`service/admin/`：AdminCouponPostgresIntegrationTest；`service/courier/`：CourierCertificationPostgresIntegrationTest；`service/order/`：OrderChatPostgresIntegrationTest。

现有测试仅增加import/继承关系和必要import排序，业务断言未修改。主源码、API、数据库对象、V1–V43迁移、依赖、Surefire超时、业务调度和graceful shutdown配置均无修改。不删除或跳过测试，不关闭日志安全断言，不延长退出期限。测试仍使用既有合成数据与Testcontainers临时PG16，不连接真实数据库。

## 实际验证

BACKEND为上述worktree的 `handover-2026-10-24/RunRunCampusBackend`。证据目录为 `D:/biancheng/rrc-jvm-lifecycle-evidence`，记录的是开发侧复验，不替代独立QA。

|执行|结果|退出码/证据|
|---|---|---|
|BACKEND `mvn -B -ntp -Dtest=OrderChatPostgresIntegrationTest,OrderConcurrencyPostgresIntegrationTest test`|24项，失败/错误/跳过均0；代表有Web和无Web两类上下文。46.691秒|0；targeted.log|
|BACKEND `mvn -B -ntp clean test`|**511项，失败/错误/跳过均0**；总耗时2分43秒，18:31:38结束；fork自然退出|0；full-tests.log、full-tests-exit.txt|
|`node EVIDENCE/verify-shutdown-order.cjs`|13类逐一关联测试区段、数据库容器ID、主测试线程的池关闭日志及Docker die事件；全部先关池后停库，18/18个启动的池均关闭|0；shutdown-order.log、shutdown-order.json、full-container-events-corrected.log|
|完整日志检查|`kill self fork JVM`、`Connection refused`、`connection-adder`均0次；没有把退出0当作唯一依据|上述脚本强断言|
|结束后 `jcmd -l`、`docker ps`|只有本次jcmd自身；无残留Surefire或运行中的测试容器|0；工具执行记录|
|验证前后 `docker volume ls` 比对|验证前已有卷缺失数0；未重置WSL/Docker，未启动保留业务卷或删除已有卷|0；volumes-before/after.txt|
|`git diff --check`、文件范围检查|仅14个测试源文件及本轮文档；主源码和业务客户端无差异，无凭据/缓存/构建产物提交|0|

关闭顺序断言检查实际日志和数据库容器停止事件，而非仅检查辅助类存在或重复实现。Docker事件记录器首次使用旧字段 `.ID` 失败，改为Docker29的 `.Actor.ID`，从本次第一条Spring时间戳补取事件并继续记录；13个目标容器事件全部找到且时间顺序断言通过，首次错误日志保留，没有将缺事件当成通过。

本次没有重复浏览器、微信工具或Compose部署验证：修复只影响测试结束阶段，完整511项已覆盖修改的13类以及v2原有回归；客户端及运行代码与074c1dd保持一致。原入口浏览器/微信证据见 [客户端退回修复报告](v2-client-entry-qa-rework.md)。订单全闭环、真实微信/真机等此前未验收范围不因此升级。

## 交付与回滚

状态由“已独立复现、待开发修复”更新为“**开发已修复，完整回归和实际关闭顺序验证通过，待独立QA复验**”。独立QA应固定5c5e1e0或其纯文档后继，重跑完整套件并检查退出阶段；不要求其信任专项通过即可放行。

回滚可单独撤销5c5e1e0测试提交，或在干净的新本地分支固定回2cb3786；会恢复原测试生命周期告警。无需改环境文件、身份密钥、数据库或卷，无down SQL、不删除卷。未修改主目录脏工作区，未推送、合并、申请发布或部署；停止在待独立QA。
