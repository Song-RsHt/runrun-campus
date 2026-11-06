package com.zhangxuan.runrunv2.mapper.courier;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;






/**
 * SQL for CourierCertificationV2Service. Uses the caller's transaction-bound JDBC connection; never
 * opens or commits a transaction.
 */
@Repository
public class CourierCertificationV2Mapper {

  private static final String base =
      """
      FROM runrun_v2.fulfillment_tasks t
      JOIN runrun_v2.v2_orders o
      ON o.id = t.order_id
      WHERE t.courier_id = ?
          AND t.status = 'DELIVERED'
      """;

  private static final String where =
      """
      WHERE u.role = 'COURIER'
          AND (CAST(? AS text) IS NULL
              OR c.status = ?)
          AND (CAST(? AS text) IS NULL
              OR u.phone ILIKE ?)
      """;

  private final JdbcTemplate jdbc;

  public CourierCertificationV2Mapper(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> lockCourierCertification(long courierId) {
    return jdbc.queryForList(
        """
        SELECT *
        FROM runrun_v2.courier_certifications
        WHERE courier_id = ?
        FOR UPDATE
        """,
        courierId);
  }

  public int insertPendingCertification(long courierId) {
    return jdbc.update(
        """
        INSERT INTO runrun_v2.courier_certifications (
            courier_id,
            status,
            submitted_at
        )
        VALUES (
            ?,
            'PENDING',
            now()
        )
        """,
        courierId);
  }

  public int resubmitCertification(long courierId) {
    return jdbc.update(
        """
        UPDATE runrun_v2.courier_certifications
        SET status = 'PENDING',
            rejection_reason = NULL,
            submitted_at = now(),
            version = version + 1,
            updated_at = now()
        WHERE courier_id = ?
        """,
        courierId);
  }

  public Long findCertificationId(long courierId) {
    return jdbc.queryForObject(
        """
        SELECT id
        FROM runrun_v2.courier_certifications
        WHERE courier_id = ?
        """,
        Long.class,
        courierId);
  }

  public int updateSubmittedPhoto(
      String resourceId, String storageKey, long certificationId, long expectedVersion) {
    return jdbc.update(
        """
        UPDATE runrun_v2.courier_certifications
        SET submitted_photo_resource_id = ?,
            submitted_photo_storage_key = ?,
            version = version + 1,
            updated_at = now()
        WHERE id = ?
            AND version = ?
        """,
        resourceId,
        storageKey,
        certificationId,
        expectedVersion);
  }

  public int touchCertificationIfVersionMatches(long certificationId, long expectedVersion) {
    return jdbc.update(
        """
        UPDATE runrun_v2.courier_certifications
        SET updated_at = now()
        WHERE id = ?
            AND version = ?
        """,
        certificationId,
        expectedVersion);
  }

  public int approveCertification(long certificationId, long expectedVersion) {
    return jdbc.update(
        """
        UPDATE runrun_v2.courier_certifications
        SET status = 'APPROVED',
            eligible = TRUE,
            approved_photo_resource_id = submitted_photo_resource_id,
            approved_photo_storage_key = submitted_photo_storage_key,
            approved_photo_at = now(),
            submitted_photo_resource_id = NULL,
            submitted_photo_storage_key = NULL,
            reviewed_at = now(),
            version = version + 1,
            updated_at = now()
        WHERE id = ?
            AND version = ?
        """,
        certificationId,
        expectedVersion);
  }

  public int rejectCertification(String reason, long certificationId, long expectedVersion) {
    return jdbc.update(
        """
        UPDATE runrun_v2.courier_certifications
        SET status = 'REJECTED',
            rejection_reason = ?,
            reviewed_at = now(),
            version = version + 1,
            updated_at = now()
        WHERE id = ?
            AND version = ?
        """,
        reason,
        certificationId,
        expectedVersion);
  }

  public Long countPhotoDeletionsInProgress(long certificationId, String resourceId) {
    return jdbc.queryForObject(
        """
        SELECT count(*)
        FROM runrun_v2.evidence_deletion_audits
        WHERE certification_id = ?
            AND resource_id = ?
            AND status = 'DELETING'
        """,
        Long.class,
        certificationId,
        resourceId);
  }

  public int insertPhotoReadAudit(
      long administratorId,
      long certificationId,
      String requestId,
      String reason,
      String resourceId) {
    return jdbc.update(
        """
        INSERT INTO runrun_v2.admin_audit_logs (
            administrator_id,
            action,
            target_type,
            target_id,
            request_id,
            reason,
            metadata
        )
        VALUES (
            ?,
            'COURIER_CERTIFICATION_PHOTO_READ',
            'COURIER_CERTIFICATION',
            ?,
            ?,
            ?,
            jsonb_build_object('photoResourceId', ?)
        )
        ON CONFLICT (
            administrator_id,
            action,
            request_id
        ) DO NOTHING
        """,
        administratorId,
        certificationId,
        requestId,
        reason,
        resourceId);
  }

  public Long countCertifications(
      String statusFilter, String status, String phoneFilter, String phonePattern) {
    return jdbc.queryForObject(
        """
        SELECT count(*)
        FROM runrun_v2.app_users u
        JOIN runrun_v2.courier_certifications c
        ON c.courier_id = u.id
        """
            + where,
        Long.class,
        statusFilter,
        status,
        phoneFilter,
        phonePattern);
  }

  public List<Map<String, Object>> pageCertifications(
      String statusFilter,
      String status,
      String phoneFilter,
      String phonePattern,
      int limit,
      long offset) {
    return jdbc.queryForList(
        """
        SELECT u.id AS \"courierId\",
            u.phone,
            u.created_at AS \"registeredAt\",
            c.id AS \"certificationId\",
            c.status,
            c.version,
            c.submitted_at AS \"submittedAt\",
            c.reviewed_at AS \"reviewedAt\",
            c.rejection_reason AS \"rejectionReason\",
            c.submitted_photo_resource_id,
            c.approved_photo_resource_id
        FROM runrun_v2.app_users u
        JOIN runrun_v2.courier_certifications c
        ON c.courier_id = u.id
        """
            + where
            + """
              \sORDER BY u.id
              LIMIT ?
              OFFSET ?
              """,
        statusFilter,
        status,
        phoneFilter,
        phonePattern,
        limit,
        offset);
  }

  public Long countDeliveredTasks(long courierId) {
    return jdbc.queryForObject(
        """
        SELECT count(*)
        """
            + base,
        Long.class,
        courierId);
  }

  public Map<String, Object> summarizeDeliveredTasks(long courierId) {
    return jdbc.queryForMap(
        """
        SELECT count(*) FILTER (
            WHERE t.delivered_at >= current_date) today,
            count(*) FILTER (
            WHERE t.delivered_at >= current_date - interval '7 days') week,
            count(*) FILTER (
            WHERE t.delivered_at >= date_trunc('month', now())) AS month_count,
            count(*) total,
            count(*) FILTER (
            WHERE o.service_type = 'PACKAGE_SMALL') small,
            count(*) FILTER (
            WHERE o.service_type = 'PACKAGE_MEDIUM') medium,
            count(*) FILTER (
            WHERE o.service_type = 'PACKAGE_MEDIUM_LARGE') mediumLarge,
            count(*) FILTER (
            WHERE o.service_type = 'PACKAGE_LARGE') large,
            count(*) FILTER (
            WHERE o.service_type = 'PACKAGE_EXTRA_LARGE') extraLarge,
            count(*) FILTER (
            WHERE o.service_type = 'PACKAGE_XL') xl
        """
            + base,
        courierId);
  }

  public List<Map<String, Object>> pageDeliveredTasks(long courierId, int limit, long offset) {
    return jdbc.queryForList(
        """
        SELECT o.id,
            o.order_no,
            o.status,
            o.service_type,
            o.effective_fee_cents fee_cents,
            t.delivered_at
        """
            + base
            + """
              \sORDER BY t.delivered_at DESC
              LIMIT ?
              OFFSET ?
              """,
        courierId,
        limit,
        offset);
  }

  public List<Map<String, Object>> findCourierCertification(long courierId) {
    return jdbc.queryForList(
        """
        SELECT *
        FROM runrun_v2.courier_certifications
        WHERE courier_id = ?
        """,
        courierId);
  }

  public List<Map<String, Object>> lockCertification(long certificationId) {
    return jdbc.queryForList(
        """
        SELECT *
        FROM runrun_v2.courier_certifications
        WHERE id = ?
        FOR UPDATE
        """,
        certificationId);
  }

  public Long lockActor(long actorId) {
    return jdbc.queryForObject(
        """
        SELECT id
        FROM runrun_v2.app_users
        WHERE id = ?
        FOR UPDATE
        """,
        Long.class,
        actorId);
  }

  public int insertCertificationEvent(
      long certificationId,
      long actorId,
      String actorKind,
      String eventType,
      String requestId,
      long versionSourceCertificationId) {
    return jdbc.update(
        """
        INSERT INTO runrun_v2.courier_certification_events (
            certification_id,
            actor_id,
            actor_kind,
            event_type,
            request_id,
            certification_version
        )
        SELECT ?,
            ?,
            ?,
            ?,
            ?,
            version
        FROM runrun_v2.courier_certifications
        WHERE id = ?
        """,
        certificationId,
        actorId,
        actorKind,
        eventType,
        requestId,
        versionSourceCertificationId);
  }
}
