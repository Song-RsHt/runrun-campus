package com.zhangxuan.runruncampus.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WechatPayProperties.class)
public class WechatPayConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "wechat.pay", name = "enabled", havingValue = "true")
    WechatPayGateway configuredWechatPayGateway(WechatPayProperties properties) {
        return new WechatPayApiV3Gateway(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "wechat.pay", name = "enabled", havingValue = "false", matchIfMissing = true)
    WechatPayGateway disabledWechatPayGateway() {
        return new DisabledWechatPayGateway();
    }
}
