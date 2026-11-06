package com.zhangxuan.runruncampus.vo.auth;

import com.zhangxuan.runruncampus.enums.auth.SessionClientType;

import java.time.Instant;

/** 会话信息响应；保持现有 JSON 字段及序列化契约。 */
public record SessionInfoVO(
        Long id,
        String deviceName,
        SessionClientType clientType,
        Instant createdAt,
        Instant expiresAt,
        boolean current) {}
