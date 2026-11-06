package com.zhangxuan.runruncampus.vo;

/** 订单创建与状态管理未读响应；保持现有 JSON 字段及序列化契约。 */
public record OrderUnreadVO(Long orderId, long unreadCount) {}
