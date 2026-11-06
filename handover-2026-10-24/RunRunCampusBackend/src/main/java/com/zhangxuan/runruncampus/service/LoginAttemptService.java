package com.zhangxuan.runruncampus.service;

/** 登录失败计数与锁定业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface LoginAttemptService {

    /**
     * 判断该账号的失败次数是否已达到验证码阈值。
     *
     * @param phone 登录或目标账号手机号
     * @return 是否必须先完成图形验证码校验
     */
    boolean requiresCaptcha(String phone);

    /**
     * 记录未知手机号的临时登录失败次数。
     *
     * @param phone 登录或目标账号手机号
     */
    void recordUnknownFailure(String phone);

    /**
     * 清除手机号的临时登录失败计数。
     *
     * @param phone 登录或目标账号手机号
     */
    void clearTransientFailures(String phone);

    /**
     * 累计已知账号的登录失败，达到阈值时按原规则锁定。
     *
     * @param userId 目标用户标识
     */
    void recordFailure(Long userId);

    /**
     * 清除账号登录失败计数及相应锁定状态。
     *
     * @param userId 目标用户标识
     */
    void clearFailures(Long userId);
}
