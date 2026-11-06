package com.zhangxuan.runruncampus.integration.payment;

import com.zhangxuan.runruncampus.model.payment.AppPayParameters;
import com.zhangxuan.runruncampus.model.payment.NotificationHeaders;
import com.zhangxuan.runruncampus.model.payment.RefundNotificationResult;
import com.zhangxuan.runruncampus.model.payment.RefundResult;
import com.zhangxuan.runruncampus.model.payment.TradeResult;
import java.time.Instant;

public interface WechatPayGateway {

  AppPayParameters prepay(
      String outTradeNo, String description, int amountCents, String attach, Instant expiresAt);

  TradeResult query(String outTradeNo);

  void close(String outTradeNo);

  RefundResult refund(
      String outTradeNo, String outRefundNo, int totalCents, int refundCents, String reason);

  RefundResult queryRefund(String outRefundNo);

  TradeResult parsePaymentNotification(NotificationHeaders headers, String body);

  RefundNotificationResult parseRefundNotification(NotificationHeaders headers, String body);
}
