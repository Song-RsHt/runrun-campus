package com.zhangxuan.runrunv2.dto.payment;

/** 取消请求参数；保留入口的字段、构造器及校验契约。 */
public record CancelRequest(Long expectedVersion, String reason) {}
