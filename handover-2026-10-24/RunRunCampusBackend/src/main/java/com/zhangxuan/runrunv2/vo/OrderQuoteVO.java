package com.zhangxuan.runrunv2.vo;

import com.zhangxuan.runrunv2.model.OrderPricing;














public record OrderQuoteVO(OrderPricing pricingSnapshot, long configVersion) {
}
