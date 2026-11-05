package com.zhangxuan.runrunv2.integration.wechat;
import com.zhangxuan.runrunv2.service.notification.NotificationService;

import java.util.Map;
import org.springframework.stereotype.Component;

/** No network call is made until a configured WeChat subscription adapter is supplied. */
@Component
public class DisabledWechatSubscriptionGateway implements WechatSubscriptionGateway {
    @Override public NotificationService.DeliveryResult send(long recipientId, Map<String,Object> payload) {
        return new NotificationService.DeliveryResult(false, "WECHAT_NOT_CONFIGURED");
    }
}
