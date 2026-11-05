package com.zhangxuan.runruncampus.task.message;

import com.zhangxuan.runruncampus.repository.message.PushDeliveryRepository;
import com.zhangxuan.runruncampus.model.message.PushDeliveryTask;
import com.zhangxuan.runruncampus.integration.push.PushNotificationGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import com.zhangxuan.runruncampus.service.message.PushDeliveryProcessor;

/**
 * Schedules PushDeliveryProcessor through its Spring service proxy; transactions and retry policy remain in that service.
 */
@Component()
@ConditionalOnProperty(name = "app.push.enabled", havingValue = "true")
public class PushDeliveryProcessorTask {

    private final PushDeliveryProcessor service;

    public PushDeliveryProcessorTask(PushDeliveryProcessor service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.push.retry-poll-millis:30000}", initialDelayString = "${app.push.retry-initial-delay-millis:30000}")
    public void process() {
        service.process();
    }
}
