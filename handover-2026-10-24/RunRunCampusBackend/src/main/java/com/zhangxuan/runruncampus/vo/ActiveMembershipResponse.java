package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.MembershipPlanType;

import java.time.Instant;

/** 有效会员响应；保持现有 JSON 字段及序列化契约。 */
public record ActiveMembershipResponse(
        Long id,
        MembershipPlanType plan,
        String planTitle,
        int totalTrips,
        int remainingTrips,
        int priceCents,
        Instant purchasedAt,
        Instant expiresAt,
        boolean active) {}
