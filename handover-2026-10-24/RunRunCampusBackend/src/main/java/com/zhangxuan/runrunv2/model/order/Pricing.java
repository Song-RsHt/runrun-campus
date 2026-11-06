package com.zhangxuan.runrunv2.model.order;

import com.zhangxuan.runrunv2.enums.order.ServiceType;

public record Pricing(
    ServiceType serviceType,
    long baseCents,
    String zoneCode,
    long zoneSurchargeCents,
    long totalCents,
    long configVersion) {}
