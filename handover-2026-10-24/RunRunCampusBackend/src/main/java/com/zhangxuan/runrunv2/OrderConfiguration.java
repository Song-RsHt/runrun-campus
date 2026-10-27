package com.zhangxuan.runrunv2;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderConfiguration {
    @Bean @ConditionalOnMissingBean(Clock.class)
    Clock orderClock() { return Clock.systemUTC(); }
    @Bean @ConditionalOnMissingBean(OriginalPaymentWriter.class)
    OriginalPaymentWriter unavailablePaymentWriter() {
        // M00 assigns payment tables to M03. Until its atomic writer is integrated,
        // A08 must fail closed instead of producing an unpaid orphan order.
        return new OriginalPaymentWriter() {
            public boolean available() { return false; }
            public OrderModels.OriginalPayment create(String id, long cents, OffsetDateTime deadline) {
                throw new IdentityFailure(503, "NEW_ORDER_DISABLED");
            }
        };
    }
}
