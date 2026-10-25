package com.zhangxuan.runruncampus.payment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ManualQrPaymentProperties.class)
public class ManualQrPaymentConfiguration {
}
