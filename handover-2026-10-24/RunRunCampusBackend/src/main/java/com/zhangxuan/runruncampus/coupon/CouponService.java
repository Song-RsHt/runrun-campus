package com.zhangxuan.runruncampus.coupon;

import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class CouponService {
    private final UserCouponRepository couponRepository;

    public CouponService(UserCouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Transactional
    public List<CouponResponse> list(AppUser customer) {
        requireCustomer(customer);
        if (!couponRepository.existsByUser(customer)) {
            couponRepository.save(new UserCoupon(customer, "新用户立减券", 100, 200,
                    Instant.now().plus(30, ChronoUnit.DAYS)));
        }
        Instant now = Instant.now();
        return couponRepository.findByUserOrderByCreatedAtDesc(customer).stream()
                .map(coupon -> response(coupon, now)).toList();
    }

    @Transactional
    public CouponApplication apply(AppUser customer, Long couponId, int originalFeeCents) {
        requireCustomer(customer);
        if (couponId == null) return new CouponApplication(0, null);
        UserCoupon coupon = couponRepository.findLockedById(couponId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "优惠券不存在"));
        if (!customer.getId().equals(coupon.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权使用此优惠券");
        }
        if (coupon.isUsed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "优惠券已使用");
        }
        if (coupon.isExpired(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "优惠券已过期");
        }
        if (originalFeeCents < coupon.getMinimumOrderCents()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单金额未达到优惠券使用门槛");
        }
        coupon.markUsed();
        return new CouponApplication(Math.min(coupon.getDiscountCents(), originalFeeCents), coupon.getTitle(),
                coupon.getId());
    }

    @Transactional
    public void refund(AppUser customer, Long couponId) {
        requireCustomer(customer);
        UserCoupon coupon = couponRepository.findLockedById(couponId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "订单使用的优惠券不存在"));
        if (!customer.getId().equals(coupon.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单与优惠券归属不一致");
        }
        coupon.restore();
    }

    private CouponResponse response(UserCoupon coupon, Instant now) {
        return new CouponResponse(coupon.getId(), coupon.getTitle(), coupon.getDiscountCents(),
                coupon.getMinimumOrderCents(), coupon.isUsed(), coupon.isExpired(now),
                coupon.getExpiresAt(), coupon.getUsedAt());
    }

    private void requireCustomer(AppUser user) {
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有用户账号可以使用优惠券");
        }
    }

    public record CouponResponse(Long id, String title, int discountCents, int minimumOrderCents,
                                 boolean used, boolean expired, Instant expiresAt, Instant usedAt) { }

    public record CouponApplication(int discountCents, String couponTitle, Long couponId) {
        public CouponApplication(int discountCents, String couponTitle) {
            this(discountCents, couponTitle, null);
        }
    }
}
