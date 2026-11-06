package com.zhangxuan.runruncampus.model;


































public final class DispatchScoreCalculator {

    private DispatchScoreCalculator() {
    }

    public static int recommendationScore(long activeOrders, long onlineMinutes, long completedOrders, long matchedCompletedOrders, Double averageRating, long ratingCount) {
        long score = 60;
        score -= Math.min(Math.max(activeOrders, 0), 2) * 45;
        score += Math.min(Math.max(onlineMinutes, 0) / 12, 10);
        score += Math.min(Math.max(completedOrders, 0) / 4, 10);
        score += Math.min(Math.max(matchedCompletedOrders, 0) / 2, 10);
        if (averageRating != null && ratingCount >= 3) {
            score += Math.min(10, Math.max(0, Math.round((averageRating - 3.0) * 5)));
        }
        return (int) Math.min(100, Math.max(0, score));
    }
}
