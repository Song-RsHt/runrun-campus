package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.UserRole;

import java.time.Instant;

/** 管理员用户维护条目响应；保持现有 JSON 字段及序列化契约。 */
public record AdminUserItem(
        Long id,
        String displayName,
        String phone,
        UserRole role,
        boolean enabled,
        boolean courierApproved,
        Instant registeredAt,
        int failedLoginAttempts,
        Instant loginLockedUntil) {}
