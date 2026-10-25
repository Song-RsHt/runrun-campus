package com.zhangxuan.runruncampus.message;

import com.aliyun.push20160801.models.PushV2Request;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliyunPushRequestFactoryTest {
    @Test
    void buildsAccountTargetedOrderNotification() {
        AliyunPushRequestFactory factory = new AliyunPushRequestFactory(
                123456L, "order_updates", "com.zhangxuan.runruncampus.MainActivity");
        OrderPushRequested event = new OrderPushRequested(
                8L, 42L, "骑手已接单", "骑手正在前往取餐点", "12345678-1234-1234-1234-123456789012");

        PushV2Request request = factory.create(event, Instant.parse("2026-08-31T00:00:00Z"));

        assertEquals(123456L, request.getAppKey());
        assertEquals("ACCOUNT", request.getPushTask().getTarget().getType());
        assertEquals("8", request.getPushTask().getTarget().getValue());
        assertEquals("ANDROID", request.getPushTask().getTarget().getPlatform());
        assertEquals("order_updates", request.getPushTask().getNotification().getAndroid().getChannelId());
        assertEquals("NONE", request.getPushTask().getNotification().getAndroid()
                .getOptions().getAccs().getOpenType());
        assertEquals("2026-09-01T00:00:00Z", request.getPushTask().getOptions().getExpireTime());
        assertTrue(request.getPushTask().getNotification().getAndroid().getExtParameters().contains("\"orderId\":42"));
    }

    @Test
    void buildsCourierCertificationNotificationRoute() {
        AliyunPushRequestFactory factory = new AliyunPushRequestFactory(
                123456L, "order_updates", "com.zhangxuan.runruncampus.MainActivity");
        OrderPushRequested event = new OrderPushRequested(
                8L, null, "骑手认证即将到期", "请及时更新材料", "certification-reminder-token",
                PushDestination.COURIER_CERTIFICATION);

        PushV2Request request = factory.create(event, Instant.parse("2026-08-31T00:00:00Z"));

        assertTrue(request.getPushTask().getNotification().getAndroid().getExtParameters()
                .contains("\"destination\":\"COURIER_CERTIFICATION\""));
        assertTrue(request.getPushTask().getOptions().getJobKey().startsWith("notification-"));
    }
}
