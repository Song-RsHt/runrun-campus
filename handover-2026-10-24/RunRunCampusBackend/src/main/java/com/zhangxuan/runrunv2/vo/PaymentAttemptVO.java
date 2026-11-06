package com.zhangxuan.runrunv2.vo;

/** 支付尝试及渠道处理状态响应；保持现有 JSON 字段及序列化契约。 */
public record PaymentAttemptVO(
        String id, String paymentOrderId, int attemptNo, String status, long version) {}
