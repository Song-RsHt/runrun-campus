package com.zhangxuan.runrunv2.service.identity;

import com.zhangxuan.runrunv2.model.identity.AccessResult;

/** 主机受控管理员开通与启停业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface DeploymentIdentityService {

    /**
     * 通过受控主机命令初始化首个管理员开通授权。
     *
     * <p>仅允许受控主机命令调用，不接受普通管理员 HTTP 凭证作为部署身份。
     *
     * @param reason 业务操作理由，按原有长度和必填规则校验
     * @param confirmation 受控操作的二次确认文本
     * @param requestId 本次操作的幂等请求标识
     * @return 受控初始化生成的一次性开通授权
     */
    String bootstrap(String reason, String confirmation, String requestId);

    /**
     * 为新系统账号开通管理员资格并记录审计。
     *
     * <p>必须使用新系统经微信核验的账号；幂等、版本、会话撤销和审计在同一事务完成。
     *
     * @param actorId 执行受控操作的账号标识
     * @param userId 目标用户标识
     * @param expectedVersion 调用方看到的并发版本，更新必须与当前版本一致
     * @param reason 业务操作理由，按原有长度和必填规则校验
     * @param confirmation 受控操作的二次确认文本
     * @param requestId 本次操作的幂等请求标识
     * @return 管理员访问授权结果
     */
    AccessResult provisionAdmin(
            long actorId,
            long userId,
            long expectedVersion,
            String reason,
            String confirmation,
            String requestId);

    /**
     * 按版本变更管理员启用状态，原子提交撤销与审计。
     *
     * <p>按既有二次确认及负责人保护规则校验，停用不能绕过现有审核负责人约束。
     *
     * @param actorId 执行受控操作的账号标识
     * @param userId 目标用户标识
     * @param enabled 目标管理员启用状态
     * @param expectedVersion 调用方看到的并发版本，更新必须与当前版本一致
     * @param reason 业务操作理由，按原有长度和必填规则校验
     * @param confirmation 受控操作的二次确认文本
     * @param requestId 本次操作的幂等请求标识
     * @return 管理员访问授权结果
     */
    AccessResult changeAccess(
            long actorId,
            long userId,
            boolean enabled,
            long expectedVersion,
            String reason,
            String confirmation,
            String requestId);
}
