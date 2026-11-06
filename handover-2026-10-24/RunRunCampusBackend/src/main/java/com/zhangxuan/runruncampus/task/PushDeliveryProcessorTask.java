package com.zhangxuan.runruncampus.task;

import com.zhangxuan.runruncampus.integration.push.PushDeliveryProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;







/**
 * Schedules PushDeliveryProcessor through its Spring service proxy; transactions and retry policy
 * remain in that service.
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
