package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminAuditTargetType;
import java.time.Instant;

public record AdminAuditItem(Long id, AdminAuditAction action, AdminAuditTargetType targetType, Long targetId, String summary, Long administratorId, String administratorName, String administratorPhone, Instant createdAt) {
}
