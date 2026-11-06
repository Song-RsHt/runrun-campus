package com.zhangxuan.runrunv2.task;

import com.zhangxuan.runrunv2.service.PaymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;








@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "rrc.payment.mock-enabled", havingValue = "true")
public class PaymentExpiryJob {

    private final PaymentService service;

    public PaymentExpiryJob(PaymentService service) {
        this.service = service;
    }

    // Same transaction/lock path as callbacks; each bounded batch can be retried by any instance.
    @Scheduled(fixedDelayString = "${rrc.payment.expiry-delay-ms:15000}", initialDelayString = "${rrc.payment.expiry-delay-ms:15000}")
    public void expire() {
        service.closeExpired();
    }
}
