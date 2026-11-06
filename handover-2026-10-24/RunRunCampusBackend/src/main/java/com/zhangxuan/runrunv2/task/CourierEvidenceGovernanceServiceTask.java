package com.zhangxuan.runrunv2.task;

import com.zhangxuan.runrunv2.service.CourierEvidenceGovernanceService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 经 Spring 服务代理触发证据保全与删除维护；事务和重试由业务实现负责，任务仅注册调度。 */
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
