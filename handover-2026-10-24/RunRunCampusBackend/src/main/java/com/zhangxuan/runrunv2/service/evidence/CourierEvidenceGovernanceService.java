package com.zhangxuan.runrunv2.service.evidence;

import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.integration.idempotency.OrderIdempotency;
import com.zhangxuan.runrunv2.integration.storage.V2StudentCardStorage;
import com.zhangxuan.runrunv2.mapper.evidence.CourierEvidenceGovernanceMapper;
import com.zhangxuan.runrunv2.service.IdentityService;
import com.zhangxuan.runrunv2.dto.response.evidence.HoldResult;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;































/** Owns retention, legal holds and retryable deletion of v2 student-card objects. */
@Service
public class CourierEvidenceGovernanceService {

  private final CourierEvidenceGovernanceMapper mapper;

  private final V2StudentCardStorage storage;

  private final IdentityService identity;

  private final OrderIdempotency idempotency;

  public CourierEvidenceGovernanceService(
      CourierEvidenceGovernanceMapper mapper,
      V2StudentCardStorage storage,
      IdentityService identity,
      OrderIdempotency idempotency) {
    this.mapper = mapper;
    this.storage = storage;
    this.identity = identity;
    this.idempotency = idempotency;
  }

  @Transactional
  public HoldResult hold(
      String token,
      Long orderId,
      Long certificationId,
      String reason,
      Instant due,
      String requestId) {
    long owner = Long.parseLong(identity.authorizeForWrite(token, "ADMIN", false).id());
    if ((orderId == null) == (certificationId == null)
        || reason == null
        || reason.isBlank()
        || due == null) throw new IdentityFailure(400, "INVALID_REQUEST");
    lockAggregate(orderId, certificationId);
    String content = String.valueOf(orderId) + ":" + certificationId + ":" + reason + ":" + due;
    HoldResult replay =
        idempotency.replay(
            Long.toString(owner), "evidence-hold:create", requestId, content, HoldResult.class);
    if (replay != null) return replay;
    if (orderId != null && mapper.countOrderById(orderId) == 0)
      throw new IdentityFailure(404, "RESOURCE_NOT_FOUND");
    if (certificationId != null && mapper.countCertificationById(certificationId) == 0)
      throw new IdentityFailure(404, "RESOURCE_NOT_FOUND");
    // PostgreSQL JDBC binds OffsetDateTime to TIMESTAMPTZ; Instant itself has no JDBC mapping.
    long id =
        mapper.insertEvidenceHold(
            orderId, certificationId, reason, owner, due.atOffset(ZoneOffset.UTC));
    // jsonb_build_object cannot infer a nullable polymorphic parameter's type; both references are
    // BIGINT.
    mapper.insertHoldCreatedAudit(
        owner,
        id,
        requestId,
        "evidence hold created",
        new SqlParameterValue(Types.BIGINT, orderId),
        new SqlParameterValue(Types.BIGINT, certificationId));
    HoldResult result = new HoldResult(id);
    idempotency.finish(
        Long.toString(owner),
        "evidence-hold:create",
        requestId,
        "EVIDENCE_HOLD",
        Long.toString(id),
        result);
    return result;
  }

  @Transactional
  public HoldResult release(String token, long holdId, String requestId) {
    long owner = Long.parseLong(identity.authorizeForWrite(token, "ADMIN", false).id());
    var rows = mapper.lockEvidenceHold(holdId);
    if (rows.isEmpty()) throw new IdentityFailure(404, "RESOURCE_NOT_FOUND");
    var hold = rows.get(0);
    lockAggregate((Number) hold.get("order_id"), (Number) hold.get("certification_id"));
    HoldResult replay =
        idempotency.replay(
            Long.toString(owner),
            "evidence-hold:release",
            requestId,
            Long.toString(holdId),
            HoldResult.class);
    if (replay != null) return replay;
    if (hold.get("released_at") != null)
      throw new IdentityFailure(409, "EVIDENCE_HOLD_ALREADY_RELEASED");
    mapper.releaseEvidenceHold(holdId);
    mapper.insertHoldReleasedAudit(owner, holdId, requestId, "evidence hold released");
    HoldResult result = new HoldResult(holdId);
    idempotency.finish(
        Long.toString(owner),
        "evidence-hold:release",
        requestId,
        "EVIDENCE_HOLD",
        Long.toString(holdId),
        result);
    return result;
  }

  @Transactional
  public void requestCertificationDeletion(
      long certificationId, String resourceId, String storageKey, Instant deleteAfter) {
    if (mapper.countActiveCertificationHolds(certificationId) > 0) return;
    // Preserve the exact retention instant using the driver's TIMESTAMPTZ representation.
    mapper.insertDeletionRequestIfAbsent(
        certificationId,
        resourceId,
        storageKey,
        deleteAfter.atOffset(ZoneOffset.UTC),
        certificationId,
        resourceId);
  }

  @Transactional
  public void cleanupDue() {
    mapper.lockDueDeletions().forEach(this::deleteOne);
    mapper
        .findExpiredApprovedPhotos()
        .forEach(
            r ->
                requestCertificationDeletion(
                    ((Number) r.get("id")).longValue(),
                    (String) r.get("approved_photo_resource_id"),
                    (String) r.get("approved_photo_storage_key"),
                    Instant.now()));
  }

  private void deleteOne(Map<String, Object> r) {
    long id = ((Number) r.get("id")).longValue();
    long cert = ((Number) r.get("certification_id")).longValue();
    String resource = (String) r.get("resource_id");
    String key = (String) r.get("resource_storage_key");
    lockAggregate(null, cert);
    if (mapper.countActiveHoldsBeforeDeletion(cert) > 0) return;
    mapper.markDeletionInProgress(id);
    try {
      storage.delete(key);
      mapper.clearSubmittedPhoto(cert, resource);
      mapper.clearApprovedPhoto(cert, resource);
      mapper.markDeletionSucceeded(id);
    } catch (Exception e) {
      mapper.markDeletionFailed(e.getClass().getSimpleName(), id);
    }
  }

  private void lockAggregate(Long orderId, Long certificationId) {
    if (orderId != null) mapper.lockOrder(orderId);
    if (certificationId != null) mapper.lockCertification(certificationId);
  }

  private void lockAggregate(Number orderId, Number certificationId) {
    lockAggregate(
        orderId == null ? null : orderId.longValue(),
        certificationId == null ? null : certificationId.longValue());
  }
}
