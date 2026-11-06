package com.zhangxuan.runrunv2.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zhangxuan.runrunv2.enums.ServiceType;

/** 创建订单请求参数；保留入口的字段、构造器及校验契约。 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateOrderRequest(
        ServiceType serviceType,
        String zoneCode,
        String servicePointCode,
        String destination,
        Long configVersion) {

    public OrderQuoteRequest quote() {
        return new OrderQuoteRequest(serviceType, zoneCode, servicePointCode, destination);
    }
}
