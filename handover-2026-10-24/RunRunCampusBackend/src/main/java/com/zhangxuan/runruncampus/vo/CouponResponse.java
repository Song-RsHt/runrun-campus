package com.zhangxuan.runruncampus.vo;

import java.time.Instant;

/** 用户优惠券响应；保持现有 JSON 字段及序列化契约。 */
public record CouponResponse(
        Long id,
        String title,
        int discountCents,
        int minimumOrderCents,
        boolean used,
        boolean expired,
        Instant expiresAt,
        Instant usedAt) {}
