package com.zhangxuan.runruncampus.coupon;

import com.zhangxuan.runruncampus.auth.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    List<CouponService.CouponResponse> list(@RequestHeader("Authorization") String authorization) {
        return couponService.list(authService.requireUser(authorization));
    }
}
