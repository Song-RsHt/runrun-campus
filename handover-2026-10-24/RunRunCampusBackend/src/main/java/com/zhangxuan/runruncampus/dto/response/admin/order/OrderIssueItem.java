package com.zhangxuan.runruncampus.dto.response.admin.order;

import com.zhangxuan.runruncampus.enums.order.OrderIssueType;
import java.time.Instant;

public record OrderIssueItem(
    Long id, OrderIssueType type, String description, UserSummary reporter, Instant createdAt) {}
