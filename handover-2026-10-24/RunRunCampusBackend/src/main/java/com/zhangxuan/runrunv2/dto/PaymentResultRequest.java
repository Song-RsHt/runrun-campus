package com.zhangxuan.runrunv2.dto;

import com.zhangxuan.runrunv2.enums.Result;

/** 支付回执及退款结果请求参数；保留入口的字段、构造器及校验契约。 */
public record PaymentResultRequest(Long expectedVersion, Result result, String eventId) {}
