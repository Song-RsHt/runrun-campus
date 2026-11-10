package com.zhangxuan.runruncampus.task.credit;

import com.zhangxuan.runruncampus.integration.maintenance.RiskSignalMaintenance;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 通过 Spring 代理执行风险信号维护；事务和重试留在维护组件，任务仅负责定时触发。 */
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
