package com.zhangxuan.runrunv2.model.identity;

/** 内部账号行；凭据不能进入 HTTP 响应。 */
public record IdentityUser(
        long id,
        String role,
        String status,
        boolean enabled,
        long securityVersion,
        boolean controlled,
        String passwordHash) {}
