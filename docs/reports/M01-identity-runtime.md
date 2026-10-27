# M01 新系统账号与身份交付

日期：2026-10-27。工作区：`D:/biancheng/RunRunCampus`；分支：`feature/m01-identity`。本报告描述本次新增源码；原先“M01仅有数据库基础”的记录保留为历史。权威字段和流程见《数据模型与接口契约》“M01全新身份运行契约”。

## 接口清单

统一前缀 `/api/v2`，失败体只有`code/message`，身份响应`Cache-Control: no-store`。未知JSON字段拒绝。账号ID返回十进制字符串；身份值和密码摘要不返回。

|方法/路径|请求|成功结果与权限|
|---|---|---|
|POST `/auth/wechat/login`|`code`|未绑定返回5分钟一次性loginTicket；已绑定返回token/account。后端兑换code2session|
|POST `/auth/wechat/bind-phone`|`loginTicket,phoneCode,requestedRole`|requestedRole只允许USER/COURIER，微信手机号验证后新建并绑定；200返回token/account。冲突409，不按手机号恢复或登录已有账号|
|POST `/auth/password/login`|`phone,password`|仅本系统已经微信绑定并设置密码的ACTIVE非部署账号；成功token/account，认证失败401|
|GET `/auth/session`|Bearer|当前account，读取实时状态、安全版本及接单资格|
|PUT `/auth/password`|Bearer、Idempotency-Key；`password`|设置/修改BCrypt密码，12字符起、UTF-8不超过72字节；撤销包括当前会话在内的全部会话|
|POST `/auth/logout`|Bearer、Idempotency-Key；`allSessions`|撤销当前或全部会话，审计与幂等同事务；撤销后旧Bearer重试401|
|PATCH `/admin/access/{userId}`|业务Bearer|始终403 ADMIN_ACCESS_MANAGEMENT_FORBIDDEN，资格管理走受控主机命令|

`account`白名单：`id,role,status,adminEnabled,securityVersion,courierEligible`。无关登录结果分支字段为null。

不实现旧账号恢复、recoveryTicket、A04人工恢复、旧v1账号入口；未实现的v2路径默认拒绝访问，v1路由在独立运行入口为404。

## 三类账号与权限

USER：微信code换取新身份，微信手机号授权后建立新账号、唯一绑定和会话。相同微信之后直接登录该新账号。

COURIER：注册时建立NOT_APPLIED/eligible=false资质基础行，可以登录和读取自身身份。`requireRole(token,"COURIER",false)`用于本人资质等无需审核资格的入口；接单等动作使用`true`并实时检查eligible，待审返回COURIER_NOT_APPROVED。没有伪造资质批准或接单接口来绕过后续模块。

ADMIN：本人先完成本系统USER微信/手机号注册，受控部署命令对这个已验证新账号执行管理员建档。转换后默认未启用，递增安全版本并撤销原USER会话；之后另行受控启用。普通注册无法直接选择ADMIN，普通管理员无法授权自己或别人。

部署操作者是新库内真实、ACTIVE、controlled_identity=true的ADMIN账号；不开放微信/密码登录，不能启用日常业务权限。主机执行权限和受控数据库凭据构成部署授权边界，环境配置的actor_id还须与数据库身份匹配，不能把任意业务ADMIN的ID当部署身份。没有复用旧SUPER_ADMIN，也没有公开首个管理员接口。

所有v2控制器须显式声明`@IdentityPermission`，缺少声明默认拒绝。ADMIN业务每次验证启用状态；所有会话每次查询撤销、到期、账号状态和安全版本。后续业务写须在业务事务中调用`IdentityService.authorizeForWrite`，其账号行锁与停用事务串行，禁止只在控制器检查一次后无锁落库。

启用、停用、重新启用均递增安全版本并撤销全部会话。重新启用必须重新登录。审计失败导致资格和会话撤销整体回滚。请求ID相同且内容一致时返回原结果，不重复递增版本；同键异参返回IDEMPOTENCY_KEY_CONFLICT。

## 数据库交付

沿用唯一迁移链：

1. `server/db/migrations/up/rrc_0001__create_v2_schema.sql`。
2. `server/db/migrations/up/rrc_0002__create_identity_foundation.sql`。
3. 本次新增`server/db/migrations/up/rrc_0003__identity_runtime.sql`。

0003增加部署身份列及“非ADMIN不可启用”“部署身份不能启用业务权限”约束，新增identity_tickets、identity_audit_events及索引，扩展V2_ADMIN_CREATED、V2_DEPLOYER_CREATED审计动作。对象所有权已登记至M00迁移登记，未重复创建订单或资质生命周期对象。

openid以AES-256-GCM随机IV加密，AppID绑定AAD；查重使用绑定AppID的HMAC。数据库对(app_id,openid_hash)、(app_id,user_id)、phone分别唯一。绑定持有票据行锁、手机号与微信身份事务锁；账号、身份、消费票据、审计和会话同事务创建。手机号只取微信服务端授权结果，不接受客户端手机号或openid。

会话和票据为32字节随机值，数据库只保存HMAC摘要。票据5分钟过期、一次消费；会话24小时过期。openid密文不进入日志/响应，session_key不保存。加密密钥和摘要密钥必须独立、至少32字节且受控注入。

0003回滚先检查运行票据、身份审计、部署身份和新增管理员审计；有数据则拒绝丢弃证据。空库回滚顺序为0003→0002→0001，均无CASCADE。备份恢复使用隔离临时数据库完成。未执行任何生产迁移或读取旧账号表。

## 构建与运行

后端工作目录：`D:/biancheng/RunRunCampus/handover-2026-10-24/RunRunCampusBackend`。

```powershell
mvn -B -ntp -Pv2-identity '-Dtest=com.zhangxuan.runrunv2.*Test' package
java -jar target/runrun-campus-backend-0.0.1-SNAPSHOT.jar
```

必须选择v2-identity构建配置，生成的Start-Class为`com.zhangxuan.runrunv2.IdentityApplication`；默认旧工程构建入口保留。新入口只扫描`com.zhangxuan.runrunv2`，禁用旧JPA/Flyway扫描；配置名application-v2，不加载旧application-dev配置。无需新增依赖。

运行前在隔离空库手动按顺序执行0001/0002/0003；不自动扫描旧迁移，不自动baseline。数据库连接配置经以下环境变量注入，不在命令行或文档填写实际值：

- `RRC_V2_JDBC_URL,RRC_V2_DB_USERNAME,RRC_V2_DB_PASSWORD`。
- `RRC_V2_ENVIRONMENT`：仅local/test/staging；其他值拒绝启动，生产没有开放。
- `RRC_WECHAT_APP_ID,RRC_WECHAT_APP_SECRET`：微信主体的受控配置。
- `RRC_IDENTITY_ENCRYPTION_KEY,RRC_IDENTITY_HASH_KEY`：分离的base64密钥，不能在运行期间随意替换。
- 可选`RRC_V2_PORT`，默认8082；默认仅绑定127.0.0.1，由受控HTTPS反向代理接入。

启动前检查新表和运行增量，并拒绝存在旧public关系的数据库；不访问public.app_users数据。

部署命令使用同一v2产物，在授权服务器的交互终端执行：

```powershell
java '-Dloader.main=com.zhangxuan.runrunv2.IdentityAdminCommand' -cp target/runrun-campus-backend-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher bootstrap
```

bootstrap仅允许尚无受控部署身份时执行。记录输出的部署账号ID，受控配置`RRC_DEPLOYMENT_ACTOR_ID`，随后命令形如：

```powershell
java '-Dloader.main=com.zhangxuan.runrunv2.IdentityAdminCommand' -cp target/runrun-campus-backend-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher create-admin <newVerifiedUserId> <expectedSecurityVersion>
java '-Dloader.main=com.zhangxuan.runrunv2.IdentityAdminCommand' -cp target/runrun-campus-backend-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher enable <adminId> <expectedSecurityVersion>
java '-Dloader.main=com.zhangxuan.runrunv2.IdentityAdminCommand' -cp target/runrun-campus-backend-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher disable <adminId> <expectedSecurityVersion>
```

每次交互输入理由、请求ID及完整确认文本（CREATE DEPLOYMENT IDENTITY / CREATE ADMIN ID / ENABLE ID / DISABLE ID）。首次USER安全版本为0，管理员建档后为1，首次启用后为2；实际值以会话查询/受控核对为准。不能把前端确认字段、手机号或普通Bearer作为部署授权。

## 已运行验证

- PostgreSQL 16 Testcontainers集成测试19项：新注册及登录、票据消费/过期、身份重复绑定、同手机号USER/COURIER/ADMIN均不恢复、并发同手机号只有一个成功、未审核骑手拒绝接单权限、未知字段和ADMIN自选拒绝、令牌篡改/到期/单次及全量撤销、密码变更、停用账户、管理员默认关闭/越权、启停及重新启用、幂等、理由/确认/版本、审计失败回滚、业务事务与停用竞争、旧表哨兵隔离、权限默认拒绝、响应隐私、生产环境拒绝。
- 微信HTTP适配测试4项：固定微信域名及code编码、stable_token/getPhoneNumber请求和缓存、微信失败/异常响应/传输错误脱敏、跨AppID watermark及国家码/手机号拒绝。HTTP响应为测试替身，未连接真实微信。
- `pwsh -NoProfile -File server/db/tools/verify-identity-runtime.ps1`：8项通过，覆盖运行迁移、授权约束、重复迁移拒绝、空库反向链、有数据拒绝回滚、pg_dump/pg_restore及恢复后授权约束。
- `pwsh -NoProfile -File server/db/tools/verify-m01.ps1`：原身份基础20项通过，旧脚本未修改。
- v2-identity定向测试与package构建成功，核对产物Start-Class及PropertiesLauncher；非交互部署命令拒绝执行。
- `pwsh -NoProfile -File server/db/tools/verify-identity-package.ps1`：7项通过，使用实际可执行JAR及临时数据库验证启动、真实HTTP会话401/no-store、无效登录400、旧登录404、恢复路径不可达。修正了冒烟脚本必须等数据库完成最终TCP初始化的就绪检查；未向真实微信发送请求。

测试只使用随机临时PostgreSQL容器和合成数据。失败调试中修复了未实现路由的异常映射，最终测试全部通过；不输出请求凭据或上游诊断。

## 未实现项与验收边界

- 真实微信code2session/getPhoneNumber代码已实现，但未使用正式主体、授权手机号能力、受控AppSecret和合法HTTPS域名做网络/真机联调；真实code一次性消费依赖微信上游，测试以替身模拟。
- 小程序、管理端页面接入、session_v2存储与真机注册交互尚未联调；本交付是后端身份模块，不表示客户端闭环已验收。
- 骑手照片申请、上传、审核和保全删除遵循M00登记的M07门禁，本次不开放；已有待审核基础状态和接单权限保护。实际订单接单接口属M04，需接入同一权限服务后再做端到端验收。
- 远程资格管理和旧超管MFA恢复不实现；本阶段使用受控服务器部署身份。后续资金操作MFA及业务审计由对应模块落实。
- 多实例统一认证限流须由网关配置；当前进程内认证入口限流不能替代网关。会话/票据归档清理和密钥轮换运维流程尚未接入，不影响当前到期和撤销判定。
- 未运行旧系统全量测试、生产操作或真实资金链路；未发布、未推送。旧系统全量测试不计入本次新身份模块通过数。

修改规则前已保存可恢复副本与原路径/SHA-256清单：`work/backups/RunRunCampus/20261027-140806/manifest.json`，未提交备份或任何凭据。
