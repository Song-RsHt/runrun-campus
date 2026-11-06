package com.zhangxuan.runruncampus.controller;

import com.zhangxuan.runruncampus.model.NotificationHeaders;
import com.zhangxuan.runruncampus.service.AuthService;
import com.zhangxuan.runruncampus.service.PaymentService;
import com.zhangxuan.runruncampus.vo.PaymentResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;















@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final AuthService authService;

    private final PaymentService paymentService;

    public PaymentController(AuthService authService, PaymentService paymentService) {
        this.authService = authService;
        this.paymentService = paymentService;
    }

    @PostMapping("/orders/{orderId}/wechat")
    public PaymentResponse orderPayment(@RequestHeader("Authorization") String authorization, @PathVariable Long orderId) {
        return paymentService.createOrderPayment(authService.requireUser(authorization), orderId);
    }

    @GetMapping("/{outTradeNo}")
    public PaymentResponse status(@RequestHeader("Authorization") String authorization, @PathVariable String outTradeNo) {
        return paymentService.status(authService.requireUser(authorization), outTradeNo);
    }

    @PostMapping("/wechat/notify")
    public Map<String, String> notify(@RequestHeader("Wechatpay-Serial") String serial, @RequestHeader("Wechatpay-Timestamp") String timestamp, @RequestHeader("Wechatpay-Nonce") String nonce, @RequestHeader("Wechatpay-Signature") String signature, @RequestHeader(value = "Wechatpay-Signature-Type", defaultValue = "WECHATPAY2-SHA256-RSA2048") String signType, @RequestBody String body) {
        paymentService.acceptPaymentNotification(new NotificationHeaders(serial, timestamp, nonce, signature, signType), body);
        return Map.of("code", "SUCCESS", "message", "成功");
    }

    @PostMapping("/wechat/refund-notify")
    public Map<String, String> refundNotify(@RequestHeader("Wechatpay-Serial") String serial, @RequestHeader("Wechatpay-Timestamp") String timestamp, @RequestHeader("Wechatpay-Nonce") String nonce, @RequestHeader("Wechatpay-Signature") String signature, @RequestHeader(value = "Wechatpay-Signature-Type", defaultValue = "WECHATPAY2-SHA256-RSA2048") String signType, @RequestBody String body) {
        paymentService.acceptRefundNotification(new NotificationHeaders(serial, timestamp, nonce, signature, signType), body);
        return Map.of("code", "SUCCESS", "message", "成功");
    }
}
