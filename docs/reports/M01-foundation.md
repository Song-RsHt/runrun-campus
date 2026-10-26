# M01 数据库迁移与公共基础交付记录

日期：2026-10-26（Asia/Shanghai）。状态：已实现明确契约部分；迁移草稿已在隔离 PostgreSQL 验证；公共契约缺口待总控补充。**当前不是完整 M01 验收通过或共享环境可部署声明。**

## Git、范围及执行门禁

- 总控指定并经本 worktree 核验的基线：`a175f8b5337bb8491bfefe151704230c4bd2d98d`。
- ROOT：`D:/biancheng/RunRunCampus-worktrees/m01-migrations`；分支：`chore/m01-migrations`。开始时干净，origin 为 `https://github.com/Song-RsHt/runrun-campus.git`。主工作区和其他模块检出未修改。
- 按 ROOT 读取 G、B、C、D、V、M、A。实现层来源为 C M44–M47、A01、E01，未采用五角色草案；没有修改规则文件。
- 本模块拥有新增迁移、公共基础及对应测试。不实现 A02 清单中的业务控制器，不提供业务成功占位接口。测试中的控制器和合成事件仅存在于 test 源集。
- 运行迁移目录为后端 `src/main/resources/db/migration`。交接 `01-数据库迁移脚本` 保留镜像副本。两处 V1–V43 共43对文件 SHA-256 一致；本次 V1–V43 均未修改。V44–V47 开工时在本基线两处目录均未占用。本次四份新脚本同步镜像副本。
- M00 留有隔离库 V1–V43 的执行证据，但没有可信共享环境执行历史。**V42/V43 的生产及其他需维护共享环境状态仍未核实**；不能由文件存在、本地测试或本报告推断已执行。共享部署前由授权运维提供环境标识、版本、成功状态、安装时间、校验和以及备份/恢复演练记录，并再次核对 V44–V47 占用。这里未连接生产或执行生产迁移。

## 已实现内容与契约映射

| 契约 | 本次实现 | 限制 |
|---|---|---|
| M44-01–04/13/14 | 微信身份唯一约束、区域及基础配置增量、服务点、独立 v2_orders；管理员默认关闭和安全版本、审计 metadata/request_id | 不兑换微信身份，不创建运营坐标，不实施管理员授予控制器；结构化配置字段命名需总控收录 |
| M44-05–12 | 任务、交接、证据、定位、售后、事件、订单/资质恰一主体的保全和删除审计 | 清理执行器、对象存储、争议判定及完整状态机由后续模块接入 |
| M44-15/16 | 共享旧资质增量、稳定照片资源标识初始化和维护、版本、资质事件表 | 不复制资质、不改变旧资格；旧服务的完整保全/清理和事件接入尚需身份/证据模块完成 |
| M45-01–07 | 调整、支付单/尝试、退款、欠款/事件、幂等记录；原款和各调整补款各自唯一 | 资金模拟标记和部分状态/审计白名单缺少技术定义，未擅自补充 |
| M46-01–06 | 独立账本、分配/明细、冻结/事件、MOCK逐单付款登记；平台 NULL 主体去重、账本/事件/付款只追加 | 不实施分配总额计算、退款余额、资金释放及冻结付款业务；其校验不得仅依赖这些表 |
| M47-01/02 | 站内通知、持久 outbox、发送去重及重试索引 | M47-03 的模板/授权结构和审计定义待补；不声称微信发送已接入 |
| A01/E01 | 37项错误码、v2限定的错误处理、金额/ID/时间工具、归属和版本入口、锁计划、事务幂等和审计 | 业务模块须实际调用；未新增 v2 业务端点，不将旧服务接入自动视为符合契约 |

数据库约束包括非负金额、债务余额界限、服务/状态枚举（C明确部分）、同单复合外键、同单唯一 ACTIVE 任务及未结调整、保全主体和明细引用约束、定位删除前后坐标形态。所有新外键均有前导列匹配索引，隔离数据库系统目录测试核对。骑手分配明细在不增添 C 字段的前提下，通过触发器核对所属订单和任务骑手。

订单流程版本 CHECK=2 且更新触发器禁止变化；公共 version 从0开始，数据库触发器对一次更新递增一次，同时接受 ORM 的显式 old+1、拒绝跳号。旧资质 ORM 增加 @Version，使旧入口的更新同样使已读版本失效。照片资源标识与内部对象键成对维护；从本轮待审照片转为已批准照片时保留同一资源标识，替换照片才换标识，不把存储键作为资源 ID 返回。

## 公共入口与接入方式

代码位于后端 `com.zhangxuan.runruncampus.v2.foundation`，不改变旧包结构或全局序列化规则。

1. v2 控制器标注 `@V2Api`；抛出 `V2Exception(V2Error, 安全提示)`。错误响应强制 application/json，提供 code/message；请求解析/校验和上传错误有固定码。旧 v1 的 message-only 错误约定保留。**不能把旧 ResponseStatusException 原样透传到 v2 并声称完成 E01**；业务模块应在边界转换为准确的 C 错误码。E01 缺少非预期500、限流等公共码，此缺口交总控，未凭空定义。
2. DTO 的 BIGINT 标识标注 `@ApiId`，金额和并发版本保留 JSON 整数；时间字段使用 Instant + `@ApiTime`，或 `V2Time.format` 输出带 Z 的 ISO-8601。可空字段保持 null。禁止直接序列化实体。
3. 金额输入使用 `@IntegerCents`（必填字段配合 Bean Validation）或 `Cents.parse`；拒绝小数、字符串、负数和越界。`Cents.add` 检查溢出，`Cents.split` 用整数商/余数实现30/70及骑手吸收舍入差，不在大金额上先乘30导致溢出。
4. `ResourceAccess` 的 actorId 必须取自已验证的后端会话。`ownedOrder` / `ownedCertification` 按库中归属查询，未授权与不存在均404；`relatedTask` 核对骑手和订单关联，仅证明读取关联，**不授予履约写权限**。管理员每次请求用 `enabledAdministrator` 重读当前资格及安全版本，资格管理身份和适用 MFA 仍由身份模块验证。
5. `ResourceChecks.order` 必须先做归属，再做流程版本和 expectedVersion。更新的版本读取/检查必须在同一个写事务和聚合锁内；冲突返回 ORDER_STATE_CONFLICT，不能先写副作用再检查。幂等重放不得再次执行版本检查或增版。
6. 使用 `IdempotentWrites.execute` 包裹一笔已认证业务写入。传入 actorId、后端确定的路由作用域、Idempotency-Key 和绑定内容；**boundContent 必须包含 URL中的资源类型/流程版本/ID、用途、调整ID、查询条件及规范化业务字段，不能只传 request body**。不用于尚无 actorId 的微信认证入口，也不伪造系统账号使用此服务。
7. 幂等入口先取得 actor/scope/key 的事务 advisory lock（包括数据库尚无记录时），再取得完整聚合锁计划，之后执行 authorize。重放仍需当前授权；同键异参409。业务回调中执行 expectedVersion、状态/余额/权限校验，写业务、事件、审计及适用 outbox；只返回白名单 DTO 快照。记录在同一事务末尾写入，异常全部回滚，不能先另开事务提交幂等成功。只存安全结果，不保存密码、Token、code、私有地址、存储键、照片字节等；上传重放还需业务模块重新验证当前资源可读条件。
8. `AuditFacts.order` / `certification` / `administrator` 拒绝无写事务使用。用户/骑手/管理员用实际操作者ID；系统用 SYSTEM、空 actorId、明确 systemSource。受控部署身份仍映射真实 ADMIN 供 admin_audit_logs 外键使用，不借用其他管理员。审核/上传等回调须同事务写事件；管理员操作另写管理员审计。删除结果走专用 evidence_deletion_audits，不复制隐私正文到事件或普通日志。

## 锁和事务约定

一笔请求先规划全部锁，统一按 `CONFIG → ACCOUNT → CERTIFICATION → ORDER → PAYMENT → SETTLEMENT`，同类按 ID 升序获取；幂等锁在这份计划之前。`AggregateLocks.acquire` 排序、取得带类型命名空间的事务 advisory lock 及数据库行锁，并拒绝同一事务中后续倒序补锁。O含 ORDER，P含 ORDER+PAYMENT，S含 ORDER+SETTLEMENT，I含账号/资质及唯一约束，C含配置；如果接单需要骑手容量/账号锁，应提前纳入计划，不能持有订单锁后再补账号锁。

调用幂等入口前不得先取业务聚合锁；一次业务请求只调用一次幂等入口，不在一个事务里串联多个不同键。事务内不得发起微信/对象存储/付款网络操作。事务外发送通知，事务内持久化 outbox；本模块只提供表，业务模块仍须实际写入并实现发送器。

资质不存在时先锁账号并靠 courier_id 唯一约束防重。现有 v1 资质写入已有资质行锁，本次增加了共享增版；**v1/v2所有入口仍须由身份模块统一为相同完整锁顺序及共享服务**，尤其现有照片物理删除/替换/账号删除任务不能继续绕过保全和删除审计。这里只交付迁移与公共基础，不能单独开启 v2 保全功能并让旧删除路径继续运行。

证据物理删除跨事务时，不能误用 transaction advisory lock 假装保持到对象存储调用结束：证据模块需持专用数据库连接的 session advisory lock，并采用同一个 `v2:ORDER:<id>` 或 `v2:CERTIFICATION:<id>` 文本经 PostgreSQL `hashtextextended(key,0)` 计算键；它与本模块的 xact advisory lock 冲突。取得执行锁后，事务复查/标记 DELETING，执行删除，再事务登记结果，最后释放 session lock。保全/读取/换图等入口也须采用同一排他协议；连接归还池前释放，不用另一连接解锁。失败保留可重试引用，禁止先记成功。

付款登记、售后冻结、调整与完成竞争、支付成功转待接单、退款累计及分账守恒均是后续模块的业务事务责任。这里没有跨表 CHECK 冒充上述控制，也没有默认成功的业务实现。

## 待总控补充的技术缺口（阻止共享部署/完整验收）

以下已在本任务集中请求协调，未修改 C 或创建第二套公共定义：

- C 未给 payment_attempts、idempotency_requests、notification_outbox 的完整状态集合。当前相应 status 依 C 保持 TEXT NOT NULL；尝试“进行中/未知”索引用 PROCESSING/UNKNOWN，幂等服务仅在成功提交时保存内部 COMPLETED。状态集合和对应约束需在总控定稿后核对，不能将未定义状态视为集成通过。
- M44–M47 除管理员启停和资质动作外，未列其余新 action / target_type 的精确值。V44 已扩明确的两项白名单并保留全部旧值，Java枚举同步。**V45–V47 尚未扩未定义的业务审计白名单，相关业务不得部署写入。** 证据保全/删除、交接/重新履约也需精确动作定义。
- C 要求支付/退款/还款标记 MOCK，但未给相应字段名、归属表和允许值；仅付款登记给了 mode=MOCK。未擅自给其他表增加通道字段。
- M47-03 未定义通知模板映射和订阅授权表/列，当前只实现 M47-01/02，不能声称 M47-03 或微信通知已接入。
- C 对结构化配置给了语义而未给全部精确列名。本次脚本的 business_start/business_end/business_timezone、review_owner_id、v1_new_order_enabled/v2_new_order_enabled 等是待总控收录的内部草稿命名；数值配置名按 M 配置表并由 C M44-13 引用。审核负责人是否启用、停用前更换接收人、营业规则等业务校验需配置/身份模块接入；未编造默认负责人。
- E01 未定义非预期异常500及限流429等通用码。当前只处理已定义的业务/解析/校验/上传分支，未将基础设施错误伪装成400或默认成功。

这些脚本尚未在任何共享环境由本任务执行；总控补充 C 后，若确仍未在任何需维护共享环境执行，可在此 PR 内完善新脚本并重跑测试。若已经执行，必须协调新的增量版本，不能原地修改。

## 验证记录

在本 worktree 的 work/ 中保存日志，未提交缓存、构建产物、工具或合成数据库。仅使用 POM/锁文件已有依赖；未增加项目依赖。Java17、Maven3.9.16、Docker29.8.0、Testcontainers PostgreSQL16。数据库全由 Testcontainers 创建清理，没有使用生产库或真实用户数据。

已执行：

- Git rev-parse/status/log/remote/worktree 核对；只读 ls-remote 确认 main 为指定基线。单命令 safe.directory 处理仓库所有者不同，不改全局信任配置。
- 两目录43对旧迁移 SHA-256比较一致；git diff确认旧SQL无改动。四份新脚本镜像同步并核对。
- 初次 Maven PowerShell 参数未加引号，导致 `.repo.local=D` 被误识别为插件，未进入测试；修正引用方式。新增金额反序列化器初次编译有受检异常错误，已修复。
- 定向测试首轮发现合成数据字段长度/旧收货码缺失、最新版本断言、一个定位外键索引缺失及错误响应XML协商问题；修复后19项定向测试全部通过。
- 随后补4项数据库用例，第一次全量 test 共384项：381通过、3失败、0错误、0跳过。两项为旧测试最新版本写死43，已改47；一项因独立worktree未构建管理端 dist。未删除或跳过这些测试。
- 管理端 `pnpm install --frozen-lockfile --store-dir ../../work/pnpm-store` 和 `pnpm build` 成功；仅为已有后端打包测试准备源码构建的静态资源，没有修改管理端源码和锁文件。
- 完整 `clean package` 退出0、BUILD SUCCESS：64个测试类、384项，Failures=0、Errors=0、Skipped=0；包含原有361项与新增23项（18项PostgreSQL、5项单元/MVC）。没有 skipTests。日志仍有M00已有的容器关闭后连接池校验告警、Windows Manifest-JAR 跨盘提示、System.exit(0)后30秒未退出而Surefire终止fork的提示；断言均通过且Maven最终成功，但生命周期清理问题没有在本模块修复。
- 提交前另加强管理员旧账号停用/删除检查、幂等未完成记录拒绝重放，以及分配父记录变更的同单校验；最终定向测试23项全部通过，0失败、0错误、0跳过，2026-10-26 15:48（Asia/Shanghai）完成。全量384项结果对应加强前的完整构建，最终改动使用相关23项验证，不冒称又跑过一次全量。

从ROOT复现（PowerShell中必须引用带点号的-D参数，避免被拆分）：

```powershell
# 先按现有锁文件构建管理端静态资源，供ProductionFoundationTest核对
Push-Location handover-2026-10-24/RunRunCampusAdmin
pnpm install --frozen-lockfile --store-dir ../../work/pnpm-store
pnpm build
Pop-Location

# 令taskRoot为本检出的git rev-parse --show-toplevel结果，缓存和临时目录仅供本地使用
$taskRoot = git rev-parse --show-toplevel
New-Item -ItemType Directory -Force "$taskRoot/work/maven-repository", "$taskRoot/work/java-tmp" | Out-Null
mvn "-Dmaven.repo.local=$taskRoot/work/maven-repository" "-DargLine=-Djava.io.tmpdir=$taskRoot/work/java-tmp" -f handover-2026-10-24/RunRunCampusBackend/pom.xml clean package
mvn "-Dmaven.repo.local=$taskRoot/work/maven-repository" "-DargLine=-Djava.io.tmpdir=$taskRoot/work/java-tmp" '-Dtest=FoundationTest,FoundationPostgresIntegrationTest' -f handover-2026-10-24/RunRunCampusBackend/pom.xml test
```

首次构建需要访问Maven仓库和postgres:16-alpine镜像；本次复用已有依赖缓存的只读内容复制到本worktree/work中，未添加依赖或把缓存提交为运行前提。新克隆可由上述命令按POM/锁文件获取已有依赖。

新增验证：空库V1–V47顺序及Flyway validate；实际交接V41→V42/V43→V47、V43→V47（使用原SQL和合成历史账号、人工/现金/微信订单、成功支付、在途退款、已批准资质/照片）；原金额/状态/资格/照片引用和旧迁移校验和守恒；旧单仍能更新状态收尾；历史订单不进入新表。另验约束/索引/非法数据、同单引用、平台NULL去重、账本只追加、共享版本和照片资源、并发幂等、同键异参、授权重放、审计失败回滚、金额溢出及API序列化。

未运行：真实共享库升级/备份恢复、生产迁移、真实支付、微信身份/订阅/对象存储联调、跨模块v2集成、管理端业务浏览器QA、开发者工具和真机。合成历史升级测试不等于恢复了真实共享库；旧业务回归通过也不等于尚缺的v2业务集成通过。

GitHub认证只在进程内使用已有Git凭据，不输出或保存。实测 workflows API返回200且total_count=0；main接口protected=false；保护详情和rulesets接口403。没有修改CI/保护规则，不能假设存在自动验收。PR交总控审查，开发agent不合并。
