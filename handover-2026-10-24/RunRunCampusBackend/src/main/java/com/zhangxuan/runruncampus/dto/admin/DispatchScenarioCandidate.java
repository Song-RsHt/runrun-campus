package com.zhangxuan.runruncampus.dto.admin;

/** 派单场景候选请求参数；保留入口的字段、构造器及校验契约。 */
public record DispatchScenarioCandidate(
        long courierId,
        long activeOrders,
        long onlineMinutes,
        long completedOrders,
        long matchedCompletedOrders,
        Double averageRating,
        long ratingCount,
        int walkingMinutes,
        int remainingWorkMinutes) {}
