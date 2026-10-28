# M07 验收状态（订单回归已复验）

日期：2026-10-28

订单请求字段拒绝逻辑已修复：v2 订单、报价和服务配置入口现在读取原始 JSON，按白名单校验字段，并拒绝小数配置/金额字段。`OrderRequestJsonContractTest` 通过，后端编译通过。

Docker 已恢复，`OrderIntegrationTest#clientAmountsSnapshotsVersionsAndUnknownFieldsAreRejected` 已在真实 PostgreSQL 16 Testcontainers 环境通过（1/1）。

仍需运行完整 M07 QA 集：

```text
mvn test -Dtest=OrderIntegrationTest
mvn test -Dtest=CourierCertificationV2DatabaseE2ETest
powershell -ExecutionPolicy Bypass -NoProfile -File server/db/tools/verify-m07.ps1
```

其中 `OrderIntegrationTest#clientAmountsSnapshotsVersionsAndUnknownFieldsAreRejected` 必须确认所有客户端金额、快照和版本字段均返回 400，并确认 `v2_orders` 与 `idempotency_requests` 没有副作用；资质数据库测试必须确认隔离 `runrun_v2` 生命周期；迁移脚本必须确认 0006 重复执行失败。

在上述测试实际完成并记录结果前，M07 保持阻塞，不执行合并审核。
