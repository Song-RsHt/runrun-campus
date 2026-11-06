package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.model.AccessResult;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface DeploymentIdentityService {

    String bootstrap(String reason, String confirmation, String requestId);

    AccessResult provisionAdmin(long actorId, long userId, long expectedVersion, String reason, String confirmation, String requestId);

    AccessResult changeAccess(long actorId, long userId, boolean enabled, long expectedVersion, String reason, String confirmation, String requestId);
}
