package com.zhangxuan.runruncampus.model.admin;

/**
 * Database facts used by the service's recommendation policy. No computed ranking or HTTP state.
 */
public record DispatchCandidateRow(
    long id,
    String displayName,
    long activeOrders,
    java.time.Instant onlineSince,
    long completedOrders,
    long matchedCompletedOrders,
    Double averageRating,
    long ratingCount) {}
