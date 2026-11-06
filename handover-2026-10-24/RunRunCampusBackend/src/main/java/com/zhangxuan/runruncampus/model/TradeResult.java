package com.zhangxuan.runruncampus.model;

/** 渠道交易结果；仅承载内部协作所需的数据或校验逻辑。 */
public record TradeResult(
        String appId,
        String merchantId,
        String outTradeNo,
        String transactionId,
        int amountCents,
        boolean paid) {}
