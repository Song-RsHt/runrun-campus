package com.zhangxuan.runrunv2.vo.notification;

import java.time.OffsetDateTime;
import java.util.Map;

/** 仅返回当前账号的通知；显式响应字段避免数据库列名和驱动 JSONB 包装泄漏给客户端。 */
public record NotificationVO(
        long id,
        Long orderId,
        String type,
        Map<String, Object> content,
        OffsetDateTime createdAt,
        OffsetDateTime readAt) {}
