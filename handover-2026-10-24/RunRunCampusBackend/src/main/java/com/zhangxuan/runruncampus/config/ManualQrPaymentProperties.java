package com.zhangxuan.runruncampus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 人工扫码付款与骑手结算属性组件；保持当前应用的配置与协作边界。 */
@ConfigurationProperties(prefix = "app.manual-qr-payment")
public record ManualQrPaymentProperties(boolean enabled) {}
