package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.OrderStatus;

import java.time.Instant;

/** 派单结果响应；保持现有 JSON 字段及序列化契约。 */
public record DispatchResultVO(
        long orderId,
        String orderNo,
        OrderStatus status,
        long courierId,
        String courierName,
        Instant dispatchedAt) {}
