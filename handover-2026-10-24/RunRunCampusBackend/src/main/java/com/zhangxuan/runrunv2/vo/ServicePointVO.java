package com.zhangxuan.runrunv2.vo;

import java.math.BigDecimal;











public record ServicePointVO(String id, String code, String name, BigDecimal latitude, BigDecimal longitude, String coordinateSystem, boolean enabled, long configVersion, long version) {
}
