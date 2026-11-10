package com.zhangxuan.runrunv2.mapper.idempotency;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 业务幂等持久化映射；事务、锁顺序及业务校验由原调用方保持。 */
@Mapper
public interface OrderIdempotencyMapper {

    /** 读取并锁定 runrun_v2.idempotency_requests 的匹配行；锁持有至调用方事务结束。 */
    List<Map<String, Object>> findRequestForUpdate(
            @Param("actor") Long actor, @Param("scope") String scope, @Param("key") String key);

    int insertRequest(
            @Param("actorId") Long actorId,
            @Param("scope") String scope,
            @Param("key") String key,
            @Param("requestHash") String requestHash);

    int completeRequest(
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("responseJson") String responseJson,
            @Param("actor") Long actor,
            @Param("scope") String scope,
            @Param("key") String key);
}
