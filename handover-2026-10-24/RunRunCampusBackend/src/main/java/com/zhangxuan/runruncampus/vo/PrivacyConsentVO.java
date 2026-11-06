package com.zhangxuan.runruncampus.vo;

import java.time.Instant;

/** 当前政策版本、用户已同意版本及同意状态响应；保持现有 JSON 字段及序列化契约。 */
public record PrivacyConsentVO(
        boolean accepted, String currentVersion, String acceptedVersion, Instant acceptedAt) {}
