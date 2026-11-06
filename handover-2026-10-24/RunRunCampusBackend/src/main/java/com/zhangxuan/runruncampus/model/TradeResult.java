package com.zhangxuan.runruncampus.model;



public record TradeResult(String appId, String merchantId, String outTradeNo, String transactionId, int amountCents, boolean paid) {
}
