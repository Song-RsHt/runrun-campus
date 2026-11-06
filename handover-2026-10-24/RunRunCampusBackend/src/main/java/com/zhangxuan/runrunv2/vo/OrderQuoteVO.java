package com.zhangxuan.runrunv2.vo;

import com.zhangxuan.runrunv2.model.Pricing;











public record OrderQuoteVO(Pricing pricingSnapshot, long configVersion) {
}
