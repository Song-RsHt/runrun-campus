package com.zhangxuan.runrunv2.integration.wechat;

import com.zhangxuan.runrunv2.model.notification.DeliveryResult;
import java.util.Map;

/** Adapter boundary for WeChat subscriptions. It is deliberately best effort. */
public interface WechatSubscriptionGateway {

  DeliveryResult send(long recipientId, Map<String, Object> payload);
}
