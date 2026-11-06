package com.zhangxuan.runruncampus.service;














/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface LoginAttemptService {

    boolean requiresCaptcha(String phone);

    void recordUnknownFailure(String phone);

    void clearTransientFailures(String phone);

    void recordFailure(Long userId);

    void clearFailures(Long userId);
}
