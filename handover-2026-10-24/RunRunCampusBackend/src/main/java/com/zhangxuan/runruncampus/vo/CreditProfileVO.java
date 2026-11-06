package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.CreditRiskLevel;
import java.util.List;



























public record CreditProfileVO(Long userId, String displayName, int score, CreditRiskLevel riskLevel, long pendingSignals, List<CreditEventVO> events, int page, int size, long totalElements, int totalPages) {
}
