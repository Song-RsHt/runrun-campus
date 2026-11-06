package com.zhangxuan.runrunv2.dto;

import com.zhangxuan.runrunv2.enums.ServiceType;

/** 订单报价请求参数；保留入口的字段、构造器及校验契约。 */
public record OrderQuoteRequest(
        ServiceType serviceType, String zoneCode, String servicePointCode, String destination) {}
