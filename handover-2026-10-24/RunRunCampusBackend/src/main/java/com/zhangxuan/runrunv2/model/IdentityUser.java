package com.zhangxuan.runrunv2.model;

/**
 * Internal account row; credentials never form part of an HTTP response.
 */
public record IdentityUser(long id, String role, String status, boolean enabled, long securityVersion, boolean controlled, String passwordHash) {
}
