package com.zhangxuan.runruncampus.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;





@Configuration
@EnableConfigurationProperties(ManualQrPaymentProperties.class)
public class ManualQrPaymentConfiguration {}
