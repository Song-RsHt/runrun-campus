package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.UserCoupon;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.model.CouponApplication;
import com.zhangxuan.runruncampus.repository.UserCouponRepository;
import com.zhangxuan.runruncampus.vo.CouponResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.service.CouponService;

@Service
public class CouponServiceImpl implements CouponService {

    private final UserCouponRepository couponRepository;

    public CouponServiceImpl(UserCouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Transactional
    @Override
    public List<CouponResponse> list(AppUser customer) {
        requireCustomer(customer);
        if (!couponRepository.existsByUser(customer)) {
            couponRepository.save(new UserCoupon(customer, "新用户立减券", 100, 200, Instant.now().plus(30, ChronoUnit.DAYS)));
        }
        Instant now = Instant.now();
        return couponRepository.findByUserOrderByCreatedAtDesc(customer).stream().map(coupon -> response(coupon, now)).toList();
    }

    @Transactional
    @Override
    public CouponApplication apply(AppUser customer, Long couponId, int originalFeeCents) {
        requireCustomer(customer);
        if (couponId == null)
            return new CouponApplication(0, null);
        UserCoupon coupon = couponRepository.findLockedById(couponId).orElseThrow(() -> new BusinessFailure(404, "优惠券不存在"));
        if (!customer.getId().equals(coupon.getUser().getId())) {
            throw new BusinessFailure(403, "无权使用此优惠券");
        }
        if (coupon.isUsed()) {
            throw new BusinessFailure(409, "优惠券已使用");
        }
        if (coupon.isExpired(Instant.now())) {
            throw new BusinessFailure(409, "优惠券已过期");
        }
        if (originalFeeCents < coupon.getMinimumOrderCents()) {
            throw new BusinessFailure(409, "订单金额未达到优惠券使用门槛");
        }
        coupon.markUsed();
        return new CouponApplication(Math.min(coupon.getDiscountCents(), originalFeeCents), coupon.getTitle(), coupon.getId());
    }

    @Transactional
    @Override
    public void refund(AppUser customer, Long couponId) {
        requireCustomer(customer);
        UserCoupon coupon = couponRepository.findLockedById(couponId).orElseThrow(() -> new BusinessFailure(409, "订单使用的优惠券不存在"));
        if (!customer.getId().equals(coupon.getUser().getId())) {
            throw new BusinessFailure(409, "订单与优惠券归属不一致");
        }
        coupon.restore();
    }

    private CouponResponse response(UserCoupon coupon, Instant now) {
        return new CouponResponse(coupon.getId(), coupon.getTitle(), coupon.getDiscountCents(), coupon.getMinimumOrderCents(), coupon.isUsed(), coupon.isExpired(now), coupon.getExpiresAt(), coupon.getUsedAt());
    }

    private void requireCustomer(AppUser user) {
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new BusinessFailure(403, "只有用户账号可以使用优惠券");
        }
    }
}
