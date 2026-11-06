package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.enums.OrderIssueType;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import java.time.Instant;

public record AdminIssueItem(Long id, OrderIssueType type, String description, Instant createdAt, Long orderId, String orderNo, OrderStatus orderStatus, Long reporterId, String reporterName, String reporterPhone, OrderIssueHandlingStatus handlingStatus, Long assigneeId, String assigneeName, String resolution, Instant handledAt, Instant updatedAt, Instant slaDeadlineAt, boolean overdue, long overdueMinutes) {
}
