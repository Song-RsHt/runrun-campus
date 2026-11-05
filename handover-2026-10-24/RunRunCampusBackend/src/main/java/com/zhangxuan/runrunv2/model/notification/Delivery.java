package com.zhangxuan.runrunv2.model.notification;

import java.util.Map;

public record Delivery(
    long id,
    long recipientId,
    String eventKey,
    String channel,
    Map<String, Object> payload,
    int attemptCount) {}
