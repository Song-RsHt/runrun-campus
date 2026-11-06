package com.zhangxuan.runrunv2.model;

import java.math.BigDecimal;

public record OrderAddress(String destination, String zoneCode, String zoneName, String servicePointCode, String servicePointName, BigDecimal latitude, BigDecimal longitude, String coordinateSystem) {
}
