package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.RiskSignalStatus;
import com.zhangxuan.runruncampus.enums.RiskSignalType;
import java.time.Instant;



























public record RiskSignalVO(Long id, Long userId, String displayName, String phone, RiskSignalType type, RiskSignalStatus status, Long orderId, String details, Instant createdAt, Instant reviewedAt, String reviewerName, String reviewNote) {
}
