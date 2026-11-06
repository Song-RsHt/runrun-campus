package com.zhangxuan.runruncampus.vo.admin;

import com.zhangxuan.runruncampus.enums.order.OrderStatus;
import com.zhangxuan.runruncampus.enums.order.ServiceType;

import java.time.Instant;

/** 管理员骑手查询订单条目响应；保持现有 JSON 字段及序列化契约。 */
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
