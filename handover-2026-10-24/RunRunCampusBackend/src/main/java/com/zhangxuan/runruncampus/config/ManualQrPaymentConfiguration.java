package com.zhangxuan.runruncampus.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 人工扫码付款与骑手结算配置组件；保持当前应用的配置与协作边界。 */
@Configuration
@EnableConfigurationProperties(ManualQrPaymentProperties.class)
public class ManualQrPaymentConfiguration {}
