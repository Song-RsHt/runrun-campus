package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.PushDeliveryStatus;
import com.zhangxuan.runruncampus.enums.PushDestination;

import java.time.Instant;

/** 推送投递条目响应；保持现有 JSON 字段及序列化契约。 */
public record PushDeliveryItemVO(
        Long id,
        Long recipientId,
        Long orderId,
        String title,
        PushDestination destination,
        PushDeliveryStatus status,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt,
        Instant sentAt) {}
