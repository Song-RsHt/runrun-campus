package com.zhangxuan.runruncampus.integration.payment;

import com.zhangxuan.runruncampus.model.AppPayParameters;
import com.zhangxuan.runruncampus.model.NotificationHeaders;
import com.zhangxuan.runruncampus.model.RefundNotificationResult;
import com.zhangxuan.runruncampus.model.RefundResult;
import com.zhangxuan.runruncampus.model.TradeResult;

import java.time.Instant;

/** 微信支付网关组件；保持当前应用的配置与协作边界。 */
public interface WechatPayGateway {

    AppPayParameters prepay(
            String outTradeNo,
            String description,
            int amountCents,
            String attach,
            Instant expiresAt);

    TradeResult query(String outTradeNo);

    void close(String outTradeNo);

    RefundResult refund(
            String outTradeNo, String outRefundNo, int totalCents, int refundCents, String reason);

    RefundResult queryRefund(String outRefundNo);

    TradeResult parsePaymentNotification(NotificationHeaders headers, String body);

    RefundNotificationResult parseRefundNotification(NotificationHeaders headers, String body);
}
