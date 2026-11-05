package com.zhangxuan.runrunv2.task.notification;

import com.zhangxuan.runrunv2.service.notification.NotificationOutboxWorker;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Schedules the existing worker; claim, lease and completion transactions stay in the service. */
@Component
public class NotificationDeliveryTask {
  private final NotificationOutboxWorker worker;

  public NotificationDeliveryTask(NotificationOutboxWorker worker) {
    this.worker = worker;
  }

  @Scheduled(fixedDelayString = "${rrc.notifications.worker-delay-ms:15000}")
  public void deliver() {
    // The service checks M08 functions before claiming; no second scheduler is registered there.
    worker.runOnce();
  }
}
