package com.zhangxuan.runruncampus.dto;
















public record DispatchScenarioCandidate(long courierId, long activeOrders, long onlineMinutes, long completedOrders, long matchedCompletedOrders, Double averageRating, long ratingCount, int walkingMinutes, int remainingWorkMinutes) {
}
