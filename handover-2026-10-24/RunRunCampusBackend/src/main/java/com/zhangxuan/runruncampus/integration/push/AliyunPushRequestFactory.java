package com.zhangxuan.runruncampus.integration.push;

import com.aliyun.push20160801.models.PushTask;
import com.aliyun.push20160801.models.PushTask.PushTaskNotification;
import com.aliyun.push20160801.models.PushTask.PushTaskNotificationAndroid;
import com.aliyun.push20160801.models.PushTask.PushTaskNotificationAndroidOptions;
import com.aliyun.push20160801.models.PushTask.PushTaskNotificationAndroidOptionsAccs;
import com.aliyun.push20160801.models.PushTask.PushTaskOptions;
import com.aliyun.push20160801.models.PushTask.PushTaskTarget;
import com.aliyun.push20160801.models.PushV2Request;
import com.zhangxuan.runruncampus.model.OrderPushRequested;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 阿里云推送请求工厂组件；保持当前应用的配置与协作边界。 */
public class AliyunPushRequestFactory {

    private final long appKey;
    private final String channelId;
    private final String activity;

    public AliyunPushRequestFactory(long appKey, String channelId, String activity) {
        if (appKey <= 0) {
            throw new IllegalArgumentException("PUSH_APP_KEY 必须是正整数");
        }
        this.appKey = appKey;
        this.channelId = channelId;
        this.activity = activity;
    }

    public PushV2Request create(OrderPushRequested event, Instant now) {
        String route =
                event.orderId() == null
                        ? "{\"destination\":\"" + event.destination().name() + "\"}"
                        : "{\"orderId\":" + event.orderId() + "}";
        String jobKey =
                event.orderId() == null
                        ? "notification-"
                                + Math.floorMod(
                                        event.idempotentToken().hashCode(), Integer.MAX_VALUE)
                        : "order-" + event.orderId();
        PushTaskTarget target =
                new PushTaskTarget()
                        .setType("ACCOUNT")
                        .setValue(event.recipientId().toString())
                        .setPlatform("ANDROID");
        PushTaskNotificationAndroidOptionsAccs accs =
                new PushTaskNotificationAndroidOptionsAccs()
                        .setOpenType("NONE")
                        .setNotifyType("BOTH")
                        .setPriority(2);
        PushTaskNotificationAndroid android =
                new PushTaskNotificationAndroid()
                        .setChannelId(channelId)
                        .setVendorChannelActivity(activity)
                        .setExtParameters(route)
                        .setNotifyId(
                                Math.floorMod(
                                        event.idempotentToken().hashCode(), Integer.MAX_VALUE))
                        .setOptions(new PushTaskNotificationAndroidOptions().setAccs(accs));
        PushTaskNotification notification =
                new PushTaskNotification()
                        .setTitle(event.title())
                        .setBody(event.content())
                        .setAndroid(android);
        PushTaskOptions options =
                new PushTaskOptions()
                        .setExpireTime(now.plus(24, ChronoUnit.HOURS).toString())
                        .setJobKey(jobKey)
                        .setTrim(true);
        return new PushV2Request()
                .setAppKey(appKey)
                .setIdempotentToken(event.idempotentToken())
                .setPushTask(
                        new PushTask()
                                .setTarget(target)
                                .setNotification(notification)
                                .setOptions(options));
    }
}
