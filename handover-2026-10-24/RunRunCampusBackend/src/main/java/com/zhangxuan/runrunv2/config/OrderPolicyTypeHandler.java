package com.zhangxuan.runrunv2.config;

import java.util.Map;

/** 订单政策快照 JSONB 映射；只由 v2 XML 显式使用。 */
public class OrderPolicyTypeHandler extends OrderSnapshotTypeHandler<Map<String, Object>> {
    public OrderPolicyTypeHandler(OrderJson json) {
        super(json, Map.class);
    }
}
