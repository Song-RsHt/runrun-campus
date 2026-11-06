package com.zhangxuan.runrunv2.vo;

import com.zhangxuan.runrunv2.enums.ServiceType;
import com.zhangxuan.runrunv2.model.Address;
import com.zhangxuan.runrunv2.model.Pricing;
import com.zhangxuan.runrunv2.model.Schedule;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record OrderVO(String id, String orderNo, int orderFlowVersion, String userId, String status, ServiceType serviceType, String zoneId, String servicePointId, Address addressSnapshot, Pricing pricingSnapshot, Map<String, Object> policySnapshot, long originalFeeCents, long effectiveFeeCents, OffsetDateTime pendingPaymentDeadline, long version, OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime deliveredAt, OffsetDateTime confirmStartedAt, OffsetDateTime confirmDeadline) {
}
