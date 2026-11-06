package com.zhangxuan.runruncampus.dto.response.admin.courier;

import com.zhangxuan.runruncampus.enums.order.OrderStatus;
import com.zhangxuan.runruncampus.enums.order.ServiceType;
import java.time.Instant;

public record AdminCourierOrderItem(
    Long id,
    String orderNo,
    OrderStatus status,
    ServiceType serviceType,
    String destination,
    int feeCents,
    String customerName,
    Instant createdAt,
    Instant updatedAt) {}
