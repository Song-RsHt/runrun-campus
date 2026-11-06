package com.zhangxuan.runrunv2.dto;

import java.math.BigDecimal;

public record PointChange(String code, String name, BigDecimal latitude, BigDecimal longitude, String coordinateSystem, Boolean enabled) {
}
