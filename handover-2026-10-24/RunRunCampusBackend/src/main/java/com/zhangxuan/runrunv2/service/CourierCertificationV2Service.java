package com.zhangxuan.runrunv2.service;

import com.zhangxuan.runrunv2.model.UploadSource;
import com.zhangxuan.runrunv2.vo.Certification;
import java.util.Map;





















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
