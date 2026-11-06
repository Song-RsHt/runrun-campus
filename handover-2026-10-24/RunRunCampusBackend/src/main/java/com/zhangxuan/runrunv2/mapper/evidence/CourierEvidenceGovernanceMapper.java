package com.zhangxuan.runrunv2.mapper.evidence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * SQL for CourierEvidenceGovernanceService. Uses the caller's transaction-bound JDBC connection;
 * never opens or commits a transaction.
 */
@Repository
public class CourierEvidenceGovernanceMapper {

  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public CourierEvidenceGovernanceMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Long holdValueV(Long orderId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.v2_orders WHERE id=?", Long.class, orderId);
  }

  public Long holdValueCourierCertifications(Long certificationId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.courier_certifications WHERE id=?",
        Long.class,
        certificationId);
  }

  public Long holdValueEvidenceHolds(
      Long orderId, Long certificationId, String reason, long ownerId, OffsetDateTime reviewDueAt) {
    return jdbc.queryForObject(
        "INSERT INTO"
            + " runrun_v2.evidence_holds(order_id,certification_id,reason,owner_id,review_due_at)"
            + " VALUES(?,?,?,?,?) RETURNING id",
        Long.class,
        orderId,
        certificationId,
        reason,
        ownerId,
        reviewDueAt);
  }

  public int holdWriteAdminAuditLogs(
      long owner,
      long id,
      String requestId,
      String value,
      org.springframework.jdbc.core.SqlParameterValue value2,
      org.springframework.jdbc.core.SqlParameterValue value3) {
    return jdbc.update(
        "INSERT INTO"
            + " runrun_v2.admin_audit_logs(administrator_id,action,target_type,target_id,request_id,reason,metadata)"
            + " VALUES(?,'EVIDENCE_HOLD_CREATED','EVIDENCE_HOLD',?,?,?,jsonb_build_object('orderId',?,'certificationId',?))",
        owner,
        id,
        requestId,
        value,
        value2,
        value3);
  }

  public List<Map<String, Object>> releaseRowsEvidenceHolds(long holdId) {
    return jdbc.queryForList(
        "SELECT order_id,certification_id,released_at FROM runrun_v2.evidence_holds WHERE id=? FOR"
            + " UPDATE",
        holdId);
  }

  public int releaseWriteEvidenceHolds(long holdId) {
    return jdbc.update("UPDATE runrun_v2.evidence_holds SET released_at=now() WHERE id=?", holdId);
  }

  public int releaseWriteAdminAuditLogs(
      long administratorId, long targetId, String requestId, String reason) {
    return jdbc.update(
        "INSERT INTO"
            + " runrun_v2.admin_audit_logs(administrator_id,action,target_type,target_id,request_id,reason,metadata)"
            + " VALUES(?,'EVIDENCE_HOLD_RELEASED','EVIDENCE_HOLD',?,?,?,'{}'::jsonb)",
        administratorId,
        targetId,
        requestId,
        reason);
  }

  public Long requestCertificationDeletionValueEvidenceHolds(long certificationId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.evidence_holds WHERE certification_id=? AND released_at IS"
            + " NULL",
        Long.class,
        certificationId);
  }

  public int requestCertificationDeletionWriteEvidenceDeletionAudits(
      long certificationId,
      String resourceId,
      String storageKey,
      OffsetDateTime timestamp,
      long certificationId2,
      String resourceId2) {
    return jdbc.update(
        "INSERT INTO"
            + " runrun_v2.evidence_deletion_audits(certification_id,resource_type,resource_id,resource_storage_key,delete_after,policy_snapshot,status)"
            + " SELECT ?, 'STUDENT_CARD', ?, ?, ?, jsonb_build_object('retentionDays',30),"
            + " 'REQUESTED' WHERE NOT EXISTS (SELECT 1 FROM runrun_v2.evidence_deletion_audits"
            + " WHERE certification_id=? AND resource_id=? AND status IN"
            + " ('REQUESTED','DELETING','SUCCEEDED'))",
        certificationId,
        resourceId,
        storageKey,
        timestamp,
        certificationId2,
        resourceId2);
  }

  public List<Map<String, Object>> cleanupDueRowsEvidenceDeletionAudits() {
    return jdbc.queryForList(
        "SELECT id,certification_id,resource_id,resource_storage_key FROM"
            + " runrun_v2.evidence_deletion_audits WHERE status IN ('REQUESTED','FAILED') AND"
            + " delete_after<=now() ORDER BY id FOR UPDATE SKIP LOCKED");
  }

  public List<Map<String, Object>> cleanupDueRowsCourierCertifications() {
    return jdbc.queryForList(
        "SELECT c.id,c.approved_photo_resource_id,c.approved_photo_storage_key,c.approved_photo_at"
            + " FROM runrun_v2.courier_certifications c WHERE c.approved_photo_resource_id IS NOT"
            + " NULL AND c.approved_photo_at<=now()-interval '30 days' AND NOT EXISTS (SELECT 1"
            + " FROM runrun_v2.evidence_deletion_audits d WHERE d.certification_id=c.id AND"
            + " d.resource_id=c.approved_photo_resource_id AND d.status IN"
            + " ('REQUESTED','DELETING','SUCCEEDED'))");
  }

  public Long deleteOneValueEvidenceHolds(long cert) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.evidence_holds WHERE certification_id=? AND released_at IS"
            + " NULL",
        Long.class,
        cert);
  }

  public int deleteOneWriteEvidenceDeletionAudits(long id) {
    return jdbc.update(
        "UPDATE runrun_v2.evidence_deletion_audits SET status='DELETING',attempt_no=attempt_no+1"
            + " WHERE id=?",
        id);
  }

  public int deleteOneWriteCourierCertifications(long cert, String resource) {
    return jdbc.update(
        "UPDATE runrun_v2.courier_certifications SET"
            + " submitted_photo_resource_id=NULL,submitted_photo_storage_key=NULL WHERE id=? AND"
            + " submitted_photo_resource_id=?",
        cert,
        resource);
  }

  public int deleteOneWriteCourierCertifications2(long cert, String resource) {
    return jdbc.update(
        "UPDATE runrun_v2.courier_certifications SET"
            + " approved_photo_resource_id=NULL,approved_photo_storage_key=NULL,approved_photo_at=NULL"
            + " WHERE id=? AND approved_photo_resource_id=?",
        cert,
        resource);
  }

  public int deleteOneWriteEvidenceDeletionAudits2(long id) {
    return jdbc.update(
        "UPDATE runrun_v2.evidence_deletion_audits SET"
            + " status='SUCCEEDED',finished_at=now(),resource_storage_key=NULL WHERE id=?",
        id);
  }

  public int deleteOneWriteEvidenceDeletionAudits3(String simpleName, long id) {
    return jdbc.update(
        "UPDATE runrun_v2.evidence_deletion_audits SET"
            + " status='FAILED',error_code=?,finished_at=now() WHERE id=?",
        simpleName,
        id);
  }

  public Long lockAggregateValueV(Long orderId) {
    return jdbc.queryForObject(
        "SELECT id FROM runrun_v2.v2_orders WHERE id=? FOR UPDATE", Long.class, orderId);
  }

  public Long lockAggregateValueCourierCertifications(Long certificationId) {
    return jdbc.queryForObject(
        "SELECT id FROM runrun_v2.courier_certifications WHERE id=? FOR UPDATE",
        Long.class,
        certificationId);
  }
}
