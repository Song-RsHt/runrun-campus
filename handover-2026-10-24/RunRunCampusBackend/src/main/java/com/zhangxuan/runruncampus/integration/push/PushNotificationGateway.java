package com.zhangxuan.runruncampus.integration.push;

import com.zhangxuan.runruncampus.model.OrderPushRequested;

/** 推送通知网关组件；保持当前应用的配置与协作边界。 */
public interface PushNotificationGateway {

    void send(OrderPushRequested notification) throws Exception;
}
