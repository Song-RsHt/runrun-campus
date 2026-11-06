package com.zhangxuan.runruncampus.vo.auth;

import com.zhangxuan.runruncampus.enums.admin.AdminPermission;
import com.zhangxuan.runruncampus.enums.admin.AdminRole;
import com.zhangxuan.runruncampus.enums.user.UserRole;

import java.time.Instant;
import java.util.Set;

/** 用户响应；保持现有 JSON 字段及序列化契约。 */
public record UserResponse(
        Long id,
        String phone,
        String displayName,
        UserRole role,
        AdminRole adminRole,
        Set<AdminPermission> permissions,
        String privacyPolicyVersion,
        Instant privacyConsentAt) {}
