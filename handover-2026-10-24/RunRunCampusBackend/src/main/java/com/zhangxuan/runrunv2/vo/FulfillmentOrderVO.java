package com.zhangxuan.runrunv2.vo;

import java.time.OffsetDateTime;

/** 订单履约订单响应；保持现有 JSON 字段及序列化契约。 */
public record FulfillmentOrderVO(
        String id,
        String orderNo,
        int flowVersion,
        String rawStatus,
        String displayStatus,
        long version,
        long originalFeeCents,
        long effectiveFeeCents,
        OffsetDateTime deliveredAt,
        OffsetDateTime confirmStartedAt,
        OffsetDateTime confirmDeadline) {}
