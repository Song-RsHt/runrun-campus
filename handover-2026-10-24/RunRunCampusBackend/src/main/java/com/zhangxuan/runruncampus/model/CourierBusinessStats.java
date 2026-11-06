package com.zhangxuan.runruncampus.model;

/** 骑手业务统计；仅承载内部协作所需的数据或校验逻辑。 */
public interface CourierBusinessStats {

    Long getTodayCompletedOrders();

    Long getTotalCompletedOrders();

    Long getTodayGrossServiceFeeCents();

    Long getTotalGrossServiceFeeCents();
}
