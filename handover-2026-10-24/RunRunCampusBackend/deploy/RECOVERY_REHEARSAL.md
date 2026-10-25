# 数据库恢复与后端版本回滚演练

`tools/rehearse-recovery.ps1` 在本机新建隔离 PostgreSQL 和应用容器，验证备份可恢复、候选版本可运行、旧版可回退。它不会恢复现有业务数据库，也不会更新当前 API、后台或生产 ECS。

## 执行

要求：PowerShell 7.2+、Docker Desktop 运行、已下载的 `postgres:16-alpine`、已构建的两个不同后端镜像，以及现有 `RunRunCampusAdmin/dist`。备份中应包含可用的超级管理员，供隔离副本内的授权查询验收。

在后端工程目录执行：

```powershell
$previous = docker inspect runruncampusbackend-api-1 --format '{{.Image}}'
pwsh -NoProfile -File .\tools\rehearse-recovery.ps1 `
  -PreviousImage $previous `
  -CandidateImage runrun-campus-backend:recovery-candidate-20260902
```

默认选择 `backups` 下按 UTC 文件名排序的最新完成 `.dump`。也可显式指定 `-BackupFile`；脚本不把旧备份自动认定为最新可恢复点。镜像名在开始时解析并固定为不可变的镜像 ID，演练时不会拉取镜像。

默认报告保存在 `target/recovery-运行ID.json`；可用 `-ReportFile` 指定新文件，已存在的文件不会覆盖。正常完成退出码为 0，任一验证失败或清理失败退出码为 1。候选版本启动失败但回滚成功时，仍退出 1，报告标为 `candidate_failed_rollback_verified`。

本轮候选镜像包含通过测试的最新 JAR，仅用于本地演练。它复用本机已有运行镜像，不代表新一轮生产镜像漏洞扫描已完成。

可选 `-LoadTest` 在候选启动后执行 [隔离压力测试](LOAD_TEST.md)，结束后继续原有回滚、数据库摘要比对和清理。普通恢复演练不启用压测；压测启用时还会限制演练容器 CPU/内存，并要求本机已有 `grafana/k6:2.2.0` 镜像。

## 演练流程

1. 建立唯一命名、带所有者标签的 Docker 内部网络；不映射宿主机端口。
2. 新建 PostgreSQL 16 容器，数据库放在 1 GiB tmpfs；仅此隔离实例启用 trust 认证，不读取生产凭据。
3. 复制所选备份到一次性容器，用 `pg_restore --single-transaction --exit-on-error --no-owner --no-acl` 恢复新库 `recovery`。恢复是单事务，错误会导致失败。[PostgreSQL 官方说明](https://www.postgresql.org/docs/16/app-pgrestore.html)
4. 校验必要表、约束和 Flyway 记录，记录每张表的行数与数据摘要、序列值、结构摘要。
5. 启动旧版，通过健康接口及管理后台首页、总览、订单、骑手、用户查询，再停止该演练应用并生成升级前快照。
6. 启动候选版本并执行同样检查，随后再次采集数据库清单。
7. 若结构摘要及迁移记录均未变化，验证只退回旧镜像；若发生变化或无法取得完整清单，则将升级前快照恢复到另一个新库 `rollback`，再验证旧版。
8. 对比回滚后的完整清单。仅换镜像时应与候选运行后状态相同；恢复快照时应与升级前状态相同。
9. 无论成功失败都清理本次容器与网络。删除前核对所有者标签，只删除本次获取的资源 ID，并核对原备份 SHA-256 未变。

内部网络提供与外部网络的隔离。[Docker 网络说明](https://docs.docker.com/engine/network/)。应用沿用本机部署方式，只读挂载当前后台静态构建；此演练验证后端版本与数据库组合，不切换历史前端版本。

查询验收在恢复副本中临时插入会话，使用保留的负数 ID，不推进业务序列，结束后删除。不会读取用户密码或现有 Token；它验证授权读取，不代表密码登录或所有角色、写操作的端到端验收。

## 回滚边界

- 结构、迁移摘要一致只是允许进一步演练的条件，不能替代对业务语义兼容性的评审。
- **恢复升级前快照会丢失该快照之后的写入。** 真实上线前必须明确停写窗口和数据补偿方案；本脚本只对隔离副本执行这一动作。
- 本地实测从 V12 升级到 V15 后，需要“旧镜像＋升级前数据库快照”路径，不能把仅切换镜像视为已验收的回退方案。
- 只验证数据库与后端，照片文件、OSS、推送、RDS 权限/TLS、生产网络与真实流量均不在本次演练范围。
- 小型本地备份每条 Docker 命令限时 15 秒，数据库就绪最多尝试 30 次，应用就绪等待最多 90 秒。超过资源或时间限制会失败，不适用于直接验收大型生产备份。
- 通常退出会自动清理；强制结束 PowerShell 或机器断电可能留下资源，可按报告中的 `runId` 检查 `runrun.recovery.id` 标签，不要执行全局 Docker 清理。
- tmpfs 停止后不保留运行数据，但宿主机交换区可能落盘，不能将其当作数据擦除保证。[Docker tmpfs 说明](https://docs.docker.com/engine/storage/tmpfs/)

## 测试

```powershell
pwsh -NoProfile -File .\tools\test-recovery-policy.ps1
```

8 项检查覆盖兼容版本、结构变化、数据迁移、迁移校验值变化、缺失/空证据、资源所有权保护。

本轮另外执行了三个真实隔离场景：正常升级并恢复快照回退、候选应用退出后只回退镜像、截断备份恢复失败。它们的验收报告与当前后台健康检查一起存放在工作区 `deploy-artifacts/recovery-drill-20260902`。
