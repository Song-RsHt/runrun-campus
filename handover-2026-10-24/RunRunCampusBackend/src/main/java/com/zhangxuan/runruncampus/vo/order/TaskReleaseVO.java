package com.zhangxuan.runruncampus.vo.order;

import com.zhangxuan.runruncampus.enums.order.OrderStatus;

/** 任务释放响应；保持现有 JSON 字段及序列化契约。 */
public record TaskReleaseVO(Long orderId, String orderNo, OrderStatus status) {}
