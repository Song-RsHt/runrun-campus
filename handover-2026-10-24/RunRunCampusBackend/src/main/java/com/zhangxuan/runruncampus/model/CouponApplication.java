package com.zhangxuan.runruncampus.model;

/** 实际抵扣金额及所用优惠券标识；仅承载内部协作所需的数据或校验逻辑。 */
public record CouponApplication(int discountCents, String couponTitle, Long couponId) {

    public CouponApplication(int discountCents, String couponTitle) {
        this(discountCents, couponTitle, null);
    }
}
