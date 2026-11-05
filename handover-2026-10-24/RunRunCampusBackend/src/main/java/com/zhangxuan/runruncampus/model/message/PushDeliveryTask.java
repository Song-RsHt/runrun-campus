package com.zhangxuan.runruncampus.model.message;

import java.time.Instant;

public record PushDeliveryTask(
    Long id, OrderPushRequested notification, int attempts, Instant createdAt) {}
