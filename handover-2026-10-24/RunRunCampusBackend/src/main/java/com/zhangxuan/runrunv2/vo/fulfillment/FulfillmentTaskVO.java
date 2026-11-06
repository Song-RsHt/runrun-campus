package com.zhangxuan.runrunv2.vo.fulfillment;

import java.time.OffsetDateTime;

/** 订单履约任务响应；保持现有 JSON 字段及序列化契约。 */
public record FulfillmentTaskVO(
        String id,
        String orderId,
        String courierId,
        String status,
        long version,
        OffsetDateTime createdAt) {}
