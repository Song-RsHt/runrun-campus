package com.zhangxuan.runrunv2.service.evidence;
import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.service.identity.IdentityService;
import com.zhangxuan.runrunv2.service.idempotency.OrderIdempotency;
import com.zhangxuan.runrunv2.integration.storage.V2StudentCardStorage;

import java.time.Instant;
import java.time.ZoneOffset;
import java.sql.Types;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns retention, legal holds and retryable deletion of v2 student-card objects. */
@Service
public class CourierEvidenceGovernanceService {
    private final JdbcTemplate db;
    private final V2StudentCardStorage storage;
    private final IdentityService identity;
    private final OrderIdempotency idempotency;
    public CourierEvidenceGovernanceService(JdbcTemplate db, V2StudentCardStorage storage, IdentityService identity, OrderIdempotency idempotency) {
        this.db=db; this.storage=storage; this.identity=identity; this.idempotency=idempotency;
    }

    @Transactional
    public HoldResult hold(String token, Long orderId, Long certificationId, String reason, Instant due, String requestId) {
        long owner=Long.parseLong(identity.authorizeForWrite(token,"ADMIN",false).id());
        if ((orderId==null)==(certificationId==null) || reason==null || reason.isBlank() || due==null)
            throw new IdentityFailure(400,"INVALID_REQUEST");
        lockAggregate(orderId, certificationId);
        String content=String.valueOf(orderId)+":"+certificationId+":"+reason+":"+due;
        HoldResult replay=idempotency.replay(Long.toString(owner),"evidence-hold:create",requestId,content,HoldResult.class);
        if(replay!=null) return replay;
        if(orderId!=null && db.queryForObject("SELECT count(*) FROM runrun_v2.v2_orders WHERE id=?",Long.class,orderId)==0) throw new IdentityFailure(404,"RESOURCE_NOT_FOUND");
        if(certificationId!=null && db.queryForObject("SELECT count(*) FROM runrun_v2.courier_certifications WHERE id=?",Long.class,certificationId)==0) throw new IdentityFailure(404,"RESOURCE_NOT_FOUND");
        // PostgreSQL JDBC binds OffsetDateTime to TIMESTAMPTZ; Instant itself has no JDBC mapping.
        long id=db.queryForObject("INSERT INTO runrun_v2.evidence_holds(order_id,certification_id,reason,owner_id,review_due_at) VALUES(?,?,?,?,?) RETURNING id",Long.class,orderId,certificationId,reason,owner,due.atOffset(ZoneOffset.UTC));
        // jsonb_build_object cannot infer a nullable polymorphic parameter's type; both references are BIGINT.
        db.update("INSERT INTO runrun_v2.admin_audit_logs(administrator_id,action,target_type,target_id,request_id,reason,metadata) VALUES(?,'EVIDENCE_HOLD_CREATED','EVIDENCE_HOLD',?,?,?,jsonb_build_object('orderId',?,'certificationId',?))",owner,id,requestId,"evidence hold created",new SqlParameterValue(Types.BIGINT,orderId),new SqlParameterValue(Types.BIGINT,certificationId));
        HoldResult result=new HoldResult(id); idempotency.finish(Long.toString(owner),"evidence-hold:create",requestId,"EVIDENCE_HOLD",Long.toString(id),result); return result;
    }

    @Transactional
    public HoldResult release(String token, long holdId, String requestId) {
        long owner=Long.parseLong(identity.authorizeForWrite(token,"ADMIN",false).id());
        var rows=db.queryForList("SELECT order_id,certification_id,released_at FROM runrun_v2.evidence_holds WHERE id=? FOR UPDATE",holdId);
        if(rows.isEmpty()) throw new IdentityFailure(404,"RESOURCE_NOT_FOUND");
        var hold=rows.get(0); lockAggregate((Number)hold.get("order_id"), (Number)hold.get("certification_id"));
        HoldResult replay=idempotency.replay(Long.toString(owner),"evidence-hold:release",requestId,Long.toString(holdId),HoldResult.class);
        if(replay!=null) return replay;
        if(hold.get("released_at")!=null) throw new IdentityFailure(409,"EVIDENCE_HOLD_ALREADY_RELEASED");
        db.update("UPDATE runrun_v2.evidence_holds SET released_at=now() WHERE id=?",holdId);
        db.update("INSERT INTO runrun_v2.admin_audit_logs(administrator_id,action,target_type,target_id,request_id,reason,metadata) VALUES(?,'EVIDENCE_HOLD_RELEASED','EVIDENCE_HOLD',?,?,?,'{}'::jsonb)",owner,holdId,requestId,"evidence hold released");
        HoldResult result=new HoldResult(holdId); idempotency.finish(Long.toString(owner),"evidence-hold:release",requestId,"EVIDENCE_HOLD",Long.toString(holdId),result); return result;
    }

    @Transactional
    public void requestCertificationDeletion(long certificationId, String resourceId, String storageKey, Instant deleteAfter) {
        if (db.queryForObject("SELECT count(*) FROM runrun_v2.evidence_holds WHERE certification_id=? AND released_at IS NULL",Long.class,certificationId)>0) return;
        // Preserve the exact retention instant using the driver's TIMESTAMPTZ representation.
        db.update("INSERT INTO runrun_v2.evidence_deletion_audits(certification_id,resource_type,resource_id,resource_storage_key,delete_after,policy_snapshot,status) SELECT ?, 'STUDENT_CARD', ?, ?, ?, jsonb_build_object('retentionDays',30), 'REQUESTED' WHERE NOT EXISTS (SELECT 1 FROM runrun_v2.evidence_deletion_audits WHERE certification_id=? AND resource_id=? AND status IN ('REQUESTED','DELETING','SUCCEEDED'))",certificationId,resourceId,storageKey,deleteAfter.atOffset(ZoneOffset.UTC),certificationId,resourceId);
    }

    @Scheduled(fixedDelayString="${rrc.evidence.cleanup-delay-ms:60000}", initialDelayString="${rrc.evidence.cleanup-delay-ms:60000}")
    @Transactional
    public void cleanupDue() {
        db.queryForList("SELECT id,certification_id,resource_id,resource_storage_key FROM runrun_v2.evidence_deletion_audits WHERE status IN ('REQUESTED','FAILED') AND delete_after<=now() ORDER BY id FOR UPDATE SKIP LOCKED").forEach(this::deleteOne);
        db.queryForList("SELECT c.id,c.approved_photo_resource_id,c.approved_photo_storage_key,c.approved_photo_at FROM runrun_v2.courier_certifications c WHERE c.approved_photo_resource_id IS NOT NULL AND c.approved_photo_at<=now()-interval '30 days' AND NOT EXISTS (SELECT 1 FROM runrun_v2.evidence_deletion_audits d WHERE d.certification_id=c.id AND d.resource_id=c.approved_photo_resource_id AND d.status IN ('REQUESTED','DELETING','SUCCEEDED'))").forEach(r -> requestCertificationDeletion(((Number)r.get("id")).longValue(),(String)r.get("approved_photo_resource_id"),(String)r.get("approved_photo_storage_key"),Instant.now()));
    }

    private void deleteOne(Map<String,Object> r) {
        long id=((Number)r.get("id")).longValue(); long cert=((Number)r.get("certification_id")).longValue(); String resource=(String)r.get("resource_id"); String key=(String)r.get("resource_storage_key");
        lockAggregate(null, cert);
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
    public record HoldResult(long id) {}
    private void lockAggregate(Long orderId, Long certificationId){
        if(orderId!=null) db.queryForObject("SELECT id FROM runrun_v2.v2_orders WHERE id=? FOR UPDATE",Long.class,orderId);
        if(certificationId!=null) db.queryForObject("SELECT id FROM runrun_v2.courier_certifications WHERE id=? FOR UPDATE",Long.class,certificationId);
    }
    private void lockAggregate(Number orderId, Number certificationId){ lockAggregate(orderId==null?null:orderId.longValue(), certificationId==null?null:certificationId.longValue()); }
}
