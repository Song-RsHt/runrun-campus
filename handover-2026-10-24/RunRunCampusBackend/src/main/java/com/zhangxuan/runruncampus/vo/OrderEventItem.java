package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.OrderStatus;
import java.time.Instant;

public record OrderEventItem(Long id, OrderStatus status, UserSummary actor, Instant createdAt) {
}
