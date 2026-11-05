package com.zhangxuan.runruncampus.enums.auth;

public enum SessionClientType {
    ANDROID,
    ADMIN_WEB,
    UNKNOWN;

    static public SessionClientType from(String value) {
        if (value == null) return UNKNOWN;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}
