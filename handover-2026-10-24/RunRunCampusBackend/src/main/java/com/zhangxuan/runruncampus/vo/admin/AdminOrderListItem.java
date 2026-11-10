package com.zhangxuan.runruncampus.vo.admin;

import com.zhangxuan.runruncampus.enums.order.CourierSettlementStatus;
import com.zhangxuan.runruncampus.enums.order.ManualPaymentStatus;
import com.zhangxuan.runruncampus.enums.order.OrderPaymentMethod;
import com.zhangxuan.runruncampus.enums.order.OrderPaymentStatus;
import com.zhangxuan.runruncampus.enums.order.OrderStatus;
import com.zhangxuan.runruncampus.enums.order.ServiceType;

import java.time.Instant;

/** 管理员订单查询与导出列表条目响应；保持现有 JSON 字段及序列化契约。 */
public record AdminOrderListItem(
        Long id,
        String orderNo,
        OrderStatus status,
        ServiceType serviceType,
        String merchant,
        String destination,
        int feeCents,
        OrderPaymentMethod paymentMethod,
        OrderPaymentStatus paymentStatus,
        int paidCents,
        ManualPaymentStatus manualPaymentStatus,
        CourierSettlementStatus courierSettlementStatus,
        UserSummary customer,
        UserSummary courier,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt) {}
