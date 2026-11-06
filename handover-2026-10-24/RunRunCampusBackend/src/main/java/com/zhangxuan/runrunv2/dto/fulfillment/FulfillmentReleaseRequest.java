package com.zhangxuan.runrunv2.dto.fulfillment;

/** 订单履约释放请求参数；保留入口的字段、构造器及校验契约。 */
public record FulfillmentReleaseRequest(Long expectedVersion, String taskId, String reason) {}
