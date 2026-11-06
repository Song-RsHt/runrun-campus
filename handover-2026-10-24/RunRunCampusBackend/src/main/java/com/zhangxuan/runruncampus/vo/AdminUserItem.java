package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.UserRole;
import java.time.Instant;

public record AdminUserItem(Long id, String displayName, String phone, UserRole role, boolean enabled, boolean courierApproved, Instant registeredAt, int failedLoginAttempts, Instant loginLockedUntil) {
}
