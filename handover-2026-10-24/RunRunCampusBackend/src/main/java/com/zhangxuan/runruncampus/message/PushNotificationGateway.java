package com.zhangxuan.runruncampus.message;

public interface PushNotificationGateway {
    void send(OrderPushRequested notification) throws Exception;
}
