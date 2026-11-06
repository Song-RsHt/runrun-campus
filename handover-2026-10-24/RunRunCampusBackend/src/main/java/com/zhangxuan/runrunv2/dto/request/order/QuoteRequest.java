package com.zhangxuan.runrunv2.dto.request.order;

import com.zhangxuan.runrunv2.enums.order.ServiceType;

public record QuoteRequest(
    ServiceType serviceType, String zoneCode, String servicePointCode, String destination) {}
