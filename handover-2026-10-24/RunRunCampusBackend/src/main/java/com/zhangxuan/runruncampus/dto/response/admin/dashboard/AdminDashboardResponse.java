package com.zhangxuan.runruncampus.dto.response.admin.dashboard;

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
