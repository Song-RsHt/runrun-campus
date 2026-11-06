package com.zhangxuan.runruncampus.integration.maintenance;

import com.zhangxuan.runruncampus.mapper.RiskSignalMaintenanceMapper;
import com.zhangxuan.runruncampus.service.PlatformSettingsService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RiskSignalMaintenance {

    private final RiskSignalMaintenanceMapper mapper;

    private final PlatformSettingsService settings;

    public RiskSignalMaintenance(RiskSignalMaintenanceMapper mapper, PlatformSettingsService settings) {
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
        mapper.recordFrequentCancellations();
    }

    private void repeatedIssues() {
        mapper.recordRepeatedIssues();
    }

    private void overdueCourierOrders(int deliveryMinutes) {
        mapper.recordOverdueCourierOrders(deliveryMinutes);
    }
}
