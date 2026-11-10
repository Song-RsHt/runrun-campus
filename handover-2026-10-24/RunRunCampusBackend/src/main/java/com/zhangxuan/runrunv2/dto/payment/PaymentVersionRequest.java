package com.zhangxuan.runrunv2.dto.payment;

/** 支付或退款的预期并发版本请求参数；保留入口的字段、构造器及校验契约。 */
public record PaymentVersionRequest(Long expectedVersion) {}
