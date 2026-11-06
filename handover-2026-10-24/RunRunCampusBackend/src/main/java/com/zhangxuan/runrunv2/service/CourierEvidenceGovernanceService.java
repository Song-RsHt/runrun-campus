package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.integration.idempotency.OrderIdempotency;
import com.zhangxuan.runrunv2.integration.storage.V2StudentCardStorage;
import com.zhangxuan.runrunv2.mapper.CourierEvidenceGovernanceMapper;
import com.zhangxuan.runrunv2.vo.HoldResult;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface CourierEvidenceGovernanceService {

    HoldResult hold(String token, Long orderId, Long certificationId, String reason, Instant due, String requestId);

    HoldResult release(String token, long holdId, String requestId);

    void requestCertificationDeletion(long certificationId, String resourceId, String storageKey, Instant deleteAfter);

    void cleanupDue();
}
