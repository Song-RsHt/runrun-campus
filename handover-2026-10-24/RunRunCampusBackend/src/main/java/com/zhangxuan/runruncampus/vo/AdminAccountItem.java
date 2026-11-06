package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.enums.AdminRole;

import java.time.Instant;
import java.util.Set;

/** 管理员账号条目响应；保持现有 JSON 字段及序列化契约。 */
public record AdminAccountItem(
        Long id,
        String phone,
        String displayName,
        AdminRole adminRole,
        Set<AdminPermission> permissions,
        boolean enabled,
        Instant createdAt,
        int failedLoginAttempts,
        Instant loginLockedUntil) {}
