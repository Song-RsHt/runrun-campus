package com.zhangxuan.runruncampus.vo.admin;

import java.time.Instant;

/** 发放后的优惠券记录响应；保持现有 JSON 字段及序列化契约。 */
public record CouponGrantResponse(
        Long couponId,
        Long userId,
        String displayName,
        String title,
        int discountCents,
        int minimumOrderCents,
        Instant expiresAt) {}
