package com.zhangxuan.runruncampus.model;

import com.zhangxuan.runruncampus.enums.SessionClientType;
























public record SessionMetadata(SessionClientType clientType, String deviceName) {

    public static SessionMetadata of(String clientType, String deviceName) {
        SessionClientType type = SessionClientType.from(clientType);
        String normalizedName = deviceName == null || deviceName.isBlank() ? "未知设备" : deviceName;
        return new SessionMetadata(type, normalizedName);
    }

    public static SessionMetadata unknown() {
        return new SessionMetadata(SessionClientType.UNKNOWN, "未知设备");
    }
}
