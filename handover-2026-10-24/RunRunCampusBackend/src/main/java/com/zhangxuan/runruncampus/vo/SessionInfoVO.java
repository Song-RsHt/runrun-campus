package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.SessionClientType;
import java.time.Instant;

























public record SessionInfoVO(Long id, String deviceName, SessionClientType clientType, Instant createdAt, Instant expiresAt, boolean current) {
}
