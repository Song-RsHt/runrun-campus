package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.MembershipPlanType;
import java.time.Instant;


















public record ActiveMembershipResponse(Long id, MembershipPlanType plan, String planTitle, int totalTrips, int remainingTrips, int priceCents, Instant purchasedAt, Instant expiresAt, boolean active) {
}
