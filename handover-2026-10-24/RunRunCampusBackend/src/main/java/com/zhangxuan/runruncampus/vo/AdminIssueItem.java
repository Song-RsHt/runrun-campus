package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.enums.OrderIssueType;
import com.zhangxuan.runruncampus.enums.OrderStatus;

import java.time.Instant;

/** 管理员异常工单条目响应；保持现有 JSON 字段及序列化契约。 */
public record AdminIssueItem(
        Long id,
        OrderIssueType type,
        String description,
        Instant createdAt,
        Long orderId,
        String orderNo,
        OrderStatus orderStatus,
        Long reporterId,
        String reporterName,
        String reporterPhone,
        OrderIssueHandlingStatus handlingStatus,
        Long assigneeId,
        String assigneeName,
        String resolution,
        Instant handledAt,
        Instant updatedAt,
        Instant slaDeadlineAt,
        boolean overdue,
        long overdueMinutes) {}
