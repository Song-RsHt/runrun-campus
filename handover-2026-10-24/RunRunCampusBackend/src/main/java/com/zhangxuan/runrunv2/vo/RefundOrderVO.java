package com.zhangxuan.runrunv2.vo;

import java.time.OffsetDateTime;

/** 退款订单响应；保持现有 JSON 字段及序列化契约。 */
public record RefundOrderVO(
        String id,
        String orderId,
        String paymentOrderId,
        String adjustmentId,
        long amountCents,
        String purpose,
        String status,
        OffsetDateTime succeededAt,
        long version) {}
