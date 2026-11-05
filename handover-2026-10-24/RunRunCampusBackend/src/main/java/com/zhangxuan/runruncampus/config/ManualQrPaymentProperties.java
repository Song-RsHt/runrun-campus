package com.zhangxuan.runruncampus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.manual-qr-payment")
public record ManualQrPaymentProperties(boolean enabled) { }
