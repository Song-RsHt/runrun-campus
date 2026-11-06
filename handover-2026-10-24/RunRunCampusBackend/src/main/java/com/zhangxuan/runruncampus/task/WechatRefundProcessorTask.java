package com.zhangxuan.runruncampus.task;

import com.zhangxuan.runruncampus.integration.payment.WechatRefundProcessor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 通过 Spring 代理执行退款处理；任务只注册调度，事务与重试留在处理器。 */
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
