# 跑跑代取后端

这是 Android App 的本地联调后端：提供账号注册/登录、用户发布订单、骑手接单、取餐、完成订单和订单查询。

## 启动

1. 安装 Docker Desktop，并确认其处于运行状态。
2. 先在后台工程执行 `pnpm build`。在此目录打开 PowerShell 7.2+，首次启用 MFA 时生成本地专用密钥，再启动：

```powershell
.\tools\new-admin-mfa-key.ps1 -OutputDirectory './secrets/admin-mfa'
docker compose -f compose.yml -f compose.admin-mfa.yml up --build
```

3. 看到 `Started RunRunCampusBackendApplication` 后，在浏览器打开 `http://localhost:8080/api/v1/health`。返回 `{ "status": "ok" }` 即成功。
4. 运营后台生产构建由 API 容器持久提供，访问 `http://127.0.0.1:5173`。该端口只绑定本机，应用 API 的 `8080` 端口保持不变。

密钥已存在时跳过生成步骤，不得重新生成覆盖。管理员必须完成密码和认证器验证；首次登录需绑定并保存恢复码。部署、密钥备份、换绑与离线恢复见 [管理员双因素认证](deploy/ADMIN_MFA.md)。

## 本地联调账户

应用内可注册两类账号：`CUSTOMER`（用户）与 `COURIER`（骑手）。请在两台设备或两个 Android 模拟器上分别注册，以测试接单和追踪。

## 骑手经营工作台

骑手通过 `GET /api/v1/courier/operations/me` 读取上下线状态、待接与进行中订单数、今日及累计完成量、订单服务费统计和评价；通过 `PUT /api/v1/courier/operations/me/availability` 更新接单状态。未通过认证的骑手不能上线，离线骑手不能接取新订单，但不影响已接订单继续履约。

Flyway V23 保存骑手接单状态。订单服务费字段按已完成订单的用户实付服务费汇总，仅用于经营统计，不代表骑手收入；分账、结算与提现不在当前版本内。

## v1.9 运营指挥台

`GET /api/v1/admin/command-center` 返回同一数据库快照中的履约预警与在线运力。订单队列参数 `queue` 支持 `WAITING`（已付款待接）、`LATE_WAITING`（待接满 10 分钟）、`ACTIVE`（配送进行中）、`LATE_DELIVERY`（接单满 30 分钟未完成）；`orderPage` 和 `courierPage` 从 0 开始，每页 20 条。

待接计时使用最后一次成功付款时间，零元订单使用创建时间；履约计时使用最早接单事件。历史数据缺少时间记录时按创建时间估算，并在响应和页面标记。上述时限仅用于运营提醒，不自动取消、扣罚或派单。在线运力只计入已启用、认证通过且主动上线的骑手，按进行中单量排序。

接口要求 `DASHBOARD_VIEW`，订单和骑手分区分别由 `ORDER_VIEW` 与 `COURIER_VIEW` 控制，无权限的分区返回 null。本版复用 V23 数据结构，无新增迁移；需要同时更新管理后台与后端，Android v1.8 可继续使用。

## 后台列表分页

订单、用户、骑手、工单、反馈、审计日志及骑手订单明细统一按创建时间倒序，同一时间再按 ID 倒序，订单 CSV 导出使用相同顺序。此规则确保数据不变时翻页顺序确定；分页期间有新增、删除或筛选字段变化时，基于页码的结果仍可能移动。

`AdminPaginationPostgresIntegrationTest` 使用一次性 PostgreSQL 16 验证真实数据的组合筛选、空关键词、总数与页数、跨页时间戳重复、越界页和 CSV 顺序。在 Docker Desktop 开启时运行：

```powershell
.\tools\apache-maven-3.9.11\bin\mvn.cmd -B -Dtest=AdminPaginationPostgresIntegrationTest test
```

## 订单历史与版本提示

新版 Android 使用 `GET /api/v1/orders/mine/active` 读取进行中订单，用 `GET /api/v1/orders/mine/history?page=0&size=20` 翻阅已完成或已取消订单；旧版 `/mine` 保留兼容。历史按创建时间、ID 倒序，页码从 0 开始，每页最多 50 条。

`GET /api/v1/app-version/android` 默认不发布更新提示。正式 APK 发布后设置 `ANDROID_UPDATE_VERSION_CODE`（须高于客户端 versionCode）、`ANDROID_UPDATE_VERSION_NAME`、`ANDROID_UPDATE_DOWNLOAD_URL`（HTTPS），客户端才显示非强制更新提示。`GET /api/v1/auth/me` 用于后台复核当前会话和权限；岗位变更后的旧会话仍须重新登录。

## 数据库备份

`backup` 容器启动后会立即生成一份 PostgreSQL 自定义格式备份，此后每 24 小时备份一次。文件保存在本目录的 `backups` 文件夹，完整写入后才会从 `.tmp` 改名为 `.dump`，并自动删除超过 7 天的备份。

手动生成备份：

```powershell
.\tools\backup-now.ps1
```

检查最新备份能否被 PostgreSQL 恢复工具读取：

```powershell
.\tools\check-latest-backup.ps1
```

恢复运行中的数据库会覆盖数据；需要真实恢复时应先停止 API 和备份服务、保留恢复前快照并明确选择目标备份。可先使用 [隔离恢复与回滚演练](deploy/RECOVERY_REHEARSAL.md) 验证备份及版本组合，该脚本只操作新建的演练数据库。

## 本地运维检查

使用 PowerShell 7.2 或更高版本执行 `pwsh -NoProfile -File .\tools\check-local-operations.ps1`，一次检查容器、API、后台页面、数据库查询、磁盘空间及最新备份，输出 JSON；全部正常退出码为 0，任一失败为 1。

备份失败后每 60 秒重试，成功后恢复每 24 小时一次；失败会清除健康标记，成功标记超过 26 小时也会判为不健康。详细阈值、测试与更新步骤见 [本地运维检查说明](deploy/LOCAL_OPERATIONS.md)。

在隔离恢复演练中加入 `-LoadTest` 可运行后台读取压力测试，记录成功吞吐量、延迟、限流、异常及容器资源占用；执行方式见 [压力测试说明](deploy/LOAD_TEST.md)。

加入 `-WriteLoadTest` 可运行持续下单、重复重试与抢单写入压测，核对订单、事件、通知及优惠扣减；执行方式见 [订单写入压力测试](deploy/ORDER_WRITE_LOAD_TEST.md)。两种压测参数互斥。

## 下单并发与重复提交

`POST /api/v1/orders` 支持可选 UUID 请求头 `Idempotency-Key`。同一用户以相同标识和内容重试，返回同一订单的当前状态，不重复扣券、扣月卡或生成创建事件；同一标识用于不同内容返回 409。不同用户的标识独立，真正的新订单应使用新标识。未携带标识的旧客户端仍可下单，但不具备重复提交保护。

Flyway V16 增加用户与提交标识的唯一约束，并用数据库序列生成 `RP-` 加 20 位数字的订单号，消除原来每秒 90 个随机尾号的碰撞。历史订单号保持原值；序列允许存在间隙，备份恢复必须包含序列状态。带标识的创建请求按用户行锁串行化，优惠扣减、订单与创建事件在同一事务中提交。

运行真实 PostgreSQL 并发验收（需要 Docker）：

```powershell
.\tools\apache-maven-3.9.11\bin\mvn.cmd '-Dtest=OrderConcurrencyPostgresIntegrationTest' test
```

测试使用临时数据库和合成账号，覆盖重复提交、不同内容冲突、跨用户隔离、同券争用、月卡最后一次额度、独立订单号、抢单与取消竞争，以及事件写入失败后的完整回滚。推送保持关闭。

## 重要限制

- 当前用户名密码登录仅用于本地开发；公开运营前需接入短信验证码、频率限制与真实身份审核。
- `compose.yml` 内的数据库密码是本地开发示例，公开部署前必须用环境变量和强密码替换。
- 本地真机调试使用 HTTP；公开上线必须改为 HTTPS 并关闭明文流量。
- 已注册账号连续输错密码 5 次后锁定 15 分钟；成功登录会自动清零失败记录。

## 用户信用与风险审核

- 普通用户和骑手初始信用分为 100 分：80–100 为“正常”，60–79 为“关注”，0–59 为“高风险”。信用等级当前只作展示，不自动停用账号，也不自动限制下单或接单。
- 系统每 10 分钟扫描风险信号：同一用户 24 小时内取消至少 3 单、30 天内关联至少 3 个异常工单，或骑手订单超过现有配送提醒阈值。信号只进入人工审核，不直接改变信用分。
- 具有用户管理权限的管理员可以确认风险并扣分、人工加减分、驳回误报或撤销既有信用变动；每次操作都要求填写原因，并写入信用流水和管理员审计日志。
- 用户和骑手可在 Android“我的信用”查看当前分数、等级、待审提示数量和信用变动依据，并通过帮助与反馈提交申诉。
- V37 为历史非管理员账号补建 100 分档案，并通过数据库触发器为后续新账号自动建档；风险来源键和撤销记录均有唯一约束，防止重复处理。

## 隐私政策与账号注销

- 当前隐私政策版本为 `2026-10-24`，运营主体为“射阳县合德镇轩轩生活服务部”，投诉电话为 `15151050678`。本版本同步小程序订单字段、微信支付、送达凭证、一次性位置及数据保存期限说明。
- Android 首次启动会先展示隐私政策，用户明确同意后才初始化第三方推送；注册和旧账号登录都必须在服务端记录当前政策版本。
- 用户可单独撤回隐私同意；撤回会清除服务端同意记录、撤销全部登录会话并退出应用，但不会删除账号和业务资料，再次同意后可继续登录。
- Android 内置政策文本用于联网和第三方 SDK 初始化之前展示。修改政策时，必须同时更新后端 `PrivacyService.CURRENT_VERSION`、后端政策正文及 Android `PrivacyPolicy` 的版本和正文。
- 用户注销需要校验当前密码并输入“注销账号”二次确认；存在进行中订单或未完成退款时拒绝注销。
- 注销立即撤销会话和骑手接单资格，删除地址、学生证材料、收件消息与待推送记录，并匿名化手机号、昵称和密码。历史订单、支付、工单及审计记录只按履约、售后和法律义务所需保留，页面统一显示为“已注销用户”；原手机号可重新注册。
- 正式上线前必须由运营方根据实际业务和适用法规确认各类历史记录的具体保存期限，并把期限写入对外政策和内部清理制度；当前实现未擅自设置期限。

## 生产环境准备

- 默认启动配置为 `dev`，保留现有本地 PostgreSQL 与上传目录。
- `prod` 默认把订单凭证保存到同地域的私有 OSS Bucket，要求显式提供数据库、管理员、OSS Region、Bucket 和 ECS RAM 角色名，不提供开发默认密码。
- OSS 生产凭证通过 ECS 实例 RAM 角色自动获取并刷新，不在配置文件中保存 AccessKey；`.env.production` 仍不能提交。
- 阿里云移动推送默认关闭。完成 EMAS Android 应用创建并给 ECS RAM 角色授予 `mpush:PushV2` 权限后，在 `.env.production` 填写 `PUSH_APP_KEY` 并设置 `PUSH_ENABLED=true`。后端通过 ECS RAM 角色临时凭证调用 PushV2，不保存 AccessKey。
- 订单站内消息提交成功后才调用推送；推送失败只记录日志，不回滚接单、取餐、送达或异常反馈。客户端通知通道 ID 与后端 `PUSH_CHANNEL_ID` 必须保持为 `order_updates`。
- 数据库结构由 Flyway 管理：旧开发库自动在 V1 建立基线，新数据库从 V1 开始完整创建。
- 正式容器以非 root 用户运行，并通过 `/actuator/health` 提供健康检查。

生产密码不再直接写入 `.env.production` 或容器环境变量，而是通过 Compose Secret 挂载到 `/run/secrets`。先生成两个互不相同的随机密钥：

```powershell
.\tools\new-production-secrets.ps1
.\tools\new-admin-mfa-key.ps1 -OutputDirectory './secrets/admin-mfa'
```

用 `secrets/production/database-password.txt` 的值创建生产数据库最小权限账号，并从 RDS 控制台下载 CA 证书保存为 `secrets/production/rds-ca.pem`。复制 `.env.production.example` 为 `.env.production`，填写真实数据库、管理员、私有 OSS Bucket 和 ECS RAM 角色信息；生产数据库连接必须使用 `sslmode=verify-full` 和挂载的 CA 证书。

上述生成脚本在 Windows 上使用 PowerShell 7.2 或更高版本，生成目录仅允许当前用户、SYSTEM 和本机管理员访问。已有密钥不能覆盖，`-Force` 也会被拒绝。需要轮换时执行：

```powershell
.\tools\prepare-production-secret-rotation.ps1
```

该命令在 `secrets/production/rotations/时间戳/` 生成候选密码、`before` 原文件备份及 `rotation.json` 准备状态，不修改现用文件或线上账号。默认读取本机 `secrets/production/`；若生产配置引用其他目录，应通过 `-SourceDirectory` 指定实际来源。现有管理员不会因为更换初始化 Secret 而自动改密。线上切换与恢复步骤见 [生产凭据轮换](deploy/PRODUCTION_SECRET_ROTATION.md)。

正式启动前执行静态门禁。该命令不会回显密钥：

```powershell
.\tools\test-production-config.ps1
```

也可以单独检查生产编排：

```powershell
docker compose --env-file .env.production -f compose.prod.yml config
```

`.env.production` 和 `secrets/production/` 已被版本控制忽略。部署服务器上应把 Secret 文件权限限制为仅部署账号可读，不得通过聊天、工单或日志传输密钥。应用在 `prod` 配置下还会再次执行启动门禁，拒绝未验证的数据库 TLS、PostgreSQL 超级用户、弱密码、占位值、重复密码或 OSS 公网端点。

生产镜像使用已经通过测试的 JAR，避免在镜像构建阶段重复下载 Maven 依赖：

```powershell
.\tools\apache-maven-3.9.11\bin\mvn.cmd clean package
docker compose --env-file .env.production -f compose.prod.yml build
```

正式启动前还必须在服务器前配置 HTTPS 反向代理；`compose.prod.yml` 默认只把 API 绑定到服务器回环地址，避免绕过 HTTPS 直接暴露 8080 端口。

## 梅园快递加价

- 快递基础价为小件 1 元、中件 2 元、大件 4 元、超大件 8 元；送达地址包含“梅园”时统一加价 2 元，对应原价为 3 元、4 元、6 元、10 元。
- 加价计入订单原价，优惠券或月卡随后抵扣；外卖订单和已有订单不受影响。
- 骑手改判为超大件前必须上传规格核验照片；用户确认后按新规格重新计价并保留梅园加价。
- 超大件适用于 20–30kg、最长边 80–120cm，或需要小推车搬运的快递；超过 30kg、最长边超过 120cm、需要两人搬运或存在安全风险的快递不承运。

## 微信支付

Android App 的普通订单、规格补差价和月卡购买统一使用微信支付。订单付款后才进入骑手可接单列表；月卡只在服务端验签并核对金额后生效。已支付订单取消或规格降档会生成退款单，退款通知失败时由定时查单继续确认。

在微信开放平台登记 Android 包名 `com.zhangxuan.runruncampus`，应用签名填写正式 APK 证书 MD5：`4face005684ee01752b9e18f952f2dc3`。在微信支付商户平台完成该 AppID 与商户号绑定，并取得商户证书序列号、商户 API 私钥和 32 字节 APIv3 密钥。

复制 `.env.production.example` 中的微信支付字段，私钥保存到 `WECHAT_PAY_PRIVATE_KEY_FILE` 指向的文件，APIv3 密钥单独保存到 `WECHAT_PAY_API_V3_KEY_FILE` 指向的文件。两个文件不能提交到版本库、写入镜像或通过日志输出。支付和退款回调地址必须是可由微信访问的 HTTPS 地址。

生产构建通过后叠加支付编排启动：

```powershell
docker compose --env-file .env.production -f compose.prod.yml -f compose.wechat-pay.yml up -d
```

## 校内网部署

无锡学院校内网限定部署请参阅 `deploy/CAMPUS_NETWORK_DEPLOYMENT.md`。示例 Nginx 配置默认拒绝远程访问；必须先取得学校确认的准确校园网 CIDR、内部域名和 HTTPS 证书，不能直接使用未经确认的私有网段。

## 挑战杯溯源与派单实验（待测试环境验收）

- `POST /api/v1/admin/fulfillment-provenance` 由运营或超级管理员逐笔录入已完成商业订单的来源；`GET` 和 `/summary` 供授权管理员查看，`POST /{id}/verify` 由另一名审计员或超级管理员核验。录入内容不可经 API 修改，核验状态只能单向更新；来源编号不得填入姓名、电话等个人信息。完成凭证和财务凭证仅保存 SHA-256 摘要，原件须在受控位置另行留存。
- 渠道为 `APP_END_TO_END`、`APP_ASSISTED` 或 `MANUAL_OFFLINE`。`APP_END_TO_END` 至少要求关联已完成且软件支付成功的订单；此校验不等同于证明所有环节在线完成，核验者仍须核对凭证。客户实付额、平台收入、骑手结算额均为独立的可空“分”字段，不从订单服务费推算；填写任一金额时须提供独立财务凭证摘要。汇总仅计算已核验且已填平台收入的记录，未知金额保持 `null`，绝不当作零。现有 50 笔订单和 200 元收入不会自动回填。
- `POST /api/v1/admin/dispatch-experiments` 接受最多 100 组匿名候选场景，每组最多 50 名骑手，返回现有评分排序与校园预计到达时间排序的对照。预计到达时间直接等于输入的步行时间加剩余工作时间，逾期按场景截止分钟判断；这些输入须由后续实测或可复核估计提供。接口不读取或修改真实订单、不执行派单，输出的 `SIMULATION_ONLY` 不是实测效率提升或经营成果。
- 代码编译和纯单元测试不验证 PostgreSQL 上的 V42 迁移。首次应用迁移前应在隔离测试库演练，并由运营复核双人核验、重复来源、权限、撤回/纠错流程；在此之前不要接入线上历史数据。

## 管理员人工完成与订单归档（待测试环境验收）

- 超级管理员可通过 `POST /api/v1/admin/orders/{id}/force-complete` 提交 5–200 字原因，仅对已取件、有送达凭证且系统已确认收款的未归档订单跳过收货码完成。人工收款码订单还须已核款；未确认收款的货到付款订单不能借此变成已收款。该操作新增订单事件、通知用户与骑手，并写管理员审计日志；送达凭证图片可由有此权限的管理员通过只读、禁止缓存的 `GET /api/v1/admin/orders/{id}/delivery-proof` 核查。
- 超级管理员或运营管理员可将已完成/已取消且无待退款、待骑手结算的订单归档，并可恢复。归档不物理删除数据，不改变用户和骑手订单历史，也不从经营统计中扣除；管理后台列表与 CSV 默认只显示未归档订单，传 `archived=true` 查看或导出已归档订单。归档及恢复均有管理员审计日志。
- V43 迁移尚需在隔离测试库演练，重点验证历史订单默认未归档、查询和导出筛选、权限、事务回滚以及图片读取；本地编译和单元测试不代表这些数据库及实际运营流程已验收。
