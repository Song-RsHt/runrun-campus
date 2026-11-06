package com.zhangxuan.runruncampus.model.payment;

import com.zhangxuan.runruncampus.enums.payment.RefundStatus;

public record RefundNotificationResult(
    String outTradeNo, String outRefundNo, String refundId, int refundCents, RefundStatus status) {}
