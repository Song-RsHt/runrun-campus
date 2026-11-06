package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.OrderStatus;





















public record TaskReleaseVO(Long orderId, String orderNo, OrderStatus status) {
}
