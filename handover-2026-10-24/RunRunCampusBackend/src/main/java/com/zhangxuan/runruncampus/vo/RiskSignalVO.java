package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.RiskSignalStatus;
import com.zhangxuan.runruncampus.enums.RiskSignalType;

import java.time.Instant;

/** 风险信号响应；保持现有 JSON 字段及序列化契约。 */
public record RiskSignalVO(
        Long id,
        Long userId,
        String displayName,
        String phone,
        RiskSignalType type,
        RiskSignalStatus status,
        Long orderId,
        String details,
        Instant createdAt,
        Instant reviewedAt,
        String reviewerName,
        String reviewNote) {}
