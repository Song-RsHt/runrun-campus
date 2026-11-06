package com.zhangxuan.runruncampus.dto.response.admin.courier;

import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.enums.ServiceType;
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
