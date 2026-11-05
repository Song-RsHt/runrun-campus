package com.zhangxuan.runrunv2.integration.wechat;
import com.zhangxuan.runrunv2.service.notification.NotificationService;

import java.util.Map;

/** Adapter boundary for WeChat subscriptions. It is deliberately best effort. */
public interface WechatSubscriptionGateway {
    NotificationService.DeliveryResult send(long recipientId, Map<String,Object> payload);
}
