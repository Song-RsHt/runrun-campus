package com.zhangxuan.runruncampus.task.payment;

import com.zhangxuan.runruncampus.enums.payment.RefundStatus;
import com.zhangxuan.runruncampus.integration.payment.WechatPayGateway;
import com.zhangxuan.runruncampus.repository.payment.WechatPaymentRepository;
import com.zhangxuan.runruncampus.model.payment.WechatRefund;
import com.zhangxuan.runruncampus.repository.payment.WechatRefundRepository;
import com.zhangxuan.runruncampus.model.order.CampusOrder;
import com.zhangxuan.runruncampus.repository.order.OrderRepository;
import com.zhangxuan.runruncampus.enums.order.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import com.zhangxuan.runruncampus.service.payment.WechatRefundProcessor;

/**
 * Schedules WechatRefundProcessor through its Spring service proxy; transactions and retry policy remain in that service.
 */
@Component()
public class WechatRefundProcessorTask {

    private final WechatRefundProcessor service;

    public WechatRefundProcessorTask(WechatRefundProcessor service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${wechat.pay.refund-poll-millis:60000}")
    public void process() {
        service.process();
    }
}
