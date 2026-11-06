package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.model.AdminMfaVerification;
import com.zhangxuan.runruncampus.model.SessionMetadata;
import com.zhangxuan.runruncampus.vo.AdminMfaChallengeVO;

/** 管理员双因素认证业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface AdminMfaService {

    /**
     * 校验管理员账号密码，签发双因素认证或绑定挑战。
     *
     * @param phone 登录或目标账号手机号
     * @param password 本次密码校验或设置所需的原文，不得记录
     * @return 管理员双因素认证挑战
     */
    AdminMfaChallengeVO begin(String phone, String password);

    /**
     * 校验管理员双因素认证授权或凭证，拒绝过期、篡改和重复消费。
     *
     * <p>账号锁先于凭据状态读取；一次性授权和挑战消费保留原并发及不回滚规则。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param code 当前认证器动态口令或一次性恢复授权
     * @return 管理员双因素认证核验结果
     */
    AdminMfaVerification verify(String token, String code);

    /**
     * 校验管理员双因素认证授权或凭证，拒绝过期、篡改和重复消费。
     *
     * <p>账号锁先于凭据状态读取；一次性授权和挑战消费保留原并发及不回滚规则。
     *
     * @param token 当前访问凭证；仅用于校验，不得记录原值
     * @param code 当前认证器动态口令或一次性恢复授权
     * @param metadata 会话设备及客户端信息
     * @return 管理员双因素认证核验结果
     */
    AdminMfaVerification verify(String token, String code, SessionMetadata metadata);

    /**
     * 验证当前凭据后生成认证器替换挑战。
     *
     * @param authenticated 已完成身份校验的管理员
     * @param password 本次密码校验或设置所需的原文，不得记录
     * @param code 当前认证器动态口令或一次性恢复授权
     * @return 管理员双因素认证挑战
     */
    AdminMfaChallengeVO replace(AppUser authenticated, String password, String code);

    /**
     * 受控重置目标管理员认证器，并保留理由与审计。
     *
     * @param authenticated 已完成身份校验的管理员
     * @param targetId 目标业务记录标识
     * @param reason 业务操作理由，按原有长度和必填规则校验
     * @return 目标管理员重置后的认证器状态
     */
    String reset(AppUser authenticated, Long targetId, String reason);

    /**
     * 查询目标管理员的双因素绑定状态。
     *
     * @param target 被查询双因素状态的目标管理员
     * @return 目标管理员当前的认证器状态
     */
    String resetState(AppUser target);
}
