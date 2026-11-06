package com.zhangxuan.runruncampus.vo;

import java.time.Instant;
import java.util.List;































public record DispatchCandidateVO(long courierId, String displayName, long activeOrders, Instant onlineSince, long completedOrders, long matchedCompletedOrders, Double averageRating, long ratingCount, int recommendationScore, boolean recommended, List<String> reasons) {
}
