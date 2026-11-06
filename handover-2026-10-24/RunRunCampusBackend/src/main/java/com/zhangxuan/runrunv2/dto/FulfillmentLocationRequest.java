package com.zhangxuan.runrunv2.dto;

import com.zhangxuan.runrunv2.enums.EventType;
import java.math.BigDecimal;

public record FulfillmentLocationRequest(Long expectedVersion, String taskId, EventType eventType, BigDecimal latitude, BigDecimal longitude, String coordinateSystem, String requestId) {
}
