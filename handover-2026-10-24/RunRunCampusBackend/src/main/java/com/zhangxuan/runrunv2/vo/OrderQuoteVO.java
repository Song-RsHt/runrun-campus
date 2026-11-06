package com.zhangxuan.runrunv2.vo;

import com.zhangxuan.runrunv2.model.OrderPricing;

/** 订单创建与状态管理报价响应；保持现有 JSON 字段及序列化契约。 */
public record OrderQuoteVO(OrderPricing pricingSnapshot, long configVersion) {}
