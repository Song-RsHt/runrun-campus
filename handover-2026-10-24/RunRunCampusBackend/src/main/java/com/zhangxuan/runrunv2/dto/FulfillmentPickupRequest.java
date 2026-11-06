package com.zhangxuan.runrunv2.dto;

/** 订单履约取件请求参数；保留入口的字段、构造器及校验契约。 */
public record FulfillmentPickupRequest(Long expectedVersion, String taskId, String pickupProofId) {}
