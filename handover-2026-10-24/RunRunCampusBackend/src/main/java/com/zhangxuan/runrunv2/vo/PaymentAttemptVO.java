package com.zhangxuan.runrunv2.vo;

/** 支付回执及退款尝试响应；保持现有 JSON 字段及序列化契约。 */
public record PaymentAttemptVO(
        String id, String paymentOrderId, int attemptNo, String status, long version) {}
