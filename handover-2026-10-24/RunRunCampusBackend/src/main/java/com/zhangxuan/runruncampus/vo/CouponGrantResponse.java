package com.zhangxuan.runruncampus.vo;

import java.time.Instant;

/** 用户优惠券授权响应；保持现有 JSON 字段及序列化契约。 */
public record CouponGrantResponse(
        Long couponId,
        Long userId,
        String displayName,
        String title,
        int discountCents,
        int minimumOrderCents,
        Instant expiresAt) {}
