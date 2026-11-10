package com.zhangxuan.runruncampus.vo.credit;

import com.zhangxuan.runruncampus.enums.credit.CreditEventType;
import com.zhangxuan.runruncampus.enums.credit.CreditRiskLevel;

import java.time.Instant;

/** 信用分变更记录响应；保持现有 JSON 字段及序列化契约。 */
public record CreditChangeVO(
        Long id,
        CreditEventType type,
        int delta,
        int balanceAfter,
        String reason,
        Instant createdAt,
        int score,
        CreditRiskLevel riskLevel) {}
