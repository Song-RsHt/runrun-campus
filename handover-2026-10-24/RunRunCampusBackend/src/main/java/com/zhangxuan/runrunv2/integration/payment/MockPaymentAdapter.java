package com.zhangxuan.runrunv2.integration.payment;

import com.zhangxuan.runrunv2.enums.Result;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.mapper.MockPaymentMapper;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;























/** Database-backed isolated channel. No merchant SDK, network call or client amount. */
@Component
@ConditionalOnProperty(name = "rrc.payment.mock-enabled", havingValue = "true")
public class MockPaymentAdapter {

  private final MockPaymentMapper mapper;

  public MockPaymentAdapter(
      MockPaymentMapper mapper,
      @Value("${rrc.identity.environment}") String environment,
      Environment spring) {
    if (!Set.of("local", "test", "staging").contains(environment)
        || spring.acceptsProfiles(Profiles.of("production", "prod")))
      throw new IllegalStateException("Mock payments require an isolated environment");
    this.mapper = mapper;
  }

  public void submit(String type, String id) {
    // Persist channel acceptance in the caller's transaction; acceptance is never a receipt.
    mapper.insertProcessingResult(type, Long.valueOf(id));
  }

  public Result query(String type, String id) {
    var rows = mapper.findResult(type, Long.valueOf(id));
    return rows.isEmpty() ? Result.UNKNOWN : Result.valueOf(rows.get(0).get("result").toString());
  }

  public long revision(String type, String id) {
    return mapper.findResultVersion(type, Long.valueOf(id));
  }

  public void record(String type, String id, Result result) {
    // A previously confirmed receipt cannot turn back into failure/processing.
    mapper.updateResultUnlessSuccessful(result.name(), type, Long.valueOf(id));
  }

  public void retryRefund(String id) {
    // Retry the original channel identity; never create a second refund transfer.
    if (mapper.retryFailedRefund(Long.valueOf(id)) != 1)
      throw new IdentityFailure(409, "PAYMENT_RESULT_PENDING");
  }
}
