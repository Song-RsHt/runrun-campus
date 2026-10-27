# M04 骑手任务与履约交付记录

日期：2026-10-27。实现分支：`feature/m04-fulfillment`。worktree：`D:/biancheng/RunRunCampus-worktrees/rrc-m04-fulfillment`。基线：已集成 M01/M02/M03 的 `8fb3a86`。本记录是模块实现交付，不是独立 QA、main 合并或端到端开放验收。

已读取 AGENTS、阶段一基线、订单与履约规则、数据模型与接口契约，并核对总控方案、迁移登记、本地验证要求。业务规则没有改变，不修改基线或决策；规则/登记修改前的可恢复副本与 SHA-256 清单位于本 worktree 的 `work/backups/RunRunCampus/20261027-184106/`，未提交备份。

## 实现与行为

- 普通已付款待接单订单可接受；同一骑手账号行锁串行化跨订单接单和邀请接受，每单 ACTIVE 部分唯一索引防双领。容量按 ACTIVE 实时计数，不维护易漂移计数器。OFFERED 不占容量；接受邀请实时检查资质与容量，拒绝邀请不要求资格。释放、取消、送达均原事务结束任务。
- 邀请为新任务。M05 审核后的重新履约调用 `invite`（MANDATORY 事务端口），仅允许 EXCEPTION 且无 ACTIVE 的订单；创建新邀请立即标记历史任务被替代，旧任务不复活。接受后新任务 ACTIVE、订单 ACCEPTED；存在实物交接时，M06 必须先完成双方确认，不能使用此端口跳过交接。
- PICKUP 每单必做，必须先有当前订单/任务/骑手的 AVAILABLE 取件凭证；没有强制规格核验前置。取件凭证已提交但未取件时禁止普通释放，交管理员核查。只有规格照片不妨碍正常释放。
- ARRIVAL/DELIVERY 事件分别保存，坐标严格 GCJ02/范围/至多七位小数，使用服务端时钟。ARRIVAL 对订单取件点快照计算 100 米采信；缺坐标或超距仍保存真实事件、accepted=false 并记录原因，不虚构地点、不允许客户端补过去时间。补贴的取消前30分钟/未取件/审核付款仍归 M06。
- `(task_id,event_type,request_id)` 定位去重；同 requestId 异坐标冲突。同任务最多一个 accepted DELIVERY，后续不同 requestId 位置保留为未采信，不能替换送达证据。
- 送达要求已取件，照片与定位都 AVAILABLE/accepted 且同单、同任务、同骑手。订单首次 delivered_at 不可覆盖；任务 DELIVERED、ended_at 与订单送达同事务提交，立即释放接单名额，不等待补款或确认。
- 无调整时送达开启完整十分钟；存在 M03 未结补款/退款或 M05 完成阻塞投影时窗口为空。用户确认与定时完成共同检查订单状态、真实送达、有效窗口、调整结清、无未决售后；自动完成另检查截止时刻。重新结清使用当前服务器时间重开完整十分钟，不改 delivered_at。
- 最新有效 DELIVERED 任务且订单 DELIVERED_PENDING_CONFIRMATION，仅允许 SPECIFICATION 上传/登记/调整授权；不恢复容量、取件/送达/定位。RELEASED/HANDED_OVER/CANCELLED、superseded 或终态/异常立即拒写。规格上传自身不阻塞完成，M05 登记调整时必须再次授权，并在同事务阻塞确认窗口。
- 业务写使用共享幂等服务、当前账号资格锁、订单锁、expectedVersion。重放不追加履约事实/版本；任务历史归属不授予新写权限。旧位置请求/上传票据不能用于新任务。通知 outbox 和履约事件与状态同事务，失败全部回滚。
- 用户订单详情增加 deliveredAt/confirmStartedAt/confirmDeadline；M04 返回 rawStatus/displayStatus，已取件映射 DELIVERING，送达按待补款/调整中/待确认投影。本人任务列表可找回邀请及已结束任务，无资格仍可只读；游标绑定身份/接口/状态筛选。接单池只查原款 PAID 且 PENDING_ASSIGNMENT。
- 全部新对象显式使用 runrun_v2，仅增加 /api/v2；不读取旧表、不生成或输入收货码、不改旧 V1–V43 或旧客户端。

## API、权限与接入端口

|入口|身份/保护|实现范围|
|---|---|---|
|GET /courier/order-pool|COURIER + 当前资格|仅已支付接单池，limit 1–100、默认20、签名游标|
|GET /courier/tasks|COURIER 本人|本人任务白名单，过滤/分页/allowedActions；资格撤销仍可读|
|POST /orders/{id}/accept|有资格 COURIER|订单版本、容量、并发接单|
|POST /tasks/{id}/respond|本人 COURIER|task expectedVersion；接受另查资格/容量|
|POST /orders/{id}/release|当前有资格 COURIER|taskId/reason/expectedVersion，无取件凭证才释放|
|POST /orders/{id}/uploads|当前或有限送达 COURIER|上传票据端口，SPECIFICATION 有限例外|
|POST /orders/{id}/proofs|同上|kind/taskId/uploadTicket，私有存储成功后登记|
|POST /orders/{id}/locations|当前有资格 COURIER|ARRIVAL/DELIVERY，requestId与坐标绑定|
|POST /orders/{id}/pickup|当前有资格 COURIER|taskId/pickupProofId|
|POST /orders/{id}/deliver|当前有资格 COURIER|taskId/deliveryProofId/locationEventId|
|POST /orders/{id}/confirm|订单 USER 本人|无未结调整/售后、有效窗口|

所有路径前缀 /api/v2；所有业务写要求 Idempotency-Key。订单更新使用 expectedVersion，邀请 respond 使用任务 version；任务列表返回订单和任务两种版本。M04 写失败沿用 E01，包括 ORDER_STATE_CONFLICT、COURIER_NOT_APPROVED、COURIER_CAPACITY_EXCEEDED、PROOF_NOT_READY、IDEMPOTENCY_KEY_CONFLICT、ADJUSTMENT_UNRESOLVED、AFTER_SALES_UNRESOLVED；不是新增错误体系。

M05 集成端口：`invite`、`authorizeSpecification`、`blockCompletion`、`unblockCompletion`、`restartWindow` 均要求已有业务事务；必须按 actor → 通知接收账号 → order 的锁顺序调用，不能先锁订单再反向锁账号。调整登记：先 authorizeSpecification，再在同一事务创建调整并 blockCompletion。售后登记同步 blockCompletion，M06 同事务冻结权益。结清在财务/审核/售后都已落库后 unblock/restart，不由前端倒计时决定。完成先成功则新调整拒绝；调整先成功则窗口失效。

## 数据库对象和文件

新增正向/回滚：`server/db/migrations/up/rrc_0300__fulfillment.sql`、`down/rrc_0300__fulfillment.rollback.sql`。顺序 0001→0002→0003→0100→0200→0300，按现有显式 psql 执行器运行。新增 tasks、proofs、location_events、completion_blocks、schema_manifest，实际列/约束/索引/触发器及后置同单任务 FK 详见 `M00-migration-registry.md` 的 M04 实际登记。无旧表回填，无旧迁移改写。

新增源码：FulfillmentModels、FulfillmentService、FulfillmentController、FulfillmentQueries、FulfillmentEvidence、FulfillmentCompletionJob；新增 FulfillmentIntegrationTest 与 verify-m04.ps1。修改共享 OrderModels/OrderStore 只添加三项履约时间投影；IdentityErrorHandler 只增加现有 E01 中文提示。其他修改限本报告、迁移登记和当前状态表。没有新增依赖。

## 已运行验证

后端工作目录 `handover-2026-10-24/RunRunCampusBackend`：

- `mvn -q -Dtest=FulfillmentIntegrationTest test`：最终29项，0失败/错误/跳过。使用 Testcontainers postgres:16-alpine，合成身份/订单，M07端口为受控测试适配器；含并发同单接单、同骑手跨单容量、同键并发取件/送达、定位requestId重试/异参/唯一有效送达、同单证据关联、释放/取消/交接/替代撤权、旧上传票据、邀请接受/拒绝、无资格只读、十分钟边界、补款释放容量、调整/售后重开窗口、用户/自动完成与调整竞争、释放与定位竞争、取消与取件竞争、失败回滚、HTTP响应、收货码字段拒绝和默认凭证端口拒绝。
- `mvn -q -Dtest=FulfillmentIntegrationTest,PaymentIntegrationTest,OrderIntegrationTest,IdentityIntegrationTest,PaymentEnvironmentTest,WechatHttpGatewayTest test`：联合回归92项通过（当时履约25项，其余67项）；此后只对 M04 增加定位采信、展示和测试，最终履约29项通过。共享订单时间字段、错误提示的回归已包含在这轮联合测试。
- 根目录 `pwsh -NoProfile -File server/db/tools/verify-m04.ps1`：12组通过。独立断网临时容器；空库初始化、重复迁移拒绝、后续附加索引回滚保护、空模块回滚、保留M03、含证据拒绝回滚、坐标约束、跨单关联拒绝、pg_dump/pg_restore 与结构指纹均通过。
- `git diff --cached --check`：通过。`mvn -q -Pv2-identity -DskipTests package`：成功，JAR 的 Start-Class 为 `com.zhangxuan.runrunv2.IdentityApplication`；打包跳过重复测试，不替代上述实际测试。

最初两次工具调用分别因项目根目录/POM位置不正确、验证脚本字符串嵌套出现本地命令失败；已纠正工作目录和脚本，最终上述验证入口全部实际成功，不作为业务测试失败掩盖。

## 尚未开放、配置和风险

- 根据总控迁移登记的分段门禁，M07 未验收时不能开放凭证及依赖凭证的取送闭环。FulfillmentEvidence 端口的默认 PendingFulfillmentEvidence 始终返回 PROOF_NOT_READY；测试替身不计作实际文件上传/私有存储/保全删除验收。M07 接入时替换该 bean，签发票据并校验订单/任务/操作者/种类/过期、真实图片类型大小、成功私有对象及一次性关联；不得使用客户端对象键或上传成功标志。证据读取、保留/保全/删除与真实存储联调未运行，本提交不实现公开图片地址。
- M05 的实际调整单、售后、管理员重新履约 HTTP 和 M06 交接双确认/补贴/权益未实现；本提交提供其必要事务端口与撤权校验，未创建重复业务表。A19 是有限规格调整授权端口，实际调整业务仍由 M05 实现；不提前暴露缺少处置前置的管理员入口。取件前释放已端到端测试，HANDED_OVER 拒写用合成交接状态测试，不冒充完整交接验收。
- `rrc.fulfillment.capacity` 为服务端配置（允许1–100），经用户确认默认上限为3单。多实例必须使用同一容量配置；测试覆盖容量竞争。
- `rrc.fulfillment.enabled=true` 才启动十分钟完成定时器；默认关闭，联调部署须先执行迁移、接入M07及M05阻塞事件后启用。默认扫描间隔15秒，业务截止仍精确为窗口起点+10分钟；可用 completion-delay-ms 配置扫描频率。
- 本人任务只提供阶段摘要，不提供其他骑手或用户私有资料。完整骑手订单历史详情、M07图片读取、管理端/小程序、真实微信、真机与生产验收未运行。
- 未进行独立 QA、未推送或合并 main。建议提交总控只读评审本模块状态与数据库能力；不得将此报告当作完整履约开放授权。
