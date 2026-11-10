package com.zhangxuan.runrunv2.dto.fulfillment;

import com.zhangxuan.runrunv2.enums.fulfillment.Decision;

/** 订单履约邀请响应请求参数；保留入口的字段、构造器及校验契约。 */
public record FulfillmentRespondRequest(Long expectedVersion, Decision decision) {}
