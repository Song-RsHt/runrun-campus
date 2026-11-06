package com.zhangxuan.runruncampus.dto.response.order;

import com.zhangxuan.runruncampus.enums.order.OrderStatus;
import java.time.Instant;

public record OrderEventResponse(
    Long id, OrderStatus status, String actorName, Instant createdAt) {}
