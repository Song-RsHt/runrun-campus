package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.dto.MembershipPurchaseRequest;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.MembershipService;
import com.zhangxuan.runruncampus.service.PaymentService;
import com.zhangxuan.runruncampus.vo.MembershipOverview;
import com.zhangxuan.runruncampus.vo.PaymentResponse;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户会员套餐 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
@RestController
@RequestMapping("/api/v1/membership")
public class MembershipController {

    private final AuthService authService;
    private final MembershipService membershipService;
    private final PaymentService paymentService;

    public MembershipController(
            AuthService authService,
            MembershipService membershipService,
            PaymentService paymentService) {
        this.authService = authService;
        this.membershipService = membershipService;
        this.paymentService = paymentService;
    }

    @GetMapping
    public MembershipOverview overview(@RequestHeader("Authorization") String authorization) {
        return membershipService.overview(authService.requireUser(authorization));
    }

    @PostMapping("/purchase")
    public PaymentResponse purchase(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody MembershipPurchaseRequest request) {
        return paymentService.createMembershipPayment(
                authService.requireUser(authorization), request.plan());
    }
}
