package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.CourierSettlementStatus;
import com.zhangxuan.runruncampus.enums.ManualPaymentStatus;
import com.zhangxuan.runruncampus.enums.OrderPaymentMethod;
import com.zhangxuan.runruncampus.enums.OrderPaymentStatus;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.enums.ServiceType;
import java.time.Instant;










public record AdminOrderListItem(Long id, String orderNo, OrderStatus status, ServiceType serviceType, String merchant, String destination, int feeCents, OrderPaymentMethod paymentMethod, OrderPaymentStatus paymentStatus, int paidCents, ManualPaymentStatus manualPaymentStatus, CourierSettlementStatus courierSettlementStatus, UserSummary customer, UserSummary courier, Instant createdAt, Instant updatedAt, Instant archivedAt) {
}
