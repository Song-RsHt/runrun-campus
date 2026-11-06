package com.zhangxuan.runruncampus.vo;

import java.time.Instant;

/** 隐私同意及账号注销同意响应；保持现有 JSON 字段及序列化契约。 */
public record PrivacyConsentVO(
        boolean accepted, String currentVersion, String acceptedVersion, Instant acceptedAt) {}
