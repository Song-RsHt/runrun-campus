# 隔离环境订单写入压力测试

使用恢复演练的数据库副本与内部 Docker 网络，目标固定为 `http://api:8080`。当前服务与云端数据不参与压测。需要已支持 Flyway V16 的候选镜像，以及本地 `grafana/k6:2.2.0` 镜像。

## 执行

在后端目录执行，报告路径必须尚不存在：

```powershell
$previous = docker inspect runruncampusbackend-api-1 --format '{{.Image}}'
.\tools\rehearse-recovery.ps1 `
  -PreviousImage $previous `
  -CandidateImage runrun-campus-backend:order-concurrency-20260902 `
  -WriteLoadTest `
  -ReportFile .\target\order-write-load.json
```

`-WriteLoadTest` 与后台读取压测 `-LoadTest` 互斥。可用 `-BackupFile` 指定 `.dump`。镜像固定到开始执行时的 ID，不会拉取镜像或更改业务限流。后端修改后必须先测试、打包并构建新的候选镜像。

## 流程和负载

每次业务流程并发提交两份相同内容、相同标识的订单创建请求；只要收到一个成功结果，再让两名不同骑手同时抢这笔订单。因此每个流程发出 2～4 次 HTTP 写入，而不是一次。普通订单、优惠券订单和月卡订单轮换执行。

| 阶段 | 开始 | 持续 | 目标业务流程/秒 | 计划流程数 |
| --- | ---: | ---: | ---: | ---: |
| warmup | 0 秒 | 10 秒 | 1 | 10 |
| steady | 12 秒 | 30 秒 | 2 | 60 |
| burst | 45 秒 | 30 秒 | 20 | 600 |
| recovery | 140 秒 | 20 秒 | 2 | 40 |

高峰结束后留出 65 秒恢复额度。保留现有令牌桶限流：每个会话每分钟 60 次写入、单一来源 IP 每分钟 600 次写入。生成器使用一个来源 IP。高峰故意触发限流，结果不能当作无限流吞吐能力。

采用固定到达率调度，每个阶段预分配 10 个虚拟用户、最多 30 个；要求无丢弃迭代，流程完成数不少于计划值。正常阶段不得出现 429；高峰必须出现带正确 `Retry-After: 60` 和 `Cache-Control: no-store` 的 429。创建和接单成功响应分别要求 P95 < 500 ms，高峰放宽到 1,000 ms；这些是本地验收阈值。

资源限制：API 2 CPU／1 GiB、堆上限为容器内存 60%；PostgreSQL 1 CPU／1 GiB，数据库位于 tmpfs；k6 1 CPU／256 MiB。约每 5 秒采样资源，采样峰值可能遗漏短暂尖峰。

## 数据及响应校验

- 同一流程两个创建响应若成功，必须返回同一订单 ID。
- 成功响应检查订单号、取件码、服务类型、费用、优惠券、月卡、状态与骑手；401、500、超时、错误内容均记为异常。
- 接单只能有一个赢家。只允许预期的“订单已被其他骑手接走”409；两个成功、两个冲突、没有赢家却出现冲突，都判为不一致。
- 高峰可能只创建成功但接单被限流；这类待接单订单单独保留在数据库核对中，不算已接单。
- 数据库订单数、已接单数、券订单数、月卡订单数必须与生成器记录一致。
- 逐单核对创建事件、接单事件、事件操作者、通知接收者、费用和骑手；逐券和逐月卡核对消耗次数。汇总相等不能替代逐条检查。

夹具包含 40 名用户、40 名已审核骑手、80 个临时会话、2,100 张单次券与 40 张合成月卡。月卡各预置 10,000 次，仅用于压力测试，并非真实套餐。夹具使用预留负数 ID，并在写入前检查冲突；不会改动原有账号认证信息。

数据核对后删除合成数据，并比较压测前后所有表的行数、内容摘要、约束及迁移记录。业务序列会前进，不回拨；原有行应完全一致。最后按既有规则验证旧版回滚，清理全部演练资源。任一阈值、数据核对或清理失败，整个演练退出 1。

## 报告

输出位于主报告路径追加 `.load` 的目录：

- `k6-summary.json`：阶段配置、Counter／Rate／Trend 与阈值。
- `database-audit.json`：数据库落库及一致性核对计数。
- `resources.json`：API、数据库、生成器资源样本。
- `result.json`：生成器退出码、数据库核对及夹具清理状态。

主报告包含 `writeExistingDataUnchanged`、回滚、备份完整性及容器网络清理结果。报告不输出真实用户行内容、密码或会话令牌。

`write_orders_created` 每个流程最多计一次，避免把成功重试算成新订单；`write_orders_accepted` 记录真正接单成功数。`probe_successes` 包含成功重试，不能直接当作新订单吞吐量。阶段业务吞吐量应以对应阶段订单计数除以阶段持续秒数；整个运行时长还包含间隔与恢复等待。

## 工具回归测试

```powershell
.\tools\test-recovery-policy.ps1
.\tools\test-load-isolation.ps1
.\tools\test-write-audit.ps1
docker run --rm --network none --mount "type=bind,source=$PWD\deploy,target=/scripts,readonly" grafana/k6:2.2.0 run --quiet --no-usage-report /scripts/tests/order-write-validation-test.js
docker run --rm --network none --mount "type=bind,source=$PWD\deploy,target=/scripts,readonly" grafana/k6:2.2.0 run --quiet --no-usage-report /scripts/tests/load-validation-test.js
```

这是一组本机小数据量基线，不覆盖生产容量、长期稳定性、大量历史订单、真实 RDS、公网、TLS、上传、登录或移动推送。
