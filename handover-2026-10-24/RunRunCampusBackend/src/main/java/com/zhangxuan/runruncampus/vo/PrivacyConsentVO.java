package com.zhangxuan.runruncampus.vo;

import java.time.Instant;

























public record PrivacyConsentVO(boolean accepted, String currentVersion, String acceptedVersion, Instant acceptedAt) {
}
