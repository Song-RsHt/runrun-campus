package com.zhangxuan.runruncampus.vo;

import java.time.Instant;
import java.util.List;

/** 管理员订单查询与导出详情响应；保持现有 JSON 字段及序列化契约。 */
public record AdminOrderDetailResponse(
        AdminOrderListItem order,
        String pickupCode,
        String pickupLocation,
        int originalFeeCents,
        int discountCents,
        String couponTitle,
        boolean membershipUsed,
        String membershipPlanTitle,
        Integer ratingScore,
        String ratingComment,
        Instant ratedAt,
        Double courierLatitude,
        Double courierLongitude,
        Instant courierLocationUpdatedAt,
        String payerReference,
        Instant paymentRequestedAt,
        Instant paymentSubmittedAt,
        Instant paymentReviewedAt,
        String paymentReviewerName,
        String paymentReviewNote,
        Instant courierSettledAt,
        String courierSettlementReviewerName,
        List<OrderEventItem> events,
        List<OrderIssueItem> issues,
        List<OrderProofItem> proofs) {}
