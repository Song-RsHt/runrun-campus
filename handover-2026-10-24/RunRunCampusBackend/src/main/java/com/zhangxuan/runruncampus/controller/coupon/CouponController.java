package com.zhangxuan.runruncampus.controller.coupon;

import com.zhangxuan.runruncampus.service.auth.AuthService;
import com.zhangxuan.runruncampus.service.coupon.CouponService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {
  private final AuthService authService;
  private final CouponService couponService;

  public CouponController(AuthService authService, CouponService couponService) {
    this.authService = authService;
    this.couponService = couponService;
  }

  @GetMapping
  public List<CouponService.CouponResponse> list(
      @RequestHeader("Authorization") String authorization) {
    return couponService.list(authService.requireUser(authorization));
  }
}
