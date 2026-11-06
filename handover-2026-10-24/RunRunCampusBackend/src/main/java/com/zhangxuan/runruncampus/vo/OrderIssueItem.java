package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.OrderIssueType;

import java.time.Instant;

/** 订单异常上报条目响应；保持现有 JSON 字段及序列化契约。 */
public record OrderIssueItem(
        Long id,
        OrderIssueType type,
        String description,
        UserSummary reporter,
        Instant createdAt) {}
