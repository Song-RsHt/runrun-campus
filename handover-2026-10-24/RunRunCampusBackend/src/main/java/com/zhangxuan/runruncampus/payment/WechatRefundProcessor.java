package com.zhangxuan.runruncampus.payment;

import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.order.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
public class WechatRefundProcessor {
    private static final Logger log = LoggerFactory.getLogger(WechatRefundProcessor.class);
    private final WechatRefundRepository refundRepository;
    private final WechatPaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final WechatPayGateway gateway;

    public WechatRefundProcessor(WechatRefundRepository refundRepository, WechatPaymentRepository paymentRepository,
                                 OrderRepository orderRepository, WechatPayGateway gateway) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.gateway = gateway;
    }

    @Scheduled(fixedDelayString = "${wechat.pay.refund-poll-millis:60000}")
    @Transactional
    public void process() {
        List<WechatRefund> work = new ArrayList<>(refundRepository.findTop20ByStatusOrderByCreatedAtAsc(RefundStatus.PENDING));
        work.addAll(refundRepository.findTop20ByStatusOrderByCreatedAtAsc(RefundStatus.PROCESSING));
        for (WechatRefund refund : work) {
            try {
                WechatPayGateway.RefundResult result = refund.getStatus() == RefundStatus.PENDING
                        ? gateway.refund(refund.getPayment().getOutTradeNo(), refund.getOutRefundNo(),
                        refund.getPayment().getAmountCents(), refund.getAmountCents(), refund.getReason())
                        : gateway.queryRefund(refund.getOutRefundNo());
                refund.record(result);
                if (result.status() == RefundStatus.SUCCESS) finishOrderRefund(refund.getPayment().getOrder());
            } catch (RuntimeException exception) {
                log.warn("微信退款处理失败，退款单 {} 将重试", refund.getOutRefundNo(), exception);
            }
        }
    }

    private void finishOrderRefund(CampusOrder order) {
        if (order == null || order.getStatus() != OrderStatus.CANCELLED) return;
        long paid = paymentRepository.sumPaidForOrder(order);
        long refunded = refundRepository.sumSucceededForOrder(order);
        if (paid > 0 && refunded >= paid) {
            CampusOrder locked = orderRepository.findLockedById(order.getId()).orElseThrow();
            locked.markRefunded();
        }
    }
}
