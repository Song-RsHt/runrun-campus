package com.zhangxuan.runrunv2.task.fulfillment;

import com.zhangxuan.runrunv2.service.fulfillment.FulfillmentService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "rrc.fulfillment.enabled",
    havingValue = "true")
@Component
@EnableScheduling
public class FulfillmentCompletionJob {
  private final FulfillmentService service;

  public FulfillmentCompletionJob(FulfillmentService service) {
    this.service = service;
  }

  @Scheduled(
      fixedDelayString = "${rrc.fulfillment.completion-delay-ms:15000}",
      initialDelayString = "${rrc.fulfillment.completion-delay-ms:15000}")
  public void complete() {
    service.autoComplete();
  }
}
