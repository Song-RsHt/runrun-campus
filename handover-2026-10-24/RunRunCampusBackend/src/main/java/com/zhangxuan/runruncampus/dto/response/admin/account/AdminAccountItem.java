package com.zhangxuan.runruncampus.dto.response.admin.account;

import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.enums.admin.AdminRole;
import java.time.Instant;
import java.util.Set;

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
