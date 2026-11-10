package com.zhangxuan.runruncampus.model.message;

import java.time.Instant;

/** 推送投递任务；仅承载内部协作所需的数据或校验逻辑。 */
public record PushDeliveryTask(
        Long id, OrderPushRequested notification, int attempts, Instant createdAt) {}
