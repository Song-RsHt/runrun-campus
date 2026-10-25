# 隔离环境后台读取压力测试

本轮建立本机性能基线，使用实际备份副本和既有后端镜像。压测复用恢复演练的隔离数据库与内部 Docker 网络，固定请求 `http://api:8080`；不接受公网目标地址，不访问现有后台端口。

## 执行

准备 Docker Desktop、两个不同版本的本地后端镜像，以及 k6：

```powershell
docker pull grafana/k6:2.2.0
$previous = docker inspect runruncampusbackend-api-1 --format '{{.Image}}'
pwsh -NoProfile -File .\tools\rehearse-recovery.ps1 `
  -PreviousImage $previous `
  -CandidateImage runrun-campus-backend:recovery-candidate-20260902 `
  -LoadTest
```

需要选择特定备份或保存位置时，沿用恢复脚本的 `-BackupFile`、`-ReportFile` 参数。脚本固定当前镜像 ID，生成器也记录镜像 ID；不会在运行中拉取依赖。后续后端修改需先测试并构建新的候选镜像，再指定该镜像。

压力测试文件输出在主报告路径后追加 `.load` 的目录：

- `k6-summary.json`：各阶段设定、指标及阈值结果，不包含响应内容或 Token。
- `resources.json`：约每 5 秒采样的 API、数据库、生成器 CPU/内存与进程数。
- `result.json`：生成器退出码、采样数、临时会话和生成器清理状态。

主报告仍包含升级、回滚、原备份未变和清理结果，并增加 `loadDataUnchanged`。任一压测阈值失败，整个演练退出 1，即使回滚成功也不会标成通过。

## 负载与验收条件

使用 k6 的固定到达率调度，每次迭代发出一个请求。目标速率不会因请求变慢自动下降，调度不足会记录 `dropped_iterations` 并导致验收失败。[k6 到达率说明](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/)

| 阶段 | 开始时间 | 持续 | 目标速率 | 计划请求数 | 判定 |
| --- | ---: | ---: | ---: | ---: | --- |
| warmup | 0 秒 | 10 秒 | 5 次/秒 | 50 | 仅预热，不设延迟门槛 |
| steady | 12 秒 | 30 秒 | 15 次/秒 | 450 | 无异常、无 429，成功请求 P95 < 500 ms |
| burst | 45 秒 | 30 秒 | 80 次/秒 | 2,400 | 必须触发正确 429，无其他异常，成功请求 P95 < 1,000 ms |
| recovery | 80 秒 | 20 秒 | 10 次/秒 | 200 | 无异常、无 429，成功请求 P95 < 500 ms |

全部阶段都要求完成计划请求数，`dropped_iterations=0`。这组延迟阈值是本轮本地验收条件，尚不是承诺给生产用户的 SLA。阈值未通过时 k6 会返回非零退出码。[k6 阈值说明](https://grafana.com/docs/k6/latest/using-k6/thresholds/)

请求平均轮换：运营总览、订单列表、骑手列表、用户列表；列表 `size=20`。20 个合成会话映射到副本中同一个可用超级管理员，不代表 20 个真实不同岗位用户。每个阶段预分配 20 个虚拟用户，最多 40 个。

保留应用限流：读取每个凭据 180 次/分钟、每个来源 IP 1,200 次/分钟，并按令牌桶逐步补充额度。所有生成器请求使用同一来源 IP。压力阶段故意超过该限制，因此必须分别看业务成功请求和被限流请求。

- `probe_requests`：所有已完成请求。
- `probe_successes`：HTTP 200 且响应结构正确。
- `probe_limited`：仅压力阶段、HTTP 429 且 `Retry-After: 60` 与 `Cache-Control: no-store` 正确。
- `probe_unexpected`：其他状态、超时、错误响应结构、正常阶段的 429 或限流响应头缺失。
- `probe_success_latency`：仅成功业务请求的延迟；不将快速拒绝的 429 混入。

报告中的 k6 Counter `rate` 可能按全程时长计算。比较阶段吞吐量时，应使用对应阶段的成功请求数除以该阶段持续秒数。`http_req_duration` 不包括建立连接的时间；这是 HTTP 请求发送、等待和接收的耗时。[k6 指标定义](https://grafana.com/docs/k6/latest/using-k6/metrics/reference/)

## 资源与数据边界

- API：2 CPU、1 GiB 内存，JVM 最大堆按容器内存 60% 设置。
- PostgreSQL：1 CPU、1 GiB 内存，恢复数据库使用 tmpfs。
- k6：1 CPU、256 MiB 内存。
- Docker CPU 百分比按单核计，200% 约等于占满两个核；采样只能说明观测到的峰值，可能漏掉短暂尖峰。
- 生成器不接入云端服务。只读挂载脚本，报告目录可写；没有公开宿主机端口。
- 合成会话仅写入隔离副本，使用负数 ID，不推进业务序列；压测结束后删除并比较数据库完整清单。
- 压测失败后仍继续原有回滚与清理流程。正常结束时演练容器与网络被删除，现有后台容器不重建。

本轮只测小数据量的后台读取与限流恢复，不包含下单/抢单等写入、上传、真实密码登录、TLS、公网链路、RDS、OSS、推送或历史前端版本。不能据此给出生产最大容量或大规模用户并发承诺。

## 脚本测试

```powershell
pwsh -NoProfile -File .\tools\test-recovery-policy.ps1
pwsh -NoProfile -File .\tools\test-load-isolation.ps1
docker run --rm --network none --mount "type=bind,source=$PWD\deploy,target=/scripts,readonly" grafana/k6:2.2.0 run --quiet --no-usage-report /scripts/tests/load-validation-test.js
```

覆盖 9 项回滚与模式选择策略、6 项读写隔离保护、11 项读取响应分类。隔离保护会拒绝其他任务的容器、其他任务的网络和非内部网络。

下单、重复重试与抢单的持续写入压测使用独立参数 `-WriteLoadTest`，见 [订单写入压力测试](ORDER_WRITE_LOAD_TEST.md)。
