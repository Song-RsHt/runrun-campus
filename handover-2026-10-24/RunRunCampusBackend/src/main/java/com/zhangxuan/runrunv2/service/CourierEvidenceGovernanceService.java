package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.vo.HoldResult;
import java.time.Instant;















/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface CourierEvidenceGovernanceService {

    HoldResult hold(String token, Long orderId, Long certificationId, String reason, Instant due, String requestId);

    HoldResult release(String token, long holdId, String requestId);

    void requestCertificationDeletion(long certificationId, String resourceId, String storageKey, Instant deleteAfter);

    void cleanupDue();
}
