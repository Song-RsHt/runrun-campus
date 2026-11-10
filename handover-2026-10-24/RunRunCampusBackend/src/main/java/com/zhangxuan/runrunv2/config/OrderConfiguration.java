package com.zhangxuan.runrunv2.config;

import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.service.payment.OriginalPaymentWriter;
import com.zhangxuan.runrunv2.vo.order.OriginalPaymentVO;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.OffsetDateTime;

/** 订单创建与状态管理配置组件；保持当前应用的配置与协作边界。 */
@Configuration
public class OrderConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock orderClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnProperty(
            name = "rrc.payment.mock-enabled",
            havingValue = "false",
            matchIfMissing = true)
    public OriginalPaymentWriter unavailablePaymentWriter() {
        // 支付表归 M03 管理；原子支付写入器不可用时拒绝创建，避免产生没有支付义务的孤立订单。
        return new OriginalPaymentWriter() {

            public boolean available() {
                return false;
            }

            public OriginalPaymentVO create(String id, long cents, OffsetDateTime deadline) {
                throw new IdentityFailure(503, "NEW_ORDER_DISABLED");
            }
        };
    }
}
