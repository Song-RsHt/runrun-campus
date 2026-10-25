package com.zhangxuan.runruncampus.message;

import com.aliyun.push20160801.models.PushTask;
import com.aliyun.push20160801.models.PushV2Request;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

class AliyunPushRequestFactory {
    private final long appKey;
    private final String channelId;
    private final String activity;

    AliyunPushRequestFactory(long appKey, String channelId, String activity) {
        if (appKey <= 0) throw new IllegalArgumentException("PUSH_APP_KEY 必须是正整数");
        this.appKey = appKey;
        this.channelId = channelId;
        this.activity = activity;
    }

    PushV2Request create(OrderPushRequested event, Instant now) {
        String route = event.orderId() == null
                ? "{\"destination\":\"" + event.destination().name() + "\"}"
                : "{\"orderId\":" + event.orderId() + "}";
        String jobKey = event.orderId() == null
                ? "notification-" + Math.floorMod(event.idempotentToken().hashCode(), Integer.MAX_VALUE)
                : "order-" + event.orderId();
        PushTask.PushTaskTarget target = new PushTask.PushTaskTarget()
                .setType("ACCOUNT")
                .setValue(event.recipientId().toString())
                .setPlatform("ANDROID");
        PushTask.PushTaskNotificationAndroidOptionsAccs accs =
                new PushTask.PushTaskNotificationAndroidOptionsAccs()
                        .setOpenType("NONE")
                        .setNotifyType("BOTH")
                        .setPriority(2);
        PushTask.PushTaskNotificationAndroid android = new PushTask.PushTaskNotificationAndroid()
                .setChannelId(channelId)
                .setVendorChannelActivity(activity)
                .setExtParameters(route)
                .setNotifyId(Math.floorMod(event.idempotentToken().hashCode(), Integer.MAX_VALUE))
                .setOptions(new PushTask.PushTaskNotificationAndroidOptions().setAccs(accs));
        PushTask.PushTaskNotification notification = new PushTask.PushTaskNotification()
                .setTitle(event.title())
                .setBody(event.content())
                .setAndroid(android);
        PushTask.PushTaskOptions options = new PushTask.PushTaskOptions()
                .setExpireTime(now.plus(24, ChronoUnit.HOURS).toString())
                .setJobKey(jobKey)
                .setTrim(true);

        return new PushV2Request()
                .setAppKey(appKey)
                .setIdempotentToken(event.idempotentToken())
                .setPushTask(new PushTask()
                        .setTarget(target)
                        .setNotification(notification)
                        .setOptions(options));
    }
}
