package com.zhangxuan.runruncampus.config;

import com.zhangxuan.runruncampus.integration.payment.DisabledWechatPayGateway;
import com.zhangxuan.runruncampus.integration.payment.WechatPayApiV3Gateway;
import com.zhangxuan.runruncampus.integration.payment.WechatPayGateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 微信支付配置组件；保持当前应用的配置与协作边界。 */
@Configuration
@EnableConfigurationProperties(WechatPayProperties.class)
public class WechatPayConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "wechat.pay", name = "enabled", havingValue = "true")
    public WechatPayGateway configuredWechatPayGateway(WechatPayProperties properties) {
        return new WechatPayApiV3Gateway(properties);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "wechat.pay",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    public WechatPayGateway disabledWechatPayGateway() {
        return new DisabledWechatPayGateway();
    }
}
