package com.zhangxuan.runrunv2.model;

import com.zhangxuan.runrunv2.enums.ServiceType;

/** 订单计价快照；仅承载内部协作所需的数据或校验逻辑。 */
public record OrderPricing(
        ServiceType serviceType,
        long baseCents,
        String zoneCode,
        long zoneSurchargeCents,
        long totalCents,
        long configVersion) {}
