package com.zhangxuan.runrunv2.dto;

/** 订单履约版本请求参数；保留入口的字段、构造器及校验契约。 */
public record FulfillmentVersionRequest(Long expectedVersion) {}
