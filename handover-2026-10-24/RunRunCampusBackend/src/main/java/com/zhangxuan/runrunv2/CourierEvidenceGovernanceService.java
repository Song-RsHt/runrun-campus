package com.zhangxuan.runrunv2;

import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns retention, legal holds and retryable deletion of v2 student-card objects. */
@Service
public class CourierEvidenceGovernanceService {
    private final JdbcTemplate db;
    private final V2StudentCardStorage storage;
    private final IdentityService identity;
    public CourierEvidenceGovernanceService(JdbcTemplate db, V2StudentCardStorage storage, IdentityService identity) {
        this.db=db; this.storage=storage; this.identity=identity;
    }

    @Transactional
    public long hold(String token, Long orderId, Long certificationId, String reason, Instant due) {
        long owner=Long.parseLong(identity.authorizeForWrite(token,"ADMIN",false).id());
        if ((orderId==null)==(certificationId==null) || reason==null || reason.isBlank() || due==null)
            throw new IdentityFailure(400,"INVALID_REQUEST");
        return db.queryForObject("INSERT INTO runrun_v2.evidence_holds(order_id,certification_id,reason,owner_id,review_due_at) VALUES(?,?,?,?,?) RETURNING id",Long.class,orderId,certificationId,reason,owner,due);
    }

    @Transactional
    public void release(String token, long holdId) {
        long owner=Long.parseLong(identity.authorizeForWrite(token,"ADMIN",false).id());
        if(db.update("UPDATE runrun_v2.evidence_holds SET released_at=now() WHERE id=? AND released_at IS NULL",holdId)!=1)
            throw new IdentityFailure(404,"RESOURCE_NOT_FOUND");
        db.update("INSERT INTO runrun_v2.admin_audit_logs(administrator_id,action,target_type,target_id,request_id,reason,metadata) VALUES(?,'V2_ADMIN_ACCESS_CHANGED','APP_USER',?,?,?, '{}'::jsonb)",owner,owner,"evidence-hold-release-"+holdId,"evidence hold released");
    }

    @Transactional
    public void requestCertificationDeletion(long certificationId, String resourceId, String storageKey, Instant deleteAfter) {
        if (db.queryForObject("SELECT count(*) FROM runrun_v2.evidence_holds WHERE certification_id=? AND released_at IS NULL",Long.class,certificationId)>0) return;
        db.update("INSERT INTO runrun_v2.evidence_deletion_audits(certification_id,resource_type,resource_id,resource_storage_key,delete_after,policy_snapshot,status) SELECT ?, 'STUDENT_CARD', ?, ?, ?, jsonb_build_object('retentionDays',30), 'REQUESTED' WHERE NOT EXISTS (SELECT 1 FROM runrun_v2.evidence_deletion_audits WHERE certification_id=? AND resource_id=? AND status IN ('REQUESTED','DELETING','SUCCEEDED'))",certificationId,resourceId,storageKey,deleteAfter,certificationId,resourceId);
    }

    @Scheduled(fixedDelayString="${rrc.evidence.cleanup-delay-ms:60000}", initialDelayString="${rrc.evidence.cleanup-delay-ms:60000}")
    @Transactional
    public void cleanupDue() {
        db.queryForList("SELECT id,certification_id,resource_id,resource_storage_key FROM runrun_v2.evidence_deletion_audits WHERE status IN ('REQUESTED','FAILED') AND delete_after<=now() ORDER BY id FOR UPDATE SKIP LOCKED").forEach(this::deleteOne);
        db.queryForList("SELECT c.id,c.approved_photo_resource_id,c.approved_photo_storage_key,c.approved_photo_at FROM runrun_v2.courier_certifications c WHERE c.approved_photo_resource_id IS NOT NULL AND c.approved_photo_at<=now()-interval '30 days' AND NOT EXISTS (SELECT 1 FROM runrun_v2.evidence_deletion_audits d WHERE d.certification_id=c.id AND d.resource_id=c.approved_photo_resource_id AND d.status IN ('REQUESTED','DELETING','SUCCEEDED'))").forEach(r -> requestCertificationDeletion(((Number)r.get("id")).longValue(),(String)r.get("approved_photo_resource_id"),(String)r.get("approved_photo_storage_key"),Instant.now()));
    }

    private void deleteOne(Map<String,Object> r) {
        long id=((Number)r.get("id")).longValue(); long cert=((Number)r.get("certification_id")).longValue(); String resource=(String)r.get("resource_id"); String key=(String)r.get("resource_storage_key");
        if(db.queryForObject("SELECT count(*) FROM runrun_v2.evidence_holds WHERE certification_id=? AND released_at IS NULL",Long.class,cert)>0) return;
        db.update("UPDATE runrun_v2.evidence_deletion_audits SET status='DELETING',attempt_no=attempt_no+1 WHERE id=?",id);
        try {
            storage.delete(key);
            db.update("UPDATE runrun_v2.courier_certifications SET submitted_photo_resource_id=NULL,submitted_photo_storage_key=NULL WHERE id=? AND submitted_photo_resource_id=?",cert,resource);
            db.update("UPDATE runrun_v2.courier_certifications SET approved_photo_resource_id=NULL,approved_photo_storage_key=NULL,approved_photo_at=NULL WHERE id=? AND approved_photo_resource_id=?",cert,resource);
            db.update("UPDATE runrun_v2.evidence_deletion_audits SET status='SUCCEEDED',finished_at=now(),resource_storage_key=NULL WHERE id=?",id);
        } catch (Exception e) {
            db.update("UPDATE runrun_v2.evidence_deletion_audits SET status='FAILED',error_code=?,finished_at=now() WHERE id=?",e.getClass().getSimpleName(),id);
        }
    }
}
