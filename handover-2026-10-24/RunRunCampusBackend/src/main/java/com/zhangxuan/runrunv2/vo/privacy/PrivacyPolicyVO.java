package com.zhangxuan.runrunv2.vo.privacy;

import java.time.LocalDate;

/** v2 协议只读响应；不包含身份或会话数据。 */
public record PrivacyPolicyVO(
        String version,
        String title,
        LocalDate effectiveDate,
        String operatorName,
        String contact,
        String content) {}
