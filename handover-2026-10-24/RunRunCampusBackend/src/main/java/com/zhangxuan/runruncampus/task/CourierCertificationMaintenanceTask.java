package com.zhangxuan.runruncampus.task;

import com.zhangxuan.runruncampus.integration.maintenance.CourierCertificationMaintenance;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;






/**
 * Schedules CourierCertificationMaintenance through its Spring service proxy; transactions and
 * retry policy remain in that service.
 */
@Component()
public class CourierCertificationMaintenanceTask {

    private final CourierCertificationMaintenance service;

    public CourierCertificationMaintenanceTask(CourierCertificationMaintenance service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.courier-certification.maintenance-cron:0 0 9 * * *}", zone = "Asia/Shanghai")
    public void run() {
        service.run();
    }
}
