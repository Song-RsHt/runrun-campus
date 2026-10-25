package com.zhangxuan.runruncampus.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.manual-qr-payment")
public record ManualQrPaymentProperties(boolean enabled) { }
