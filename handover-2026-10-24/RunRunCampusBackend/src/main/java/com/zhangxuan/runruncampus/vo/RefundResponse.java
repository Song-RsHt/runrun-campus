package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.RefundStatus;

import java.time.Instant;

/** 退款响应；保持现有 JSON 字段及序列化契约。 */
public record RefundResponse(
        int amountCents,
        String reason,
        RefundStatus status,
        Instant createdAt,
        Instant updatedAt) {}
