package com.zhangxuan.runruncampus.vo;

import java.time.Instant;
import java.util.List;

public record AdminOrderDetailResponse(AdminOrderListItem order, String pickupCode, String pickupLocation, int originalFeeCents, int discountCents, String couponTitle, boolean membershipUsed, String membershipPlanTitle, Integer ratingScore, String ratingComment, Instant ratedAt, Double courierLatitude, Double courierLongitude, Instant courierLocationUpdatedAt, String payerReference, Instant paymentRequestedAt, Instant paymentSubmittedAt, Instant paymentReviewedAt, String paymentReviewerName, String paymentReviewNote, Instant courierSettledAt, String courierSettlementReviewerName, List<OrderEventItem> events, List<OrderIssueItem> issues, List<OrderProofItem> proofs) {
}
