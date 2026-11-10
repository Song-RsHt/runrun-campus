package com.zhangxuan.runruncampus.enums.order;

/** 订单创建与状态管理支付状态枚举；名称和取值沿用既有业务与协议定义。 */
public enum OrderPaymentStatus {
    UNPAID,
    PAID,
    REFUNDING,
    REFUNDED
}
