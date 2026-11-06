package com.zhangxuan.runrunv2.dto;

import com.zhangxuan.runrunv2.enums.EventType;

import java.math.BigDecimal;

/** 订单履约位置请求参数；保留入口的字段、构造器及校验契约。 */
public record FulfillmentLocationRequest(
        Long expectedVersion,
        String taskId,
        EventType eventType,
        BigDecimal latitude,
        BigDecimal longitude,
        String coordinateSystem,
        String requestId) {}
