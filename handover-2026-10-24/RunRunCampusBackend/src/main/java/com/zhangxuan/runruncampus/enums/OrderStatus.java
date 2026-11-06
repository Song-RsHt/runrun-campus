package com.zhangxuan.runruncampus.enums;

/** 订单创建与状态管理状态枚举；名称和取值沿用既有业务与协议定义。 */
public enum OrderStatus {
    PENDING_ASSIGNMENT,
    PENDING_REASSIGNMENT,
    ACCEPTED,
    PICKED_UP,
    COMPLETED,
    CANCELLED
}
