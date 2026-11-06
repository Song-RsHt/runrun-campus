package com.zhangxuan.runruncampus.vo;

/** 骑手履约统计响应；保持现有 JSON 字段及序列化契约。 */
public record AdminCourierPerformanceResponse(
        long todayCompletedPackageOrders,
        long lastSevenDaysCompletedPackageOrders,
        long currentMonthCompletedPackageOrders,
        long totalCompletedPackageOrders,
        long smallCompletedPackageOrders,
        long mediumCompletedPackageOrders,
        long mediumLargeCompletedPackageOrders,
        long largeCompletedPackageOrders,
        long specialLargeCompletedPackageOrders,
        long extraLargeCompletedPackageOrders,
        AdminCourierOrderPage orders) {}
