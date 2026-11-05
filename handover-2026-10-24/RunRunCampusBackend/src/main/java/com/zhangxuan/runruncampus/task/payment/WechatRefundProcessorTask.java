package com.zhangxuan.runruncampus.task.payment;

import com.zhangxuan.runruncampus.service.payment.WechatRefundProcessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Schedules WechatRefundProcessor through its Spring service proxy; transactions and retry policy
 * remain in that service.
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
