package com.zhangxuan.runrunv2.task;

import com.zhangxuan.runrunv2.service.PaymentService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 模拟支付启用时检查到期支付；由支付业务服务在原事务和锁路径内关闭，不处理网络回执。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "rrc.payment.mock-enabled", havingValue = "true")
public class PaymentExpiryJob {

    private final PaymentService service;

    public PaymentExpiryJob(PaymentService service) {
        this.service = service;
    }

    // 超时批次与回调共用事务及锁路径；有界批次允许任一实例重试。
    @Scheduled(
            fixedDelayString = "${rrc.payment.expiry-delay-ms:15000}",
            initialDelayString = "${rrc.payment.expiry-delay-ms:15000}")
    public void expire() {
        service.closeExpired();
    }
}
