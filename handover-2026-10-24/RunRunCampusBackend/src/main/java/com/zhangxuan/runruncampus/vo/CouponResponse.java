package com.zhangxuan.runruncampus.vo;

import java.time.Instant;















public record CouponResponse(Long id, String title, int discountCents, int minimumOrderCents, boolean used, boolean expired, Instant expiresAt, Instant usedAt) {
}
