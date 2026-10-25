# 管理员双因素认证

五种管理员岗位统一使用密码 + TOTP；普通用户和骑手继续使用原密码登录接口。此功能随 V17 迁移启用，没有密码单因素回退开关。

## 登录与恢复

- `POST /api/v1/auth/admin/login`：正确密码只返回 5 分钟挑战；未绑定时返回 `ENROLL` 和绑定资料，已绑定返回 `VERIFY`，人工重置后返回 `RECOVER`。挑战不能作为业务会话使用。
- `POST /api/v1/auth/admin/verify`：提交 `challengeToken` 和 `code`。绑定或验证成功才返回 `session`；绑定成功同时返回一次性的 10 个恢复码。重置凭证校验成功只返回新的绑定挑战，仍需确认新认证器。
- `POST /api/v1/admin/accounts/me/mfa/replace`：完整管理员会话、当前密码和现有动态码/恢复码换取新绑定挑战。确认新认证器之前旧认证器仍可用；确认后旧恢复码与全部旧会话失效。
- `POST /api/v1/admin/accounts/{id}/mfa/reset`：仅超级管理员可在人工核验后重置非超级管理员，必须填写核验记录及原因。返回一次性重置凭证，由操作人当面交给本人，24 小时有效。目标账号旧会话、挑战、认证器及恢复码立即失效。输入密码和凭证后再绑定；绑定中断可在有效期内重新开始，成功绑定后凭证失效。

动态码为 SHA-1、6 位、30 秒周期，允许前后各一个时间步；服务端原子记录最后使用时间步并拒绝重放。每个挑战最多 5 次错误；账号连续失败 5 次锁定 15 分钟，重新提交正确密码不会清零第二因素失败记录。登录挑战申请按账号、验证码验证按挑战使用容量 10、每分钟补充 10 次的令牌桶；认证路径另有每来源容量 30、每分钟补充 30 次的令牌桶。限流状态保存在单 API 实例内存，账号锁定及一次性消费保存于 PostgreSQL。

二维码由浏览器本地生成，密钥、挑战、恢复码不写入浏览器存储；成功会话保存在现有 `sessionStorage`。认证响应与错误响应均使用 `Cache-Control: no-store`。审计记录绑定、换绑、登录成功/失败、恢复码使用及人工重置，不记录验证材料。

## 密钥与部署

主密钥是独立随机的 32 字节 Base64 字符串。使用 Windows PowerShell 7.2+ 生成到新目录（不允许覆盖）：

```powershell
.\tools\new-admin-mfa-key.ps1 -OutputDirectory './secrets/admin-mfa'
```

目录仅允许当前用户、SYSTEM 和本机管理员访问；`secrets/admin-mfa/` 已被 Git 忽略，Docker 构建上下文排除 `secrets/`。生成脚本只输出路径。生产与本地应分别生成密钥，禁止采用测试夹具中的固定密钥。应用在生产配置缺失或密钥无效时拒绝启动；开发环境缺失密钥时管理员认证返回 503。

本地首次启动前构建最新后台 `pnpm build`，使用 `docker compose -f compose.yml -f compose.admin-mfa.yml up --build` 挂载密钥；单独使用旧的 `compose.yml` 不具备 MFA Secret。该覆盖文件不切换当前运行服务，只有执行启动命令后才生效。

生产 `.env.production` 增加 `ADMIN_MFA_KEY_FILE`，指向服务器上的受保护密钥文件；`compose.prod.yml` 以 Secret 挂载到 `/run/secrets/ADMIN_MFA_KEY`，由 `configtree` 加载。文件须可由容器内 `runrun` 用户读取，同时限制宿主机访问；Compose 文件 Secret 是文件挂载，宿主机文件保护仍由运维负责。上线前运行 `tools/test-production-config.ps1`，验证配置及文件，然后在目标主机验证实际挂载可读、应用启动及管理员绑定。

发布前备份数据库、旧版本、配置以及 MFA 主密钥，记录每份数据库备份对应的密钥版本。数据库备份只包含加密的 TOTP 数据，现有日备份脚本不会自动备份外部 Secret；主密钥应单独存入组织受控的加密备份或密码保管库并验证恢复，不能附在交付包、日志或普通数据库备份目录中。恢复时必须恢复匹配的密钥和数据库；丢失主密钥时仅恢复数据库无法解密认证器。当前未实现主密钥在线轮换，不得直接用新随机密钥替换现用密钥。

V17 增加凭据表、认证标志和账号安全版本，并删除既有管理员会话；普通用户会话保留。全部管理员首次需本人绑定。部署前需可信 HTTPS、准确系统时间及运维恢复通道。启用 V17 后不得只回滚到不支持 MFA 的旧 JAR，旧代码会恢复密码单因素入口；应按维护窗口恢复成套版本与备份并处理恢复点后的业务数据。

## 超级管理员离线恢复

仅由拥有服务器数据库运维权限的负责人，在核实本人身份后执行。准备工具不连接数据库：

```powershell
.\tools\prepare-admin-mfa-recovery.ps1 -Phone '<目标管理员手机号>' -Reason '<操作负责人及身份核验记录>' -OutputDirectory './secrets/admin-mfa/recovery-YYYYMMDD-HHmmss'
```

新建受保护目录包含 `recovery.sql` 和 `one-time-reset-grant.txt`；SQL 只含凭证摘要。使用既有安全数据库连接运行 `psql -v ON_ERROR_STOP=1 -f <recovery.sql路径>`。脚本只允许匹配一位启用的超级管理员，在单事务内撤销其认证材料、会话并写入 `ADMIN_MFA_RESET` 审计，其他账号保持不变。审计的账号列是目标超级管理员，摘要中应明确实际操作负责人及核验记录。

将凭证当面交给本人；本人必须输入原密码和凭证，绑定新认证器并保存新恢复码。凭证不是密码重置功能。不要把 SQL 或凭证发布到普通工单；使用完成后按密钥保管流程清理临时副本。SQL 是新的重置操作，成功后不要重复执行同一文件。

## 隔离验证与交付

```powershell
.\tools\apache-maven-3.9.11\bin\mvn.cmd -B '-Dtest=AdminMfaCryptoTest,AdminMfaPostgresIntegrationTest' test
```

需要 Docker Desktop，使用一次性 PostgreSQL 16。覆盖 RFC 向量、密文篡改、账号绑定、缺失密钥、五岗位门禁、挑战过期与替换、锁定、并发消费、恢复码重放、改岗/停用/改密失效、换绑、人工与离线恢复、V16 数据迁移和账号限流。后台的 `tools/run-admin-role-qa.ps1` 验证真实 Edge 流程，报告内二维码和凭证仅属于已销毁的合成账号。

候选交付必须包含最新后台构建，打包时通过 `-Dadmin.frontend.directory=<已验收后台目录>` 指定，再用 `Dockerfile.runtime` 构建镜像。当前开发交付不会自动执行生产迁移、生成生产密钥或启动候选版本；真实密钥备份与管理员本人绑定仍需部署时完成。

算法依据：[RFC 6238](https://www.rfc-editor.org/rfc/rfc6238)。恢复与换绑设计参考 [OWASP MFA 指南](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html)。文件挂载行为参考 [Docker Compose Secret 文档](https://docs.docker.com/compose/how-tos/use-secrets/)。
