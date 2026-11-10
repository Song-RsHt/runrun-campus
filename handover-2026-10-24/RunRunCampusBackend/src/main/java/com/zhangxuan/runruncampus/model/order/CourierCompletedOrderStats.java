package com.zhangxuan.runruncampus.model.order;

/** 骑手已完成订单统计；仅承载内部协作所需的数据或校验逻辑。 */
public interface CourierCompletedOrderStats {

    Long getCourierId();

    Long getCompletedOrderCount();

    Long getCompletedPackageOrderCount();
}
