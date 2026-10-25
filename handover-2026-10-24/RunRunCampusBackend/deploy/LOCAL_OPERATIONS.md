# 本地运维检查与备份恢复重试

适用环境：Windows、PowerShell 7.2+、Docker Desktop，以及本工程的 `compose.yml`。运行前保持 Docker Desktop 开启。检查脚本只读取状态，不启动、停止或重建容器，不修改数据库。

在后端工程目录执行：

```powershell
pwsh -NoProfile -File .\tools\check-local-operations.ps1
$LASTEXITCODE
```

脚本输出一个 JSON 对象，包含 UTC 检查时间 `checkedAt`、汇总状态 `healthy`、逐项 `checks`。全部通过退出 0，任一失败退出 1；单项失败后继续检查其他项。HTTP 请求限时 5 秒，Docker 命令限时 15 秒；超时同样视为失败。错误结果不回显 Docker 原始输出、数据库数据、密码或 Token。

| 检查名 | 通过条件 |
| --- | --- |
| `containers` | API、postgres、backup 三个服务均 running 且 healthy |
| `api` | 本机 5173 的 `/actuator/health` 返回 HTTP 200、`status=UP`，不跟随重定向 |
| `admin` | 本机 5173 首页返回 HTTP 200 且包含后台页面标题 |
| `database` | 现有本地数据库可执行 `SELECT 1` |
| `host_disk` | 系统盘和工程盘各自至少剩余 5 GiB，且剩余比例至少 10% |
| `database_disk` | PostgreSQL 容器数据目录所在文件系统满足同样空间阈值 |
| `backup` | 最新完成的归档非空，UTC 文件名时间不超过 26 小时且不超前 5 分钟，并能完整读取 |

这些阈值对应现有每日备份计划，允许 2 小时处理延迟。`.dump.tmp` 未完成文件不参与最新备份选择。备份检查使用文件名内的 UTC 生成时间，不使用复制文件时可能变化的修改时间。

完整读取使用 `pg_restore --file=/dev/null`，没有数据库连接目标，不会恢复或覆盖数据。`pg_restore --list` 仅列出归档目录，不能替代数据完整读取；完整读取本身仍不能替代临时数据库的真实恢复演练。[PostgreSQL pg_restore 官方说明](https://www.postgresql.org/docs/16/app-pgrestore.html)

页面检查只验证后台入口，不代表管理员登录和全部业务操作验收。容器磁盘值是虚拟文件系统容量；若 Docker 数据盘移到系统盘、工程盘之外，还需检查那块宿主机磁盘。数据库查询检查本地开发库，不代表生产 RDS 连接或备份状态。

## 备份失败的恢复行为

旧备份循环在失败后等待 24 小时，且不会清除既有成功标记。Docker Desktop 重启时若数据库尚未就绪，会出现“备份实际失败，容器仍显示健康”。

现在循环启动时清除旧标记，备份失败再次清除标记并在 60 秒后重试；成功后才按 24 小时等待。健康检查同时验证成功标记存在和时效，拒绝将来时间。Docker 按现有检查间隔和失败次数更新健康状态，因此 UI 可能有短暂延迟。

更新本机备份服务：

```powershell
docker compose config --quiet
docker compose up -d --no-deps backup
```

只重建 backup，不重建 API 或 postgres；既有备份仍保存在原目录，并沿用原有 7 天保留规则。需要立即手动备份时，继续使用 `tools/backup-now.ps1`。

## 回归测试

```powershell
pwsh -NoProfile -File .\tools\test-local-operations.ps1
docker run --rm --network none --mount "type=bind,source=$PWD\deploy,target=/source,readonly" postgres:16-alpine sh /source/tests/backup-loop-test.sh
```

PowerShell 测试使用故障替身，不会停服务或写数据库。Shell 测试必须在上述一次性容器中运行，其中 `/scripts`、`/tmp` 都是测试夹具；它不挂载真实数据库或备份目录。

本轮交付单次检查能力和本地备份重试修复。尚未配置定时检查或消息通知，也未部署到生产 ECS；后续可由明确选定的调度和告警系统根据 JSON、退出码接入通知。
