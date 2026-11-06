package com.zhangxuan.runruncampus.service.admin;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.UserCoupon;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.enums.admin.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.admin.AdminAuditTargetType;
import com.zhangxuan.runruncampus.repository.UserCouponRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;































@Service
public class AdminCouponService {

  public static final String TITLE = "管理员赠送1元券";

  public static final int DISCOUNT_CENTS = 100;

  public static final int MINIMUM_ORDER_CENTS = 200;

  private final UserRepository userRepository;

  private final UserCouponRepository couponRepository;

  private final AdminAuditService auditService;

  public AdminCouponService(
      UserRepository userRepository,
      UserCouponRepository couponRepository,
      AdminAuditService auditService) {
    this.userRepository = userRepository;
    this.couponRepository = couponRepository;
    this.auditService = auditService;
  }

  @Transactional
  public GrantResponse issueOneYuan(AppUser administrator, Long userId) {
    AppUser customer =
        userRepository
            .findLockedById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    if (customer.getRole() != UserRole.CUSTOMER) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "只能向普通用户发放优惠券");
    }
    if (!customer.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "账号已停用，不能发放优惠券");
    }
    UserCoupon coupon =
        couponRepository.save(
            new UserCoupon(
                customer,
                TITLE,
                DISCOUNT_CENTS,
                MINIMUM_ORDER_CENTS,
                Instant.now().plus(30, ChronoUnit.DAYS)));
    auditService.record(
        administrator,
        AdminAuditAction.COUPON_ISSUED,
        AdminAuditTargetType.USER,
        customer.getId(),
        "向用户发放1元优惠券：" + customer.getDisplayName());
    return new GrantResponse(
        coupon.getId(),
        customer.getId(),
        customer.getDisplayName(),
        coupon.getTitle(),
        coupon.getDiscountCents(),
        coupon.getMinimumOrderCents(),
        coupon.getExpiresAt());
  }

  public record GrantResponse(
      Long couponId,
      Long userId,
      String displayName,
      String title,
      int discountCents,
      int minimumOrderCents,
      Instant expiresAt) {}
}
