package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.CreditEventType;
import com.zhangxuan.runruncampus.enums.CreditRiskLevel;
import java.time.Instant;



























public record CreditChangeVO(Long id, CreditEventType type, int delta, int balanceAfter, String reason, Instant createdAt, int score, CreditRiskLevel riskLevel) {
}
