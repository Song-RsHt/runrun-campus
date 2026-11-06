package com.zhangxuan.runruncampus.dto.response.admin.courier;

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
