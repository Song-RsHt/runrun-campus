package com.zhangxuan.runruncampus.vo.auth;

import java.time.Instant;

/** 登录图形验证码响应；保持现有 JSON 字段及序列化契约。 */
public record LoginCaptchaVO(String challengeToken, String imageDataUrl, Instant expiresAt) {}
