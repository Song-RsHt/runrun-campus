package com.zhangxuan.runruncampus.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wechat.pay")
public record WechatPayProperties(boolean enabled, String appId, String merchantId, String merchantSerialNumber,
                                  String privateKeyPath, String apiV3Key, String apiV3KeyPath,
                                  String notifyUrl, String refundNotifyUrl) {
}
