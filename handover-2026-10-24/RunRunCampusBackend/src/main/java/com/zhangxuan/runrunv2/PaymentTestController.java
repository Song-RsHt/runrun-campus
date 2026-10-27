package com.zhangxuan.runrunv2;

import static com.zhangxuan.runrunv2.PaymentModels.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/test")
@IdentityPermission(role="ADMIN")
@org.springframework.context.annotation.Profile("!production & !prod")
@ConditionalOnExpression("${rrc.payment.mock-enabled:false} && ('${rrc.identity.environment:}' == 'local' || '${rrc.identity.environment:}' == 'test' || '${rrc.identity.environment:}' == 'staging')")
public class PaymentTestController {
    private final PaymentService service;
    public PaymentTestController(PaymentService service) { this.service=service; }
    @PostMapping("/payment-attempts/{id}/result")
    public Attempt payment(HttpServletRequest request,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody ResultRequest body) { return service.paymentResult(IdentityWebConfiguration.bearer(request),id,key,body); }
    @PostMapping("/refunds/{id}/result")
    public Refund refund(HttpServletRequest request,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody ResultRequest body) { return service.refundResult(IdentityWebConfiguration.bearer(request),id,key,body); }
}
