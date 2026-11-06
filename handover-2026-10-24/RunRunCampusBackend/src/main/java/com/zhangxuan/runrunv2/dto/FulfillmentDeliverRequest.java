package com.zhangxuan.runrunv2.dto;

/** 订单履约送达请求参数；保留入口的字段、构造器及校验契约。 */
public record FulfillmentDeliverRequest(
        Long expectedVersion, String taskId, String deliveryProofId, String locationEventId) {}
