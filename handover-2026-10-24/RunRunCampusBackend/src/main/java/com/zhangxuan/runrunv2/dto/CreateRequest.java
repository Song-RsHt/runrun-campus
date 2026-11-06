package com.zhangxuan.runrunv2.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zhangxuan.runrunv2.enums.ServiceType;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateRequest(ServiceType serviceType, String zoneCode, String servicePointCode, String destination, Long configVersion) {

    public QuoteRequest quote() {
        return new QuoteRequest(serviceType, zoneCode, servicePointCode, destination);
    }
}
