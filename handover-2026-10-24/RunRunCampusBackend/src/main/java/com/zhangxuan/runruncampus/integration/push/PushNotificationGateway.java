package com.zhangxuan.runruncampus.integration.push;

import com.zhangxuan.runruncampus.model.message.OrderPushRequested;

public interface PushNotificationGateway {

  void send(OrderPushRequested notification) throws Exception;
}
