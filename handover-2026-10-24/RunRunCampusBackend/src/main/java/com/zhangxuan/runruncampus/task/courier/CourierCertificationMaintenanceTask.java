package com.zhangxuan.runruncampus.task.courier;

import com.zhangxuan.runruncampus.model.courier.CourierCertification;
import com.zhangxuan.runruncampus.repository.courier.CourierCertificationRepository;
import com.zhangxuan.runruncampus.integration.storage.OrderProofStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import com.zhangxuan.runruncampus.service.courier.CourierCertificationMaintenance;

/**
 * Schedules CourierCertificationMaintenance through its Spring service proxy; transactions and retry policy remain in that service.
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
