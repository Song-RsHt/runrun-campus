package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.OrderStatus;

import java.time.Instant;

/** 订单创建与状态管理事件响应；保持现有 JSON 字段及序列化契约。 */
public record OrderEventResponse(
        Long id, OrderStatus status, String actorName, Instant createdAt) {}
