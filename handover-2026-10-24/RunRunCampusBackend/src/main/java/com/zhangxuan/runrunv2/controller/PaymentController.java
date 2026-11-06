package com.zhangxuan.runrunv2.controller;

import com.zhangxuan.runrunv2.dto.CancelRequest;
import com.zhangxuan.runrunv2.dto.PaymentVersionRequest;
import com.zhangxuan.runrunv2.security.IdentityPermission;
import com.zhangxuan.runrunv2.security.IdentityWebConfiguration;
import com.zhangxuan.runrunv2.service.PaymentService;
import com.zhangxuan.runrunv2.vo.CancelledOrderVO;
import com.zhangxuan.runrunv2.vo.PaymentSummaryVO;
import com.zhangxuan.runrunv2.vo.RefundOrderVO;
import com.zhangxuan.runrunv2.vo.StartedPaymentVO;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 支付回执及退款 HTTP 入口；绑定请求并委托业务服务，不直接访问数据库。 */
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
    public PaymentSummaryVO get(HttpServletRequest request, @PathVariable String id) {
        return service.get(IdentityWebConfiguration.bearer(request), id);
    }

    @PostMapping("/payments/{id}/attempts")
    public StartedPaymentVO start(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody PaymentVersionRequest body) {
        return service.start(IdentityWebConfiguration.bearer(request), id, key, body);
    }

    @PostMapping("/payments/{id}/query")
    public PaymentSummaryVO query(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody PaymentVersionRequest body) {
        return service.queryPayment(IdentityWebConfiguration.bearer(request), id, key, body);
    }

    @PostMapping("/refunds/{id}/query")
    public RefundOrderVO queryRefund(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody PaymentVersionRequest body) {
        return service.queryRefund(IdentityWebConfiguration.bearer(request), id, key, body);
    }

    @PostMapping("/orders/{id}/cancel")
    public CancelledOrderVO cancel(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody CancelRequest body) {
        return service.cancel(IdentityWebConfiguration.bearer(request), id, key, body);
    }
}
