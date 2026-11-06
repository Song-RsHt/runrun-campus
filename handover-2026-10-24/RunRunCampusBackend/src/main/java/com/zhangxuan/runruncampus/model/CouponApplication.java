package com.zhangxuan.runruncampus.model;
















public record CouponApplication(int discountCents, String couponTitle, Long couponId) {

    public CouponApplication(int discountCents, String couponTitle) {
        this(discountCents, couponTitle, null);
    }
}
