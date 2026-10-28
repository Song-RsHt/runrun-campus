package com.zhangxuan.runrunv2;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** JDBC implementation used by the isolated v2 application; it never touches legacy tables. */
@Service
public class CourierCertificationV2Service {
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private final JdbcTemplate db;
    private final V2StudentCardStorage storage;
    private final OrderIdempotency idempotency;
    private final CourierEvidenceGovernanceService governance;
    public CourierCertificationV2Service(JdbcTemplate db, V2StudentCardStorage storage, OrderIdempotency idempotency, CourierEvidenceGovernanceService governance) { this.db=db; this.storage=storage; this.idempotency=idempotency; this.governance=governance; }

    public record Certification(long id,long courierId,String status,boolean eligible,long version,
                                Instant submittedAt,Instant reviewedAt,boolean photoUploaded,String photoResourceId,String rejectionReason) {}

    @Transactional
    public Certification apply(String token, IdentityService identity, String requestId) {
        IdentityService.Account a=identity.authorizeForWrite(token,"COURIER",false);
        long courier=Long.parseLong(a.id());
        lockActor(courier);
        Certification replay=idempotency.replay(Long.toString(courier),"courier-certification:apply",requestId,"apply",Certification.class);
        if(replay!=null) return replay;
        var rows=db.queryForList("SELECT * FROM runrun_v2.courier_certifications WHERE courier_id=? FOR UPDATE",courier);
        if(rows.isEmpty()) { db.update("INSERT INTO runrun_v2.courier_certifications(courier_id,status,submitted_at) VALUES(?,'PENDING',now())",courier); }
        else {
            var row=rows.get(0); if("PENDING".equals(row.get("status"))) throw new IdentityFailure(409,"CERTIFICATION_STATE_CONFLICT");
            db.update("UPDATE runrun_v2.courier_certifications SET status='PENDING',rejection_reason=NULL,submitted_at=now(),version=version+1,updated_at=now() WHERE courier_id=?",courier);
        }
        long id=db.queryForObject("SELECT id FROM runrun_v2.courier_certifications WHERE courier_id=?",Long.class,courier);
        event(id,courier,"COURIER","SUBMITTED",requestId);
        Certification result=get(courier,id); idempotency.finish(Long.toString(courier),"courier-certification:apply",requestId,"COURIER_CERTIFICATION",Long.toString(id),result); return result;
    }

    @Transactional
    public Certification upload(String token, IdentityService identity,long id,long expectedVersion,String requestId,MultipartFile file) {
        IdentityService.Account a=identity.authorizeForWrite(token,"COURIER",false); long courier=Long.parseLong(a.id());
        lockActor(courier);
        byte[] bytes; try { bytes=file==null?new byte[0]:file.getBytes(); } catch(IOException e){ throw new IdentityFailure(400,"UPLOAD_INVALID"); }
        String contentHash=digest(bytes);
        Certification replay=idempotency.replay(Long.toString(courier),"courier-certification:upload",requestId,id+":"+expectedVersion+":"+contentHash,Certification.class);
        if(replay!=null) return replay;
        var row=locked(id); if(((Number)row.get("courier_id")).longValue()!=courier || !"PENDING".equals(row.get("status"))) throw new IdentityFailure(409,"CERTIFICATION_STATE_CONFLICT");
        if(((Number)row.get("version")).longValue()!=expectedVersion || file==null || file.isEmpty() || file.getSize()>MAX_BYTES) throw new IdentityFailure(400,"UPLOAD_INVALID");
        String type=file.getContentType(); boolean jpeg="image/jpeg".equals(type)&&bytes.length>=3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255;
        boolean png="image/png".equals(type)&&bytes.length>=8&&(bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71&&bytes[4]==13&&bytes[5]==10&&bytes[6]==26&&bytes[7]==10;
        if(!jpeg&&!png) throw new IdentityFailure(400,"UPLOAD_INVALID");
        String oldResource=(String)row.get("submitted_photo_resource_id"), oldKey=(String)row.get("submitted_photo_storage_key");
        String resource="student-card-"+UUID.randomUUID(), key="courier/"+courier+"/"+resource+(jpeg?".jpg":".png");
        try { storage.store(key,type,bytes); } catch(IOException e){ throw new IdentityFailure(500,"UPLOAD_STORAGE_FAILED"); }
        db.update("UPDATE runrun_v2.courier_certifications SET submitted_photo_resource_id=?,submitted_photo_storage_key=?,version=version+1,updated_at=now() WHERE id=? AND version=?",resource,key,id,expectedVersion);
        if(db.update("UPDATE runrun_v2.courier_certifications SET updated_at=now() WHERE id=? AND version=?",id,expectedVersion+1)!=1) throw new IdentityFailure(409,"CERTIFICATION_STATE_CONFLICT");
        if(oldResource!=null && !oldResource.equals(resource)) governance.requestCertificationDeletion(id,oldResource,oldKey,Instant.now().plus(java.time.Duration.ofDays(30)));
        event(id,courier,"COURIER","PHOTO_UPLOADED",requestId); Certification result=get(courier,id); idempotency.finish(Long.toString(courier),"courier-certification:upload",requestId,"COURIER_CERTIFICATION",Long.toString(id),result); return result;
    }

    @Transactional
    public Certification decide(String token, IdentityService identity,long id,long expectedVersion,String resource,String decision,String reason,String requestId) {
        IdentityService.Account a=identity.authorizeForWrite(token,"ADMIN",false); long admin=Long.parseLong(a.id());
        lockActor(admin);
        if(!"APPROVE".equals(decision) && !"REJECT".equals(decision)) throw new IdentityFailure(400,"INVALID_REQUEST");
        Certification replay=idempotency.replay(Long.toString(admin),"courier-certification:decide",requestId,id+":"+expectedVersion+":"+resource+":"+decision+":"+reason,Certification.class);
        if(replay!=null) return replay;
        var row=locked(id);
        if(((Number)row.get("version")).longValue()!=expectedVersion || !"PENDING".equals(row.get("status"))) throw new IdentityFailure(409,"CERTIFICATION_STATE_CONFLICT");
        String oldApprovedResource=(String)row.get("approved_photo_resource_id"), oldApprovedKey=(String)row.get("approved_photo_storage_key");
        if("APPROVE".equals(decision)) {
            if(resource==null || !resource.equals(row.get("submitted_photo_resource_id"))) throw new IdentityFailure(409,"PROOF_NOT_READY");
            db.update("UPDATE runrun_v2.courier_certifications SET status='APPROVED',eligible=TRUE,approved_photo_resource_id=submitted_photo_resource_id,approved_photo_storage_key=submitted_photo_storage_key,approved_photo_at=now(),submitted_photo_resource_id=NULL,submitted_photo_storage_key=NULL,reviewed_at=now(),version=version+1,updated_at=now() WHERE id=? AND version=?",id,expectedVersion);
            if(oldApprovedResource!=null && !oldApprovedResource.equals(resource)) governance.requestCertificationDeletion(id,oldApprovedResource,oldApprovedKey,Instant.now().plus(java.time.Duration.ofDays(30)));
        } else { if(reason==null||reason.isBlank()) throw new IdentityFailure(400,"INVALID_REQUEST"); db.update("UPDATE runrun_v2.courier_certifications SET status='REJECTED',rejection_reason=?,reviewed_at=now(),version=version+1,updated_at=now() WHERE id=? AND version=?",reason,id,expectedVersion); }
        event(id,admin,"ADMIN","REVIEWED",requestId); Certification result=get(admin,id); idempotency.finish(Long.toString(admin),"courier-certification:decide",requestId,"COURIER_CERTIFICATION",Long.toString(id),result); return result;
    }

    @Transactional
    public byte[] read(String token, IdentityService identity,long id,String resource,String requestId) {
        IdentityService.Account a=identity.requireRole(token,"ADMIN",false); var row=locked(id);
        String actual=resource!=null?resource:(String)row.get("approved_photo_resource_id"); if(actual==null) actual=(String)row.get("submitted_photo_resource_id");
        if(actual==null || !actual.equals(row.get("approved_photo_resource_id"))&&!actual.equals(row.get("submitted_photo_resource_id"))) throw new IdentityFailure(404,"PROOF_EXPIRED");
        if(db.queryForObject("SELECT count(*) FROM runrun_v2.evidence_deletion_audits WHERE certification_id=? AND resource_id=? AND status='DELETING'",Long.class,id,actual)>0) throw new IdentityFailure(409,"EVIDENCE_DELETION_IN_PROGRESS");
        try { byte[] result=storage.read((String)(actual.equals(row.get("approved_photo_resource_id"))?row.get("approved_photo_storage_key"):row.get("submitted_photo_storage_key")));
            db.update("INSERT INTO runrun_v2.admin_audit_logs(administrator_id,action,target_type,target_id,request_id,reason,metadata) VALUES(?,'COURIER_CERTIFICATION_PHOTO_READ','COURIER_CERTIFICATION',?,?,?,jsonb_build_object('photoResourceId',?)) ON CONFLICT (administrator_id,action,request_id) DO NOTHING",Long.parseLong(a.id()),id,requestId,"controlled student-card read",actual);
            return result;
        } catch(IOException e){ throw new IdentityFailure(404,"PROOF_EXPIRED"); }
    }

    public Certification get(long subject,long id){ var r=locked(id); return map(r); }
    public Certification getMine(long courier){ var rows=db.queryForList("SELECT * FROM runrun_v2.courier_certifications WHERE courier_id=?",courier); if(rows.isEmpty()) return null; return map(rows.get(0)); }
    private java.util.Map<String,Object> locked(long id){ var rows=db.queryForList("SELECT * FROM runrun_v2.courier_certifications WHERE id=? FOR UPDATE",id); if(rows.isEmpty()) throw new IdentityFailure(404,"RESOURCE_NOT_FOUND"); return rows.get(0); }
    private Certification map(java.util.Map<String,Object> r){ return new Certification(((Number)r.get("id")).longValue(),((Number)r.get("courier_id")).longValue(),(String)r.get("status"),Boolean.TRUE.equals(r.get("eligible")),((Number)r.get("version")).longValue(),instant(r.get("submitted_at")),instant(r.get("reviewed_at")),r.get("submitted_photo_resource_id")!=null||r.get("approved_photo_resource_id")!=null,(String)(r.get("status").equals("APPROVED")?r.get("approved_photo_resource_id"):r.get("submitted_photo_resource_id")),(String)r.get("rejection_reason")); }
    private static Instant instant(Object value){ if(value==null)return null; if(value instanceof Instant i)return i; if(value instanceof java.sql.Timestamp t)return t.toInstant(); if(value instanceof java.time.OffsetDateTime o)return o.toInstant(); return Instant.parse(value.toString()); }
    private static String digest(byte[] bytes){ try { byte[] d=MessageDigest.getInstance("SHA-256").digest(bytes); StringBuilder s=new StringBuilder(); for(byte b:d)s.append(String.format("%02x",b)); return s.toString(); } catch(Exception e){ throw new IllegalStateException("digest unavailable",e); } }
    private void lockActor(long actor){ db.queryForObject("SELECT id FROM runrun_v2.app_users WHERE id=? FOR UPDATE",Long.class,actor); }
    private void event(long id,long actor,String kind,String type,String request){ db.update("INSERT INTO runrun_v2.courier_certification_events(certification_id,actor_id,actor_kind,event_type,request_id,certification_version) SELECT ?,?,?,?, ?,version FROM runrun_v2.courier_certifications WHERE id=?",id,actor,kind,type,request,id); }
}
