package com.zhangxuan.runruncampus.vo.admin;

import java.time.Instant;
import java.util.List;

/** 派单候选响应；保持现有 JSON 字段及序列化契约。 */
public record DispatchCandidateVO(
        long courierId,
        String displayName,
        long activeOrders,
        Instant onlineSince,
        long completedOrders,
        long matchedCompletedOrders,
        Double averageRating,
        long ratingCount,
        int recommendationScore,
        boolean recommended,
        List<String> reasons) {}
