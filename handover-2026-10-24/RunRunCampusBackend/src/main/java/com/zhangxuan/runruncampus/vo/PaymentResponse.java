package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.OrderPaymentStatus;
import com.zhangxuan.runruncampus.enums.PaymentBusinessType;
import com.zhangxuan.runruncampus.enums.PaymentStatus;

import java.time.Instant;

/** 支付回执及退款响应；保持现有 JSON 字段及序列化契约。 */
public record PaymentResponse(
        String outTradeNo,
        PaymentBusinessType businessType,
        PaymentStatus status,
        int amountCents,
        OrderPaymentStatus orderPaymentStatus,
        Instant expiresAt,
        AppPaymentParameters payParameters) {}
