package com.zhangxuan.runrunv2.service.payment;

import com.zhangxuan.runrunv2.vo.order.OriginalPaymentVO;

import java.time.OffsetDateTime;

/** 原始支付原子写入业务契约；实现类负责原有事务、锁与持久化协作。 */
public interface OriginalPaymentWriter {

    /**
     * 报告原始支付写入能力是否已装配可用。
     *
     * @return 原始支付原子写入器是否可用
     */
    boolean available();

    /**
     * 在调用方订单事务内写入原始支付义务，不单独提交事务。
     *
     * <p>调用方必须持有订单创建事务；支付写入不可用时拒绝提交孤立订单。
     *
     * @param orderId 订单标识
     * @param amountCents 原始支付金额，单位分
     * @param deadline 原订单支付截止时刻
     * @return 与订单原子写入的原始支付义务，包含金额、截止时刻及版本
     */
    OriginalPaymentVO create(String orderId, long amountCents, OffsetDateTime deadline);
}
