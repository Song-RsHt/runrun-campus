package com.zhangxuan.runrunv2.mapper.evidence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 新系统证据保全与删除数据访问；使用调用方事务绑定的 JDBC 连接，不开启或提交事务。 */
@Repository
public class CourierEvidenceGovernanceMapper {

    private final JdbcTemplate jdbc;

    public CourierEvidenceGovernanceMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Long countOrderById(Long orderId) {
        return jdbc.queryForObject(
                """
                SELECT count(*)
                FROM runrun_v2.v2_orders
                WHERE id = ?
                """,
                Long.class,
                orderId);
    }

    public Long countCertificationById(Long certificationId) {
        return jdbc.queryForObject(
                """
                SELECT count(*)
                FROM runrun_v2.courier_certifications
                WHERE id = ?
                """,
                Long.class,
                certificationId);
    }

    public Long insertEvidenceHold(
            Long orderId,
            Long certificationId,
            String reason,
            long ownerId,
            OffsetDateTime reviewDueAt) {
        return jdbc.queryForObject(
                """
                INSERT INTO runrun_v2.evidence_holds (
                    order_id,
                    certification_id,
                    reason,
                    owner_id,
                    review_due_at
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                RETURNING id
                """,
                Long.class,
                orderId,
                certificationId,
                reason,
                ownerId,
                reviewDueAt);
    }

    public int insertHoldCreatedAudit(
            long administratorId,
            long holdId,
            String requestId,
            String reason,
            SqlParameterValue orderIdParameter,
            SqlParameterValue certificationIdParameter) {
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
                    'EVIDENCE_HOLD_CREATED',
                    'EVIDENCE_HOLD',
                    ?,
                    ?,
                    ?,
                    jsonb_build_object('orderId', ?, 'certificationId', ?)
                )
                """,
                administratorId,
                holdId,
                requestId,
                reason,
                orderIdParameter,
                certificationIdParameter);
    }

    /** 读取并锁定 runrun_v2.evidence_holds 的匹配行；锁持有至调用方事务结束。 */
    public List<Map<String, Object>> lockEvidenceHold(long holdId) {
        return jdbc.queryForList(
                """
                SELECT order_id,
                    certification_id,
                    released_at
                FROM runrun_v2.evidence_holds
                WHERE id = ?
                FOR UPDATE
                """,
                holdId);
    }

    public int releaseEvidenceHold(long holdId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.evidence_holds
                SET released_at = now()
                WHERE id = ?
                """,
                holdId);
    }

    public int insertHoldReleasedAudit(
            long administratorId, long targetId, String requestId, String reason) {
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
                    'EVIDENCE_HOLD_RELEASED',
                    'EVIDENCE_HOLD',
                    ?,
                    ?,
                    ?,
                    '{}'::jsonb
                )
                """,
                administratorId,
                targetId,
                requestId,
                reason);
    }

    public Long countActiveCertificationHolds(long certificationId) {
        return jdbc.queryForObject(
                """
                SELECT count(*)
                FROM runrun_v2.evidence_holds
                WHERE certification_id = ?
                    AND released_at IS NULL
                """,
                Long.class,
                certificationId);
    }

    public int insertDeletionRequestIfAbsent(
            long certificationId,
            String resourceId,
            String storageKey,
            OffsetDateTime deleteAfter,
            long existingCertificationId,
            String existingResourceId) {
        return jdbc.update(
                """
                INSERT INTO runrun_v2.evidence_deletion_audits (
                    certification_id,
                    resource_type,
                    resource_id,
                    resource_storage_key,
                    delete_after,
                    policy_snapshot,
                    status
                )
                SELECT ?,
                    'STUDENT_CARD',
                    ?,
                    ?,
                    ?,
                    jsonb_build_object (
                    'retentionDays',
                    30
                ),
                    'REQUESTED'
                WHERE NOT EXISTS (

                    SELECT 1
                    FROM runrun_v2.evidence_deletion_audits
                    WHERE certification_id = ?
                        AND resource_id = ?
                        AND status IN ('REQUESTED', 'DELETING', 'SUCCEEDED')
                )
                """,
                certificationId,
                resourceId,
                storageKey,
                deleteAfter,
                existingCertificationId,
                existingResourceId);
    }

    /** 读取并锁定 runrun_v2.evidence_deletion_audits 的匹配行；锁持有至调用方事务结束。跳过其他事务已锁定的候选，保持原领取顺序与批次条件。 */
    public List<Map<String, Object>> lockDueDeletions() {
        return jdbc.queryForList(
                """
                SELECT id,
                    certification_id,
                    resource_id,
                    resource_storage_key
                FROM runrun_v2.evidence_deletion_audits
                WHERE status IN ('REQUESTED', 'FAILED')
                    AND delete_after <= now()
                ORDER BY id
                FOR UPDATE SKIP LOCKED
                """);
    }

    public List<Map<String, Object>> findExpiredApprovedPhotos() {
        return jdbc.queryForList(
                """
                SELECT c.id,
                    c.approved_photo_resource_id,
                    c.approved_photo_storage_key,
                    c.approved_photo_at
                FROM runrun_v2.courier_certifications c
                WHERE c.approved_photo_resource_id IS NOT NULL
                    AND c.approved_photo_at <= now() - interval '30 days'
                    AND NOT EXISTS (
                    SELECT 1
                    FROM runrun_v2.evidence_deletion_audits d
                    WHERE d.certification_id = c.id
                        AND d.resource_id = c.approved_photo_resource_id
                        AND d.status IN ('REQUESTED', 'DELETING', 'SUCCEEDED'))
                """);
    }

    public Long countActiveHoldsBeforeDeletion(long certificationId) {
        return jdbc.queryForObject(
                """
                SELECT count(*)
                FROM runrun_v2.evidence_holds
                WHERE certification_id = ?
                    AND released_at IS NULL
                """,
                Long.class,
                certificationId);
    }

    public int markDeletionInProgress(long deletionAuditId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.evidence_deletion_audits
                SET status = 'DELETING',
                    attempt_no = attempt_no + 1
                WHERE id = ?
                """,
                deletionAuditId);
    }

    public int clearSubmittedPhoto(long certificationId, String resourceId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.courier_certifications
                SET submitted_photo_resource_id = NULL,
                    submitted_photo_storage_key = NULL
                WHERE id = ?
                    AND submitted_photo_resource_id = ?
                """,
                certificationId,
                resourceId);
    }

    public int clearApprovedPhoto(long certificationId, String resourceId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.courier_certifications
                SET approved_photo_resource_id = NULL,
                    approved_photo_storage_key = NULL,
                    approved_photo_at = NULL
                WHERE id = ?
                    AND approved_photo_resource_id = ?
                """,
                certificationId,
                resourceId);
    }

    public int markDeletionSucceeded(long deletionAuditId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.evidence_deletion_audits
                SET status = 'SUCCEEDED',
                    finished_at = now(),
                    resource_storage_key = NULL
                WHERE id = ?
                """,
                deletionAuditId);
    }

    public int markDeletionFailed(String errorCode, long deletionAuditId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.evidence_deletion_audits
                SET status = 'FAILED',
                    error_code = ?,
                    finished_at = now()
                WHERE id = ?
                """,
                errorCode,
                deletionAuditId);
    }

    /** 读取并锁定 runrun_v2.v2_orders 的匹配行；锁持有至调用方事务结束。 */
    public Long lockOrder(Long orderId) {
        return jdbc.queryForObject(
                """
                SELECT id
                FROM runrun_v2.v2_orders
                WHERE id = ?
                FOR UPDATE
                """,
                Long.class,
                orderId);
    }

    /** 读取并锁定 runrun_v2.courier_certifications 的匹配行；锁持有至调用方事务结束。 */
    public Long lockCertification(Long certificationId) {
        return jdbc.queryForObject(
                """
                SELECT id
                FROM runrun_v2.courier_certifications
                WHERE id = ?
                FOR UPDATE
                """,
                Long.class,
                certificationId);
    }
}
