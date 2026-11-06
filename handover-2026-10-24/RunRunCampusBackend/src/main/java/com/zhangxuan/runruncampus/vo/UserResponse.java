package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.enums.admin.AdminRole;
import java.time.Instant;
import java.util.Set;

public record UserResponse(Long id, String phone, String displayName, UserRole role, AdminRole adminRole, Set<AdminPermission> permissions, String privacyPolicyVersion, Instant privacyConsentAt) {
}
