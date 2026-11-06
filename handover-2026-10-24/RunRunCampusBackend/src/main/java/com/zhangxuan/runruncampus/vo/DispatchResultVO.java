package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.OrderStatus;
import java.time.Instant;































public record DispatchResultVO(long orderId, String orderNo, OrderStatus status, long courierId, String courierName, Instant dispatchedAt) {
}
