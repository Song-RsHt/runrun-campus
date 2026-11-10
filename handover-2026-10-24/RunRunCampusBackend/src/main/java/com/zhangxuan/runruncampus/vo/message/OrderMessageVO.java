package com.zhangxuan.runruncampus.vo.message;

import com.zhangxuan.runruncampus.enums.message.OrderMessageType;

import java.time.Instant;

/** 订单站内消息响应；保持现有 JSON 字段及序列化契约。 */
public record OrderMessageVO(
        Long id,
        Long orderId,
        String orderNo,
        OrderMessageType type,
        String title,
        String content,
        boolean read,
        Instant createdAt,
        Instant readAt) {}
