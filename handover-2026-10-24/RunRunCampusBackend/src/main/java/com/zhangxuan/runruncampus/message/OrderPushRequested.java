package com.zhangxuan.runruncampus.message;

import java.util.UUID;

public record OrderPushRequested(
        Long recipientId,
        Long orderId,
        String title,
        String content,
        String idempotentToken,
        PushDestination destination
) {
    public OrderPushRequested(Long recipientId, Long orderId, String title, String content,
                              String idempotentToken) {
        this(recipientId, orderId, title, content, idempotentToken, null);
    }

    public OrderPushRequested {
        if ((orderId == null) == (destination == null)) {
            throw new IllegalArgumentException("推送必须且只能指定订单或页面目的地");
        }
    }

    static OrderPushRequested create(Long recipientId, Long orderId, String title, String content) {
        return new OrderPushRequested(recipientId, orderId, title, content, UUID.randomUUID().toString(), null);
    }
}
