package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.CouponService;
import com.zhangxuan.runruncampus.vo.CouponResponse;
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
    public List<CouponResponse> list(@RequestHeader("Authorization") String authorization) {
        return couponService.list(authService.requireUser(authorization));
    }
}
