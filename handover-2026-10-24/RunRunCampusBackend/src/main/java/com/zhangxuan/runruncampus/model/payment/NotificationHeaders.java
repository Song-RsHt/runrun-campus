package com.zhangxuan.runruncampus.model.payment;

public record NotificationHeaders(
    String serial, String timestamp, String nonce, String signature, String signType) {}
