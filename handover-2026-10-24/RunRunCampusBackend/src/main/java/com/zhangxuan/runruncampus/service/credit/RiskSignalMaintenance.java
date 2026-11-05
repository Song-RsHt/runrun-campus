package com.zhangxuan.runruncampus.service.credit;

import com.zhangxuan.runruncampus.mapper.credit.RiskSignalMaintenanceMapper;
import com.zhangxuan.runruncampus.service.api.PlatformSettingsService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RiskSignalMaintenance {

  private final RiskSignalMaintenanceMapper mapper;

  private final PlatformSettingsService settings;

  public RiskSignalMaintenance(
      RiskSignalMaintenanceMapper mapper, PlatformSettingsService settings) {
    this.mapper = mapper;
    this.settings = settings;
  }

  @Transactional
  public void scan() {
    frequentCancellations();
    repeatedIssues();
    overdueCourierOrders(settings.get().deliveryMinutes());
  }

  private void frequentCancellations() {
    mapper.frequentCancellationsWriteStatement();
  }

  private void repeatedIssues() {
    mapper.repeatedIssuesWriteStatement();
  }

  private void overdueCourierOrders(int deliveryMinutes) {
    mapper.overdueCourierOrdersWriteStatement(deliveryMinutes);
  }
}
