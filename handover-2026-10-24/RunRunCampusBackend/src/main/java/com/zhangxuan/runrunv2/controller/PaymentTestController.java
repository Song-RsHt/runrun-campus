package com.zhangxuan.runrunv2.controller;

import com.zhangxuan.runrunv2.dto.PaymentResultRequest;
import com.zhangxuan.runrunv2.security.IdentityPermission;
import com.zhangxuan.runrunv2.security.IdentityWebConfiguration;
import com.zhangxuan.runrunv2.service.PaymentService;
import com.zhangxuan.runrunv2.vo.PaymentAttemptVO;
import com.zhangxuan.runrunv2.vo.RefundOrderVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



































@RestController
@RequestMapping("/api/v2/test")
@IdentityPermission(role = "ADMIN")
@Profile("!production & !prod")
@ConditionalOnExpression("${rrc.payment.mock-enabled:false} && ('${rrc.identity.environment:}' == 'local' ||" + " '${rrc.identity.environment:}' == 'test' || '${rrc.identity.environment:}' ==" + " 'staging')")
public class PaymentTestController {

    private final PaymentService service;

    public PaymentTestController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/payment-attempts/{id}/result")
    public PaymentAttemptVO payment(HttpServletRequest request, @PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody PaymentResultRequest body) {
        return service.paymentResult(IdentityWebConfiguration.bearer(request), id, key, body);
    }

    @PostMapping("/refunds/{id}/result")
    public RefundOrderVO refund(HttpServletRequest request, @PathVariable String id, @RequestHeader("Idempotency-Key") String key, @RequestBody PaymentResultRequest body) {
        return service.refundResult(IdentityWebConfiguration.bearer(request), id, key, body);
    }
}
