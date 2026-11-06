package com.zhangxuan.runrunv2.controller.payment;

import static com.zhangxuan.runrunv2.dto.response.payment.PaymentModels.*;

import com.zhangxuan.runrunv2.dto.request.payment.CancelRequest;
import com.zhangxuan.runrunv2.dto.request.payment.VersionRequest;
import com.zhangxuan.runrunv2.security.IdentityPermission;
import com.zhangxuan.runrunv2.security.IdentityWebConfiguration;
import com.zhangxuan.runrunv2.service.payment.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2")
@ConditionalOnProperty(name = "rrc.payment.mock-enabled", havingValue = "true")
@IdentityPermission(role = "USER")
public class PaymentController {

  private final PaymentService service;

  public PaymentController(PaymentService service) {
    this.service = service;
  }

  @GetMapping("/payments/{id}")
  public Summary get(HttpServletRequest request, @PathVariable String id) {
    return service.get(IdentityWebConfiguration.bearer(request), id);
  }

  @PostMapping("/payments/{id}/attempts")
  public Started start(
      HttpServletRequest request,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody VersionRequest body) {
    return service.start(IdentityWebConfiguration.bearer(request), id, key, body);
  }

  @PostMapping("/payments/{id}/query")
  public Summary query(
      HttpServletRequest request,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody VersionRequest body) {
    return service.queryPayment(IdentityWebConfiguration.bearer(request), id, key, body);
  }

  @PostMapping("/refunds/{id}/query")
  public Refund queryRefund(
      HttpServletRequest request,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody VersionRequest body) {
    return service.queryRefund(IdentityWebConfiguration.bearer(request), id, key, body);
  }

  @PostMapping("/orders/{id}/cancel")
  public Cancelled cancel(
      HttpServletRequest request,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody CancelRequest body) {
    return service.cancel(IdentityWebConfiguration.bearer(request), id, key, body);
  }
}
