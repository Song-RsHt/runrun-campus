package com.zhangxuan.runruncampus.vo.admin;

import com.zhangxuan.runruncampus.enums.user.UserRole;

import java.time.Instant;

/** 用户账号及管理状态响应；保持现有 JSON 字段及序列化契约。 */
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
