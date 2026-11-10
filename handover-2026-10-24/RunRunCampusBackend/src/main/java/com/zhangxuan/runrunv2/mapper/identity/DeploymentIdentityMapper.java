package com.zhangxuan.runrunv2.mapper.identity;

import com.zhangxuan.runrunv2.config.SingleRowRequired;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 身份与会话持久化映射；事务、锁顺序及业务校验由原调用方保持。 */
@Mapper
public interface DeploymentIdentityMapper {

    @SingleRowRequired
    Boolean deploymentIdentityExists();

    @SingleRowRequired
    Long insertDeploymentIdentity();

    @SingleRowRequired
    Boolean hasWechatBinding(@Param("userId") long userId);

    /** 按标识和原有版本条件更新 runrun_v2.app_users；返回影响行数，由业务层判定并发冲突。 */
    int promoteToDisabledAdmin(@Param("next") long next, @Param("userId") long userId);

    @SingleRowRequired
    Boolean settingsInstalled();

    @SingleRowRequired
    Boolean isReviewOwner(@Param("userId") long userId);

    /** 按标识和原有版本条件更新 runrun_v2.app_users；返回影响行数，由业务层判定并发冲突。 */
    int updateAdminAccess(
            @Param("enabled") boolean enabled,
            @Param("next") long next,
            @Param("userId") long userId);

    int invalidateAdminSessions(@Param("id") long id);

    int insertAccessAudit(
            @Param("administratorId") long administratorId,
            @Param("action") String action,
            @Param("targetId") long targetId,
            @Param("requestId") String requestId,
            @Param("reason") String reason,
            @Param("metadata") String metadata);

    List<Map<String, Object>> findAccessAudit(
            @Param("actor") long actor,
            @Param("action") String action,
            @Param("requestId") String requestId);
}
