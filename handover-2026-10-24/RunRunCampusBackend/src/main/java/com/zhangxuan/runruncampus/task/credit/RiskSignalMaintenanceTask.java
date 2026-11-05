package com.zhangxuan.runruncampus.task.credit;

import com.zhangxuan.runruncampus.service.credit.RiskSignalMaintenance;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules RiskSignalMaintenance through its Spring service proxy; transactions and retry policy
 * remain in that service.
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
