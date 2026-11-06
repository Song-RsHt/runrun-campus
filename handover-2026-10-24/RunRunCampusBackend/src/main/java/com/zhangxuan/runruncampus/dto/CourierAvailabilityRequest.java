package com.zhangxuan.runruncampus.dto;

/** 骑手接单开关请求参数；保留入口的字段、构造器及校验契约。 */
public record CourierAvailabilityRequest(boolean acceptingOrders) {}
