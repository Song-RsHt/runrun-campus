package com.zhangxuan.runruncampus.vo;

import java.time.Instant;



















public record LoginCaptchaVO(String challengeToken, String imageDataUrl, Instant expiresAt) {
}
