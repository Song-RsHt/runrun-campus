package com.zhangxuan.runruncampus.vo.credit;

import com.zhangxuan.runruncampus.enums.credit.CreditEventType;

import java.time.Instant;

/** 信用分及风险信号事件响应；保持现有 JSON 字段及序列化契约。 */
public record CreditEventVO(
        Long id,
        CreditEventType type,
        int delta,
        int balanceAfter,
        String reason,
        String operatorName,
        Long riskSignalId,
        Long originalEventId,
        Instant createdAt) {}
