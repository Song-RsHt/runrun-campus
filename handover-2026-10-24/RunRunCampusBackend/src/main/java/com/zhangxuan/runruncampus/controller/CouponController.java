package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.CouponService;
import com.zhangxuan.runruncampus.vo.CouponResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 用户优惠券 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
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
