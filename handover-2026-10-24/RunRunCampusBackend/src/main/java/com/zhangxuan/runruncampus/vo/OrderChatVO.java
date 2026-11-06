package com.zhangxuan.runruncampus.vo;

import java.time.Instant;

/** 订单参与者聊天响应；保持现有 JSON 字段及序列化契约。 */
public record OrderChatVO(
        Long id,
        Long senderId,
        String senderName,
        Long recipientId,
        String content,
        Instant createdAt) {}
