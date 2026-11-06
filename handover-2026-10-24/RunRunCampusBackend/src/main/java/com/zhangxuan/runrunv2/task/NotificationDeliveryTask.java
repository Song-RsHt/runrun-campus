package com.zhangxuan.runrunv2.task;

import com.zhangxuan.runrunv2.integration.notification.NotificationOutboxWorker;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 触发现有通知工作器；领取、租约和完成回写事务仍由工作器管理。 */
@Component
public class NotificationDeliveryTask {

    private final NotificationOutboxWorker worker;

    public NotificationDeliveryTask(NotificationOutboxWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${rrc.notifications.worker-delay-ms:15000}")
    public void deliver() {
        // 工作器领取前检查 M08 开关，不能在工作器内重复注册调度。
        worker.runOnce();
    }
}
