package com.zhangxuan.runruncampus.vo;

import java.time.Instant;


















public record CouponGrantResponse(Long couponId, Long userId, String displayName, String title, int discountCents, int minimumOrderCents, Instant expiresAt) {
}
