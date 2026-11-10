package com.zhangxuan.runruncampus.enums.order;

/** 人工支付状态枚举；名称和取值沿用既有业务与协议定义。 */
public enum ManualPaymentStatus {
    NOT_REQUESTED,
    AWAITING_PAYMENT,
    SUBMITTED,
    CONFIRMED,
    REJECTED
}
