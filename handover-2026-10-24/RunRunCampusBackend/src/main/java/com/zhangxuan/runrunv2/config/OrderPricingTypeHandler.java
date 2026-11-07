package com.zhangxuan.runrunv2.config;

import com.zhangxuan.runrunv2.model.order.OrderPricing;

/** 订单报价地址快照 JSONB 映射；只由 v2 XML 显式使用。 */
public class OrderPricingTypeHandler extends OrderSnapshotTypeHandler<OrderPricing> {
    public OrderPricingTypeHandler(OrderJson json) {
        super(json, OrderPricing.class);
    }
}
