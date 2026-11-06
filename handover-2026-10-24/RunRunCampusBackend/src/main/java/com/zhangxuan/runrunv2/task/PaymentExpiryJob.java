package com.zhangxuan.runrunv2.task;

import com.zhangxuan.runrunv2.service.PaymentService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 支付回执及退款到期调度；仅注册定时触发，业务事务和重试由注入的协作者执行。 */
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
