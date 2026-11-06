package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.CourierSettlementStatus;
import com.zhangxuan.runruncampus.enums.DeliveryZone;
import com.zhangxuan.runruncampus.enums.ManualPaymentStatus;
import com.zhangxuan.runruncampus.enums.OrderPaymentMethod;
import com.zhangxuan.runruncampus.enums.OrderPaymentStatus;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.enums.PickupStation;
import com.zhangxuan.runruncampus.enums.ServiceType;
import java.time.Instant;
import java.util.List;

public record OrderResponse(Long id, String orderNo, String merchant, String pickupCode, String pickupLocation, String destination, PickupStation pickupStation, DeliveryZone deliveryZone, ServiceType serviceType, OrderStatus status, int originalFeeCents, int discountCents, int feeCents, OrderPaymentMethod paymentMethod, OrderPaymentStatus paymentStatus, int paidCents, ManualPaymentStatus manualPaymentStatus, String payerReference, Instant paymentRequestedAt, Instant paymentSubmittedAt, Instant paymentReviewedAt, String paymentReviewNote, CourierSettlementStatus courierSettlementStatus, Instant courierSettledAt, String couponTitle, boolean membershipUsed, String membershipPlanTitle, String courierName, boolean packageSizeReviewProofUploaded, boolean pickupProofUploaded, boolean deliveryProofUploaded, boolean packageSizeConfirmed, ServiceType proposedPackageServiceType, Integer ratingScore, String ratingComment, Instant ratedAt, List<RefundResponse> refunds, Instant createdAt, Instant updatedAt) {
}
