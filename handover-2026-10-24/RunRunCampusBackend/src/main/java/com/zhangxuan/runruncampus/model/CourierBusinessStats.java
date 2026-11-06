package com.zhangxuan.runruncampus.model;


















public interface CourierBusinessStats {

    Long getTodayCompletedOrders();

    Long getTotalCompletedOrders();

    Long getTodayGrossServiceFeeCents();

    Long getTotalGrossServiceFeeCents();
}
