package com.zhangxuan.runruncampus.dto.response.courier.courieroperations;

import java.time.Instant;

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
