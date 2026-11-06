package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.OrderIssueType;
import java.time.Instant;





public record OrderIssueItem(Long id, OrderIssueType type, String description, UserSummary reporter, Instant createdAt) {
}
