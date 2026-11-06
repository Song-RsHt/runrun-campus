package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.OrderMessageType;
import java.time.Instant;
















public record OrderMessageVO(Long id, Long orderId, String orderNo, OrderMessageType type, String title, String content, boolean read, Instant createdAt, Instant readAt) {
}
