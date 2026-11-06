package com.zhangxuan.runruncampus.vo;

import java.time.Instant;

/** 管理员双因素认证挑战响应；保持现有 JSON 字段及序列化契约。 */
public record AdminMfaChallengeVO(
        String step, String challengeToken, Instant expiresAt, String secret, String otpauthUri) {}
