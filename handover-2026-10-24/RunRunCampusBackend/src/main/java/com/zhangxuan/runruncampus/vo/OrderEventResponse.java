package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.OrderStatus;
import java.time.Instant;





public record OrderEventResponse(Long id, OrderStatus status, String actorName, Instant createdAt) {
}
