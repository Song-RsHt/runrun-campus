package com.zhangxuan.runruncampus.dto.response.auth;

import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.enums.admin.AdminRole;
import com.zhangxuan.runruncampus.enums.user.UserRole;
import java.time.Instant;
import java.util.Set;

public record UserResponse(
    Long id,
    String phone,
    String displayName,
    UserRole role,
    AdminRole adminRole,
    Set<AdminPermission> permissions,
    String privacyPolicyVersion,
    Instant privacyConsentAt) {}
