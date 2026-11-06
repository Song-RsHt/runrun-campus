package com.zhangxuan.runrunv2.model;

import com.zhangxuan.runrunv2.enums.ServiceType;

public record OrderPricing(ServiceType serviceType, long baseCents, String zoneCode, long zoneSurchargeCents, long totalCents, long configVersion) {
}
