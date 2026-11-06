# 苍穹外卖风格后端返工

基线：`030924f625bb1c9403062e2601fdb09d27189551`；分支：`refactor/backend-sky-style`；独立 worktree：`D:/biancheng/RunRunCampus-worktrees/rrc-refactor-backend-sky`。起始工作区干净。参考工程只读，不修改 main、m09/integration 或 feature/m01-identity。

## 参考核对与实施前差距

已实际阅读参考工程的 EmployeeController、EmployeeService、EmployeeServiceImpl、EmployeeMapper，以及 AddressBook 的 Controller/Service/Impl 和 sky-pojo 的 DTO、VO、Entity 示例。采用其技术层目录、业务接口与实现分离、四空格、中文职责说明和业务步骤表达；采用构造器注入，保留当前 record 及 Jackson 行为。

参考工程的 Controller 仍有 admin/user 子包，本项目按用户明确要求进一步集中到各自 controller 包。参考源码出现的字段注入、MD5、默认密码、登录参数日志、Lombok/MyBatis、Result 包装及空 Javadoc 标签均不照搬。

现有差距：业务子包分散；47 个业务服务尚需接口/实现分离；三个 v2 Models 容器及多处 legacy Service 仍内嵌对外响应；JPA 实体与内部值均在 model；四空格、显式 import、控制块花括号及中文说明未统一。上传 Service 的 MultipartFile 与 legacy 服务的 HTTP 错误类型需要边界适配，保持原来的鉴权、文件读取、失败状态、响应正文及事务时序。

## 目录与全类迁移计划

重构前：`controller/<业务>`、`service/<业务>`、`mapper/<业务>`、`repository/<业务>`、`dto/request|response/<业务>`、`model/<业务>`、`enums/<业务>`；两个应用均独立。

目标（在 `com.zhangxuan.runrunv2` 和 `com.zhangxuan.runruncampus` 下分别建立，不创建空目录）：

```text
应用启动类
controller/          HTTP 入口
service/             业务接口
service/impl/        业务实现与事务
mapper/              JDBC SQL 与映射
repository/          legacy JPA Repository
 dto/                请求（实际目录名无前导空格）
vo/                  响应
entity/              JPA 持久化实体
model/               内部投影、快照和值
 enums/              枚举（实际目录名无前导空格）
constant/            真正常量
exception/           业务失败
handler/             HTTP 异常映射
security/、config/   安全与运行配置
integration/         渠道、存储和工作器，按需要隔离子包
task/                薄调度入口
bootstrap/           受控初始化和启动校验
```

实施前已生成完整的 384 个顶层类迁移映射、47 组 Service 接口/Impl 清单及 76 个内嵌类型的抽取计划，见 [迁移清单](backend-sky-style/migration-plan.md) 和 [机器映射](backend-sky-style/class-mapping.json)。三个同应用 DTO 重名组将使用明确的账号/用户、订单/信用、MFA 名称解决，不增加业务子包。

后续按身份、通知、订单支付履约、资质证据、legacy 分批、类型复核、风格、验证分阶段提交。此文件当前记录实施前分析；最终目录、抽查、行为证据、命令结果、风险和回滚说明将在实施及验证后补齐，不将计划写成通过。
