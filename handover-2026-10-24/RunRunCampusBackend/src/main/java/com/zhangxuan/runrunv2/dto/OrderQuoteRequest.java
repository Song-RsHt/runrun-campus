package com.zhangxuan.runrunv2.dto;

import com.zhangxuan.runrunv2.enums.ServiceType;

public record OrderQuoteRequest(ServiceType serviceType, String zoneCode, String servicePointCode, String destination) {
}
