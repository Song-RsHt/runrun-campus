# M01 身份运行时验收

日期：2026-10-27

结论：**通过，可建议合并到 main**。本验收只覆盖隔离环境和合成数据，不代表真实微信、真机、生产部署或前端联调已完成。

## 交付范围

- 微信登录票据、手机号验证、唯一绑定和会话签发/撤销。
- 新骑手登录；资质未批准时拒绝接单权限。
- 管理员受控建档、启停、二次确认、理由、审计和会话立即失效。
- 独立 `rrc_0003__identity_runtime.sql` 迁移及受保护回滚。
- 仅扫描 `runrun_v2`，不读取旧账号、不提供旧账号恢复入口。
- A05 管理员权限调整通过受控部署命令执行；普通管理员 Bearer 请求固定返回 `403 ADMIN_ACCESS_MANAGEMENT_FORBIDDEN`。

## 验证结果

|检查|结果|
|---|---|
|Maven v2 identity package|通过；IdentityIntegrationTest 20 项、WechatHttpGatewayTest 4 项，0 失败/错误|
|`verify-identity-runtime.ps1`|通过；8 项 PASS|
|`verify-m01.ps1`|通过；26 项 PASS|
|`verify-identity-package.ps1`|通过；6 项 PASS|
|`git diff --check main...HEAD`|通过（修复提交范围）|

测试使用 PostgreSQL 16 临时容器、回环地址和合成密钥；未连接生产数据库。

## 修复记录

- `9721079`：单会话退出的幂等摘要绑定当前会话；新增两会话共用幂等键回归测试，第二会话收到 `IDEMPOTENCY_KEY_CONFLICT` 且仍有效。
- `3c14658`：A02 新用户创建响应统一为 HTTP 201，与总接口契约的创建响应规则一致。

## 未实现/未验证

- 真实微信接口、真实手机号验证、真机联调。
- 小程序和管理端前端接入。
- 资质照片业务闭环、订单及后续 M02–M08 模块端到端联调。
- Spring/Flyway 生产 runner、生产迁移、真实支付和部署。

## 风险与合并建议

运行时代码位于旧后端目录下，但通过 `IdentityApplication`、`v2-identity` profile 和独立配置隔离；旧默认启动方式不代表新版运行时。建议只 cherry-pick 本验收对应的运行时提交链到集成 main，并在合并后重新执行三类脚本。
