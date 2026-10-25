package com.zhangxuan.runruncampus.membership;

import com.zhangxuan.runruncampus.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
import com.zhangxuan.runruncampus.payment.PaymentService;

@RestController
@RequestMapping("/api/v1/membership")
public class MembershipController {
    private final AuthService authService;
    private final MembershipService membershipService;
    private final PaymentService paymentService;

    public MembershipController(AuthService authService, MembershipService membershipService,
                                PaymentService paymentService) {
        this.authService = authService;
        this.membershipService = membershipService;
        this.paymentService = paymentService;
    }

    @GetMapping
    MembershipService.MembershipOverview overview(@RequestHeader("Authorization") String authorization) {
        return membershipService.overview(authService.requireUser(authorization));
    }

    @PostMapping("/purchase")
    PaymentService.PaymentResponse purchase(@RequestHeader("Authorization") String authorization,
                                             @Valid @RequestBody PurchaseRequest request) {
        return paymentService.createMembershipPayment(authService.requireUser(authorization), request.plan());
    }

    public record PurchaseRequest(@NotNull(message = "请选择月卡套餐") MembershipPlanType plan) { }
}
