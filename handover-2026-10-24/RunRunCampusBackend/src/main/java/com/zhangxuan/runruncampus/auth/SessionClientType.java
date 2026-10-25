package com.zhangxuan.runruncampus.auth;

public enum SessionClientType {
    ANDROID,
    ADMIN_WEB,
    UNKNOWN;

    static SessionClientType from(String value) {
        if (value == null) return UNKNOWN;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}
