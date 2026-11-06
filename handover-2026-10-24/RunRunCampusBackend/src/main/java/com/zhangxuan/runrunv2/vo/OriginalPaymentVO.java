package com.zhangxuan.runrunv2.vo;

import java.time.OffsetDateTime;

/** 原始支付响应；保持现有 JSON 字段及序列化契约。 */
public record OriginalPaymentVO(
        String id,
        String orderId,
        String purpose,
        long amountCents,
        String status,
        OffsetDateTime deadline,
        long version) {}
