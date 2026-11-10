# v2 联调入口：独立 QA 退回修复记录

日期：2026-11-10。状态：**待独立 QA**。

> 后续测试生命周期修复已固定到 `5c5e1e05d0c6021554a0befce2f688a2e4dd8889`：13类先关闭缓存上下文再停库，完整511项无强杀和连接拒绝，开发复验通过、待独立QA；详见 [生命周期专项报告](JVM-test-context-lifecycle-fix.md)。下列原始结果及“未定位”风险保留为当时记录，当前风险状态以上述专项为准。上轮 `f4f4d9109d55c57d22c18683f6b3e57c0326a73c` 已被独立 QA 判定不通过；本报告记录开发复现及修复，不代替独立验收，不申请发布、不推送、不合并、不部署。

## 固定版本与环境边界

- 分支：`fix/v2-client-integration-entry`。
- worktree：`D:/biancheng/RunRunCampus-worktrees/rrc-fix-v2-client-entry`；开工 HEAD 为 f4f4d91，工作区干净。
- 原始开发基线：`bb1d9d0d1df68c9f994209cf6e26708a8efd1324`；甲方记录中的版本：`2836dee1658596f031b7af6c166299eb84057029`。
- 通知修复提交：`f514e8ae640c92885c8b14f9bfc52b51a3e1b964`。
- **修复后固定代码提交：`074c1dd2ec03f6201d1594d30693062c89f9b2ec`**；后续交付文档提交不改变代码树。
- 独立 QA 原报告：`D:/biancheng/rrc-entry-qa-evidence/独立QA报告-v2客户端联调入口.md`，仅阅读，未修改 QA 检出、卷或证据。
- 新证据目录 EVIDENCE：`D:/biancheng/rrc-entry-rework-evidence`；包含本轮脚本、日志、合成截图及独立 clean-clone。不是需提交的业务制品，秘密 env 留在本机，不能外发整个目录。
- 专用 Compose 项目：`rrc-entry-rework-20261110`、`rrc-entry-rework-clean-20261110`，均新建独立 PG16、合成账号和本机密钥。API 为 `http://127.0.0.1:8082`，管理端精确 Origin 为 `http://127.0.0.1:5186`。数据库不发布宿主端口，仅模拟支付。
- 部署附件本轮可读取；其原始缺陷和测试记录只作核验线索。当前机器仍无甲方 `D:/RunRunCampusTest/RunRunCampus`，不声称现场更新或验收。主目录 feature/m01-identity 脏工作区未修改。

## 复现、根因与修复

|问题|开发复现及根因|修复与证据|
|---|---|---|
|QA-01 / P1 非空通知白屏|f4f4d91 新隔离 Compose 插入本人合成通知；真实 Edge 正文为空，pageerror 为 `Invalid time value`。Map 返回下划线列名和 PGobject，客户端读取 camelCase 及普通 JSON，缺失日期导致渲染异常。复现脚本退出1，baseline-notifications.log/png 保留。|NotificationVO 明确六字段，XML 局部构造映射使用既有 JSONB handler 和 OffsetDateTime；原 SQL、账号过滤、已读归属和事务不变。管理端在 HTTP 边界校验日期和通知字段，异常进入可重试错误态。真实非空通知、已读、刷新及四种异常响应由浏览器验证。|
|QA-02 / P2 同意回执未校验|新增 Page 源码测试在旧代码上因 undefined 回执仍进入首页而退出1；独立 QA 的三类成功状态异常响应证据也核对。|捕获勾选版本；仅 accepted 严格为 true 且版本严格匹配才进入首页。失败沿用登出、清会话及错误提示，不推断同意事实。八种异常回执及异步版本替换由 Node 覆盖，三类回执由实际微信 Page + 本地 HTTP 注入覆盖。|
|QA-03 / P2 协议元数据不完整|旧代码只检查 version/title/content 的真值；独立复现器确认缺三个元数据仍通过。|六字段均要求非空字符串，生效日期校验实际日历值；加载失败清协议和勾选。每字段缺失/null/空/空白/数值/对象/数组及非法日期共覆盖，微信实际验证三个缺字段响应。批准协议正文、版本及后端隐私实现未改。|
|QA-04 / P3 文档不一致|现行指南误写双默认 Origin，维护计数仍是47组/16 XML。|更正默认单 Origin、可选端口和对应精确 CORS；现行计数48组/17 XML；旧报告明确历史属性。规则文档修改前已在 work/backups 保存原文件、绝对路径和 SHA256 清单。|

## 文件、API 与数据库对象

以下路径相对于实际检出根目录：

- 后端 `handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/`：新增 `vo/notification/NotificationVO.java`；修改 `controller/notification/NotificationController.java`、`service/notification/NotificationService.java`、`service/impl/notification/NotificationServiceImpl.java`、`mapper/notification/NotificationMapper.java`。`config/OutboxPayloadTypeHandler.java` 仅修正用途注释，未改处理逻辑或注册。
- 后端 `src/main/resources/mapper/v2/notification/NotificationMapper.xml` 新增局部 resultMap，SQL token 与绑定顺序不变；`src/test/java/com/zhangxuan/runrunv2/MybatisPersistenceDatabaseTest.java` 增加真实数据库非空响应/时间/归属/重复已读用例。
- 管理端 `handover-2026-10-24/RunRunCampusAdmin/src/v2Api.ts` 增加运行时通知响应校验；小程序 `RunRunCampusMiniProgram/pages/login/login.js` 增加完整协议和回执校验。
- `tools/verify-v2-client-entry.cjs` 增加非空通知/已读/刷新/异常数据断言，每次生成唯一合成通知，支持重复执行；新增 `tools/verify-v2-mini-response-contract.cjs` 55项离线 Page 边界测试；`tools/verify-v2-environment.ps1` 增加可选 `-EnvFile`，默认仍是 `.env.v2-test`，避免验证独立配置时覆盖已有 env。
- 文档：本报告、首轮历史报告、甲方更新说明、甲方环境指南、本地开发与验证、后端分层维护指南、数据模型与接口契约。

API：GET `/api/v2/notifications` 仍返回本人最近100条数组，响应明确 `id,orderId,type,content,createdAt,readAt`。`content` 是普通 JSON 对象，时间为 ISO 字符串，orderId/readAt 可空。POST `/{id}/read` 行为不变。目标 A31 的游标分页仍未实现，本轮只修复既有入口字段。隐私两个 API 路径、六字段、批准版本和同意持久化不变，仅修复消费方校验。

数据库：**无新建、修改或删除对象，无 DDL、无迁移**。通知继续读/更新 `runrun_v2.user_notifications`，本人范围不变。隐私继续使用既有 `idempotency_requests` 的 PRIVACY_CONSENT scope。不修改 V1–V43，不导入旧数据，无直接 JDBC 主实现，无新依赖或全局 JSON/匿名放行变更。

人工追踪 Controller → Service 接口 → Impl → Mapper/XML：构造器注入、业务子包和原事务保持；VO 只承担响应数据，outbox claim/complete 不改。数据库测试 JDBC 仅作独立事实检查。

## 实际执行与结果

ROOT 是修复 worktree，BACKEND 为其后端目录；CLEAN 是 EVIDENCE/clean-clone（本地 clone 后 detached 到完整074c1dd，最终 git status 干净）。Maven 为本机3.9.16，Java17。集合有重叠，不能把表内数字累加为去重用例数。

|命令/范围|数量及结果|退出码/日志|
|---|---|---|
|旧代码 `node tools/verify-v2-mini-response-contract.cjs`|成功复现错误放行；预期回执拒绝断言失败|1；baseline-mini-contract.log|
|`node EVIDENCE/baseline-mini-negative.cjs`，通过 git show 读取完整f4f4d91源码|独立重现空/否定/错误版本回执和缺元数据共4类错误放行；不读取或操作QA库|0（复现器成功，不是功能通过）；baseline-mini-negative.log|
|旧代码 `node EVIDENCE/baseline-notifications.cjs`|真实浏览器成功复现通知白屏|1；baseline-notifications.log、nonempty-notifications.png|
|BACKEND `mvn -B -ntp -Dtest=MybatisPersistenceDatabaseTest,IdentityIntegrationTest,PrivacyPolicyContractTest,BackendLayeringTest,V2PersistenceArchitectureTest test`|39，失败/错误/跳过0；含登录/session JSON、匿名隐私保护、批准正文比对、通知实际Mapper和架构|0；targeted-tests.log|
|BACKEND `mvn -B -ntp clean test`|511，失败/错误/跳过0；**结束阶段存在连接拒绝及30秒JVM强退告警，未定位**|0；full-tests.log，不将退出0当成资源回收正常|
|BACKEND `mvn -B -ntp -Pv2-identity -DskipTests package`|打包成功；测试由上两行及隔离回归另行执行|0；package.log|
|ROOT `pwsh -NoProfile -File tools/run-v2-isolated-tests.ps1 -KeepDatabase`|139，失败/错误/跳过0；独立PG16固定初始化链|0；isolated.log|
|ROOT `pwsh -NoProfile -File tools/verify-v2-local-migrations.ps1`|30 PASS，含空库链/重复拒绝/隔离/回滚保护|0；migrations.log|
|ROOT 与 CLEAN `pwsh -NoProfile -File tools/build-v2-clients.ps1 -ApiBaseUrl http://127.0.0.1:8082`|冻结锁文件、TypeScript/Vite构建及小程序环境元数据；不把元数据等同微信编译|各0；clients.log、clean-clients.log|
|ROOT `verify-v2-environment.ps1 -EnvFile <本轮新配置> -ApiBaseUrl http://127.0.0.1:8082`；CLEAN 默认env 同命令|各45 PASS；客户端构建单独执行|各0；environment.log、clean-environment.log|
|ROOT 与 CLEAN `node tools/verify-v2-client-entry.cjs`，既有Playwright + Edge|各32 PASS：合成ADMIN、刷新、导航、非空通知、已读/持久刷新、四类畸形通知、资质列表、权限拒绝、停用、失效、退出；无未捕获错误及v1调用|各0；browser-fixed.log、clean-browser.log|
|ROOT 与 CLEAN `node tools/verify-v2-mini-entry.cjs`|各8 PASS，真实匿名协议、真实合成登录及当前版本同意、故障清理和v1断言|各0；mini-http.log、clean-mini-http.log|
|ROOT 与 CLEAN `node tools/verify-v2-mini-response-contract.cjs`|各55 PASS：8种异常回执、42个字段异常、3个非法日期、正确流程、异步版本替换|各0；mini-contract.log、clean-mini-contract.log|
|小程序 `node --check`、JSON解析、`rg -n '/api/v1'`；管理端 `pnpm ... check:v2-shell`|12个JS、11个JSON有效（含忽略的本地项目配置）；小程序v1匹配0，管理端允许历史api.ts外实际v1调用0|语法/解析/管理扫描0；rg无匹配1为预期|
|BACKEND `node tools/verify-v2-persistence-sql.cjs`|404次冻结SQL展开 token/参数数量/顺序一致|0|
|CLEAN 专用项目 `docker compose --env-file .env.v2-test -f compose.v2-test.yml up -d --build`|固定提交从干净clone构建并健康启动，未复制现有密钥|0；clean-compose.log|
|CLEAN `start-v2-admin.ps1 -Mode preview -Port 5186`|真实浏览器Origin5186；OPTIONS和带认证请求通过，未配置Origin403|实际运行成功；browser日志和截图|
|5186已占用，CLEAN 分别 `start-v2-admin.ps1 -Mode preview/dev -Port 5186`|均明确报端口占用，不切换|各1（预期）；port-occupied-preview/dev.log|
|`node EVIDENCE/preserve-rework.cjs`|4 PASS：无-v down/up --build 保留账号/身份/审计/同意/非空通知指纹、env字节与密钥、原会话、精确CORS|0；preserve.log|
|CLEAN 保留卷重建后再执行 `verify-v2-environment.ps1`|45 PASS，重建后继续通过既有门禁|0；after-rebuild-environment.log|
|微信官方skill `open_project_window`、`automation_evaluate`、DOM读取及 `simulator_screenshot`；`node EVIDENCE/wechat-skill-rework.cjs`|8 PASS，实际已编译Page、真实匿名协议/753字正文DOM、3个缺字段HTTP响应、3类异常同意HTTP回执；有截图|0；wechat-skill-rework.log、wechat-policy.png、wechat-incomplete-policy.png、wechat-invalid-consent.png|

浏览器截图 `admin-nonempty-notifications.png` 已人工查看：正文、时间、已读可见；两份微信异常截图已人工查看：协议失败提示、回执失败提示可见且保留登录页。全部合成数据，无凭据截图。

微信使用 `C:/Program Files/Tencent/微信开发者工具`（2.02.2609240）的已授权 Codex skill；status 仅提取 versionRelation=equal、loginExpired=false、无需CLI令牌，不打印个人资料。初始原生自动化请求因本项目缺少本地域名配置失败，补上忽略的本项目 urlCheck=false 后重开；原生auto9422重开等待未完成，改用官方skill明确打开本项目fullMode后执行成功。首次截图找不到窗口、socket失败和先前失败日志保留，不记通过。没有真实wx.login/手机号授权、上传或支付。

其他执行边界：首次新后端镜像尚未构建完成时提前运行 browser.log，因仍连接旧后端而字段断言失败；等待构建就绪后正式 browser-fixed.log 及 clean-browser.log 通过。没有删除断言或放宽安全测试。首次formatter因工作目录错误未处理文件，随后在实际ROOT重新运行成功；测试及固定提交均使用完成格式化的代码。

## 风险、未完成与回滚

- 完整511项执行再次出现数据库连接拒绝及 Surefire 退出等待强杀告警，原因仍未知；本轮39项及139项没有复现该强退。没有证据将它归为“已有且无害”，也未关闭调度或放宽断言掩盖。需要独立追踪测试资源生命周期。
- 本轮资质浏览器仅空列表和查询，独立上轮非空资质结果属于其历史证据。订单→支付→履约→售后→结算闭环、审批照片、真实微信身份/手机号、订阅消息、真机及真实支付未运行。微信同意异常用本地HTTP合成回执；真实后端当前返回正常回执，未声称服务端实际产生了畸形结果。
- 甲方实际电脑更新、保留数据与代码回滚未现场执行；本轮只在新隔离配置执行同机制重建。
- 更新说明见 [甲方现有环境更新与复验说明](../v2-甲方更新与复验说明.md)：准确原版及修复hash、待发布的指定ref步骤、保留.env/密钥/所有卷、精确端口/CORS、小程序API地址、复验预期及不删卷回滚。未发布前不能假定GitHub已有该提交。
- 回滚使用更新前记录的固定提交切新本地分支后重建，不重置脏工作区，不执行down -v或down SQL。回滚到f4f4d91会恢复本报告所述非空通知/协议校验缺陷；回到甲方原版本还会恢复最初权限缺失及隐私403。回滚不能当作客户端问题已解决。

开发侧已完成本轮三项阻断的修复与复验；**不改变独立 QA 的前次不通过结论，新固定提交仍待独立 QA，尚不申请发布测试分支，也不宣称全系统验收通过。**

## 收尾与审查

两套本轮Compose均不带-v执行down，预览仅停止核对命令行属于本轮路径的进程；微信仅关闭本项目s5窗口，并停止已核对命令行的本轮卡住auto9422 CLI，没有退出其他项目。自己的isolated容器 `rrc-v2-test-5094ec1e74b2` 已stop并保留。两套数据库/回执/证据命名卷、自己的忽略配置、clean-clone和本轮证据保留，未操作原开发/QA卷。

提交前检查 `git diff --check`、新增路径及本机随机密钥/密码值未出现在变更文件；没有env、密钥、照片、备份、缓存或构建产物进入交付。代码分两个提交，文档另一个提交；文档提交与固定代码提交排除docs后必须无差异。最终工作区应干净，完整交付文档hash由交付回复提供，避免文档自引用hash。
