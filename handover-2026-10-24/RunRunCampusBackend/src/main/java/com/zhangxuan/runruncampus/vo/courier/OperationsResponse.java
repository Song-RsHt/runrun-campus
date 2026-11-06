package com.zhangxuan.runruncampus.vo.courier;

import java.time.Instant;

/** 接单管理响应；保持现有 JSON 字段及序列化契约。 */
public record OperationsResponse(
        boolean courierApproved,
        boolean acceptingOrders,
        Instant availabilityUpdatedAt,
        long availableOrders,
        long activeOrders,
        int maxActiveOrders,
        long todayCompletedOrders,
        long totalCompletedOrders,
        long todayGrossServiceFeeCents,
        long totalGrossServiceFeeCents,
        double averageRating,
        long ratingCount,
        Instant generatedAt) {}
