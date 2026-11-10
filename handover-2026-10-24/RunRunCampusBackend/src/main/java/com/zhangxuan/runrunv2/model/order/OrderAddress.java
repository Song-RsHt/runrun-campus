package com.zhangxuan.runrunv2.model.order;

import java.math.BigDecimal;

/** 订单地址快照；仅承载内部协作所需的数据或校验逻辑。 */
public record OrderAddress(
        String destination,
        String zoneCode,
        String zoneName,
        String servicePointCode,
        String servicePointName,
        BigDecimal latitude,
        BigDecimal longitude,
        String coordinateSystem) {}
