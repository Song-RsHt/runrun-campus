package com.zhangxuan.runruncampus.payment;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

final class DisabledWechatPayGateway implements WechatPayGateway {
    private RuntimeException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信支付尚未完成商户配置");
    }
    @Override public AppPayParameters prepay(String a, String b, int c, String d, Instant e) { throw unavailable(); }
    @Override public TradeResult query(String a) { throw unavailable(); }
    @Override public void close(String a) { throw unavailable(); }
    @Override public RefundResult refund(String a, String b, int c, int d, String e) { throw unavailable(); }
    @Override public RefundResult queryRefund(String a) { throw unavailable(); }
    @Override public TradeResult parsePaymentNotification(NotificationHeaders a, String b) { throw unavailable(); }
    @Override public RefundNotificationResult parseRefundNotification(NotificationHeaders a, String b) { throw unavailable(); }
}
