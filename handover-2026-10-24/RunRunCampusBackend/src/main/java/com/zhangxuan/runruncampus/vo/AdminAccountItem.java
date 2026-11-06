package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.enums.AdminRole;
import java.time.Instant;
import java.util.Set;







public record AdminAccountItem(Long id, String phone, String displayName, AdminRole adminRole, Set<AdminPermission> permissions, boolean enabled, Instant createdAt, int failedLoginAttempts, Instant loginLockedUntil) {
}
