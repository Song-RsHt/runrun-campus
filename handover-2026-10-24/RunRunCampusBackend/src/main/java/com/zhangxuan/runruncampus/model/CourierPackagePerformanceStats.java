package com.zhangxuan.runruncampus.model;

/** 骑手包裹履约统计统计；仅承载内部协作所需的数据或校验逻辑。 */
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
