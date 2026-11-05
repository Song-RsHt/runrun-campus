package com.zhangxuan.runrunv2.mapper.courier;

import org.springframework.stereotype.Repository;

/**
 * SQL for CourierCertificationV2Service. Uses the caller's transaction-bound JDBC connection; never
 * opens or commits a transaction.
 */
@Repository
public class CourierCertificationV2Mapper {

    private static final String base = " FROM runrun_v2.fulfillment_tasks t JOIN runrun_v2.v2_orders o ON o.id=t.order_id WHERE" + " t.courier_id=? AND t.status='DELIVERED'";

    private static final String where = " WHERE u.role='COURIER' AND (CAST(? AS text) IS NULL OR c.status=?) AND (CAST(? AS text)" + " IS NULL OR u.phone ILIKE ?)";

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public CourierCertificationV2Mapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> applyRowsCourierCertifications(long courier) {
        return jdbc.queryForList("SELECT * FROM runrun_v2.courier_certifications WHERE courier_id=? FOR UPDATE", courier);
    }

    public int applyWriteCourierCertifications(long courier) {
        return jdbc.update("INSERT INTO runrun_v2.courier_certifications(courier_id,status,submitted_at)" + " VALUES(?,'PENDING',now())", courier);
    }

    public int applyWriteCourierCertifications2(long courier) {
        return jdbc.update("UPDATE runrun_v2.courier_certifications SET" + " status='PENDING',rejection_reason=NULL,submitted_at=now(),version=version+1,updated_at=now()" + " WHERE courier_id=?", courier);
    }

    public java.lang.Long applyValueCourierCertifications(long courier) {
        return jdbc.queryForObject("SELECT id FROM runrun_v2.courier_certifications WHERE courier_id=?", Long.class, courier);
    }

    public int uploadWriteCourierCertifications(java.lang.String resource, java.lang.String key, long id, long expectedVersion) {
        return jdbc.update("UPDATE runrun_v2.courier_certifications SET" + " submitted_photo_resource_id=?,submitted_photo_storage_key=?,version=version+1,updated_at=now()" + " WHERE id=? AND version=?", resource, key, id, expectedVersion);
    }

    public int uploadWriteCourierCertifications2(long id, long parameter2) {
        return jdbc.update("UPDATE runrun_v2.courier_certifications SET updated_at=now() WHERE id=? AND version=?", id, parameter2);
    }

    public int decideWriteCourierCertifications(long id, long expectedVersion) {
        return jdbc.update("UPDATE runrun_v2.courier_certifications SET" + " status='APPROVED',eligible=TRUE,approved_photo_resource_id=submitted_photo_resource_id,approved_photo_storage_key=submitted_photo_storage_key,approved_photo_at=now(),submitted_photo_resource_id=NULL,submitted_photo_storage_key=NULL,reviewed_at=now(),version=version+1,updated_at=now()" + " WHERE id=? AND version=?", id, expectedVersion);
    }

    public int decideWriteCourierCertifications2(java.lang.String reason, long id, long expectedVersion) {
        return jdbc.update("UPDATE runrun_v2.courier_certifications SET" + " status='REJECTED',rejection_reason=?,reviewed_at=now(),version=version+1,updated_at=now()" + " WHERE id=? AND version=?", reason, id, expectedVersion);
    }

    public java.lang.Long readValueEvidenceDeletionAudits(long id, java.lang.String actual) {
        return jdbc.queryForObject("SELECT count(*) FROM runrun_v2.evidence_deletion_audits WHERE certification_id=? AND" + " resource_id=? AND status='DELETING'", Long.class, id, actual);
    }

    public int readWriteAdminAuditLogs(long parameter1, long id, java.lang.String requestId, java.lang.String parameter4, java.lang.String actual) {
        return jdbc.update("INSERT INTO" + " runrun_v2.admin_audit_logs(administrator_id,action,target_type,target_id,request_id,reason,metadata)" + " VALUES(?,'COURIER_CERTIFICATION_PHOTO_READ','COURIER_CERTIFICATION',?,?,?,jsonb_build_object('photoResourceId',?))" + " ON CONFLICT (administrator_id,action,request_id) DO NOTHING", parameter1, id, requestId, parameter4, actual);
    }

    public java.lang.Long adminListValueAppUsers(java.lang.String filter, java.lang.String binding3, java.lang.String q, java.lang.String binding5) {
        return jdbc.queryForObject("SELECT count(*) FROM runrun_v2.app_users u JOIN runrun_v2.courier_certifications c ON" + " c.courier_id=u.id" + where, Long.class, filter, binding3, q, binding5);
    }

    public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> adminListRowsAppUsers(java.lang.String filter, java.lang.String binding2, java.lang.String q, java.lang.String binding4, int safeSize, long parameter6) {
        return jdbc.queryForList("SELECT u.id AS \"courierId\",u.phone,u.created_at AS \"registeredAt\",c.id AS" + " \"certificationId\",c.status,c.version,c.submitted_at AS" + " \"submittedAt\",c.reviewed_at AS \"reviewedAt\",c.rejection_reason AS" + " \"rejectionReason\",c.submitted_photo_resource_id,c.approved_photo_resource_id FROM" + " runrun_v2.app_users u JOIN runrun_v2.courier_certifications c ON c.courier_id=u.id" + where + " ORDER BY u.id LIMIT ? OFFSET ?", filter, binding2, q, binding4, safeSize, parameter6);
    }

    public java.lang.Long performanceValueStatement(long courier) {
        return jdbc.queryForObject("SELECT count(*)" + base, Long.class, courier);
    }

    public java.util.Map<java.lang.String, java.lang.Object> performanceRowStatement(long courier) {
        return jdbc.queryForMap("SELECT count(*) FILTER (WHERE t.delivered_at>=current_date) today, count(*) FILTER (WHERE" + " t.delivered_at>=current_date-interval '7 days') week, count(*) FILTER (WHERE" + " t.delivered_at>=date_trunc('month',now())) AS month_count, count(*) total, count(*)" + " FILTER (WHERE o.service_type='PACKAGE_SMALL') small, count(*) FILTER (WHERE" + " o.service_type='PACKAGE_MEDIUM') medium, count(*) FILTER (WHERE" + " o.service_type='PACKAGE_MEDIUM_LARGE') mediumLarge, count(*) FILTER (WHERE" + " o.service_type='PACKAGE_LARGE') large, count(*) FILTER (WHERE" + " o.service_type='PACKAGE_EXTRA_LARGE') extraLarge, count(*) FILTER (WHERE" + " o.service_type='PACKAGE_XL') xl" + base, courier);
    }

    public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> performanceRowsStatement(long courier, int s, long parameter3) {
        return jdbc.queryForList("SELECT o.id,o.order_no,o.status,o.service_type,o.effective_fee_cents" + " fee_cents,t.delivered_at" + base + " ORDER BY t.delivered_at DESC LIMIT ? OFFSET ?", courier, s, parameter3);
    }

    public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> getMineRowsCourierCertifications(long courier) {
        return jdbc.queryForList("SELECT * FROM runrun_v2.courier_certifications WHERE courier_id=?", courier);
    }

    public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> lockedRowsCourierCertifications(long id) {
        return jdbc.queryForList("SELECT * FROM runrun_v2.courier_certifications WHERE id=? FOR UPDATE", id);
    }

    public java.lang.Long lockActorValueAppUsers(long actor) {
        return jdbc.queryForObject("SELECT id FROM runrun_v2.app_users WHERE id=? FOR UPDATE", Long.class, actor);
    }

    public int eventWriteCourierCertificationEvents(long id, long actor, java.lang.String kind, java.lang.String type, java.lang.String request, long binding6) {
        return jdbc.update("INSERT INTO" + " runrun_v2.courier_certification_events(certification_id,actor_id,actor_kind,event_type,request_id,certification_version)" + " SELECT ?,?,?,?, ?,version FROM runrun_v2.courier_certifications WHERE id=?", id, actor, kind, type, request, binding6);
    }
}
