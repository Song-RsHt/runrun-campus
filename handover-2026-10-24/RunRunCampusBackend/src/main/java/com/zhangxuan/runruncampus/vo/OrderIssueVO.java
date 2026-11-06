package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.OrderIssueHandlingStatus;
import com.zhangxuan.runruncampus.enums.OrderIssueType;
import com.zhangxuan.runruncampus.enums.UserRole;
import java.time.Instant;




















public record OrderIssueVO(Long id, OrderIssueType type, String description, String reporterName, UserRole reporterRole, Instant createdAt, OrderIssueHandlingStatus handlingStatus, String resolution, Instant handledAt, Instant updatedAt) {
}
