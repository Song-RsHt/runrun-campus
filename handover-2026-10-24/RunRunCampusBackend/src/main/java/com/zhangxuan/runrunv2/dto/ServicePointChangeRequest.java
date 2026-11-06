package com.zhangxuan.runrunv2.dto;

import java.math.BigDecimal;

/** 服务服务点变更请求参数；保留入口的字段、构造器及校验契约。 */
public record ServicePointChangeRequest(
        String code,
        String name,
        BigDecimal latitude,
        BigDecimal longitude,
        String coordinateSystem,
        Boolean enabled) {}
