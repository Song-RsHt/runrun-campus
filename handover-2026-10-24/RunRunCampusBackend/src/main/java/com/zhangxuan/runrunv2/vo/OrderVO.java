package com.zhangxuan.runrunv2.vo;

import com.zhangxuan.runrunv2.enums.ServiceType;
import com.zhangxuan.runrunv2.model.OrderAddress;
import com.zhangxuan.runrunv2.model.OrderPricing;

import java.time.OffsetDateTime;
import java.util.Map;

/** 订单创建与状态管理响应；保持现有 JSON 字段及序列化契约。 */
public record OrderVO(
        String id,
        String orderNo,
        int orderFlowVersion,
        String userId,
        String status,
        ServiceType serviceType,
        String zoneId,
        String servicePointId,
        OrderAddress addressSnapshot,
        OrderPricing pricingSnapshot,
        Map<String, Object> policySnapshot,
        long originalFeeCents,
        long effectiveFeeCents,
        OffsetDateTime pendingPaymentDeadline,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime confirmStartedAt,
        OffsetDateTime confirmDeadline) {}
