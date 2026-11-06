package com.zhangxuan.runrunv2.integration.wechat;

import com.zhangxuan.runrunv2.model.DeliveryResult;

import java.util.Map;

/** 微信订阅适配边界；采用尽力投递，失败由现有通知重试流程处理。 */
public interface WechatSubscriptionGateway {

    DeliveryResult send(long recipientId, Map<String, Object> payload);
}
