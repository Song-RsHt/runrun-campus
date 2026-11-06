package com.zhangxuan.runruncampus.vo;

import java.time.LocalDate;

/** 隐私同意及账号注销策略响应；保持现有 JSON 字段及序列化契约。 */
public record PrivacyPolicyVO(
        String version,
        String title,
        LocalDate effectiveDate,
        String operatorName,
        String contact,
        String content) {}
