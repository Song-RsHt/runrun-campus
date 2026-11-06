package com.zhangxuan.runruncampus.dto.response.admin.issue;

import com.zhangxuan.runruncampus.enums.order.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.enums.order.OrderIssueType;
import com.zhangxuan.runruncampus.enums.order.OrderStatus;
import java.time.Instant;

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
