package com.zhangxuan.runrunv2.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zhangxuan.runrunv2.enums.ServiceType;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateOrderRequest(ServiceType serviceType, String zoneCode, String servicePointCode, String destination, Long configVersion) {

    public OrderQuoteRequest quote() {
        return new OrderQuoteRequest(serviceType, zoneCode, servicePointCode, destination);
    }
}
