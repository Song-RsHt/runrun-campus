package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.integration.idempotency.OrderIdempotency;
import com.zhangxuan.runrunv2.integration.storage.V2StudentCardStorage;
import com.zhangxuan.runrunv2.mapper.CourierCertificationV2Mapper;
import com.zhangxuan.runrunv2.vo.Account;
import com.zhangxuan.runrunv2.vo.Certification;
import java.io.IOException;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.zhangxuan.runrunv2.model.UploadSource;

/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface CourierCertificationV2Service {

    Certification apply(String token, IdentityService identity, String requestId);

    Certification upload(String token, IdentityService identity, long id, long expectedVersion, String requestId, UploadSource file);

    Certification decide(String token, IdentityService identity, long id, long expectedVersion, String resource, String decision, String reason, String requestId);

    byte[] read(String token, IdentityService identity, long id, String resource, String requestId);

    Map<String, Object> adminList(String token, String status, String query, int page, int size);

    Map<String, Object> performance(String token, long courier, int page, int size);

    Certification get(long subject, long id);

    Certification getMine(long courier);
}
