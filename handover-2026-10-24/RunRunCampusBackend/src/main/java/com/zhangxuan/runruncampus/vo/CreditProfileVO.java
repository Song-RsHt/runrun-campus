package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.CreditRiskLevel;

import java.util.List;

/** 用户信用分及信用事件分页响应；保持现有 JSON 字段及序列化契约。 */
public record CreditProfileVO(
        Long userId,
        String displayName,
        int score,
        CreditRiskLevel riskLevel,
        long pendingSignals,
        List<CreditEventVO> events,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
