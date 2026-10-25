package com.zhangxuan.runruncampus.message;

import com.aliyun.credentials.models.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "app.push.enabled", havingValue = "true")
public class AliyunPushNotificationGateway implements PushNotificationGateway {
    private final com.aliyun.push20160801.Client client;
    private final AliyunPushRequestFactory requestFactory;

    public AliyunPushNotificationGateway(
            @Value("${app.push.app-key}") long appKey,
            @Value("${app.push.ecs-role-name}") String ecsRoleName,
            @Value("${app.push.region:cn-hangzhou}") String region,
            @Value("${app.push.endpoint:cloudpush.aliyuncs.com}") String endpoint,
            @Value("${app.push.channel-id:order_updates}") String channelId,
            @Value("${app.push.activity:com.zhangxuan.runruncampus.MainActivity}") String activity
    ) throws Exception {
        if (ecsRoleName.isBlank()) throw new IllegalArgumentException("PUSH_ECS_ROLE_NAME 不能为空");
        com.aliyun.credentials.Client credential = new com.aliyun.credentials.Client(
                new Config().setType("ecs_ram_role").setRoleName(ecsRoleName));
        this.client = new com.aliyun.push20160801.Client(new com.aliyun.teaopenapi.models.Config()
                .setCredential(credential)
                .setEndpoint(endpoint)
                .setRegionId(region));
        this.requestFactory = new AliyunPushRequestFactory(appKey, channelId, activity);
    }

    @Override
    public void send(OrderPushRequested notification) throws Exception {
        client.pushV2(requestFactory.create(notification, Instant.now()));
    }
}
