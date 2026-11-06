package com.zhangxuan.runrunv2.dto;

/** 支付回执及退款版本请求参数；保留入口的字段、构造器及校验契约。 */
public record PaymentVersionRequest(Long expectedVersion) {}
