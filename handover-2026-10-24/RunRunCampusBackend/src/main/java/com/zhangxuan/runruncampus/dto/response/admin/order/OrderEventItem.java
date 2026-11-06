package com.zhangxuan.runruncampus.dto.response.admin.order;

import com.zhangxuan.runruncampus.enums.order.OrderStatus;
import java.time.Instant;

public record OrderEventItem(Long id, OrderStatus status, UserSummary actor, Instant createdAt) {}
