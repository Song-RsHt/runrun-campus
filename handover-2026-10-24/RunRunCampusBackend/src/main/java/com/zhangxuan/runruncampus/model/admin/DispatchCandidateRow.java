package com.zhangxuan.runruncampus.model.admin;

import java.time.Instant;

/** 派单策略所需的数据库事实；不包含计算后的排名或 HTTP 状态。 */
public record DispatchCandidateRow(
        long id,
        String displayName,
        long activeOrders,
        Instant onlineSince,
        long completedOrders,
        long matchedCompletedOrders,
        Double averageRating,
        long ratingCount) {}
