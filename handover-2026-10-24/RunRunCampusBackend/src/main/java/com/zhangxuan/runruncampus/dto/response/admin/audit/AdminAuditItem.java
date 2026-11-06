package com.zhangxuan.runruncampus.dto.response.admin.audit;

import com.zhangxuan.runruncampus.enums.admin.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.admin.AdminAuditTargetType;
import java.time.Instant;

public record AdminAuditItem(
    Long id,
    AdminAuditAction action,
    AdminAuditTargetType targetType,
    Long targetId,
    String summary,
    Long administratorId,
    String administratorName,
    String administratorPhone,
    Instant createdAt) {}
