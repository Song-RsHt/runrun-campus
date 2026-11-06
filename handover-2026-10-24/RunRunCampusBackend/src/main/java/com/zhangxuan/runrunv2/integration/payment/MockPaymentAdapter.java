package com.zhangxuan.runrunv2.integration.payment;

import static com.zhangxuan.runrunv2.dto.response.payment.PaymentModels.*;

import com.zhangxuan.runrunv2.enums.payment.Result;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.mapper.payment.MockPaymentMapper;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Database-backed isolated channel. No merchant SDK, network call or client amount. */
@Component
@ConditionalOnProperty(name = "rrc.payment.mock-enabled", havingValue = "true")
public class MockPaymentAdapter {

  private final MockPaymentMapper mapper;

  public MockPaymentAdapter(
      MockPaymentMapper mapper,
      @Value("${rrc.identity.environment}") String environment,
      org.springframework.core.env.Environment spring) {
    if (!Set.of("local", "test", "staging").contains(environment)
        || spring.acceptsProfiles(org.springframework.core.env.Profiles.of("production", "prod")))
      throw new IllegalStateException("Mock payments require an isolated environment");
    this.mapper = mapper;
  }

  public void submit(String type, String id) {
    // Persist channel acceptance in the caller's transaction; acceptance is never a receipt.
    mapper.submitWriteMockChannelResults(type, Long.valueOf(id));
  }

  public Result query(String type, String id) {
    var rows = mapper.queryRowsMockChannelResults(type, Long.valueOf(id));
    return rows.isEmpty() ? Result.UNKNOWN : Result.valueOf(rows.get(0).get("result").toString());
  }

  public long revision(String type, String id) {
    return mapper.revisionValueMockChannelResults(type, Long.valueOf(id));
  }

  public void record(String type, String id, Result result) {
    // A previously confirmed receipt cannot turn back into failure/processing.
    mapper.recordWriteMockChannelResults(result.name(), type, Long.valueOf(id));
  }

  public void retryRefund(String id) {
    // Retry the original channel identity; never create a second refund transfer.
    if (mapper.retryRefundWriteMockChannelResults(Long.valueOf(id)) != 1)
      throw new IdentityFailure(409, "PAYMENT_RESULT_PENDING");
  }
}
