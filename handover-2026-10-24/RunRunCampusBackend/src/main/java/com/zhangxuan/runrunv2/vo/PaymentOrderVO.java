package com.zhangxuan.runrunv2.vo;

import java.time.OffsetDateTime;

/** 支付回执及退款订单响应；保持现有 JSON 字段及序列化契约。 */
public record PaymentOrderVO(
        String id,
        String orderId,
        String adjustmentId,
        String purpose,
        long amountCents,
        String status,
        OffsetDateTime deadline,
        OffsetDateTime paidAt,
        long version) {}
