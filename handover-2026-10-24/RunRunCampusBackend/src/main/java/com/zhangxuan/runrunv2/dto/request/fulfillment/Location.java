package com.zhangxuan.runrunv2.dto.request.fulfillment;

import com.zhangxuan.runrunv2.enums.fulfillment.EventType;
import java.math.BigDecimal;

public record Location(
    Long expectedVersion,
    String taskId,
    EventType eventType,
    BigDecimal latitude,
    BigDecimal longitude,
    String coordinateSystem,
    String requestId) {}
