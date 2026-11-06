package com.zhangxuan.runruncampus.enums.order;

/** 订单异常上报类型枚举；名称和取值沿用既有业务与协议定义。 */
public enum OrderIssueType {
    MISSING_ITEM,
    WRONG_ORDER,
    CANNOT_CONTACT,
    DELIVERY_DELAY,
    OTHER
}
