package com.zhangxuan.runrunv2.model.notification;

import java.util.Map;

/** 订阅通知投递请求；仅承载内部协作所需的数据或校验逻辑。 */
public record Delivery(
        long id,
        long recipientId,
        String eventKey,
        String channel,
        Map<String, Object> payload,
        int attemptCount) {}
