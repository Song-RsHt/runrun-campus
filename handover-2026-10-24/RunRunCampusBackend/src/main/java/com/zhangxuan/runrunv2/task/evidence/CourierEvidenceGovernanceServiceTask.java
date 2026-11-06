package com.zhangxuan.runrunv2.task.evidence;

import com.zhangxuan.runrunv2.service.evidence.CourierEvidenceGovernanceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;






/**
 * Schedules CourierEvidenceGovernanceService through its Spring service proxy; transactions and
 * retry policy remain in that service.
 */
@Component()
public class CourierEvidenceGovernanceServiceTask {

  private final CourierEvidenceGovernanceService service;

  public CourierEvidenceGovernanceServiceTask(CourierEvidenceGovernanceService service) {
    this.service = service;
  }

  @Scheduled(
      fixedDelayString = "${rrc.evidence.cleanup-delay-ms:60000}",
      initialDelayString = "${rrc.evidence.cleanup-delay-ms:60000}")
  public void cleanupDue() {
    service.cleanupDue();
  }
}
