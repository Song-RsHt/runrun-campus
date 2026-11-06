package com.zhangxuan.runruncampus.enums;

/** 会话客户端类型枚举；名称和取值沿用既有业务与协议定义。 */
public enum SessionClientType {
    ANDROID,
    ADMIN_WEB,
    UNKNOWN;

    public static SessionClientType from(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}
