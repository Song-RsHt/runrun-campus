package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.AdminPermission;
import com.zhangxuan.runruncampus.enums.AdminRole;
import com.zhangxuan.runruncampus.enums.UserRole;

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
