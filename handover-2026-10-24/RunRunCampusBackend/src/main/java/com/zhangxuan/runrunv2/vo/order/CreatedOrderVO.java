package com.zhangxuan.runrunv2.vo.order;

/** 已创建订单响应；保持现有 JSON 字段及序列化契约。 */
public record CreatedOrderVO(OrderVO order, OriginalPaymentVO originalPayment) {}
