package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.OrderStatus;

import java.time.Instant;

/** 订单状态事件条目响应；保持现有 JSON 字段及序列化契约。 */
public record OrderEventItem(Long id, OrderStatus status, UserSummary actor, Instant createdAt) {}
