package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminAuditTargetType;

import java.time.Instant;

/** 管理员操作审计条目响应；保持现有 JSON 字段及序列化契约。 */
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
