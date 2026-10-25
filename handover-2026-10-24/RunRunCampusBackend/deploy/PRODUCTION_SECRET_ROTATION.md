# 生产凭据轮换

本流程覆盖 RDS 应用账号密码与管理员账号密码。Android 签名密码已另行处理；OSS 和移动推送使用 ECS RAM 角色临时凭据，不在这里生成或替换长期 AccessKey。

## 准备材料

在 Windows 的 PowerShell 7.2 或更高版本、后端工程目录中执行：

```powershell
.\tools\prepare-production-secret-rotation.ps1
```

默认源目录为 `secrets/production`，输出为其中新的 `rotations/时间戳` 目录。可通过 `-SourceDirectory` 指定现有两个 Secret 文件所在目录，通过 `-OutputDirectory` 指定新的输出目录。已有输出目录一律拒绝使用。默认输出受现有 `.gitignore` 保护；不要把自定义输出目录提交版本控制。

输出目录仅允许当前用户、SYSTEM 和本机管理员访问：

| 文件 | 用途 |
| --- | --- |
| `database-password.txt` | 32 字符随机候选数据库密码 |
| `admin-password.txt` | 56 字符独立随机候选管理员密码 |
| `before/database-password.txt` | 源数据库密码文件的原始字节备份 |
| `before/admin-password.txt` | 源管理员 Secret 文件的原始字节备份 |
| `rotation.json` | 全部准备检查成功后才写入，状态为 `prepared_not_applied` |

没有 `rotation.json` 的目录视为准备失败；保留受限文件用于排查，重新选择新目录准备。该工具不连接数据库、API 或云服务。`before` 只是本机文件快照，不证明其内容仍与线上密码一致。

## 切换前核对

1. 确认线上正在使用的发布目录、Compose 项目和 API 镜像，安排允许短暂停服的时间。不要将本机全部代码或镜像顺带部署。
2. 核对线上 `.env.production` 中的两个 Secret 路径，以及 RDS 主机、数据库和账号；将本机源文件与线上挂载源文件在受保护环境中比对。发现差异应先更新已验证的恢复材料，不能直接用旧本机快照回退线上。
3. 建立启用 `sslmode=verify-full` 且使用正确 CA 的数据库连接，确认当前密码可建立新连接。确认当前管理员能登录，并能通过正常认证接口修改自身密码。
4. 单独保存线上当前 Secret、文件属主/权限和 Compose 配置的受限副本。保留恢复数据库连接；管理员初始化 Secret 可能已不等于账号当前密码，应以实际登录凭据为准。

## 数据库密码切换

1. 在维护时间暂停 API，避免应用在文件与数据库密码短暂不一致时处理请求。保留用于恢复的数据库管理连接。
2. 使用受保护的交互式 `psql` 会话执行 `\password runrun_app`，输入候选数据库密码；账号若不同，以已核对的实际账号为准。不要将明文密码拼进 SQL、命令行或聊天。PostgreSQL 官方建议使用 `\password` 避免明文进入客户端历史或服务端日志：[ALTER ROLE 文档](https://www.postgresql.org/docs/16/sql-alterrole.html)。
3. 用候选密码建立独立的新 TLS 连接并执行 `SELECT 1`；用旧密码新建连接应认证失败。原有连接仍可用不能作为旧密码仍有效的判断依据。
4. 将线上数据库 Secret 切换为候选文件，沿用已验收的容器可读权限；同时同步本机对应 Secret。保留原文件恢复副本。限制普通账号读取，确保 API 的非 root 用户仍可读取挂载文件。
5. 在已核对的线上发布目录重建现有 API 容器，沿用当前镜像：

```sh
docker compose --env-file .env.production -f compose.prod.yml up -d --no-deps --force-recreate --no-build api
```

6. 验证容器健康、`/actuator/health`、管理员登录及需要查询数据库的列表接口；确认日志没有认证失败、Secret 读取失败或密码内容。

Compose 文件型 Secret 是独立文件挂载，应用在启动时读取配置；本项目切换后应重建容器并检查新连接，不能只依赖文件替换或连接池中的旧连接。[Compose Secret 文档](https://docs.docker.com/compose/how-tos/use-secrets/)、[重建选项](https://docs.docker.com/reference/cli/docker/compose/up/)。

## 管理员密码切换

数据库切换验收后单独执行，避免同时改变两处认证导致故障难以定位。

1. 通过已验证的 HTTPS 或 SSH 本机隧道，用当前管理员凭据获取会话。
2. 调用现有 `PUT /api/v1/admin/accounts/me/password`，使用 `currentPassword` 和候选 `newPassword`。密码及 Authorization 不得出现在命令参数或日志中。
3. 该接口成功时会在同一事务中修改密码、删除该账号全部会话并记录 `ADMIN_PASSWORD_CHANGED` 审计。验证原 Token 返回 401，新密码可以登录，旧密码不能登录。
4. 成功后同步线上与本机管理员 Secret，以保持恢复材料一致；再重建 API 并重复新密码登录检查。`AdminAccountInitializer` 仅创建不存在的管理员，不会替已有账号同步 Secret 密码，不能跳过改密接口。

## 故障恢复

- 数据库尚未改密：停止本次操作即可，现用文件和服务无需变更。
- 数据库已改密但应用无法恢复：使用保留的管理连接或新密码连接，将数据库密码改回已验证的原值，再恢复匹配的数据库 Secret，重建 API 并复验健康。只恢复文件会继续造成密码不一致。
- 管理员已改密：通过正常认证的改密接口设置已验证的恢复密码，随后同步管理员 Secret；如果已无法登录，使用另一个有管理员管理权限的账号走既有重置流程。不要通过重启应用覆盖数据库中的管理员密码。
- 不回退数据库结构，不恢复整库，不更换 APK 签名。恢复后仍须验证账号、会话和审计结果。
- 完成切换后记录实际执行时间与验收结果；准备状态标记不能作为“线上已完成”的证据。历史副本中的旧密码仍可用于解锁对应旧材料，应持续限制访问。

## 本地验证

```powershell
.\tools\test-production-secret-rotation.ps1
.\tools\test-production-config.ps1
.\tools\apache-maven-3.9.11\bin\mvn.cmd '-Dtest=AdminAccountServiceTest,ProductionConfigurationValidatorTest' test
```

轮换脚本测试使用独立临时目录和虚构凭据，覆盖禁止覆盖、半对文件保护、候选格式/权限、日志不泄密、原文件不变、备份一致及错误输入。测试不会连接线上服务。
