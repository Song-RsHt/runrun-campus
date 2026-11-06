package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.constant.AdminCouponConstants;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.UserCoupon;
import com.zhangxuan.runruncampus.enums.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.AdminAuditTargetType;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.repository.UserCouponRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.service.AdminAuditService;
import com.zhangxuan.runruncampus.vo.CouponGrantResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.service.AdminCouponService;

@Service
public class AdminCouponServiceImpl implements AdminCouponService {

    private final UserRepository userRepository;

    private final UserCouponRepository couponRepository;

    private final AdminAuditService auditService;

    public AdminCouponServiceImpl(UserRepository userRepository, UserCouponRepository couponRepository, AdminAuditService auditService) {
        this.userRepository = userRepository;
        this.couponRepository = couponRepository;
        this.auditService = auditService;
    }

    @Transactional
    @Override
    public CouponGrantResponse issueOneYuan(AppUser administrator, Long userId) {
        AppUser customer = userRepository.findLockedById(userId).orElseThrow(() -> new BusinessFailure(404, "用户不存在"));
        if (customer.getRole() != UserRole.CUSTOMER) {
            throw new BusinessFailure(409, "只能向普通用户发放优惠券");
        }
        if (!customer.isEnabled()) {
            throw new BusinessFailure(409, "账号已停用，不能发放优惠券");
        }
        UserCoupon coupon = couponRepository.save(new UserCoupon(customer, AdminCouponConstants.TITLE, AdminCouponConstants.DISCOUNT_CENTS, AdminCouponConstants.MINIMUM_ORDER_CENTS, Instant.now().plus(30, ChronoUnit.DAYS)));
        auditService.record(administrator, AdminAuditAction.COUPON_ISSUED, AdminAuditTargetType.USER, customer.getId(), "向用户发放1元优惠券：" + customer.getDisplayName());
        return new CouponGrantResponse(coupon.getId(), customer.getId(), customer.getDisplayName(), coupon.getTitle(), coupon.getDiscountCents(), coupon.getMinimumOrderCents(), coupon.getExpiresAt());
    }
}
