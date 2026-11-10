package com.zhangxuan.runrunv2.config;

import com.zhangxuan.runrunv2.model.order.OrderAddress;

/** 订单报价地址快照 JSONB 映射；只由 v2 XML 显式使用。 */
public class OrderAddressTypeHandler extends OrderSnapshotTypeHandler<OrderAddress> {
    public OrderAddressTypeHandler(OrderJson json) {
        super(json, OrderAddress.class);
    }
}
