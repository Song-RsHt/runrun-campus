package com.zhangxuan.runrunv2.vo;

import java.time.OffsetDateTime;

/** 订单履约结果响应；保持现有 JSON 字段及序列化契约。 */
public record FulfillmentResultVO(
        FulfillmentOrderVO order, FulfillmentTaskVO task, OffsetDateTime confirmDeadline) {}
