package com.zhangxuan.runruncampus.model;


















public interface CourierPackagePerformanceStats {

    Long getTodayCompletedPackageOrders();

    Long getLastSevenDaysCompletedPackageOrders();

    Long getCurrentMonthCompletedPackageOrders();

    Long getTotalCompletedPackageOrders();

    Long getSmallCompletedPackageOrders();

    Long getMediumCompletedPackageOrders();

    Long getMediumLargeCompletedPackageOrders();

    Long getLargeCompletedPackageOrders();

    Long getSpecialLargeCompletedPackageOrders();

    Long getExtraLargeCompletedPackageOrders();
}
