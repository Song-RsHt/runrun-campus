package com.zhangxuan.runruncampus.payment;

import java.time.Instant;

public interface WechatPayGateway {
    AppPayParameters prepay(String outTradeNo, String description, int amountCents, String attach, Instant expiresAt);
    TradeResult query(String outTradeNo);
    void close(String outTradeNo);
    RefundResult refund(String outTradeNo, String outRefundNo, int totalCents, int refundCents, String reason);
    RefundResult queryRefund(String outRefundNo);
    TradeResult parsePaymentNotification(NotificationHeaders headers, String body);
    RefundNotificationResult parseRefundNotification(NotificationHeaders headers, String body);

    record AppPayParameters(String appId, String partnerId, String prepayId, String packageValue,
                            String nonceStr, String timestamp, String sign) { }
    record TradeResult(String appId, String merchantId, String outTradeNo, String transactionId,
                       int amountCents, boolean paid) { }
    record RefundResult(String refundId, RefundStatus status) { }
    record RefundNotificationResult(String outTradeNo, String outRefundNo, String refundId,
                                    int refundCents, RefundStatus status) { }
    record NotificationHeaders(String serial, String timestamp, String nonce, String signature, String signType) { }
}
