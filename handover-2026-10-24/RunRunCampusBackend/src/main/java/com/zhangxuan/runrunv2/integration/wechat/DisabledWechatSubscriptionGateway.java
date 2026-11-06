package com.zhangxuan.runrunv2.integration.wechat;

import com.zhangxuan.runrunv2.model.DeliveryResult;
import java.util.Map;
import org.springframework.stereotype.Component;






/** No network call is made until a configured WeChat subscription adapter is supplied. */
@Component
public class DisabledWechatSubscriptionGateway implements WechatSubscriptionGateway {

  @Override
  public DeliveryResult send(long recipientId, Map<String, Object> payload) {
    return new DeliveryResult(false, "WECHAT_NOT_CONFIGURED");
  }
}
