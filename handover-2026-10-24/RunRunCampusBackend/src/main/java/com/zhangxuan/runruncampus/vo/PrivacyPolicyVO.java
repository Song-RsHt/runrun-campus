package com.zhangxuan.runruncampus.vo;

import java.time.LocalDate;

/** 当前隐私政策的版本、生效日期及正文响应；保持现有 JSON 字段及序列化契约。 */
public record PrivacyPolicyVO(
        String version,
        String title,
        LocalDate effectiveDate,
        String operatorName,
        String contact,
        String content) {}
