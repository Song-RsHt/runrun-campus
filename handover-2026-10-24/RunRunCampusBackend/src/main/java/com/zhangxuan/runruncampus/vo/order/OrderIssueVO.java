package com.zhangxuan.runruncampus.vo.order;

import com.zhangxuan.runruncampus.enums.order.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.enums.order.OrderIssueType;
import com.zhangxuan.runruncampus.enums.user.UserRole;

import java.time.Instant;

/** 订单异常上报响应；保持现有 JSON 字段及序列化契约。 */
public record OrderIssueVO(
        Long id,
        OrderIssueType type,
        String description,
        String reporterName,
        UserRole reporterRole,
        Instant createdAt,
        OrderIssueHandlingStatus handlingStatus,
        String resolution,
        Instant handledAt,
        Instant updatedAt) {}
