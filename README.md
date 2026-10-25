# RunRunCampus

代码提交仓库：[Song-RsHt/runrun-campus](https://github.com/Song-RsHt/runrun-campus)（私有）；Git远程名称为 origin，默认分支为 main。

跑跑校园（跑跑代取）阶段一开发仓库。现有交接代码与目标规则分开管理；规则文档不代表功能已实现。

## 工程位置

| 内容 | 仓库相对路径 |
|---|---|
| 当前规则入口 | [AGENTS.md](AGENTS.md) |
| 业务基线 | [阶段一业务基线](docs/阶段一业务基线.md) |
| 技术契约 | [数据模型与接口契约](docs/数据模型与接口契约.md) |
| 本地验证 | [本地开发与验证](docs/本地开发与验证.md) |
| 后端 | [RunRunCampusBackend](handover-2026-10-24/RunRunCampusBackend/) |
| 管理端 | [RunRunCampusAdmin](handover-2026-10-24/RunRunCampusAdmin/) |
| 小程序 | [RunRunCampusMiniProgram](RunRunCampusMiniProgram/) |

本机项目根目录：D:/biancheng/RunRunCampus。原中文根目录及中文交接目录已改为上述英文路径。中文业务名称、历史资料名称保留，不修改已执行数据库迁移。

## 开发与提交

统一从根目录管理三个工程与规则；main保存审查通过的集成版本，模块开发使用独立分支。执行命令及阶段一验收以本地验证文档为准。

生产v1/v2停止新建单，真实资金属于阶段二。不得将模拟支付、本地构建或文档检查描述为生产验收。

## 保留在本机的交接材料

生产运行制品证据、品牌与合规参考资料、随附Maven发行包、本地密钥、上传数据和备份由.gitignore排除，未删除。原SHA256SUMS.txt是交接时历史清单，只覆盖其原交接范围，不能当成当前Git检出的完整性证明；缺少被排除的本地制品时不能宣称历史核验全部通过。

克隆后按文档安装JDK17、Maven、pnpm和Docker，外部微信能力及存储配置须受控提供；不能依赖本机未提交材料认定新环境可运行。
