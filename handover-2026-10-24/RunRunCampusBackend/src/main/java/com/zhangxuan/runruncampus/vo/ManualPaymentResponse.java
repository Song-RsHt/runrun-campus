package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.CourierSettlementStatus;
import com.zhangxuan.runruncampus.enums.ManualPaymentStatus;
import java.time.Instant;






















public record ManualPaymentResponse(Long orderId, ManualPaymentStatus status, int amountCents, String payerReference, Instant requestedAt, Instant submittedAt, Instant reviewedAt, String reviewerName, String reviewNote, CourierSettlementStatus settlementStatus, Instant settledAt, String settlementReviewerName) {
}
