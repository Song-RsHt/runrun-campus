package com.zhangxuan.runrunv2.mapper.fulfillment;

import com.zhangxuan.runrunv2.model.fulfillment.FulfillmentTaskRow;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 仅读取稳定的 (created_at,id) 分页投影；权限和游标校验由原查询服务负责。 */
@Mapper
public interface FulfillmentQueryMapper {
    List<FulfillmentTaskRow> findCourierTasks(
            @Param("courierId") long courierId,
            @Param("status") String status,
            @Param("afterCreatedAt") OffsetDateTime afterCreatedAt,
            @Param("afterId") Long afterId,
            @Param("limit") int limit);

    List<Map<String, Object>> findPaidOrderPool(
            @Param("afterCreatedAt") OffsetDateTime afterCreatedAt,
            @Param("afterId") Long afterId,
            @Param("limit") int limit);
}
