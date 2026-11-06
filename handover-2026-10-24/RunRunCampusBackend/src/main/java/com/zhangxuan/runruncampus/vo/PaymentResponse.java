package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.OrderPaymentStatus;
import com.zhangxuan.runruncampus.enums.PaymentBusinessType;
import com.zhangxuan.runruncampus.enums.PaymentStatus;
import java.time.Instant;

































public record PaymentResponse(String outTradeNo, PaymentBusinessType businessType, PaymentStatus status, int amountCents, OrderPaymentStatus orderPaymentStatus, Instant expiresAt, AppPaymentParameters payParameters) {
}
