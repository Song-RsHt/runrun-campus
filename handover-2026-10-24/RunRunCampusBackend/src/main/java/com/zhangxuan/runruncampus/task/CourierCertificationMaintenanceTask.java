package com.zhangxuan.runruncampus.task;

import com.zhangxuan.runruncampus.integration.maintenance.CourierCertificationMaintenance;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 通过 Spring 代理执行资质维护；事务和重试由维护组件负责，调度条件在任务注解中声明。 */
@Component()
public class CourierCertificationMaintenanceTask {

    private final CourierCertificationMaintenance service;

    public CourierCertificationMaintenanceTask(CourierCertificationMaintenance service) {
        this.service = service;
    }

    @Scheduled(
            cron = "${app.courier-certification.maintenance-cron:0 0 9 * * *}",
            zone = "Asia/Shanghai")
    public void run() {
        service.run();
    }
}
