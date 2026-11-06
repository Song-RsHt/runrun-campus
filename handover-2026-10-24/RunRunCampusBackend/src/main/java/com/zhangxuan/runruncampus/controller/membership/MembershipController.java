package com.zhangxuan.runruncampus.controller.membership;

import com.zhangxuan.runruncampus.dto.request.membership.PurchaseRequest;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.membership.MembershipService;
import com.zhangxuan.runruncampus.service.membership.MembershipService.MembershipOverview;
import com.zhangxuan.runruncampus.service.payment.PaymentService;
import com.zhangxuan.runruncampus.service.payment.PaymentService.PaymentResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;









@RestController
@RequestMapping("/api/v1/membership")
public class MembershipController {

  private final AuthService authService;

  private final MembershipService membershipService;

  private final PaymentService paymentService;

  public MembershipController(
      AuthService authService, MembershipService membershipService, PaymentService paymentService) {
    this.authService = authService;
    this.membershipService = membershipService;
    this.paymentService = paymentService;
  }

  @GetMapping
  public MembershipOverview overview(
      @RequestHeader("Authorization") String authorization) {
    return membershipService.overview(authService.requireUser(authorization));
  }

  @PostMapping("/purchase")
  public PaymentResponse purchase(
      @RequestHeader("Authorization") String authorization,
      @Valid @RequestBody PurchaseRequest request) {
    return paymentService.createMembershipPayment(
        authService.requireUser(authorization), request.plan());
  }
}
