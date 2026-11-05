package com.zhangxuan.runrunv2.mapper.evidence;

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

  public java.lang.Long holdValueV(java.lang.Long orderId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.v2_orders WHERE id=?", Long.class, orderId);
  }

  public java.lang.Long holdValueCourierCertifications(java.lang.Long certificationId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.courier_certifications WHERE id=?",
        Long.class,
        certificationId);
  }

  public java.lang.Long holdValueEvidenceHolds(
      java.lang.Long orderId,
      java.lang.Long certificationId,
      java.lang.String reason,
      long owner,
      java.time.OffsetDateTime parameter6) {
    return jdbc.queryForObject(
        "INSERT INTO"
            + " runrun_v2.evidence_holds(order_id,certification_id,reason,owner_id,review_due_at)"
            + " VALUES(?,?,?,?,?) RETURNING id",
        Long.class,
        orderId,
        certificationId,
        reason,
        owner,
        parameter6);
  }

  public int holdWriteAdminAuditLogs(
      long owner,
      long id,
      java.lang.String requestId,
      java.lang.String parameter4,
      org.springframework.jdbc.core.SqlParameterValue parameter5,
      org.springframework.jdbc.core.SqlParameterValue parameter6) {
    return jdbc.update(
        "INSERT INTO"
            + " runrun_v2.admin_audit_logs(administrator_id,action,target_type,target_id,request_id,reason,metadata)"
            + " VALUES(?,'EVIDENCE_HOLD_CREATED','EVIDENCE_HOLD',?,?,?,jsonb_build_object('orderId',?,'certificationId',?))",
        owner,
        id,
        requestId,
        parameter4,
        parameter5,
        parameter6);
  }

  public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> releaseRowsEvidenceHolds(
      long holdId) {
    return jdbc.queryForList(
        "SELECT order_id,certification_id,released_at FROM runrun_v2.evidence_holds WHERE id=? FOR"
            + " UPDATE",
        holdId);
  }

  public int releaseWriteEvidenceHolds(long holdId) {
    return jdbc.update("UPDATE runrun_v2.evidence_holds SET released_at=now() WHERE id=?", holdId);
  }

  public int releaseWriteAdminAuditLogs(
      long owner, long holdId, java.lang.String requestId, java.lang.String parameter4) {
    return jdbc.update(
        "INSERT INTO"
            + " runrun_v2.admin_audit_logs(administrator_id,action,target_type,target_id,request_id,reason,metadata)"
            + " VALUES(?,'EVIDENCE_HOLD_RELEASED','EVIDENCE_HOLD',?,?,?,'{}'::jsonb)",
        owner,
        holdId,
        requestId,
        parameter4);
  }

  public java.lang.Long requestCertificationDeletionValueEvidenceHolds(long certificationId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.evidence_holds WHERE certification_id=? AND released_at IS"
            + " NULL",
        Long.class,
        certificationId);
  }

  public int requestCertificationDeletionWriteEvidenceDeletionAudits(
      long certificationId,
      java.lang.String resourceId,
      java.lang.String storageKey,
      java.time.OffsetDateTime parameter4,
      long binding5,
      java.lang.String binding6) {
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
        parameter4,
        binding5,
        binding6);
  }

  public java.util.List<java.util.Map<java.lang.String, java.lang.Object>>
      cleanupDueRowsEvidenceDeletionAudits() {
    return jdbc.queryForList(
        "SELECT id,certification_id,resource_id,resource_storage_key FROM"
            + " runrun_v2.evidence_deletion_audits WHERE status IN ('REQUESTED','FAILED') AND"
            + " delete_after<=now() ORDER BY id FOR UPDATE SKIP LOCKED");
  }

  public java.util.List<java.util.Map<java.lang.String, java.lang.Object>>
      cleanupDueRowsCourierCertifications() {
    return jdbc.queryForList(
        "SELECT c.id,c.approved_photo_resource_id,c.approved_photo_storage_key,c.approved_photo_at"
            + " FROM runrun_v2.courier_certifications c WHERE c.approved_photo_resource_id IS NOT"
            + " NULL AND c.approved_photo_at<=now()-interval '30 days' AND NOT EXISTS (SELECT 1"
            + " FROM runrun_v2.evidence_deletion_audits d WHERE d.certification_id=c.id AND"
            + " d.resource_id=c.approved_photo_resource_id AND d.status IN"
            + " ('REQUESTED','DELETING','SUCCEEDED'))");
  }

  public java.lang.Long deleteOneValueEvidenceHolds(long cert) {
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

  public int deleteOneWriteCourierCertifications(long cert, java.lang.String resource) {
    return jdbc.update(
        "UPDATE runrun_v2.courier_certifications SET"
            + " submitted_photo_resource_id=NULL,submitted_photo_storage_key=NULL WHERE id=? AND"
            + " submitted_photo_resource_id=?",
        cert,
        resource);
  }

  public int deleteOneWriteCourierCertifications2(long cert, java.lang.String resource) {
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

  public int deleteOneWriteEvidenceDeletionAudits3(java.lang.String parameter1, long id) {
    return jdbc.update(
        "UPDATE runrun_v2.evidence_deletion_audits SET"
            + " status='FAILED',error_code=?,finished_at=now() WHERE id=?",
        parameter1,
        id);
  }

  public java.lang.Long lockAggregateValueV(java.lang.Long orderId) {
    return jdbc.queryForObject(
        "SELECT id FROM runrun_v2.v2_orders WHERE id=? FOR UPDATE", Long.class, orderId);
  }

  public java.lang.Long lockAggregateValueCourierCertifications(java.lang.Long certificationId) {
    return jdbc.queryForObject(
        "SELECT id FROM runrun_v2.courier_certifications WHERE id=? FOR UPDATE",
        Long.class,
        certificationId);
  }
}
