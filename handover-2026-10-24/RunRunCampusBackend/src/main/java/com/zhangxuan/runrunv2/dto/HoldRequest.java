package com.zhangxuan.runrunv2.dto;

import java.time.Instant;

public record HoldRequest(Long orderId, Long certificationId, String reason, Instant reviewDueAt) {
}
