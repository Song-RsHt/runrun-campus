package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.RefundStatus;
import java.time.Instant;

































public record RefundResponse(int amountCents, String reason, RefundStatus status, Instant createdAt, Instant updatedAt) {
}
