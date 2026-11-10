package com.zhangxuan.runrunv2.mapper.order;

import com.zhangxuan.runrunv2.config.SingleRowRequired;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;

/** 订单写入持久化映射；事务、锁顺序及业务校验由原调用方保持。 */
@Mapper
public interface OrderMapper {

    @SingleRowRequired
    String insertOrder(
            @Param("orderNo") String orderNo,
            @Param("userId") Long userId,
            @Param("serviceType") String serviceType,
            @Param("zoneId") Long zoneId,
            @Param("servicePointId") Long servicePointId,
            @Param("addressSnapshot") String addressSnapshot,
            @Param("pricingSnapshot") String pricingSnapshot,
            @Param("policySnapshot") String policySnapshot,
            @Param("originalFeeCents") long originalFeeCents,
            @Param("effectiveFeeCents") long effectiveFeeCents,
            @Param("pendingPaymentDeadline") OffsetDateTime pendingPaymentDeadline,
            @Param("createdAt") OffsetDateTime createdAt,
            @Param("updatedAt") OffsetDateTime updatedAt);

    int insertOrderCreatedEvent(
            @Param("orderId") Long orderId,
            @Param("actorId") Long actorId,
            @Param("requestId") String requestId,
            @Param("payload") String payload);
}
