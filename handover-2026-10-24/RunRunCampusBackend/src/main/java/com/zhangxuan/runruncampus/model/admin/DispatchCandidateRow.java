package com.zhangxuan.runruncampus.model.admin;

import java.time.Instant;

/**
 * Database facts used by the service's recommendation policy. No computed ranking or HTTP state.
 */
public record DispatchCandidateRow(
    long id,
    String displayName,
    long activeOrders,
    Instant onlineSince,
    long completedOrders,
    long matchedCompletedOrders,
    Double averageRating,
    long ratingCount) {}
