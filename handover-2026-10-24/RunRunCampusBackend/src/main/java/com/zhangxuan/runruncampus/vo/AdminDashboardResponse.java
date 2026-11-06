package com.zhangxuan.runruncampus.vo;

/** 后台业务概览响应；保持现有 JSON 字段及序列化契约。 */
public record AdminDashboardResponse(
        long totalUsers,
        long totalCouriers,
        long totalOrders,
        long pendingOrders,
        long activeDeliveries,
        long pendingCertifications,
        long pendingIssues,
        long processingIssues,
        long pendingFeedback,
        long processingFeedback) {}
