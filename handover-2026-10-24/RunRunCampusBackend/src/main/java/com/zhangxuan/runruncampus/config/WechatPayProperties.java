package com.zhangxuan.runruncampus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 微信支付属性组件；保持当前应用的配置与协作边界。 */
@ConfigurationProperties(prefix = "wechat.pay")
public record WechatPayProperties(
        boolean enabled,
        String appId,
        String merchantId,
        String merchantSerialNumber,
        String privateKeyPath,
        String apiV3Key,
        String apiV3KeyPath,
        String notifyUrl,
        String refundNotifyUrl) {}
