package com.zhangxuan.runruncampus.v2.foundation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhangxuan.runruncampus.admin.AdminAuditAction;
import com.zhangxuan.runruncampus.admin.AdminAuditTargetType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.Objects;

@Component
public class AuditFacts {
    public enum ActorKind { USER, COURIER, ADMIN, SYSTEM }
    public record Actor(ActorKind kind, Long id, String systemSource) {
        public Actor {
            Objects.requireNonNull(kind);
            if (kind == ActorKind.SYSTEM) {
                if (id != null || systemSource == null || systemSource.isBlank()) throw new IllegalArgumentException("System source required; no fabricated user");
            } else if (id == null || id <= 0 || systemSource != null) throw new IllegalArgumentException("Real actor required");
        }
    }
    private final JdbcTemplate jdbc;
    public AuditFacts(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void order(long orderId, Long taskId, Actor actor, String event, String requestId, ObjectNode metadata) {
        TransactionRequired.check(); requireRequest(requestId);
        ObjectNode payload = metadata.deepCopy();
        payload.put("actorKind", actor.kind().name());
        if (actor.systemSource() != null) payload.put("systemSource", actor.systemSource());
        jdbc.update("insert into v2_order_events(order_id,task_id,actor_id,event_type,request_id,payload) values (?,?,?,?,?,?::jsonb)",
                orderId, taskId, actor.id(), event, requestId, payload.toString());
    }
    public void certification(long id, long version, Actor actor, String event, String requestId, ObjectNode metadata) {
        TransactionRequired.check(); requireRequest(requestId);
        if (actor.kind() == ActorKind.USER) throw new IllegalArgumentException("Certification actor must be COURIER/ADMIN/SYSTEM");
        ObjectNode payload = metadata.deepCopy();
        if (actor.systemSource() != null) payload.put("systemSource", actor.systemSource());
        jdbc.update("insert into courier_certification_events(certification_id,actor_id,actor_kind,event_type,request_id,certification_version,metadata) values (?,?,?,?,?,?,?::jsonb)",
                id, actor.id(), actor.kind().name(), event, requestId, version, payload.toString());
    }
    public void administrator(long realAdminId, AdminAuditAction action, AdminAuditTargetType target, long targetId,
                              String reason, String requestId, JsonNode metadata) {
        TransactionRequired.check(); requireRequest(requestId);
        if (reason == null || reason.isBlank()) throw new V2Exception(V2Error.VALIDATION_ERROR, "理由必填");
        ResourceChecks.visible(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from app_users where id=? and role='ADMIN')", Boolean.class, realAdminId)));
        // M44-14: a controlled deployment identity still maps to a real ADMIN; never invent one.
        jdbc.update("insert into admin_audit_logs(action,target_type,target_id,summary,created_at,administrator_id,metadata,request_id) values (?,?,?,?,now(),?,?::jsonb,?)",
                action.name(), target.name(), targetId, reason, realAdminId, metadata.toString(), requestId);
    }
    private static void requireRequest(String requestId) {
        if (requestId == null || requestId.isBlank()) throw new V2Exception(V2Error.VALIDATION_ERROR, "requestId必填");
    }
}
