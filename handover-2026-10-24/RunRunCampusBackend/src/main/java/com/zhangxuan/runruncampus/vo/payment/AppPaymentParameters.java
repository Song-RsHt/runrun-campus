package com.zhangxuan.runruncampus.vo.payment;

/** 应用支付参数响应；保持现有 JSON 字段及序列化契约。 */
public record AppPaymentParameters(
        String appId,
        String partnerId,
        String prepayId,
        String packageValue,
        String nonceStr,
        String timestamp,
        String sign) {}
