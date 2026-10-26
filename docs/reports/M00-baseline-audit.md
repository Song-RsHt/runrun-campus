# M00 规则同步、基线与开发环境核查

日期：2026-10-26（Asia/Shanghai）。性质：代码现状核查与开发验证，不是阶段一业务验收。

## Git基线与交付

- 用户确认的审查基线：`3da72bc0ef183dcb435bfec1b612e7deba3ee4e7`，初始main与本地origin/main一致；权限开放后通过远端读取和GitHub API再次确认main仍为该SHA。
- 唯一origin：`https://github.com/Song-RsHt/runrun-campus.git`，README与实际配置一致。
- 用户确认分配的ROOT：`D:/biancheng/RunRunCampus-worktrees/m00-baseline`；分支：`chore/phase1-baseline`。通过git rev-parse --show-toplevel核实，从ROOT映射三个工程。
- 独立规则提交：`d981f0ca572ff6f0a2dd07ecf66450a24e657d5a`，只含AGENTS.md与.gitignore。保留用户的规则内容，仅清除行尾空格及多余EOF空行；未采用五角色升级草案。
- 主工作区保持main及原有AGENTS.md修改、未跟踪管理端.pnpm-store；未暂存、删除缓存或业务文件。
- 初轮推送受沙箱凭据权限限制；权限开放后已成功推送，创建目标为main的[PR #1](https://github.com/Song-RsHt/runrun-campus/pull/1)。使用已有受控Git凭据，未打印/保存令牌，未更换仓库。PR保持open，开发agent未合并。
- 合并由总控负责。当前不能提供“合并后main统一开工SHA”；总控审查合并后，须重新fetch并记录main完整40位SHA，不能将本分支HEAD冒充统一基线。

规则文件修改前在本worktree的work/backups/RunRunCampus/时间戳目录保存基线版、用户版和SHA256清单；该目录被忽略，未提交。规则快照从用户主工作区读取，本worktree之外未修改项目文件。

## 核查范围、写入与事务边界

已依序读取实际G、B、C、D、V及专题A、M。M00只写规则同步、缓存忽略规则和核查交付文档；不拥有任何业务接口或数据库写入，不创建迁移，不实施业务功能。生产连接、真实支付、生产迁移和恢复生产新订单均未执行。

逐项结果见[40组接口、37个错误码、66条决策清单](M00-contract-inventory.md)。该清单只描述现状，技术定义仍仅以C为准。40组v2接口均未达到当前契约；37个稳定错误码未实现E01错误体。旧接口、旧事务、同名类和测试存在不能据此认定新规则实现。

## 已有能力与缺口

后端为Spring Boot 3.5.16/JPA/Flyway/PostgreSQL，JDK17；62个现有Test类，其中13个PostgresIntegrationTest类。Testcontainers使用postgres:16-alpine，测试动态配置隔离数据库。

可复用旧能力：密码登录/会话、九档整数分价格及仅包裹加区域费、部分账号/订单行锁和建单幂等、骑手资质照片申请/审核/30天清理、旧订单问题/重派、凭证存储接口、站内消息和旧推送outbox。它们尚未满足v2流程隔离、版本控制和权限/事件约束。

主要缺口：真实微信身份与手机号兑换、绑定恢复、v2管理员启用与安全版本、独立订单/任务/调整/支付尝试/欠款/账本冻结模型、十分钟确认窗口、到场补贴、按任务交接及分配、全链路幂等/审计/outbox、证据保全删除排他、微信订阅消息、授权后混合历史统一分页。

管理端为React 19/TypeScript 7/Vite 8，package.json只有dev/build/preview，无test/lint脚本；没有独立/v2-admin入口。旧角色QA脚本在管理端tools下，依赖Docker、指定本地镜像、Playwright和本机Codex Node路径；不能作为新克隆可直接运行证明。

小程序为原生微信JS/WXML/WXSS，无package.json、自动化测试或构建期环境生成入口；仍用runrun_session_v1及固定生产API地址。没有打开小程序或请求该生产地址。管理端及小程序三处仍有“夜间急送/22:00后优先派送”旧文案，本模块不改业务页面。

## 迁移核查

运行目录src/main/resources/db/migration及交接01-数据库迁移脚本均为V1–V43，各43份；逐份SHA256一致。未发现V44–V47脚本。本模块未修改SQL、未连接生产读取Flyway历史。

权限开放后，现有测试在全新隔离PostgreSQL库中成功校验并应用43个迁移到v43；这是本地执行证据。V42/V43生产执行状态仍待核实：需由授权运维提供可信的目标环境Flyway历史（版本、成功状态、安装时间、校验和和环境标识），以及备份/恢复演练记录。文件存在或本地通过不能推断生产已执行；无法核实其他共享环境时也不原地修改旧SQL。M01统一协调V44–V47及双审计白名单扩容。

## 开发环境

| 项目 | 实测结果 | 限制 |
|---|---|---|
| PowerShell | 7.6.5 | 满足V的7.2+ |
| JDK | Temurin OpenJDK 17.0.18 | 满足JDK17 |
| Maven | 3.9.16 | 使用worktree/work/maven-repository独立可写依赖缓存；无新增项目依赖 |
| Node/pnpm | Node 22.23.2、pnpm 11.25.0 | pnpm冻结安装成功，现有锁文件未改 |
| Docker/Compose | 权限开放后Docker客户端/引擎29.8.0、Compose v5.5.1均可用 | 已启动原先未运行的Docker Desktop，desktop-linux引擎可访问；未触碰其他容器 |
| PostgreSQL | 本机psql/服务未发现；Testcontainers的postgres:16-alpine容器为PostgreSQL 16.15 | 隔离数据库测试81项通过，本机psql不是Testcontainers的前提；未探测生产 |
| Git | 2.50.1.windows.1 | 仓库所有者与沙箱用户不同，仅使用单命令safe.directory |
| GitHub CLI/认证 | gh不在PATH；权限开放后现有Git凭据可用 | 通过Git推送及GitHub REST API创建/更新PR；凭据仅在进程内使用 |

仓库未跟踪.github workflow文件。初轮无认证的API响应不作配置结论；权限开放后认证查询actions/workflows返回200且total_count=0，main分支返回protected=false。保护详情和rulesets接口仍返回403，未修改权限或保护设置，不假设存在自动验收。

## 执行命令与结果

所有项目命令在本ROOT内对应工程执行，安装的均为锁文件/POM已有依赖；缓存、构建结果、日志未提交。

- Git：rev-parse、remote -v、status、log、diff、worktree list/add、diff --check、只暂存AGENTS.md/.gitignore后commit，以及push -u origin chore/phase1-baseline。规则diff检查通过，提交成功；初轮push失败，权限开放后重试成功，PR已创建。
- 管理端：`pnpm install --frozen-lockfile --store-dir ../../work/pnpm-store`成功；`pnpm build`成功（tsc -b与Vite构建，47模块）。未运行pnpm test/lint，因为没有对应脚本。
- 小程序：逐个`node --check`检查11个JS文件，全部通过；仅语法检查，不是开发者工具、微信联调或真机验收。
- 现有纯JS校验：在后端执行`node --input-type=module -e "import('./deploy/tests/load-validation-test.js').then(m=>m.default())"`与对应order-write-validation-test入口，分别11和44项通过；只执行响应分类函数，不进行网络压测。
- 后端首次：`mvn -Dmaven.repo.local=<ROOT>/work/maven-repository test`失败；Surefire报告293条记录、0断言失败、23错误、0跳过。270项通过；13个PostgresIntegrationTest类在容器初始化失败，根因为“Could not find a valid Docker environment”，数据库内测试方法未实际执行；另10项OrderProofServiceTest在JUnit清理系统临时目录时出现AccessDeniedException，不是业务断言失败。
- 定向环境复核：加`-DargLine=-Djava.io.tmpdir=<ROOT>/work/java-tmp -Dtest=OrderProofServiceTest`运行`mvn test`，10项全部通过，未改测试/业务源码。
- V完整构建：`mvn -Dmaven.repo.local=<ROOT>/work/maven-repository -DargLine=-Djava.io.tmpdir=<ROOT>/work/java-tmp clean package`仍失败；293条记录、280项通过、0断言失败、13个Docker类初始化错误、0跳过。临时目录问题已消除，数据库集成仍未通过。
- V仅打包：`mvn -Dmaven.repo.local=<ROOT>/work/maven-repository clean package -DskipTests`成功，Spring Boot重打包产物生成；包含此前由管理端源码构建的静态资源。跳过测试构建绝不作为验收通过，产物未提交。

Docker的实际发现路径为`C:/Users/29375/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe`。初轮直接执行被拒；权限开放后CLI可运行，最初info报引擎管道不存在。使用Start-Process -WindowStyle Hidden启动Docker Desktop后，info确认引擎29.8.0，测试已进入镜像拉取阶段。初轮“当前身份不可访问”的结论已被本次结果替代。

权限开放后执行：`mvn -Dmaven.repo.local=<ROOT>/work/maven-repository -DargLine=-Djava.io.tmpdir=<ROOT>/work/java-tmp clean package`，2026-10-26 14:57（Asia/Shanghai）完成，退出码0、BUILD SUCCESS。62个测试类共361项通过，其中非Postgres测试280项、13个PostgresIntegrationTest类81项；Failures=0、Errors=0、Skipped=0。包含测试的完整打包成功，替代初轮“数据库测试不可运行”的环境结论；不代表缺失的v2功能已完成。

镜像首次下载较慢。额外执行`docker pull postgres:16-alpine`成功，镜像摘要为sha256:f4610795587492cd4fb887c2dc3de301b813278bb6f616992061141b5f15ac36。下载期间独立Registry连通性检查一度返回HTTP000/CurlExit28，随后拉取完成，未更换镜像源或修改Docker全局配置。测试结束后docker ps为空，Testcontainers已清理本轮容器；保留已下载镜像缓存，不删除其他镜像或卷。

保留的既有运行问题：Surefire输出System.exit(0)后30秒未完成退出并自终止fork JVM的提示；dump显示main等待shutdown hooks，部分定时线程仍等待Hikari连接，日志同时有测试容器关闭后的连接池验证告警。Maven最终退出0，361项断言/数据库用例均通过，但测试生命周期清理问题仍需后续排查，不能称日志全无异常。另有Windows跨盘Boot Manifest-JAR classpath提示。日志及dump仅保存在被忽略的work/、target/中，未提交；M00不修改业务或测试源码掩盖这些现象。

未运行：Docker Compose全栈联调、管理端真实浏览器角色QA、跨模块v2集成、开发者工具验证、真实微信手机号/订阅/私有存储联调、真机验收、生产迁移/数据库校验及恢复演练。角色QA已检查前提：其默认后端镜像runrun-campus-backend:admin-mfa-20260903不存在，本机工具运行时虽有Playwright模块但无对应Chromium可执行文件；这些是额外测试资产条件，不是当前权限拒绝。构建和旧v1数据库测试不代表上述层级通过。

## 外部条件与受控配置

| 条件 | 需要的证据/配置方式 | 当前结论 |
|---|---|---|
| 隔离HTTPS测试域名 | 有效证书、适用备案/平台要求、request/uploadFile合法域名、后端与数据库隔离 | 未提供可信配置，不使用旧生产域名代替 |
| 微信主体/手机号能力 | AppID确认、已认证主体、getPhoneNumber权限及真实code后端兑换 | 尚未联调；不要求在聊天提供密钥 |
| 受控密钥 | AppSecret、身份加密/摘要密钥、MFA及存储凭据通过测试环境秘密管理或受保护只读文件注入，最小权限、轮换、日志脱敏 | 仅说明托管方式，不提交实际值 |
| 订阅消息 | 补款/送达/退款/售后模板映射、授权记录、失败重试和站内可靠通知 | 缺模板/授权及新适配器验证 |
| 私有存储 | 私有桶/对象ACL、受控读取、短效签名、删除重试、位置及学生证保留/保全策略 | 旧存储接口存在，实际权限及新全链路待验收 |
| 服务点和区域 | 运营核实的GCJ02坐标、启停与版本；缺坐标不自动判补贴 | 不编造坐标 |
| 值班负责人/管理员 | 已启用的有效审核负责人、超时升级接收人、首个v2管理员受控启用身份与审计 | 未提供有效配置及新资格服务 |
| 停单运营信息 | 生产停单公告、存量收尾负责人/进度及阶段二恢复条件 | 项目所有者维护，本模块不编造日期 |
| 隔离开发库 | 可访问的Docker Engine、postgres:16-alpine获取能力、隔离测试卷/端口及合成账号 | 已满足现有Testcontainers测试，81项通过；不等于生产迁移验收 |
| GitHub | 使用已有受控凭据推送模块分支并创建PR | 权限开放后已解决；PR #1交总控审查，未合并 |

## 后续模块接入

1. PR #1已推送并创建；总控审查合并后另行记录main完整SHA，才作为统一开工基线。开发agent不自行合并。
2. M01先核对V42/V43执行史并统一负责V44–V47，前序迁移先扩相应action/target_type；不让V45引用尚未存在的V46表。新旧资质共享字段与版本纳入V44。
3. 身份/管理员模块依赖V44，按C01、A01–A05及A36–A40接入旧账号与共享资质服务；普通管理员无资格管理权。
4. 计价/建单/履约依赖V44，支付调整依赖V45，冻结分配付款依赖V46，通知/升级依赖V47。原款支付成功与待接单、售后冻结与阻止完成、付款与冻结检查必须各在规定聚合锁和事务中。
5. 管理端和小程序只按C连接v2；先完成独立路由、会话和构建期域名断言，取得隔离域名后再联调。混合列表由后端统一授权分页，不拼接前端分页。
6. Docker及原有数据库基线已复核通过；后续模块复用本轮命令与隔离环境，另验收新业务，并排查测试退出阶段的连接池/定时线程清理。不得把旧v1数据库通过标为v2、真实微信或真机通过。
