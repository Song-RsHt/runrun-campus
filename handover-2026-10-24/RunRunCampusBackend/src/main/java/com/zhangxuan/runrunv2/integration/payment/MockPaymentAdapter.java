package com.zhangxuan.runrunv2.integration.payment;

import com.zhangxuan.runrunv2.enums.payment.Result;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.mapper.payment.MockPaymentMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 隔离环境的数据库模拟渠道；不使用商户 SDK、网络调用或客户端金额作为事实。 */
@Component
@ConditionalOnProperty(name = "rrc.payment.mock-enabled", havingValue = "true")
public class MockPaymentAdapter {

    private final MockPaymentMapper mapper;

    public MockPaymentAdapter(
            MockPaymentMapper mapper,
            @Value("${rrc.identity.environment}") String environment,
            Environment spring) {
        if (!Set.of("local", "test", "staging").contains(environment)
                || spring.acceptsProfiles(Profiles.of("production", "prod"))) {
            throw new IllegalStateException("Mock payments require an isolated environment");
        }
        this.mapper = mapper;
    }

    public void submit(String type, String id) {
        // 渠道受理与业务使用同一事务落库；受理并不等于收到付款。
        mapper.insertProcessingResult(type, Long.valueOf(id));
    }

    public Result query(String type, String id) {
        var rows = mapper.findResult(type, Long.valueOf(id));
        return rows.isEmpty()
                ? Result.UNKNOWN
                : Result.valueOf(rows.get(0).get("result").toString());
    }

    public long revision(String type, String id) {
        return mapper.findResultVersion(type, Long.valueOf(id));
    }

    public void record(String type, String id, Result result) {
        // 已确认的成功回执不能退回失败或处理中。
        mapper.updateResultUnlessSuccessful(result.name(), type, Long.valueOf(id));
    }

    public void retryRefund(String id) {
        // 重试沿用原渠道身份，避免再次转出同一笔退款。
        if (mapper.retryFailedRefund(Long.valueOf(id)) != 1) {
            throw new IdentityFailure(409, "PAYMENT_RESULT_PENDING");
        }
    }
}
