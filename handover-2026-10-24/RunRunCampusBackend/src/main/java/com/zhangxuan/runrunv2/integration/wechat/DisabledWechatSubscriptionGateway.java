package com.zhangxuan.runrunv2.integration.wechat;

import com.zhangxuan.runrunv2.model.notification.DeliveryResult;

import org.springframework.stereotype.Component;

import java.util.Map;

/** 未配置微信订阅适配器前不发起网络请求。 */
@Component
public class DisabledWechatSubscriptionGateway implements WechatSubscriptionGateway {

    @Override
    public DeliveryResult send(long recipientId, Map<String, Object> payload) {
        return new DeliveryResult(false, "WECHAT_NOT_CONFIGURED");
    }
}
