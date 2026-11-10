# v2 联调入口修复验证报告

日期：2026-11-10（基线复现与首轮回归始于 2026-11-09）。最终状态：**待独立 QA**，未推送、未合并、未部署甲方环境。

## 固定版本与边界

- 分支：`fix/v2-client-integration-entry`
- worktree：`D:/biancheng/RunRunCampus-worktrees/rrc-fix-v2-client-entry`
- 甲方记录中的部署版本：`2836dee1658596f031b7af6c166299eb84057029`
- 开发基线：`bb1d9d0d1df68c9f994209cf6e26708a8efd1324`，Git 祖先检查确认包含甲方版本；原交付 worktree 干净。
- 第一批（权限和隐私契约）：`34e8eb512c4aa5acb5883da2663e03652b4db61b`
- 第二批（本地端口、CORS、复验脚本）：**`f74fe0ccc85161eb86de96cca7b2b32f38bb2b69`**，本轮功能修复固定提交，干净 clone 的检出版本。
- 后续报告提交只包含交付文档；不改变上述固定代码。甲方更新说明固定拉取该功能提交，当前待发布。

主目录 `feature/m01-identity` 为原有脏工作区，未写入、未覆盖其他 worktree。干净验证目录为 `D:/biancheng/rrc-entry-evidence/clean-clone`；从唯一仓库的本地对象库 clone 后按完整 hash detached checkout，不是从 GitHub 拉取已发布版本。

部署记录附件可读取，已作为待核验现象来源；其中建议未作为额外操作授权。甲方实际路径 `D:/RunRunCampusTest/RunRunCampus` 在当前开发机不存在，无法现场核验其当前 Git/env/卷状态；更新前必须由甲方核对。

仅使用本机 PostgreSQL 16、合成账号、模拟支付。未连接生产、导入真实业务数据、修改 V1–V43、重建既有身份密钥或删除 Compose 命名数据卷。完整 legacy 回归也只在 Testcontainers 新建的合成数据库运行，不接触旧业务库。一次性测试容器按仓库原有 runner/Testcontainers 清理。

## 根因、修复与 API

| 问题 | 基线证据 | 修复结果 |
|---|---|---|
| 管理端白屏 | 登录200、session200，account 都只有6个字段，无 permissions；真实 Edge 抛出 includes 异常，body 长度0 | IdentityAccountVO.permissions 显式 @JsonProperty；只有 ACTIVE 且启用 ADMIN 返回原有派生权限，其他为空数组。登录和 session 同模型；客户端运行时校验，恢复后渲染，异常响应/失效会话清理权限并回到登录，不补授权限 |
| 小程序隐私403 | 匿名 GET v2 privacy/policy 实际403，无v2 Handler | 新增独立 v2 Controller → Service → Impl；GET 显式匿名只读，其他接口仍默认保护。沿用批准正文，契约测试逐字段/逐字比对来源 |
| 同意流程后续阻断 | 小程序 finishLogin 还会调用不存在的 v2 consent | 新增受保护 consent，校验当前版本并锁定认证账号保存回执；小程序必须勾选、成功保存版本后才进首页，失败撤销/清理客户端会话 |
| 5173冲突 | 部署记录指出被无关Docker占用；当前开发机未冒充具有同一冲突 | dev/preview 端口显式可配、默认5173、strictPort；使用5186做真实联调，故意再次启动同端口验证退出1。精确 Origin 配置，不使用通配符或 IPv6 绕行 |

API 清单：

| API | 变化及状态 |
|---|---|
| POST `/api/v2/auth/password/login` | 现有account增补稳定JSON permissions数组；合成ADMIN200 |
| GET `/api/v2/auth/session` | 与登录account一致；有效200、匿名/撤销/到期401 |
| GET `/api/v2/privacy/policy` | 新增；匿名200，字段version/title/effectiveDate/operatorName/contact/content；版本2026-10-24 |
| POST `/api/v2/privacy/consent` | 新增；有效Bearer+当前version为200 `{accepted:true,version}`；旧版本400，匿名/撤销401 |
| 邻近v2接口 | 继续认证/角色保护；ADMIN访问骑手专属查询403、管理员资格管理PATCH403；未声明路由403 |

数据库对象清单：**新增/修改表、列、索引、函数、迁移均为0**。仅新增对既有 `runrun_v2.idempotency_requests` 的 `PRIVACY_CONSENT` 成功回执写入，按 actor_id+版本唯一去重，保留版本及首次创建时间，不读取 public 旧业务表。既有44张v2表和12份迁移回执保持不变。新 Mapper 使用项目现有 MyBatis-Plus 工厂和 XML，不引入 JDBC 回退或新依赖。

人工检查调用链：PrivacyController → PrivacyService → PrivacyServiceImpl → PrivacyMapper；Controller只绑定HTTP，Impl持有事务，经 IdentityService.authorizeForWrite 持账号锁并复核当前会话，再写成功回执。新增业务接口/实现和Mapper工厂清单同步更新为48/17，既有分层、安全、SQL和数据库断言继续执行。

## 实际执行命令及结果

下表路径约定：ROOT 为上述修复worktree或明确标注的干净clone；BACKEND 为 ROOT/handover-2026-10-24/RunRunCampusBackend；EVIDENCE 为 `D:/biancheng/rrc-entry-evidence`。重复运行不累加成独立用例数量，所有通过的测试均无跳过。

| 命令/检查 | 数量与结果 | 退出码 |
|---|---|---:|
| 基线clone checkout bb1d9d0…，`docker compose --env-file <本轮合成env> -f compose.v2-test.yml up -d --build`，项目rrc-entry-baseline | 健康启动；基线登录/session/协议和真实浏览器复现 | 0 |
| 基线 `tools/verify-v2-environment.ps1` | 41条PASS；证明旧门禁通过仍会漏掉白屏和协议403 | 0 |
| BACKEND `mvn -B -ntp clean test`（修复最终Java树） | **510 tests，0 failures/errors/skipped**；含IdentityIntegrationTest23项、PrivacyPolicyContractTest2项 | 0 |
| BACKEND `mvn -B -ntp -Pv2-identity -DskipTests package` | v2主类制品打包成功；测试已单独运行 | 0 |
| ROOT `pwsh -NoProfile -File tools/run-v2-isolated-tests.ps1` | **138项v2测试**，真实PostgreSQL16，0失败/错误/跳过；固定链及public哨兵隔离 | 0 |
| `node handover-2026-10-24/RunRunCampusBackend/tools/verify-v2-persistence-sql.cjs` | **404次**SQL展开token/参数数量/顺序一致；新增协议SQL另由真实数据库用例覆盖 | 0 |
| `pwsh -NoProfile -File tools/verify-v2-local-migrations.ps1` | **30条PASS**；固定12份迁移、重复拒绝、旧库哨兵、回滚保护、dump/恢复和空库回滚 | 0 |
| `pwsh -NoProfile -File tools/build-v2-clients.ps1 -ApiBaseUrl http://127.0.0.1:8082` | 管理端TypeScript/Vite构建；小程序local元数据；worktree及固定提交clone均通过 | 0 |
| `pnpm --dir handover-2026-10-24/RunRunCampusAdmin check:v2-shell` | 无v1调用 | 0 |
| 小程序逐文件 `node --check`、JSON.parse及v1扫描（静态脚本保存在EVIDENCE） | **12 JS、10 JSON**，v1命中0 | 0 |
| `pwsh -NoProfile -File tools/start-v2-admin.ps1 -Mode preview -Port 5186`，故意重复启动 | 明确Port5186占用，不切换端口；原预览仍运行 | **1，预期拒绝** |
| `RRC_ADMIN_PORT=0` 下 `pnpm preview --host 127.0.0.1` | 明确配置非法，不启动 | **1，预期拒绝** |
| `COMPOSE_PROJECT_NAME=rrc-entry-clean`、`RRC_LOCAL_ADMIN_ORIGIN=http://127.0.0.1:5186` 下干净clone `docker compose … up -d --build` | 固定提交实际Docker构建、PG16/后端健康，迁移Exit0，8082仅绑定127.0.0.1 | 0 |
| 干净clone `pwsh -NoProfile -File tools/verify-v2-environment.ps1 -ApiBaseUrl http://127.0.0.1:8082` | **45条PASS**，含权限JSON、匿名协议、OPTIONS、认证/角色/模拟支付/列表/退出边界 | 0 |
| `node tools/verify-v2-client-entry.cjs`，既有Playwright+Edge、5186 Origin | **25条PASS**，worktree和干净clone分别通过；详见下节 | 0 |
| `node tools/verify-v2-mini-entry.cjs` | **8条PASS**：Node Page harness+真实HTTP，非微信编译替代物 | 0 |
| 保留卷 `docker compose … down` → `up -d --build` | env文件哈希和账号身份指纹前后一致；原密钥未变化，未重新初始化 | 0 |
| 重启后再次 `verify-v2-environment.ps1` | **45条PASS**，原管理员和初始化审计各一份 | 0 |
| `git diff --check`、暂存范围/敏感文件扫描、固定代码树比对 | 见最终检查记录；不提交env、target、dist、照片、缓存、Token或原始敏感日志 | 0 |

干净clone固定提交的完整回归/打包结果在本报告末尾最终检查记录补充。这里的 `-DskipTests` 仅用于已独立完成回归后的打包与Docker构建，不作为测试通过证据。

## 真实浏览器证据

运行环境为已安装的Edge，Playwright来自本机既有运行时；没有新增工程依赖。API为127.0.0.1:8082，浏览器Origin为127.0.0.1:5186。脚本严格限定rrc-entry-*专用Compose项目及合成test-admin，会改变该测试账号资格和会话，不能对甲方保留数据环境运行。

25条检查包括：匿名政策和保护接口、精确Origin预检及错误Origin403、合成ADMIN真实登录、session权限数组、首页刷新、导航通知、资质默认/PENDING/搜索、退出后前端清理及服务端401、实际停用合成账号、实际令会话到期、无未捕获JS异常/上述includes错误、无v1请求。

另外，明确通过响应故障注入覆盖“缺少登录权限”“恢复响应缺权限”“权限为空隐藏资质页面”；这些是客户端安全行为测试，不冒充后端授权测试。后端ADMIN角色拒绝及禁止资格提权使用真实API，USER/COURIER空权限另在真实登录/session契约测试覆盖。

本轮列表使用合成空库，截图显示真实空态；没有伪造骑手照片或真实通知。证据（保留在仓库外）：

- `EVIDENCE/baseline-result.txt`：原始6字段、policy403、includes异常及body长度0。
- `EVIDENCE/baseline-blank.png`：原版白屏。
- `EVIDENCE/clean-browser.log`：25条PASS。
- `EVIDENCE/admin-home.png`、`EVIDENCE/admin-certifications.png`：固定提交真实页面，只有合成数据。

## 微信开发者工具实际验证

发现D盘旧工具副本的CLI报缺Electron，备用CLI写旧profile目录失败；未修改这些旧工具。随后通过注册表找到有效安装：`C:/Program Files/Tencent/微信开发者工具`，版本2.02.2609240。

实际执行官方 `cli.bat open --project <干净clone小程序路径>` 和 `cli.bat auto --project <同一路径> --auto-port 9420 --trust-project`，均Exit0。用户已在工具桌面确认Codex授权，版本门禁一致、登录未过期；未调用真实wx.login/getPhoneNumber或上传/支付。

工具自带wechatide-skill/automator/compiler/debugger用于理解官方接口。其MCP刷新/截图入口曾返回terminated、operation aborted或project window not found；某些CLI进程Exit0但业务JSON为ok:false，均按失败记录，未冒充通过。没有反复盲目刷新或全局放行接口。

后续使用同一官方自动化端口的原生RPC完成实际运行验证，脚本为 `EVIDENCE/wechat-real-validation.cjs`，结果为 `EVIDENCE/wechat-real-validation-result.txt`：

1. 在该项目工具本地存储设local、127.0.0.1:8082、模拟支付。仅在干净clone被忽略的project.private.config.json中关闭本机request域名校验，不改公共配置。先前url not in domain list属于工具本地设置，配置后恢复。
2. 调用实际登录页自己的loadPrivacyPolicy，真实wx.request `/api/v2/privacy/policy` 返回200。
3. 当前路由pages/login/login，协议版本2026-10-24、正文753字符，页面error为空；展开后实际生成1个.privacy-content节点，读取其innerText含协议正文且长度753。
4. 用本机临时HTTP空响应夹具进行明确的故障注入，实际页面执行新增的“协议响应不完整，请重试”分支、清空协议并保持未勾选；恢复真实API后重新加载正文成功。这一行为验证了最终新增源码在开发者工具中实际编译运行，不能仅以CLI open成功代替。

**结论：固定源码登录页实际编译运行、匿名协议请求和内容展示/错误恢复通过；微信截图取证通道仍不可用，未取得微信PNG截图。** Node harness、元数据和静态检查与上述真实工具证据分别列示。未上传、生成体验版、使用真实身份或真机。

## 失败、修正及环境限制

| 类别 | 现象和处理 |
|---|---|
| 已有问题（有基线实证） | bb1d9d0的缺permissions白屏、v2匿名政策403；本轮已修复 |
| 本次实现问题，已修正 | 初轮CORS校验转义导致编译失败；修正后完整510和打包通过 |
| 本次测试维护问题，已修正 | 新业务接口加入后架构清单仍断言47（实际48）；核对调用链后更新。早期完整508项有这1项失败，最终未放宽业务断言 |
| 本次测试脚本问题，已修正 | 首次浏览器脚本查找“通知”标题，实际为“站内消息”；核对真实页面后修正选择器，重跑25项通过 |
| 本地Git环境，已处理 | 本地clone遇对象库属主检查，使用仅当前子进程的精确safe.directory；随后按完整hash检出。没有改全局Git信任或原脏工作区 |
| 工具环境限制 | 旧微信工具副本无效；有效工具MCP通道异常。通过官方原生自动化端口完成真实页面验证，但无微信截图 |
| 敏感日志处置 | 状态工具额外返回工具登录用户资料，曾被误原样输出；已从本地状态日志移除loginUser，未进入提交或交付材料。后续状态/页面日志只记录脱敏字段，不含凭据 |
| 甲方环境不可现场访问 | 当前开发机不存在甲方仓库路径，无法声称核验了其当前HEAD、env和卷；更新说明要求甲方自行检查 |

没有将本次引入失败称为“已有问题”，没有删除测试、静默跳过或放宽安全断言。

## 已实现与未实现

已实现本轮A/B/C及直接相关同意回执衔接。入口具备继续受控本地基础业务联调条件：管理端首页/刷新/通知/资质列表可用，小程序协议前置不再被403阻断。

仍未验收：订单、支付、接单、照片、送达、售后、结算完整闭环；真实微信登录和手机号授权；真机、订阅消息、体验版或上传；真实支付和生产。管理端照片/审批/通知已读等mutation没有在本轮浏览器空库执行，不把后台接口/历史回归等同于这些UI闭环通过。

已批准协议正文未修改；今后如需法律内容修订、回执存储/清理策略变化，应独立评审，不在本轮顺手扩展。

## 更新、回滚及最终状态

甲方操作见 `docs/v2-甲方更新与复验说明.md`，包含原版及固定完整hash、待发布说明、原env/密钥/卷保留、精确CORS、客户端配置、状态预期和不删卷回滚。

本轮无DDL，回滚为切回原固定提交重新构建，保留env和卷，不运行down SQL；旧版会恢复已知入口缺陷和原端口限制。没有GitHub推送、main合并、远程集成分支修改、甲方部署或公网部署。最终停止在**待独立QA**，不宣称全系统验收通过。

## 修改文件清单

相对仓库根目录，固定功能提交共28个文件；本报告及甲方说明为后续文档提交。
```text
.env.v2-test.example
RunRunCampusMiniProgram/README.md
RunRunCampusMiniProgram/pages/login/login.js
RunRunCampusMiniProgram/pages/login/login.wxml
compose.v2-test.yml
docs/数据模型与接口契约.md
docs/本地开发与验证.md
handover-2026-10-24/RunRunCampusAdmin/README.md
handover-2026-10-24/RunRunCampusAdmin/src/App.tsx
handover-2026-10-24/RunRunCampusAdmin/src/v2Api.ts
handover-2026-10-24/RunRunCampusAdmin/vite.config.ts
handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/config/LocalTestCors.java
handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/controller/privacy/PrivacyController.java
handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/dto/privacy/PrivacyConsentRequest.java
handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/mapper/privacy/PrivacyMapper.java
handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/service/impl/privacy/PrivacyServiceImpl.java
handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/service/privacy/PrivacyService.java
handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/vo/identity/IdentityAccountVO.java
handover-2026-10-24/RunRunCampusBackend/src/main/java/com/zhangxuan/runrunv2/vo/privacy/PrivacyPolicyVO.java
handover-2026-10-24/RunRunCampusBackend/src/main/resources/mapper/v2/privacy/PrivacyMapper.xml
handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runrunv2/architecture/BackendLayeringTest.java
handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runrunv2/architecture/V2PersistenceArchitectureTest.java
handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runrunv2/controller/identity/IdentityIntegrationTest.java
handover-2026-10-24/RunRunCampusBackend/src/test/java/com/zhangxuan/runrunv2/controller/privacy/PrivacyPolicyContractTest.java
tools/start-v2-admin.ps1
tools/verify-v2-client-entry.cjs
tools/verify-v2-environment.ps1
tools/verify-v2-mini-entry.cjs
```

## 最终检查记录（2026-11-10）

- 干净clone明确检出 `f74fe0ccc85161eb86de96cca7b2b32f38bb2b69` 后再次执行完整 `mvn -B -ntp clean test`：510 tests，0 failures/errors/skipped，BUILD SUCCESS，退出0；`mvn -B -ntp -Pv2-identity -DskipTests package` 再次退出0。原worktree完整回归和干净clone完整回归分别留存，不混算数量。
- 干净clone完整回归收尾有 Surefire 告警：等待测试子JVM退出30秒后终止该子进程。510项和Maven退出码均正常；原因未定位，不无证据认定为已有问题，也不将该告警隐藏为“无任何异常”。独立QA需关注测试线程/调度资源回收；本轮不扩展改造无关测试生命周期。
- 重启后确认合成管理员=1、初始化审计=1、PRIVACY_CONSENT回执=1；env哈希和账号身份指纹相同。CORS/配置和身份数据没有重新初始化。
- 固定功能提交28个文件加两份交付文档；敏感路径/新增秘密模式扫描命中0，无env、构建产物、照片、备份或缓存进入Git。四份规则/文档原件备份的SHA-256全部核验一致，备份位于被忽略的work目录。
- 相对固定功能提交，Java、管理端、小程序、脚本、Compose和example均无后续代码差异；后续只提交本报告及甲方说明。最后运行git diff --check及工作树检查。
- 本轮Compose项目rrc-entry-baseline、rrc-entry-final、rrc-entry-clean均已down，保留各自三个命名卷；仅停止本轮5186预览及关闭本轮微信QA项目窗口，不停止其他项目或Docker引擎。测试证据在仓库外，便于独立QA复核。

可直接复核的本机证据：

- [固定提交浏览器检查](D:/biancheng/rrc-entry-evidence/clean-browser.log)
- [首页截图](D:/biancheng/rrc-entry-evidence/admin-home.png)
- [资质列表截图](D:/biancheng/rrc-entry-evidence/admin-certifications.png)
- [微信实际页面运行证据](D:/biancheng/rrc-entry-evidence/wechat-real-validation-result.txt)
- [保留数据重启证据](D:/biancheng/rrc-entry-evidence/restart-result.txt)

完整测试日志保留在本机仓库外供受控复核，不提交可能包含第三方诊断数据的原始日志。截图仅有合成数据，微信无截图的限制已单列。
