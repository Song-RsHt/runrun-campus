package com.zhangxuan.runrunv2.vo.payment;

/** 已发起支付响应；保持现有 JSON 字段及序列化契约。 */
public record StartedPaymentVO(PaymentOrderVO payment, PaymentAttemptVO attempt) {}
