package com.zhangxuan.runrunv2.vo.order;

import com.zhangxuan.runrunv2.model.order.OrderPricing;

/** 订单报价响应；保持现有 JSON 字段及序列化契约。 */
public record OrderQuoteVO(OrderPricing pricingSnapshot, long configVersion) {}
