package com.zhangxuan.runrunv2.vo.order;

import java.math.BigDecimal;

/** 服务服务点响应；保持现有 JSON 字段及序列化契约。 */
public record ServicePointVO(
        String id,
        String code,
        String name,
        BigDecimal latitude,
        BigDecimal longitude,
        String coordinateSystem,
        boolean enabled,
        long configVersion,
        long version) {}
