package com.zhangxuan.runruncampus.task.credit;

import com.zhangxuan.runruncampus.api.PlatformSettingsService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.zhangxuan.runruncampus.service.credit.RiskSignalMaintenance;

/**
 * Schedules RiskSignalMaintenance through its Spring service proxy; transactions and retry policy remain in that service.
 */
@Component()
public class RiskSignalMaintenanceTask {

    private final RiskSignalMaintenance service;

    public RiskSignalMaintenanceTask(RiskSignalMaintenance service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.risk.scan-cron:0 */10 * * * *}")
    public void scan() {
        service.scan();
    }
}
