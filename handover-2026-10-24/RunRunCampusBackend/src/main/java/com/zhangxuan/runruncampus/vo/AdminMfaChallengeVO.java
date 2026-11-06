package com.zhangxuan.runruncampus.vo;

import java.time.Instant;





























public record AdminMfaChallengeVO(String step, String challengeToken, Instant expiresAt, String secret, String otpauthUri) {
}
