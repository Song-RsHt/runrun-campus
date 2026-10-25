package com.zhangxuan.runruncampus.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@ConditionalOnProperty(name = "app.push.enabled", havingValue = "true")
public class PushDeliveryProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(PushDeliveryProcessor.class);
    private static final long[] RETRY_MINUTES = {1, 5, 30, 120};
    private static final int MAX_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 20;
    private final PushDeliveryRepository deliveries;
    private final PushNotificationGateway gateway;

    public PushDeliveryProcessor(PushDeliveryRepository deliveries, PushNotificationGateway gateway) {
        this.deliveries = deliveries;
        this.gateway = gateway;
    }

    @Scheduled(fixedDelayString = "${app.push.retry-poll-millis:30000}",
            initialDelayString = "${app.push.retry-initial-delay-millis:30000}")
    public void process() {
        for (int index = 0; index < BATCH_SIZE; index++) {
            var claimed = deliveries.claimNext();
            if (claimed.isEmpty()) return;
            deliver(claimed.orElseThrow());
        }
    }

    private void deliver(PushDeliveryTask task) {
        try {
            gateway.send(task.notification());
            deliveries.markSent(task.id());
        } catch (Exception exception) {
            int attempts = task.attempts() + 1;
            boolean dead = attempts >= MAX_ATTEMPTS;
            long delay = RETRY_MINUTES[Math.min(attempts - 1, RETRY_MINUTES.length - 1)];
            String message = errorMessage(exception);
            deliveries.markFailure(task.id(), attempts, message,
                    Instant.now().plus(delay, ChronoUnit.MINUTES), dead);
            if (dead) {
                LOG.error("订单推送重试耗尽，taskId={}, orderId={}, recipientId={}", task.id(),
                        task.notification().orderId(), task.notification().recipientId(), exception);
            } else {
                LOG.warn("订单推送失败，将进行第 {} 次重试，taskId={}", attempts + 1, task.id());
            }
        }
    }

    private String errorMessage(Exception exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) value = exception.getClass().getSimpleName();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
