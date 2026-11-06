package com.zhangxuan.runruncampus.model;

import com.zhangxuan.runruncampus.enums.PushDestination;

import java.util.UUID;

/** 订单推送待发送事件；仅承载内部协作所需的数据或校验逻辑。 */
public record OrderPushRequested(
        Long recipientId,
        Long orderId,
        String title,
        String content,
        String idempotentToken,
        PushDestination destination) {

    public OrderPushRequested(
            Long recipientId, Long orderId, String title, String content, String idempotentToken) {
        this(recipientId, orderId, title, content, idempotentToken, null);
    }

    public OrderPushRequested {
        if ((orderId == null) == (destination == null)) {
            throw new IllegalArgumentException("推送必须且只能指定订单或页面目的地");
        }
    }

    public static OrderPushRequested create(
            Long recipientId, Long orderId, String title, String content) {
        return new OrderPushRequested(
                recipientId, orderId, title, content, UUID.randomUUID().toString(), null);
    }
}
