package com.zhangxuan.runrunv2.dto.request.evidence;

import java.time.Instant;

public record HoldRequest(Long orderId, Long certificationId, String reason, Instant reviewDueAt) {}
