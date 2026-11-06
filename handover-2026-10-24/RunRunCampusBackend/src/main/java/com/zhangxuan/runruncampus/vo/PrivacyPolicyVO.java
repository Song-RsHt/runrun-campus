package com.zhangxuan.runruncampus.vo;

import java.time.LocalDate;

























public record PrivacyPolicyVO(String version, String title, LocalDate effectiveDate, String operatorName, String contact, String content) {
}
